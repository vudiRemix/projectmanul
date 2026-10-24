package dev.projectskins.skin;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Общие правила проверки источника скина, доступные и серверу, и клиенту.
 * Класс не обращается к Minecraft, сети или файловой системе. Проверка хоста
 * выполняется по разобранному URI, а не по наличию строки «imgur.com» в адресе.
 */
public final class SkinValidator {
    public static final int MAX_SOURCE_LENGTH = 2048;

    private static final Pattern LOCAL_ID = Pattern.compile("[a-z0-9_-]{1,64}");
    private static final Pattern IMAGE_PATH = Pattern.compile("/[A-Za-z0-9]+\\.png");

    private SkinValidator() {
    }

    /** Проверяет имя файла без расширения и исключает обход каталогов. */
    public static String localId(String value) {
        if (value == null || !LOCAL_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Имя скина: от 1 до 64 символов a-z, 0-9, _ или -, без расширения.");
        }
        return value;
    }

    /**
     * Принимает только прямой HTTPS-адрес PNG на Imgur. Ссылки imgur.com
     * приводятся к серверу изображений i.imgur.com без HTTP-перенаправлений.
     * Регистр идентификатора изображения сохраняется: он является частью пути.
     */
    public static URI imgurUri(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_SOURCE_LENGTH) {
            throw new IllegalArgumentException("Недопустимая длина URL скина.");
        }

        final URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Некорректный URL скина.", exception);
        }

        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !("imgur.com".equals(host.toLowerCase(Locale.ROOT))
                || "i.imgur.com".equals(host.toLowerCase(Locale.ROOT)))
                || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getRawPath() == null || !IMAGE_PATH.matcher(uri.getRawPath()).matches()) {
            throw new IllegalArgumentException(
                    "Нужен прямой HTTPS URL вида https://i.imgur.com/ID.png без параметров.");
        }

        return URI.create("https://i.imgur.com" + uri.getRawPath());
    }
}
