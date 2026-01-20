package com.example.userchat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class FaqCacheService {

    private static final Logger log = LoggerFactory.getLogger(FaqCacheService.class);
    private static final String CACHE_NAME = "faqCache";

    private final CacheManager cacheManager;

    public FaqCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Generate a hash for a question to use as cache key
     */
    public String generateQuestionHash(String question, String categoryId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String composite = categoryId + ":" + question.toLowerCase().trim();
            byte[] hash = digest.digest(composite.getBytes());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate question hash", e);
        }
    }

    /**
     * Try to get answer from cache, returns null if not found
     */
    public String tryGetCachedAnswer(String question, String categoryId) {
        String hash = generateQuestionHash(question, categoryId);
        Cache cache = cacheManager.getCache(CACHE_NAME);

        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(hash);
            if (wrapper != null) {
                String cached = (String) wrapper.get();
                log.info("Cache HIT for question: {}", question.substring(0, Math.min(50, question.length())));
                return cached;
            }
        }

        log.info("Cache MISS for question: {}", question.substring(0, Math.min(50, question.length())));
        return null;
    }

    /**
     * Cache an answer for a question
     */
    public void cacheQuestionAnswer(String question, String categoryId, String answer) {
        String hash = generateQuestionHash(question, categoryId);
        Cache cache = cacheManager.getCache(CACHE_NAME);

        if (cache != null) {
            cache.put(hash, answer);
            log.info("Caching answer for question: {} (hash: {})",
                question.substring(0, Math.min(50, question.length())),
                hash.substring(0, 10) + "...");
        }
    }

    /**
     * Clear the entire FAQ cache
     */
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void clearCache() {
        log.info("Clearing FAQ cache");
    }

    /**
     * Get cache statistics
     */
    public String getCacheStats() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            Object nativeCache = cache.getNativeCache();
            return "Cache available: " + nativeCache.getClass().getName();
        }
        return "Cache not available";
    }
}
