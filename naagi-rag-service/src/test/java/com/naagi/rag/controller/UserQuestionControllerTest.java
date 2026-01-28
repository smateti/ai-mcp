package com.naagi.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.rag.entity.FaqEntry;
import com.naagi.rag.entity.UserQuestion;
import com.naagi.rag.service.UserQuestionAnalyticsService;
import com.naagi.rag.service.UserQuestionAnalyticsService.PromotionResult;
import com.naagi.rag.service.UserQuestionAnalyticsService.QuestionAnalytics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for UserQuestionController.
 * Tests REST API endpoints for user question analytics.
 */
@WebMvcTest(UserQuestionController.class)
class UserQuestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserQuestionAnalyticsService analyticsService;

    @Nested
    @DisplayName("POST /api/questions/track")
    class TrackQuestionTests {

        @Test
        @DisplayName("Should track question successfully")
        void shouldTrackQuestionSuccessfully() throws Exception {
            // Given
            UserQuestion tracked = UserQuestion.builder()
                    .id("q-123")
                    .question("How do I reset my password?")
                    .categoryId("cat-1")
                    .frequency(1)
                    .build();

            when(analyticsService.trackQuestion(any())).thenReturn(tracked);

            // When/Then
            mockMvc.perform(post("/api/questions/track")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "question": "How do I reset my password?",
                                    "categoryId": "cat-1"
                                }
                                """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.questionId").value("q-123"))
                    .andExpect(jsonPath("$.frequency").value(1))
                    .andExpect(jsonPath("$.isDuplicate").value(false));
        }

        @Test
        @DisplayName("Should return success false when tracking fails")
        void shouldReturnSuccessFalseWhenTrackingFails() throws Exception {
            // Given
            when(analyticsService.trackQuestion(any())).thenReturn(null);

            // When/Then
            mockMvc.perform(post("/api/questions/track")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "question": "Test question",
                                    "categoryId": "cat-1"
                                }
                                """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("POST /api/questions/{questionId}/promote-to-faq")
    class PromoteToFaqTests {

        @Test
        @DisplayName("Should promote question to FAQ successfully")
        void shouldPromoteQuestionToFaqSuccessfully() throws Exception {
            // Given
            FaqEntry createdFaq = FaqEntry.builder()
                    .id(1L)
                    .question("How do I reset my password?")
                    .answer("Go to settings and click reset")
                    .categoryId("cat-1")
                    .build();

            UserQuestion deletedQuestion = UserQuestion.builder()
                    .id("q-123")
                    .question("How do I reset my password?")
                    .build();

            PromotionResult result = new PromotionResult(createdFaq, deletedQuestion, null);
            when(analyticsService.promoteToFaqAndDelete(eq("q-123"), anyString(), anyString()))
                    .thenReturn(result);

            // When/Then
            mockMvc.perform(post("/api/questions/q-123/promote-to-faq")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "answer": "Go to settings and click reset",
                                    "promotedBy": "admin"
                                }
                                """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.faqId").value(1))
                    .andExpect(jsonPath("$.question").value("How do I reset my password?"))
                    .andExpect(jsonPath("$.answer").value("Go to settings and click reset"));
        }

        @Test
        @DisplayName("Should return bad request when answer is missing")
        void shouldReturnBadRequestWhenAnswerMissing() throws Exception {
            // When/Then
            mockMvc.perform(post("/api/questions/q-123/promote-to-faq")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "promotedBy": "admin"
                                }
                                """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error").value("Answer is required to create FAQ"));
        }

        @Test
        @DisplayName("Should return bad request when promotion fails")
        void shouldReturnBadRequestWhenPromotionFails() throws Exception {
            // Given
            PromotionResult result = new PromotionResult(null, null, "Question not found");
            when(analyticsService.promoteToFaqAndDelete(anyString(), anyString(), anyString()))
                    .thenReturn(result);

            // When/Then
            mockMvc.perform(post("/api/questions/q-123/promote-to-faq")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "answer": "Test answer",
                                    "promotedBy": "admin"
                                }
                                """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error").value("Question not found"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/questions/{questionId}")
    class DeleteQuestionTests {

        @Test
        @DisplayName("Should delete question successfully")
        void shouldDeleteQuestionSuccessfully() throws Exception {
            // Given
            when(analyticsService.deleteQuestion("q-123")).thenReturn(true);

            // When/Then
            mockMvc.perform(delete("/api/questions/q-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Should return not found when question doesn't exist")
        void shouldReturnNotFoundWhenQuestionDoesntExist() throws Exception {
            // Given
            when(analyticsService.deleteQuestion("non-existent")).thenReturn(false);

            // When/Then
            mockMvc.perform(delete("/api/questions/non-existent"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/questions/frequent")
    class GetFrequentQuestionsTests {

        @Test
        @DisplayName("Should return frequent questions")
        void shouldReturnFrequentQuestions() throws Exception {
            // Given
            UserQuestion q1 = UserQuestion.builder()
                    .id("q-1")
                    .question("Question 1")
                    .frequency(10)
                    .build();
            UserQuestion q2 = UserQuestion.builder()
                    .id("q-2")
                    .question("Question 2")
                    .frequency(5)
                    .build();

            when(analyticsService.getFrequentlyAskedQuestions(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(q1, q2)));

            // When/Then
            mockMvc.perform(get("/api/questions/frequent")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].frequency").value(10));
        }
    }

    @Nested
    @DisplayName("GET /api/questions/analytics")
    class GetAnalyticsTests {

        @Test
        @DisplayName("Should return analytics")
        void shouldReturnAnalytics() throws Exception {
            // Given
            QuestionAnalytics analytics = new QuestionAnalytics(
                    100L, 500L, 60L, 40L, 60.0, Map.of("pointsCount", 100L)
            );
            when(analyticsService.getAnalytics(any())).thenReturn(analytics);

            // When/Then
            mockMvc.perform(get("/api/questions/analytics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalUniqueQuestions").value(100))
                    .andExpect(jsonPath("$.totalQuestionCount").value(500))
                    .andExpect(jsonPath("$.faqCoveragePercent").value(60.0));
        }
    }

    @Nested
    @DisplayName("GET /api/questions/{questionId}")
    class GetQuestionByIdTests {

        @Test
        @DisplayName("Should return question by ID")
        void shouldReturnQuestionById() throws Exception {
            // Given
            UserQuestion question = UserQuestion.builder()
                    .id("q-123")
                    .question("Test question")
                    .frequency(5)
                    .build();
            when(analyticsService.getQuestionById("q-123")).thenReturn(Optional.of(question));

            // When/Then
            mockMvc.perform(get("/api/questions/q-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("q-123"))
                    .andExpect(jsonPath("$.question").value("Test question"));
        }

        @Test
        @DisplayName("Should return not found when question doesn't exist")
        void shouldReturnNotFoundWhenQuestionDoesntExist() throws Exception {
            // Given
            when(analyticsService.getQuestionById("non-existent")).thenReturn(Optional.empty());

            // When/Then
            mockMvc.perform(get("/api/questions/non-existent"))
                    .andExpect(status().isNotFound());
        }
    }
}
