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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private LlamaCppProxy llamaCppProxy;

    private ChatController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        controller = new ChatController(llamaCppProxy, objectMapper);
    }

    @Test
    void parseRequest_basicMessages() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                    "messages": [
                        {"role": "system", "content": "You are helpful"},
                        {"role": "user", "content": "Hello"}
                    ],
                    "temperature": 0.5,
                    "max_tokens": 256
                }""");

        ChatRequest request = controller.parseRequest(body);

        assertEquals(2, request.messages().size());
        assertEquals("system", request.messages().get(0).role());
        assertEquals("You are helpful", request.messages().get(0).content());
        assertEquals("user", request.messages().get(1).role());
        assertEquals("Hello", request.messages().get(1).content());
        assertEquals(0.5, request.temperature(), 0.01);
        assertEquals(256, request.maxTokens());
        assertNull(request.tools());
    }

    @Test
    void parseRequest_withTools() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                    "messages": [{"role": "user", "content": "weather?"}],
                    "tools": [{
                        "type": "function",
                        "function": {
                            "name": "get_weather",
                            "description": "Get weather",
                            "parameters": {"type": "object", "properties": {"city": {"type": "string"}}}
                        }
                    }],
                    "tool_choice": "auto"
                }""");

        ChatRequest request = controller.parseRequest(body);

        assertNotNull(request.tools());
        assertEquals(1, request.tools().size());
        assertEquals("get_weather", request.tools().get(0).function().name());
        assertEquals("auto", request.toolChoice());
    }

    @Test
    void parseRequest_withToolCalls() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                    "messages": [
                        {"role": "user", "content": "weather?"},
                        {
                            "role": "assistant",
                            "tool_calls": [{
                                "id": "call_1",
                                "type": "function",
                                "function": {"name": "get_weather", "arguments": "{\\"city\\":\\"NY\\"}"}
                            }]
                        },
                        {"role": "tool", "content": "Sunny", "tool_call_id": "call_1", "name": "get_weather"}
                    ]
                }""");

        ChatRequest request = controller.parseRequest(body);

        assertEquals(3, request.messages().size());

        ChatMessage assistantMsg = request.messages().get(1);
        assertNotNull(assistantMsg.toolCalls());
        assertEquals(1, assistantMsg.toolCalls().size());
        assertEquals("call_1", assistantMsg.toolCalls().get(0).id());
        assertEquals("get_weather", assistantMsg.toolCalls().get(0).function().name());

        ChatMessage toolMsg = request.messages().get(2);
        assertEquals("tool", toolMsg.role());
        assertEquals("call_1", toolMsg.toolCallId());
        assertEquals("get_weather", toolMsg.name());
    }

    @Test
    void parseRequest_defaults() throws Exception {
        JsonNode body = objectMapper.readTree("""
                {"messages": [{"role": "user", "content": "Hi"}]}""");

        ChatRequest request = controller.parseRequest(body);

        assertEquals(0.7, request.temperature(), 0.01);
        assertEquals(512, request.maxTokens());
        assertNull(request.toolChoice());
    }

    @Test
    void buildResponseJson_textContent() {
        ChatResponse response = ChatResponse.text("Hello!");

        Map<String, Object> json = controller.buildResponseJson(response);

        @SuppressWarnings("unchecked")
        var choices = (List<Map<String, Object>>) json.get("choices");
        assertEquals(1, choices.size());

        @SuppressWarnings("unchecked")
        var message = (Map<String, Object>) choices.get(0).get("message");
        assertEquals("assistant", message.get("role"));
        assertEquals("Hello!", message.get("content"));
        assertFalse(message.containsKey("tool_calls"));
        assertEquals("stop", choices.get(0).get("finish_reason"));
    }

    @Test
    void buildResponseJson_toolCalls() {
        ChatResponse response = ChatResponse.withToolCalls(
                List.of(ToolCall.function("c1", "search", "{\"q\":\"test\"}")));

        Map<String, Object> json = controller.buildResponseJson(response);

        @SuppressWarnings("unchecked")
        var choices = (List<Map<String, Object>>) json.get("choices");
        @SuppressWarnings("unchecked")
        var message = (Map<String, Object>) choices.get(0).get("message");
        assertTrue(message.containsKey("tool_calls"));

        @SuppressWarnings("unchecked")
        var toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        assertEquals(1, toolCalls.size());
        assertEquals("c1", toolCalls.get(0).get("id"));
        assertEquals("tool_calls", choices.get(0).get("finish_reason"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatCompletions_nonStreaming_returnsResponse() throws Exception {
        when(llamaCppProxy.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.text("Response text"));

        JsonNode body = objectMapper.readTree("""
                {"messages": [{"role": "user", "content": "Hello"}], "stream": false}""");

        Object result = controller.chatCompletions(body);

        assertNotNull(result);
    }
}
