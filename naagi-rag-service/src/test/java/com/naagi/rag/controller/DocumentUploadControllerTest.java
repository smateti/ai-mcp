package com.naagi.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.rag.entity.DocumentUpload;
import com.naagi.rag.entity.DocumentUpload.ProcessingStatus;
import com.naagi.rag.entity.GeneratedQA;
import com.naagi.rag.entity.GeneratedQA.QuestionType;
import com.naagi.rag.entity.GeneratedQA.ValidationStatus;
import com.naagi.rag.repository.DocumentUploadRepository;
import com.naagi.rag.repository.GeneratedQARepository;
import com.naagi.rag.service.DocumentProcessingService;
import com.naagi.rag.service.LinkExtractionService;
import com.naagi.rag.service.TempCollectionService;
import com.naagi.rag.sse.SseNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for DocumentUploadController.
 * Tests REST API endpoints for document upload and processing.
 */
@WebMvcTest(DocumentUploadController.class)
class DocumentUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentProcessingService processingService;

    @MockBean
    private DocumentUploadRepository uploadRepository;

    @MockBean
    private GeneratedQARepository qaRepository;

    @MockBean
    private SseNotificationService sseService;

    @MockBean
    private LinkExtractionService linkExtractionService;

    @MockBean
    private TempCollectionService tempCollectionService;

    @Nested
    @DisplayName("POST /api/documents/uploads/{uploadId}/generate-more-qa")
    class GenerateMoreQATests {

        @Test
        @DisplayName("Should generate additional Q&A pairs successfully")
        void shouldGenerateAdditionalQAPairs() throws Exception {
            // Given
            String uploadId = "upload-123";
            DocumentUpload upload = createUpload(uploadId);

            GeneratedQA qa1 = GeneratedQA.builder()
                    .id(1L)
                    .uploadId(uploadId)
                    .question("New question 1?")
                    .expectedAnswer("Answer 1")
                    .questionType(QuestionType.FINE_GRAIN)
                    .build();
            GeneratedQA qa2 = GeneratedQA.builder()
                    .id(2L)
                    .uploadId(uploadId)
                    .question("New question 2?")
                    .expectedAnswer("Answer 2")
                    .questionType(QuestionType.SUMMARY)
                    .build();

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            when(processingService.generateAdditionalQA(uploadId, 3, 2))
                    .thenReturn(List.of(qa1, qa2));

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/generate-more-qa", uploadId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "fineGrainCount": 3,
                                    "summaryCount": 2
                                }
                                """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.generatedCount").value(2))
                    .andExpect(jsonPath("$.uploadId").value(uploadId))
                    .andExpect(jsonPath("$.qaPairs").isArray())
                    .andExpect(jsonPath("$.qaPairs.length()").value(2));

            verify(processingService).generateAdditionalQA(uploadId, 3, 2);
        }

        @Test
        @DisplayName("Should use default counts when request body is empty")
        void shouldUseDefaultCountsWhenBodyEmpty() throws Exception {
            // Given
            String uploadId = "upload-123";
            DocumentUpload upload = createUpload(uploadId);

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            when(processingService.generateAdditionalQA(uploadId, 3, 2))
                    .thenReturn(List.of());

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/generate-more-qa", uploadId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(processingService).generateAdditionalQA(uploadId, 3, 2);
        }

        @Test
        @DisplayName("Should return 404 when upload not found")
        void shouldReturn404WhenUploadNotFound() throws Exception {
            // Given
            when(uploadRepository.findById("non-existent")).thenReturn(Optional.empty());

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/generate-more-qa", "non-existent")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return error when generation fails")
        void shouldReturnErrorWhenGenerationFails() throws Exception {
            // Given
            String uploadId = "upload-123";
            DocumentUpload upload = createUpload(uploadId);

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            when(processingService.generateAdditionalQA(anyString(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("LLM service unavailable"));

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/generate-more-qa", uploadId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Failed to generate Q&A: LLM service unavailable"));
        }
    }

    @Nested
    @DisplayName("POST /api/documents/uploads/{uploadId}/chat")
    class DocumentScopedChatTests {

        @Test
        @DisplayName("Should return chat response successfully")
        void shouldReturnChatResponseSuccessfully() throws Exception {
            // Given
            String uploadId = "upload-123";
            String question = "What is this document about?";
            String answer = "This document is about testing features.";

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(createUpload(uploadId)));
            when(processingService.documentScopedChat(uploadId, question)).thenReturn(answer);

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/chat", uploadId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "question": "What is this document about?"
                                }
                                """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.uploadId").value(uploadId))
                    .andExpect(jsonPath("$.question").value(question))
                    .andExpect(jsonPath("$.answer").value(answer));

            verify(processingService).documentScopedChat(uploadId, question);
        }

        @Test
        @DisplayName("Should return bad request when question is missing")
        void shouldReturnBadRequestWhenQuestionMissing() throws Exception {
            // Given
            String uploadId = "upload-123";

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/chat", uploadId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "question": ""
                                }
                                """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Question is required"));
        }

        @Test
        @DisplayName("Should return bad request when question is blank")
        void shouldReturnBadRequestWhenQuestionBlank() throws Exception {
            // Given
            String uploadId = "upload-123";

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/chat", uploadId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "question": "   "
                                }
                                """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Question is required"));
        }

        @Test
        @DisplayName("Should return 404 when upload not found")
        void shouldReturn404WhenUploadNotFound() throws Exception {
            // Given
            when(uploadRepository.findById("non-existent")).thenReturn(Optional.empty());

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/chat", "non-existent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "question": "Test question?"
                                }
                                """))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return error when chat fails")
        void shouldReturnErrorWhenChatFails() throws Exception {
            // Given
            String uploadId = "upload-123";

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(createUpload(uploadId)));
            when(processingService.documentScopedChat(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Chat service unavailable"));

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/chat", uploadId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "question": "Test question?"
                                }
                                """))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Chat failed: Chat service unavailable"));
        }
    }

    @Nested
    @DisplayName("POST /api/documents/upload")
    class UploadDocumentTests {

        @Test
        @DisplayName("Should upload document successfully")
        void shouldUploadDocumentSuccessfully() throws Exception {
            // Given
            DocumentUpload upload = DocumentUpload.builder()
                    .id("upload-123")
                    .docId("10000")
                    .status(ProcessingStatus.PENDING)
                    .build();

            when(uploadRepository.existsByTitle("Test Title")).thenReturn(false);
            when(processingService.initiateUpload("Test Title", "Test content", "cat-1"))
                    .thenReturn(upload);

            // When/Then
            mockMvc.perform(post("/api/documents/upload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "title": "Test Title",
                                    "content": "Test content",
                                    "categoryId": "cat-1"
                                }
                                """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.uploadId").value("upload-123"))
                    .andExpect(jsonPath("$.docId").value("10000"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.sseEndpoint").exists());
        }

        @Test
        @DisplayName("Should return bad request when content is missing")
        void shouldReturnBadRequestWhenContentMissing() throws Exception {
            // When/Then
            mockMvc.perform(post("/api/documents/upload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "title": "Test Title"
                                }
                                """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Content is required"));
        }

        @Test
        @DisplayName("Should return bad request when title is missing")
        void shouldReturnBadRequestWhenTitleMissing() throws Exception {
            // When/Then
            mockMvc.perform(post("/api/documents/upload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "content": "Test content"
                                }
                                """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Title is required"));
        }

        @Test
        @DisplayName("Should return bad request when title already exists")
        void shouldReturnBadRequestWhenTitleExists() throws Exception {
            // Given
            when(uploadRepository.existsByTitle("Test Title")).thenReturn(true);

            // When/Then
            mockMvc.perform(post("/api/documents/upload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "title": "Test Title",
                                    "content": "Test content"
                                }
                                """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("A document with this title already exists"));
        }
    }

    @Nested
    @DisplayName("POST /api/documents/uploads/{uploadId}/approve")
    class ApproveUploadTests {

        @Test
        @DisplayName("Should approve upload successfully")
        void shouldApproveUploadSuccessfully() throws Exception {
            // Given
            String uploadId = "upload-123";
            doNothing().when(processingService).moveToRag(uploadId);

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/approve", uploadId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Document successfully moved to RAG knowledge base"));

            verify(processingService).moveToRag(uploadId);
        }

        @Test
        @DisplayName("Should return 404 when upload not found")
        void shouldReturn404WhenUploadNotFound() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Upload not found"))
                    .when(processingService).moveToRag("non-existent");

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/approve", "non-existent"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return bad request when upload not ready")
        void shouldReturnBadRequestWhenNotReady() throws Exception {
            // Given
            doThrow(new IllegalStateException("Upload is not ready for RAG"))
                    .when(processingService).moveToRag("upload-123");

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/approve", "upload-123"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Upload is not ready for RAG"));
        }
    }

    @Nested
    @DisplayName("POST /api/documents/uploads/{uploadId}/retry")
    class RetryProcessingTests {

        @Test
        @DisplayName("Should retry processing successfully")
        void shouldRetryProcessingSuccessfully() throws Exception {
            // Given
            String uploadId = "upload-123";
            doNothing().when(processingService).retryProcessing(uploadId);

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/retry", uploadId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Processing restarted"));

            verify(processingService).retryProcessing(uploadId);
        }

        @Test
        @DisplayName("Should return 404 when upload not found")
        void shouldReturn404WhenUploadNotFound() throws Exception {
            // Given
            doThrow(new IllegalArgumentException("Upload not found"))
                    .when(processingService).retryProcessing("non-existent");

            // When/Then
            mockMvc.perform(post("/api/documents/uploads/{uploadId}/retry", "non-existent"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/documents/uploads/{uploadId}")
    class DeleteUploadTests {

        @Test
        @DisplayName("Should delete upload successfully")
        void shouldDeleteUploadSuccessfully() throws Exception {
            // Given
            String uploadId = "upload-123";
            doNothing().when(processingService).deleteUpload(uploadId);

            // When/Then
            mockMvc.perform(delete("/api/documents/uploads/{uploadId}", uploadId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Upload deleted successfully"));

            verify(processingService).deleteUpload(uploadId);
        }

        @Test
        @DisplayName("Should return error when delete fails")
        void shouldReturnErrorWhenDeleteFails() throws Exception {
            // Given
            doThrow(new RuntimeException("Database error"))
                    .when(processingService).deleteUpload("upload-123");

            // When/Then
            mockMvc.perform(delete("/api/documents/uploads/{uploadId}", "upload-123"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Failed to delete upload: Database error"));
        }
    }

    @Nested
    @DisplayName("GET /api/documents/uploads")
    class ListUploadsTests {

        @Test
        @DisplayName("Should list all uploads excluding deleted")
        void shouldListAllUploadsExcludingDeleted() throws Exception {
            // Given
            List<DocumentUpload> uploads = List.of(
                    createUpload("upload-1"),
                    createUpload("upload-2")
            );
            when(uploadRepository.findAllByStatusNotOrderByCreatedAtDesc(ProcessingStatus.DELETED))
                    .thenReturn(uploads);

            // When/Then
            mockMvc.perform(get("/api/documents/uploads"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("Should filter by status")
        void shouldFilterByStatus() throws Exception {
            // Given
            List<DocumentUpload> uploads = List.of(createUpload("upload-1"));
            when(uploadRepository.findByStatusOrderByCreatedAtDesc(ProcessingStatus.READY_FOR_REVIEW))
                    .thenReturn(uploads);

            // When/Then
            mockMvc.perform(get("/api/documents/uploads")
                            .param("status", "READY_FOR_REVIEW"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("Should include deleted when flag is set")
        void shouldIncludeDeletedWhenFlagSet() throws Exception {
            // Given
            List<DocumentUpload> uploads = List.of(
                    createUpload("upload-1"),
                    createUpload("upload-2")
            );
            when(uploadRepository.findAllByOrderByCreatedAtDesc()).thenReturn(uploads);

            // When/Then
            mockMvc.perform(get("/api/documents/uploads")
                            .param("includeDeleted", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Nested
    @DisplayName("GET /api/documents/uploads/{uploadId}")
    class GetUploadDetailsTests {

        @Test
        @DisplayName("Should return upload details")
        void shouldReturnUploadDetails() throws Exception {
            // Given
            String uploadId = "upload-123";
            DocumentUpload upload = createUpload(uploadId);

            List<GeneratedQA> qaList = List.of(
                    GeneratedQA.builder()
                            .id(1L)
                            .uploadId(uploadId)
                            .question("Q1?")
                            .expectedAnswer("A1")
                            .validationStatus(ValidationStatus.PASSED)
                            .similarityScore(0.85)
                            .build()
            );

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            when(qaRepository.findByUploadIdOrderByIdAsc(uploadId)).thenReturn(qaList);
            when(qaRepository.getAverageSimilarityScore(uploadId)).thenReturn(0.85);
            when(qaRepository.countByUploadIdAndValidationStatus(uploadId, ValidationStatus.PASSED)).thenReturn(1L);
            when(qaRepository.countByUploadIdAndValidationStatus(uploadId, ValidationStatus.FAILED)).thenReturn(0L);

            // When/Then
            mockMvc.perform(get("/api/documents/uploads/{uploadId}", uploadId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upload.id").value(uploadId))
                    .andExpect(jsonPath("$.qaPairs").isArray())
                    .andExpect(jsonPath("$.qaPairs.length()").value(1))
                    .andExpect(jsonPath("$.stats.passed").value(1))
                    .andExpect(jsonPath("$.stats.failed").value(0));
        }

        @Test
        @DisplayName("Should return 404 when upload not found")
        void shouldReturn404WhenUploadNotFound() throws Exception {
            // Given
            when(uploadRepository.findById("non-existent")).thenReturn(Optional.empty());

            // When/Then
            mockMvc.perform(get("/api/documents/uploads/{uploadId}", "non-existent"))
                    .andExpect(status().isNotFound());
        }
    }

    // Helper method
    private DocumentUpload createUpload(String uploadId) {
        return DocumentUpload.builder()
                .id(uploadId)
                .docId("doc-" + uploadId)
                .title("Test Document")
                .originalContent("Test content")
                .categoryId("cat-1")
                .status(ProcessingStatus.READY_FOR_REVIEW)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
