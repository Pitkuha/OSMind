package dev.osmind.explainer;

import dev.osmind.anomaly.Anomaly;
import dev.osmind.behavior.ProcessBehaviorProfile;

import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;

public final class TemplateExplainer implements Explainer {
    @Override
    public String explain(ExplanationRequest request) {
        if (request.anomalies().isEmpty()) {
            return """
                    Question: %s

                    Short answer: I do not see a clear anomaly in the selected time window.

                    Explanation: This does not prove the system is completely clean, but the collected events do not show mass file mutation, dropper behavior, or unusual network fan-out.
                    AI mode: local heuristic analyzer plus explanation module. A local LLM backend is planned for the next layer.
                    """.formatted(questionOrFallback(request)).trim();
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
            default -> "The behavior differs from the normal profile and needs manual review.";
        };
    }
}
