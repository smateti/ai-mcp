package com.example.mcp.service;

import com.example.mcp.model.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResourceRegistry {
    private final Map<String, Resource> resources = new HashMap<>();
    private final Map<String, String> resourceContents = new HashMap<>();

    public ResourceRegistry() {
        registerDefaultResources();
    }

    private void registerDefaultResources() {
        registerResource(
            "resource://config/server",
            "Server Configuration",
            "Configuration settings for the MCP server",
            "application/json",
            "{\"name\": \"mcp-spring-boot-server\", \"version\": \"1.0.0\", \"environment\": \"development\"}"
        );

        registerResource(
            "resource://docs/welcome",
            "Welcome Message",
            "Welcome documentation for the MCP server",
            "text/plain",
            "Welcome to the Spring Boot MCP Server! This server implements the Model Context Protocol."
        );
    }

    public void registerResource(String uri, String name, String description,
                                String mimeType, String content) {
        Resource resource = new Resource(uri, name, description, mimeType);
        resources.put(uri, resource);
        resourceContents.put(uri, content);
    }

    public List<Resource> getAllResources() {
        return new ArrayList<>(resources.values());
    }

    public String readResource(String uri) {
        String content = resourceContents.get(uri);
        if (content == null) {
            throw new IllegalArgumentException("Resource not found: " + uri);
        }
        return content;
    }
}
