package com.naagi.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrchestrationRequest {
    private String message;
    private String sessionId;
    private String categoryId;
    private boolean useAgent;
    private boolean replyToMode;
    private List<ConversationContext> conversationContext;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationContext {
        private String role;
        private String content;
    }
}
