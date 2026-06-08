package dev.osmind.cli;

import dev.osmind.anomaly.HeuristicAnomalyDetector;
import dev.osmind.api.Alert;
import dev.osmind.api.ConsoleAlertNotifier;
import dev.osmind.api.MacOsSnapshotCollector;
import dev.osmind.api.MacOsNotificationNotifier;
import dev.osmind.api.SentinelService;
import dev.osmind.behavior.BehaviorEngine;
import dev.osmind.behavior.ProcessBehaviorProfile;
import dev.osmind.explainer.AdaptiveExplainer;
import dev.osmind.explainer.AiMemory;
import dev.osmind.explainer.AiMemoryStore;
import dev.osmind.schema.EventType;
import dev.osmind.schema.OsEvent;
import dev.osmind.schema.ProcessIdentity;
import dev.osmind.storage.EventStore;
import dev.osmind.storage.JsonlEventStore;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SentinelCli {
    private SentinelCli() {
    }

    public static void main(String[] args) {
        if (args.length == 0 || "help".equals(args[0])) {
            printHelp();
            return;
        }

        EventStore store = new JsonlEventStore(defaultStorePath());
        SentinelService sentinel = new SentinelService(
                store,
                new BehaviorEngine(),
                new HeuristicAnomalyDetector(),
                new AdaptiveExplainer()
        );
        switch (args[0]) {
            case "seed-demo" -> seedDemo(sentinel);
            case "seed-network-demo" -> seedDemo(sentinel);
            case "seed-heat-demo" -> seedHeatDemo(sentinel);
            case "clear-demo" -> clearDemo(sentinel);
            case "collect-once" -> collectOnce(sentinel);
            case "ask" -> ask(sentinel, String.join(" ", List.of(args).subList(1, args.length)));
            case "ai-remember" -> remember(args);
            case "ai-memory" -> printMemory();
            case "monitor" -> monitor(sentinel, args);
            case "profile" -> printProfiles(sentinel);
            default -> {
                System.err.println("Unknown command: " + args[0]);
                printHelp();
            }
        }
    }

    private static void ask(SentinelService sentinel, String question) {
        Duration lookback = Duration.ofMinutes(3);
        System.out.println(sentinel.ask(question, lookback));
    }

    private static void printProfiles(SentinelService sentinel) {
        List<ProcessBehaviorProfile> profiles = sentinel.profiles(Duration.ofMinutes(10));
        for (ProcessBehaviorProfile profile : profiles) {
            System.out.printf(
                    "%s pid=%d files=%d writes=%d connects=%d targets=%d cwd=%s%n",
                    profile.process().processName(),
                    profile.process().pid(),
                    profile.fileTargets().size(),
                    profile.writeCount(),
                    profile.connectCount(),
                    profile.networkTargets().size(),
                    profile.process().workingDirectory()
            );
            if (profile.hasCpuTelemetry()) {
                System.out.printf("  cpu=%.1f%% memory=%s%n", profile.maxCpuPercent(), formatPercent(profile.maxMemoryPercent()));
            }
        }
    }

    private static String formatPercent(double value) {
        if (value < 0) {
            return "unknown";
        }
        return String.format(java.util.Locale.ROOT, "%.1f%%", value);
    }

    private static void monitor(SentinelService sentinel, String[] args) {
        boolean once = List.of(args).contains("--once");
        Duration interval = Duration.ofSeconds(readLongOption(args, "--interval-seconds", 30));
        Duration lookback = Duration.ofMinutes(readLongOption(args, "--lookback-minutes", 3));

        try (var monitor = sentinel.createMonitor(lookback, interval)) {
            monitor.addListener(new ConsoleAlertNotifier());
            if (!notificationsDisabled()) {
                monitor.addListener(new MacOsNotificationNotifier(new ConsoleAlertNotifier()));
            }
            if (once) {
                List<Alert> alerts = monitor.checkOnce();
                if (alerts.isEmpty()) {
                    System.out.println("No anomalies detected.");
                }
                return;
            }

            System.out.printf(
                    "OSMind monitor started. interval=%ds lookback=%dm storage=%s%n",
                    interval.toSeconds(),
                    lookback.toMinutes(),
                    defaultStorePath()
            );
            monitor.start();
            Runtime.getRuntime().addShutdownHook(new Thread(monitor::close));
            while (true) {
                Thread.sleep(60_000);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void clearDemo(SentinelService sentinel) {
        int removed = sentinel.clearDemoEvents();
        System.out.println("Removed " + removed + " demo events.");
    }

    private static void collectOnce(SentinelService sentinel) {
        int collected = new MacOsSnapshotCollector(sentinel).collectOnce();
        System.out.println("Collected " + collected + " live process snapshot events.");
    }

    private static void remember(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: sentinel ai-remember \"note to learn\"");
            return;
        }
        String text = String.join(" ", List.of(args).subList(1, args.length));
        AiMemoryStore.defaultStore().remember("operator-note", text);
        System.out.println("Remembered note in " + AiMemoryStore.defaultStore().path());
    }

    private static void printMemory() {
        List<AiMemory> memories = AiMemoryStore.defaultStore().recent(20);
        if (memories.isEmpty()) {
            System.out.println("No AI memory entries yet.");
            return;
        }
        for (AiMemory memory : memories) {
            System.out.printf("%s [%s] %s%n", memory.timestamp(), memory.kind(), memory.text());
        }
    }

    private static long readLongOption(String[] args, String name, long fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return Long.parseLong(args[i + 1]);
            }
        }
        return fallback;
    }

    private static boolean notificationsDisabled() {
        return "true".equalsIgnoreCase(System.getenv("OSMIND_DISABLE_NOTIFICATIONS"));
    }

    private static void seedDemo(SentinelService sentinel) {
        Instant now = Instant.now();
        ProcessIdentity node = new ProcessIdentity(
                1842,
                931,
                "node",
                "/opt/homebrew/bin/node",
                System.getProperty("user.home") + "/dev/parser",
                "node crawler.js"
        );
        for (int i = 0; i < 42; i++) {
            sentinel.ingest(new OsEvent(
                    now.minusSeconds(150 - i),
                    "demo-agent",
                    EventType.CONNECT,
                    node,
                    "203.0.113." + (i % 28) + ":443",
                    0,
                    Map.of("protocol", "tcp")
            ));
        }

        System.out.println("macOS network demo events written to " + defaultStorePath());
    }

    private static void seedHeatDemo(SentinelService sentinel) {
        Instant now = Instant.now();
        ProcessIdentity mds = new ProcessIdentity(
                2751,
                1,
                "mds_stores",
                "/System/Library/Frameworks/CoreServices.framework/Frameworks/Metadata.framework/Support/mds_stores",
                "",
                "mds_stores"
        );
        sentinel.ingest(new OsEvent(
                now,
                "demo-agent",
                EventType.METRIC,
                mds,
                "process-snapshot",
                0,
                Map.of("collector", "demo", "cpuPercent", "91.4", "memoryPercent", "4.2")
        ));
        System.out.println("macOS heat demo events written to " + defaultStorePath());
    }

    private static Path defaultStorePath() {
        String explicitStore = System.getenv("OSMIND_STORE");
        if (explicitStore != null && !explicitStore.isBlank()) {
            return Path.of(explicitStore);
        }
        String osmindHome = System.getenv("OSMIND_HOME");
        if (osmindHome != null && !osmindHome.isBlank()) {
            return Path.of(osmindHome, "events.jsonl");
        }
        return Path.of(System.getProperty("user.home"), ".osmind", "events.jsonl");
    }

    private static void printHelp() {
        System.out.println("""
                OSMind Sentinel

                Commands:
                  sentinel seed-demo
                  sentinel seed-network-demo
                  sentinel seed-heat-demo
                  sentinel clear-demo
                  sentinel collect-once
                  sentinel ai-remember "Chrome is expected to use high CPU during video calls"
                  sentinel ai-memory
                  sentinel ask "Why did my network traffic spike?"
                  sentinel ask "Почему у меня резко вырос сетевой трафик?"
                  sentinel monitor
                  sentinel monitor --once
                  sentinel monitor --interval-seconds 30 --lookback-minutes 3
                  sentinel profile
                """.trim());
    }
}
