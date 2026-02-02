package com.naagi.categoryadmin.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class AgentRegistryClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AgentRegistryClient(
            @Value("${naagi.services.tool-registry.url}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ── Agent CRUD ──────────────────────────────────────────────

    public List<Map<String, Object>> getAllAgents() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/agents"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                log.error("Failed to get agents: HTTP {}", response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching agents from registry", e);
            return Collections.emptyList();
        }
    }

    public Optional<Map<String, Object>> getAgent(String agentId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/agents/" + agentId))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return Optional.of(objectMapper.readValue(response.body(), new TypeReference<>() {}));
            } else if (response.statusCode() == 404) {
                return Optional.empty();
            } else {
                log.error("Failed to get agent {}: HTTP {}", agentId, response.statusCode());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error fetching agent {} from registry", agentId, e);
            return Optional.empty();
        }
    }

    public Map<String, Object> createAgent(Map<String, Object> agent) {
        try {
            String jsonBody = objectMapper.writeValueAsString(agent);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/agents"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                log.info("Agent created successfully");
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                log.error("Failed to create agent: HTTP {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to create agent: " + extractError(response.body()));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error creating agent in registry", e);
            throw new RuntimeException("Error creating agent", e);
        }
    }

    public Map<String, Object> updateAgent(String agentId, Map<String, Object> updates) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/agents/" + agentId))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(updates)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                log.error("Failed to update agent {}: HTTP {}", agentId, response.statusCode());
                throw new RuntimeException("Failed to update agent: " + extractError(response.body()));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating agent {} in registry", agentId, e);
            throw new RuntimeException("Error updating agent", e);
        }
    }

    public void deleteAgent(String agentId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/agents/" + agentId))
                    .timeout(Duration.ofSeconds(30))
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                log.error("Failed to delete agent {}: HTTP {}", agentId, response.statusCode());
                throw new RuntimeException("Failed to delete agent: " + extractError(response.body()));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting agent {} from registry", agentId, e);
            throw new RuntimeException("Error deleting agent", e);
        }
    }

    // ── Tools ───────────────────────────────────────────────────

    public Map<String, Object> assignTool(String agentId, Map<String, Object> toolData) {
        return postSubResource(agentId, "tools", toolData);
    }

    public void removeTool(String agentId, String toolId) {
        deleteSubResource(agentId, "tools/" + toolId);
    }

    public Map<String, Object> setToolRestrictions(String agentId, String toolId, List<Map<String, Object>> restrictions) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/agents/" + agentId + "/tools/" + toolId + "/restrictions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(restrictions)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                throw new RuntimeException("Failed to set restrictions: " + extractError(response.body()));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error setting tool restrictions", e);
            throw new RuntimeException("Error setting restrictions", e);
        }
    }

    // ── Skills ──────────────────────────────────────────────────

    public Map<String, Object> addSkill(String agentId, Map<String, Object> skillData) {
        return postSubResource(agentId, "skills", skillData);
    }

    public void removeSkill(String agentId, String skillId) {
        deleteSubResource(agentId, "skills/" + skillId);
    }

    // ── Pinned Documents ────────────────────────────────────────

    public Map<String, Object> pinDocument(String agentId, Map<String, Object> docData) {
        return postSubResource(agentId, "pinned-documents", docData);
    }

    public void unpinDocument(String agentId, String docId) {
        deleteSubResource(agentId, "pinned-documents/" + docId);
    }

    // ── Agent Card ──────────────────────────────────────────────

    public Map<String, Object> getAgentCard(String agentId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/agents/" + agentId + "/card"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                throw new RuntimeException("Failed to get agent card: " + extractError(response.body()));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching agent card for {}", agentId, e);
            throw new RuntimeException("Error fetching agent card", e);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────

    private Map<String, Object> postSubResource(String agentId, String subPath, Map<String, Object> body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/agents/" + agentId + "/" + subPath))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                throw new RuntimeException("Failed: " + extractError(response.body()));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error posting to agents/{}/{}", agentId, subPath, e);
            throw new RuntimeException("Error: " + e.getMessage(), e);
        }
    }

    private void deleteSubResource(String agentId, String subPath) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/agents/" + agentId + "/" + subPath))
                    .timeout(Duration.ofSeconds(30))
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                throw new RuntimeException("Failed: " + extractError(response.body()));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting agents/{}/{}", agentId, subPath, e);
            throw new RuntimeException("Error: " + e.getMessage(), e);
        }
    }

    private String extractError(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            if (json.has("error")) {
                return json.get("error").asText();
            }
            return responseBody;
        } catch (Exception e) {
            return responseBody;
        }
    }
}
