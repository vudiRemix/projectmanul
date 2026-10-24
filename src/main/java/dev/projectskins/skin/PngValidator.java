package dev.projectskins.skin;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.zip.CRC32;

/**
 * Проверяет содержимое локального, скачанного или закэшированного PNG.
 * Ограничения размера файла и IHDR проверяются до декодирования пикселей,
 * поэтому маленький ответ с огромными размерами не запускает большое выделение
 * памяти. Затем проверяются структура, CRC и фактическая декодируемость PNG.
 * ImageIO работает без окна и не требует загрузки клиентских классов Minecraft.
 */
public final class PngValidator {
    public static final int WIDTH = 64;
    public static final int HEIGHT = 64;
    public static final int MAX_BYTES = 1024 * 1024;

    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private PngValidator() {
    }

    /** Возвращается только после успешной проверки; ошибка не меняет текущий скин. */
    public static void validate(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 45 || bytes.length > MAX_BYTES) {
            throw new IOException("PNG должен быть не больше 1 МиБ и содержать полное изображение.");
        }
        if (!Arrays.equals(SIGNATURE, Arrays.copyOf(bytes, SIGNATURE.length))) {
            throw new IOException("Ответ не является PNG.");
        }
        if (integer(bytes, 8) != 13 || !chunkIs(bytes, 12, "IHDR")) {
            throw new IOException("Отсутствует корректный заголовок PNG IHDR.");
        }
        if (integer(bytes, 16) != WIDTH || integer(bytes, 20) != HEIGHT) {
            throw new IOException("Скин должен иметь размер ровно 64×64 пикселя.");
        }
        checkChunks(bytes);

        try (MemoryCacheImageInputStream input =
                     new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Не удалось найти декодер PNG.");
            }
            ImageReader reader = readers.next();
            try {
                if (!"png".equalsIgnoreCase(reader.getFormatName())) {
                    throw new IOException("Неверный формат изображения.");
                }
                reader.setInput(input, true, true);
                if (reader.getWidth(0) != WIDTH || reader.getHeight(0) != HEIGHT) {
                    throw new IOException("Скин должен иметь размер ровно 64×64 пикселя.");
                }
                BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() != WIDTH || image.getHeight() != HEIGHT) {
                    throw new IOException("Не удалось декодировать PNG 64×64.");
                }
            } finally {
                reader.dispose();
            }
        } catch (RuntimeException exception) {
            throw new IOException("Повреждённое изображение PNG.", exception);
        }
    }

    /** Проверяет длины и CRC всех блоков; обрезанный файл не попадёт в кэш. */
    private static void checkChunks(byte[] bytes) throws IOException {
        int offset = SIGNATURE.length;
        boolean imageDataSeen = false;
        while (offset < bytes.length) {
            if (bytes.length - offset < 12) {
                throw new IOException("Обрезанный блок PNG.");
            }
            int length = integer(bytes, offset);
            if (length < 0 || (long) offset + length + 12 > bytes.length) {
                throw new IOException("Недопустимая длина блока PNG.");
            }
            CRC32 checksum = new CRC32();
            checksum.update(bytes, offset + 4, length + 4);
            if (checksum.getValue() != Integer.toUnsignedLong(integer(bytes, offset + length + 8))) {
                throw new IOException("Повреждённый блок PNG: CRC не совпадает.");
            }
            if (offset != SIGNATURE.length && chunkIs(bytes, offset + 4, "IHDR")) {
                throw new IOException("Повторный заголовок PNG IHDR.");
            }
            if (chunkIs(bytes, offset + 4, "IDAT")) {
                imageDataSeen = true;
            }
            if (chunkIs(bytes, offset + 4, "IEND")) {
                if (length != 0 || !imageDataSeen || offset + 12 != bytes.length) {
                    throw new IOException("Некорректное окончание PNG.");
                }
                return;
            }
            offset += length + 12;
        }
        throw new IOException("В PNG отсутствует конечный блок IEND.");
    }

    private static boolean chunkIs(byte[] bytes, int offset, String type) {
        for (int index = 0; index < 4; index++) {
            if (bytes[offset + index] != type.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int integer(byte[] bytes, int offset) {
        return ((bytes[offset] & 255) << 24) | ((bytes[offset + 1] & 255) << 16)
                | ((bytes[offset + 2] & 255) << 8) | (bytes[offset + 3] & 255);
    }
}
