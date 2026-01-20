package com.naag.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naag.rag.chunk.HybridChunker;
import com.naag.rag.entity.DocumentUpload;
import com.naag.rag.entity.DocumentUpload.ProcessingStatus;
import com.naag.rag.entity.GeneratedQA;
import com.naag.rag.entity.GeneratedQA.QuestionType;
import com.naag.rag.entity.GeneratedQA.ValidationStatus;
import com.naag.rag.llm.ChatClient;
import com.naag.rag.llm.EmbeddingsClient;
import com.naag.rag.qdrant.QdrantClient;
import com.naag.rag.qdrant.QdrantClient.Point;
import com.naag.rag.repository.DocumentUploadRepository;
import com.naag.rag.repository.GeneratedQARepository;
import com.naag.rag.sse.SseNotificationService;
import com.naag.rag.sse.SseNotificationService.UploadEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final DocumentUploadRepository uploadRepository;
    private final GeneratedQARepository qaRepository;
    private final ChatClient chatClient;
    private final EmbeddingsClient embeddingsClient;
    private final TempCollectionService tempCollectionService;
    private final SseNotificationService sseService;
    private final ObjectMapper objectMapper;
    private final FaqCacheService faqCacheService;

    @Value("${naag.rag.chunking.maxChars:1200}")
    private int maxChars;

    @Value("${naag.rag.chunking.overlapChars:200}")
    private int overlapChars;

    @Value("${naag.rag.chunking.minChars:100}")
    private int minChars;

    @Value("${naag.rag.qa-generation.fine-grain-count:5}")
    private int fineGrainCount;

    @Value("${naag.rag.qa-generation.summary-count:3}")
    private int summaryCount;

    @Transactional
    public DocumentUpload initiateUpload(String docId, String title, String content, String categoryId) {
        String uploadId = UUID.randomUUID().toString();

        DocumentUpload upload = DocumentUpload.builder()
                .id(uploadId)
                .docId(docId)
                .title(title)
                .originalContent(content)
                .categoryId(categoryId)
                .status(ProcessingStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        return uploadRepository.save(upload);
    }

    @Async("documentProcessingExecutor")
    public CompletableFuture<Void> processDocumentAsync(String uploadId) {
        log.info("Starting async processing for upload: {}", uploadId);

        try {
            DocumentUpload upload = uploadRepository.findById(uploadId)
                    .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + uploadId));

            upload.setStatus(ProcessingStatus.GENERATING_QA);
            upload.setProcessingStartedAt(LocalDateTime.now());
            uploadRepository.save(upload);

            sseService.notifyUploadProgress(uploadId, UploadEvent.processing(
                    uploadId, "Generating Q&A pairs from document...", 10, 100));

            // Step 1: Generate Q&A pairs from the document
            List<GeneratedQA> qaList = generateQAPairs(upload);
            upload.setQuestionsGenerated(qaList.size());
            uploadRepository.save(upload);

            sseService.notifyUploadProgress(uploadId, UploadEvent.processing(
                    uploadId, "Generated " + qaList.size() + " Q&A pairs. Chunking document...", 30, 100));

            // Step 2: Chunk and store in temp collection
            upload.setStatus(ProcessingStatus.CHUNKING_TEMP);
            uploadRepository.save(upload);

            int chunkCount = tempCollectionService.chunkAndStoreTemp(upload);
            upload.setTotalChunks(chunkCount);
            uploadRepository.save(upload);

            sseService.notifyUploadProgress(uploadId, UploadEvent.processing(
                    uploadId, "Created " + chunkCount + " chunks. Validating Q&A with RAG...", 50, 100));

            // Step 3: Validate Q&A pairs using temp collection
            upload.setStatus(ProcessingStatus.VALIDATING_QA);
            uploadRepository.save(upload);

            validateQAPairs(upload, qaList);
            upload.setQuestionsValidated(qaList.size());
            uploadRepository.save(upload);

            sseService.notifyUploadProgress(uploadId, UploadEvent.processing(
                    uploadId, "Validation complete. Ready for review.", 90, 100));

            // Step 4: Mark as ready for review
            upload.setStatus(ProcessingStatus.READY_FOR_REVIEW);
            upload.setProcessingCompletedAt(LocalDateTime.now());
            uploadRepository.save(upload);

            // Calculate validation stats
            long passed = qaRepository.countByUploadIdAndValidationStatus(uploadId, ValidationStatus.PASSED);
            long failed = qaRepository.countByUploadIdAndValidationStatus(uploadId, ValidationStatus.FAILED);
            Double avgScore = qaRepository.getAverageSimilarityScore(uploadId);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalQuestions", qaList.size());
            stats.put("passed", passed);
            stats.put("failed", failed);
            stats.put("averageScore", avgScore != null ? String.format("%.2f", avgScore) : "N/A");
            stats.put("chunkCount", chunkCount);

            sseService.notifyUploadProgress(uploadId, UploadEvent.completed(
                    uploadId, "READY_FOR_REVIEW",
                    "Document processing complete. Ready for review.",
                    stats));

            log.info("Document processing completed for upload: {}", uploadId);

        } catch (Exception e) {
            log.error("Error processing document upload: {}", uploadId, e);

            DocumentUpload upload = uploadRepository.findById(uploadId).orElse(null);
            if (upload != null) {
                upload.setStatus(ProcessingStatus.FAILED);
                upload.setErrorMessage(e.getMessage());
                upload.setProcessingCompletedAt(LocalDateTime.now());
                uploadRepository.save(upload);
            }

            sseService.notifyUploadProgress(uploadId, UploadEvent.error(
                    uploadId, "Processing failed: " + e.getMessage()));
        }

        return CompletableFuture.completedFuture(null);
    }

    private List<GeneratedQA> generateQAPairs(DocumentUpload upload) {
        List<GeneratedQA> allQA = new ArrayList<>();

        // Generate fine-grain questions (detailed, specific)
        log.info("Generating {} fine-grain Q&A pairs for upload {}", fineGrainCount, upload.getId());
        String fineGrainPrompt = buildFineGrainPrompt(upload.getOriginalContent(), fineGrainCount);
        try {
            String fineGrainResponse = chatClient.chatOnce(fineGrainPrompt, 0.3, 2048);
            log.info("LLM fine-grain response length: {} chars", fineGrainResponse != null ? fineGrainResponse.length() : 0);
            List<GeneratedQA> fineGrainQA = parseQAFromLLM(fineGrainResponse, upload.getId(), QuestionType.FINE_GRAIN);
            allQA.addAll(fineGrainQA);
        } catch (Exception e) {
            log.error("Failed to generate fine-grain Q&A for upload {}: {}", upload.getId(), e.getMessage());
        }

        // Generate summary questions (high-level, conceptual)
        log.info("Generating {} summary Q&A pairs for upload {}", summaryCount, upload.getId());
        String summaryPrompt = buildSummaryPrompt(upload.getOriginalContent(), summaryCount);
        try {
            String summaryResponse = chatClient.chatOnce(summaryPrompt, 0.3, 2048);
            log.info("LLM summary response length: {} chars", summaryResponse != null ? summaryResponse.length() : 0);
            List<GeneratedQA> summaryQA = parseQAFromLLM(summaryResponse, upload.getId(), QuestionType.SUMMARY);
            allQA.addAll(summaryQA);
        } catch (Exception e) {
            log.error("Failed to generate summary Q&A for upload {}: {}", upload.getId(), e.getMessage());
        }

        // Save all Q&A pairs
        for (GeneratedQA qa : allQA) {
            qa.setGeneratedAt(LocalDateTime.now());
            qa.setValidationStatus(ValidationStatus.PENDING);
            qaRepository.save(qa);
        }

        log.info("Generated {} Q&A pairs for upload {} ({} fine-grain, {} summary)",
                allQA.size(), upload.getId(),
                allQA.stream().filter(qa -> qa.getQuestionType() == QuestionType.FINE_GRAIN).count(),
                allQA.stream().filter(qa -> qa.getQuestionType() == QuestionType.SUMMARY).count());
        return allQA;
    }

    private String buildFineGrainPrompt(String content, int count) {
        return """
                Generate %d detailed Q&A pairs from this document. Each Q&A should test specific facts, numbers, or details.

                Document:
                %s

                IMPORTANT: Respond with ONLY a JSON array. No explanation, no markdown, just the array starting with [ and ending with ].

                Example format:
                [{"question": "What is X?", "answer": "X is..."},{"question": "How many Y?", "answer": "There are..."}]

                Generate exactly %d pairs now:""".formatted(count, truncateForContext(content), count);
    }

    private String buildSummaryPrompt(String content, int count) {
        return """
                Generate %d high-level Q&A pairs from this document. Each Q&A should test understanding of main concepts and themes.

                Document:
                %s

                IMPORTANT: Respond with ONLY a JSON array. No explanation, no markdown, just the array starting with [ and ending with ].

                Example format:
                [{"question": "What is the main purpose?", "answer": "The purpose is..."},{"question": "What are the key themes?", "answer": "The themes are..."}]

                Generate exactly %d pairs now:""".formatted(count, truncateForContext(content), count);
    }

    private String truncateForContext(String content) {
        // Truncate to fit within model context
        int maxLength = 6000;
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "\n...[truncated]";
    }

    private List<GeneratedQA> parseQAFromLLM(String response, String uploadId, QuestionType type) {
        List<GeneratedQA> result = new ArrayList<>();

        try {
            // Log the raw response for debugging
            log.debug("LLM response for {} ({}): {}", uploadId, type,
                    response != null ? response.substring(0, Math.min(500, response.length())) : "null");

            if (response == null || response.isBlank()) {
                log.warn("Empty LLM response for upload {} ({})", uploadId, type);
                return result;
            }

            // Clean up response - extract JSON array
            String jsonStr = extractJsonArray(response);
            if (jsonStr == null) {
                log.warn("Could not extract JSON array from LLM response for {} ({}). Response: {}",
                        uploadId, type, response.substring(0, Math.min(200, response.length())));

                // Try to parse markdown code blocks
                jsonStr = extractJsonFromMarkdown(response);
                if (jsonStr == null) {
                    log.error("LLM did not return valid JSON for {}. Full response: {}", uploadId, response);
                    return result;
                }
            }

            List<Map<String, String>> qaList = objectMapper.readValue(
                    jsonStr, new TypeReference<List<Map<String, String>>>() {});

            for (Map<String, String> qa : qaList) {
                String question = qa.get("question");
                String answer = qa.get("answer");

                if (question != null && !question.isBlank() && answer != null && !answer.isBlank()) {
                    result.add(GeneratedQA.builder()
                            .uploadId(uploadId)
                            .questionType(type)
                            .question(question.trim())
                            .expectedAnswer(answer.trim())
                            .build());
                }
            }

            log.info("Parsed {} Q&A pairs of type {} for upload {}", result.size(), type, uploadId);
        } catch (Exception e) {
            log.error("Failed to parse Q&A from LLM response for upload {} ({}): {}. Response: {}",
                    uploadId, type, e.getMessage(),
                    response != null ? response.substring(0, Math.min(300, response.length())) : "null");
        }

        return result;
    }

    private String extractJsonArray(String response) {
        // Try to find JSON array in the response
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');

        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return null;
    }

    private String extractJsonFromMarkdown(String response) {
        // Try to extract JSON from markdown code blocks like ```json ... ``` or ``` ... ```
        Pattern codeBlockPattern = Pattern.compile("```(?:json)?\\s*\\n?([\\s\\S]*?)```");
        Matcher matcher = codeBlockPattern.matcher(response);

        while (matcher.find()) {
            String content = matcher.group(1).trim();
            // Check if it looks like a JSON array
            if (content.startsWith("[") && content.endsWith("]")) {
                return content;
            }
            // Also try to extract array from within the code block
            String jsonArray = extractJsonArray(content);
            if (jsonArray != null) {
                return jsonArray;
            }
        }

        return null;
    }

    private void validateQAPairs(DocumentUpload upload, List<GeneratedQA> qaList) {
        String tempCollection = tempCollectionService.getTempCollectionName(upload.getId());

        for (int i = 0; i < qaList.size(); i++) {
            GeneratedQA qa = qaList.get(i);

            try {
                // Query the temp collection with the question
                String ragAnswer = tempCollectionService.queryTemp(
                        upload.getId(),
                        qa.getQuestion(),
                        5  // topK
                );

                qa.setRagAnswer(ragAnswer);

                // Calculate similarity score between expected and RAG answer
                double similarity = calculateAnswerSimilarity(qa.getExpectedAnswer(), ragAnswer);
                qa.setSimilarityScore(similarity);

                // Determine validation status based on similarity
                if (similarity >= 0.7) {
                    qa.setValidationStatus(ValidationStatus.PASSED);
                } else if (similarity >= 0.4) {
                    qa.setValidationStatus(ValidationStatus.PENDING);  // Needs review
                } else {
                    qa.setValidationStatus(ValidationStatus.FAILED);
                }

                qa.setValidatedAt(LocalDateTime.now());
                qaRepository.save(qa);

                // Update progress
                int progress = 50 + (int) ((i + 1.0) / qaList.size() * 40);
                sseService.notifyUploadProgress(upload.getId(), UploadEvent.processing(
                        upload.getId(),
                        "Validating Q&A " + (i + 1) + "/" + qaList.size(),
                        progress, 100));

            } catch (Exception e) {
                log.error("Failed to validate Q&A {}: {}", qa.getId(), e.getMessage());
                qa.setValidationStatus(ValidationStatus.FAILED);
                qa.setValidatedAt(LocalDateTime.now());
                qaRepository.save(qa);
            }
        }
    }

    private double calculateAnswerSimilarity(String expected, String actual) {
        if (expected == null || actual == null) {
            return 0.0;
        }

        // Use embedding similarity for semantic comparison
        try {
            List<Double> expectedVec = embeddingsClient.embed(expected);
            List<Double> actualVec = embeddingsClient.embed(actual);

            return cosineSimilarity(expectedVec, actualVec);
        } catch (Exception e) {
            log.warn("Failed to calculate embedding similarity, falling back to word overlap");
            return wordOverlapSimilarity(expected, actual);
        }
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            dotProduct += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double wordOverlapSimilarity(String a, String b) {
        Set<String> wordsA = new HashSet<>(Arrays.asList(a.toLowerCase().split("\\W+")));
        Set<String> wordsB = new HashSet<>(Arrays.asList(b.toLowerCase().split("\\W+")));

        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);

        Set<String> union = new HashSet<>(wordsA);
        union.addAll(wordsB);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }

    @Transactional
    public void moveToRag(String uploadId) {
        DocumentUpload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + uploadId));

        if (upload.getStatus() != ProcessingStatus.READY_FOR_REVIEW &&
            upload.getStatus() != ProcessingStatus.APPROVED) {
            throw new IllegalStateException("Upload is not ready for RAG: " + upload.getStatus());
        }

        try {
            // Move from temp to actual RAG collection
            tempCollectionService.moveToMainCollection(upload);

            upload.setStatus(ProcessingStatus.MOVED_TO_RAG);
            upload.setMovedToRagAt(LocalDateTime.now());
            uploadRepository.save(upload);

            // Store Q&A pairs in FAQ repository for caching
            faqCacheService.storeFaqsFromUpload(uploadId, upload.getDocId(), upload.getCategoryId());
            log.info("Stored FAQs from upload {} for category {}", uploadId, upload.getCategoryId());

            // Cleanup temp collection
            tempCollectionService.deleteTempCollection(upload.getId());

            sseService.notifyUploadProgress(uploadId, UploadEvent.completed(
                    uploadId, "MOVED_TO_RAG",
                    "Document successfully added to RAG knowledge base.",
                    Map.of("docId", upload.getDocId(), "chunks", upload.getTotalChunks())));

            log.info("Document {} moved to RAG collection", uploadId);

        } catch (Exception e) {
            log.error("Failed to move document to RAG: {}", uploadId, e);
            upload.setStatus(ProcessingStatus.FAILED);
            upload.setErrorMessage("Failed to move to RAG: " + e.getMessage());
            uploadRepository.save(upload);

            sseService.notifyUploadProgress(uploadId, UploadEvent.error(
                    uploadId, "Failed to move to RAG: " + e.getMessage()));

            throw new RuntimeException("Failed to move to RAG", e);
        }
    }

    @Transactional
    public void deleteUpload(String uploadId) {
        DocumentUpload upload = uploadRepository.findById(uploadId).orElse(null);

        if (upload != null) {
            // Delete Q&A pairs
            qaRepository.deleteByUploadId(uploadId);

            // Delete temp collection
            tempCollectionService.deleteTempCollection(uploadId);

            // Mark as deleted
            upload.setStatus(ProcessingStatus.DELETED);
            uploadRepository.save(upload);

            log.info("Deleted upload and associated data: {}", uploadId);
        }
    }

    @Transactional
    public void retryProcessing(String uploadId) {
        DocumentUpload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found: " + uploadId));

        // Delete existing Q&A pairs
        qaRepository.deleteByUploadId(uploadId);

        // Delete temp collection
        tempCollectionService.deleteTempCollection(uploadId);

        // Reset status
        upload.setStatus(ProcessingStatus.PENDING);
        upload.setErrorMessage(null);
        upload.setQuestionsGenerated(0);
        upload.setQuestionsValidated(0);
        upload.setTotalChunks(0);
        uploadRepository.save(upload);

        // Start processing again
        processDocumentAsync(uploadId);
    }
}
