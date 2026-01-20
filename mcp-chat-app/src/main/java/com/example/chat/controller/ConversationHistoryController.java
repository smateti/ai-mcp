package com.example.chat.controller;

import com.example.chat.entity.Conversation;
import com.example.chat.entity.Message;
import com.example.chat.service.ChatService;
import com.example.chat.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationHistoryController {

  private final ConversationService conversationService;
  private final ChatService chatService;

  /**
   * Get all conversations
   */
  @GetMapping
  public ResponseEntity<List<Map<String, Object>>> getConversations() {
    List<Conversation> conversations = conversationService.getAllConversations();

    List<Map<String, Object>> response = conversations.stream().map(conv -> {
      Map<String, Object> map = new HashMap<>();
      map.put("id", conv.getId());
      map.put("title", conv.getTitle());
      map.put("createdAt", conv.getCreatedAt().toString());
      map.put("updatedAt", conv.getUpdatedAt().toString());
      map.put("messageCount", conv.getMessages().size());
      return map;
    }).collect(Collectors.toList());

    return ResponseEntity.ok(response);
  }

  /**
   * Create a new conversation
   */
  @PostMapping
  public ResponseEntity<Map<String, Object>> createConversation(@RequestBody Map<String, String> request) {
    String title = request.getOrDefault("title", "New Chat");
    Conversation conversation = conversationService.createConversation(title);

    Map<String, Object> response = new HashMap<>();
    response.put("id", conversation.getId());
    response.put("title", conversation.getTitle());
    response.put("createdAt", conversation.getCreatedAt().toString());
    response.put("updatedAt", conversation.getUpdatedAt().toString());

    return ResponseEntity.ok(response);
  }

  /**
   * Get messages for a conversation
   */
  @GetMapping("/{id}/messages")
  public ResponseEntity<List<Map<String, Object>>> getMessages(@PathVariable Long id) {
    List<Message> messages = conversationService.getMessages(id);

    List<Map<String, Object>> response = messages.stream().map(msg -> {
      Map<String, Object> map = new HashMap<>();
      map.put("id", msg.getId());
      map.put("role", msg.getRole());
      map.put("content", msg.getContent());
      map.put("createdAt", msg.getCreatedAt().toString());
      return map;
    }).collect(Collectors.toList());

    return ResponseEntity.ok(response);
  }

  /**
   * Send a message in a conversation using MCP tools
   */
  @PostMapping("/{id}/messages")
  public ResponseEntity<Map<String, Object>> sendMessage(
      @PathVariable Long id,
      @RequestBody Map<String, String> request) {

    String userMessage = request.get("message");

    // Save user message
    Message userMsg = conversationService.addMessage(id, "user", userMessage);

    // Process with ChatService (includes MCP tool selection and execution)
    // Use conversation ID as session ID to maintain context
    // No category filtering for conversation history (uses all tools)
    com.example.chat.model.ChatMessage chatResponse = chatService.processMessage(
        "conv_" + id, userMessage, null);

    String aiResponse = chatResponse.getContent();

    // Save AI response
    Message aiMsg = conversationService.addMessage(id, "assistant", aiResponse);

    // Return both messages
    Map<String, Object> response = new HashMap<>();
    response.put("userMessage", Map.of(
        "id", userMsg.getId(),
        "role", "user",
        "content", userMessage,
        "createdAt", userMsg.getCreatedAt().toString()
    ));
    response.put("assistantMessage", Map.of(
        "id", aiMsg.getId(),
        "role", "assistant",
        "content", aiResponse,
        "createdAt", aiMsg.getCreatedAt().toString()
    ));

    return ResponseEntity.ok(response);
  }

  /**
   * Delete a conversation
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteConversation(@PathVariable Long id) {
    conversationService.deleteConversation(id);
    return ResponseEntity.ok().build();
  }

  /**
   * Update conversation title
   */
  @PutMapping("/{id}/title")
  public ResponseEntity<Map<String, Object>> updateTitle(
      @PathVariable Long id,
      @RequestBody Map<String, String> request) {

    String newTitle = request.get("title");
    Conversation conversation = conversationService.updateTitle(id, newTitle);

    Map<String, Object> response = new HashMap<>();
    response.put("id", conversation.getId());
    response.put("title", conversation.getTitle());
    response.put("updatedAt", conversation.getUpdatedAt().toString());

    return ResponseEntity.ok(response);
  }
}
