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
public class ServiceClient {

    private final WebClient webClient;

    public ServiceClient(@Value("${service.url}") String serviceUrl) {
        this.webClient = WebClient.builder().baseUrl(serviceUrl).build();
    }

    public String createJob(String folderPath, String copybookPath, String batchRunId) {
        try {
            Map<String, Object> body = Map.of(
                    "folderPath", folderPath,
                    "copybookPath", copybookPath != null ? copybookPath : "",
                    "batchRunId", batchRunId,
                    "status", "RUNNING"
            );

            String response = webClient.post()
                    .uri("/api/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            log.info("Created job record in service: {}", response);
            return response;
        } catch (Exception e) {
            log.warn("Failed to create job in service (service may be down): {}", e.getMessage());
            return null;
        }
    }

    public void updateJobStatus(Long jobId, String status, String currentStep,
                                 int progress, Integer programCount, String errorMessage) {
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("status", status);
            body.put("currentStep", currentStep);
            body.put("progress", progress);
            if (programCount != null) body.put("programCount", programCount);
            if (errorMessage != null) body.put("errorMessage", errorMessage);

            webClient.put()
                    .uri("/api/jobs/{id}", jobId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            log.debug("Updated job {} status: {} step: {} progress: {}%", jobId, status, currentStep, progress);
        } catch (Exception e) {
            log.warn("Failed to update job status in service: {}", e.getMessage());
        }
    }
}
