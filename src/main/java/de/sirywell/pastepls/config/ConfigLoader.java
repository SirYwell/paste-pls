package de.sirywell.pastepls.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;

final class ConfigLoader {
    private static final String CONFIG_FILE_ENV = "PASTE_PLS_CONFIG_FILE";
    private static final String CONFIG_FILE_PROPERTY = "paste-pls.config-file";
    private static final Path DEFAULT_CONFIG_FILE = Path.of("paste-pls.properties");

    AppConfig load() {
        Properties fileProperties = loadFileProperties();

        URI pasteApiBaseUrl = normalizeBaseUrl(parseUri(
            "paste.api-base-url",
            value("paste.api-base-url", fileProperties).orElse("https://api.pastes.dev/")
        ));
        URI pastePublicBaseUrl = normalizeBaseUrl(parseUri(
            "paste.public-base-url",
            value("paste.public-base-url", fileProperties).orElseGet(() -> derivePublicBaseUrl(pasteApiBaseUrl).toString())
        ));

        String pasteUserAgent = value("paste.user-agent", fileProperties)
            .orElseGet(() -> defaultUserAgent(pasteApiBaseUrl));
        if (isOfficialPasteApi(pasteApiBaseUrl) && pasteUserAgent.isBlank()) {
            throw new ConfigException("paste.user-agent is required when using api.pastes.dev");
        }

        return new AppConfig(
            require("discord.token", fileProperties),
            optionalLong("discord.guild-id", fileProperties),
            new PasteServiceConfig(
                PasteProvider.parse(value("paste.provider", fileProperties).orElse("lucko")),
                pasteApiBaseUrl,
                pastePublicBaseUrl,
                pasteUserAgent
            ),
            ResponseVisibility.parse(value("discord.response-visibility", fileProperties).orElse("ephemeral")),
            positiveLong("discord.attachment-max-bytes", fileProperties, 1_048_576L),
            Duration.ofSeconds(positiveLong("http.request-timeout-seconds", fileProperties, 20L))
        );
    }

    static URI derivePublicBaseUrl(URI apiBaseUrl) {
        String host = apiBaseUrl.getHost();
        if (host != null && host.startsWith("api.")) {
            return normalizeBaseUrl(rebuildUri(apiBaseUrl, host.substring(4), "/"));
        }

        String path = Optional.ofNullable(apiBaseUrl.getPath()).orElse("/");
        if (path.endsWith("/data/")) {
            return normalizeBaseUrl(rebuildUri(apiBaseUrl, host, path.substring(0, path.length() - "data/".length())));
        }
        if (path.endsWith("/data")) {
            return normalizeBaseUrl(rebuildUri(apiBaseUrl, host, path.substring(0, path.length() - "data".length())));
        }
        return normalizeBaseUrl(rebuildUri(apiBaseUrl, host, path));
    }

    private static URI rebuildUri(URI source, String host, String path) {
        try {
            return new URI(source.getScheme(), source.getUserInfo(), host, source.getPort(), path, null, null);
        } catch (URISyntaxException exception) {
            throw new ConfigException("Invalid URI generated from " + source, exception);
        }
    }

    private static boolean isOfficialPasteApi(URI uri) {
        return "api.pastes.dev".equalsIgnoreCase(uri.getHost());
    }

    private static String defaultUserAgent(URI apiBaseUrl) {
        if (isOfficialPasteApi(apiBaseUrl)) {
            throw new ConfigException("paste.user-agent must be set for api.pastes.dev");
        }
        return "paste-pls/1.0";
    }

    private static URI normalizeBaseUrl(URI uri) {
        String path = Optional.ofNullable(uri.getPath()).orElse("/");
        if (path.isBlank()) {
            path = "/";
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        return rebuildUri(uri, uri.getHost(), path);
    }

    private static URI parseUri(String key, String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new ConfigException(key + " must be an absolute URI");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new ConfigException("Invalid URI for " + key + ": " + value, exception);
        }
    }

    private static long positiveLong(String key, Properties fileProperties, long defaultValue) {
        return value(key, fileProperties)
            .map(raw -> parsePositiveLong(key, raw))
            .orElse(defaultValue);
    }

    private static OptionalLong optionalLong(String key, Properties fileProperties) {
        return value(key, fileProperties)
            .map(raw -> OptionalLong.of(parsePositiveLong(key, raw)))
            .orElseGet(OptionalLong::empty);
    }

    private static long parsePositiveLong(String key, String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value <= 0) {
                throw new ConfigException(key + " must be greater than zero");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new ConfigException(key + " must be a number", exception);
        }
    }

    private static String require(String key, Properties fileProperties) {
        return value(key, fileProperties)
            .filter(candidate -> !candidate.isBlank())
            .orElseThrow(() -> new ConfigException("Missing required configuration: " + key));
    }

    private static Optional<String> value(String key, Properties fileProperties) {
        String systemValue = System.getProperty(systemPropertyKey(key));
        if (systemValue != null) {
            return Optional.of(systemValue.trim());
        }

        String envValue = System.getenv(environmentKey(key));
        if (envValue != null) {
            return Optional.of(envValue.trim());
        }

        String propertyValue = fileProperties.getProperty(key);
        if (propertyValue != null) {
            return Optional.of(propertyValue.trim());
        }

        return Optional.empty();
    }

    private static String systemPropertyKey(String key) {
        return "paste-pls." + key;
    }

    private static String environmentKey(String key) {
        return "PASTE_PLS_" + key.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private static Properties loadFileProperties() {
        Path configFile = configuredPath().orElse(DEFAULT_CONFIG_FILE);
        if (!Files.exists(configFile)) {
            return new Properties();
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(configFile)) {
            properties.load(inputStream);
            return properties;
        } catch (IOException exception) {
            throw new ConfigException("Failed to read config file: " + configFile, exception);
        }
    }

    private static Optional<Path> configuredPath() {
        String systemValue = System.getProperty(CONFIG_FILE_PROPERTY);
        if (systemValue != null && !systemValue.isBlank()) {
            return Optional.of(Path.of(systemValue));
        }

        String envValue = System.getenv(CONFIG_FILE_ENV);
        if (envValue != null && !envValue.isBlank()) {
            return Optional.of(Path.of(envValue));
        }

        return Optional.empty();
    }
}
