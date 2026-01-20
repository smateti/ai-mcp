package com.naag.rag.controller;

import com.naag.rag.entity.FaqEntry;
import com.naag.rag.entity.FaqSettings;
import com.naag.rag.entity.GeneratedQA;
import com.naag.rag.entity.GeneratedQA.AnswerSource;
import com.naag.rag.qdrant.FaqQdrantClient.FaqSearchResult;
import com.naag.rag.service.FaqManagementService;
import com.naag.rag.service.FaqManagementService.FaqApprovalResult;
import com.naag.rag.service.FaqSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for FAQ management - review, approval, and CRUD operations.
 */
@RestController
@RequestMapping("/api/faq-management")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class FaqManagementController {

    private final FaqManagementService faqService;
    private final FaqSettingsService settingsService;

    // ========== Q&A Review Endpoints ==========

    /**
     * Get Q&A pairs for review for a specific upload
     */
    @GetMapping("/review/{uploadId}")
    public ResponseEntity<List<GeneratedQA>> getQAPairsForReview(@PathVariable String uploadId) {
        List<GeneratedQA> qaPairs = faqService.getQAPairsForReview(uploadId);
        return ResponseEntity.ok(qaPairs);
    }

    /**
     * Update a single Q&A pair
     */
    @PutMapping("/review/{uploadId}/qa/{qaId}")
    public ResponseEntity<GeneratedQA> updateQAPair(
            @PathVariable String uploadId,
            @PathVariable Long qaId,
            @RequestBody UpdateQARequest request) {

        GeneratedQA updated = faqService.updateQAPair(
                qaId,
                request.editedAnswer(),
                request.answerSource(),
                request.selectedForFaq()
        );
        return ResponseEntity.ok(updated);
    }

    /**
     * Batch select Q&A pairs for FAQ
     */
    @PostMapping("/review/{uploadId}/select")
    public ResponseEntity<Map<String, Object>> selectQAPairs(
            @PathVariable String uploadId,
            @RequestBody SelectQARequest request) {

        faqService.selectQAPairsForFaq(uploadId, request.qaIds(), request.selected());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Selected " + request.qaIds().size() + " Q&A pairs"
        ));
    }

    /**
     * Auto-select Q&A pairs with passing validation scores
     */
    @PostMapping("/review/{uploadId}/auto-select")
    public ResponseEntity<Map<String, Object>> autoSelectPassed(@PathVariable String uploadId) {
        int count = faqService.autoSelectPassedQAPairs(uploadId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "selectedCount", count
        ));
    }

    /**
     * Approve selected FAQs and push to Qdrant
     */
    @PostMapping("/review/{uploadId}/approve")
    public ResponseEntity<Map<String, Object>> approveSelectedFaqs(
            @PathVariable String uploadId,
            @RequestBody ApproveRequest request) {

        FaqApprovalResult result = faqService.approveSelectedFaqs(uploadId, request.approvedBy());

        return ResponseEntity.ok(Map.of(
                "success", !result.hasErrors(),
                "approvedCount", result.approvedCount(),
                "errors", result.errors()
        ));
    }

    // ========== FAQ CRUD Endpoints ==========

    /**
     * List all FAQs with pagination and optional filters
     */
    @GetMapping
    public ResponseEntity<Page<FaqEntry>> listFaqs(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size);

        Page<FaqEntry> faqs;
        if (search != null && !search.isBlank()) {
            faqs = faqService.searchFaqs(search, pageable);
        } else if (categoryId != null && !categoryId.isBlank()) {
            faqs = faqService.getFaqsByCategory(categoryId, pageable);
        } else {
            faqs = faqService.getAllFaqs(pageable);
        }

        return ResponseEntity.ok(faqs);
    }

    /**
     * Get FAQ by ID
     */
    @GetMapping("/{faqId}")
    public ResponseEntity<FaqEntry> getFaqById(@PathVariable Long faqId) {
        return faqService.getFaqById(faqId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get FAQs by document
     */
    @GetMapping("/document/{docId}")
    public ResponseEntity<List<FaqEntry>> getFaqsByDocument(@PathVariable String docId) {
        return ResponseEntity.ok(faqService.getFaqsByDocument(docId));
    }

    /**
     * Get FAQs by upload
     */
    @GetMapping("/upload/{uploadId}")
    public ResponseEntity<List<FaqEntry>> getFaqsByUpload(@PathVariable String uploadId) {
        return ResponseEntity.ok(faqService.getFaqsByUpload(uploadId));
    }

    /**
     * Update FAQ
     */
    @PutMapping("/{faqId}")
    public ResponseEntity<FaqEntry> updateFaq(
            @PathVariable Long faqId,
            @RequestBody UpdateFaqRequest request) {

        FaqEntry updated = faqService.updateFaq(faqId, request.question(), request.answer());
        return ResponseEntity.ok(updated);
    }

    /**
     * Deactivate FAQ (soft delete)
     */
    @DeleteMapping("/{faqId}")
    public ResponseEntity<Map<String, Object>> deactivateFaq(@PathVariable Long faqId) {
        faqService.deactivateFaq(faqId);
        return ResponseEntity.ok(Map.of("success", true, "message", "FAQ deactivated"));
    }

    /**
     * Reactivate FAQ
     */
    @PostMapping("/{faqId}/reactivate")
    public ResponseEntity<Map<String, Object>> reactivateFaq(@PathVariable Long faqId) {
        faqService.reactivateFaq(faqId);
        return ResponseEntity.ok(Map.of("success", true, "message", "FAQ reactivated"));
    }

    // ========== FAQ Query Endpoints ==========

    /**
     * Query FAQs semantically by question (uses Qdrant)
     */
    @PostMapping("/query")
    public ResponseEntity<List<FaqSearchResult>> queryFaqs(@RequestBody FaqQueryRequest request) {
        List<FaqSearchResult> results = faqService.queryFaqs(
                request.question(),
                request.categoryId(),
                request.limit() > 0 ? request.limit() : 5
        );
        return ResponseEntity.ok(results);
    }

    /**
     * Find best matching FAQ for a question (always queries regardless of settings)
     */
    @PostMapping("/match")
    public ResponseEntity<Map<String, Object>> findBestMatch(@RequestBody FaqQueryRequest request) {
        return faqService.findBestMatch(request.question(), request.categoryId())
                .map(match -> ResponseEntity.ok(Map.<String, Object>of(
                        "found", true,
                        "faqId", match.faqId(),
                        "question", match.question(),
                        "answer", match.answer(),
                        "score", match.score()
                )))
                .orElse(ResponseEntity.ok(Map.of("found", false)));
    }

    /**
     * Find best matching FAQ for a question - respects FAQ query enabled setting.
     * Use this endpoint for user-facing queries where admin toggle should be respected.
     * Returns found=false if FAQ query is disabled OR no match found.
     */
    @PostMapping("/match-if-enabled")
    public ResponseEntity<Map<String, Object>> findBestMatchIfEnabled(@RequestBody FaqQueryRequest request) {
        // Include the enabled status in response so callers know if it was checked
        boolean faqQueryEnabled = faqService.isFaqQueryEnabled();

        if (!faqQueryEnabled) {
            return ResponseEntity.ok(Map.of(
                    "found", false,
                    "faqQueryEnabled", false,
                    "reason", "FAQ query is disabled"
            ));
        }

        return faqService.findBestMatchIfEnabled(request.question(), request.categoryId())
                .map(match -> ResponseEntity.ok(Map.<String, Object>of(
                        "found", true,
                        "faqQueryEnabled", true,
                        "faqId", match.faqId(),
                        "question", match.question(),
                        "answer", match.answer(),
                        "score", match.score()
                )))
                .orElse(ResponseEntity.ok(Map.of(
                        "found", false,
                        "faqQueryEnabled", true,
                        "reason", "No matching FAQ found"
                )));
    }

    /**
     * Record FAQ access
     */
    @PostMapping("/{faqId}/access")
    public ResponseEntity<Void> recordAccess(@PathVariable Long faqId) {
        faqService.recordFaqAccess(faqId);
        return ResponseEntity.ok().build();
    }

    /**
     * Find similar FAQs for a given FAQ ID.
     * Uses semantic similarity to find questions that are asking the same thing.
     * Returns FAQs with similarity score >= minScore (default 0.90 for 90%+).
     */
    @GetMapping("/{faqId}/similar")
    public ResponseEntity<List<Map<String, Object>>> findSimilarFaqs(
            @PathVariable Long faqId,
            @RequestParam(defaultValue = "0.90") double minScore,
            @RequestParam(defaultValue = "20") int limit) {

        List<Map<String, Object>> similarFaqs = faqService.findSimilarFaqs(faqId, minScore, limit);
        return ResponseEntity.ok(similarFaqs);
    }

    /**
     * Merge FAQs - copy the answer from source FAQ to target FAQs.
     * The target FAQs keep their questions but get the same answer as source.
     */
    @PostMapping("/merge")
    public ResponseEntity<Map<String, Object>> mergeFaqs(@RequestBody MergeFaqsRequest request) {
        int mergedCount = faqService.mergeFaqs(request.sourceFaqId(), request.targetFaqIds());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "mergedCount", mergedCount,
                "message", "Merged answer to " + mergedCount + " FAQs"
        ));
    }

    /**
     * Get FAQ statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getFaqStats() {
        Map<String, Object> stats = faqService.getFaqStats();
        // Include settings status in stats
        FaqSettings settings = settingsService.getSettings();
        stats.put("faqQueryEnabled", settings.isFaqQueryEnabled());
        stats.put("minSimilarityScore", settings.getMinSimilarityScore());
        stats.put("storeUserQuestions", settings.isStoreUserQuestions());
        return ResponseEntity.ok(stats);
    }

    // ========== Settings Endpoints ==========

    /**
     * Get current FAQ settings
     */
    @GetMapping("/settings")
    public ResponseEntity<FaqSettings> getSettings() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    /**
     * Update FAQ settings
     */
    @PutMapping("/settings")
    public ResponseEntity<FaqSettings> updateSettings(
            @RequestBody UpdateSettingsRequest request) {

        FaqSettings settings = FaqSettings.builder()
                .faqQueryEnabled(request.faqQueryEnabled())
                .minSimilarityScore(request.minSimilarityScore())
                .storeUserQuestions(request.storeUserQuestions())
                .autoSelectThreshold(request.autoSelectThreshold())
                .build();

        FaqSettings updated = settingsService.updateSettings(settings, request.updatedBy());
        return ResponseEntity.ok(updated);
    }

    /**
     * Toggle FAQ query on/off
     */
    @PostMapping("/settings/toggle-faq-query")
    public ResponseEntity<FaqSettings> toggleFaqQuery(@RequestBody ToggleFaqQueryRequest request) {
        FaqSettings updated = settingsService.toggleFaqQuery(request.enabled(), request.updatedBy());
        return ResponseEntity.ok(updated);
    }

    /**
     * Check if FAQ query is enabled
     */
    @GetMapping("/settings/faq-query-enabled")
    public ResponseEntity<Map<String, Object>> isFaqQueryEnabled() {
        return ResponseEntity.ok(Map.of(
                "enabled", settingsService.isFaqQueryEnabled()
        ));
    }

    // ========== Request Records ==========

    public record UpdateQARequest(
            String editedAnswer,
            AnswerSource answerSource,
            boolean selectedForFaq
    ) {}

    public record SelectQARequest(
            List<Long> qaIds,
            boolean selected
    ) {}

    public record ApproveRequest(
            String approvedBy
    ) {}

    public record UpdateFaqRequest(
            String question,
            String answer
    ) {}

    public record FaqQueryRequest(
            String question,
            String categoryId,
            int limit
    ) {}

    public record UpdateSettingsRequest(
            boolean faqQueryEnabled,
            double minSimilarityScore,
            boolean storeUserQuestions,
            double autoSelectThreshold,
            String updatedBy
    ) {}

    public record ToggleFaqQueryRequest(
            boolean enabled,
            String updatedBy
    ) {}

    public record MergeFaqsRequest(
            Long sourceFaqId,
            List<Long> targetFaqIds
    ) {}
}
