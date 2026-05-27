package com.example.brdagent.client;

// PROVENANCE: LlamaClient DTOs — agent loop step 2, OpenAI-compatible message format

/**
 * A single message in the chat completions API.
 * Used for both request (system/user) and response (assistant) messages.
 */
public class LlamaMessage {

    private String role;
    private String content;

    public LlamaMessage() {
    }

    public LlamaMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
