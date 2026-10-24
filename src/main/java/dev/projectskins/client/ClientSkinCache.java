package dev.projectskins.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.projectskins.ProjectSkins;
import dev.projectskins.network.SkinSyncPacket;
import dev.projectskins.skin.PngValidator;
import dev.projectskins.skin.SkinSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Клиентский кэш: UUID → текущая рабочая текстура, отдельно от желаемого назначения.
 * Все карты, регистрация текстур и их освобождение доступны только игровому потоку.
 * Потоки загрузки работают исключительно с байтами и диском; они не трогают рендер.
 * Новое назначение становится видимым после успешной подготовки, поэтому ошибка
 * Imgur не превращает уже работающий скин в отсутствующую текстуру.
 * Один URL загружается один раз для всех ожидающих игроков данного подключения.
 */
public final class ClientSkinCache {
    private static final ClientSkinCache INSTANCE = new ClientSkinCache();
    private final Minecraft minecraft = Minecraft.getInstance();
    private final SkinAssignments assignments = new SkinAssignments();
    private final Map<UUID, Texture> active = new HashMap<>();
    private final Map<SkinSource, Texture> loaded = new HashMap<>();
    private final Map<SkinSource, CompletableFuture<byte[]>> pending = new HashMap<>();
    private final SkinDownloader downloader = new SkinDownloader(
            minecraft.gameDirectory.toPath().resolve("cache/projectskins"));
    private ThreadPoolExecutor workers;

    private ClientSkinCache() { }

    public static ClientSkinCache getInstance() {
        return INSTANCE;
    }

    /** Вызывается Mixin на каждом кадре; здесь нет диска, HTTP или декодирования. */
    public ResourceLocation textureFor(UUID playerId) {
        Texture texture = active.get(playerId);
        return texture == null ? null : texture.location();
    }

    /** Сетевой consumerMainThread вызывает этот метод на клиентском потоке. */
    public void apply(SkinSyncPacket packet) {
        SkinAssignments.Request request = assignments.accept(packet.playerId(), packet.source(), packet.revision());
        if (request == null) {
            return;
        }
        Texture existing = loaded.get(request.source());
        if (existing != null) {
            active.put(request.playerId(), existing);
            releaseUnused();
            return;
        }
        if (request.source().type() == SkinSource.Type.LOCAL) {
            applyLocal(request);
        } else {
            loadRemote(request);
        }
    }

    private void applyLocal(SkinAssignments.Request request) {
        ResourceLocation location = new ResourceLocation(ProjectSkins.MOD_ID,
                "textures/skins/" + request.source().value() + ".png");
        try (InputStream input = minecraft.getResourceManager().getResourceOrThrow(location).open()) {
            // Проверяется фактический ресурс клиента: он может быть заменён resource pack.
            byte[] bytes = input.readNBytes(PngValidator.MAX_BYTES + 1);
            PngValidator.validate(bytes);
            Texture texture = new Texture(request.source(), location, false);
            loaded.put(request.source(), texture);
            active.put(request.playerId(), texture);
            releaseUnused();
        } catch (IOException | RuntimeException error) {
            reportFailure(request, error);
        }
    }

    private void loadRemote(SkinAssignments.Request request) {
        try {
            CompletableFuture<byte[]> download = pending.computeIfAbsent(request.source(), source ->
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return downloader.load(source);
                        } catch (IOException error) {
                            throw new CompletionException(error);
                        }
                    }, workers()));
            download.whenComplete((bytes, failure) -> minecraft.execute(() -> {
                pending.remove(request.source(), download);
                // Проверяется и номер назначения, и поколение подключения.
                // Это также делает безопасным callback после cancel при отключении.
                if (!assignments.isCurrent(request)) {
                    return;
                }
                if (failure != null) {
                    reportFailure(request, failure);
                    return;
                }
                try {
                    Texture texture = loaded.get(request.source());
                    if (texture == null) {
                        texture = registerRemote(request.source(), bytes);
                        loaded.put(request.source(), texture);
                    }
                    active.put(request.playerId(), texture);
                    releaseUnused();
                } catch (IOException | RuntimeException error) {
                    reportFailure(request, error);
                }
            }));
        } catch (RuntimeException error) {
            // Переполнение ограниченной очереди — ошибка загрузки, не краш клиента.
            reportFailure(request, error);
        }
    }

    private Texture registerRemote(SkinSource source, byte[] bytes) throws IOException {
        NativeImage image = NativeImage.read(bytes);
        if (image.getWidth() != 64 || image.getHeight() != 64) {
            image.close();
            throw new IOException("Ожидался PNG 64x64");
        }
        DynamicTexture dynamic;
        try {
            dynamic = new DynamicTexture(image);
        } catch (RuntimeException error) {
            image.close();
            throw error;
        }
        try {
            ResourceLocation location = minecraft.getTextureManager().register("projectskins/skin", dynamic);
            return new Texture(source, location, true);
        } catch (RuntimeException error) {
            dynamic.close();
            throw error;
        }
    }

    private ThreadPoolExecutor workers() {
        if (workers == null) {
            workers = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(128), runnable -> {
                        Thread thread = new Thread(runnable, "ProjectSkins-download");
                        thread.setDaemon(true);
                        return thread;
                    });
            workers.allowCoreThreadTimeOut(true);
        }
        return workers;
    }

    /** Освобождает заменённые URL-текстуры, сохраняя используемые другими игроками. */
    private void releaseUnused() {
        Set<SkinSource> retained = new HashSet<>();
        active.values().forEach(texture -> retained.add(texture.source()));
        assignments.requests().forEach(request -> retained.add(request.source()));
        loaded.entrySet().removeIf(entry -> {
            if (retained.contains(entry.getKey())) {
                return false;
            }
            release(entry.getValue());
            return true;
        });
    }

    private void release(Texture texture) {
        if (texture.dynamic()) {
            minecraft.getTextureManager().release(texture.location());
        }
    }

    /** Новый снимок сервера или выход: старые callback больше не имеют права записи. */
    public void reset() {
        assignments.reset();
        active.clear();
        loaded.values().forEach(this::release);
        loaded.clear();
        // Callback может исполняться немедленно: сначала отделяем карту pending.
        var downloads = java.util.List.copyOf(pending.values());
        pending.clear();
        downloads.forEach(future -> future.cancel(true));
        if (workers != null) {
            workers.shutdownNow();
            workers = null;
        }
    }

    /** F3+T: заново проверить локальные ресурсы и восстановить URL из дискового кэша. */
    public void reload() {
        var requests = assignments.requests();
        reset();
        requests.forEach(request -> apply(new SkinSyncPacket(request.playerId(), request.source(), request.revision())));
    }

    private void reportFailure(SkinAssignments.Request request, Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        ProjectSkins.LOGGER.warn("Не удалось загрузить скин {} для {}: {}",
                request.source().value(), request.playerId(), cause.toString());
        if (minecraft.player != null) {
            // Имя берётся у загруженной сущности; если игрок далеко, остаётся UUID.
            Player target = minecraft.level == null ? null : minecraft.level.getPlayerByUUID(request.playerId());
            String who = target == null ? request.playerId().toString() : target.getName().getString();
            minecraft.player.displayClientMessage(Component.literal("[ProjectSkins] Скин для "
                    + who + " не загружен: " + request.source().value()
                    + ". Причина указана в logs/latest.log."), false);
        }
        releaseUnused();
    }

    /** Один объект может использоваться несколькими UUID; освобождается через loaded. */
    private record Texture(SkinSource source, ResourceLocation location, boolean dynamic) { }
}
