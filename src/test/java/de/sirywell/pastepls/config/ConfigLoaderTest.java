package de.sirywell.pastepls.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void useIsolatedConfigPath() {
        System.setProperty("paste-pls.config-file", tempDir.resolve("missing.properties").toString());
    }

    @AfterEach
    void clearProperties() {
        System.clearProperty("paste-pls.config-file");
        System.clearProperty("paste-pls.discord.token");
        System.clearProperty("paste-pls.paste.api-base-url");
        System.clearProperty("paste-pls.paste.user-agent");
        System.clearProperty("paste-pls.discord.response-visibility");
    }

    @Test
    void derivesPublicUrlForOfficialService() {
        assertEquals(
            URI.create("https://pastes.dev/"),
            ConfigLoader.derivePublicBaseUrl(URI.create("https://api.pastes.dev/"))
        );
    }

    @Test
    void derivesPublicUrlForSelfHostedDataPath() {
        assertEquals(
            URI.create("http://localhost:8080/"),
            ConfigLoader.derivePublicBaseUrl(URI.create("http://localhost:8080/data/"))
        );
    }

    @Test
    void loadsRequiredValuesFromSystemProperties() {
        System.setProperty("paste-pls.discord.token", "discord-token");
        System.setProperty("paste-pls.paste.api-base-url", "https://api.pastes.dev/");
        System.setProperty("paste-pls.paste.user-agent", "paste-pls-test");

        AppConfig config = new ConfigLoader().load();

        assertEquals("discord-token", config.discordToken());
        assertEquals(URI.create("https://pastes.dev/"), config.paste().publicBaseUrl());
        assertEquals(ResponseVisibility.EPHEMERAL, config.responseVisibility());
    }

    @Test
    void requiresUserAgentForOfficialService() {
        System.setProperty("paste-pls.discord.token", "discord-token");
        System.setProperty("paste-pls.paste.api-base-url", "https://api.pastes.dev/");

        assertThrows(ConfigException.class, () -> new ConfigLoader().load());
    }
}
