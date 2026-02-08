package com.enterprise.cobol.controller;

import com.enterprise.cobol.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RagControllerTest {

    private MockMvc mockMvc;
    private RagService ragService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ragService = mock(RagService.class);
        objectMapper = new ObjectMapper();

        RagController controller = new RagController(ragService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testAskQuestion() throws Exception {
        Map<String, Object> ragResponse = Map.of(
                "answer", "CBTRN01C handles authentication",
                "sources", List.of(Map.of("programName", "CBTRN01C", "score", 0.95)),
                "question", "Which program handles auth?"
        );
        when(ragService.askQuestion(eq("Which program handles auth?"), isNull()))
                .thenReturn(ragResponse);

        mockMvc.perform(post("/api/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "Which program handles auth?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("CBTRN01C handles authentication"))
                .andExpect(jsonPath("$.sources[0].programName").value("CBTRN01C"));
    }

    @Test
    void testAskQuestionWithBatchRunId() throws Exception {
        Map<String, Object> ragResponse = Map.of(
                "answer", "Scoped answer",
                "sources", List.of(),
                "question", "test question"
        );
        when(ragService.askQuestion(eq("test question"), eq("run-abc")))
                .thenReturn(ragResponse);

        mockMvc.perform(post("/api/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "test question",
                                "batchRunId", "run-abc"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Scoped answer"));
    }

    @Test
    void testAskQuestionMissing() throws Exception {
        mockMvc.perform(post("/api/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "batchRunId", "run-abc"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("question is required"));
    }

    @Test
    void testAskQuestionBlank() throws Exception {
        mockMvc.perform(post("/api/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("question is required"));
    }
}
