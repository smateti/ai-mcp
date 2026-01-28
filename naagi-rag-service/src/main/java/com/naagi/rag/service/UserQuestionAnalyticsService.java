package com.naagi.rag.service;

import com.naagi.rag.config.FaqConfig;
import com.naagi.rag.entity.FaqEntry;
import com.naagi.rag.entity.UserQuestion;
import com.naagi.rag.llm.EmbeddingsClient;
import com.naagi.rag.qdrant.FaqQdrantClient;
import com.naagi.rag.qdrant.FaqQdrantClient.FaqSearchResult;
import com.naagi.rag.qdrant.UserQuestionQdrantClient;
import com.naagi.rag.qdrant.UserQuestionQdrantClient.QuestionPoint;
import com.naagi.rag.qdrant.UserQuestionQdrantClient.SimilarQuestionResult;
import com.naagi.rag.repository.FaqEntryRepository;
import com.naagi.rag.repository.UserQuestionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for FAQ analytics on user questions.
 * This is NOT for audit - it tracks unique questions with frequency for FAQ candidate identification.
 *
 * Key features:
 * - Semantic deduplication via Qdrant embeddings
 * - Frequency tracking (how often similar questions are asked)
 * - FAQ matching (does this question match an existing FAQ?)
 * - Promotion workflow (mark frequent questions as FAQ candidates)
 */
@Service
@Slf4j
public class UserQuestionAnalyticsService {

    private final UserQuestionRepository questionRepository;
    private final FaqEntryRepository faqEntryRepository;
    private final UserQuestionQdrantClient questionQdrantClient;
    private final FaqQdrantClient faqQdrantClient;
    private final EmbeddingsClient embeddingsClient;
    private final FaqConfig faqConfig;
    private final FaqManagementService faqManagementService;

    @Autowired
    public UserQuestionAnalyticsService(
            UserQuestionRepository questionRepository,
            FaqEntryRepository faqEntryRepository,
            @Autowired(required = false) UserQuestionQdrantClient questionQdrantClient,
            @Autowired(required = false) FaqQdrantClient faqQdrantClient,
            EmbeddingsClient embeddingsClient,
            FaqConfig faqConfig,
            @Lazy FaqManagementService faqManagementService) {
        this.questionRepository = questionRepository;
        this.faqEntryRepository = faqEntryRepository;
        this.questionQdrantClient = questionQdrantClient;
        this.faqQdrantClient = faqQdrantClient;
        this.embeddingsClient = embeddingsClient;
        this.faqConfig = faqConfig;
        this.faqManagementService = faqManagementService;

        if (questionQdrantClient == null) {
            log.warn("User Question Qdrant client not available - deduplication will be limited");
        }
    }

    /**
     * Track a user question for FAQ analytics.
     * If a similar question exists (>= deduplication threshold), increment its frequency.
     * Otherwise, store as a new unique question.
     *
     * Note: This does NOT duplicate audit data. Only stores what's needed for FAQ analytics.
     */
    @Transactional
    public UserQuestion trackQuestion(TrackQuestionRequest request) {
        if (!faqConfig.isStoreAllQuestions()) {
            log.debug("Question tracking disabled");
            return null;
        }

        try {
            // Generate embedding for the question
            List<Double> embedding = embeddingsClient.embed(request.question());

            // Check for similar existing questions (deduplication)
            if (questionQdrantClient != null) {
                double threshold = faqConfig.getDeduplicationThreshold();
                List<SimilarQuestionResult> similar = questionQdrantClient.findSimilarQuestions(
                        embedding, 1, threshold, request.categoryId());

                if (!similar.isEmpty()) {
                    // Found a duplicate - increment frequency
                    SimilarQuestionResult existing = similar.get(0);
                    log.debug("Found similar question (score={}): {}", existing.score(), existing.questionId());

                    UserQuestion existingQuestion = questionRepository.findById(existing.questionId()).orElse(null);
                    if (existingQuestion != null) {
                        existingQuestion.incrementFrequency();
                        questionRepository.save(existingQuestion);

                        // Update frequency in Qdrant
                        questionQdrantClient.updateFrequency(
                                existingQuestion.getQdrantPointId(),
                                existingQuestion.getFrequency());

                        return existingQuestion;
                    }
                }
            }

            // Check if question matches an existing FAQ
            String matchedFaqId = null;
            Double matchedFaqScore = null;
            if (faqQdrantClient != null) {
                List<FaqSearchResult> faqMatches = faqQdrantClient.searchFaqs(
                        embedding, 1, request.categoryId(), faqConfig.getFaqMinSimilarityScore());
                if (!faqMatches.isEmpty()) {
                    FaqSearchResult faqMatch = faqMatches.get(0);
                    matchedFaqId = faqMatch.faqId();
                    matchedFaqScore = faqMatch.score();
                    log.debug("Question matches FAQ {} with score {}", matchedFaqId, matchedFaqScore);
                }
            }

            // Create new unique question entry
            String pointId = UUID.randomUUID().toString();
            UserQuestion question = UserQuestion.builder()
                    .question(request.question())
                    .categoryId(request.categoryId())
                    .matchedFaqId(matchedFaqId)
                    .matchedFaqScore(matchedFaqScore)
                    .frequency(1)
                    .qdrantPointId(pointId)
                    .firstAskedAt(LocalDateTime.now())
                    .lastAskedAt(LocalDateTime.now())
                    .build();

            question = questionRepository.save(question);

            // Store in Qdrant for future deduplication
            if (questionQdrantClient != null) {
                QuestionPoint point = new QuestionPoint(
                        pointId,
                        embedding,
                        question.getId(),
                        question.getQuestion(),
                        question.getCategoryId(),
                        null, // categoryName not stored in simplified entity
                        null, // sourceDocId not stored
                        question.getFrequency(),
                        question.getMatchedFaqId(),
                        question.getFirstAskedAt()
                );
                questionQdrantClient.upsertQuestion(point);
            }

            log.debug("Tracked new unique question: {}", question.getId());
            return question;

        } catch (Exception e) {
            log.error("Failed to track user question", e);
            return null;
        }
    }

    /**
     * Find similar questions to a given question text
     */
    public List<SimilarQuestionResult> findSimilarQuestions(String question, double minScore, int limit) {
        if (questionQdrantClient == null) {
            return List.of();
        }

        try {
            List<Double> embedding = embeddingsClient.embed(question);
            return questionQdrantClient.findSimilarQuestions(embedding, limit, minScore);
        } catch (Exception e) {
            log.error("Failed to find similar questions", e);
            return List.of();
        }
    }

    /**
     * Get frequently asked questions with filters
     */
    public Page<UserQuestion> getFrequentlyAskedQuestions(
            String categoryId,
            Boolean hasMatchedFaq,
            Integer minFrequency,
            Pageable pageable) {
        return getFrequentlyAskedQuestions(categoryId, hasMatchedFaq, minFrequency, null, null, pageable);
    }

    /**
     * Get frequently asked questions with filters including time range
     */
    public Page<UserQuestion> getFrequentlyAskedQuestions(
            String categoryId,
            Boolean hasMatchedFaq,
            Integer minFrequency,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable) {

        return questionRepository.findWithFilters(
                categoryId,
                hasMatchedFaq,
                minFrequency,
                fromDate,
                toDate,
                pageable);
    }

    /**
     * Get questions without FAQ match (potential new FAQs)
     */
    public Page<UserQuestion> getUnmatchedQuestions(String categoryId, int minFrequency, Pageable pageable) {
        if (categoryId != null) {
            return questionRepository.findByCategoryIdAndMatchedFaqIdIsNullOrderByFrequencyDesc(categoryId, pageable);
        }
        return questionRepository.findFrequentUnmatchedQuestions(minFrequency, pageable);
    }

    /**
     * Check if a question matches an existing FAQ
     */
    public Optional<FaqMatchResult> checkFaqMatch(String question, String categoryId) {
        if (faqQdrantClient == null) {
            return Optional.empty();
        }

        try {
            List<Double> embedding = embeddingsClient.embed(question);
            List<FaqSearchResult> results = faqQdrantClient.searchFaqs(
                    embedding, 1, categoryId, faqConfig.getFaqMinSimilarityScore());

            if (!results.isEmpty()) {
                FaqSearchResult match = results.get(0);
                return Optional.of(new FaqMatchResult(
                        match.faqId(),
                        match.question(),
                        match.answer(),
                        match.score()
                ));
            }
        } catch (Exception e) {
            log.error("Failed to check FAQ match", e);
        }

        return Optional.empty();
    }

    /**
     * Promote a user question to FAQ and delete from analytics tracking.
     * This creates the FAQ in the operational database and removes the question from analytics.
     *
     * @param questionId The ID of the user question to promote
     * @param answer The answer for the FAQ (provided by admin)
     * @param promotedBy Who is promoting this FAQ
     * @return PromotionResult containing the created FAQ and deleted question
     */
    @Transactional
    public PromotionResult promoteToFaqAndDelete(String questionId, String answer, String promotedBy) {
        Optional<UserQuestion> questionOpt = questionRepository.findById(questionId);
        if (questionOpt.isEmpty()) {
            log.warn("Question not found for promotion: {}", questionId);
            return new PromotionResult(null, null, "Question not found");
        }

        UserQuestion question = questionOpt.get();
        String qdrantPointId = question.getQdrantPointId();

        log.info("Promoting question {} to FAQ and deleting from analytics", questionId);

        // Create FAQ in operational database
        FaqEntry createdFaq = faqManagementService.createFaqFromUserQuestion(
                question.getQuestion(),
                answer,
                question.getCategoryId(),
                promotedBy
        );

        if (createdFaq == null) {
            log.error("Failed to create FAQ for question: {}", questionId);
            return new PromotionResult(null, question, "Failed to create FAQ");
        }

        // Delete from user questions Qdrant collection
        if (questionQdrantClient != null && qdrantPointId != null) {
            try {
                questionQdrantClient.deleteQuestion(qdrantPointId);
                log.debug("Deleted question from Qdrant: {}", qdrantPointId);
            } catch (Exception e) {
                log.error("Failed to delete question from Qdrant: {}", qdrantPointId, e);
            }
        }

        // Delete from H2
        questionRepository.delete(question);
        log.info("Question {} promoted to FAQ {} and deleted from analytics tracking", questionId, createdFaq.getId());

        return new PromotionResult(createdFaq, question, null);
    }

    /**
     * Delete a user question from analytics tracking (without promoting to FAQ).
     * Used when admin dismisses a question as not relevant for FAQ.
     */
    @Transactional
    public boolean deleteQuestion(String questionId) {
        Optional<UserQuestion> questionOpt = questionRepository.findById(questionId);
        if (questionOpt.isEmpty()) {
            log.warn("Question not found for deletion: {}", questionId);
            return false;
        }

        UserQuestion question = questionOpt.get();
        String qdrantPointId = question.getQdrantPointId();

        // Delete from Qdrant first
        if (questionQdrantClient != null && qdrantPointId != null) {
            try {
                questionQdrantClient.deleteQuestion(qdrantPointId);
                log.debug("Deleted question from Qdrant: {}", qdrantPointId);
            } catch (Exception e) {
                log.error("Failed to delete question from Qdrant: {}", qdrantPointId, e);
            }
        }

        // Delete from H2
        questionRepository.delete(question);
        log.info("Question {} deleted from analytics tracking", questionId);
        return true;
    }

    /**
     * Get question analytics/statistics
     */
    public QuestionAnalytics getAnalytics(String categoryId) {
        long totalUniqueQuestions;
        long totalQuestionCount;
        long matchedFaqCount;
        long unmatchedCount;

        if (categoryId != null) {
            totalUniqueQuestions = questionRepository.countByCategoryId(categoryId);
            totalQuestionCount = questionRepository.getTotalQuestionCountByCategory(categoryId);
            matchedFaqCount = questionRepository.countByMatchedFaqIdIsNotNull();
            unmatchedCount = totalUniqueQuestions - matchedFaqCount;
        } else {
            totalUniqueQuestions = questionRepository.count();
            totalQuestionCount = questionRepository.getTotalQuestionCount();
            matchedFaqCount = questionRepository.countByMatchedFaqIdIsNotNull();
            unmatchedCount = questionRepository.countByMatchedFaqIdIsNull();
        }

        double faqCoverage = totalUniqueQuestions > 0
                ? (double) matchedFaqCount / totalUniqueQuestions * 100
                : 0;

        Map<String, Object> qdrantStats = questionQdrantClient != null
                ? questionQdrantClient.getCollectionStats()
                : Map.of();

        return new QuestionAnalytics(
                totalUniqueQuestions,
                totalQuestionCount,
                matchedFaqCount,
                unmatchedCount,
                faqCoverage,
                qdrantStats
        );
    }

    /**
     * Search questions by text
     */
    public Page<UserQuestion> searchQuestions(String searchText, Pageable pageable) {
        return questionRepository.searchByQuestionText(searchText, pageable);
    }

    /**
     * Get a single question by ID
     */
    public Optional<UserQuestion> getQuestionById(String questionId) {
        return questionRepository.findById(questionId);
    }

    /**
     * Get FAQ details by ID (for displaying matched FAQ answers)
     */
    public Optional<FaqEntry> getFaqDetails(String faqId) {
        try {
            Long id = Long.parseLong(faqId);
            return faqEntryRepository.findById(id);
        } catch (NumberFormatException e) {
            log.warn("Invalid FAQ ID format: {}", faqId);
            return Optional.empty();
        }
    }

    // ========== Request/Response Records ==========

    /**
     * Request to track a question - minimal data needed for FAQ analytics
     */
    public record TrackQuestionRequest(
            String question,
            String categoryId
    ) {}

    public record FaqMatchResult(
            String faqId,
            String question,
            String answer,
            double score
    ) {}

    public record QuestionAnalytics(
            long totalUniqueQuestions,
            long totalQuestionCount,
            long matchedFaqCount,
            long unmatchedCount,
            double faqCoveragePercent,
            Map<String, Object> qdrantStats
    ) {}

    public record PromotionResult(
            FaqEntry createdFaq,
            UserQuestion deletedQuestion,
            String error
    ) {
        public boolean isSuccess() {
            return createdFaq != null && error == null;
        }
    }
}
