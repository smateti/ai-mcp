package com.example.userchat.controller;

import com.example.userchat.dto.CategoryDto;
import com.example.userchat.dto.ChatRequest;
import com.example.userchat.dto.ChatResponse;
import com.example.userchat.entity.ChatMessage;
import com.example.userchat.entity.ChatSession;
import com.example.userchat.service.CategoryService;
import com.example.userchat.service.ChatHistoryService;
import com.example.userchat.service.ChatService;
import com.example.userchat.service.FaqCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatController {

    private final CategoryService categoryService;
    private final ChatService chatService;
    private final ChatHistoryService historyService;
    private final FaqCacheService cacheService;

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getCategories() {
        return ResponseEntity.ok(categoryService.getActiveCategories());
    }

    @PostMapping("/session")
    public ResponseEntity<Map<String, String>> createSession(@RequestBody(required = false) Map<String, String> request) {
        if (request == null || request.get("categoryId") == null || request.get("firstQuestion") == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Missing required fields: categoryId, categoryName, firstQuestion"));
        }

        String categoryId = request.get("categoryId");
        String categoryName = request.get("categoryName");
        String firstQuestion = request.get("firstQuestion");

        String sessionId = chatService.createSession(categoryId, categoryName, firstQuestion);
        if (sessionId != null) {
            return ResponseEntity.ok(Map.of("sessionId", sessionId));
        }
        return ResponseEntity.internalServerError()
            .body(Map.of("error", "Failed to create session"));
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        ChatResponse response = chatService.sendMessage(request);
        return ResponseEntity.ok(response);
    }

    // History endpoints
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSession>> getAllSessions() {
        return ResponseEntity.ok(historyService.getAllSessions());
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ChatSession> getSession(@PathVariable String sessionId) {
        return historyService.getSession(sessionId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessage>> getMessages(@PathVariable String sessionId) {
        return ResponseEntity.ok(historyService.getMessages(sessionId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        historyService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions/category/{categoryId}")
    public ResponseEntity<List<ChatSession>> getSessionsByCategory(@PathVariable String categoryId) {
        return ResponseEntity.ok(historyService.getSessionsByCategory(categoryId));
    }

    // Cache management
    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, String>> clearCache() {
        cacheService.clearCache();
        return ResponseEntity.ok(Map.of("message", "Cache cleared successfully"));
    }
}
