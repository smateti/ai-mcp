package com.naagi.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naagi.llm.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LlamaCppProxyTest {

    private LlamaCppProxy proxy;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        proxy = new LlamaCppProxy("http://localhost:8000", "llama3.1", 120, objectMapper);
    }

    @Test
    void buildRequestBody_simpleChatRequest() {
        ChatRequest request = ChatRequest.of(
                List.of(ChatMessage.system("You are helpful"), ChatMessage.user("Hello")),
                0.7, 512);

        ObjectNode body = proxy.buildRequestBody(request, false);

        assertEquals("llama3.1", body.get("model").asText());
        assertEquals(0.7, body.get("temperature").asDouble(), 0.01);
        assertEquals(512, body.get("max_tokens").asInt());
        assertFalse(body.get("stream").asBoolean());
        assertTrue(body.has("messages"));
        assertEquals(2, body.get("messages").size());
        assertEquals("system", body.get("messages").get(0).get("role").asText());
        assertEquals("You are helpful", body.get("messages").get(0).get("content").asText());
        assertEquals("user", body.get("messages").get(1).get("role").asText());
        assertEquals("Hello", body.get("messages").get(1).get("content").asText());
        assertFalse(body.has("tools"));
    }

    @Test
    void buildRequestBody_withStream() {
        ChatRequest request = ChatRequest.of(
                List.of(ChatMessage.user("Hi")), 0.5, 256);

        ObjectNode body = proxy.buildRequestBody(request, true);

        assertTrue(body.get("stream").asBoolean());
    }

    @Test
    void buildRequestBody_withTools() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "object");
        ObjectNode props = params.putObject("properties");
        props.putObject("city").put("type", "string");

        ChatRequest request = ChatRequest.withTools(
                List.of(ChatMessage.user("Weather?")),
                List.of(ToolDefinition.function("get_weather", "Get weather", params)),
                "auto", 0.2, 1024);

        ObjectNode body = proxy.buildRequestBody(request, false);

        assertTrue(body.has("tools"));
        assertEquals(1, body.get("tools").size());
        assertEquals("function", body.get("tools").get(0).get("type").asText());
        assertEquals("get_weather", body.get("tools").get(0).get("function").get("name").asText());
        assertEquals("auto", body.get("tool_choice").asText());
    }

    @Test
    void buildRequestBody_withToolCalls() {
        ChatMessage assistantMsg = ChatMessage.assistantWithToolCalls(
                List.of(ToolCall.function("call_1", "get_weather", "{\"city\":\"London\"}")));
        ChatMessage toolMsg = ChatMessage.tool("Sunny 20C", "call_1", "get_weather");

        ChatRequest request = ChatRequest.of(
                List.of(ChatMessage.user("Weather?"), assistantMsg, toolMsg), 0.7, 512);

        ObjectNode body = proxy.buildRequestBody(request, false);

        JsonNode messages = body.get("messages");
        assertEquals(3, messages.size());

        // Assistant with tool_calls
        JsonNode assistantNode = messages.get(1);
        assertEquals("assistant", assistantNode.get("role").asText());
        assertTrue(assistantNode.has("tool_calls"));
        assertEquals("call_1", assistantNode.get("tool_calls").get(0).get("id").asText());
        assertEquals("get_weather", assistantNode.get("tool_calls").get(0).get("function").get("name").asText());

        // Tool response
        JsonNode toolNode = messages.get(2);
        assertEquals("tool", toolNode.get("role").asText());
        assertEquals("call_1", toolNode.get("tool_call_id").asText());
        assertEquals("get_weather", toolNode.get("name").asText());
        assertEquals("Sunny 20C", toolNode.get("content").asText());
    }

    @Test
    void buildRequestBody_noToolChoice_noTools() {
        ChatRequest request = ChatRequest.of(
                List.of(ChatMessage.user("Hello")), 0.7, 512);

        ObjectNode body = proxy.buildRequestBody(request, false);

        assertFalse(body.has("tool_choice"));
    }

    @Test
    void parseResponse_textContent() throws Exception {
        String responseBody = """
                {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello there!"},"finish_reason":"stop"}]}""";

        ChatResponse response = proxy.parseResponse(responseBody);

        assertEquals("Hello there!", response.content());
        assertNull(response.toolCalls());
        assertEquals("stop", response.finishReason());
        assertTrue(response.hasContent());
        assertFalse(response.hasToolCalls());
    }

    @Test
    void parseResponse_withToolCalls() throws Exception {
        String responseBody = """
                {"choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[
                {"id":"call_abc","type":"function","function":{"name":"get_weather","arguments":"{\\"city\\":\\"London\\"}"}}
                ]},"finish_reason":"tool_calls"}]}""";

        ChatResponse response = proxy.parseResponse(responseBody);

        assertNull(response.content());
        assertNotNull(response.toolCalls());
        assertEquals(1, response.toolCalls().size());
        assertEquals("call_abc", response.toolCalls().get(0).id());
        assertEquals("get_weather", response.toolCalls().get(0).function().name());
        assertEquals("{\"city\":\"London\"}", response.toolCalls().get(0).function().arguments());
        assertEquals("tool_calls", response.finishReason());
        assertTrue(response.hasToolCalls());
    }

    @Test
    void parseResponse_emptyContent() throws Exception {
        String responseBody = """
                {"choices":[{"index":0,"message":{"role":"assistant","content":"  "},"finish_reason":"stop"}]}""";

        ChatResponse response = proxy.parseResponse(responseBody);

        assertNull(response.content());
        assertFalse(response.hasContent());
    }

    @Test
    void parseResponse_multipleToolCalls() throws Exception {
        String responseBody = """
                {"choices":[{"index":0,"message":{"role":"assistant","tool_calls":[
                {"id":"c1","type":"function","function":{"name":"tool_a","arguments":"{}"}},
                {"id":"c2","type":"function","function":{"name":"tool_b","arguments":"{}"}}
                ]},"finish_reason":"tool_calls"}]}""";

        ChatResponse response = proxy.parseResponse(responseBody);

        assertEquals(2, response.toolCalls().size());
        assertEquals("tool_a", response.toolCalls().get(0).function().name());
        assertEquals("tool_b", response.toolCalls().get(1).function().name());
    }
}
