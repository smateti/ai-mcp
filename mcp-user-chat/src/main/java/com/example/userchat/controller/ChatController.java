package com.example.userchat.controller;

import com.example.userchat.dto.CategoryDto;
import com.example.userchat.dto.ChatRequest;
import com.example.userchat.dto.ChatResponse;
import com.example.userchat.service.CategoryService;
import com.example.userchat.service.ChatService;
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

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getCategories() {
        return ResponseEntity.ok(categoryService.getActiveCategories());
    }

    @PostMapping("/session")
    public ResponseEntity<Map<String, String>> createSession() {
        String sessionId = chatService.createSession();
        if (sessionId != null) {
            return ResponseEntity.ok(Map.of("sessionId", sessionId));
        }
        return ResponseEntity.internalServerError().build();
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        ChatResponse response = chatService.sendMessage(request);
        return ResponseEntity.ok(response);
    }
}
