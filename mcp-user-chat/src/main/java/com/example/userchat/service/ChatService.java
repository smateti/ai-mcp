package com.example.userchat.service;

import com.example.userchat.dto.ChatRequest;
import com.example.userchat.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    @Value("${services.mcp-chat}")
    private String mcpChatUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final CategoryService categoryService;

    public String createSession() {
        try {
            String url = mcpChatUrl + "/api/chat/session";
            Map<String, String> response = restTemplate.postForObject(url, null, Map.class);
            return response != null ? response.get("sessionId") : null;
        } catch (Exception e) {
            log.error("Failed to create session", e);
            return null;
        }
    }

    public ChatResponse sendMessage(ChatRequest request) {
        try {
            // Get enabled tools for the category (for future filtering)
            if (request.getCategoryId() != null) {
                categoryService.getEnabledToolsForCategory(request.getCategoryId());
                categoryService.getEnabledDocumentsForCategory(request.getCategoryId());
                // TODO: Pass these to MCP chat for filtering
            }

            String url = mcpChatUrl + "/api/chat/message";

            Map<String, String> payload = new HashMap<>();
            payload.put("sessionId", request.getSessionId());
            payload.put("message", request.getMessage());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

            return restTemplate.postForObject(url, entity, ChatResponse.class);
        } catch (Exception e) {
            log.error("Failed to send message", e);
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setRole("assistant");
            errorResponse.setContent("Sorry, I encountered an error: " + e.getMessage());
            return errorResponse;
        }
    }
}
