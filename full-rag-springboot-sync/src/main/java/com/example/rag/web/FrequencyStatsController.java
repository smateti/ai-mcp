package com.example.rag.web;

import com.example.rag.service.QuestionFrequencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST API for viewing question frequency statistics.
 */
@RestController
@RequestMapping("/api/stats")
public class FrequencyStatsController {

  private final QuestionFrequencyService frequencyService;

  public FrequencyStatsController(QuestionFrequencyService frequencyService) {
    this.frequencyService = frequencyService;
  }

  /**
   * Get statistics about question frequency and caching.
   *
   * Returns:
   * - uniqueQuestions: Total number of unique questions asked
   * - totalAsks: Total number of times questions were asked
   * - cachedQuestions: Number of questions that qualify for caching
   * - cacheThreshold: Minimum times a question must be asked to be cached
   * - top10FrequentQuestions: Top 10 most frequently asked questions
   *
   * Example response:
   * {
   *   "uniqueQuestions": 50,
   *   "totalAsks": 120,
   *   "cachedQuestions": 8,
   *   "cacheThreshold": 2,
   *   "top10FrequentQuestions": [
   *     {"question": "what is 2+2", "askCount": 15, "isCached": true},
   *     {"question": "what is the capital of france", "askCount": 8, "isCached": true},
   *     ...
   *   ]
   * }
   */
  @GetMapping("/frequency")
  public ResponseEntity<Map<String, Object>> getFrequencyStatistics() {
    Map<String, Object> stats = frequencyService.getStatistics();
    return ResponseEntity.ok(stats);
  }
}
