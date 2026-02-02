package com.naagi.rag.controller;

import com.naagi.rag.entity.AnswerFeedback;
import com.naagi.rag.repository.AnswerFeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@Slf4j
public class FeedbackController {

    private final AnswerFeedbackRepository feedbackRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> submitFeedback(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        String question = (String) request.get("question");
        String answer = (String) request.get("answer");
        Integer rating = (Integer) request.get("rating");
        String comment = (String) request.get("comment");
        String categoryId = (String) request.get("categoryId");
        String source = (String) request.get("source");

        if (sessionId == null || question == null || answer == null || rating == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId, question, answer, and rating are required"));
        }
        if (rating != 1 && rating != -1) {
            return ResponseEntity.badRequest().body(Map.of("error", "rating must be 1 (positive) or -1 (negative)"));
        }

        AnswerFeedback feedback = AnswerFeedback.builder()
                .sessionId(sessionId)
                .question(question)
                .answer(answer)
                .rating(rating)
                .comment(comment)
                .categoryId(categoryId)
                .source(source)
                .build();

        feedbackRepository.save(feedback);
        log.info("[FEEDBACK] {} feedback for session {} (source={})", rating == 1 ? "Positive" : "Negative", sessionId, source);

        return ResponseEntity.ok(Map.of("id", feedback.getId(), "status", "saved"));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long positive = feedbackRepository.countByRating(1);
        long negative = feedbackRepository.countByRating(-1);
        long total = positive + negative;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("positive", positive);
        stats.put("negative", negative);
        stats.put("positiveRate", total > 0 ? (double) positive / total : 0.0);

        // Per-category breakdown
        List<Object[]> categoryStats = feedbackRepository.countByRatingGroupedByCategory();
        Map<String, Map<String, Long>> byCategory = new HashMap<>();
        for (Object[] row : categoryStats) {
            String catId = (String) row[0];
            int r = (int) row[1];
            long count = (long) row[2];
            byCategory.computeIfAbsent(catId, k -> new HashMap<>())
                    .put(r == 1 ? "positive" : "negative", count);
        }
        stats.put("byCategory", byCategory);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/negative")
    public ResponseEntity<List<AnswerFeedback>> getNegativeFeedback() {
        return ResponseEntity.ok(feedbackRepository.findNegativeFeedback());
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<AnswerFeedback>> getSessionFeedback(@PathVariable String sessionId) {
        return ResponseEntity.ok(feedbackRepository.findBySessionIdOrderByCreatedAtDesc(sessionId));
    }
}
