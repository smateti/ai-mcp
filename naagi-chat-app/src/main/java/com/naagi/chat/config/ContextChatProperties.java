package com.naagi.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for context-driven chat features:
 * reply-to anchoring and automatic context compaction.
 */
@Data
@Component
@ConfigurationProperties(prefix = "naagi.context-chat")
public class ContextChatProperties {

    private CompactionConfig compaction = new CompactionConfig();
    private ReplyToConfig replyTo = new ReplyToConfig();

    @Data
    public static class CompactionConfig {
        /** Enable automatic context compaction */
        private boolean enabled = true;
        /** Compact when context exceeds this fraction of conversation budget (0.0-1.0) */
        private double threshold = 0.70;
        /** Keep last N messages in full during compaction */
        private int keepRecent = 4;
        /** Maximum tokens for the compaction summary */
        private int maxSummaryTokens = 400;
        /** Approximate max conversation tokens before compaction triggers */
        private int maxConversationTokens = 3000;
    }

    @Data
    public static class ReplyToConfig {
        /** Enable reply-to anchoring in chat UI */
        private boolean enabled = true;
        /** Include session summary in reply-to context */
        private boolean includeSessionSummary = true;
    }
}
