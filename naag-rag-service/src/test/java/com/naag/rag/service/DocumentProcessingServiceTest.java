package com.naag.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naag.rag.entity.DocumentUpload;
import com.naag.rag.entity.DocumentUpload.ProcessingStatus;
import com.naag.rag.entity.GeneratedQA;
import com.naag.rag.entity.GeneratedQA.QuestionType;
import com.naag.rag.entity.GeneratedQA.ValidationStatus;
import com.naag.rag.llm.ChatClient;
import com.naag.rag.llm.EmbeddingsClient;
import com.naag.rag.repository.DocumentUploadRepository;
import com.naag.rag.repository.GeneratedQARepository;
import com.naag.rag.sse.SseNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DocumentProcessingService.
 * Tests document processing, Q&A generation, and document-scoped chat functionality.
 */
@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

    @Mock
    private DocumentUploadRepository uploadRepository;

    @Mock
    private GeneratedQARepository qaRepository;

    @Mock
    private ChatClient chatClient;

    @Mock
    private EmbeddingsClient embeddingsClient;

    @Mock
    private TempCollectionService tempCollectionService;

    @Mock
    private SseNotificationService sseService;

    @Mock
    private FaqCacheService faqCacheService;

    @Mock
    private LinkExtractionService linkExtractionService;

    @Mock
    private RagService ragService;

    private DocumentProcessingService processingService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        processingService = new DocumentProcessingService(
                uploadRepository,
                qaRepository,
                chatClient,
                embeddingsClient,
                tempCollectionService,
                sseService,
                objectMapper,
                faqCacheService,
                linkExtractionService,
                ragService
        );
    }

    @Nested
    @DisplayName("Generate Additional Q&A Tests")
    class GenerateAdditionalQATests {

        @Test
        @DisplayName("Should generate additional fine-grain Q&A pairs")
        void shouldGenerateAdditionalFineGrainQA() {
            // Given
            String uploadId = "upload-123";
            DocumentUpload upload = createUpload(uploadId, "Test document content for generating questions.");
            // Set to PENDING to skip validation (no temp collection available in test)
            upload.setStatus(ProcessingStatus.PENDING);

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            when(qaRepository.findByUploadIdOrderByIdAsc(uploadId)).thenReturn(Collections.emptyList());
            when(chatClient.chatOnce(anyString(), eq(0.5), eq(2048)))
                    .thenReturn("[{\"question\": \"What is the test about?\", \"answer\": \"It is about testing.\"}]");
            when(qaRepository.save(any(GeneratedQA.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            List<GeneratedQA> result = processingService.generateAdditionalQA(uploadId, 1, 0);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getQuestion()).isEqualTo("What is the test about?");
            assertThat(result.get(0).getExpectedAnswer()).isEqualTo("It is about testing.");
            assertThat(result.get(0).getQuestionType()).isEqualTo(QuestionType.FINE_GRAIN);
            assertThat(result.get(0).getValidationStatus()).isEqualTo(ValidationStatus.PENDING);

            verify(qaRepository, times(1)).save(any(GeneratedQA.class));
            verify(uploadRepository).save(argThat(u -> u.getQuestionsGenerated() == 1));
        }

        @Test
        @DisplayName("Should generate additional summary Q&A pairs")
        void shouldGenerateAdditionalSummaryQA() {
            // Given
            String uploadId = "upload-123";
            DocumentUpload upload = createUpload(uploadId, "Test document content for generating questions.");
            upload.setStatus(ProcessingStatus.PENDING);

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            when(qaRepository.findByUploadIdOrderByIdAsc(uploadId)).thenReturn(Collections.emptyList());
            when(chatClient.chatOnce(anyString(), eq(0.5), eq(2048)))
                    .thenReturn("[{\"question\": \"What is the main theme?\", \"answer\": \"The theme is testing.\"}]");
            when(qaRepository.save(any(GeneratedQA.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            List<GeneratedQA> result = processingService.generateAdditionalQA(uploadId, 0, 1);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getQuestionType()).isEqualTo(QuestionType.SUMMARY);
        }

        @Test
        @DisplayName("Should generate both fine-grain and summary Q&A pairs")
        void shouldGenerateBothTypes() {
            // Given
            String uploadId = "upload-123";
            DocumentUpload upload = createUpload(uploadId, "Test document content.");
            upload.setStatus(ProcessingStatus.PENDING);

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            when(qaRepository.findByUploadIdOrderByIdAsc(uploadId)).thenReturn(Collections.emptyList());
            when(chatClient.chatOnce(anyString(), eq(0.5), eq(2048)))
                    .thenReturn("[{\"question\": \"Q1?\", \"answer\": \"A1\"}]")
                    .thenReturn("[{\"question\": \"Q2?\", \"answer\": \"A2\"}]");
            when(qaRepository.save(any(GeneratedQA.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            List<GeneratedQA> result = processingService.generateAdditionalQA(uploadId, 1, 1);

            // Then
            assertThat(result).hasSize(2);
            verify(chatClient, times(2)).chatOnce(anyString(), eq(0.5), eq(2048));
        }

        @Test
        @DisplayName("Should throw exception when upload not found")
        void shouldThrowWhenUploadNotFound() {
            // Given
            when(uploadRepository.findById("non-existent")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> processingService.generateAdditionalQA("non-existent", 1, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Upload not found");
        }

        @Test
        @DisplayName("Should handle LLM failure gracefully")
        void shouldHandleLLMFailureGracefully() {
            // Given
            String uploadId = "upload-123";
            DocumentUpload upload = createUpload(uploadId, "Test content");
            upload.setStatus(ProcessingStatus.PENDING);

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            when(qaRepository.findByUploadIdOrderByIdAsc(uploadId)).thenReturn(Collections.emptyList());
            when(chatClient.chatOnce(anyString(), eq(0.5), eq(2048)))
                    .thenThrow(new RuntimeException("LLM service unavailable"));

            // When
            List<GeneratedQA> result = processingService.generateAdditionalQA(uploadId, 1, 0);

            // Then
            assertThat(result).isEmpty();
            verify(uploadRepository).save(argThat(u -> u.getQuestionsGenerated() == 0));
        }

        @Test
        @DisplayName("Should handle invalid JSON response from LLM")
        void shouldHandleInvalidJsonResponse() {
            // Given
            String uploadId = "upload-123";
            DocumentUpload upload = createUpload(uploadId, "Test content");
            upload.setStatus(ProcessingStatus.PENDING);

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            when(qaRepository.findByUploadIdOrderByIdAsc(uploadId)).thenReturn(Collections.emptyList());
            when(chatClient.chatOnce(anyString(), eq(0.5), eq(2048)))
                    .thenReturn("This is not valid JSON");

            // When
            List<GeneratedQA> result = processingService.generateAdditionalQA(uploadId, 1, 0);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should update upload count correctly")
        void shouldUpdateUploadCountCorrectly() {
            // Given
            String uploadId = "upload-123";
            DocumentUpload upload = createUpload(uploadId, "Test content");
            upload.setStatus(ProcessingStatus.PENDING);
            upload.setQuestionsGenerated(5); // Already has 5 questions

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            when(qaRepository.findByUploadIdOrderByIdAsc(uploadId)).thenReturn(Collections.emptyList());
            when(chatClient.chatOnce(anyString(), eq(0.5), eq(2048)))
                    .thenReturn("[{\"question\": \"Q1?\", \"answer\": \"A1\"}, {\"question\": \"Q2?\", \"answer\": \"A2\"}]");
            when(qaRepository.save(any(GeneratedQA.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            processingService.generateAdditionalQA(uploadId, 2, 0);

            // Then
            ArgumentCaptor<DocumentUpload> captor = ArgumentCaptor.forClass(DocumentUpload.class);
            verify(uploadRepository).save(captor.capture());
            assertThat(captor.getValue().getQuestionsGenerated()).isEqualTo(7); // 5 + 2
        }

        @Test
        @DisplayName("Should validate against RAG when document is MOVED_TO_RAG")
        void shouldValidateAgainstRagWhenMovedToRag() {
            // Given
            String uploadId = "upload-123";
            DocumentUpload upload = createUpload(uploadId, "Test document content.");
            upload.setStatus(ProcessingStatus.MOVED_TO_RAG);
            upload.setCategoryId("test-category");

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            when(qaRepository.findByUploadIdOrderByIdAsc(uploadId)).thenReturn(Collections.emptyList());
            when(chatClient.chatOnce(anyString(), eq(0.5), eq(2048)))
                    .thenReturn("[{\"question\": \"What is the test?\", \"answer\": \"It is testing.\"}]");
            when(qaRepository.save(any(GeneratedQA.class))).thenAnswer(inv -> inv.getArgument(0));
            when(ragService.ask(anyString(), eq("test-category"))).thenReturn("The RAG answer about testing.");
            when(embeddingsClient.embed(anyString())).thenReturn(List.of(1.0, 0.0, 0.0)); // Mock embeddings

            // When
            List<GeneratedQA> result = processingService.generateAdditionalQA(uploadId, 1, 0);

            // Then
            assertThat(result).hasSize(1);
            verify(ragService).ask(eq("What is the test?"), eq("test-category"));
            // QA should be saved twice: once initially with PENDING, once after validation
            verify(qaRepository, times(2)).save(any(GeneratedQA.class));
        }
    }

    @Nested
    @DisplayName("Document Scoped Chat Tests")
    class DocumentScopedChatTests {

        @Test
        @DisplayName("Should answer question using document content")
        void shouldAnswerQuestionUsingDocumentContent() {
            // Given
            String uploadId = "upload-123";
            String documentContent = "This document describes how to configure the application.";
            String question = "How do I configure the application?";
            String expectedAnswer = "You can configure the application using the settings panel.";

            DocumentUpload upload = createUpload(uploadId, documentContent);

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            when(chatClient.chatOnce(anyString(), eq(0.3), eq(1024))).thenReturn(expectedAnswer);

            // When
            String result = processingService.documentScopedChat(uploadId, question);

            // Then
            assertThat(result).isEqualTo(expectedAnswer);

            // Verify the prompt contains both document content and question
            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(chatClient).chatOnce(promptCaptor.capture(), eq(0.3), eq(1024));

            String capturedPrompt = promptCaptor.getValue();
            assertThat(capturedPrompt).contains(documentContent);
            assertThat(capturedPrompt).contains(question);
            assertThat(capturedPrompt).contains("ONLY use information that is EXPLICITLY stated");
        }

        @Test
        @DisplayName("Should throw exception when upload not found")
        void shouldThrowWhenUploadNotFound() {
            // Given
            when(uploadRepository.findById("non-existent")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> processingService.documentScopedChat("non-existent", "question"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Upload not found");
        }

        @Test
        @DisplayName("Should truncate long document content")
        void shouldTruncateLongDocumentContent() {
            // Given
            String uploadId = "upload-123";
            String longContent = "x".repeat(10000); // Very long content

            DocumentUpload upload = createUpload(uploadId, longContent);

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            when(chatClient.chatOnce(anyString(), eq(0.3), eq(1024))).thenReturn("Answer");

            // When
            processingService.documentScopedChat(uploadId, "question");

            // Then
            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(chatClient).chatOnce(promptCaptor.capture(), eq(0.3), eq(1024));

            // The prompt should not contain the full 10000 characters
            assertThat(promptCaptor.getValue().length()).isLessThan(10000);
            assertThat(promptCaptor.getValue()).contains("[truncated]");
        }
    }

    @Nested
    @DisplayName("Document Scoped Chat Stream Tests")
    class DocumentScopedChatStreamTests {

        @Test
        @DisplayName("Should stream chat response with tokens")
        void shouldStreamChatResponse() {
            // Given
            String uploadId = "upload-123";
            String documentContent = "Test document content.";
            String question = "What is this about?";

            DocumentUpload upload = createUpload(uploadId, documentContent);

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));
            doAnswer(invocation -> {
                Consumer<String> onToken = invocation.getArgument(3);
                onToken.accept("Hello");
                onToken.accept(" ");
                onToken.accept("World");
                return null;
            }).when(chatClient).chatStream(anyString(), eq(0.3), eq(1024), any());

            StringBuilder result = new StringBuilder();

            // When
            processingService.documentScopedChatStream(uploadId, question, result::append);

            // Then
            assertThat(result.toString()).isEqualTo("Hello World");

            // Verify prompt was built correctly
            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(chatClient).chatStream(promptCaptor.capture(), eq(0.3), eq(1024), any());

            assertThat(promptCaptor.getValue()).contains(documentContent);
            assertThat(promptCaptor.getValue()).contains(question);
        }

        @Test
        @DisplayName("Should throw exception when upload not found")
        void shouldThrowWhenUploadNotFound() {
            // Given
            when(uploadRepository.findById("non-existent")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> processingService.documentScopedChatStream("non-existent", "question", s -> {}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Upload not found");
        }
    }

    @Nested
    @DisplayName("Initiate Upload Tests")
    class InitiateUploadTests {

        @Test
        @DisplayName("Should initiate upload successfully")
        void shouldInitiateUploadSuccessfully() {
            // Given
            when(uploadRepository.save(any(DocumentUpload.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            DocumentUpload result = processingService.initiateUpload(
                    "doc-123", "Test Title", "Test Content", "cat-1");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDocId()).isEqualTo("doc-123");
            assertThat(result.getTitle()).isEqualTo("Test Title");
            assertThat(result.getOriginalContent()).isEqualTo("Test Content");
            assertThat(result.getCategoryId()).isEqualTo("cat-1");
            assertThat(result.getStatus()).isEqualTo(ProcessingStatus.PENDING);
            assertThat(result.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Delete Upload Tests")
    class DeleteUploadTests {

        @Test
        @DisplayName("Should delete upload and associated data")
        void shouldDeleteUploadAndAssociatedData() {
            // Given
            String uploadId = "upload-123";
            DocumentUpload upload = createUpload(uploadId, "content");

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

            // When
            processingService.deleteUpload(uploadId);

            // Then
            verify(qaRepository).deleteByUploadId(uploadId);
            verify(tempCollectionService).deleteTempCollection(uploadId);
            verify(uploadRepository).delete(upload);
        }

        @Test
        @DisplayName("Should do nothing when upload not found")
        void shouldDoNothingWhenUploadNotFound() {
            // Given
            when(uploadRepository.findById("non-existent")).thenReturn(Optional.empty());

            // When
            processingService.deleteUpload("non-existent");

            // Then
            verify(qaRepository, never()).deleteByUploadId(anyString());
            verify(uploadRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Retry Processing Tests")
    class RetryProcessingTests {

        @Test
        @DisplayName("Should reset upload state and restart processing")
        void shouldResetAndRestartProcessing() {
            // Given
            String uploadId = "upload-123";
            DocumentUpload upload = createUpload(uploadId, "content");
            upload.setStatus(ProcessingStatus.FAILED);
            upload.setQuestionsGenerated(5);
            upload.setQuestionsValidated(3);
            upload.setTotalChunks(10);
            upload.setErrorMessage("Previous error");

            when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

            // Capture the state at first save (before async processing modifies the object)
            final ProcessingStatus[] capturedStatus = {null};
            final String[] capturedError = {""};
            final int[] capturedQuestionsGenerated = {-1};
            final int[] capturedQuestionsValidated = {-1};
            final int[] capturedTotalChunks = {-1};

            when(uploadRepository.save(any(DocumentUpload.class))).thenAnswer(invocation -> {
                DocumentUpload saved = invocation.getArgument(0);
                // Only capture on first call
                if (capturedStatus[0] == null) {
                    capturedStatus[0] = saved.getStatus();
                    capturedError[0] = saved.getErrorMessage();
                    capturedQuestionsGenerated[0] = saved.getQuestionsGenerated();
                    capturedQuestionsValidated[0] = saved.getQuestionsValidated();
                    capturedTotalChunks[0] = saved.getTotalChunks();
                }
                return saved;
            });

            // When
            processingService.retryProcessing(uploadId);

            // Then
            verify(qaRepository).deleteByUploadId(uploadId);
            verify(tempCollectionService).deleteTempCollection(uploadId);
            verify(uploadRepository, atLeastOnce()).save(any(DocumentUpload.class));

            // Verify the first save had the reset state
            assertThat(capturedStatus[0]).isEqualTo(ProcessingStatus.PENDING);
            assertThat(capturedError[0]).isNull();
            assertThat(capturedQuestionsGenerated[0]).isEqualTo(0);
            assertThat(capturedQuestionsValidated[0]).isEqualTo(0);
            assertThat(capturedTotalChunks[0]).isEqualTo(0);
        }

        @Test
        @DisplayName("Should throw exception when upload not found")
        void shouldThrowWhenUploadNotFound() {
            // Given
            when(uploadRepository.findById("non-existent")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> processingService.retryProcessing("non-existent"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Upload not found");
        }
    }

    // Helper method
    private DocumentUpload createUpload(String uploadId, String content) {
        return DocumentUpload.builder()
                .id(uploadId)
                .docId("doc-" + uploadId)
                .title("Test Document")
                .originalContent(content)
                .categoryId("cat-1")
                .status(ProcessingStatus.READY_FOR_REVIEW)
                .questionsGenerated(0)
                .questionsValidated(0)
                .totalChunks(0)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
