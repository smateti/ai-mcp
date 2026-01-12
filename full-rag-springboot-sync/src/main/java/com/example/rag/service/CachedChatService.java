package com.example.rag.service;

import com.example.rag.llm.ChatClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Cached wrapper for chat services to avoid repeated LLM calls for the same questions.
 *
 * This service provides caching for both direct chat and RAG queries.
 * Now with frequency tracking - only frequently asked questions are cached.
 */
@Service
public class CachedChatService {

  private final ChatClient chatClient;
  private final RagService ragService;
  private final QuestionFrequencyService frequencyService;

  public CachedChatService(ChatClient chatClient,
                           RagService ragService,
                           QuestionFrequencyService frequencyService) {
    this.chatClient = chatClient;
    this.ragService = ragService;
    this.frequencyService = frequencyService;
  }

  /**
   * Get chat response with smart caching based on frequency.
   *
   * Questions are only cached if they've been asked multiple times (threshold: 2).
   * This prevents one-time questions from wasting cache space.
   *
   * @param prompt The user's question
   * @param temperature Temperature parameter for LLM
   * @param maxTokens Maximum tokens to generate
   * @return LLM response (cached if frequent, fresh otherwise)
   */
  public String getChatResponse(String prompt, double temperature, int maxTokens) {
    // Track frequency and check if this should be cached
    boolean isFrequent = frequencyService.recordAndCheckIfFrequent(prompt);

    if (isFrequent) {
      // Use cached version for frequently asked questions
      return getChatResponseCached(prompt, temperature, maxTokens);
    } else {
      // Direct call for infrequent questions (not cached)
      return chatClient.chatOnce(prompt, temperature, maxTokens);
    }
  }

  /**
   * Internal cached method - only called for frequent questions.
   */
  @Cacheable(value = "chatResponses", key = "#prompt")
  protected String getChatResponseCached(String prompt, double temperature, int maxTokens) {
    return chatClient.chatOnce(prompt, temperature, maxTokens);
  }

  /**
   * Get RAG response with smart caching based on frequency.
   *
   * Questions are only cached if they've been asked multiple times (threshold: 2).
   * RAG responses depend on document corpus, so caching is more selective.
   *
   * @param question The user's question
   * @return RAG-enhanced response (cached if frequent, fresh otherwise)
   */
  public String getRagResponse(String question) {
    // Track frequency and check if this should be cached
    boolean isFrequent = frequencyService.recordAndCheckIfFrequent(question);

    if (isFrequent) {
      // Use cached version for frequently asked questions
      return getRagResponseCached(question);
    } else {
      // Direct call for infrequent questions (not cached)
      return ragService.ask(question);
    }
  }

  /**
   * Internal cached method - only called for frequent questions.
   */
  @Cacheable(value = "ragResponses", key = "#question")
  protected String getRagResponseCached(String question) {
    return ragService.ask(question);
  }

  /**
   * Get chat response without caching (for unique/one-time queries).
   */
  public String getChatResponseUncached(String prompt, double temperature, int maxTokens) {
    return chatClient.chatOnce(prompt, temperature, maxTokens);
  }

  /**
   * Get RAG response without caching (for unique/one-time queries).
   */
  public String getRagResponseUncached(String question) {
    return ragService.ask(question);
  }
}
