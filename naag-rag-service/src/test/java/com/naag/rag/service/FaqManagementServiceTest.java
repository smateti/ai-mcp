package com.naag.rag.service;

import com.naag.rag.config.FaqConfig;
import com.naag.rag.entity.FaqEntry;
import com.naag.rag.entity.FaqSettings;
import com.naag.rag.entity.GeneratedQA;
import com.naag.rag.llm.EmbeddingsClient;
import com.naag.rag.qdrant.FaqQdrantClient;
import com.naag.rag.qdrant.FaqQdrantClient.FaqSearchResult;
import com.naag.rag.repository.DocumentUploadRepository;
import com.naag.rag.repository.FaqEntryRepository;
import com.naag.rag.repository.FaqSettingsRepository;
import com.naag.rag.repository.GeneratedQARepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FaqManagementService.
 * Tests FAQ creation, querying, and settings management.
 */
@ExtendWith(MockitoExtension.class)
class FaqManagementServiceTest {

    @Mock
    private GeneratedQARepository qaRepository;

    @Mock
    private DocumentUploadRepository uploadRepository;

    @Mock
    private FaqEntryRepository faqEntryRepository;

    @Mock
    private FaqSettingsRepository settingsRepository;

    @Mock
    private FaqQdrantClient faqQdrantClient;

    @Mock
    private EmbeddingsClient embeddingsClient;

    @Mock
    private FaqConfig faqConfig;

    private FaqManagementService faqManagementService;

    @BeforeEach
    void setUp() {
        faqManagementService = new FaqManagementService(
                qaRepository,
                uploadRepository,
                faqEntryRepository,
                settingsRepository,
                faqQdrantClient,
                embeddingsClient,
                faqConfig
        );
    }

    @Nested
    @DisplayName("Create FAQ from User Question Tests")
    class CreateFaqFromUserQuestionTests {

        @Test
        @DisplayName("Should create FAQ from user question successfully")
        void shouldCreateFaqFromUserQuestion() {
            // Given
            String question = "How do I reset my password?";
            String answer = "Go to settings and click reset password";
            String categoryId = "cat-1";
            String createdBy = "admin";

            when(embeddingsClient.embed(question)).thenReturn(List.of(0.1, 0.2, 0.3));
            when(faqEntryRepository.save(any(FaqEntry.class))).thenAnswer(invocation -> {
                FaqEntry entry = invocation.getArgument(0);
                entry.setId(1L);
                return entry;
            });

            // When
            FaqEntry result = faqManagementService.createFaqFromUserQuestion(
                    question, answer, categoryId, createdBy);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getQuestion()).isEqualTo(question);
            assertThat(result.getAnswer()).isEqualTo(answer);
            assertThat(result.getCategoryId()).isEqualTo(categoryId);
            assertThat(result.getQuestionType()).isEqualTo("USER_PROMOTED");
            assertThat(result.getDocId()).isEqualTo("USER_QUESTION");
            assertThat(result.isActive()).isTrue();

            verify(faqQdrantClient).upsertFaq(any());
            verify(faqEntryRepository).save(any(FaqEntry.class));
        }

        @Test
        @DisplayName("Should return null when Qdrant client is not available")
        void shouldReturnNullWhenQdrantNotAvailable() {
            // Given - create service without Qdrant client
            FaqManagementService serviceWithoutQdrant = new FaqManagementService(
                    qaRepository, uploadRepository, faqEntryRepository,
                    settingsRepository, null, embeddingsClient, faqConfig
            );

            // When
            FaqEntry result = serviceWithoutQdrant.createFaqFromUserQuestion(
                    "question", "answer", "cat-1", "admin");

            // Then
            assertThat(result).isNull();
            verifyNoInteractions(faqEntryRepository);
        }

        @Test
        @DisplayName("Should return null when embedding fails")
        void shouldReturnNullWhenEmbeddingFails() {
            // Given
            when(embeddingsClient.embed(anyString())).thenThrow(new RuntimeException("Embedding failed"));

            // When
            FaqEntry result = faqManagementService.createFaqFromUserQuestion(
                    "question", "answer", "cat-1", "admin");

            // Then
            assertThat(result).isNull();
            verifyNoInteractions(faqEntryRepository);
        }
    }

    @Nested
    @DisplayName("FAQ Query Tests")
    class FaqQueryTests {

        @Test
        @DisplayName("Should query FAQs when enabled")
        void shouldQueryFaqsWhenEnabled() {
            // Given
            String question = "password reset";
            String categoryId = "cat-1";
            FaqSettings settings = FaqSettings.builder()
                    .faqQueryEnabled(true)
                    .minSimilarityScore(0.85)
                    .build();

            when(settingsRepository.getSettings()).thenReturn(settings);
            when(embeddingsClient.embed(question)).thenReturn(List.of(0.1, 0.2, 0.3));
            when(faqQdrantClient.searchFaqs(anyList(), eq(5), eq(categoryId), eq(0.85)))
                    .thenReturn(List.of(
                            new FaqSearchResult("faq-1", "How to reset password?",
                                    "Click reset link", categoryId, null, "doc-1", "title", 0.92)
                    ));

            // When
            List<FaqSearchResult> results = faqManagementService.queryFaqs(question, categoryId, 5);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).faqId()).isEqualTo("faq-1");
            assertThat(results.get(0).score()).isEqualTo(0.92);
        }

        @Test
        @DisplayName("Should return empty list when FAQ query is disabled")
        void shouldReturnEmptyWhenFaqQueryDisabled() {
            // Given
            FaqSettings settings = FaqSettings.builder()
                    .faqQueryEnabled(false)
                    .build();

            when(settingsRepository.getSettings()).thenReturn(settings);

            // When
            List<FaqSearchResult> results = faqManagementService.queryFaqsIfEnabled(
                    "question", "cat-1", 5);

            // Then
            assertThat(results).isEmpty();
            verifyNoInteractions(embeddingsClient);
            verifyNoInteractions(faqQdrantClient);
        }

        @Test
        @DisplayName("Should find best match")
        void shouldFindBestMatch() {
            // Given
            FaqSettings settings = FaqSettings.builder()
                    .faqQueryEnabled(true)
                    .minSimilarityScore(0.85)
                    .build();

            when(settingsRepository.getSettings()).thenReturn(settings);
            when(embeddingsClient.embed(anyString())).thenReturn(List.of(0.1, 0.2, 0.3));
            when(faqQdrantClient.searchFaqs(anyList(), eq(1), anyString(), eq(0.85)))
                    .thenReturn(List.of(
                            new FaqSearchResult("faq-1", "Best match question",
                                    "Best match answer", "cat-1", null, "doc-1", "title", 0.95)
                    ));

            // When
            Optional<FaqSearchResult> result = faqManagementService.findBestMatch("question", "cat-1");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().score()).isEqualTo(0.95);
        }
    }

    @Nested
    @DisplayName("FAQ Settings Tests")
    class FaqSettingsTests {

        @Test
        @DisplayName("Should return FAQ query enabled status")
        void shouldReturnFaqQueryEnabledStatus() {
            // Given
            FaqSettings settings = FaqSettings.builder()
                    .faqQueryEnabled(true)
                    .build();
            when(settingsRepository.getSettings()).thenReturn(settings);

            // When
            boolean isEnabled = faqManagementService.isFaqQueryEnabled();

            // Then
            assertThat(isEnabled).isTrue();
        }

        @Test
        @DisplayName("Should return settings")
        void shouldReturnSettings() {
            // Given
            FaqSettings settings = FaqSettings.builder()
                    .faqQueryEnabled(true)
                    .minSimilarityScore(0.90)
                    .storeUserQuestions(true)
                    .build();
            when(settingsRepository.getSettings()).thenReturn(settings);

            // When
            FaqSettings result = faqManagementService.getSettings();

            // Then
            assertThat(result.isFaqQueryEnabled()).isTrue();
            assertThat(result.getMinSimilarityScore()).isEqualTo(0.90);
        }
    }

    @Nested
    @DisplayName("FAQ CRUD Tests")
    class FaqCrudTests {

        @Test
        @DisplayName("Should get FAQ by ID")
        void shouldGetFaqById() {
            // Given
            FaqEntry faq = FaqEntry.builder()
                    .id(1L)
                    .question("Test question")
                    .answer("Test answer")
                    .build();
            when(faqEntryRepository.findById(1L)).thenReturn(Optional.of(faq));

            // When
            Optional<FaqEntry> result = faqManagementService.getFaqById(1L);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getQuestion()).isEqualTo("Test question");
        }

        @Test
        @DisplayName("Should deactivate FAQ")
        void shouldDeactivateFaq() {
            // Given
            FaqEntry faq = FaqEntry.builder()
                    .id(1L)
                    .question("Test question")
                    .active(true)
                    .build();
            when(faqEntryRepository.findById(1L)).thenReturn(Optional.of(faq));

            // When
            faqManagementService.deactivateFaq(1L);

            // Then
            assertThat(faq.isActive()).isFalse();
            verify(faqEntryRepository).save(faq);
        }
    }
}
