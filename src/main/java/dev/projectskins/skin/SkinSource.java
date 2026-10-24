package dev.projectskins.skin;

import java.util.Objects;

/**
 * Неизменяемое описание скина для серверного назначения и сетевого пакета.
 * В отличие от ResourceLocation, этот объект не обозначает готовую GPU-текстуру:
 * URL ещё необходимо скачать. Конструктор проверяет данные независимо от того,
 * пришли ли они из команды или были прочитаны из сетевого пакета.
 */
public record SkinSource(Type type, String value) {
    /** Тип источника записывается в пакет отдельно от его строкового значения. */
    public enum Type {
        LOCAL,
        URL
    }

    public SkinSource {
        Objects.requireNonNull(type, "type");
        value = switch (type) {
            case LOCAL -> SkinValidator.localId(value);
            case URL -> SkinValidator.imgurUri(value).toString();
        };
    }

    /**
     * Разбирает пользовательский аргумент. Строка, не являющаяся допустимым
     * локальным именем, должна пройти полную проверку HTTPS URL.
     */
    public static SkinSource parse(String value) {
        if (value != null && value.matches("[a-z0-9_-]{1,64}")) {
            return new SkinSource(Type.LOCAL, value);
        }
        return new SkinSource(Type.URL, value);
    }
}
