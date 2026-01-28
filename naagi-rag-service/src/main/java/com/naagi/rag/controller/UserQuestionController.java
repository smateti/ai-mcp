package com.naagi.rag.controller;

import com.naagi.rag.entity.UserQuestion;
import com.naagi.rag.qdrant.UserQuestionQdrantClient.SimilarQuestionResult;
import com.naagi.rag.service.UserQuestionAnalyticsService;
import com.naagi.rag.service.UserQuestionAnalyticsService.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for user question FAQ analytics.
 * This is NOT for audit - use chat-app audit endpoints for full audit data.
 * This tracks unique questions with frequency for FAQ candidate identification.
 */
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class UserQuestionController {

    private final UserQuestionAnalyticsService analyticsService;

    /**
     * Track a user question for FAQ analytics (called from chat service).
     * This only stores minimal data for deduplication and frequency tracking.
     * Full audit data is stored separately in chat-app's audit system.
     */
    @PostMapping("/track")
    public ResponseEntity<Map<String, Object>> trackQuestion(@RequestBody TrackQuestionRequestDTO request) {
        TrackQuestionRequest serviceRequest = new TrackQuestionRequest(
                request.question(),
                request.categoryId()
        );

        UserQuestion tracked = analyticsService.trackQuestion(serviceRequest);

        if (tracked != null) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "questionId", tracked.getId(),
                    "frequency", tracked.getFrequency(),
                    "isDuplicate", tracked.getFrequency() > 1,
                    "matchedFaqId", tracked.getMatchedFaqId() != null ? tracked.getMatchedFaqId() : ""
            ));
        }
        return ResponseEntity.ok(Map.of("success", false, "message", "Question tracking disabled or failed"));
    }

    /**
     * Get frequently asked questions with filters
     */
    @GetMapping("/frequent")
    public ResponseEntity<Page<UserQuestion>> getFrequentQuestions(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Boolean hasMatchedFaq,
            @RequestParam(required = false) Integer minFrequency,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Determine sort order
        Sort sort;
        if ("recent".equals(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "lastAskedAt");
        } else if ("oldest".equals(sortBy)) {
            sort = Sort.by(Sort.Direction.ASC, "firstAskedAt");
        } else {
            // Default: frequency desc
            sort = Sort.by(Sort.Direction.DESC, "frequency");
        }

        PageRequest pageable = PageRequest.of(page, size, sort);

        // Convert ZonedDateTime to LocalDateTime
        LocalDateTime fromDateTime = fromDate != null ? fromDate.toLocalDateTime() : null;
        LocalDateTime toDateTime = toDate != null ? toDate.toLocalDateTime() : null;

        Page<UserQuestion> questions = analyticsService.getFrequentlyAskedQuestions(
                categoryId, hasMatchedFaq, minFrequency, fromDateTime, toDateTime, pageable);

        return ResponseEntity.ok(questions);
    }

    /**
     * Get questions without FAQ match (potential new FAQs)
     */
    @GetMapping("/unmatched")
    public ResponseEntity<Page<UserQuestion>> getUnmatchedQuestions(
            @RequestParam(required = false) String categoryId,
            @RequestParam(defaultValue = "1") int minFrequency,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size);
        Page<UserQuestion> questions = analyticsService.getUnmatchedQuestions(categoryId, minFrequency, pageable);

        return ResponseEntity.ok(questions);
    }

    /**
     * Find similar questions to a given question
     */
    @GetMapping("/similar")
    public ResponseEntity<List<SimilarQuestionResult>> findSimilarQuestions(
            @RequestParam String question,
            @RequestParam(defaultValue = "0.7") double minScore,
            @RequestParam(defaultValue = "10") int limit) {

        List<SimilarQuestionResult> results = analyticsService.findSimilarQuestions(question, minScore, limit);
        return ResponseEntity.ok(results);
    }

    /**
     * Check if a question matches an existing FAQ
     */
    @PostMapping("/check-faq-match")
    public ResponseEntity<Map<String, Object>> checkFaqMatch(@RequestBody CheckFaqMatchRequest request) {
        Optional<FaqMatchResult> match = analyticsService.checkFaqMatch(request.question(), request.categoryId());

        if (match.isPresent()) {
            FaqMatchResult result = match.get();
            return ResponseEntity.ok(Map.of(
                    "found", true,
                    "faqId", result.faqId(),
                    "question", result.question(),
                    "answer", result.answer(),
                    "score", result.score()
            ));
        }
        return ResponseEntity.ok(Map.of("found", false));
    }

    /**
     * Get question analytics/statistics
     */
    @GetMapping("/analytics")
    public ResponseEntity<QuestionAnalytics> getAnalytics(
            @RequestParam(required = false) String categoryId) {

        QuestionAnalytics analytics = analyticsService.getAnalytics(categoryId);
        return ResponseEntity.ok(analytics);
    }

    /**
     * Get a single question by ID
     */
    @GetMapping("/{questionId}")
    public ResponseEntity<UserQuestion> getQuestionById(@PathVariable String questionId) {
        return analyticsService.getQuestionById(questionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get the matched FAQ details for a user question.
     * Returns FAQ question and answer if the user question is matched to an FAQ.
     */
    @GetMapping("/{questionId}/matched-faq")
    public ResponseEntity<Map<String, Object>> getMatchedFaq(@PathVariable String questionId) {
        Optional<UserQuestion> questionOpt = analyticsService.getQuestionById(questionId);
        if (questionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        UserQuestion question = questionOpt.get();
        if (question.getMatchedFaqId() == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("found", false);
            response.put("message", "No matched FAQ");
            return ResponseEntity.ok(response);
        }

        return analyticsService.getFaqDetails(question.getMatchedFaqId())
                .map(faq -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("found", true);
                    response.put("faqId", faq.getId());
                    response.put("question", faq.getQuestion());
                    response.put("answer", faq.getAnswer());
                    response.put("matchScore", question.getMatchedFaqScore() != null ? question.getMatchedFaqScore() : 0.0);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("found", false);
                    response.put("message", "Matched FAQ not found");
                    return ResponseEntity.ok(response);
                });
    }

    /**
     * Search questions by text
     */
    @GetMapping("/search")
    public ResponseEntity<Page<UserQuestion>> searchQuestions(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size);
        Page<UserQuestion> questions = analyticsService.searchQuestions(query, pageable);
        return ResponseEntity.ok(questions);
    }

    /**
     * Promote question to FAQ and delete from analytics tracking.
     * Creates the FAQ in operational database (FaqEntry + Qdrant) and deletes from user questions.
     */
    @PostMapping("/{questionId}/promote-to-faq")
    public ResponseEntity<Map<String, Object>> promoteToFaq(
            @PathVariable String questionId,
            @RequestBody PromoteRequest request) {

        if (request.answer() == null || request.answer().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Answer is required to create FAQ"
            ));
        }

        PromotionResult result = analyticsService.promoteToFaqAndDelete(
                questionId, request.answer(), request.promotedBy());

        if (!result.isSuccess()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", result.error() != null ? result.error() : "Failed to promote question");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Question promoted to FAQ and removed from analytics");
        response.put("faqId", result.createdFaq().getId());
        response.put("question", result.createdFaq().getQuestion());
        response.put("answer", result.createdFaq().getAnswer());
        response.put("categoryId", result.createdFaq().getCategoryId() != null ? result.createdFaq().getCategoryId() : "");

        return ResponseEntity.ok(response);
    }

    /**
     * Delete a question from analytics tracking (dismiss without promoting to FAQ)
     */
    @DeleteMapping("/{questionId}")
    public ResponseEntity<Map<String, Object>> deleteQuestion(@PathVariable String questionId) {
        boolean deleted = analyticsService.deleteQuestion(questionId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Question deleted from analytics"));
    }

    // ========== Request DTOs ==========

    /**
     * Minimal request for tracking questions - only what's needed for FAQ analytics
     */
    public record TrackQuestionRequestDTO(
            String question,
            String categoryId
    ) {}

    public record CheckFaqMatchRequest(
            String question,
            String categoryId
    ) {}

    public record PromoteRequest(
            String answer,
            String promotedBy
    ) {}
}
