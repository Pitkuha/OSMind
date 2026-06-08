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
        if (request.anomalies().isEmpty()) {
            return noAnomalyAnswer(request);
        }
        if (isHeatQuestion(request) && request.anomalies().stream().noneMatch(anomaly -> anomaly.id().equals("high-cpu"))) {
            return heatAnswer(request);
        }

        Anomaly top = selectAnomaly(request);
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
                AI mode: local heuristic analyzer plus explanation module. A local LLM backend is planned for the next layer.
                """.formatted(
                questionOrFallback(request),
                top.title(),
                profile.process().processName(),
                profile.process().pid(),
                request.lookback().toMinutes(),
                evidence,
                humanize(top),
                top.recommendation()
        ).trim();
    }

    private String noAnomalyAnswer(ExplanationRequest request) {
        if (isHeatQuestion(request)) {
            return heatAnswer(request);
        }

        String shortAnswer = noAnomalyShortAnswer(request);
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
                Recommendation: If you expected live data, click Collect Live Snapshot or start the macOS collector, then ask again.
                AI mode: local heuristic analyzer plus explanation module. A local LLM backend is planned for the next layer.
                """.formatted(
                questionOrFallback(request),
                shortAnswer,
                request.lookback().toMinutes(),
                request.profiles().size(),
                context
        ).trim();
    }

    private String noAnomalyShortAnswer(ExplanationRequest request) {
        String question = request.userQuestion() == null ? "" : request.userQuestion().toLowerCase(Locale.ROOT);
        if (isHeatQuestion(request)) {
            return "I do not see a clear heat-related process anomaly in the collected CPU samples.";
        }
        if (question.contains("сеть") || question.contains("сетев") || question.contains("traffic") || question.contains("network") || question.contains("tcp")) {
            return "I do not see a network spike in the collected events.";
        }
        if (question.contains("файл") || question.contains("шифр") || question.contains("ransom") || question.contains("file")) {
            return "I do not see suspicious file mutation or ransomware-like behavior in the collected events.";
        }
        if (question.contains("процесс") || question.contains("process") || question.contains("cpu") || question.contains("memory")) {
            return "I do not see a process anomaly in the collected events.";
        }
        return "I do not see a clear anomaly related to this question in the selected time window.";
    }

    private boolean isHeatQuestion(ExplanationRequest request) {
        String question = request.userQuestion() == null ? "" : request.userQuestion().toLowerCase(Locale.ROOT);
        return question.contains("нагре")
                || question.contains("горяч")
                || question.contains("перегрев")
                || question.contains("температур")
                || question.contains("heat")
                || question.contains("hot")
                || question.contains("overheat")
                || question.contains("fan")
                || question.contains("thermal");
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

    private Anomaly selectAnomaly(ExplanationRequest request) {
        String question = request.userQuestion() == null ? "" : request.userQuestion().toLowerCase(Locale.ROOT);
        if (question.contains("сеть") || question.contains("сетев") || question.contains("traffic") || question.contains("network") || question.contains("tcp")) {
            return request.anomalies().stream()
                    .filter(anomaly -> anomaly.id().equals("network-fanout"))
                    .findFirst()
                    .orElseGet(() -> mostSevere(request));
        }
        if (question.contains("файл") || question.contains("шифр") || question.contains("ransom")) {
            return request.anomalies().stream()
                    .filter(anomaly -> anomaly.id().equals("ransomware-like-file-mutation"))
                    .findFirst()
                    .orElseGet(() -> mostSevere(request));
        }
        if (question.contains("скач") || question.contains("curl") || question.contains("tmp") || question.contains("dropper")) {
            return request.anomalies().stream()
                    .filter(anomaly -> anomaly.id().equals("dropper-like-download-execute"))
                    .findFirst()
                    .orElseGet(() -> mostSevere(request));
        }
        if (isHeatQuestion(request)) {
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
}
