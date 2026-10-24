package dev.projectskins.skin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Проверки границ доверия: команда/пакет не должны открывать произвольный адрес. */
class SkinValidatorTest {
    @Test
    void localSourceKeepsItsId() {
        SkinSource skin = SkinSource.parse("bazuev_2-test");
        assertEquals(SkinSource.Type.LOCAL, skin.type());
        assertEquals("bazuev_2-test", skin.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://imgur.com/AbC123.png",
            "https://i.imgur.com/AbC123.png",
            "HTTPS://I.IMGUR.COM:443/AbC123.png"
    })
    void canonicalizesDirectImgurImages(String url) {
        SkinSource source = SkinSource.parse(url);
        assertEquals(SkinSource.Type.URL, source.type());
        assertEquals("https://i.imgur.com/AbC123.png", source.value());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "http://i.imgur.com/a.png", "ftp://i.imgur.com/a.png",
            "https://imgur.com.example.org/a.png", "https://evilimgur.com/a.png",
            "https://a.imgur.com/a.png", "https://i.imgur.com./a.png",
            "https://i.imgur.com@evil.example/a.png", "https://user@i.imgur.com/a.png",
            "https://i.imgur.com:80/a.png", "https://i.imgur.com:444/a.png",
            "https://i.imgur.com/a.png?download=1", "https://i.imgur.com/a.png#part",
            "https://i.imgur.com/a.png?", "https://i.imgur.com/a.png#",
            "https://i.imgur.com/a.jpg", "https://i.imgur.com/a.PNG",
            "https://imgur.com/a/album.png", "https://i.imgur.com/../a.png",
            "https://i.imgur.com/%61.png", "https://i.imgur.com/a%2epng",
            "https://i.imgur.com/a.png/extra", "https://i.imgur.com/",
            "//i.imgur.com/a.png", "https://127.0.0.1/a.png",
            "https://i.imgur.com\\@evil.example/a.png", " https://i.imgur.com/a.png",
            "https://i.imgur.com/a.png\n", "https://i.imgur.com/a b.png"
    })
    void rejectsUnsafeOrNonDirectUrls(String url) {
        assertThrows(IllegalArgumentException.class, () -> SkinValidator.imgurUri(url));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../skin", "folder/skin", "Skin", "skin.png", "skin space", "скин", "a\\b"})
    void rejectsInvalidLocalNames(String id) {
        assertThrows(IllegalArgumentException.class, () -> SkinValidator.localId(id));
    }

    @Test
    void rejectsOversizedArguments() {
        assertThrows(IllegalArgumentException.class, () -> SkinValidator.localId("a".repeat(65)));
        assertThrows(IllegalArgumentException.class,
                () -> SkinValidator.imgurUri("https://i.imgur.com/" + "a".repeat(2048) + ".png"));
    }

    @Test
    void directConstructionCannotBypassValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new SkinSource(SkinSource.Type.LOCAL, "../../secret"));
        assertThrows(IllegalArgumentException.class,
                () -> new SkinSource(SkinSource.Type.URL, "https://example.org/a.png"));
    }
}
