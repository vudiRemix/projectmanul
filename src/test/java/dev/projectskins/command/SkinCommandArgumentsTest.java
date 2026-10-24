package dev.projectskins.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Проверяет разбор реальных вариантов команды, особенно URL без кавычек. */
class SkinCommandArgumentsTest {
    @Test
    void acceptsUnquotedUrlAndNickname() {
        SkinCommandArguments parsed = SkinCommandArguments.parse("https://i.imgur.com/Ab123.png DED0KOT");
        assertEquals("https://i.imgur.com/Ab123.png", parsed.source());
        assertEquals("DED0KOT", parsed.nickname());
    }

    @Test
    void acceptsLocalNameAndWhitespace() {
        assertEquals(new SkinCommandArguments("bazuev", "DED0KOT"),
                SkinCommandArguments.parse("  bazuev   DED0KOT  "));
    }

    @Test
    void refusesMissingOrExtraArguments() {
        assertThrows(IllegalArgumentException.class, () -> SkinCommandArguments.parse(""));
        assertThrows(IllegalArgumentException.class, () -> SkinCommandArguments.parse("bazuev"));
        assertThrows(IllegalArgumentException.class, () -> SkinCommandArguments.parse("bazuev DED0KOT extra"));
    }

    @Test
    void refusesInvalidPlayerNamesAndOversizedInput() {
        assertThrows(IllegalArgumentException.class, () -> SkinCommandArguments.parse("bazuev @a"));
        assertThrows(IllegalArgumentException.class, () -> SkinCommandArguments.parse("bazuev player-name"));
        assertThrows(IllegalArgumentException.class,
                () -> SkinCommandArguments.parse("a".repeat(2081) + " DED0KOT"));
    }
}
