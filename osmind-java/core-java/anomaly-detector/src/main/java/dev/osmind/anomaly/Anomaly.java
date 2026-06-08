package dev.osmind.anomaly;

import dev.osmind.behavior.ProcessBehaviorProfile;
import dev.osmind.schema.Severity;

import java.util.List;

public record Anomaly(
        String id,
        Severity severity,
        String title,
        ProcessBehaviorProfile profile,
        List<String> evidence,
        String recommendation
) {
}
