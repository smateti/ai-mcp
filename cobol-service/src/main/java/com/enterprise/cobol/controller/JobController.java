package com.enterprise.cobol.controller;

import com.enterprise.cobol.entity.AnalysisJob;
import com.enterprise.cobol.repository.jpa.AnalysisJobRepository;
import com.enterprise.cobol.service.BatchProxyService;
import com.enterprise.cobol.service.RunCleanupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final AnalysisJobRepository jobRepo;
    private final BatchProxyService batchProxy;
    private final RunCleanupService runCleanupService;
    private final ObjectMapper objectMapper;

    // Called by Web UI to trigger a batch run (proxied through service)
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runJob(@RequestBody Map<String, String> request) {
        String folderPath = request.get("folderPath");
        String copybookPath = request.getOrDefault("copybookPath", "");

        if (folderPath == null || folderPath.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "folderPath is required"));
        }

        // Create job record first
        AnalysisJob job = AnalysisJob.builder()
                .folderPath(folderPath)
                .copybookPath(copybookPath)
                .status("PENDING")
                .progress(0)
                .startedAt(LocalDateTime.now())
                .build();
        job = jobRepo.save(job);

        try {
            // Forward to batch service with our job ID
            String batchResponse = batchProxy.triggerBatch(folderPath, copybookPath, job.getId());
            job.setStatus("RUNNING");
            job.setCurrentStep("STARTING");

            // Extract batchRunId and batchJobId from response
            try {
                var node = objectMapper.readTree(batchResponse);
                if (node.has("batchRunId")) {
                    job.setBatchRunId(node.get("batchRunId").asText());
                }
                if (node.has("jobId")) {
                    job.setBatchJobId(node.get("jobId").asLong());
                }
            } catch (Exception e) {
                log.warn("Could not parse batch response: {}", e.getMessage());
            }

            jobRepo.save(job);

            return ResponseEntity.ok(Map.of(
                    "id", job.getId(),
                    "status", job.getStatus(),
                    "batchRunId", job.getBatchRunId() != null ? job.getBatchRunId() : ""
            ));
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage("Failed to trigger batch: " + e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            jobRepo.save(job);

            return ResponseEntity.internalServerError().body(Map.of(
                    "id", job.getId(),
                    "error", e.getMessage()
            ));
        }
    }

    // Called by Batch to create a job record
    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody Map<String, Object> request) {
        AnalysisJob job = AnalysisJob.builder()
                .folderPath((String) request.getOrDefault("folderPath", ""))
                .copybookPath((String) request.getOrDefault("copybookPath", ""))
                .batchRunId((String) request.getOrDefault("batchRunId", ""))
                .status((String) request.getOrDefault("status", "RUNNING"))
                .progress(0)
                .startedAt(LocalDateTime.now())
                .build();
        job = jobRepo.save(job);

        return ResponseEntity.ok(Map.of("id", job.getId()));
    }

    // Called by Batch to update job status
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateJob(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        return jobRepo.findById(id).map(job -> {
            // Don't allow batch to overwrite terminal statuses
            String currentStatus = job.getStatus();
            if ("STOPPED".equals(currentStatus) || "COMPLETED".equals(currentStatus) || "FAILED".equals(currentStatus)) {
                log.debug("Ignoring update for job {} in terminal status {}", id, currentStatus);
                return ResponseEntity.ok().<Void>build();
            }

            if (request.containsKey("status")) job.setStatus((String) request.get("status"));
            if (request.containsKey("currentStep")) job.setCurrentStep((String) request.get("currentStep"));
            if (request.containsKey("progress")) job.setProgress((Integer) request.get("progress"));
            if (request.containsKey("programCount")) job.setProgramCount((Integer) request.get("programCount"));
            if (request.containsKey("errorMessage")) job.setErrorMessage((String) request.get("errorMessage"));

            if ("COMPLETED".equals(job.getStatus()) || "FAILED".equals(job.getStatus())) {
                job.setCompletedAt(LocalDateTime.now());
            }

            jobRepo.save(job);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    // Called by Web to list jobs
    @GetMapping
    public ResponseEntity<?> listJobs() {
        return ResponseEntity.ok(jobRepo.findAllByOrderByStartedAtDesc());
    }

    // Called by Web to get job detail
    @GetMapping("/{id}")
    public ResponseEntity<?> getJob(@PathVariable Long id) {
        return jobRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Called by Web to stop a running job
    @PostMapping("/{id}/stop")
    public ResponseEntity<?> stopJob(@PathVariable Long id) {
        var jobOpt = jobRepo.findById(id);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalysisJob job = jobOpt.get();
        if (!"RUNNING".equals(job.getStatus()) && !"PENDING".equals(job.getStatus())) {
            return ResponseEntity.ok(Map.of("stopped", false, "reason", "Job is not running"));
        }
        if (job.getBatchJobId() == null) {
            return ResponseEntity.ok(Map.of("stopped", false, "reason", "No batch job ID available"));
        }
        try {
            batchProxy.stopBatch(job.getBatchJobId());
            job.setStatus("STOPPED");
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage("Stopped by user");
            jobRepo.save(job);
            log.info("Stopped job {} (batchJobId: {})", id, job.getBatchJobId());
            return ResponseEntity.ok(Map.of("stopped", true, "id", id));
        } catch (Exception e) {
            log.error("Failed to stop job {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // Delete a run and all its data (ES + Qdrant)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteJob(@PathVariable Long id) {
        return jobRepo.findById(id).map(job -> {
            // Stop the batch if it's still running
            if (("RUNNING".equals(job.getStatus()) || "PENDING".equals(job.getStatus()))
                    && job.getBatchJobId() != null) {
                try {
                    batchProxy.stopBatch(job.getBatchJobId());
                    log.info("Stopped running batch job {} before delete", job.getBatchJobId());
                } catch (Exception e) {
                    log.warn("Could not stop batch job {} (may have already finished): {}",
                            job.getBatchJobId(), e.getMessage());
                }
            }

            // Delete all artifacts from ES and Qdrant
            String batchRunId = job.getBatchRunId();
            if (batchRunId != null && !batchRunId.isEmpty()) {
                runCleanupService.deleteRunData(batchRunId);
            }

            // Delete H2 record
            jobRepo.delete(job);
            log.info("Deleted job {} and all artifacts (batchRunId: {})", id, batchRunId);
            return ResponseEntity.ok(Map.of("deleted", true, "id", id));
        }).orElse(ResponseEntity.notFound().build());
    }
}
