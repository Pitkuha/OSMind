package dev.osmind.explainer;

import dev.osmind.anomaly.Anomaly;
import dev.osmind.behavior.ProcessBehaviorProfile;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class AdaptiveExplainer implements Explainer {
    private final Explainer fallback;
    private final LlmClient llmClient;
    private final AiMemoryStore memoryStore;

    public AdaptiveExplainer() {
        this(new TemplateExplainer(), new OllamaLlmClient(), AiMemoryStore.defaultStore());
    }

    public AdaptiveExplainer(Explainer fallback, LlmClient llmClient, AiMemoryStore memoryStore) {
        this.fallback = fallback;
        this.llmClient = llmClient;
        this.memoryStore = memoryStore;
    }

    @Override
    public String explain(ExplanationRequest request) {
        String deterministic = fallback.explain(request);
        String prompt = buildPrompt(request, deterministic);
        LlmResult result = llmClient.generate(prompt);
        if (result.success()) {
            memoryStore.remember("answer", "Q: " + questionOrFallback(request) + "\nA: " + result.text());
            return result.text().trim()
                    + "\n\nAI mode: local LLM + OSMind memory + heuristic evidence. Memory path: "
                    + memoryStore.path();
        }
        return deterministic
                + "\n\nAdaptive AI status: local LLM generation is unavailable, so I used the deterministic reasoning fallback."
                + "\nWhy: " + result.failureReason()
                + "\nLearning: I can still store feedback and operator notes, but I cannot synthesize a non-template answer until a local LLM is running."
                + memorySummary();
    }

    private String buildPrompt(ExplanationRequest request, String deterministic) {
        return """
                You are OSMind, a local macOS system intelligence agent.
                Answer in English unless the user asks in Russian; if the user asks in Russian, answer in Russian.
                Do not invent telemetry. If OSMind cannot observe something, state exactly what is missing and why.
                Use the evidence, anomalies, process profiles, and memory below.
                Be concrete, concise, and diagnostic. Include what you can say, what you cannot say, and next steps.

                USER QUESTION:
                %s

                CURRENT DETERMINISTIC ANALYSIS:
                %s

                ANOMALIES:
                %s

                TOP PROCESS PROFILES:
                %s

                LEARNED MEMORY:
                %s
                """.formatted(
                questionOrFallback(request),
                deterministic,
                anomalies(request.anomalies()),
                profiles(request.profiles()),
                memories()
        );
    }

    private String anomalies(List<Anomaly> anomalies) {
        if (anomalies.isEmpty()) {
            return "- none";
        }
        return anomalies.stream()
                .map(anomaly -> "- " + anomaly.severity() + " " + anomaly.id() + ": " + anomaly.title()
                        + " process=" + anomaly.profile().process().processName()
                        + " pid=" + anomaly.profile().process().pid())
                .collect(Collectors.joining("\n"));
    }

    private String profiles(List<ProcessBehaviorProfile> profiles) {
        if (profiles.isEmpty()) {
            return "- none";
        }
        return profiles.stream()
                .sorted(Comparator.comparingDouble(this::profileScore).reversed())
                .limit(12)
                .map(profile -> "- " + profile.process().processName()
                        + " pid=" + profile.process().pid()
                        + " cpu=" + profile.maxCpuPercent()
                        + " mem=" + profile.maxMemoryPercent()
                        + " writes=" + profile.writeCount()
                        + " connects=" + profile.connectCount()
                        + " cmd=" + profile.process().commandLine())
                .collect(Collectors.joining("\n"));
    }

    private double profileScore(ProcessBehaviorProfile profile) {
        return Math.max(0, profile.maxCpuPercent())
                + profile.connectCount()
                + profile.fileMutationCount()
                + profile.writeCount();
    }

    private String memories() {
        List<AiMemory> memories = memoryStore.recent(8);
        if (memories.isEmpty()) {
            return "- none";
        }
        return memories.stream()
                .map(memory -> "- [" + memory.kind() + "] " + memory.text())
                .collect(Collectors.joining("\n"));
    }

    private String memorySummary() {
        List<AiMemory> memories = memoryStore.recent(3);
        if (memories.isEmpty()) {
            return "\nMemory: no learned notes yet.";
        }
        return "\nMemory considered:\n" + memories.stream()
                .map(memory -> "- [" + memory.kind() + "] " + memory.text())
                .collect(Collectors.joining("\n"));
    }

    private String questionOrFallback(ExplanationRequest request) {
        return request.userQuestion() == null || request.userQuestion().isBlank() ? "(no question provided)" : request.userQuestion();
    }
}
