package dev.projectskins.client;

import dev.projectskins.skin.PngValidator;
import dev.projectskins.skin.SkinSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверяет кэш и ограничение HTTP body без зависимости от доступности Imgur. */
class SkinDownloaderTest {
    @TempDir
    Path cacheDirectory;

    @Test
    void readsValidatedDiskCacheWithoutNetwork() throws Exception {
        SkinSource source = SkinSource.parse("https://imgur.com/AbC123.png");
        byte[] png = png();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(source.value().getBytes(UTF_8)));
        Files.write(cacheDirectory.resolve(hash + ".png"), png);

        assertArrayEquals(png, new SkinDownloader(cacheDirectory).load(source));
    }

    @Test
    void rejectsLocalSourceWithoutNetwork() {
        assertThrows(IllegalArgumentException.class,
                () -> new SkinDownloader(cacheDirectory).load(SkinSource.parse("bazuev")));
    }

    @Test
    void limitsAccumulatedBodyEvenWithoutContentLength() {
        SkinDownloader.LimitedBodySubscriber body = new SkinDownloader.LimitedBodySubscriber(null);
        TestSubscription subscription = new TestSubscription();
        body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.wrap(new byte[PngValidator.MAX_BYTES])));
        assertFalse(subscription.cancelled);
        body.onNext(List.of(ByteBuffer.wrap(new byte[1])));

        assertTrue(subscription.cancelled);
        CompletionException error = assertThrows(CompletionException.class,
                () -> body.getBody().toCompletableFuture().join());
        assertTrue(error.getCause() instanceof IOException);
    }

    @Test
    void collectsMultipleBuffersInOrder() {
        SkinDownloader.LimitedBodySubscriber body = new SkinDownloader.LimitedBodySubscriber(null);
        TestSubscription subscription = new TestSubscription();
        body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2}), ByteBuffer.wrap(new byte[]{3})));
        body.onComplete();

        assertArrayEquals(new byte[]{1, 2, 3}, body.getBody().toCompletableFuture().join());
        assertEquals(2, subscription.requests);
        assertFalse(subscription.cancelled);
    }

    @Test
    void rejectsStatusOrLengthBeforeReceivingBody() {
        SkinDownloader.LimitedBodySubscriber body =
                new SkinDownloader.LimitedBodySubscriber(new IOException("HTTP 302"));
        TestSubscription subscription = new TestSubscription();
        body.onSubscribe(subscription);

        assertTrue(subscription.cancelled);
        assertEquals(0, subscription.requests);
        assertThrows(CompletionException.class, () -> body.getBody().toCompletableFuture().join());
    }

    private static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("PNG writer отсутствует в тестовой JVM.");
        }
        return output.toByteArray();
    }

    /** Минимальная управляемая подписка для проверки отмены HTTP-приёма. */
    private static final class TestSubscription implements Flow.Subscription {
        private boolean cancelled;
        private long requests;

        @Override
        public void request(long amount) {
            requests += amount;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
