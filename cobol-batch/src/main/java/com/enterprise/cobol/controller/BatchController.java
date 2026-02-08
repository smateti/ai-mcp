package com.enterprise.cobol.controller;

import com.enterprise.cobol.service.ServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class BatchController {

    private final JobLauncher asyncJobLauncher;
    private final Job cobolAnalysisJob;
    private final JobExplorer jobExplorer;
    private final JobOperator jobOperator;
    private final ServiceClient serviceClient;

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runBatch(@RequestBody Map<String, String> request) {
        String folderPath = request.get("folderPath");
        String copybookPath = request.getOrDefault("copybookPath", "");
        String batchRunId = UUID.randomUUID().toString();

        if (folderPath == null || folderPath.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "folderPath is required"));
        }

        try {
            // Use service job ID if provided (when called via service proxy)
            // Otherwise create a new job record
            String serviceJobId = request.getOrDefault("serviceJobId", "");

            if (serviceJobId.isEmpty()) {
                // Direct call to batch - create job in service
                String serviceResponse = serviceClient.createJob(folderPath, copybookPath, batchRunId);
                if (serviceResponse != null) {
                    try {
                        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        var node = mapper.readTree(serviceResponse);
                        if (node.has("id")) {
                            serviceJobId = node.get("id").asText();
                        }
                    } catch (Exception e) {
                        log.warn("Could not parse service job ID: {}", e.getMessage());
                    }
                }
            }

            String projectId = request.getOrDefault("projectId", "");
            String customPrompt = request.getOrDefault("customPrompt", "");

            var paramsBuilder = new JobParametersBuilder()
                    .addString("folderPath", folderPath)
                    .addString("copybookPath", copybookPath)
                    .addString("batchRunId", batchRunId)
                    .addLong("timestamp", System.currentTimeMillis());

            if (!serviceJobId.isEmpty()) {
                paramsBuilder.addString("serviceJobId", serviceJobId);
            }
            if (!projectId.isEmpty()) {
                paramsBuilder.addString("projectId", projectId);
            }
            if (!customPrompt.isEmpty()) {
                paramsBuilder.addString("customPrompt", customPrompt);
            }

            JobExecution execution = asyncJobLauncher.run(cobolAnalysisJob, paramsBuilder.toJobParameters());

            return ResponseEntity.ok(Map.of(
                    "jobId", execution.getJobId(),
                    "batchRunId", batchRunId,
                    "status", execution.getStatus().toString()
            ));
        } catch (Exception e) {
            log.error("Failed to launch batch job: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/stop/{jobId}")
    public ResponseEntity<Map<String, Object>> stopBatch(@PathVariable Long jobId) {
        try {
            JobExecution execution = jobExplorer.getJobExecution(jobId);
            if (execution == null) {
                return ResponseEntity.notFound().build();
            }
            if (!execution.isRunning()) {
                return ResponseEntity.ok(Map.of("stopped", false, "reason", "Job is not running"));
            }
            jobOperator.stop(jobId);
            log.info("Stop requested for job {}", jobId);
            return ResponseEntity.ok(Map.of("stopped", true, "jobId", jobId));
        } catch (Exception e) {
            log.error("Failed to stop job {}: {}", jobId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable Long jobId) {
        JobExecution execution = jobExplorer.getJobExecution(jobId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> status = new java.util.HashMap<>();
        status.put("jobId", execution.getJobId());
        status.put("status", execution.getStatus().toString());
        status.put("startTime", execution.getStartTime());
        status.put("endTime", execution.getEndTime());
        status.put("exitStatus", execution.getExitStatus().getExitCode());

        var steps = execution.getStepExecutions();
        var stepStatuses = steps.stream().map(s -> Map.of(
                "stepName", s.getStepName(),
                "status", s.getStatus().toString(),
                "startTime", s.getStartTime() != null ? s.getStartTime().toString() : "",
                "endTime", s.getEndTime() != null ? s.getEndTime().toString() : ""
        )).toList();
        status.put("steps", stepStatuses);

        return ResponseEntity.ok(status);
    }
}
