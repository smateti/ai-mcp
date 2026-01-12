package com.example.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP Chat Application - Claude Desktop style web interface
 * Connects to MCP server for tool execution and RAG integration
 */
@SpringBootApplication
public class McpChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpChatApplication.class, args);
    }
}
