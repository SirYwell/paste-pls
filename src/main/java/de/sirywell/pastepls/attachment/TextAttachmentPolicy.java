package de.sirywell.pastepls.attachment;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class TextAttachmentPolicy {
    private static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.ofEntries(
        Map.entry("txt", "plain"),
        Map.entry("log", "plain"),
        Map.entry("md", "markdown"),
        Map.entry("markdown", "markdown"),
        Map.entry("json", "json"),
        Map.entry("yaml", "yaml"),
        Map.entry("yml", "yaml"),
        Map.entry("xml", "xml"),
        Map.entry("html", "html"),
        Map.entry("css", "css"),
        Map.entry("js", "javascript"),
        Map.entry("mjs", "javascript"),
        Map.entry("cjs", "javascript"),
        Map.entry("ts", "typescript"),
        Map.entry("java", "java"),
        Map.entry("kt", "kotlin"),
        Map.entry("kts", "kotlin"),
        Map.entry("gradle", "groovy"),
        Map.entry("properties", "properties"),
        Map.entry("toml", "toml"),
        Map.entry("ini", "ini"),
        Map.entry("csv", "csv"),
        Map.entry("sql", "sql"),
        Map.entry("sh", "bash"),
        Map.entry("bash", "bash"),
        Map.entry("zsh", "bash"),
        Map.entry("py", "python"),
        Map.entry("rb", "ruby"),
        Map.entry("go", "go"),
        Map.entry("rs", "rust"),
        Map.entry("c", "c"),
        Map.entry("h", "c"),
        Map.entry("cpp", "cpp"),
        Map.entry("cc", "cpp"),
        Map.entry("hpp", "cpp"),
        Map.entry("cs", "csharp"),
        Map.entry("php", "php")
    );

    private static final Map<String, String> MIME_TO_LANGUAGE = Map.ofEntries(
        Map.entry("application/json", "json"),
        Map.entry("application/xml", "xml"),
        Map.entry("application/x-yaml", "yaml"),
        Map.entry("application/yaml", "yaml"),
        Map.entry("application/javascript", "javascript"),
        Map.entry("application/x-sh", "bash"),
        Map.entry("text/plain", "plain"),
        Map.entry("text/markdown", "markdown"),
        Map.entry("text/html", "html"),
        Map.entry("text/css", "css"),
        Map.entry("text/javascript", "javascript"),
        Map.entry("text/x-java-source", "java"),
        Map.entry("text/x-python", "python"),
        Map.entry("text/x-shellscript", "bash")
    );

    public Optional<AttachmentSelection> classify(String fileName, String contentType, long sizeBytes) {
        if (sizeBytes <= 0) {
            return Optional.empty();
        }

        Optional<String> extensionLanguage = extension(fileName).map(EXTENSION_TO_LANGUAGE::get);
        if (extensionLanguage.isPresent()) {
            return extensionLanguage.map(AttachmentSelection::new);
        }

        if (contentType == null || contentType.isBlank()) {
            return Optional.empty();
        }

        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        String mappedLanguage = MIME_TO_LANGUAGE.get(normalizedContentType);
        if (mappedLanguage != null) {
            return Optional.of(new AttachmentSelection(mappedLanguage));
        }
        if (normalizedContentType.startsWith("text/")) {
            return Optional.of(new AttachmentSelection(normalizedContentType.substring("text/".length())));
        }
        return Optional.empty();
    }

    private static Optional<String> extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }
}
