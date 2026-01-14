package com.example.userchat.dto;

import lombok.Data;

@Data
public class ChatResponse {
    private String id;
    private String role;
    private String content;
    private String timestamp;
    private Object metadata;
}
