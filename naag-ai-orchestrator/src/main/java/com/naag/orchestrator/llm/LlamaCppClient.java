package com.naag.orchestrator.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naag.orchestrator.metrics.OrchestratorMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@Slf4j
public class LlamaCppClient implements LlmClient {

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OrchestratorMetrics metrics;

    public LlamaCppClient(
            @Value("${naag.llm.baseUrl}") String baseUrl,
            @Value("${naag.llm.model}") String model,
            ObjectMapper objectMapper,
            OrchestratorMetrics metrics) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String chat(String prompt, double temperature, int maxTokens) {
        long startTime = System.currentTimeMillis();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);
            body.put("stream", false);

            ArrayNode messages = body.putArray("messages");

            messages.addObject()
                    .put("role", "system")
                    .put("content", "You are a helpful assistant. Follow the user's instructions exactly. Output format depends on the task: for tool selection tasks requesting JSON, respond with JSON only; for answering questions naturally, respond with plain text only (no JSON, no function calls, no tool use).");

            messages.addObject()
                    .put("role", "user")
                    .put("content", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new RuntimeException("LLM HTTP " + response.statusCode() + ": " + response.body());
            }

            long llmTime = System.currentTimeMillis() - startTime;
            metrics.recordLlmChatTime(llmTime);
            log.debug("[TIMING] LLM chat: {}ms, promptLen={}", llmTime, prompt.length());

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.at("/choices/0/message/content");
            return content.isTextual() ? content.asText() : response.body();
        } catch (Exception e) {
            log.error("LLM chat failed", e);
            throw new RuntimeException("LLM chat failed", e);
        }
    }
}
