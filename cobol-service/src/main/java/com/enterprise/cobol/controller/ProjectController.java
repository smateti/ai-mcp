package com.enterprise.cobol.controller;

import com.enterprise.cobol.entity.AnalysisJob;
import com.enterprise.cobol.entity.Project;
import com.enterprise.cobol.repository.jpa.AnalysisJobRepository;
import com.enterprise.cobol.repository.jpa.ProjectRepository;
import com.enterprise.cobol.service.BatchProxyService;
import com.enterprise.cobol.service.RunCleanupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepo;
    private final AnalysisJobRepository jobRepo;
    private final BatchProxyService batchProxy;
    private final RunCleanupService runCleanupService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<?> createProject(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        if (projectRepo.existsByName(name)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Project name already exists"));
        }

        Project project = Project.builder()
                .name(name)
                .description(request.getOrDefault("description", ""))
                .basePath(request.getOrDefault("basePath", ""))
                .programsSubPath(request.getOrDefault("programsSubPath", "programs"))
                .copybooksSubPath(request.getOrDefault("copybooksSubPath", "copybooks"))
                .build();
        project = projectRepo.save(project);
        return ResponseEntity.ok(project);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listProjects() {
        List<Project> projects = projectRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Project p : projects) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.getId());
            item.put("name", p.getName());
            item.put("description", p.getDescription());
            item.put("basePath", p.getBasePath());
            item.put("programsSubPath", p.getProgramsSubPath());
            item.put("copybooksSubPath", p.getCopybooksSubPath());
            item.put("createdAt", p.getCreatedAt());

            List<AnalysisJob> runs = jobRepo.findByProjectIdOrderByStartedAtDesc(p.getId());
            item.put("runCount", runs.size());
            if (!runs.isEmpty()) {
                AnalysisJob latest = runs.get(0);
                item.put("latestRunStatus", latest.getStatus());
                item.put("latestRunId", latest.getId());
            }
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProject(@PathVariable Long id) {
        return projectRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProject(@PathVariable Long id, @RequestBody Map<String, String> request) {
        return projectRepo.findById(id).map(project -> {
            if (request.containsKey("name")) project.setName(request.get("name"));
            if (request.containsKey("description")) project.setDescription(request.get("description"));
            if (request.containsKey("basePath")) project.setBasePath(request.get("basePath"));
            if (request.containsKey("programsSubPath")) project.setProgramsSubPath(request.get("programsSubPath"));
            if (request.containsKey("copybooksSubPath")) project.setCopybooksSubPath(request.get("copybooksSubPath"));
            projectRepo.save(project);
            return ResponseEntity.ok(project);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProject(@PathVariable Long id) {
        return projectRepo.findById(id).map(project -> {
            List<AnalysisJob> runs = jobRepo.findByProjectId(id);
            for (AnalysisJob run : runs) {
                String batchRunId = run.getBatchRunId();
                if (batchRunId != null && !batchRunId.isEmpty()) {
                    runCleanupService.deleteRunData(batchRunId);
                }
                jobRepo.delete(run);
            }
            projectRepo.delete(project);
            log.info("Deleted project {} with {} runs", id, runs.size());
            return ResponseEntity.ok(Map.of("deleted", true, "runsDeleted", runs.size()));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Start a new analysis run for a project
    @PostMapping("/{id}/runs")
    public ResponseEntity<?> startRun(@PathVariable Long id, @RequestBody(required = false) Map<String, String> request) {
        return projectRepo.findById(id).map(project -> {
            String basePath = project.getBasePath();
            String folderPath = basePath + "/" + project.getProgramsSubPath();
            String copybookPath = basePath + "/" + project.getCopybooksSubPath();
            String runLabel = request != null ? request.getOrDefault("runLabel", "") : "";
            String customPrompt = request != null ? request.getOrDefault("customPrompt", "") : "";

            // Create job record
            AnalysisJob job = AnalysisJob.builder()
                    .projectId(project.getId())
                    .runLabel(runLabel)
                    .folderPath(folderPath)
                    .copybookPath(copybookPath)
                    .customPrompt(customPrompt.isBlank() ? null : customPrompt)
                    .status("PENDING")
                    .progress(0)
                    .startedAt(LocalDateTime.now())
                    .build();
            job = jobRepo.save(job);

            try {
                String batchResponse = batchProxy.triggerBatch(folderPath, copybookPath, job.getId(), project.getId(), customPrompt);
                job.setStatus("RUNNING");
                job.setCurrentStep("STARTING");

                try {
                    var node = objectMapper.readTree(batchResponse);
                    if (node.has("batchRunId")) {
                        job.setBatchRunId(node.get("batchRunId").asText());
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
                return ResponseEntity.internalServerError().body(Map.of("id", job.getId(), "error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    // List runs for a project
    @GetMapping("/{id}/runs")
    public ResponseEntity<?> listRuns(@PathVariable Long id) {
        if (!projectRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(jobRepo.findByProjectIdOrderByStartedAtDesc(id));
    }
}
