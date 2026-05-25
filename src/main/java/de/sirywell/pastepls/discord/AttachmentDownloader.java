package de.sirywell.pastepls.discord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

public final class AttachmentDownloader {
    private final HttpClient httpClient;
    private final Duration timeout;
    private final String userAgent;

    public AttachmentDownloader(HttpClient httpClient, Duration timeout, String userAgent) {
        this.httpClient = httpClient;
        this.timeout = timeout;
        this.userAgent = userAgent;
    }

    public CompletableFuture<byte[]> download(URI uri, long maxBytes) {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("User-Agent", userAgent)
            .GET()
            .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
            .thenApply(response -> readBody(uri, maxBytes, response));
    }

    private byte[] readBody(URI uri, long maxBytes, HttpResponse<InputStream> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AttachmentDownloadException(
                "Downloading " + uri + " failed with HTTP " + response.statusCode()
            );
        }

        OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
        if (contentLength.isPresent() && contentLength.getAsLong() > maxBytes) {
            throw new AttachmentDownloadException("Attachment exceeds configured size limit");
        }

        try (InputStream inputStream = response.body()) {
            return readBounded(inputStream, maxBytes);
        } catch (IOException exception) {
            throw new AttachmentDownloadException("Failed to read attachment from " + uri, exception);
        }
    }

    static byte[] readBounded(InputStream inputStream, long maxBytes) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long totalRead = 0;
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            totalRead += bytesRead;
            if (totalRead > maxBytes) {
                throw new AttachmentDownloadException("Attachment exceeds configured size limit");
            }
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toByteArray();
    }
}
