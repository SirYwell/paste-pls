package de.sirywell.pastepls.discord;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AttachmentDownloaderTest {
    @Test
    void readsSmallStreams() throws Exception {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(content, AttachmentDownloader.readBounded(new ByteArrayInputStream(content), 5));
    }

    @Test
    void rejectsStreamsThatExceedTheConfiguredLimit() {
        byte[] content = "too-large".getBytes(StandardCharsets.UTF_8);

        assertThrows(
            AttachmentDownloadException.class,
            () -> AttachmentDownloader.readBounded(new ByteArrayInputStream(content), 3)
        );
    }
}
