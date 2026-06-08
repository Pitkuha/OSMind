package dev.osmind.storage;

import dev.osmind.schema.EventType;
import dev.osmind.schema.OsEvent;
import dev.osmind.schema.ProcessIdentity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

final class JsonlEventStoreCodec {
    private JsonlEventStoreCodec() {
    }

    static String encode(OsEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("ts", event.timestamp().toString());
        fields.put("agent", event.sourceAgent());
        fields.put("type", event.type().name());
        fields.put("pid", Long.toString(event.process().pid()));
        fields.put("ppid", Long.toString(event.process().ppid()));
        fields.put("name", event.process().processName());
        fields.put("exe", event.process().executablePath());
        fields.put("cwd", event.process().workingDirectory());
        fields.put("cmd", event.process().commandLine());
        fields.put("target", event.target());
        fields.put("bytes", Long.toString(event.bytes()));
        fields.put("attrs", event.attributes().entrySet().stream()
                .map(entry -> escape(entry.getKey()) + "=" + escape(entry.getValue()))
                .collect(Collectors.joining("&")));
        return fields.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + escape(entry.getValue()) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    static Optional<OsEvent> decode(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> fields = parseFlatJsonObject(line);
        ProcessIdentity process = new ProcessIdentity(
                parseLong(fields.get("pid")),
                parseLong(fields.get("ppid")),
                fields.getOrDefault("name", "unknown"),
                fields.getOrDefault("exe", ""),
                fields.getOrDefault("cwd", ""),
                fields.getOrDefault("cmd", "")
        );
        return Optional.of(new OsEvent(
                Instant.parse(fields.get("ts")),
                fields.getOrDefault("agent", "unknown"),
                EventType.valueOf(fields.get("type")),
                process,
                fields.getOrDefault("target", ""),
                parseLong(fields.get("bytes")),
                parseAttributes(fields.getOrDefault("attrs", ""))
        ));
    }

    private static Map<String, String> parseFlatJsonObject(String line) {
        Map<String, String> result = new LinkedHashMap<>();
        String body = line.trim();
        if (body.startsWith("{")) {
            body = body.substring(1);
        }
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1);
        }
        String[] pairs = body.split("\",\"");
        for (String pair : pairs) {
            String normalized = pair.replaceFirst("^\"", "").replaceFirst("\"$", "");
            int separator = normalized.indexOf("\":\"");
            if (separator > 0) {
                String key = normalized.substring(0, separator);
                String value = normalized.substring(separator + 3);
                result.put(key, unescape(value));
            }
        }
        return result;
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Long.parseLong(value);
    }

    private static Map<String, String> parseAttributes(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        Map<String, String> attrs = new LinkedHashMap<>();
        for (String pair : value.split("&")) {
            int separator = pair.indexOf('=');
            if (separator > 0) {
                attrs.put(unescape(pair.substring(0, separator)), unescape(pair.substring(separator + 1)));
            }
        }
        return attrs;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
