package de.sirywell.pastepls.config;

public enum PasteProvider {
    LUCKO;

    public static PasteProvider parse(String value) {
        return switch (value.trim().toLowerCase()) {
            case "lucko" -> LUCKO;
            default -> throw new ConfigException("Unsupported paste.provider: " + value);
        };
    }
}
