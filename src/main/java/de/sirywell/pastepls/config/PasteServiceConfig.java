package de.sirywell.pastepls.config;

import java.net.URI;

public record PasteServiceConfig(
    PasteProvider provider,
    URI apiBaseUrl,
    URI publicBaseUrl,
    String userAgent
) {
}
