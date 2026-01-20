package com.naag.rag.controller;

import com.naag.rag.entity.DocumentUpload;
import com.naag.rag.entity.GeneratedQA;
import com.naag.rag.repository.DocumentUploadRepository;
import com.naag.rag.repository.GeneratedQARepository;
import com.naag.rag.service.DocumentProcessingService;
import com.naag.rag.sse.SseNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadController {

    private final DocumentProcessingService processingService;
    private final DocumentUploadRepository uploadRepository;
    private final GeneratedQARepository qaRepository;
    private final SseNotificationService sseService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestBody UploadRequest request) {
        log.info("Received document upload request: docId={}, title={}", request.docId(), request.title());

        if (request.content() == null || request.content().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content is required"));
        }

        if (request.docId() == null || request.docId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Document ID is required"));
        }

        // Check for duplicate docId
        if (uploadRepository.findByDocId(request.docId()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Document ID already exists"));
        }

        // Create upload record
        DocumentUpload upload = processingService.initiateUpload(
                request.docId(),
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
            @RequestParam(required = false) String categoryId) {

        List<DocumentUpload> uploads;

        if (status != null) {
            try {
                DocumentUpload.ProcessingStatus statusEnum = DocumentUpload.ProcessingStatus.valueOf(status);
                uploads = uploadRepository.findByStatusOrderByCreatedAtDesc(statusEnum);
            } catch (IllegalArgumentException e) {
                uploads = uploadRepository.findAllByOrderByCreatedAtDesc();
            }
        } else if (categoryId != null) {
            uploads = uploadRepository.findByCategoryIdOrderByCreatedAtDesc(categoryId);
        } else {
            uploads = uploadRepository.findAllByOrderByCreatedAtDesc();
        }

        return ResponseEntity.ok(uploads);
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
            String docId,
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
}
