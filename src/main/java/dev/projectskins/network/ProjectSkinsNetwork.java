package dev.projectskins.network;

import dev.projectskins.ProjectSkins;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Общий сетевой канал Forge 1.20.1. Он обязателен на сервере и у каждого клиента:
 * соединение без совместимого протокола отклоняется при входе, а не оставляет
 * часть игроков без скинов. Оба сообщения разрешены только server → client.
 *
 * Клиент устанавливает обработчики отдельно через installClientHandlers. Здесь
 * нет ссылок на Minecraft, динамические текстуры или другие клиентские классы,
 * поэтому выделенный сервер может безопасно загрузить и зарегистрировать канал.
 */
public final class ProjectSkinsNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ProjectSkins.MOD_ID, "skins"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static volatile ClientHandlers clientHandlers;

    private ProjectSkinsNetwork() {
    }

    /** Вызывается ровно один раз из конструктора мода на обеих физических сторонах. */
    public static void register() {
        CHANNEL.messageBuilder(SkinSyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkinSyncPacket::encode)
                .decoder(SkinSyncPacket::decode)
                .consumerMainThread((packet, context) -> handlers().sync().accept(packet))
                .add();
        CHANNEL.messageBuilder(SkinResetPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkinResetPacket::encode)
                .decoder(SkinResetPacket::decode)
                .consumerMainThread((packet, context) -> handlers().reset().run())
                .add();
        // consumerMainThread в Forge сам ставит задачу в основной поток
        // и отмечает пакет обработанным; повторный enqueueWork не нужен.
    }

    /** Вызывается клиентской инициализацией до подключения к серверу. */
    public static void installClientHandlers(Consumer<SkinSyncPacket> sync, Runnable reset) {
        clientHandlers = new ClientHandlers(
                Objects.requireNonNull(sync, "sync"), Objects.requireNonNull(reset, "reset"));
    }

    /** Рассылка всем, а не только владельцу или игрокам в радиусе отслеживания. */
    public static void broadcast(SkinSyncPacket packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void sendTo(ServerPlayer player, SkinSyncPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendReset(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SkinResetPacket());
    }

    private static ClientHandlers handlers() {
        ClientHandlers installed = clientHandlers;
        if (installed == null) {
            throw new IllegalStateException("Клиентские обработчики ProjectSkins не зарегистрированы");
        }
        return installed;
    }

    /** Пара обработчиков публикуется атомарно при клиентской инициализации. */
    private record ClientHandlers(Consumer<SkinSyncPacket> sync, Runnable reset) {
    }
}
