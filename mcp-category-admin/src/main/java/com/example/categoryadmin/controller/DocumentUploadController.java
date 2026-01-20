package com.example.categoryadmin.controller;

import com.example.categoryadmin.dto.AddDocumentRequest;
import com.example.categoryadmin.dto.CategoryDocumentDto;
import com.example.categoryadmin.service.CategoryService;
import com.example.categoryadmin.service.DocumentParserService;
import com.example.categoryadmin.service.RagIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Controller for uploading documents to RAG and associating with categories.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Document Upload", description = "APIs for uploading documents to RAG with category association")
@CrossOrigin(origins = "*")
public class DocumentUploadController {

    private final RagIngestionService ragIngestionService;
    private final CategoryService categoryService;
    private final DocumentParserService documentParserService;

    /**
     * Upload a document to RAG and associate it with a category.
     */
    @PostMapping("/upload")
    @Operation(summary = "Upload document to RAG and associate with category")
    public ResponseEntity<UploadResponse> uploadDocument(@RequestBody UploadRequest request) {
        log.info("Uploading document: {} to category: {}", request.docId(), request.categoryId());

        // Validate request
        if (request.docId() == null || request.docId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new UploadResponse(false, 0, "Document ID is required", null));
        }
        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new UploadResponse(false, 0, "Document text is required", null));
        }
        if (request.categoryId() == null || request.categoryId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new UploadResponse(false, 0, "Category ID is required", null));
        }

        try {
            // First, ingest the document into RAG with category
            List<String> categories = List.of(request.categoryId());
            RagIngestionService.IngestResult ingestResult =
                    ragIngestionService.ingestDocument(request.docId(), request.text(), categories);

            if (!ingestResult.success()) {
                return ResponseEntity.ok(new UploadResponse(
                        false, 0, ingestResult.errorMessage(), null));
            }

            // Then, add the document reference to the category in admin DB
            AddDocumentRequest docRequest = new AddDocumentRequest();
            docRequest.setDocumentId(request.docId());
            docRequest.setDocumentName(request.documentName() != null ? request.documentName() : request.docId());
            docRequest.setDocumentDescription(request.description());
            docRequest.setDocumentType(request.documentType() != null ? request.documentType() : "text");

            CategoryDocumentDto savedDoc = categoryService.addDocumentToCategory(
                    request.categoryId(), docRequest);

            log.info("Document {} uploaded successfully - {} chunks created", request.docId(), ingestResult.chunksCreated());

            return ResponseEntity.ok(new UploadResponse(
                    true,
                    ingestResult.chunksCreated(),
                    null,
                    savedDoc));

        } catch (Exception e) {
            log.error("Failed to upload document", e);
            return ResponseEntity.ok(new UploadResponse(
                    false, 0, "Upload failed: " + e.getMessage(), null));
        }
    }

    /**
     * Upload a file (PDF, Word, or text) to RAG and associate it with a category.
     */
    @PostMapping(value = "/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file (PDF, Word, TXT) to RAG and associate with category")
    public ResponseEntity<UploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("categoryId") String categoryId,
            @RequestParam(value = "docId", required = false) String docId,
            @RequestParam(value = "documentName", required = false) String documentName,
            @RequestParam(value = "description", required = false) String description) {

        String filename = file.getOriginalFilename();
        log.info("Uploading file: {} to category: {}", filename, categoryId);

        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new UploadResponse(false, 0, "File is empty", null));
        }

        if (!documentParserService.isSupported(filename)) {
            return ResponseEntity.badRequest().body(
                    new UploadResponse(false, 0,
                            "Unsupported file type. Supported types: PDF, DOC, DOCX, TXT", null));
        }

        if (categoryId == null || categoryId.isBlank()) {
            return ResponseEntity.badRequest().body(
                    new UploadResponse(false, 0, "Category ID is required", null));
        }

        try {
            // Parse the document to extract text
            DocumentParserService.ParseResult parseResult = documentParserService.parseDocument(file);

            if (!parseResult.success()) {
                return ResponseEntity.ok(new UploadResponse(
                        false, 0, parseResult.errorMessage(), null));
            }

            // Generate docId if not provided
            String finalDocId = (docId != null && !docId.isBlank()) ? docId :
                    generateDocId(filename);

            // Use filename as document name if not provided
            String finalDocName = (documentName != null && !documentName.isBlank()) ?
                    documentName : filename;

            // Get document type
            String documentType = documentParserService.getDocumentType(filename);

            // Ingest the document into RAG with category
            List<String> categories = List.of(categoryId);
            RagIngestionService.IngestResult ingestResult =
                    ragIngestionService.ingestDocument(finalDocId, parseResult.text(), categories);

            if (!ingestResult.success()) {
                return ResponseEntity.ok(new UploadResponse(
                        false, 0, ingestResult.errorMessage(), null));
            }

            // Add the document reference to the category in admin DB
            AddDocumentRequest docRequest = new AddDocumentRequest();
            docRequest.setDocumentId(finalDocId);
            docRequest.setDocumentName(finalDocName);
            docRequest.setDocumentDescription(description);
            docRequest.setDocumentType(documentType);

            CategoryDocumentDto savedDoc = categoryService.addDocumentToCategory(
                    categoryId, docRequest);

            log.info("File {} uploaded successfully - {} chunks created",
                    filename, ingestResult.chunksCreated());

            return ResponseEntity.ok(new UploadResponse(
                    true,
                    ingestResult.chunksCreated(),
                    null,
                    savedDoc));

        } catch (Exception e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.ok(new UploadResponse(
                    false, 0, "Upload failed: " + e.getMessage(), null));
        }
    }

    /**
     * Generate a document ID from filename.
     */
    private String generateDocId(String filename) {
        if (filename == null) return "doc-" + System.currentTimeMillis();

        // Remove extension and sanitize
        int dotIndex = filename.lastIndexOf('.');
        String baseName = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;

        // Replace spaces and special chars with dashes
        baseName = baseName.replaceAll("[^a-zA-Z0-9-_]", "-")
                .replaceAll("-+", "-")
                .toLowerCase();

        // Add timestamp for uniqueness
        return baseName + "-" + System.currentTimeMillis();
    }

    /**
     * Check if RAG service is available.
     */
    @GetMapping("/health")
    @Operation(summary = "Check RAG service availability")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        boolean ragAvailable = ragIngestionService.isRagServiceAvailable();
        return ResponseEntity.ok(Map.of(
                "ragServiceAvailable", ragAvailable,
                "status", ragAvailable ? "healthy" : "degraded",
                "supportedFileTypes", List.of("pdf", "doc", "docx", "txt")
        ));
    }

    /**
     * Request payload for document upload.
     */
    public record UploadRequest(
            String docId,
            String documentName,
            String description,
            String text,
            String documentType,
            String categoryId
    ) {}

    /**
     * Response for document upload.
     */
    public record UploadResponse(
            boolean success,
            int chunksCreated,
            String errorMessage,
            CategoryDocumentDto document
    ) {}
}
