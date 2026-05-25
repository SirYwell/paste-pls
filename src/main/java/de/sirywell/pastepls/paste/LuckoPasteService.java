package de.sirywell.pastepls.paste;

import de.sirywell.pastepls.config.PasteServiceConfig;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class LuckoPasteService implements PasteService {
    private final HttpClient httpClient;
    private final PasteServiceConfig config;
    private final Duration timeout;

    public LuckoPasteService(HttpClient httpClient, PasteServiceConfig config, Duration timeout) {
        this.httpClient = httpClient;
        this.config = config;
        this.timeout = timeout;
    }

    @Override
    public CompletableFuture<PasteResult> upload(PasteUpload upload) {
        HttpRequest request = HttpRequest.newBuilder(config.apiBaseUrl().resolve("post"))
            .timeout(timeout)
            .header("Accept", "application/json")
            .header("User-Agent", config.userAgent())
            .header("Content-Type", "text/" + upload.language())
            .POST(HttpRequest.BodyPublishers.ofByteArray(upload.content()))
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new PasteServiceException(
                        "Paste upload failed with HTTP " + response.statusCode() + ": " + response.body()
                    );
                }

                String location = response.headers().firstValue("Location").orElse(null);
                String key = PasteResponseParser.extractKey(location, response.body());
                return new PasteResult(key, config.publicBaseUrl().resolve(key));
            });
    }
}
