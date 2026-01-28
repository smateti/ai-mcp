package com.naagi.rag.service;

import com.naagi.rag.entity.FaqCacheConfig;
import com.naagi.rag.entity.FaqEntry;
import com.naagi.rag.entity.GeneratedQA;
import com.naagi.rag.repository.FaqCacheConfigRepository;
import com.naagi.rag.repository.FaqEntryRepository;
import com.naagi.rag.repository.GeneratedQARepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FaqCacheService {

    private final FaqEntryRepository faqRepository;
    private final FaqCacheConfigRepository configRepository;
    private final GeneratedQARepository generatedQARepository;
    private final CacheManager cacheManager;

    // Cache name for FAQ entries
    public static final String FAQ_CACHE_NAME = "faqCache";

    // Track last refresh times per category
    private final Map<String, LocalDateTime> categoryRefreshTimes = new ConcurrentHashMap<>();

    // Default cache expiry in minutes
    private static final int DEFAULT_CACHE_EXPIRY_MINUTES = 5;

    /**
     * Get FAQs for a category - uses EhCache with configurable expiration
     */
    @Cacheable(value = FAQ_CACHE_NAME, key = "#categoryId", unless = "#result == null || #result.isEmpty()")
    public List<FaqEntry> getFaqsForCategory(String categoryId) {
        log.debug("Cache miss for category {}, loading from database", categoryId);
        List<FaqEntry> faqs = faqRepository.findByCategoryIdAndActiveTrueOrderByAccessCountDesc(categoryId);
        categoryRefreshTimes.put(categoryId, LocalDateTime.now());
        return faqs;
    }

    /**
     * Get a specific FAQ answer - checks cache first
     */
    public Optional<FaqEntry> findAnswer(String categoryId, String question) {
        // Normalize the question for matching
        String normalizedQuestion = normalizeQuestion(question);

        // Try to find in cached FAQs
        List<FaqEntry> faqs = getFaqsForCategory(categoryId);

        // Find best match
        Optional<FaqEntry> match = faqs.stream()
                .filter(faq -> calculateSimilarity(normalizedQuestion, normalizeQuestion(faq.getQuestion())) > 0.8)
                .max(Comparator.comparingDouble(faq -> calculateSimilarity(normalizedQuestion, normalizeQuestion(faq.getQuestion()))));

        // Update access stats if found
        match.ifPresent(faq -> {
            faq.incrementAccessCount();
            faqRepository.save(faq);
        });

        return match;
    }

    /**
     * Store FAQs from approved document upload
     */
    @Transactional
    public void storeFaqsFromUpload(String uploadId, String docId, String categoryId) {
        log.info("Storing FAQs from upload {} for category {}", uploadId, categoryId);

        // Get all validated Q&A pairs from the upload
        List<GeneratedQA> qaPairs = generatedQARepository.findByUploadIdOrderByIdAsc(uploadId);

        List<FaqEntry> faqEntries = qaPairs.stream()
                .filter(qa -> qa.getValidationStatus() == GeneratedQA.ValidationStatus.PASSED ||
                              qa.getValidationStatus() == GeneratedQA.ValidationStatus.PENDING)
                .map(qa -> FaqEntry.builder()
                        .categoryId(categoryId)
                        .docId(docId)
                        .uploadId(uploadId)
                        .questionType(qa.getQuestionType().name())
                        .question(qa.getQuestion())
                        .answer(qa.getExpectedAnswer())
                        .similarityScore(qa.getSimilarityScore())
                        .active(true)
                        .build())
                .collect(Collectors.toList());

        faqRepository.saveAll(faqEntries);
        log.info("Stored {} FAQ entries for category {}", faqEntries.size(), categoryId);

        // Evict cache for this category so it reloads with new FAQs
        evictCategoryCache(categoryId);
    }

    /**
     * Manually refresh cache for a category
     */
    @CacheEvict(value = FAQ_CACHE_NAME, key = "#categoryId")
    public void evictCategoryCache(String categoryId) {
        log.info("Evicting FAQ cache for category {}", categoryId);
        categoryRefreshTimes.remove(categoryId);

        // Update last refreshed time in config
        configRepository.findByCategoryId(categoryId).ifPresent(config -> {
            config.setLastRefreshedAt(LocalDateTime.now());
            configRepository.save(config);
        });
    }

    /**
     * Refresh all category caches
     */
    public void refreshAllCaches() {
        log.info("Refreshing all FAQ caches");
        Cache cache = cacheManager.getCache(FAQ_CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
        categoryRefreshTimes.clear();

        // Update refresh times
        configRepository.findAll().forEach(config -> {
            config.setLastRefreshedAt(LocalDateTime.now());
            configRepository.save(config);
        });
    }

    /**
     * Scheduled task to check and refresh expired caches
     * Runs every minute to check for expired category caches
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void checkAndRefreshExpiredCaches() {
        LocalDateTime now = LocalDateTime.now();

        List<String> categoryIds = faqRepository.findDistinctCategoryIds();

        for (String categoryId : categoryIds) {
            FaqCacheConfig config = getOrCreateConfig(categoryId);

            if (!config.isCacheEnabled()) {
                continue;
            }

            LocalDateTime lastRefresh = categoryRefreshTimes.get(categoryId);
            if (lastRefresh == null) {
                continue; // Cache not loaded yet, will load on first access
            }

            LocalDateTime expiryTime = lastRefresh.plusMinutes(config.getCacheExpiryMinutes());
            if (now.isAfter(expiryTime)) {
                log.info("Cache expired for category {}, refreshing (expiry was {}min)", categoryId, config.getCacheExpiryMinutes());
                evictCategoryCache(categoryId);
            }
        }
    }

    /**
     * Get or create cache configuration for a category
     */
    public FaqCacheConfig getOrCreateConfig(String categoryId) {
        return configRepository.findByCategoryId(categoryId)
                .orElseGet(() -> {
                    FaqCacheConfig config = FaqCacheConfig.builder()
                            .categoryId(categoryId)
                            .cacheExpiryMinutes(DEFAULT_CACHE_EXPIRY_MINUTES)
                            .cacheEnabled(true)
                            .build();
                    return configRepository.save(config);
                });
    }

    /**
     * Update cache configuration
     */
    @Transactional
    public FaqCacheConfig updateCacheConfig(String categoryId, int expiryMinutes, boolean enabled) {
        FaqCacheConfig config = getOrCreateConfig(categoryId);
        config.setCacheExpiryMinutes(expiryMinutes);
        config.setCacheEnabled(enabled);
        FaqCacheConfig saved = configRepository.save(config);

        // Evict cache if config changed
        evictCategoryCache(categoryId);

        log.info("Updated FAQ cache config for category {}: expiry={}min, enabled={}",
                categoryId, expiryMinutes, enabled);
        return saved;
    }

    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();

        List<FaqCacheConfig> configs = configRepository.findAll();
        List<Map<String, Object>> categoryStats = new ArrayList<>();

        for (FaqCacheConfig config : configs) {
            Map<String, Object> catStat = new HashMap<>();
            catStat.put("categoryId", config.getCategoryId());
            catStat.put("expiryMinutes", config.getCacheExpiryMinutes());
            catStat.put("enabled", config.isCacheEnabled());
            catStat.put("lastRefreshed", config.getLastRefreshedAt());
            catStat.put("faqCount", faqRepository.countByCategoryId(config.getCategoryId()));

            LocalDateTime lastRefresh = categoryRefreshTimes.get(config.getCategoryId());
            catStat.put("cacheLoaded", lastRefresh != null);
            if (lastRefresh != null) {
                catStat.put("cacheAge", java.time.Duration.between(lastRefresh, LocalDateTime.now()).toSeconds() + "s");
            }

            categoryStats.add(catStat);
        }

        stats.put("categories", categoryStats);
        stats.put("totalFaqs", faqRepository.count());
        stats.put("activeFaqs", faqRepository.findAll().stream().filter(FaqEntry::isActive).count());

        return stats;
    }

    /**
     * Deactivate FAQs for a document (when document is deleted)
     */
    @Transactional
    public void deactivateFaqsForDocument(String docId) {
        faqRepository.deactivateByDocId(docId);

        // Find affected categories and evict their caches
        List<FaqEntry> faqs = faqRepository.findByDocIdAndActiveTrue(docId);
        faqs.stream()
                .map(FaqEntry::getCategoryId)
                .distinct()
                .forEach(this::evictCategoryCache);
    }

    /**
     * Get top FAQs for a category (most accessed)
     */
    public List<FaqEntry> getTopFaqs(String categoryId, int limit) {
        return getFaqsForCategory(categoryId).stream()
                .sorted(Comparator.comparingInt(FaqEntry::getAccessCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Search FAQs by question text
     */
    public List<FaqEntry> searchFaqs(String categoryId, String query) {
        return faqRepository.searchByQuestion(categoryId, query);
    }

    // Helper methods

    private String normalizeQuestion(String question) {
        if (question == null) return "";
        return question.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;

        Set<String> words1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
