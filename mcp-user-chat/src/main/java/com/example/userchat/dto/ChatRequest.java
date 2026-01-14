package com.example.userchat.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String sessionId;
    private String categoryId;
    private String message;
}
