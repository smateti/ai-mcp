package com.naag.rag.config;

import com.naag.rag.llm.ChatClient;
import com.naag.rag.llm.EmbeddingsClient;
import com.naag.rag.llm.llamacpp.LlamaCppOpenAIChatClient;
import com.naag.rag.llm.openai.OpenAIEmbeddingsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmProviderConfig {

    // llama.cpp with OpenAI-compatible API (default)
    @Bean
    @ConditionalOnProperty(name = "naag.rag.llm.provider", havingValue = "llamacpp-openai", matchIfMissing = true)
    public EmbeddingsClient embeddingsLlamaCppOpenAI(
            @Value("${naag.rag.llama.baseUrl}") String baseUrl,
            @Value("${naag.rag.llama.embedModel}") String model
    ) {
        return new OpenAIEmbeddingsClient(baseUrl, model);
    }

    @Bean
    @ConditionalOnProperty(name = "naag.rag.llm.provider", havingValue = "llamacpp-openai", matchIfMissing = true)
    public ChatClient chatLlamaCppOpenAI(
            @Value("${naag.rag.llama.baseUrl}") String baseUrl,
            @Value("${naag.rag.llama.chatModel}") String model
    ) {
        return new LlamaCppOpenAIChatClient(baseUrl, model);
    }

    // Ollama OpenAI-compatible API
    @Bean
    @ConditionalOnProperty(name = "naag.rag.llm.provider", havingValue = "ollama-openai")
    public EmbeddingsClient embeddingsOllamaOpenAI(
            @Value("${naag.rag.ollama.baseUrl}") String baseUrl,
            @Value("${naag.rag.ollama.embedModel}") String model
    ) {
        return new OpenAIEmbeddingsClient(baseUrl, model);
    }

    @Bean
    @ConditionalOnProperty(name = "naag.rag.llm.provider", havingValue = "ollama-openai")
    public ChatClient chatOllamaOpenAI(
            @Value("${naag.rag.ollama.baseUrl}") String baseUrl,
            @Value("${naag.rag.ollama.chatModel}") String model
    ) {
        return new LlamaCppOpenAIChatClient(baseUrl, model);
    }
}
