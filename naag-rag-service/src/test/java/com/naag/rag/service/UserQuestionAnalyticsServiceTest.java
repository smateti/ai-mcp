package com.naag.rag.service;

import com.naag.rag.config.FaqConfig;
import com.naag.rag.entity.FaqEntry;
import com.naag.rag.entity.UserQuestion;
import com.naag.rag.llm.EmbeddingsClient;
import com.naag.rag.qdrant.FaqQdrantClient;
import com.naag.rag.qdrant.UserQuestionQdrantClient;
import com.naag.rag.repository.FaqEntryRepository;
import com.naag.rag.repository.UserQuestionRepository;
import com.naag.rag.service.UserQuestionAnalyticsService.PromotionResult;
import com.naag.rag.service.UserQuestionAnalyticsService.TrackQuestionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserQuestionAnalyticsService.
 * Tests question tracking, deduplication, FAQ matching, and promotion workflows.
 */
@ExtendWith(MockitoExtension.class)
class UserQuestionAnalyticsServiceTest {

    @Mock
    private UserQuestionRepository questionRepository;

    @Mock
    private FaqEntryRepository faqEntryRepository;

    @Mock
    private UserQuestionQdrantClient questionQdrantClient;

    @Mock
    private FaqQdrantClient faqQdrantClient;

    @Mock
    private EmbeddingsClient embeddingsClient;

    @Mock
    private FaqConfig faqConfig;

    @Mock
    private FaqManagementService faqManagementService;

    private UserQuestionAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new UserQuestionAnalyticsService(
                questionRepository,
                faqEntryRepository,
                questionQdrantClient,
                faqQdrantClient,
                embeddingsClient,
                faqConfig,
                faqManagementService
        );
    }

    @Nested
    @DisplayName("Track Question Tests")
    class TrackQuestionTests {

        @Test
        @DisplayName("Should track new unique question")
        void shouldTrackNewUniqueQuestion() {
            // Given
            String question = "How do I reset my password?";
            String categoryId = "cat-1";
            TrackQuestionRequest request = new TrackQuestionRequest(question, categoryId);

            when(faqConfig.isStoreAllQuestions()).thenReturn(true);
            when(faqConfig.getDeduplicationThreshold()).thenReturn(0.95);
            when(faqConfig.getFaqMinSimilarityScore()).thenReturn(0.85);
            when(embeddingsClient.embed(question)).thenReturn(List.of(0.1, 0.2, 0.3));
            when(questionQdrantClient.findSimilarQuestions(anyList(), eq(1), eq(0.95), eq(categoryId)))
                    .thenReturn(List.of());
            when(faqQdrantClient.searchFaqs(anyList(), eq(1), eq(categoryId), eq(0.85)))
                    .thenReturn(List.of());
            when(questionRepository.save(any(UserQuestion.class))).thenAnswer(invocation -> {
                UserQuestion q = invocation.getArgument(0);
                q.setId("q-123");
                return q;
            });

            // When
            UserQuestion result = analyticsService.trackQuestion(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getQuestion()).isEqualTo(question);
            assertThat(result.getCategoryId()).isEqualTo(categoryId);
            assertThat(result.getFrequency()).isEqualTo(1);
            verify(questionRepository).save(any(UserQuestion.class));
            verify(questionQdrantClient).upsertQuestion(any());
        }

        @Test
        @DisplayName("Should increment frequency for duplicate question")
        void shouldIncrementFrequencyForDuplicate() {
            // Given
            String question = "How do I reset my password?";
            String categoryId = "cat-1";
            TrackQuestionRequest request = new TrackQuestionRequest(question, categoryId);

            UserQuestion existingQuestion = UserQuestion.builder()
                    .id("existing-id")
                    .question(question)
                    .categoryId(categoryId)
                    .frequency(5)
                    .qdrantPointId("point-123")
                    .build();

            when(faqConfig.isStoreAllQuestions()).thenReturn(true);
            when(faqConfig.getDeduplicationThreshold()).thenReturn(0.95);
            when(embeddingsClient.embed(question)).thenReturn(List.of(0.1, 0.2, 0.3));
            when(questionQdrantClient.findSimilarQuestions(anyList(), eq(1), eq(0.95), eq(categoryId)))
                    .thenReturn(List.of(new UserQuestionQdrantClient.SimilarQuestionResult(
                            "existing-id", question, categoryId, null, 5, null, 0.98
                    )));
            when(questionRepository.findById("existing-id")).thenReturn(Optional.of(existingQuestion));
            when(questionRepository.save(any(UserQuestion.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // When
            UserQuestion result = analyticsService.trackQuestion(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFrequency()).isEqualTo(6);
            verify(questionQdrantClient).updateFrequency("point-123", 6);
        }

        @Test
        @DisplayName("Should return null when tracking is disabled")
        void shouldReturnNullWhenTrackingDisabled() {
            // Given
            when(faqConfig.isStoreAllQuestions()).thenReturn(false);

            // When
            UserQuestion result = analyticsService.trackQuestion(
                    new TrackQuestionRequest("question", "cat-1"));

            // Then
            assertThat(result).isNull();
            verifyNoInteractions(questionRepository);
        }
    }

    @Nested
    @DisplayName("Promote to FAQ Tests")
    class PromoteToFaqTests {

        @Test
        @DisplayName("Should promote question to FAQ and delete from analytics")
        void shouldPromoteQuestionToFaqAndDelete() {
            // Given
            String questionId = "q-123";
            String answer = "Go to settings and click reset password";
            String promotedBy = "admin";

            UserQuestion question = UserQuestion.builder()
                    .id(questionId)
                    .question("How do I reset my password?")
                    .categoryId("cat-1")
                    .frequency(10)
                    .qdrantPointId("point-123")
                    .build();

            FaqEntry createdFaq = FaqEntry.builder()
                    .id(1L)
                    .question(question.getQuestion())
                    .answer(answer)
                    .categoryId("cat-1")
                    .questionType("USER_PROMOTED")
                    .active(true)
                    .build();

            when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
            when(faqManagementService.createFaqFromUserQuestion(
                    question.getQuestion(), answer, "cat-1", promotedBy))
                    .thenReturn(createdFaq);

            // When
            PromotionResult result = analyticsService.promoteToFaqAndDelete(questionId, answer, promotedBy);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.createdFaq()).isEqualTo(createdFaq);
            assertThat(result.deletedQuestion()).isEqualTo(question);
            assertThat(result.error()).isNull();

            verify(faqManagementService).createFaqFromUserQuestion(
                    question.getQuestion(), answer, "cat-1", promotedBy);
            verify(questionQdrantClient).deleteQuestion("point-123");
            verify(questionRepository).delete(question);
        }

        @Test
        @DisplayName("Should return error when question not found")
        void shouldReturnErrorWhenQuestionNotFound() {
            // Given
            when(questionRepository.findById("non-existent")).thenReturn(Optional.empty());

            // When
            PromotionResult result = analyticsService.promoteToFaqAndDelete(
                    "non-existent", "answer", "admin");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.error()).isEqualTo("Question not found");
            verifyNoInteractions(faqManagementService);
        }

        @Test
        @DisplayName("Should return error when FAQ creation fails")
        void shouldReturnErrorWhenFaqCreationFails() {
            // Given
            String questionId = "q-123";
            UserQuestion question = UserQuestion.builder()
                    .id(questionId)
                    .question("Test question")
                    .categoryId("cat-1")
                    .build();

            when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
            when(faqManagementService.createFaqFromUserQuestion(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(null);

            // When
            PromotionResult result = analyticsService.promoteToFaqAndDelete(
                    questionId, "answer", "admin");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.error()).isEqualTo("Failed to create FAQ");
            verify(questionRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Delete Question Tests")
    class DeleteQuestionTests {

        @Test
        @DisplayName("Should delete question from H2 and Qdrant")
        void shouldDeleteQuestionFromH2AndQdrant() {
            // Given
            String questionId = "q-123";
            UserQuestion question = UserQuestion.builder()
                    .id(questionId)
                    .question("Test question")
                    .qdrantPointId("point-123")
                    .build();

            when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

            // When
            boolean result = analyticsService.deleteQuestion(questionId);

            // Then
            assertThat(result).isTrue();
            verify(questionQdrantClient).deleteQuestion("point-123");
            verify(questionRepository).delete(question);
        }

        @Test
        @DisplayName("Should return false when question not found")
        void shouldReturnFalseWhenQuestionNotFound() {
            // Given
            when(questionRepository.findById("non-existent")).thenReturn(Optional.empty());

            // When
            boolean result = analyticsService.deleteQuestion("non-existent");

            // Then
            assertThat(result).isFalse();
            verify(questionRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Analytics Tests")
    class AnalyticsTests {

        @Test
        @DisplayName("Should return correct analytics")
        void shouldReturnCorrectAnalytics() {
            // Given
            when(questionRepository.count()).thenReturn(100L);
            when(questionRepository.getTotalQuestionCount()).thenReturn(500L);
            when(questionRepository.countByMatchedFaqIdIsNotNull()).thenReturn(60L);
            when(questionRepository.countByMatchedFaqIdIsNull()).thenReturn(40L);
            when(questionQdrantClient.getCollectionStats()).thenReturn(java.util.Map.of("pointsCount", 100L));

            // When
            var analytics = analyticsService.getAnalytics(null);

            // Then
            assertThat(analytics.totalUniqueQuestions()).isEqualTo(100L);
            assertThat(analytics.totalQuestionCount()).isEqualTo(500L);
            assertThat(analytics.matchedFaqCount()).isEqualTo(60L);
            assertThat(analytics.unmatchedCount()).isEqualTo(40L);
            assertThat(analytics.faqCoveragePercent()).isEqualTo(60.0);
        }
    }

    @Nested
    @DisplayName("Get FAQ Details Tests")
    class GetFaqDetailsTests {

        @Test
        @DisplayName("Should return FAQ details when found")
        void shouldReturnFaqDetailsWhenFound() {
            // Given
            FaqEntry faq = FaqEntry.builder()
                    .id(123L)
                    .question("How do I reset my password?")
                    .answer("Go to settings and click reset password")
                    .categoryId("cat-1")
                    .build();

            when(faqEntryRepository.findById(123L)).thenReturn(Optional.of(faq));

            // When
            Optional<FaqEntry> result = analyticsService.getFaqDetails("123");

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getQuestion()).isEqualTo("How do I reset my password?");
            assertThat(result.get().getAnswer()).isEqualTo("Go to settings and click reset password");
        }

        @Test
        @DisplayName("Should return empty when FAQ not found")
        void shouldReturnEmptyWhenFaqNotFound() {
            // Given
            when(faqEntryRepository.findById(999L)).thenReturn(Optional.empty());

            // When
            Optional<FaqEntry> result = analyticsService.getFaqDetails("999");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty for invalid FAQ ID format")
        void shouldReturnEmptyForInvalidFaqIdFormat() {
            // When
            Optional<FaqEntry> result = analyticsService.getFaqDetails("invalid-id");

            // Then
            assertThat(result).isEmpty();
            verifyNoInteractions(faqEntryRepository);
        }
    }
}
