package dev.osmind.api;

import dev.osmind.anomaly.Anomaly;
import dev.osmind.schema.Severity;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SentinelMonitor implements AutoCloseable {
    private final SentinelService sentinel;
    private final Duration lookback;
    private final Duration interval;
    private final Severity minimumSeverity;
    private final List<AlertListener> listeners = new CopyOnWriteArrayList<>();
    private final Set<String> emittedAlertKeys = new HashSet<>();
    private volatile boolean running;
    private Thread worker;

    public SentinelMonitor(
            SentinelService sentinel,
            Duration lookback,
            Duration interval,
            Severity minimumSeverity
    ) {
        this.sentinel = sentinel;
        this.lookback = lookback;
        this.interval = interval;
        this.minimumSeverity = minimumSeverity;
    }

    public void addListener(AlertListener listener) {
        listeners.add(listener);
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::runLoop, "osmind-monitor");
        worker.setDaemon(true);
        worker.start();
    }

    public List<Alert> checkOnce() {
        return sentinel.anomalies(lookback).stream()
                .filter(this::passesSeverity)
                .map(this::toAlert)
                .filter(this::isNewAlert)
                .peek(this::notifyListeners)
                .toList();
    }

    @Override
    public synchronized void close() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void runLoop() {
        while (running) {
            checkOnce();
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean passesSeverity(Anomaly anomaly) {
        return anomaly.severity().ordinal() >= minimumSeverity.ordinal();
    }

    private Alert toAlert(Anomaly anomaly) {
        String processName = anomaly.profile().process().processName();
        long pid = anomaly.profile().process().pid();
        String message = processName + " (PID " + pid + "): " + anomaly.title();
        return new Alert(
                anomaly.id() + ":" + pid + ":" + anomaly.profile().lastSeen(),
                Instant.now(),
                anomaly.severity(),
                anomaly.title(),
                message,
                anomaly
        );
    }

    private synchronized boolean isNewAlert(Alert alert) {
        return emittedAlertKeys.add(alert.key());
    }

    private void notifyListeners(Alert alert) {
        for (AlertListener listener : listeners) {
            listener.onAlert(alert);
        }
    }
}
