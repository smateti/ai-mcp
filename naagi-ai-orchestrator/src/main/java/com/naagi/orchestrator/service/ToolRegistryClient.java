package com.naagi.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.naagi.orchestrator.model.AgentConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ToolRegistryClient {

    private final String toolRegistryUrl;
    private final String categoryAdminUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ToolRegistryClient(
            @Value("${naagi.services.tool-registry.url}") String toolRegistryUrl,
            @Value("${naagi.services.category-admin.url}") String categoryAdminUrl,
            ObjectMapper objectMapper) {
        this.toolRegistryUrl = toolRegistryUrl;
        this.categoryAdminUrl = categoryAdminUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<JsonNode> getAllTools() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(toolRegistryUrl + "/api/tools"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to fetch tools from registry: HTTP {}", response.statusCode());
                return List.of();
            }

            JsonNode toolsArray = objectMapper.readTree(response.body());
            List<JsonNode> tools = new ArrayList<>();

            if (toolsArray.isArray()) {
                for (JsonNode tool : toolsArray) {
                    tools.add(tool);
                }
            }

            log.info("Loaded {} tools from registry", tools.size());
            return tools;
        } catch (Exception e) {
            log.error("Error fetching tools from registry", e);
            return List.of();
        }
    }

    public JsonNode getToolByName(String toolName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(toolRegistryUrl + "/api/tools/by-tool-id/" + toolName))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            }
        } catch (Exception e) {
            log.error("Error fetching tool {} from registry", toolName, e);
        }
        return null;
    }

    /**
     * Get tools for a category with parameter overrides applied.
     * Fetches merged tools from category-admin which includes locked values and enum restrictions.
     */
    public List<JsonNode> getToolsByCategory(String categoryId) {
        // First try to get merged tools from category-admin (includes overrides)
        List<JsonNode> mergedTools = getMergedToolsFromCategoryAdmin(categoryId);
        if (!mergedTools.isEmpty()) {
            return mergedTools;
        }

        // Fallback to tool-registry if category-admin fails
        log.warn("Falling back to tool-registry for category {} (category-admin unavailable)", categoryId);
        return getToolsFromRegistry(categoryId);
    }

    /**
     * Fetch merged tools from category-admin with parameter overrides applied.
     */
    private List<JsonNode> getMergedToolsFromCategoryAdmin(String categoryId) {
        try {
            String url = categoryAdminUrl + "/api/categories/" + categoryId + "/tools/merged";
            log.debug("Fetching merged tools from category-admin: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Category-admin returned HTTP {} for merged tools, categoryId={}",
                        response.statusCode(), categoryId);
                return List.of();
            }

            JsonNode toolsArray = objectMapper.readTree(response.body());
            List<JsonNode> tools = new ArrayList<>();

            if (toolsArray.isArray()) {
                for (JsonNode tool : toolsArray) {
                    tools.add(tool);
                }
            }

            log.info("Loaded {} merged tools for category {} from category-admin", tools.size(), categoryId);
            return tools;
        } catch (Exception e) {
            log.warn("Error fetching merged tools from category-admin for category {}: {}",
                    categoryId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fallback: fetch tools directly from tool-registry (without overrides).
     */
    private List<JsonNode> getToolsFromRegistry(String categoryId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(toolRegistryUrl + "/api/tools?categoryId=" + categoryId))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to fetch tools for category {}: HTTP {}", categoryId, response.statusCode());
                return List.of();
            }

            JsonNode toolsArray = objectMapper.readTree(response.body());
            List<JsonNode> tools = new ArrayList<>();

            if (toolsArray.isArray()) {
                for (JsonNode tool : toolsArray) {
                    tools.add(tool);
                }
            }

            log.info("Loaded {} tools for category {} from registry", tools.size(), categoryId);
            return tools;
        } catch (Exception e) {
            log.error("Error fetching tools for category {} from registry", categoryId, e);
            return List.of();
        }
    }

    /**
     * Look up the active agent definition for a category from tool-registry.
     * Returns Optional.empty() if no agent is registered, or on any error (graceful fallback).
     */
    public Optional<AgentConfig> getAgentForCategory(String categoryId) {
        List<AgentConfig> agents = getAgentsForCategory(categoryId);
        return agents.isEmpty() ? Optional.empty() : Optional.of(agents.get(0));
    }

    /**
     * Fetch ALL active agents for a category from tool-registry.
     * Used by the coordinator to select the best agent when multiple are registered.
     */
    public List<AgentConfig> getAgentsForCategory(String categoryId) {
        try {
            String url = toolRegistryUrl + "/api/agents?categoryId=" + categoryId + "&active=true";
            log.debug("Looking up agents for category {}: {}", categoryId, url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.debug("No agents found for category {} (HTTP {})", categoryId, response.statusCode());
                return List.of();
            }

            JsonNode agentsArray = objectMapper.readTree(response.body());
            if (!agentsArray.isArray() || agentsArray.isEmpty()) {
                log.debug("No active agents found for category {}", categoryId);
                return List.of();
            }

            List<AgentConfig> agents = new ArrayList<>();
            for (JsonNode agentNode : agentsArray) {
                agents.add(parseAgentConfig(agentNode));
            }

            log.info("Found {} active agents for category {}", agents.size(), categoryId);
            return agents;

        } catch (Exception e) {
            log.warn("Error looking up agents for category {}: {}", categoryId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Parse a single agent JSON node into an AgentConfig, including description and skills.
     */
    private AgentConfig parseAgentConfig(JsonNode agentNode) {
        AgentConfig config = new AgentConfig();
        config.setAgentId(agentNode.has("agentId") ? agentNode.get("agentId").asText() : null);
        config.setName(agentNode.has("name") ? agentNode.get("name").asText() : null);
        config.setDescription(agentNode.has("description") && !agentNode.get("description").isNull()
                ? agentNode.get("description").asText() : null);
        config.setRole(agentNode.has("role") ? agentNode.get("role").asText() : null);
        config.setAgentType(agentNode.has("agentType") ? agentNode.get("agentType").asText() : "INTERNAL");
        config.setMaxSteps(agentNode.has("maxSteps") ? agentNode.get("maxSteps").asInt(10) : 10);
        config.setPlanningEnabled(agentNode.has("planningEnabled") && agentNode.get("planningEnabled").asBoolean());
        config.setReflectionEnabled(agentNode.has("reflectionEnabled") && agentNode.get("reflectionEnabled").asBoolean());
        config.setParallelToolCalls(!agentNode.has("parallelToolCalls") || agentNode.get("parallelToolCalls").asBoolean());
        config.setSystemPromptOverride(agentNode.has("systemPromptOverride") && !agentNode.get("systemPromptOverride").isNull()
                ? agentNode.get("systemPromptOverride").asText() : null);
        config.setDocumentAccess(agentNode.has("documentAccess") ? agentNode.get("documentAccess").asText() : "AUTO");

        // Parse tool assignments
        if (agentNode.has("tools") && agentNode.get("tools").isArray()) {
            List<AgentConfig.AgentToolConfig> tools = new ArrayList<>();
            for (JsonNode toolNode : agentNode.get("tools")) {
                AgentConfig.AgentToolConfig toolConfig = new AgentConfig.AgentToolConfig();
                toolConfig.setToolId(toolNode.has("toolId") ? toolNode.get("toolId").asText() : null);
                toolConfig.setCustomDescription(toolNode.has("customDescription") && !toolNode.get("customDescription").isNull()
                        ? toolNode.get("customDescription").asText() : null);
                toolConfig.setRequired(toolNode.has("required") && toolNode.get("required").asBoolean());
                tools.add(toolConfig);
            }
            config.setTools(tools);
        }

        // Parse skills
        if (agentNode.has("skills") && agentNode.get("skills").isArray()) {
            List<AgentConfig.AgentSkillConfig> skills = new ArrayList<>();
            for (JsonNode skillNode : agentNode.get("skills")) {
                AgentConfig.AgentSkillConfig skillConfig = new AgentConfig.AgentSkillConfig();
                skillConfig.setName(skillNode.has("name") ? skillNode.get("name").asText() : null);
                skillConfig.setDescription(skillNode.has("description") && !skillNode.get("description").isNull()
                        ? skillNode.get("description").asText() : null);
                if (skillNode.has("tags") && skillNode.get("tags").isArray()) {
                    List<String> tags = new ArrayList<>();
                    for (JsonNode tag : skillNode.get("tags")) {
                        tags.add(tag.asText());
                    }
                    skillConfig.setTags(tags);
                }
                skills.add(skillConfig);
            }
            config.setSkills(skills);
        }

        return config;
    }

    /**
     * Get a merged tool by name for a specific category (includes overrides).
     */
    public JsonNode getMergedToolByName(String categoryId, String toolName) {
        try {
            String url = categoryAdminUrl + "/api/categories/" + categoryId + "/tools/" + toolName + "/merged";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            }
        } catch (Exception e) {
            log.error("Error fetching merged tool {} for category {}", toolName, categoryId, e);
        }
        return null;
    }
}
