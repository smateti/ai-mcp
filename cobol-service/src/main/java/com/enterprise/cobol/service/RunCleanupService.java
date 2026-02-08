package com.enterprise.cobol.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
public class RunCleanupService {

    private final WebClient esWebClient;
    private final WebClient qdrantWebClient;

    @Value("${qdrant.collection}")
    private String qdrantCollection;

    public RunCleanupService(
            @Value("${spring.elasticsearch.uris}") String esUrl,
            @Value("${qdrant.url}") String qdrantUrl) {
        this.esWebClient = WebClient.builder().baseUrl(esUrl).build();
        this.qdrantWebClient = WebClient.builder().baseUrl(qdrantUrl).build();
    }

    public void deleteRunData(String batchRunId) {
        log.info("Deleting all data for batchRunId: {}", batchRunId);

        // Delete from ES indices using delete-by-query
        deleteFromEsIndex("cobol-programs", batchRunId);
        deleteFromEsIndex("cobol-paragraphs", batchRunId);
        deleteFromEsIndex("cobol-dependencies", batchRunId);

        // Delete from Qdrant
        deleteFromQdrant(batchRunId);
    }

    private void deleteFromEsIndex(String index, String batchRunId) {
        try {
            String response = esWebClient.post()
                    .uri("/{index}/_delete_by_query", index)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("query", Map.of("term", Map.of("batchRunId", batchRunId))))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            log.info("  Deleted from ES index {}: {}", index, response);
        } catch (Exception e) {
            log.warn("  Failed to delete from ES index {}: {}", index, e.getMessage());
        }
    }

    private void deleteFromQdrant(String batchRunId) {
        try {
            String response = qdrantWebClient.post()
                    .uri("/collections/{name}/points/delete", qdrantCollection)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("filter",
                            Map.of("must", java.util.List.of(
                                    Map.of("key", "batchRunId",
                                            "match", Map.of("value", batchRunId))
                            ))))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            log.info("  Deleted from Qdrant: {}", response);
        } catch (Exception e) {
            log.warn("  Failed to delete from Qdrant: {}", e.getMessage());
        }
    }
}
