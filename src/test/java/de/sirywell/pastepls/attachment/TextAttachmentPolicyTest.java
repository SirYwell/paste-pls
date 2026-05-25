package de.sirywell.pastepls.attachment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextAttachmentPolicyTest {
    private final TextAttachmentPolicy policy = new TextAttachmentPolicy();

    @Test
    void prefersLanguageFromKnownExtension() {
        AttachmentSelection selection = policy.classify("script.kts", null, 32).orElseThrow();

        assertEquals("kotlin", selection.language());
    }

    @Test
    void fallsBackToTextMimeTypes() {
        AttachmentSelection selection = policy.classify("README", "text/markdown", 32).orElseThrow();

        assertEquals("markdown", selection.language());
    }

    @Test
    void rejectsUnsupportedBinaryContentTypes() {
        assertTrue(policy.classify("archive.bin", "application/octet-stream", 32).isEmpty());
    }

    @Test
    void rejectsEmptyAttachments() {
        assertFalse(policy.classify("notes.txt", "text/plain", 0).isPresent());
    }
}
