package com.example.gitlabmigration.client;

import com.example.gitlabmigration.model.BulkImportResult;
import com.example.gitlabmigration.model.ProjectInfo;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;

/**
 * Handles GitLab bulk import (direct transfer) API interactions:
 * POST /api/v4/bulk_imports, status polling, and entity result collection.
 */
@Component
public class BulkImportClient {

    private static final Logger log = LoggerFactory.getLogger(BulkImportClient.class);
    private static final Set<String> TERMINAL_IMPORT_STATES = Set.of("finished", "failed", "timeout");

    /**
     * Build bulk import entity payloads from a batch of project records.
     * Each project is recreated at the same full path on the destination.
     */
    public List<Map<String, Object>> buildEntities(List<ProjectInfo> batch) {
        List<Map<String, Object>> entities = new ArrayList<>();
        for (ProjectInfo rec : batch) {
            String full = rec.pathWithNamespace();
            int lastSlash = full.lastIndexOf('/');
            String namespace = lastSlash > 0 ? full.substring(0, lastSlash) : "";
            String slug = lastSlash > 0 ? full.substring(lastSlash + 1) : full;

            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("source_full_path", full);
            entity.put("destination_slug", slug);
            entity.put("destination_namespace", namespace);

            if ("group".equals(rec.entityType())) {
                entity.put("source_type", "group_entity");
                entity.put("migrate_projects", true);
            } else {
                entity.put("source_type", "project_entity");
            }
            entities.add(entity);
        }
        return entities;
    }

    /**
     * Submit one batch of projects as a single bulk import.
     * Returns the bulk import id, or null if submission failed.
     */
    public Integer submitBatch(RestClient dstClient, String destUrl,
                                String sourceUrl, String sourceToken,
                                List<ProjectInfo> batch) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("configuration", Map.of("url", sourceUrl, "access_token", sourceToken));
        payload.put("entities", buildEntities(batch));

        try {
            ResponseEntity<JsonNode> resp = dstClient.post()
                    .uri(destUrl + "/api/v4/bulk_imports")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(JsonNode.class);

            JsonNode body = resp.getBody();
            if (body != null && body.has("id")) {
                int bulkId = body.get("id").asInt();
                log.info("Submitted bulk import id={} with {} projects", bulkId, batch.size());
                return bulkId;
            }
            log.error("Batch submission returned unexpected body: {}", body);
            return null;
        } catch (RestClientResponseException e) {
            log.error("Batch submission failed: HTTP {} {}",
                    e.getStatusCode().value(), truncate(e.getResponseBodyAsString(), 500));
            return null;
        }
    }

    /**
     * Poll a bulk import until terminal state, then fetch per-entity outcomes.
     */
    public Map<String, BulkImportResult> waitForBatch(RestClient dstClient, String destUrl,
                                                        int bulkId, int pollIntervalSec,
                                                        int timeoutSec) {
        long deadline = System.currentTimeMillis() + (timeoutSec * 1000L);
        String status = "created";

        while (System.currentTimeMillis() < deadline) {
            JsonNode resp = dstClient.get()
                    .uri(destUrl + "/api/v4/bulk_imports/" + bulkId)
                    .retrieve()
                    .body(JsonNode.class);
            if (resp != null) {
                status = resp.get("status").asText();
            }
            if (TERMINAL_IMPORT_STATES.contains(status)) break;

            log.info("  bulk_import {} status={} ... waiting {}s", bulkId, status, pollIntervalSec);
            sleep(pollIntervalSec * 1000L);
        }

        if (!TERMINAL_IMPORT_STATES.contains(status)) {
            log.warn("  bulk_import {} did not finish within {}s (last={})",
                    bulkId, timeoutSec, status);
        }

        // Per-entity results
        JsonNode entities = dstClient.get()
                .uri(destUrl + "/api/v4/bulk_imports/" + bulkId + "/entities?per_page=100")
                .retrieve()
                .body(JsonNode.class);

        Map<String, BulkImportResult> results = new LinkedHashMap<>();
        if (entities != null && entities.isArray()) {
            for (JsonNode ent : entities) {
                String path = ent.get("source_full_path").asText();
                String entStatus = ent.get("status").asText();
                List<String> failures = new ArrayList<>();
                if (ent.has("failures") && ent.get("failures").isArray()) {
                    for (JsonNode f : ent.get("failures")) {
                        String msg = f.path("pipeline_class").asText("") + ": "
                                + truncate(f.path("exception_message").asText(""), 200);
                        failures.add(msg);
                    }
                }
                results.put(path, new BulkImportResult(path, entStatus, failures));
            }
        }
        return results;
    }

    /**
     * Submit a single-project reimport and poll until completion.
     * Used by the resync phase for --reimport mode.
     */
    public void submitAndWaitSingle(RestClient dstClient, String destUrl,
                                     String sourceUrl, String sourceToken,
                                     String path, int pollIntervalSec,
                                     int timeoutSec) {
        int lastSlash = path.lastIndexOf('/');
        String namespace = lastSlash > 0 ? path.substring(0, lastSlash) : "";
        String slug = lastSlash > 0 ? path.substring(lastSlash + 1) : path;

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("source_type", "project_entity");
        entity.put("source_full_path", path);
        entity.put("destination_slug", slug);
        entity.put("destination_namespace", namespace);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("configuration", Map.of("url", sourceUrl, "access_token", sourceToken));
        payload.put("entities", List.of(entity));

        ResponseEntity<JsonNode> resp;
        try {
            resp = dstClient.post()
                    .uri(destUrl + "/api/v4/bulk_imports")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new RuntimeException("bulk import submit failed: HTTP " + e.getStatusCode().value()
                    + " " + truncate(e.getResponseBodyAsString(), 200));
        }

        JsonNode body = resp.getBody();
        if (body == null || !body.has("id")) {
            throw new RuntimeException("bulk import submit returned unexpected body");
        }
        int bulkId = body.get("id").asInt();

        long deadline = System.currentTimeMillis() + (timeoutSec * 1000L);
        while (System.currentTimeMillis() < deadline) {
            JsonNode statusResp = dstClient.get()
                    .uri(destUrl + "/api/v4/bulk_imports/" + bulkId)
                    .retrieve()
                    .body(JsonNode.class);
            String st = statusResp != null ? statusResp.get("status").asText() : "unknown";
            if ("finished".equals(st)) return;
            if ("failed".equals(st) || "timeout".equals(st)) {
                throw new RuntimeException("re-import ended with status '" + st + "'");
            }
            sleep(pollIntervalSec * 1000L);
        }
        throw new RuntimeException("re-import polling timed out");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Polling interrupted", e);
        }
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
