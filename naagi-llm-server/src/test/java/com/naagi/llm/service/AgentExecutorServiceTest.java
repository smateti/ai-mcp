package com.naagi.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naagi.llm.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentExecutorServiceTest {

    @Mock
    private LlamaCppProxy llamaCppProxy;

    @Mock
    private ToolExecutionService toolExecutionService;

    private AgentExecutorService agentService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        agentService = new AgentExecutorService(llamaCppProxy, toolExecutionService, objectMapper);
        ReflectionTestUtils.setField(agentService, "defaultMaxSteps", 10);
        ReflectionTestUtils.setField(agentService, "defaultSystemPrompt", "You are helpful");
    }

    @Test
    void execute_directAnswer_noToolCalls() {
        AgentRequest request = new AgentRequest(
                "What is 2+2?", null,
                List.of(buildAgentTool("calc", "Calculate", "http://calc/api", "GET")),
                5, 0.2, 1024, false);

        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.text("4"));

        AgentResponse response = agentService.execute(request);

        assertEquals("4", response.answer());
        assertEquals(0, response.totalToolCalls());
        assertEquals(1, response.totalSteps());
        assertTrue(response.steps().isEmpty());
    }

    @Test
    void execute_singleToolCall_thenFinalAnswer() {
        AgentRequest request = new AgentRequest(
                "Search logs", null,
                List.of(buildAgentTool("search_logs", "Search logs", "http://localhost:8084/api/logs/search", "GET")),
                5, 0.2, 1024, false);

        // Step 1: LLM returns tool call
        ChatResponse toolCallResponse = ChatResponse.withToolCalls(
                List.of(ToolCall.function("call_1", "search_logs", "{\"containerName\":\"app-inv\"}")));
        // Step 2: LLM returns final answer
        ChatResponse finalResponse = ChatResponse.text("Found 10 logs for app-inv");

        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);
        when(toolExecutionService.executeTool(
                eq("http://localhost:8084/api/logs/search"), eq("GET"), any()))
                .thenReturn("{\"hits\":{\"total\":10}}");

        AgentResponse response = agentService.execute(request);

        assertEquals("Found 10 logs for app-inv", response.answer());
        assertEquals(1, response.totalToolCalls());
        assertEquals(1, response.steps().size());
        assertEquals("search_logs", response.steps().get(0).tool());
        verify(toolExecutionService).executeTool(
                "http://localhost:8084/api/logs/search", "GET", "{\"containerName\":\"app-inv\"}");
    }

    @Test
    void execute_multipleToolCalls_chainedSteps() {
        AgentRequest request = new AgentRequest(
                "Find failed jobs and their logs", null,
                List.of(
                        buildAgentTool("get_jobs", "Get jobs", "http://localhost:8084/api/batch-jobs/app-inv/runs", "GET"),
                        buildAgentTool("get_details", "Get details", "http://localhost:8084/api/batch-jobs/runs/{jobInstanceId}", "GET")
                ),
                5, 0.2, 1024, false);

        // Step 1: call get_jobs
        ChatResponse step1 = ChatResponse.withToolCalls(
                List.of(ToolCall.function("c1", "get_jobs", "{\"startTime\":\"2026-01-01\"}")));
        // Step 2: call get_details
        ChatResponse step2 = ChatResponse.withToolCalls(
                List.of(ToolCall.function("c2", "get_details", "{\"jobInstanceId\":\"abc-123\"}")));
        // Step 3: final answer
        ChatResponse step3 = ChatResponse.text("Job abc-123 failed");

        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(step1).thenReturn(step2).thenReturn(step3);
        when(toolExecutionService.executeTool(any(), any(), any()))
                .thenReturn("{\"runs\":[{\"jobInstanceId\":\"abc-123\"}]}")
                .thenReturn("{\"resultStatus\":\"FAIL\"}");

        AgentResponse response = agentService.execute(request);

        assertEquals("Job abc-123 failed", response.answer());
        assertEquals(2, response.totalToolCalls());
        assertEquals(2, response.steps().size());
    }

    @Test
    void execute_loopDetection_sameToolCallTwice() {
        AgentRequest request = new AgentRequest(
                "Search logs", null,
                List.of(buildAgentTool("search", "Search", "http://api/search", "GET")),
                5, 0.2, 1024, false);

        String sameArgs = "{\"q\":\"error\"}";
        ChatResponse toolCall = ChatResponse.withToolCalls(
                List.of(ToolCall.function("c1", "search", sameArgs)));
        ChatResponse toolCallDuplicate = ChatResponse.withToolCalls(
                List.of(ToolCall.function("c2", "search", sameArgs)));
        ChatResponse finalAnswer = ChatResponse.text("Done");

        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(toolCall)
                .thenReturn(toolCallDuplicate)
                .thenReturn(finalAnswer);
        when(toolExecutionService.executeTool(any(), any(), any()))
                .thenReturn("results");

        AgentResponse response = agentService.execute(request);

        // Only 1 actual tool execution (second was loop-detected)
        assertEquals(1, response.totalToolCalls());
        verify(toolExecutionService, times(1)).executeTool(any(), any(), any());
    }

    @Test
    void execute_maxStepsReached() {
        AgentRequest request = new AgentRequest(
                "Loop forever", null,
                List.of(buildAgentTool("tool_a", "A", "http://api/a", "GET")),
                2, 0.2, 1024, false);

        // Always returns tool calls — never final answer
        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.withToolCalls(
                        List.of(ToolCall.function("c1", "tool_a", "{\"x\":1}"))))
                .thenReturn(ChatResponse.withToolCalls(
                        List.of(ToolCall.function("c2", "tool_a", "{\"x\":2}"))));
        when(toolExecutionService.executeTool(any(), any(), any()))
                .thenReturn("ok");

        AgentResponse response = agentService.execute(request);

        assertTrue(response.answer().contains("maximum steps"));
        assertEquals(2, response.totalToolCalls());
    }

    @Test
    void execute_noEndpoint_returnsError() {
        AgentRequest.AgentTool toolNoEndpoint = new AgentRequest.AgentTool(
                "broken", "Broken tool", null, null);

        AgentRequest request = new AgentRequest(
                "Use broken tool", null, List.of(toolNoEndpoint),
                5, 0.2, 1024, false);

        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.withToolCalls(
                        List.of(ToolCall.function("c1", "broken", "{}"))))
                .thenReturn(ChatResponse.text("Error occurred"));

        AgentResponse response = agentService.execute(request);

        assertEquals(1, response.totalToolCalls());
        assertTrue(response.steps().get(0).result().contains("ERROR"));
    }

    @Test
    void execute_embeddedToolCall_detected() {
        AgentRequest request = new AgentRequest(
                "Search logs", null,
                List.of(buildAgentTool("search_logs", "Search", "http://api/search", "GET")),
                5, 0.2, 1024, false);

        // LLM returns text with embedded tool call JSON
        String embeddedText = "I will search now: {\"name\": \"search_logs\", \"parameters\": {\"q\": \"error\"}}";
        ChatResponse embeddedResponse = ChatResponse.text(embeddedText);
        ChatResponse finalAnswer = ChatResponse.text("Found results");

        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(embeddedResponse)
                .thenReturn(finalAnswer);
        when(toolExecutionService.executeTool(any(), any(), any()))
                .thenReturn("results");

        AgentResponse response = agentService.execute(request);

        assertEquals("Found results", response.answer());
        assertEquals(1, response.totalToolCalls());
        assertEquals("search_logs", response.steps().get(0).tool());
    }

    @Test
    void execute_usesCustomSystemPrompt() {
        AgentRequest request = new AgentRequest(
                "Hello", "Custom prompt",
                List.of(buildAgentTool("t", "T", "http://api", "GET")),
                5, 0.2, 1024, false);

        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.text("Hi"));

        agentService.execute(request);

        var captor = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        verify(llamaCppProxy).chat(captor.capture());
        assertEquals("Custom prompt", captor.getValue().messages().get(0).content());
    }

    private AgentRequest.AgentTool buildAgentTool(String name, String desc, String url, String method) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "object");
        params.putObject("properties");
        return new AgentRequest.AgentTool(name, desc, params,
                new AgentRequest.ToolEndpoint(url, method));
    }
}
