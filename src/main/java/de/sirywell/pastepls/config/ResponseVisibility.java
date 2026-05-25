package de.sirywell.pastepls.config;

public enum ResponseVisibility {
    EPHEMERAL(true),
    PUBLIC(false);

    private final boolean ephemeral;

    ResponseVisibility(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }

    public boolean ephemeral() {
        return ephemeral;
    }

    public static ResponseVisibility parse(String value) {
        return switch (value.trim().toLowerCase()) {
            case "ephemeral" -> EPHEMERAL;
            case "public" -> PUBLIC;
            default -> throw new ConfigException("Unsupported discord.response-visibility: " + value);
        };
    }
}
