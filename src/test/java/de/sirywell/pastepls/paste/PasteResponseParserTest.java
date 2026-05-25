package de.sirywell.pastepls.paste;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PasteResponseParserTest {
    @Test
    void extractsKeyFromAbsoluteLocationHeader() {
        assertEquals("abc123", PasteResponseParser.extractKey("https://api.pastes.dev/abc123", ""));
    }

    @Test
    void extractsKeyFromRelativeLocationHeader() {
        assertEquals("abc123", PasteResponseParser.extractKey("/abc123", ""));
    }

    @Test
    void fallsBackToJsonResponseBody() {
        assertEquals("body-key", PasteResponseParser.extractKey(null, "{\"key\":\"body-key\"}"));
    }

    @Test
    void failsWhenNeitherLocationNorJsonKeyIsPresent() {
        assertThrows(PasteServiceException.class, () -> PasteResponseParser.extractKey(null, "{}"));
    }
}
