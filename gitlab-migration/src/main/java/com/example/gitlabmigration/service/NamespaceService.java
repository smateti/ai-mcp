package com.example.gitlabmigration.service;

import com.example.gitlabmigration.util.PathEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ensures destination namespace group chains exist, creating missing levels.
 * The bulk import API requires destination_namespace to be an existing group.
 */
@Service
public class NamespaceService {

    private static final Logger log = LoggerFactory.getLogger(NamespaceService.class);

    /**
     * Make sure a destination group chain exists, creating missing levels.
     * Walks the chain top-down (g1, then g1/sg1, then g1/sg1/sg2, ...).
     *
     * @param dstClient  Destination API session
     * @param destUrl    Destination base URL
     * @param namespace  Full group path, e.g. "g1/sg1/sg2" (empty = nothing to ensure)
     * @param cache      Shared path->group_id cache across calls
     * @return true if namespace exists (or was created), false if creation failed
     */
    public boolean ensureNamespace(RestClient dstClient, String destUrl,
                                    String namespace, Map<String, Integer> cache) {
        if (namespace == null || namespace.isBlank()) return true;

        Integer parentId = null;
        String current = "";

        for (String segment : namespace.split("/")) {
            current = current.isEmpty() ? segment : current + "/" + segment;

            if (cache.containsKey(current)) {
                parentId = cache.get(current);
                continue;
            }

            // Check if group exists
            try {
                ResponseEntity<JsonNode> resp = dstClient.get()
                        .uri(destUrl + "/api/v4/groups/" + PathEncoder.encode(current)
                                + "?with_projects=false")
                        .retrieve()
                        .toEntity(JsonNode.class);
                JsonNode body = resp.getBody();
                if (body != null && body.has("id")) {
                    parentId = body.get("id").asInt();
                    cache.put(current, parentId);
                    continue;
                }
            } catch (HttpClientErrorException.NotFound ignored) {
                // Group doesn't exist, create it below
            } catch (RestClientResponseException e) {
                log.error("Namespace check failed for '{}': HTTP {}", current, e.getStatusCode().value());
                return false;
            }

            // Create the group
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", segment);
            payload.put("path", segment);
            payload.put("visibility", "private");
            if (parentId != null) {
                payload.put("parent_id", parentId);
            }

            try {
                ResponseEntity<JsonNode> createResp = dstClient.post()
                        .uri(destUrl + "/api/v4/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .toEntity(JsonNode.class);
                JsonNode body = createResp.getBody();
                if (body != null && body.has("id")) {
                    parentId = body.get("id").asInt();
                    cache.put(current, parentId);
                    log.info("  created missing destination group: {} (id={})", current, parentId);
                } else {
                    log.error("Could not create group '{}': unexpected response", current);
                    return false;
                }
            } catch (RestClientResponseException e) {
                log.error("Could not create group '{}': HTTP {} {}",
                        current, e.getStatusCode().value(),
                        truncate(e.getResponseBodyAsString(), 300));
                return false;
            }
        }
        return true;
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
