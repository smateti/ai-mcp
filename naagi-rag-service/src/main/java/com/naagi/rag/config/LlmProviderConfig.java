package com.naagi.rag.config;

import com.naagi.rag.llm.ChatClient;
import com.naagi.rag.llm.EmbeddingsClient;
import com.naagi.rag.llm.llamacpp.LlamaCppOpenAIChatClient;
import com.naagi.rag.llm.openai.OpenAIEmbeddingsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmProviderConfig {

    // llama.cpp with OpenAI-compatible API (default)
    @Bean
    @ConditionalOnProperty(name = "naagi.rag.llm.provider", havingValue = "llamacpp-openai", matchIfMissing = true)
    public EmbeddingsClient embeddingsLlamaCppOpenAI(
            @Value("${naagi.rag.llama.baseUrl}") String baseUrl,
            @Value("${naagi.rag.llama.embedModel}") String model
    ) {
        return new OpenAIEmbeddingsClient(baseUrl, model);
    }

    @Bean
    @ConditionalOnProperty(name = "naagi.rag.llm.provider", havingValue = "llamacpp-openai", matchIfMissing = true)
    public ChatClient chatLlamaCppOpenAI(
            @Value("${naagi.rag.llama.baseUrl}") String baseUrl,
            @Value("${naagi.rag.llama.chatModel}") String model
    ) {
        return new LlamaCppOpenAIChatClient(baseUrl, model);
    }

    // Ollama OpenAI-compatible API
    @Bean
    @ConditionalOnProperty(name = "naagi.rag.llm.provider", havingValue = "ollama-openai")
    public EmbeddingsClient embeddingsOllamaOpenAI(
            @Value("${naagi.rag.ollama.baseUrl}") String baseUrl,
            @Value("${naagi.rag.ollama.embedModel}") String model
    ) {
        return new OpenAIEmbeddingsClient(baseUrl, model);
    }

    @Bean
    @ConditionalOnProperty(name = "naagi.rag.llm.provider", havingValue = "ollama-openai")
    public ChatClient chatOllamaOpenAI(
            @Value("${naagi.rag.ollama.baseUrl}") String baseUrl,
            @Value("${naagi.rag.ollama.chatModel}") String model
    ) {
        return new LlamaCppOpenAIChatClient(baseUrl, model);
    }
}
