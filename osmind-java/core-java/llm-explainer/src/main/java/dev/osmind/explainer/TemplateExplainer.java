package dev.osmind.explainer;

import dev.osmind.anomaly.Anomaly;
import dev.osmind.behavior.ProcessBehaviorProfile;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class TemplateExplainer implements Explainer {
    @Override
    public String explain(ExplanationRequest request) {
        QuestionIntent intent = QuestionClassifier.classify(request.userQuestion());
        if (request.anomalies().isEmpty()) {
            return noAnomalyAnswer(request, intent);
        }
        if (intent == QuestionIntent.HEAT && request.anomalies().stream().noneMatch(anomaly -> anomaly.id().equals("high-cpu"))) {
            return heatAnswer(request);
        }

        Anomaly top = selectAnomaly(request, intent);
        ProcessBehaviorProfile profile = top.profile();

        String evidence = top.evidence().stream()
                .map(item -> "- " + item)
                .collect(Collectors.joining(System.lineSeparator()));

        return """
                Question: %s

                Short answer: %s.

                Process %s (PID %d) showed a suspicious profile during the last %d minutes.

                Evidence:
                %s

                Explanation: %s
                Recommendation: %s
                Limits: %s
                AI mode: local heuristic analyzer plus explanation module. A local LLM backend is planned for the next layer.
                """.formatted(
                questionOrFallback(request),
                top.title(),
                profile.process().processName(),
                profile.process().pid(),
                request.lookback().toMinutes(),
                evidence,
                humanize(top),
                top.recommendation(),
                limitsForIntent(intent, CapabilityReport.from(request.profiles()))
        ).trim();
    }

    private String noAnomalyAnswer(ExplanationRequest request, QuestionIntent intent) {
        if (intent == QuestionIntent.HEAT) {
            return heatAnswer(request);
        }

        CapabilityReport capabilities = CapabilityReport.from(request.profiles());
        String shortAnswer = noAnomalyShortAnswer(intent);
        String context = request.profiles().isEmpty()
                ? "I did not have any process profiles to evaluate in this time window. Use Collect Live Snapshot, start the native collector, or load demo data first."
                : "I evaluated " + request.profiles().size() + " observed process profile(s) in this time window.";

        return """
                Question: %s

                Short answer: %s

                Evidence:
                - lookback window: last %d minutes
                - observed profiles: %d
                - detected anomalies: 0

                Explanation: %s This does not prove the system is completely clean; it only means the collected events do not cross the current anomaly thresholds.
                What I can say: %s
                What I cannot say yet: %s
                Recommendation: %s
                AI mode: local heuristic analyzer plus explanation module. A local LLM backend is planned for the next layer.
                """.formatted(
                questionOrFallback(request),
                shortAnswer,
                request.lookback().toMinutes(),
                request.profiles().size(),
                context,
                canSay(intent, capabilities),
                cannotSay(intent, capabilities),
                recommendationFor(intent, capabilities)
        ).trim();
    }

    private String noAnomalyShortAnswer(QuestionIntent intent) {
        return switch (intent) {
            case NETWORK -> "I do not see a network spike in the collected events.";
            case FILE_ACTIVITY -> "I do not see suspicious file mutation or ransomware-like behavior in the collected events.";
            case DROPPER -> "I do not see a download-and-execute chain in the collected events.";
            case PROCESS_HEALTH -> "I do not see a process anomaly in the collected events.";
            case SECURITY -> "I do not see behavior that crosses the current security anomaly thresholds.";
            case UNKNOWN -> "I do not see a clear anomaly related to this question in the selected time window.";
            case HEAT -> "I do not see a clear heat-related process anomaly in the collected CPU samples.";
        };
    }

    private String heatAnswer(ExplanationRequest request) {
        List<ProcessBehaviorProfile> cpuProfiles = request.profiles().stream()
                .filter(ProcessBehaviorProfile::hasCpuTelemetry)
                .sorted(Comparator.comparingDouble(ProcessBehaviorProfile::maxCpuPercent).reversed())
                .limit(5)
                .toList();
        String topCpu = cpuProfiles.isEmpty()
                ? "- no CPU telemetry is available in the current samples"
                : cpuProfiles.stream()
                .map(profile -> "- " + profile.process().processName()
                        + " (PID " + profile.process().pid() + "): CPU "
                        + String.format(Locale.ROOT, "%.1f%%", profile.maxCpuPercent())
                        + ", memory " + formatPercent(profile.maxMemoryPercent()))
                .collect(Collectors.joining(System.lineSeparator()));

        String shortAnswer = cpuProfiles.stream().findFirst()
                .filter(profile -> profile.maxCpuPercent() >= 30)
                .map(profile -> "The laptop may be heating because " + profile.process().processName()
                        + " is currently the highest CPU user in the collected samples.")
                .orElse("I cannot identify a CPU-heavy process from the collected samples.");

        return """
                Question: %s

                Short answer: %s

                Evidence:
                - lookback window: last %d minutes
                - observed profiles: %d
                - profiles with CPU telemetry: %d
                - detected high-CPU anomalies: 0

                Top CPU candidates:
                %s

                Explanation: Laptop heat is usually caused by sustained CPU/GPU load, charging, poor ventilation, or a thermal sensor condition. OSMind currently sees process CPU/MEM samples, but it does not yet read macOS thermal sensors or GPU energy counters.
                What I can say: I can compare process CPU and memory samples that reached OSMind.
                What I cannot say yet: I cannot read direct temperature sensors, fan RPM, battery charging heat, or GPU energy counters in this build.
                Recommendation: Click Collect Live Snapshot and ask again while the laptop is hot. If top CPU remains low, the cause may be charging, ventilation, GPU load, or a thermal issue outside the current collector.
                AI mode: local heuristic analyzer plus explanation module. A local LLM backend is planned for the next layer.
                """.formatted(
                questionOrFallback(request),
                shortAnswer,
                request.lookback().toMinutes(),
                request.profiles().size(),
                cpuProfiles.size(),
                topCpu
        ).trim();
    }

    private String formatPercent(double value) {
        if (value < 0) {
            return "unknown";
        }
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private String questionOrFallback(ExplanationRequest request) {
        return request.userQuestion() == null || request.userQuestion().isBlank() ? "(no question provided)" : request.userQuestion();
    }

    private Anomaly selectAnomaly(ExplanationRequest request, QuestionIntent intent) {
        if (intent == QuestionIntent.NETWORK) {
            return request.anomalies().stream()
                    .filter(anomaly -> anomaly.id().equals("network-fanout"))
                    .findFirst()
                    .orElseGet(() -> mostSevere(request));
        }
        if (intent == QuestionIntent.FILE_ACTIVITY) {
            return request.anomalies().stream()
                    .filter(anomaly -> anomaly.id().equals("ransomware-like-file-mutation"))
                    .findFirst()
                    .orElseGet(() -> mostSevere(request));
        }
        if (intent == QuestionIntent.DROPPER) {
            return request.anomalies().stream()
                    .filter(anomaly -> anomaly.id().equals("dropper-like-download-execute"))
                    .findFirst()
                    .orElseGet(() -> mostSevere(request));
        }
        if (intent == QuestionIntent.HEAT || intent == QuestionIntent.PROCESS_HEALTH) {
            return request.anomalies().stream()
                    .filter(anomaly -> anomaly.id().equals("high-cpu"))
                    .findFirst()
                    .orElseGet(() -> mostSevere(request));
        }
        return mostSevere(request);
    }

    private Anomaly mostSevere(ExplanationRequest request) {
        return request.anomalies().stream()
                .max(Comparator.comparing(anomaly -> anomaly.severity().ordinal()))
                .orElseThrow();
    }

    private String humanize(Anomaly anomaly) {
        return switch (anomaly.id()) {
            case "network-fanout" -> "The process is actively reaching out to the network. If it was started from a project directory, it may be a crawler, parser, test runner, or sync job rather than macOS system activity.";
            case "dropper-like-download-execute" -> "The chain resembles dropper behavior: a scripting runtime starts a downloader, places a file in a temporary directory, makes it executable, and prepares it for launch.";
            case "ransomware-like-file-mutation" -> "The pattern resembles ransomware: many different files are opened and modified quickly, which can match mass encryption or overwrite behavior.";
            case "high-cpu" -> "High sustained CPU usage is a common reason for laptop heat and fan activity. OSMind does not yet read thermal sensors, so this is a process-level explanation rather than a direct temperature reading.";
            default -> "The behavior differs from the normal profile and needs manual review.";
        };
    }

    private String canSay(QuestionIntent intent, CapabilityReport capabilities) {
        if (!capabilities.hasProfiles()) {
            return "I can only say that no recent profiles were available to analyze.";
        }
        return switch (intent) {
            case NETWORK -> capabilities.hasNetworkEvents()
                    ? "I can compare collected connection events and identify network fan-out."
                    : "I can see process profiles, but no network connection events are present in this window.";
            case FILE_ACTIVITY -> capabilities.hasFileEvents()
                    ? "I can compare collected file open/write/chmod/unlink events."
                    : "I can see process profiles, but no file activity events are present in this window.";
            case PROCESS_HEALTH, HEAT -> capabilities.hasCpuTelemetry()
                    ? "I can compare CPU and memory samples for " + capabilities.cpuProfileCount() + " process profile(s)."
                    : "I can see process profiles, but they do not include CPU telemetry in this window.";
            case DROPPER, SECURITY, UNKNOWN -> "I can compare collected process, file, network, and CPU indicators against the current heuristic rules.";
        };
    }

    private String cannotSay(QuestionIntent intent, CapabilityReport capabilities) {
        return switch (intent) {
            case HEAT -> "I cannot read direct temperature sensors, fan RPM, charging heat, or GPU counters yet.";
            case NETWORK -> "I cannot prove that all network traffic is benign without live native network telemetry and destination reputation.";
            case FILE_ACTIVITY -> "I cannot inspect file contents or prove encryption without deeper file telemetry.";
            case DROPPER -> "I cannot prove intent; I can only detect download, permission, and execution patterns.";
            case PROCESS_HEALTH -> "I cannot yet sample kernel pressure, GPU load, or full energy impact.";
            case SECURITY -> "I cannot provide a malware verdict without signatures, reputation, and deeper native telemetry.";
            case UNKNOWN -> capabilities.hasProfiles()
                    ? "I may not have classified the question correctly; the current AI layer is heuristic, not a full LLM."
                    : "I cannot analyze a system state without collected events.";
        };
    }

    private String recommendationFor(QuestionIntent intent, CapabilityReport capabilities) {
        if (!capabilities.hasProfiles()) {
            return "Click Collect Live Snapshot or start the macOS collector, then ask again.";
        }
        return switch (intent) {
            case HEAT -> "Collect a live snapshot while the laptop is hot. If CPU remains low, check charging, ventilation, and GPU-heavy apps.";
            case NETWORK -> "Start the native collector or load network telemetry, then ask again while the traffic spike is happening.";
            case FILE_ACTIVITY -> "Start the Endpoint Security collector for file events, then repeat the action you want to inspect.";
            case DROPPER -> "Start the Endpoint Security collector before running installers or scripts you want OSMind to inspect.";
            case PROCESS_HEALTH -> "Collect another live snapshot during the slowdown and compare the top CPU/memory profiles.";
            case SECURITY -> "Use the native collector for richer telemetry; do not treat a no-anomaly answer as a clean bill of health.";
            case UNKNOWN -> "Rephrase the question with a target such as network, files, heat, CPU, memory, or suspicious process.";
        };
    }

    private String limitsForIntent(QuestionIntent intent, CapabilityReport capabilities) {
        return cannotSay(intent, capabilities);
    }
}
