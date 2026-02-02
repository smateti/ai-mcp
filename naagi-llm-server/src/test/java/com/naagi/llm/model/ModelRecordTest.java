package com.naagi.llm.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelRecordTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== ChatMessage ====================

    @Test
    void chatMessage_system() {
        ChatMessage msg = ChatMessage.system("You are helpful");
        assertEquals("system", msg.role());
        assertEquals("You are helpful", msg.content());
        assertNull(msg.name());
        assertNull(msg.toolCallId());
        assertNull(msg.toolCalls());
    }

    @Test
    void chatMessage_user() {
        ChatMessage msg = ChatMessage.user("Hello");
        assertEquals("user", msg.role());
        assertEquals("Hello", msg.content());
    }

    @Test
    void chatMessage_assistant() {
        ChatMessage msg = ChatMessage.assistant("Hi there");
        assertEquals("assistant", msg.role());
        assertEquals("Hi there", msg.content());
        assertNull(msg.toolCalls());
    }

    @Test
    void chatMessage_assistantWithToolCalls() {
        List<ToolCall> calls = List.of(ToolCall.function("c1", "tool", "{}"));
        ChatMessage msg = ChatMessage.assistantWithToolCalls(calls);
        assertEquals("assistant", msg.role());
        assertNull(msg.content());
        assertEquals(1, msg.toolCalls().size());
    }

    @Test
    void chatMessage_tool() {
        ChatMessage msg = ChatMessage.tool("result", "call_1", "my_tool");
        assertEquals("tool", msg.role());
        assertEquals("result", msg.content());
        assertEquals("call_1", msg.toolCallId());
        assertEquals("my_tool", msg.name());
    }

    // ==================== ChatRequest ====================

    @Test
    void chatRequest_of() {
        ChatRequest req = ChatRequest.of(
                List.of(ChatMessage.user("Hi")), 0.5, 256);
        assertEquals(1, req.messages().size());
        assertEquals(0.5, req.temperature());
        assertEquals(256, req.maxTokens());
        assertFalse(req.stream());
        assertNull(req.tools());
        assertFalse(req.hasTools());
    }

    @Test
    void chatRequest_withTools() {
        ObjectNode params = objectMapper.createObjectNode();
        List<ToolDefinition> tools = List.of(
                ToolDefinition.function("fn", "desc", params));
        ChatRequest req = ChatRequest.withTools(
                List.of(ChatMessage.user("Hi")), tools, "auto", 0.2, 1024);
        assertTrue(req.hasTools());
        assertEquals("auto", req.toolChoice());
    }

    // ==================== ChatResponse ====================

    @Test
    void chatResponse_text() {
        ChatResponse resp = ChatResponse.text("Hello");
        assertEquals("Hello", resp.content());
        assertTrue(resp.hasContent());
        assertFalse(resp.hasToolCalls());
        assertEquals("stop", resp.finishReason());
    }

    @Test
    void chatResponse_withToolCalls() {
        ChatResponse resp = ChatResponse.withToolCalls(
                List.of(ToolCall.function("c1", "fn", "{}")));
        assertNull(resp.content());
        assertFalse(resp.hasContent());
        assertTrue(resp.hasToolCalls());
        assertEquals("tool_calls", resp.finishReason());
    }

    // ==================== ToolCall ====================

    @Test
    void toolCall_function() {
        ToolCall call = ToolCall.function("call_1", "my_func", "{\"a\":1}");
        assertEquals("call_1", call.id());
        assertEquals("function", call.type());
        assertEquals("my_func", call.function().name());
        assertEquals("{\"a\":1}", call.function().arguments());
    }

    // ==================== ToolDefinition ====================

    @Test
    void toolDefinition_function() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("type", "object");
        ToolDefinition def = ToolDefinition.function("my_tool", "A tool", params);
        assertEquals("function", def.type());
        assertEquals("my_tool", def.function().name());
        assertEquals("A tool", def.function().description());
        assertEquals("object", def.function().parameters().get("type").asText());
    }

    // ==================== ConversationRequest ====================

    @Test
    void conversationRequest_record() {
        ConversationRequest req = new ConversationRequest(
                "conv-1", "Hello", "Be nice", 0.8, 1024, true);
        assertEquals("conv-1", req.conversationId());
        assertEquals("Hello", req.message());
        assertEquals("Be nice", req.systemPrompt());
        assertEquals(0.8, req.temperature());
        assertEquals(1024, req.maxTokens());
        assertTrue(req.stream());
    }

    // ==================== ConversationResponse ====================

    @Test
    void conversationResponse_record() {
        ConversationResponse resp = new ConversationResponse("conv-1", "Hi!", 2);
        assertEquals("conv-1", resp.conversationId());
        assertEquals("Hi!", resp.message());
        assertEquals(2, resp.turnNumber());
    }

    // ==================== AgentRequest ====================

    @Test
    void agentRequest_record() {
        AgentRequest req = new AgentRequest(
                "search", "sys", List.of(), 5, 0.2, 512, true);
        assertEquals("search", req.message());
        assertEquals("sys", req.systemPrompt());
        assertTrue(req.tools().isEmpty());
        assertEquals(5, req.maxSteps());
        assertTrue(req.stream());
    }

    // ==================== AgentResponse ====================

    @Test
    void agentResponse_record() {
        AgentResponse.AgentStepResult step = new AgentResponse.AgentStepResult(
                "tool_a", "{}", "result", 50);
        AgentResponse resp = new AgentResponse("answer", List.of(step), 2, 1);
        assertEquals("answer", resp.answer());
        assertEquals(1, resp.steps().size());
        assertEquals("tool_a", resp.steps().get(0).tool());
        assertEquals(50, resp.steps().get(0).durationMs());
        assertEquals(2, resp.totalSteps());
        assertEquals(1, resp.totalToolCalls());
    }
}
