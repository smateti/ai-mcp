package com.example.rag.service;

import com.example.rag.entity.QuestionFrequency;
import com.example.rag.repository.QuestionFrequencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tracks question frequency to identify which questions should be cached.
 */
@Service
public class QuestionFrequencyService {

  private final QuestionFrequencyRepository repository;

  // Threshold: only cache questions asked at least this many times
  private static final int CACHE_THRESHOLD = 2;

  public QuestionFrequencyService(QuestionFrequencyRepository repository) {
    this.repository = repository;
  }

  /**
   * Record that a question was asked and return whether it should be cached.
   *
   * @param question The question text
   * @return true if this question should be cached (asked >= CACHE_THRESHOLD times)
   */
  @Transactional
  public boolean recordAndCheckIfFrequent(String question) {
    String normalized = normalizeQuestion(question);
    String hash = hashQuestion(normalized);

    QuestionFrequency freq = repository.findByQuestionHash(hash)
        .map(existing -> {
          existing.incrementCount();
          return repository.save(existing);
        })
        .orElseGet(() -> {
          QuestionFrequency newFreq = new QuestionFrequency(hash, normalized);
          return repository.save(newFreq);
        });

    // Mark as cached if it meets the threshold
    if (freq.getAskCount() >= CACHE_THRESHOLD && !freq.getIsCached()) {
      freq.setIsCached(true);
      repository.save(freq);
    }

    return freq.getAskCount() >= CACHE_THRESHOLD;
  }

  /**
   * Get statistics about question frequency.
   */
  public Map<String, Object> getStatistics() {
    Long uniqueQuestions = repository.countUniqueQuestions();
    Long totalAsks = repository.sumTotalAsks();
    List<QuestionFrequency> top10 = repository.findTopFrequentQuestions()
        .stream()
        .limit(10)
        .collect(Collectors.toList());

    long cachedQuestions = repository.findByAskCountGreaterThanEqual(CACHE_THRESHOLD).size();

    return Map.of(
        "uniqueQuestions", uniqueQuestions != null ? uniqueQuestions : 0,
        "totalAsks", totalAsks != null ? totalAsks : 0,
        "cachedQuestions", cachedQuestions,
        "cacheThreshold", CACHE_THRESHOLD,
        "top10FrequentQuestions", top10.stream().map(q -> Map.of(
            "question", q.getQuestion(),
            "askCount", q.getAskCount(),
            "isCached", q.getIsCached()
        )).collect(Collectors.toList())
    );
  }

  /**
   * Normalize question to handle variations (case, spacing, punctuation).
   */
  private String normalizeQuestion(String question) {
    return question.toLowerCase()
        .replaceAll("\\s+", " ")
        .replaceAll("[?!.,;:]", "")
        .trim();
  }

  /**
   * Generate hash of question for database indexing.
   */
  private String hashQuestion(String question) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(question.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (Exception e) {
      throw new RuntimeException("Failed to hash question", e);
    }
  }
}
