package dev.osmind.schema;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record OsEvent(
        Instant timestamp,
        String sourceAgent,
        EventType type,
        ProcessIdentity process,
        String target,
        long bytes,
        Map<String, String> attributes
) {
    public OsEvent {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(sourceAgent, "sourceAgent");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(process, "process");
        target = target == null ? "" : target;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static OsEvent now(String sourceAgent, EventType type, ProcessIdentity process, String target) {
        return new OsEvent(Instant.now(), sourceAgent, type, process, target, 0, Map.of());
    }
}
