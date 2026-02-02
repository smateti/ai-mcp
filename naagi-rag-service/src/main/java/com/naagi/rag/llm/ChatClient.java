package com.naagi.rag.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * Standardized chat client interface following the OpenAI/LlamaStack chat completions format.
 * Supports structured messages, tool definitions, and streaming.
 */
public interface ChatClient {

    /**
     * Send a structured chat completion request and return the full response.
     */
    ChatResponse chat(ChatRequest request);

    /**
     * Send a structured chat completion request and stream tokens.
     */
    void chatStream(ChatRequest request, Consumer<String> onToken);

    /**
     * Legacy convenience method: send a flat user prompt with a default system message.
     * Delegates to the structured API.
     */
    default String chatOnce(String userPrompt, double temperature, int maxTokens) {
        ChatResponse resp = chat(ChatRequest.of(
                List.of(
                        ChatMessage.system("You are a helpful assistant."),
                        ChatMessage.user(userPrompt)
                ),
                temperature, maxTokens));
        return resp.content();
    }

    /**
     * Legacy convenience method: stream a flat user prompt with a default system message.
     * Delegates to the structured API.
     */
    default void chatStream(String userPrompt, double temperature, int maxTokens, Consumer<String> onToken) {
        chatStream(ChatRequest.stream(
                List.of(
                        ChatMessage.system("You are a helpful assistant."),
                        ChatMessage.user(userPrompt)
                ),
                temperature, maxTokens), onToken);
    }
}
