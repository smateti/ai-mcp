package com.example.rag.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

/**
 * Cache configuration for the RAG application.
 *
 * Enables Spring's caching support and uses EhCache as the cache provider.
 *
 * Cache strategies:
 * - chatResponses: Caches direct LLM responses (24 hours, 1000 entries)
 * - ragResponses: Caches RAG-enhanced responses (12 hours, 500 entries)
 * - embeddings: Caches text embeddings (7 days, 2000 entries)
 */
@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public CacheManager ehCacheManager() {
    try {
      CachingProvider provider = Caching.getCachingProvider();
      javax.cache.CacheManager cacheManager = provider.getCacheManager(
          getClass().getResource("/ehcache.xml").toURI(),
          getClass().getClassLoader());
      return cacheManager;
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize EhCache", e);
    }
  }

  @Bean
  public org.springframework.cache.CacheManager cacheManager() {
    return new JCacheCacheManager(ehCacheManager());
  }
}
