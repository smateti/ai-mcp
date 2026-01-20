package com.example.userchat.service;

import com.example.userchat.dto.ChatRequest;
import com.example.userchat.dto.ChatResponse;
import com.example.userchat.entity.ChatSession;
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
    private final ChatHistoryService historyService;
    private final FaqCacheService cacheService;

    public String createSession(String categoryId, String categoryName, String firstQuestion) {
        // Create chat session with auto-generated name
        ChatSession session = historyService.createSession(categoryId, categoryName, firstQuestion);

        // Create backend MCP session
        try {
            String url = mcpChatUrl + "/api/chat/session";
            Map<String, String> response = restTemplate.postForObject(url, null, Map.class);
            String mcpSessionId = response != null ? response.get("sessionId") : null;

            log.info("Created session: {} with MCP session: {}", session.getId(), mcpSessionId);
            return session.getId();
        } catch (Exception e) {
            log.error("Failed to create MCP session", e);
            return session.getId(); // Return local session even if MCP fails
        }
    }

    public ChatResponse sendMessage(ChatRequest request) {
        // Validate sessionId is present
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            log.error("sessionId is required but was null or blank");
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setRole("assistant");
            errorResponse.setContent("Error: sessionId is required. Please create a session first.");
            return errorResponse;
        }

        try {
            // Check cache first for frequently asked questions
            String cachedAnswer = cacheService.tryGetCachedAnswer(
                request.getMessage(),
                request.getCategoryId()
            );

            if (cachedAnswer != null) {
                log.info("Returning cached answer");

                // Save to history
                historyService.saveMessage(request.getSessionId(), "user", request.getMessage(), null);
                historyService.saveMessage(request.getSessionId(), "assistant", cachedAnswer, "cache");

                ChatResponse response = new ChatResponse();
                response.setRole("assistant");
                response.setContent(cachedAnswer);
                response.setMetadata(Map.of("source", "cache"));
                return response;
            }

            // Get enabled tools for the category (for future filtering)
            if (request.getCategoryId() != null) {
                categoryService.getEnabledToolsForCategory(request.getCategoryId());
                categoryService.getEnabledDocumentsForCategory(request.getCategoryId());
            }

            String url = mcpChatUrl + "/api/chat/message";

            Map<String, String> payload = new HashMap<>();
            payload.put("sessionId", request.getSessionId());
            payload.put("message", request.getMessage());
            if (request.getCategoryId() != null) {
                payload.put("categoryId", request.getCategoryId());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(payload, headers);

            ChatResponse response = restTemplate.postForObject(url, entity, ChatResponse.class);

            // Save to history
            historyService.saveMessage(request.getSessionId(), "user", request.getMessage(), null);
            if (response != null) {
                String toolUsed = null;
                if (response.getMetadata() != null && response.getMetadata() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metaMap = (Map<String, Object>) response.getMetadata();
                    Object toolObj = metaMap.get("tool");
                    toolUsed = toolObj != null ? toolObj.toString() : null;
                }
                historyService.saveMessage(
                    request.getSessionId(),
                    "assistant",
                    response.getContent(),
                    toolUsed
                );

                // Cache the answer for future use
                cacheService.cacheQuestionAnswer(
                    request.getMessage(),
                    request.getCategoryId(),
                    response.getContent()
                );
            }

            return response;
        } catch (Exception e) {
            log.error("Failed to send message", e);
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setRole("assistant");
            errorResponse.setContent("Sorry, I encountered an error: " + e.getMessage());
            return errorResponse;
        }
    }
}
