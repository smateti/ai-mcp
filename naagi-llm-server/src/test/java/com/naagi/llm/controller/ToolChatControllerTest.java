package com.naagi.llm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.llm.model.*;
import com.naagi.llm.service.LlamaCppProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ToolChatControllerTest {

    @Mock
    private LlamaCppProxy llamaCppProxy;

    private ToolChatController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new ToolChatController(llamaCppProxy, objectMapper);
    }

    @Test
    void parseToolRequest_openAiFormat() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                    "messages": [{"role": "user", "content": "weather?"}],
                    "tools": [{
                        "type": "function",
                        "function": {
                            "name": "get_weather",
                            "description": "Get weather",
                            "parameters": {"type": "object"}
                        }
                    }],
                    "temperature": 0.3,
                    "maxTokens": 2048,
                    "toolChoice": "required"
                }""");

        ChatRequest request = controller.parseToolRequest(body);

        assertEquals(1, request.messages().size());
        assertEquals(1, request.tools().size());
        assertEquals("get_weather", request.tools().get(0).function().name());
        assertEquals(0.3, request.temperature(), 0.01);
        assertEquals(2048, request.maxTokens());
        assertEquals("required", request.toolChoice());
    }

    @Test
    void parseToolRequest_flatFormat() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                    "messages": [{"role": "user", "content": "search"}],
                    "tools": [{
                        "name": "search_logs",
                        "description": "Search app logs",
                        "parameters": {"type": "object", "properties": {"q": {"type": "string"}}}
                    }]
                }""");

        ChatRequest request = controller.parseToolRequest(body);

        assertEquals(1, request.tools().size());
        assertEquals("search_logs", request.tools().get(0).function().name());
        assertEquals("Search app logs", request.tools().get(0).function().description());
    }

    @Test
    void parseToolRequest_defaults() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {"messages": [{"role": "user", "content": "hi"}], "tools": []}""");

        ChatRequest request = controller.parseToolRequest(body);

        assertEquals(0.2, request.temperature(), 0.01);
        assertEquals(1024, request.maxTokens());
        assertEquals("auto", request.toolChoice());
    }

    @Test
    void buildToolResponse_textContent() {
        ChatResponse response = ChatResponse.text("Answer");

        Map<String, Object> result = controller.buildToolResponse(response);

        @SuppressWarnings("unchecked")
        var choices = (List<Map<String, Object>>) result.get("choices");
        @SuppressWarnings("unchecked")
        var message = (Map<String, Object>) choices.get(0).get("message");
        assertEquals("Answer", message.get("content"));
        assertFalse(message.containsKey("tool_calls"));
    }

    @Test
    void buildToolResponse_toolCalls() {
        ChatResponse response = ChatResponse.withToolCalls(
                List.of(
                        ToolCall.function("c1", "tool_a", "{\"x\":1}"),
                        ToolCall.function("c2", "tool_b", "{\"y\":2}")
                ));

        Map<String, Object> result = controller.buildToolResponse(response);

        @SuppressWarnings("unchecked")
        var choices = (List<Map<String, Object>>) result.get("choices");
        @SuppressWarnings("unchecked")
        var message = (Map<String, Object>) choices.get(0).get("message");

        @SuppressWarnings("unchecked")
        var toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        assertEquals(2, toolCalls.size());
        assertEquals("c1", toolCalls.get(0).get("id"));
        assertEquals("c2", toolCalls.get(1).get("id"));
    }

    @Test
    void parseToolRequest_multipleTools() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                    "messages": [{"role": "user", "content": "search"}],
                    "tools": [
                        {"name": "tool_a", "description": "A"},
                        {"name": "tool_b", "description": "B"}
                    ]
                }""");

        ChatRequest request = controller.parseToolRequest(body);

        assertEquals(2, request.tools().size());
        assertEquals("tool_a", request.tools().get(0).function().name());
        assertEquals("tool_b", request.tools().get(1).function().name());
    }
}
