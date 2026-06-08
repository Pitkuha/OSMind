package dev.osmind.explainer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class OllamaLlmClient implements LlmClient {
    private final URI endpoint;
    private final String model;
    private final HttpClient client;

    public OllamaLlmClient() {
        this(
                URI.create(System.getenv().getOrDefault("OSMIND_LLM_ENDPOINT", "http://localhost:11434/api/generate")),
                System.getenv().getOrDefault("OSMIND_LLM_MODEL", "llama3.1")
        );
    }

    public OllamaLlmClient(URI endpoint, String model) {
        this.endpoint = endpoint;
        this.model = model;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Override
    public LlmResult generate(String prompt) {
        String body = "{\"model\":\"" + escape(model)
                + "\",\"prompt\":\"" + escape(prompt)
                + "\",\"stream\":false}";
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return LlmResult.failure("local LLM returned HTTP " + response.statusCode());
            }
            String text = extractResponse(response.body());
            if (text.isBlank()) {
                return LlmResult.failure("local LLM returned an empty response");
            }
            return LlmResult.success(text);
        } catch (IOException exception) {
            return LlmResult.failure("local LLM is not reachable at " + endpoint);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return LlmResult.failure("local LLM request was interrupted");
        }
    }

    private String extractResponse(String json) {
        String marker = "\"response\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char current = json.charAt(i);
            if (escaped) {
                value.append(switch (current) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> current;
                });
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                break;
            } else {
                value.append(current);
            }
        }
        return value.toString();
    }

    private String escape(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
