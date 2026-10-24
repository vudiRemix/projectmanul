package dev.projectskins.client;

import dev.projectskins.skin.PngValidator;
import dev.projectskins.skin.SkinSource;
import dev.projectskins.skin.SkinValidator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Загружает только проверенные URL Imgur и хранит исходные PNG на диске.
 * {@link #load(SkinSource)} блокирует вызывающий поток, поэтому его разрешено
 * вызывать только из фонового исполнителя, никогда из рендера или обработчика
 * пакетов. Класс не создаёт GPU-текстуры и не обращается к Minecraft.
 *
 * <p>HTTP-перенаправления отключены. Ограничены как время всей загрузки, так и
 * количество полученных байтов, включая ответы без Content-Length. Недоступный
 * файловый кэш не препятствует применению успешно скачанного изображения.</p>
 */
public final class SkinDownloader {
    private static final int MAX_CACHE_FILES = 128;
    private static final int TOTAL_TIMEOUT_SECONDS = 15;

    private final Path cacheDirectory;
    private final HttpClient httpClient;

    public SkinDownloader(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory.toAbsolutePath().normalize();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Сначала читает проверенный кэш; иначе скачивает PNG и пытается сохранить его.
     * Прерывание фоновой задачи отменяет HTTP-запрос и сохраняет interrupt-флаг.
     */
    public byte[] load(SkinSource source) throws IOException {
        if (source.type() != SkinSource.Type.URL) {
            throw new IllegalArgumentException("SkinDownloader принимает только URL.");
        }
        URI uri = SkinValidator.imgurUri(source.value());
        String key = cacheKey(uri);
        byte[] cached = readCache(key);
        if (cached != null) {
            return cached;
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(TOTAL_TIMEOUT_SECONDS))
                .header("User-Agent", "ProjectSkins/1.0")
                .header("Accept", "image/png")
                .GET()
                .build();

        CompletableFuture<HttpResponse<byte[]>> pending = httpClient.sendAsync(request, info -> {
            IOException rejection = null;
            if (info.statusCode() != 200) {
                rejection = new IOException("Imgur ответил HTTP " + info.statusCode()
                        + "; перенаправления не разрешены.");
            } else if (info.headers().firstValueAsLong("Content-Length").orElse(0) > PngValidator.MAX_BYTES) {
                rejection = new IOException("Размер ответа Imgur превышает 1 МиБ.");
            }
            return new LimitedBodySubscriber(rejection);
        });

        final byte[] bytes;
        try {
            bytes = pending.get(TOTAL_TIMEOUT_SECONDS, TimeUnit.SECONDS).body();
        } catch (InterruptedException exception) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Загрузка скина прервана.", exception);
        } catch (TimeoutException exception) {
            pending.cancel(true);
            throw new IOException("Истекло время загрузки скина.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Не удалось загрузить скин с Imgur.", cause);
        }

        PngValidator.validate(bytes);
        writeCache(key, bytes);
        return bytes;
    }

    /** Имя кэша не содержит частей пользовательского пути и стабильно между запусками. */
    private static String cacheKey(URI uri) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().getBytes(UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM не поддерживает обязательный алгоритм SHA-256.", exception);
        }
    }

    /** Синхронизация нужна только для коротких операций кэша, но не для сетевого ожидания. */
    private synchronized byte[] readCache(String key) {
        Path file = cacheDirectory.resolve(key + ".png");
        try {
            if (Files.isSymbolicLink(cacheDirectory)
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(file) > PngValidator.MAX_BYTES) {
                return null;
            }
            byte[] bytes;
            try (InputStream input = Files.newInputStream(file)) {
                bytes = input.readNBytes(PngValidator.MAX_BYTES + 1);
            }
            PngValidator.validate(bytes);
            try {
                Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
            } catch (IOException | SecurityException ignored) {
                // Отсутствие права менять дату не делает корректный PNG непригодным.
            }
            return bytes;
        } catch (IOException | SecurityException ignored) {
            // Отсутствующий/повреждённый кэш заменяется обычной загрузкой из сети.
            return null;
        }
    }

    /**
     * В кэш попадают только уже проверенные данные. Запись через временный файл
     * исключает чтение половины PNG после аварийного завершения предыдущей игры.
     */
    private synchronized void writeCache(String key, byte[] bytes) {
        Path temporary = null;
        try {
            if (Files.isSymbolicLink(cacheDirectory)) {
                return;
            }
            Files.createDirectories(cacheDirectory);
            temporary = Files.createTempFile(cacheDirectory, key + "-", ".part");
            Files.write(temporary, bytes);
            Path target = cacheDirectory.resolve(key + ".png");
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            trimCache();
        } catch (IOException | SecurityException ignored) {
            // Пользователь всё равно получает изображение из памяти при read-only диске.
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | SecurityException ignored) {
                    // Ошибка уборки временного файла не отменяет уже готовый скин.
                }
            }
        }
    }

    /**
     * Не больше 128 файлов по 1 МиБ: верхняя граница постоянного PNG-кэша — 128 МиБ.
     * Удаляются только файлы собственного формата имени, без обхода подкаталогов.
     */
    private void trimCache() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> entries = Files.list(cacheDirectory)) {
            entries.filter(path -> path.getFileName().toString().matches("[0-9a-f]{64}\\.png"))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .forEach(files::add);
        }
        files.sort(Comparator.comparingLong(SkinDownloader::modificationTime));
        for (int index = 0; index < files.size() - MAX_CACHE_FILES; index++) {
            Files.deleteIfExists(files.get(index));
        }
    }

    private static long modificationTime(Path file) {
        try {
            return Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException | SecurityException ignored) {
            return Long.MIN_VALUE;
        }
    }

    /**
     * Получатель HTTP body с ограничением памяти. Ограничение проверяется до
     * копирования каждого буфера; превышение немедленно отменяет подписку.
     * Ожидание завершения этого получателя дополнительно ограничено в load().
     */
    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        LimitedBodySubscriber(IOException rejection) {
            if (rejection != null) {
                body.completeExceptionally(rejection);
            }
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription newSubscription) {
            if (subscription != null || body.isDone()) {
                newSubscription.cancel();
                return;
            }
            subscription = newSubscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                int count = buffer.remaining();
                if (count > PngValidator.MAX_BYTES - output.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Размер ответа Imgur превышает 1 МиБ."));
                    return;
                }
                byte[] part = new byte[count];
                buffer.get(part);
                output.writeBytes(part);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }
    }
}
