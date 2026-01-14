package com.example.rag.service;

import com.example.rag.llm.ChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Cached wrapper for chat services to avoid repeated LLM calls for the same questions.
 *
 * Features:
 * - Caches all successful responses (30 min TTL)
 * - Does NOT cache failures/errors
 * - Evicts cache on service failures
 * - No frequency tracking overhead
 */
@Service
public class CachedChatService {

  private static final Logger log = LoggerFactory.getLogger(CachedChatService.class);

  private final ChatClient chatClient;
  private final RagService ragService;

  public CachedChatService(ChatClient chatClient, RagService ragService) {
    this.chatClient = chatClient;
    this.ragService = ragService;
  }

  /**
   * Get chat response with caching.
   * Cache is automatically evicted if service fails (exception thrown).
   *
   * @param prompt The user's question
   * @param temperature Temperature parameter for LLM
   * @param maxTokens Maximum tokens to generate
   * @return LLM response (cached for 30 minutes)
   */
  @Cacheable(value = "chatResponses", key = "#prompt", unless = "#result == null || #result.isEmpty()")
  public String getChatResponse(String prompt, double temperature, int maxTokens) {
    try {
      log.debug("Cache miss or expired - fetching chat response for: {}", prompt);
      String response = chatClient.chatOnce(prompt, temperature, maxTokens);

      // Check if response indicates an error
      if (response == null || response.isEmpty() ||
          response.toLowerCase().contains("error") ||
          response.toLowerCase().contains("failed")) {
        log.warn("Service returned error response, not caching");
        throw new RuntimeException("Service error detected");
      }

      return response;
    } catch (Exception e) {
      log.error("Chat service failed, evicting cache for: {}", prompt, e);
      evictChatCache(prompt);
      throw e;
    }
  }

  /**
   * Get RAG response with caching.
   * Cache is automatically evicted if service fails (exception thrown).
   *
   * @param question The user's question
   * @return RAG-enhanced response (cached for 30 minutes)
   */
  @Cacheable(value = "ragResponses", key = "#question", unless = "#result == null || #result.isEmpty()")
  public String getRagResponse(String question) {
    try {
      log.debug("Cache miss or expired - fetching RAG response for: {}", question);
      String response = ragService.ask(question);

      // Check if response indicates an error
      if (response == null || response.isEmpty() ||
          response.toLowerCase().contains("error") ||
          response.toLowerCase().contains("failed")) {
        log.warn("RAG service returned error response, not caching");
        throw new RuntimeException("RAG service error detected");
      }

      return response;
    } catch (Exception e) {
      log.error("RAG service failed, evicting cache for: {}", question, e);
      evictRagCache(question);
      throw e;
    }
  }

  /**
   * Evict specific chat response from cache (used on failures)
   */
  @CacheEvict(value = "chatResponses", key = "#prompt")
  public void evictChatCache(String prompt) {
    log.info("Evicted cache for chat prompt: {}", prompt);
  }

  /**
   * Evict specific RAG response from cache (used on failures)
   */
  @CacheEvict(value = "ragResponses", key = "#question")
  public void evictRagCache(String question) {
    log.info("Evicted cache for RAG question: {}", question);
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
