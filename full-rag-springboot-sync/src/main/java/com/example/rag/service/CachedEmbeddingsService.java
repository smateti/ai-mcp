package com.example.rag.service;

import com.example.rag.llm.EmbeddingsClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Cached wrapper for embeddings to avoid repeated expensive embedding calls.
 *
 * Embeddings are computationally expensive, so caching frequently queried text
 * can significantly improve performance.
 */
@Service
public class CachedEmbeddingsService {

  private final EmbeddingsClient embeddingsClient;

  public CachedEmbeddingsService(EmbeddingsClient embeddingsClient) {
    this.embeddingsClient = embeddingsClient;
  }

  /**
   * Get cached embedding for text.
   *
   * Cache key is the text itself. Same text will return cached embeddings.
   * Cache expires after 7 days or when cache is full (2000 entries).
   *
   * This is particularly useful for:
   * - Repeated user queries
   * - Common search terms
   * - Standard questions
   *
   * @param text The text to embed
   * @return Embedding vector (cached or fresh)
   */
  @Cacheable(value = "embeddings", key = "#text")
  public List<Double> getEmbedding(String text) {
    return embeddingsClient.embed(text);
  }

  /**
   * Get embedding without caching (for document ingestion, unique text, etc.).
   */
  public List<Double> getEmbeddingUncached(String text) {
    return embeddingsClient.embed(text);
  }
}
