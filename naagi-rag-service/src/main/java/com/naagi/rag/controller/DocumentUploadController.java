package com.naagi.rag.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.rag.entity.DocumentUpload;
import com.naagi.rag.entity.GeneratedQA;
import com.naagi.rag.repository.DocumentUploadRepository;
import com.naagi.rag.repository.GeneratedQARepository;
import com.naagi.rag.service.DocumentProcessingService;
import com.naagi.rag.service.LinkExtractionService;
import com.naagi.rag.service.LinkExtractionService.ExtractedLink;
import com.naagi.rag.service.TempCollectionService;
import com.naagi.rag.service.TempCollectionService.ChunkData;
import com.naagi.rag.sse.SseNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadController {

    private final DocumentProcessingService processingService;
    private final DocumentUploadRepository uploadRepository;
    private final GeneratedQARepository qaRepository;
    private final SseNotificationService sseService;
    private final LinkExtractionService linkExtractionService;
    private final ObjectMapper objectMapper;
    private final TempCollectionService tempCollectionService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestBody UploadRequest request) {
        log.info("Received document upload request: title={}", request.title());

        if (request.content() == null || request.content().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content is required"));
        }

        if (request.title() == null || request.title().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));
        }

        // Check for duplicate title
        if (uploadRepository.existsByTitle(request.title())) {
            return ResponseEntity.badRequest().body(Map.of("error", "A document with this title already exists"));
        }

        // Create upload record (docId will be auto-generated)
        DocumentUpload upload = processingService.initiateUpload(
                request.title(),
                request.content(),
                request.categoryId()
        );

        // Start async processing
        processingService.processDocumentAsync(upload.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("uploadId", upload.getId());
        response.put("docId", upload.getDocId());
        response.put("status", upload.getStatus().name());
        response.put("message", "Document upload initiated. Processing will continue in background.");
        response.put("sseEndpoint", "/api/documents/uploads/" + upload.getId() + "/events");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/uploads")
    public ResponseEntity<List<DocumentUpload>> listUploads(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false, defaultValue = "false") boolean includeDeleted) {

        List<DocumentUpload> uploads;

        if (status != null) {
            try {
                DocumentUpload.ProcessingStatus statusEnum = DocumentUpload.ProcessingStatus.valueOf(status);
                uploads = uploadRepository.findByStatusOrderByCreatedAtDesc(statusEnum);
            } catch (IllegalArgumentException e) {
                uploads = includeDeleted
                        ? uploadRepository.findAllByOrderByCreatedAtDesc()
                        : uploadRepository.findAllByStatusNotOrderByCreatedAtDesc(DocumentUpload.ProcessingStatus.DELETED);
            }
        } else if (categoryId != null) {
            uploads = uploadRepository.findByCategoryIdOrderByCreatedAtDesc(categoryId);
            if (!includeDeleted) {
                uploads = uploads.stream()
                        .filter(u -> u.getStatus() != DocumentUpload.ProcessingStatus.DELETED)
                        .toList();
            }
        } else {
            uploads = includeDeleted
                    ? uploadRepository.findAllByOrderByCreatedAtDesc()
                    : uploadRepository.findAllByStatusNotOrderByCreatedAtDesc(DocumentUpload.ProcessingStatus.DELETED);
        }

        return ResponseEntity.ok(uploads);
    }

    @GetMapping("/check-title")
    public ResponseEntity<Map<String, Object>> checkTitleExists(
            @RequestParam String title,
            @RequestParam(required = false) String excludeId) {
        boolean exists;
        if (excludeId != null && !excludeId.isBlank()) {
            exists = uploadRepository.existsByTitleAndIdNot(title, excludeId);
        } else {
            exists = uploadRepository.existsByTitle(title);
        }
        return ResponseEntity.ok(Map.of("exists", exists, "title", title));
    }

    @GetMapping("/uploads/{uploadId}")
    public ResponseEntity<DocumentUploadDetails> getUploadDetails(@PathVariable String uploadId) {
        return uploadRepository.findById(uploadId)
                .map(upload -> {
                    List<GeneratedQA> qaList = qaRepository.findByUploadIdOrderByIdAsc(uploadId);
                    Double avgScore = qaRepository.getAverageSimilarityScore(uploadId);
                    long passed = qaRepository.countByUploadIdAndValidationStatus(uploadId, GeneratedQA.ValidationStatus.PASSED);
                    long failed = qaRepository.countByUploadIdAndValidationStatus(uploadId, GeneratedQA.ValidationStatus.FAILED);

                    return ResponseEntity.ok(new DocumentUploadDetails(
                            upload,
                            qaList,
                            new ValidationStats(qaList.size(), passed, failed, avgScore)
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/uploads/{uploadId}/qa")
    public ResponseEntity<List<GeneratedQA>> getQAPairs(
            @PathVariable String uploadId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {

        List<GeneratedQA> qaList;

        if (type != null) {
            try {
                GeneratedQA.QuestionType questionType = GeneratedQA.QuestionType.valueOf(type);
                qaList = qaRepository.findByUploadIdAndQuestionTypeOrderByIdAsc(uploadId, questionType);
            } catch (IllegalArgumentException e) {
                qaList = qaRepository.findByUploadIdOrderByIdAsc(uploadId);
            }
        } else if (status != null) {
            try {
                GeneratedQA.ValidationStatus validationStatus = GeneratedQA.ValidationStatus.valueOf(status);
                qaList = qaRepository.findByUploadIdAndValidationStatusOrderByIdAsc(uploadId, validationStatus);
            } catch (IllegalArgumentException e) {
                qaList = qaRepository.findByUploadIdOrderByIdAsc(uploadId);
            }
        } else {
            qaList = qaRepository.findByUploadIdOrderByIdAsc(uploadId);
        }

        return ResponseEntity.ok(qaList);
    }

    @GetMapping("/uploads/{uploadId}/links")
    public ResponseEntity<Map<String, Object>> getExtractedLinks(@PathVariable String uploadId) {
        return uploadRepository.findById(uploadId)
                .map(upload -> {
                    List<ExtractedLink> links = Collections.emptyList();
                    Map<LinkExtractionService.LinkType, Long> summary = Collections.emptyMap();

                    if (upload.getExtractedLinks() != null && !upload.getExtractedLinks().isBlank()) {
                        try {
                            links = objectMapper.readValue(
                                    upload.getExtractedLinks(),
                                    new TypeReference<List<ExtractedLink>>() {}
                            );
                            summary = linkExtractionService.getLinkSummary(links);
                        } catch (Exception e) {
                            log.error("Failed to parse extracted links for upload {}", uploadId, e);
                        }
                    }

                    List<ExtractedLink> ingestable = linkExtractionService.getIngestableLinks(links);

                    Map<String, Object> response = new HashMap<>();
                    response.put("uploadId", uploadId);
                    response.put("links", links);
                    response.put("ingestableLinks", ingestable);
                    response.put("summary", summary);
                    response.put("totalLinks", links.size());
                    response.put("ingestableCount", ingestable.size());

                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/uploads/{uploadId}/chunks")
    public ResponseEntity<Map<String, Object>> getChunks(@PathVariable String uploadId) {
        return uploadRepository.findById(uploadId)
                .map(upload -> {
                    List<ChunkData> chunks;
                    String source;

                    // If document is moved to RAG, get chunks from main collection
                    // Otherwise, get from temp collection (for preview)
                    if (upload.getStatus() == DocumentUpload.ProcessingStatus.MOVED_TO_RAG) {
                        chunks = tempCollectionService.getChunksFromMainCollection(upload.getDocId());
                        source = "main";
                    } else {
                        chunks = tempCollectionService.getChunksForUpload(uploadId);
                        source = "temp";
                    }

                    Map<String, Object> response = new HashMap<>();
                    response.put("uploadId", uploadId);
                    response.put("docId", upload.getDocId());
                    response.put("totalChunks", upload.getTotalChunks());
                    response.put("chunks", chunks);
                    response.put("chunksAvailable", !chunks.isEmpty());
                    response.put("source", source);

                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/uploads/{uploadId}/approve")
    public ResponseEntity<Map<String, Object>> approveAndMoveToRag(@PathVariable String uploadId) {
        log.info("Approving and moving upload to RAG: {}", uploadId);

        try {
            processingService.moveToRag(uploadId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Document successfully moved to RAG knowledge base",
                    "uploadId", uploadId
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to move document to RAG: {}", uploadId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to move document to RAG: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/uploads/{uploadId}/retry")
    public ResponseEntity<Map<String, Object>> retryProcessing(@PathVariable String uploadId) {
        log.info("Retrying processing for upload: {}", uploadId);

        try {
            processingService.retryProcessing(uploadId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Processing restarted",
                    "uploadId", uploadId
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/uploads/{uploadId}/title")
    public ResponseEntity<Map<String, Object>> updateTitle(
            @PathVariable String uploadId,
            @RequestBody UpdateTitleRequest request) {
        log.info("Updating title for upload: {} to '{}'", uploadId, request.title());

        if (request.title() == null || request.title().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));
        }

        return uploadRepository.findById(uploadId)
                .map(upload -> {
                    String oldTitle = upload.getTitle();
                    upload.setTitle(request.title());
                    uploadRepository.save(upload);

                    // If document is already in RAG, update the title in Qdrant too
                    if (upload.getStatus() == DocumentUpload.ProcessingStatus.MOVED_TO_RAG) {
                        try {
                            tempCollectionService.updateTitleInMainCollection(upload.getDocId(), request.title());
                            log.info("Updated title in Qdrant for docId: {}", upload.getDocId());
                        } catch (Exception e) {
                            log.error("Failed to update title in Qdrant for docId: {}", upload.getDocId(), e);
                            // Continue anyway - the DB title is updated
                        }
                    }

                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("uploadId", uploadId);
                    response.put("oldTitle", oldTitle);
                    response.put("newTitle", request.title());
                    response.put("qdrantUpdated", upload.getStatus() == DocumentUpload.ProcessingStatus.MOVED_TO_RAG);
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/uploads/{uploadId}")
    public ResponseEntity<Map<String, Object>> deleteUpload(@PathVariable String uploadId) {
        log.info("Deleting upload: {}", uploadId);

        try {
            processingService.deleteUpload(uploadId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Upload deleted successfully",
                    "uploadId", uploadId
            ));
        } catch (Exception e) {
            log.error("Failed to delete upload: {}", uploadId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to delete upload: " + e.getMessage()
            ));
        }
    }

    @DeleteMapping("/uploads")
    public ResponseEntity<Map<String, Object>> deleteAllUploads() {
        log.info("Deleting all uploads");

        try {
            List<DocumentUpload> allUploads = uploadRepository.findAll();
            int count = allUploads.size();

            for (DocumentUpload upload : allUploads) {
                processingService.deleteUpload(upload.getId());
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Deleted " + count + " uploads",
                    "count", count
            ));
        } catch (Exception e) {
            log.error("Failed to delete all uploads", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to delete: " + e.getMessage()
            ));
        }
    }

    @DeleteMapping("/uploads/purge-deleted")
    public ResponseEntity<Map<String, Object>> purgeDeletedUploads() {
        log.info("Purging all soft-deleted uploads");

        try {
            List<DocumentUpload> deletedUploads = uploadRepository.findAllByStatus(DocumentUpload.ProcessingStatus.DELETED);
            int count = deletedUploads.size();

            for (DocumentUpload upload : deletedUploads) {
                qaRepository.deleteByUploadId(upload.getId());
                uploadRepository.delete(upload);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Purged " + count + " soft-deleted uploads",
                    "count", count
            ));
        } catch (Exception e) {
            log.error("Failed to purge deleted uploads", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to purge: " + e.getMessage()
            ));
        }
    }

    /**
     * Generate additional Q&A pairs for an existing document.
     * This can be called even after the document has been moved to RAG.
     */
    @PostMapping("/uploads/{uploadId}/generate-more-qa")
    public ResponseEntity<Map<String, Object>> generateMoreQA(
            @PathVariable String uploadId,
            @RequestBody(required = false) GenerateMoreQARequest request) {
        log.info("Generating more Q&A pairs for upload: {}", uploadId);

        var uploadOpt = uploadRepository.findById(uploadId);
        if (uploadOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            int fineGrainCount = request != null && request.fineGrainCount() != null ? request.fineGrainCount() : 3;
            int summaryCount = request != null && request.summaryCount() != null ? request.summaryCount() : 2;

            List<GeneratedQA> newQA = processingService.generateAdditionalQA(uploadId, fineGrainCount, summaryCount);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Generated " + newQA.size() + " additional Q&A pairs");
            response.put("uploadId", uploadId);
            response.put("generatedCount", newQA.size());
            response.put("qaPairs", newQA);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to generate more Q&A for upload: {}", uploadId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to generate Q&A: " + e.getMessage()
            ));
        }
    }

    /**
     * Document-scoped chat endpoint.
     * Allows chatting within the context of a specific document only.
     */
    @PostMapping("/uploads/{uploadId}/chat")
    public ResponseEntity<Map<String, Object>> documentScopedChat(
            @PathVariable String uploadId,
            @RequestBody DocumentChatRequest request) {
        log.info("Document-scoped chat for upload: {}, question: {}", uploadId, request.question());

        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Question is required"));
        }

        var uploadOpt = uploadRepository.findById(uploadId);
        if (uploadOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            String answer = processingService.documentScopedChat(uploadId, request.question());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("uploadId", uploadId);
            response.put("question", request.question());
            response.put("answer", answer);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to chat for upload: {}", uploadId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Chat failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Document-scoped chat with streaming response.
     */
    @PostMapping(value = "/uploads/{uploadId}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter documentScopedChatStream(
            @PathVariable String uploadId,
            @RequestBody DocumentChatRequest request) {
        log.info("Document-scoped streaming chat for upload: {}, question: {}", uploadId, request.question());

        SseEmitter emitter = new SseEmitter(120000L);

        new Thread(() -> {
            try {
                DocumentUpload upload = uploadRepository.findById(uploadId).orElse(null);
                if (upload == null) {
                    emitter.send(SseEmitter.event().name("error").data("{\"error\":\"Upload not found\"}"));
                    emitter.complete();
                    return;
                }

                processingService.documentScopedChatStream(uploadId, request.question(), token -> {
                    try {
                        emitter.send(SseEmitter.event().name("token").data("{\"t\":\"" + escapeJson(token) + "\"}"));
                    } catch (Exception e) {
                        log.warn("Error sending token: {}", e.getMessage());
                    }
                });

                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming chat error: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // SSE Endpoints
    @GetMapping(value = "/uploads/{uploadId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToUploadEvents(@PathVariable String uploadId) {
        log.info("SSE subscription for upload: {}", uploadId);
        return sseService.subscribeToUpload(uploadId);
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToGlobalEvents() {
        log.info("SSE subscription for global events");
        return sseService.subscribeToGlobal();
    }

    // Request/Response DTOs
    public record UploadRequest(
            String title,
            String content,
            String categoryId
    ) {}

    public record DocumentUploadDetails(
            DocumentUpload upload,
            List<GeneratedQA> qaPairs,
            ValidationStats stats
    ) {}

    public record ValidationStats(
            int totalQuestions,
            long passed,
            long failed,
            Double averageScore
    ) {}

    public record GenerateMoreQARequest(
            Integer fineGrainCount,
            Integer summaryCount
    ) {}

    public record DocumentChatRequest(
            String question
    ) {}

    public record UpdateTitleRequest(
            String title
    ) {}
}
