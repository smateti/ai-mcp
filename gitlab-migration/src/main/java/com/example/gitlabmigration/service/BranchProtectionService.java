package com.example.gitlabmigration.service;

import com.example.gitlabmigration.client.GitlabApiClient;
import com.example.gitlabmigration.model.ProtectedBranchRule;
import com.example.gitlabmigration.util.PathEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lift and restore protected branch rules on the destination project.
 * Required before/after git push --mirror (which is a force-push).
 */
@Service
public class BranchProtectionService {

    private static final Logger log = LoggerFactory.getLogger(BranchProtectionService.class);

    private final GitlabApiClient apiClient;

    public BranchProtectionService(GitlabApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Remove all protected-branch rules from a target project.
     * Returns the rules so they can be restored after the mirror push.
     */
    public List<ProtectedBranchRule> liftProtections(RestClient dstClient, String baseUrl,
                                                       int projectId) {
        List<JsonNode> rules = apiClient.getPaginated(dstClient, baseUrl,
                "/api/v4/projects/" + projectId + "/protected_branches", null);

        List<ProtectedBranchRule> saved = new ArrayList<>();
        for (JsonNode rule : rules) {
            String name = rule.get("name").asText();
            Integer pushLevel = extractAccessLevel(rule, "push_access_levels");
            Integer mergeLevel = extractAccessLevel(rule, "merge_access_levels");
            saved.add(new ProtectedBranchRule(name, pushLevel, mergeLevel));

            try {
                dstClient.delete()
                        .uri(baseUrl + "/api/v4/projects/" + projectId
                                + "/protected_branches/" + PathEncoder.encode(name))
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException e) {
                log.warn("    could not delete protection on '{}': HTTP {}",
                        name, e.getStatusCode().value());
            }
        }
        return saved;
    }

    /**
     * Re-create protected-branch rules removed by liftProtections().
     * Only core attributes (name + push/merge access levels) are restored.
     */
    public void restoreProtections(RestClient dstClient, String baseUrl,
                                    int projectId, List<ProtectedBranchRule> rules) {
        for (ProtectedBranchRule rule : rules) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", rule.name());
            if (rule.pushAccessLevel() != null) {
                payload.put("push_access_level", rule.pushAccessLevel());
            }
            if (rule.mergeAccessLevel() != null) {
                payload.put("merge_access_level", rule.mergeAccessLevel());
            }

            try {
                dstClient.post()
                        .uri(baseUrl + "/api/v4/projects/" + projectId + "/protected_branches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException e) {
                log.warn("    could not restore protection on '{}': HTTP {}",
                        rule.name(), e.getStatusCode().value());
            }
        }
    }

    private Integer extractAccessLevel(JsonNode rule, String field) {
        if (rule.has(field) && rule.get(field).isArray() && !rule.get(field).isEmpty()) {
            return rule.get(field).get(0).path("access_level").asInt();
        }
        return null;
    }
}
