package dev.osmind.api;

import dev.osmind.anomaly.Anomaly;
import dev.osmind.anomaly.AnomalyDetector;
import dev.osmind.behavior.BehaviorEngine;
import dev.osmind.behavior.ProcessBehaviorProfile;
import dev.osmind.explainer.Explainer;
import dev.osmind.explainer.ExplanationRequest;
import dev.osmind.schema.OsEvent;
import dev.osmind.storage.EventStore;

import java.time.Duration;
import java.util.List;

public final class SentinelService {
    private final EventStore store;
    private final BehaviorEngine behaviorEngine;
    private final AnomalyDetector anomalyDetector;
    private final Explainer explainer;

    public SentinelService(
            EventStore store,
            BehaviorEngine behaviorEngine,
            AnomalyDetector anomalyDetector,
            Explainer explainer
    ) {
        this.store = store;
        this.behaviorEngine = behaviorEngine;
        this.anomalyDetector = anomalyDetector;
        this.explainer = explainer;
    }

    public void ingest(OsEvent event) {
        store.append(event);
    }

    public int clearDemoEvents() {
        return store.removeIf(event -> event.sourceAgent().contains("demo"));
    }

    public List<ProcessBehaviorProfile> profiles(Duration lookback) {
        return behaviorEngine.buildProfiles(store.last(lookback));
    }

    public List<Anomaly> anomalies(Duration lookback) {
        return anomalyDetector.detect(profiles(lookback));
    }

    public String ask(String question, Duration lookback) {
        List<ProcessBehaviorProfile> currentProfiles = profiles(lookback);
        return explainer.explain(new ExplanationRequest(
                question,
                lookback,
                anomalyDetector.detect(currentProfiles),
                currentProfiles
        ));
    }

    public SentinelMonitor createMonitor(Duration lookback, Duration interval) {
        return new SentinelMonitor(this, lookback, interval, dev.osmind.schema.Severity.MEDIUM);
    }
}
