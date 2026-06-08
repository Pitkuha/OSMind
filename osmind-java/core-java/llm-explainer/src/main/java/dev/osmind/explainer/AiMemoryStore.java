package dev.osmind.explainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AiMemoryStore {
    private final Path path;

    public AiMemoryStore(Path path) {
        this.path = path;
    }

    public static AiMemoryStore defaultStore() {
        String explicit = System.getenv("OSMIND_AI_MEMORY");
        if (explicit != null && !explicit.isBlank()) {
            return new AiMemoryStore(Path.of(explicit));
        }
        String osmindHome = System.getenv("OSMIND_HOME");
        if (osmindHome != null && !osmindHome.isBlank()) {
            return new AiMemoryStore(Path.of(osmindHome, "ai-memory.jsonl"));
        }
        return new AiMemoryStore(Path.of(System.getProperty("user.home"), ".osmind", "ai-memory.jsonl"));
    }

    public synchronized void remember(String kind, String text) {
        AiMemory memory = new AiMemory(Instant.now(), normalizeKind(kind), text == null ? "" : text.trim());
        if (memory.text().isBlank()) {
            return;
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    path,
                    encode(memory) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public synchronized List<AiMemory> recent(int limit) {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            List<AiMemory> all = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                decode(line).ifPresent(all::add);
            }
            int from = Math.max(0, all.size() - limit);
            return List.copyOf(all.subList(from, all.size()));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public Path path() {
        return path;
    }

    private String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return "note";
        }
        return kind.trim().toLowerCase();
    }

    private String encode(AiMemory memory) {
        return "{\"ts\":\"" + escape(memory.timestamp().toString())
                + "\",\"kind\":\"" + escape(memory.kind())
                + "\",\"text\":\"" + escape(memory.text()) + "\"}";
    }

    private Optional<AiMemory> decode(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String ts = extract(line, "ts");
        String kind = extract(line, "kind");
        String text = extract(line, "text");
        if (ts.isBlank() || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new AiMemory(Instant.parse(ts), kind.isBlank() ? "note" : kind, text));
    }

    private String extract(String line, String key) {
        String marker = "\"" + key + "\":\"";
        int start = line.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < line.length(); i++) {
            char current = line.charAt(i);
            if (escaped) {
                value.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                break;
            } else {
                value.append(current);
            }
        }
        return value.toString();
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
