package dev.osmind.api;

import dev.osmind.anomaly.Anomaly;
import dev.osmind.schema.Severity;

import java.time.Instant;

public record Alert(
        String key,
        Instant createdAt,
        Severity severity,
        String title,
        String message,
        Anomaly anomaly
) {
}
