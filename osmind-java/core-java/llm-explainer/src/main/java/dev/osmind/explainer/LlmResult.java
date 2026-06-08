package dev.osmind.explainer;

public record LlmResult(
        boolean success,
        String text,
        String failureReason
) {
    public static LlmResult success(String text) {
        return new LlmResult(true, text, "");
    }

    public static LlmResult failure(String reason) {
        return new LlmResult(false, "", reason);
    }
}
