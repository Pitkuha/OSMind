package dev.osmind.anomaly;

import dev.osmind.behavior.ProcessBehaviorProfile;

import java.util.List;

public interface AnomalyDetector {
    List<Anomaly> detect(List<ProcessBehaviorProfile> profiles);
}
