package com.naagi.rag.controller;

import com.naagi.rag.entity.FaqCacheConfig;
import com.naagi.rag.entity.FaqEntry;
import com.naagi.rag.service.FaqCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/faq")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class FaqController {

    private final FaqCacheService faqCacheService;

    /**
     * Get all FAQs for a category (from cache)
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<FaqEntry>> getFaqsByCategory(@PathVariable String categoryId) {
        List<FaqEntry> faqs = faqCacheService.getFaqsForCategory(categoryId);
        return ResponseEntity.ok(faqs);
    }

    /**
     * Get top FAQs for a category (most accessed)
     */
    @GetMapping("/category/{categoryId}/top")
    public ResponseEntity<List<FaqEntry>> getTopFaqs(
            @PathVariable String categoryId,
            @RequestParam(defaultValue = "10") int limit) {
        List<FaqEntry> faqs = faqCacheService.getTopFaqs(categoryId, limit);
        return ResponseEntity.ok(faqs);
    }

    /**
     * Find answer from FAQ cache
     */
    @GetMapping("/answer")
    public ResponseEntity<Map<String, Object>> findAnswer(
            @RequestParam String categoryId,
            @RequestParam String question) {
        Optional<FaqEntry> faq = faqCacheService.findAnswer(categoryId, question);

        if (faq.isPresent()) {
            FaqEntry entry = faq.get();
            return ResponseEntity.ok(Map.of(
                    "found", true,
                    "question", entry.getQuestion(),
                    "answer", entry.getAnswer(),
                    "similarityScore", entry.getSimilarityScore() != null ? entry.getSimilarityScore() : 0,
                    "accessCount", entry.getAccessCount(),
                    "source", "faq_cache"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "found", false,
                "message", "No matching FAQ found"
        ));
    }

    /**
     * Search FAQs by question text
     */
    @GetMapping("/search")
    public ResponseEntity<List<FaqEntry>> searchFaqs(
            @RequestParam String categoryId,
            @RequestParam String query) {
        List<FaqEntry> results = faqCacheService.searchFaqs(categoryId, query);
        return ResponseEntity.ok(results);
    }

    // ==================== Cache Management ====================

    /**
     * Get cache configuration for a category
     */
    @GetMapping("/config/{categoryId}")
    public ResponseEntity<FaqCacheConfig> getCacheConfig(@PathVariable String categoryId) {
        FaqCacheConfig config = faqCacheService.getOrCreateConfig(categoryId);
        return ResponseEntity.ok(config);
    }

    /**
     * Update cache configuration for a category
     */
    @PutMapping("/config/{categoryId}")
    public ResponseEntity<FaqCacheConfig> updateCacheConfig(
            @PathVariable String categoryId,
            @RequestBody Map<String, Object> request) {
        int expiryMinutes = (int) request.getOrDefault("expiryMinutes", 5);
        boolean enabled = (boolean) request.getOrDefault("enabled", true);

        FaqCacheConfig config = faqCacheService.updateCacheConfig(categoryId, expiryMinutes, enabled);
        return ResponseEntity.ok(config);
    }

    /**
     * Refresh cache for a specific category
     */
    @PostMapping("/cache/{categoryId}/refresh")
    public ResponseEntity<Map<String, String>> refreshCategoryCache(@PathVariable String categoryId) {
        faqCacheService.evictCategoryCache(categoryId);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Cache refreshed for category: " + categoryId
        ));
    }

    /**
     * Refresh all caches
     */
    @PostMapping("/cache/refresh-all")
    public ResponseEntity<Map<String, String>> refreshAllCaches() {
        faqCacheService.refreshAllCaches();
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "All FAQ caches refreshed"
        ));
    }

    /**
     * Get cache statistics
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = faqCacheService.getCacheStats();
        return ResponseEntity.ok(stats);
    }

    // ==================== Admin Operations ====================

    /**
     * Deactivate FAQs for a document (when document is removed)
     */
    @DeleteMapping("/document/{docId}")
    public ResponseEntity<Map<String, String>> deactivateFaqsForDocument(@PathVariable String docId) {
        faqCacheService.deactivateFaqsForDocument(docId);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "FAQs deactivated for document: " + docId
        ));
    }
}
