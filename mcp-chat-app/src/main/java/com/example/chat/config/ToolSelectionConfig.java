package com.example.chat.config;

import com.example.chat.llm.ChatClient;
import com.example.chat.llm.LlamaCppChatClient;
import com.example.chat.llm.LlamaCppOpenAIChatClient;
import com.example.chat.llm.OllamaChatClient;
import com.example.chat.llm.OpenAIChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolSelectionConfig {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ToolSelectionConfig.class);

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
        logger.info("Creating ChatClient bean - provider={}, baseUrl={}, model={}", llmProvider, llmBaseUrl, llmChatModel);
        ChatClient client = switch (llmProvider.toLowerCase()) {
            case "ollama-native" -> new OllamaChatClient(llmBaseUrl, llmChatModel);
            case "ollama-openai" -> new OpenAIChatClient(llmBaseUrl, llmChatModel);
            case "llamacpp" -> new LlamaCppChatClient(llmBaseUrl, llmChatModel);
            case "llamacpp-openai" -> new LlamaCppOpenAIChatClient(llmBaseUrl, llmChatModel);
            default -> throw new IllegalArgumentException(
                "Unknown LLM provider: " + llmProvider + ". Valid options: ollama-native, ollama-openai, llamacpp, llamacpp-openai"
            );
        };
        logger.info("Created ChatClient: {}", client.getClass().getSimpleName());
        return client;
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
