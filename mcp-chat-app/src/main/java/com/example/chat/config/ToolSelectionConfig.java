package com.example.chat.config;

import com.example.chat.llm.ChatClient;
import com.example.chat.llm.LlamaCppChatClient;
import com.example.chat.llm.OllamaChatClient;
import com.example.chat.llm.OpenAIChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolSelectionConfig {

    @Value("${llm.provider:ollama-openai}")
    private String llmProvider;

    @Value("${llm.base-url:http://localhost:11434}")
    private String llmBaseUrl;

    @Value("${llm.chat-model:llama3.1}")
    private String llmChatModel;

    @Value("${tool.selection.confidence.high-threshold:0.8}")
    private double highConfidenceThreshold;

    @Value("${tool.selection.confidence.low-threshold:0.5}")
    private double lowConfidenceThreshold;

    @Value("${tool.selection.max-alternatives:3}")
    private int maxAlternatives;

    @Value("${tool.selection.parameter-timeout-minutes:5}")
    private int parameterTimeoutMinutes;

    @Bean
    public ChatClient chatClient() {
        return switch (llmProvider.toLowerCase()) {
            case "ollama-native" -> new OllamaChatClient(llmBaseUrl, llmChatModel);
            case "ollama-openai" -> new OpenAIChatClient(llmBaseUrl, llmChatModel);
            case "llamacpp" -> new LlamaCppChatClient(llmBaseUrl, llmChatModel);
            default -> throw new IllegalArgumentException(
                "Unknown LLM provider: " + llmProvider + ". Valid options: ollama-native, ollama-openai, llamacpp"
            );
        };
    }

    @Bean
    public ToolSelectionThresholds toolSelectionThresholds() {
        return new ToolSelectionThresholds(
            highConfidenceThreshold,
            lowConfidenceThreshold,
            maxAlternatives,
            parameterTimeoutMinutes
        );
    }

    public record ToolSelectionThresholds(
        double highThreshold,
        double lowThreshold,
        int maxAlternatives,
        int parameterTimeoutMinutes
    ) {}
}
