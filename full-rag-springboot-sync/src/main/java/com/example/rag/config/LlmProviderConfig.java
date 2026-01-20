package com.example.rag.config;

import com.example.rag.llama.LlamaChatClient;
import com.example.rag.llama.LlamaCppOpenAIChatClient;
import com.example.rag.llama.LlamaEmbeddingsClient;
import com.example.rag.llm.ChatClient;
import com.example.rag.llm.EmbeddingsClient;
import com.example.rag.ollama.OllamaChatClient;
import com.example.rag.ollama.OllamaEmbeddingsClient;
import com.example.rag.openai.OpenAIChatClient;
import com.example.rag.openai.OpenAIEmbeddingsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmProviderConfig {

  // Ollama Native API (original format: /api/chat and /api/embed)
  @Bean
  @ConditionalOnProperty(name = "rag.llm.provider", havingValue = "ollama-native")
  public EmbeddingsClient embeddingsOllamaNative(
      @Value("${rag.ollama.baseUrl}") String baseUrl,
      @Value("${rag.ollama.embedModel}") String model
  ) {
    return new OllamaEmbeddingsClient(baseUrl, model);
  }

  @Bean
  @ConditionalOnProperty(name = "rag.llm.provider", havingValue = "ollama-native")
  public ChatClient chatOllamaNative(
      @Value("${rag.ollama.baseUrl}") String baseUrl,
      @Value("${rag.ollama.chatModel}") String model
  ) {
    return new OllamaChatClient(baseUrl, model);
  }

  // Ollama OpenAI-compatible API (/v1/chat/completions and /v1/embeddings)
  @Bean
  @ConditionalOnProperty(name = "rag.llm.provider", havingValue = "ollama-openai", matchIfMissing = true)
  public EmbeddingsClient embeddingsOllamaOpenAI(
      @Value("${rag.ollama.baseUrl}") String baseUrl,
      @Value("${rag.ollama.embedModel}") String model
  ) {
    return new OpenAIEmbeddingsClient(baseUrl, model);
  }

  @Bean
  @ConditionalOnProperty(name = "rag.llm.provider", havingValue = "ollama-openai", matchIfMissing = true)
  public ChatClient chatOllamaOpenAI(
      @Value("${rag.ollama.baseUrl}") String baseUrl,
      @Value("${rag.ollama.chatModel}") String model
  ) {
    return new OpenAIChatClient(baseUrl, model);
  }

  // llama.cpp (non-OpenAI format: /completion and /embedding)
  @Bean
  @ConditionalOnProperty(name = "rag.llm.provider", havingValue = "llamacpp")
  public EmbeddingsClient embeddingsLlamaCpp(
      @Value("${rag.llama.baseUrl}") String baseUrl,
      @Value("${rag.llama.embedModel}") String model
  ) {
    return new LlamaEmbeddingsClient(baseUrl, model);
  }

  @Bean
  @ConditionalOnProperty(name = "rag.llm.provider", havingValue = "llamacpp")
  public ChatClient chatLlamaCpp(
      @Value("${rag.llama.baseUrl}") String baseUrl,
      @Value("${rag.llama.chatModel}") String model
  ) {
    return new LlamaChatClient(baseUrl, model);
  }

  // llama.cpp with OpenAI-compatible API (/v1/chat/completions and /v1/embeddings)
  @Bean
  @ConditionalOnProperty(name = "rag.llm.provider", havingValue = "llamacpp-openai")
  public EmbeddingsClient embeddingsLlamaCppOpenAI(
      @Value("${rag.llama.baseUrl}") String baseUrl,
      @Value("${rag.llama.embedModel}") String model
  ) {
    return new OpenAIEmbeddingsClient(baseUrl, model);
  }

  @Bean
  @ConditionalOnProperty(name = "rag.llm.provider", havingValue = "llamacpp-openai")
  public ChatClient chatLlamaCppOpenAI(
      @Value("${rag.llama.baseUrl}") String baseUrl,
      @Value("${rag.llama.chatModel}") String model
  ) {
    return new LlamaCppOpenAIChatClient(baseUrl, model);
  }
}
