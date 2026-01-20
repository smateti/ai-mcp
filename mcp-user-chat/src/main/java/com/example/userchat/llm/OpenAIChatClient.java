package com.example.userchat.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Component
public class OpenAIChatClient {

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAIChatClient(
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.chat-model}") String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newBuilder().build();
        this.objectMapper = new ObjectMapper();
    }

    public String chat(String systemPrompt, String userPrompt) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0.7);
            body.put("max_tokens", 100);
            body.put("stream", false);

            var messages = body.putArray("messages");
            messages.addObject()
                    .put("role", "system")
                    .put("content", systemPrompt);
            messages.addObject()
                    .put("role", "user")
                    .put("content", userPrompt);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            return root.at("/choices/0/message/content").asText();

        } catch (Exception e) {
            throw new RuntimeException("Failed to call LLM", e);
        }
    }

    public String generateChatName(String firstQuestion) {
        String systemPrompt = "Generate a short, descriptive chat title (3-6 words) based on the first question. Respond with ONLY the title, no quotes or explanation.";
        return chat(systemPrompt, firstQuestion);
    }
}
