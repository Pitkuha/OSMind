package dev.osmind.explainer;

import dev.osmind.anomaly.Anomaly;
import dev.osmind.behavior.ProcessBehaviorProfile;

import java.time.Duration;
import java.util.List;

public record ExplanationRequest(
        String userQuestion,
        Duration lookback,
        List<Anomaly> anomalies,
        List<ProcessBehaviorProfile> profiles
) {
}
