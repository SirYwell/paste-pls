package de.sirywell.pastepls.paste;

import de.sirywell.pastepls.config.AppConfig;
import de.sirywell.pastepls.config.PasteProvider;
import java.net.http.HttpClient;

public final class PasteServiceFactory {
    private PasteServiceFactory() {
    }

    public static PasteService create(AppConfig config, HttpClient httpClient) {
        if (config.paste().provider() == PasteProvider.LUCKO) {
            return new LuckoPasteService(httpClient, config.paste(), config.httpRequestTimeout());
        }
        throw new IllegalStateException("Unsupported paste provider: " + config.paste().provider());
    }
}
