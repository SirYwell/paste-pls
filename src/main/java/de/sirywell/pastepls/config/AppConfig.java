package de.sirywell.pastepls.config;

import java.time.Duration;
import java.util.OptionalLong;

public record AppConfig(
    String discordToken,
    OptionalLong developmentGuildId,
    PasteServiceConfig paste,
    ResponseVisibility responseVisibility,
    long attachmentMaxBytes,
    Duration httpRequestTimeout
) {
    public static AppConfig load() {
        return new ConfigLoader().load();
    }
}
