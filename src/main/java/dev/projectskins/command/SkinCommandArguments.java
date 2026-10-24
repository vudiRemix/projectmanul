package dev.projectskins.command;

import java.util.regex.Pattern;

/**
 * Независимый от Minecraft разбор двух аргументов команды. Brigadier получает
 * остаток строки как greedyString: обычный word/string не принимает URL без
 * кавычек из-за двоеточия и слешей. После этого разбор строго требует источник
 * и один никнейм; дополнительные слова не игнорируются.
 */
public record SkinCommandArguments(String source, String nickname) {
    private static final Pattern NICKNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public static SkinCommandArguments parse(String text) {
        if (text == null || text.length() > 2080) {
            throw new IllegalArgumentException("Слишком длинные аргументы команды");
        }
        String[] words = text.strip().split("\\s+");
        if (words.length != 2) {
            throw new IllegalArgumentException("Использование: /projectskins <skin_id или URL> <nickname>");
        }
        if (!NICKNAME.matcher(words[1]).matches()) {
            throw new IllegalArgumentException("Некорректный никнейм игрока");
        }
        return new SkinCommandArguments(words[0], words[1]);
    }
}
