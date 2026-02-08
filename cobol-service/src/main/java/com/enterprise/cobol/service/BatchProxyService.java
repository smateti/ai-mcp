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
public class BatchProxyService {

    private final WebClient webClient;

    public BatchProxyService(@Value("${batch.url}") String batchUrl) {
        this.webClient = WebClient.builder().baseUrl(batchUrl).build();
    }

    public String triggerBatch(String folderPath, String copybookPath, Long serviceJobId) {
        return triggerBatch(folderPath, copybookPath, serviceJobId, null, null);
    }

    public String triggerBatch(String folderPath, String copybookPath, Long serviceJobId, Long projectId) {
        return triggerBatch(folderPath, copybookPath, serviceJobId, projectId, null);
    }

    public String triggerBatch(String folderPath, String copybookPath, Long serviceJobId, Long projectId, String customPrompt) {
        try {
            Map<String, String> body = new java.util.HashMap<>();
            body.put("folderPath", folderPath);
            body.put("copybookPath", copybookPath != null ? copybookPath : "");
            if (serviceJobId != null) {
                body.put("serviceJobId", serviceJobId.toString());
            }
            if (projectId != null) {
                body.put("projectId", projectId.toString());
            }
            if (customPrompt != null && !customPrompt.isBlank()) {
                body.put("customPrompt", customPrompt);
            }

            return webClient.post()
                    .uri("/api/batch/run")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (Exception e) {
            log.error("Failed to trigger batch: {}", e.getMessage());
            throw new RuntimeException("Failed to trigger batch job: " + e.getMessage(), e);
        }
    }

    public String stopBatch(Long jobId) {
        try {
            return webClient.post()
                    .uri("/api/batch/stop/{jobId}", jobId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception e) {
            log.error("Failed to stop batch job {}: {}", jobId, e.getMessage());
            throw new RuntimeException("Failed to stop batch job: " + e.getMessage(), e);
        }
    }

    public String getBatchStatus(Long jobId) {
        try {
            return webClient.get()
                    .uri("/api/batch/status/{jobId}", jobId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception e) {
            log.error("Failed to get batch status: {}", e.getMessage());
            return null;
        }
    }
}
