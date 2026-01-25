package com.naag.rag.service;

import com.naag.rag.config.FaqConfig;
import com.naag.rag.entity.DocumentUpload;
import com.naag.rag.entity.FaqEntry;
import com.naag.rag.entity.FaqSettings;
import com.naag.rag.entity.GeneratedQA;
import com.naag.rag.entity.GeneratedQA.AnswerSource;
import com.naag.rag.entity.GeneratedQA.FaqStatus;
import com.naag.rag.llm.EmbeddingsClient;
import com.naag.rag.qdrant.FaqQdrantClient;
import com.naag.rag.qdrant.FaqQdrantClient.FaqPoint;
import com.naag.rag.qdrant.FaqQdrantClient.FaqSearchResult;
import com.naag.rag.repository.DocumentUploadRepository;
import com.naag.rag.repository.FaqEntryRepository;
import com.naag.rag.repository.FaqSettingsRepository;
import com.naag.rag.repository.GeneratedQARepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing FAQs - review, approval, and querying.
 */
@Service
@Slf4j
public class FaqManagementService {

    private final GeneratedQARepository qaRepository;
    private final DocumentUploadRepository uploadRepository;
    private final FaqEntryRepository faqEntryRepository;
    private final FaqSettingsRepository settingsRepository;
    private final FaqQdrantClient faqQdrantClient;
    private final EmbeddingsClient embeddingsClient;
    private final FaqConfig faqConfig;

    @Autowired
    public FaqManagementService(
            GeneratedQARepository qaRepository,
            DocumentUploadRepository uploadRepository,
            FaqEntryRepository faqEntryRepository,
            FaqSettingsRepository settingsRepository,
            @Autowired(required = false) FaqQdrantClient faqQdrantClient,
            EmbeddingsClient embeddingsClient,
            FaqConfig faqConfig) {
        this.qaRepository = qaRepository;
        this.uploadRepository = uploadRepository;
        this.faqEntryRepository = faqEntryRepository;
        this.settingsRepository = settingsRepository;
        this.faqQdrantClient = faqQdrantClient;
        this.embeddingsClient = embeddingsClient;
        this.faqConfig = faqConfig;

        if (faqQdrantClient == null) {
            log.warn("FAQ Qdrant client not available - FAQ features will be limited");
        }
    }

    /**
     * Check if FAQ query is enabled (from runtime settings)
     */
    public boolean isFaqQueryEnabled() {
        return settingsRepository.getSettings().isFaqQueryEnabled();
    }

    /**
     * Get runtime settings
     */
    public FaqSettings getSettings() {
        return settingsRepository.getSettings();
    }

    // ========== Q&A Review Methods ==========

    /**
     * Get Q&A pairs for review for a specific upload
     */
    public List<GeneratedQA> getQAPairsForReview(String uploadId) {
        return qaRepository.findByUploadIdOrderByIdAsc(uploadId);
    }

    /**
     * Update a single Q&A pair with admin edits
     */
    @Transactional
    public GeneratedQA updateQAPair(Long qaId, String editedAnswer, AnswerSource answerSource, boolean selectedForFaq) {
        GeneratedQA qa = qaRepository.findById(qaId)
                .orElseThrow(() -> new IllegalArgumentException("Q&A not found: " + qaId));

        qa.setEditedAnswer(editedAnswer);
        qa.setSelectedAnswerSource(answerSource);
        qa.setSelectedForFaq(selectedForFaq);

        return qaRepository.save(qa);
    }

    /**
     * Batch select Q&A pairs for FAQ
     */
    @Transactional
    public void selectQAPairsForFaq(String uploadId, List<Long> qaIds, boolean selected) {
        List<GeneratedQA> qas = qaRepository.findAllById(qaIds);
        for (GeneratedQA qa : qas) {
            if (qa.getUploadId().equals(uploadId)) {
                qa.setSelectedForFaq(selected);
            }
        }
        qaRepository.saveAll(qas);
    }

    /**
     * Auto-select Q&A pairs with validation score >= threshold
     */
    @Transactional
    public int autoSelectPassedQAPairs(String uploadId) {
        List<GeneratedQA> qas = qaRepository.findByUploadIdOrderByIdAsc(uploadId);
        double threshold = faqConfig.getFaqAutoSelectThreshold();

        int count = 0;
        for (GeneratedQA qa : qas) {
            if (qa.getSimilarityScore() != null && qa.getSimilarityScore() >= threshold) {
                qa.setSelectedForFaq(true);
                if (qa.getSelectedAnswerSource() == null) {
                    // Default to expected answer for passed validations
                    qa.setSelectedAnswerSource(AnswerSource.EXPECTED);
                }
                count++;
            }
        }
        qaRepository.saveAll(qas);
        return count;
    }

    /**
     * Approve selected FAQs and push to Qdrant
     * @return Result containing count of approved FAQs and any errors
     */
    @Transactional
    public FaqApprovalResult approveSelectedFaqs(String uploadId, String approvedBy) {
        if (faqQdrantClient == null) {
            return new FaqApprovalResult(0, List.of("FAQ Qdrant client not available"));
        }

        DocumentUpload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + uploadId));

        List<GeneratedQA> selectedQas = qaRepository.findByUploadIdOrderByIdAsc(uploadId).stream()
                .filter(qa -> Boolean.TRUE.equals(qa.getSelectedForFaq()))
                .filter(qa -> qa.getFaqStatus() != FaqStatus.APPROVED)
                .collect(Collectors.toList());

        if (selectedQas.isEmpty()) {
            return new FaqApprovalResult(0, List.of("No Q&A pairs selected for FAQ approval"));
        }

        List<String> errors = new ArrayList<>();
        List<FaqPoint> faqPoints = new ArrayList<>();
        List<FaqEntry> faqEntries = new ArrayList<>();

        for (GeneratedQA qa : selectedQas) {
            try {
                String question = qa.getQuestion();
                String answer = qa.getFinalAnswer();

                // Generate embedding for the question
                List<Double> embedding = embeddingsClient.embed(question);

                // Create unique point ID
                String pointId = UUID.randomUUID().toString();

                // Create FAQ point for Qdrant
                FaqPoint point = new FaqPoint(
                        pointId,
                        embedding,
                        String.valueOf(qa.getId()),
                        question,
                        answer,
                        upload.getCategoryId(),
                        null, // categoryName - will be added later if available
                        upload.getDocId(),
                        upload.getTitle(),
                        uploadId,
                        qa.getQuestionType().name(),
                        approvedBy,
                        LocalDateTime.now()
                );
                faqPoints.add(point);

                // Create FAQ entry in database
                FaqEntry entry = FaqEntry.builder()
                        .categoryId(upload.getCategoryId())
                        .docId(upload.getDocId())
                        .uploadId(uploadId)
                        .questionType(qa.getQuestionType().name())
                        .question(question)
                        .answer(answer)
                        .similarityScore(qa.getSimilarityScore())
                        .qdrantPointId(pointId)
                        .active(true)
                        .build();
                faqEntries.add(entry);

                // Update Q&A with approval info
                qa.setFaqStatus(FaqStatus.APPROVED);
                qa.setFaqApprovedAt(LocalDateTime.now());
                qa.setFaqApprovedBy(approvedBy);
                qa.setFaqQdrantPointId(pointId);

            } catch (Exception e) {
                log.error("Failed to process Q&A {} for FAQ approval", qa.getId(), e);
                errors.add("Failed to process Q&A " + qa.getId() + ": " + e.getMessage());
            }
        }

        // Batch upsert to Qdrant
        if (!faqPoints.isEmpty()) {
            try {
                faqQdrantClient.upsertFaqs(faqPoints);
                log.info("Upserted {} FAQs to Qdrant for upload {}", faqPoints.size(), uploadId);
            } catch (Exception e) {
                log.error("Failed to upsert FAQs to Qdrant", e);
                errors.add("Failed to store FAQs in Qdrant: " + e.getMessage());
                // Don't save to DB if Qdrant failed
                return new FaqApprovalResult(0, errors);
            }
        }

        // Save to database
        qaRepository.saveAll(selectedQas);
        faqEntryRepository.saveAll(faqEntries);

        return new FaqApprovalResult(faqPoints.size(), errors);
    }

    // ========== FAQ CRUD Methods ==========

    /**
     * Get all FAQs with pagination
     */
    public Page<FaqEntry> getAllFaqs(Pageable pageable) {
        return faqEntryRepository.findByActiveTrue(pageable);
    }

    /**
     * Get FAQs by category
     */
    public Page<FaqEntry> getFaqsByCategory(String categoryId, Pageable pageable) {
        return faqEntryRepository.findByCategoryIdAndActiveTrue(categoryId, pageable);
    }

    /**
     * Get FAQs by document
     */
    public List<FaqEntry> getFaqsByDocument(String docId) {
        return faqEntryRepository.findByDocIdAndActiveTrue(docId);
    }

    /**
     * Get FAQs by upload
     */
    public List<FaqEntry> getFaqsByUpload(String uploadId) {
        return faqEntryRepository.findByUploadIdAndActiveTrue(uploadId);
    }

    /**
     * Get a single FAQ by ID
     */
    public Optional<FaqEntry> getFaqById(Long faqId) {
        return faqEntryRepository.findById(faqId);
    }

    /**
     * Update an existing FAQ
     */
    @Transactional
    public FaqEntry updateFaq(Long faqId, String question, String answer) {
        FaqEntry faq = faqEntryRepository.findById(faqId)
                .orElseThrow(() -> new IllegalArgumentException("FAQ not found: " + faqId));

        boolean questionChanged = !faq.getQuestion().equals(question);

        faq.setQuestion(question);
        faq.setAnswer(answer);

        // If question changed, update embedding in Qdrant
        if (questionChanged && faqQdrantClient != null) {
            try {
                // Find the Qdrant point ID from GeneratedQA
                GeneratedQA qa = qaRepository.findById(faqId).orElse(null);
                if (qa != null && qa.getFaqQdrantPointId() != null) {
                    List<Double> embedding = embeddingsClient.embed(question);
                    FaqPoint point = new FaqPoint(
                            qa.getFaqQdrantPointId(),
                            embedding,
                            String.valueOf(faqId),
                            question,
                            answer,
                            faq.getCategoryId(),
                            null,
                            faq.getDocId(),
                            null,
                            faq.getUploadId(),
                            faq.getQuestionType(),
                            null,
                            faq.getCreatedAt()
                    );
                    faqQdrantClient.upsertFaq(point);
                }
            } catch (Exception e) {
                log.error("Failed to update FAQ in Qdrant", e);
            }
        }

        return faqEntryRepository.save(faq);
    }

    /**
     * Deactivate a FAQ (soft delete)
     */
    @Transactional
    public void deactivateFaq(Long faqId) {
        FaqEntry faq = faqEntryRepository.findById(faqId)
                .orElseThrow(() -> new IllegalArgumentException("FAQ not found: " + faqId));

        faq.setActive(false);
        faqEntryRepository.save(faq);

        // Delete from Qdrant using the FaqEntry's qdrantPointId
        if (faqQdrantClient != null) {
            try {
                String pointId = faq.getQdrantPointId();
                if (pointId != null && !pointId.isEmpty()) {
                    faqQdrantClient.deleteFaq(pointId);
                    log.info("Deleted FAQ {} from Qdrant (pointId: {})", faqId, pointId);
                } else {
                    // Fallback: try to find via GeneratedQA (for backward compatibility)
                    GeneratedQA qa = qaRepository.findById(faqId).orElse(null);
                    if (qa != null && qa.getFaqQdrantPointId() != null) {
                        faqQdrantClient.deleteFaq(qa.getFaqQdrantPointId());
                        log.info("Deleted FAQ {} from Qdrant via GeneratedQA fallback", faqId);
                    } else {
                        // Last resort: delete by faqId payload filter
                        faqQdrantClient.deleteFaqsByFaqId(String.valueOf(faqId));
                        log.info("Deleted FAQ {} from Qdrant via faqId filter fallback", faqId);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to delete FAQ {} from Qdrant", faqId, e);
            }
        }
    }

    /**
     * Reactivate a FAQ
     */
    @Transactional
    public void reactivateFaq(Long faqId) {
        FaqEntry faq = faqEntryRepository.findById(faqId)
                .orElseThrow(() -> new IllegalArgumentException("FAQ not found: " + faqId));

        faq.setActive(true);
        faqEntryRepository.save(faq);

        // Re-add to Qdrant
        if (faqQdrantClient != null) {
            try {
                List<Double> embedding = embeddingsClient.embed(faq.getQuestion());
                String pointId = UUID.randomUUID().toString();

                FaqPoint point = new FaqPoint(
                        pointId,
                        embedding,
                        String.valueOf(faqId),
                        faq.getQuestion(),
                        faq.getAnswer(),
                        faq.getCategoryId(),
                        null,
                        faq.getDocId(),
                        null,
                        faq.getUploadId(),
                        faq.getQuestionType(),
                        null,
                        faq.getCreatedAt()
                );
                faqQdrantClient.upsertFaq(point);

                // Update point ID in FaqEntry
                faq.setQdrantPointId(pointId);
                faqEntryRepository.save(faq);

                // Update point ID in GeneratedQA if exists
                GeneratedQA qa = qaRepository.findById(faqId).orElse(null);
                if (qa != null) {
                    qa.setFaqQdrantPointId(pointId);
                    qaRepository.save(qa);
                }
            } catch (Exception e) {
                log.error("Failed to re-add FAQ to Qdrant", e);
            }
        }
    }

    // ========== FAQ Query Methods ==========

    /**
     * Query FAQs by question text (semantic search).
     * This method always queries regardless of settings - use queryFaqsIfEnabled() for setting-aware query.
     */
    public List<FaqSearchResult> queryFaqs(String question, String categoryId, int limit) {
        if (faqQdrantClient == null) {
            log.warn("FAQ Qdrant client not available");
            return List.of();
        }

        try {
            List<Double> embedding = embeddingsClient.embed(question);
            FaqSettings settings = settingsRepository.getSettings();
            double minScore = settings.getMinSimilarityScore();
            return faqQdrantClient.searchFaqs(embedding, limit, categoryId, minScore);
        } catch (Exception e) {
            log.error("Failed to query FAQs", e);
            return List.of();
        }
    }

    /**
     * Query FAQs only if FAQ query is enabled in settings.
     * Returns empty list if FAQ query is disabled.
     */
    public List<FaqSearchResult> queryFaqsIfEnabled(String question, String categoryId, int limit) {
        if (!isFaqQueryEnabled()) {
            log.debug("FAQ query is disabled - skipping FAQ search");
            return List.of();
        }
        return queryFaqs(question, categoryId, limit);
    }

    /**
     * Find best matching FAQ for a question.
     * This method always queries regardless of settings - use findBestMatchIfEnabled() for setting-aware query.
     */
    public Optional<FaqSearchResult> findBestMatch(String question, String categoryId) {
        List<FaqSearchResult> results = queryFaqs(question, categoryId, 1);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find best matching FAQ only if FAQ query is enabled in settings.
     * Returns empty if FAQ query is disabled or no match found.
     */
    public Optional<FaqSearchResult> findBestMatchIfEnabled(String question, String categoryId) {
        if (!isFaqQueryEnabled()) {
            log.debug("FAQ query is disabled - skipping FAQ match");
            return Optional.empty();
        }
        return findBestMatch(question, categoryId);
    }

    /**
     * Search FAQs by text (keyword search in database)
     */
    public Page<FaqEntry> searchFaqs(String searchText, Pageable pageable) {
        return faqEntryRepository.searchByQuestionOrAnswer(searchText, pageable);
    }

    /**
     * Record FAQ access (increment counter)
     */
    @Transactional
    public void recordFaqAccess(Long faqId) {
        faqEntryRepository.findById(faqId).ifPresent(faq -> {
            faq.incrementAccessCount();
            faqEntryRepository.save(faq);
        });
    }

    /**
     * Find FAQs that are semantically similar to a given FAQ.
     * Uses the FAQ's question to find other FAQs asking about the same thing.
     *
     * @param faqId The source FAQ ID to find similar FAQs for
     * @param minScore Minimum similarity score (0.0 to 1.0), e.g., 0.90 for 90%+
     * @param limit Maximum number of similar FAQs to return
     * @return List of similar FAQs with their details and similarity scores
     */
    public List<Map<String, Object>> findSimilarFaqs(Long faqId, double minScore, int limit) {
        if (faqQdrantClient == null) {
            log.warn("FAQ Qdrant client not available - cannot find similar FAQs");
            return List.of();
        }

        // Get the source FAQ
        FaqEntry sourceFaq = faqEntryRepository.findById(faqId)
                .orElseThrow(() -> new IllegalArgumentException("FAQ not found: " + faqId));

        try {
            // Generate embedding for the source question
            List<Double> embedding = embeddingsClient.embed(sourceFaq.getQuestion());

            // Search for similar FAQs (get more than limit to filter out self)
            List<FaqSearchResult> results = faqQdrantClient.searchFaqs(
                    embedding,
                    limit + 5, // Get extra to account for filtering out self
                    null, // No category filter - search across all
                    minScore
            );

            // Convert to response format, filtering out the source FAQ itself
            List<Map<String, Object>> similarFaqs = new ArrayList<>();
            for (FaqSearchResult result : results) {
                // Skip the source FAQ itself
                if (result.faqId() != null && result.faqId().equals(String.valueOf(faqId))) {
                    continue;
                }

                // Try to get the database entry for additional info
                Long resultFaqId = null;
                try {
                    resultFaqId = Long.parseLong(result.faqId());
                } catch (NumberFormatException e) {
                    // faqId might not be numeric for promoted FAQs
                }

                Map<String, Object> faqMap = new HashMap<>();
                faqMap.put("faqId", result.faqId());
                faqMap.put("question", result.question());
                faqMap.put("answer", result.answer());
                faqMap.put("categoryId", result.categoryId());
                faqMap.put("categoryName", result.categoryName());
                faqMap.put("score", result.score());
                faqMap.put("scorePercent", Math.round(result.score() * 100));

                // Get database info if available
                if (resultFaqId != null) {
                    faqEntryRepository.findById(resultFaqId).ifPresent(entry -> {
                        faqMap.put("docId", entry.getDocId());
                        faqMap.put("active", entry.isActive());
                        faqMap.put("accessCount", entry.getAccessCount());
                    });
                }

                similarFaqs.add(faqMap);

                if (similarFaqs.size() >= limit) {
                    break;
                }
            }

            log.info("Found {} similar FAQs for FAQ {} with minScore {}",
                    similarFaqs.size(), faqId, minScore);
            return similarFaqs;

        } catch (Exception e) {
            log.error("Failed to find similar FAQs for FAQ {}", faqId, e);
            return List.of();
        }
    }

    /**
     * Merge FAQs by copying the answer from source FAQ to target FAQs.
     * This allows multiple similar questions to share the same answer.
     *
     * @param sourceFaqId The FAQ ID whose answer should be copied
     * @param targetFaqIds List of FAQ IDs to receive the answer
     * @return Number of FAQs that were updated
     */
    @Transactional
    public int mergeFaqs(Long sourceFaqId, List<Long> targetFaqIds) {
        // Get the source FAQ
        FaqEntry sourceFaq = faqEntryRepository.findById(sourceFaqId)
                .orElseThrow(() -> new IllegalArgumentException("Source FAQ not found: " + sourceFaqId));

        String sourceAnswer = sourceFaq.getAnswer();
        int mergedCount = 0;

        for (Long targetFaqId : targetFaqIds) {
            if (targetFaqId.equals(sourceFaqId)) {
                // Skip if trying to merge with self
                continue;
            }

            try {
                Optional<FaqEntry> targetOpt = faqEntryRepository.findById(targetFaqId);
                if (targetOpt.isEmpty()) {
                    log.warn("Target FAQ not found for merge: {}", targetFaqId);
                    continue;
                }

                FaqEntry targetFaq = targetOpt.get();

                // Update the answer
                targetFaq.setAnswer(sourceAnswer);
                faqEntryRepository.save(targetFaq);

                // Update in Qdrant if available
                if (faqQdrantClient != null) {
                    try {
                        // Find the Qdrant point for this FAQ
                        GeneratedQA qa = qaRepository.findById(targetFaqId).orElse(null);
                        if (qa != null && qa.getFaqQdrantPointId() != null) {
                            // Re-generate embedding (question unchanged, answer updated)
                            List<Double> embedding = embeddingsClient.embed(targetFaq.getQuestion());
                            FaqPoint point = new FaqPoint(
                                    qa.getFaqQdrantPointId(),
                                    embedding,
                                    String.valueOf(targetFaqId),
                                    targetFaq.getQuestion(),
                                    sourceAnswer,
                                    targetFaq.getCategoryId(),
                                    null,
                                    targetFaq.getDocId(),
                                    null,
                                    targetFaq.getUploadId(),
                                    targetFaq.getQuestionType(),
                                    null,
                                    targetFaq.getCreatedAt()
                            );
                            faqQdrantClient.upsertFaq(point);
                        }
                    } catch (Exception e) {
                        log.error("Failed to update FAQ {} in Qdrant during merge", targetFaqId, e);
                    }
                }

                log.info("Merged FAQ {} with source FAQ {} - answer updated", targetFaqId, sourceFaqId);
                mergedCount++;

            } catch (Exception e) {
                log.error("Failed to merge FAQ {}", targetFaqId, e);
            }
        }

        log.info("Merged {} FAQs with source FAQ {}", mergedCount, sourceFaqId);
        return mergedCount;
    }

    /**
     * Get FAQ statistics
     */
    public Map<String, Object> getFaqStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFaqs", faqEntryRepository.countByActiveTrue());
        stats.put("totalAccess", faqEntryRepository.sumAccessCount());

        if (faqQdrantClient != null) {
            stats.putAll(faqQdrantClient.getCollectionStats());
        }

        return stats;
    }

    // ========== Create FAQ from User Question ==========

    /**
     * Create a new FAQ directly from a user question (promotion flow).
     * This is used when admin identifies a frequently asked question and wants to add it as an FAQ.
     *
     * @param question The question text
     * @param answer The answer provided by admin
     * @param categoryId The category for the FAQ
     * @param createdBy Who created this FAQ
     * @return The created FAQ entry, or null if creation failed
     */
    @Transactional
    public FaqEntry createFaqFromUserQuestion(String question, String answer, String categoryId, String createdBy) {
        if (faqQdrantClient == null) {
            log.error("FAQ Qdrant client not available - cannot create FAQ");
            return null;
        }

        try {
            // Generate embedding for the question
            List<Double> embedding = embeddingsClient.embed(question);

            // Create unique point ID
            String pointId = UUID.randomUUID().toString();

            // Create FAQ point for Qdrant
            FaqPoint point = new FaqPoint(
                    pointId,
                    embedding,
                    pointId, // Use pointId as qaId since there's no GeneratedQA
                    question,
                    answer,
                    categoryId,
                    null, // categoryName
                    "USER_QUESTION", // docId - indicates source is user question
                    "Promoted from User Questions", // title
                    null, // uploadId - not from upload
                    "USER_PROMOTED", // questionType
                    createdBy,
                    LocalDateTime.now()
            );

            // Upsert to Qdrant
            faqQdrantClient.upsertFaq(point);
            log.info("Created FAQ in Qdrant from user question: {}", pointId);

            // Create FAQ entry in database
            FaqEntry entry = FaqEntry.builder()
                    .categoryId(categoryId)
                    .docId("USER_QUESTION")
                    .uploadId(null)
                    .questionType("USER_PROMOTED")
                    .question(question)
                    .answer(answer)
                    .similarityScore(null)
                    .qdrantPointId(pointId)
                    .active(true)
                    .build();

            entry = faqEntryRepository.save(entry);
            log.info("Created FAQ entry in database: {}", entry.getId());

            return entry;

        } catch (Exception e) {
            log.error("Failed to create FAQ from user question", e);
            return null;
        }
    }

    // ========== Result Classes ==========

    public record FaqApprovalResult(int approvedCount, List<String> errors) {
        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }
    }
}
