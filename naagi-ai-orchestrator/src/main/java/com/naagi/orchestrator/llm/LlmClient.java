package com.naagi.orchestrator.llm;

import java.util.List;

/**
 * Standardized LLM client interface following the OpenAI/LlamaStack chat completions format.
 * Supports structured messages, tool definitions, and tool calling.
 */
public interface LlmClient {

    /**
     * Send a structured chat completion request and return the full response.
     */
    ChatResponse chat(ChatRequest request);

    /**
     * Legacy convenience method: send a flat prompt with a default system message.
     * Delegates to the structured API.
     */
    default String chat(String prompt, double temperature, int maxTokens) {
        ChatResponse resp = chat(ChatRequest.of(
                List.of(
                        ChatMessage.system("You are a helpful assistant. Follow the user's instructions exactly."),
                        ChatMessage.user(prompt)
                ),
                temperature, maxTokens));
        return resp.content();
    }
}
