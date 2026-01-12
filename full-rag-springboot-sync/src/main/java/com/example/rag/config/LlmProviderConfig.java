package com.example.rag.config;

import com.example.rag.llama.LlamaChatClient;
import com.example.rag.llama.LlamaEmbeddingsClient;
import com.example.rag.llm.ChatClient;
import com.example.rag.llm.EmbeddingsClient;
import com.example.rag.ollama.OllamaChatClient;
import com.example.rag.ollama.OllamaEmbeddingsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmProviderConfig {

  @Bean
  @ConditionalOnProperty(name = "rag.llm.provider", havingValue = "llamacpp", matchIfMissing = true)
  public EmbeddingsClient embeddingsLlamaCpp(
      @Value("${rag.llama.embedBaseUrl}") String baseUrl,
      @Value("${rag.llama.embedModel}") String model
  ) {
    return new LlamaEmbeddingsClient(baseUrl, model);
  }

  @Bean
  @ConditionalOnProperty(name = "rag.llm.provider", havingValue = "llamacpp", matchIfMissing = true)
  public ChatClient chatLlamaCpp(
      @Value("${rag.llama.chatBaseUrl}") String baseUrl,
      @Value("${rag.llama.chatModel}") String model
  ) {
    return new LlamaChatClient(baseUrl, model);
  }

  @Bean
  @ConditionalOnProperty(name = "rag.llm.provider", havingValue = "ollama")
  public EmbeddingsClient embeddingsOllama(
      @Value("${rag.ollama.baseUrl}") String baseUrl,
      @Value("${rag.ollama.embedModel}") String model
  ) {
    return new OllamaEmbeddingsClient(baseUrl, model);
  }

  @Bean
  @ConditionalOnProperty(name = "rag.llm.provider", havingValue = "ollama")
  public ChatClient chatOllama(
      @Value("${rag.ollama.baseUrl}") String baseUrl,
      @Value("${rag.ollama.chatModel}") String model
  ) {
    return new OllamaChatClient(baseUrl, model);
  }
}
