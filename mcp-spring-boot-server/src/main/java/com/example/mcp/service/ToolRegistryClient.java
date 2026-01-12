package com.example.mcp.service;

import com.example.mcp.dto.RegistryToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class ToolRegistryClient {

    private final WebClient webClient;
    private final String registryUrl;

    public ToolRegistryClient(
            WebClient.Builder webClientBuilder,
            @Value("${tool.registry.url:http://localhost:8080}") String registryUrl) {
        this.registryUrl = registryUrl;
        this.webClient = webClientBuilder
                .baseUrl(registryUrl)
                .build();
    }

    public List<RegistryToolDefinition> fetchAllTools() {
        try {
            log.info("Fetching tools from registry at: {}", registryUrl);

            List<RegistryToolDefinition> tools = webClient
                    .get()
                    .uri("/api/tools")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<RegistryToolDefinition>>() {})
                    .block();

            log.info("Fetched {} tools from registry", tools != null ? tools.size() : 0);
            return tools != null ? tools : Collections.emptyList();

        } catch (Exception e) {
            log.error("Failed to fetch tools from registry: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public RegistryToolDefinition fetchToolByToolId(String toolId) {
        try {
            log.info("Fetching tool {} from registry", toolId);

            return webClient
                    .get()
                    .uri("/api/tools/by-tool-id/{toolId}", toolId)
                    .retrieve()
                    .bodyToMono(RegistryToolDefinition.class)
                    .block();

        } catch (Exception e) {
            log.error("Failed to fetch tool {}: {}", toolId, e.getMessage());
            return null;
        }
    }
}
