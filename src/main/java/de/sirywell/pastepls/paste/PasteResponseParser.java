package de.sirywell.pastepls.paste;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;

public final class PasteResponseParser {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PasteResponseParser() {
    }

    public static String extractKey(String locationHeader, String responseBody) {
        if (locationHeader != null && !locationHeader.isBlank()) {
            return keyFromLocation(locationHeader);
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode keyNode = root.get("key");
            if (keyNode != null && !keyNode.asText().isBlank()) {
                return keyNode.asText();
            }
        } catch (Exception exception) {
            throw new PasteServiceException("Paste service response did not contain a usable key", exception);
        }

        throw new PasteServiceException("Paste service response did not contain a usable key");
    }

    private static String keyFromLocation(String locationHeader) {
        URI location = URI.create(locationHeader);
        String path = location.getPath();
        if (path == null || path.isBlank()) {
            throw new PasteServiceException("Paste service returned an empty Location header");
        }
        int lastSlash = path.lastIndexOf('/');
        String key = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        if (key.isBlank()) {
            throw new PasteServiceException("Paste service returned an empty Location header");
        }
        return key;
    }
}
