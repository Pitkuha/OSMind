package dev.osmind.explainer;

public interface LlmClient {
    LlmResult generate(String prompt);
}
