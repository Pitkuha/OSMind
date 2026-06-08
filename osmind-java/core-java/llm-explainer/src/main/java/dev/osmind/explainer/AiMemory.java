package dev.osmind.explainer;

import java.time.Instant;

public record AiMemory(
        Instant timestamp,
        String kind,
        String text
) {
}
