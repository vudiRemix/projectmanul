package dev.projectskins.skin;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Синтетические изображения существуют только как тестовые данные. */
class PngValidatorTest {
    @Test
    void acceptsRgbaSkin64By64() throws IOException {
        byte[] image = png(64, 64);
        assertDoesNotThrow(() -> PngValidator.validate(image));
    }

    @Test
    void rejectsOldSkinLayoutAndOversizedDimensions() throws IOException {
        byte[] oldSkin = png(64, 32);
        byte[] largeSkin = png(128, 128);
        assertThrows(IOException.class, () -> PngValidator.validate(oldSkin));
        assertThrows(IOException.class, () -> PngValidator.validate(largeSkin));
    }

    @Test
    void rejectsHtmlDisguisedAsImageAndEmptyBody() {
        assertThrows(IOException.class,
                () -> PngValidator.validate("<html>Not an image</html>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> PngValidator.validate(new byte[0]));
        assertThrows(IOException.class, () -> PngValidator.validate(null));
    }

    @Test
    void rejectsOversizedDownloadBeforeDecoding() {
        assertThrows(IOException.class, () -> PngValidator.validate(new byte[PngValidator.MAX_BYTES + 1]));
    }

    @Test
    void rejectsCorruptChunkChecksum() throws IOException {
        byte[] image = png(64, 64);
        image[29] ^= 1; // CRC блока IHDR, а не размер изображения.
        assertThrows(IOException.class, () -> PngValidator.validate(image));
    }

    @Test
    void rejectsTruncatedFileAndTrailingData() throws IOException {
        byte[] image = png(64, 64);
        assertThrows(IOException.class, () -> PngValidator.validate(Arrays.copyOf(image, image.length - 5)));
        assertThrows(IOException.class, () -> PngValidator.validate(Arrays.copyOf(image, image.length + 1)));
    }

    @Test
    void rejectsMaliciousDeclaredDimensionsBeforeImageIo() throws IOException {
        byte[] image = png(64, 64);
        Arrays.fill(image, 16, 20, (byte) 127);
        assertThrows(IOException.class, () -> PngValidator.validate(image));
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff335577);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("PNG writer отсутствует в тестовой JVM.");
        }
        return output.toByteArray();
    }
}
