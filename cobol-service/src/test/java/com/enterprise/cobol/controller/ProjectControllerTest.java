package com.enterprise.cobol.controller;

import com.enterprise.cobol.entity.AnalysisJob;
import com.enterprise.cobol.entity.Project;
import com.enterprise.cobol.repository.jpa.AnalysisJobRepository;
import com.enterprise.cobol.repository.jpa.ProjectRepository;
import com.enterprise.cobol.service.BatchProxyService;
import com.enterprise.cobol.service.RunCleanupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectControllerTest {

    private MockMvc mockMvc;
    private ProjectRepository projectRepo;
    private AnalysisJobRepository jobRepo;
    private BatchProxyService batchProxy;
    private RunCleanupService runCleanupService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        projectRepo = mock(ProjectRepository.class);
        jobRepo = mock(AnalysisJobRepository.class);
        batchProxy = mock(BatchProxyService.class);
        runCleanupService = mock(RunCleanupService.class);
        objectMapper = new ObjectMapper();

        ProjectController controller = new ProjectController(
                projectRepo, jobRepo, batchProxy, runCleanupService, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testCreateProject() throws Exception {
        Project saved = Project.builder()
                .id(1L).name("CardDemo").basePath("/path/to/carddemo")
                .programsSubPath("cbl").copybooksSubPath("cpy")
                .createdAt(LocalDateTime.now()).build();
        when(projectRepo.existsByName("CardDemo")).thenReturn(false);
        when(projectRepo.save(any())).thenReturn(saved);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "CardDemo",
                                "basePath", "/path/to/carddemo",
                                "programsSubPath", "cbl",
                                "copybooksSubPath", "cpy"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("CardDemo"));
    }

    @Test
    void testCreateProjectDuplicateName() throws Exception {
        when(projectRepo.existsByName("CardDemo")).thenReturn(true);

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "CardDemo",
                                "basePath", "/path"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Project name already exists"));
    }

    @Test
    void testCreateProjectMissingName() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "basePath", "/path"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name is required"));
    }

    @Test
    void testListProjects() throws Exception {
        Project p1 = Project.builder()
                .id(1L).name("CardDemo").basePath("/path1")
                .programsSubPath("cbl").copybooksSubPath("cpy")
                .createdAt(LocalDateTime.now()).build();
        when(projectRepo.findAll()).thenReturn(List.of(p1));
        when(jobRepo.findByProjectIdOrderByStartedAtDesc(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("CardDemo"))
                .andExpect(jsonPath("$[0].runCount").value(0));
    }

    @Test
    void testListProjectsWithRuns() throws Exception {
        Project p1 = Project.builder()
                .id(1L).name("CardDemo").basePath("/path1")
                .programsSubPath("cbl").copybooksSubPath("cpy")
                .createdAt(LocalDateTime.now()).build();
        AnalysisJob run = AnalysisJob.builder()
                .id(10L).projectId(1L).status("COMPLETED").folderPath("/path")
                .startedAt(LocalDateTime.now()).build();
        when(projectRepo.findAll()).thenReturn(List.of(p1));
        when(jobRepo.findByProjectIdOrderByStartedAtDesc(1L)).thenReturn(List.of(run));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runCount").value(1))
                .andExpect(jsonPath("$[0].latestRunStatus").value("COMPLETED"));
    }

    @Test
    void testGetProject() throws Exception {
        Project p = Project.builder()
                .id(1L).name("CardDemo").basePath("/path")
                .programsSubPath("cbl").copybooksSubPath("cpy")
                .createdAt(LocalDateTime.now()).build();
        when(projectRepo.findById(1L)).thenReturn(Optional.of(p));

        mockMvc.perform(get("/api/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("CardDemo"));
    }

    @Test
    void testGetProjectNotFound() throws Exception {
        when(projectRepo.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/projects/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateProject() throws Exception {
        Project p = Project.builder()
                .id(1L).name("CardDemo").description("Old desc").basePath("/path")
                .programsSubPath("cbl").copybooksSubPath("cpy")
                .createdAt(LocalDateTime.now()).build();
        when(projectRepo.findById(1L)).thenReturn(Optional.of(p));
        when(projectRepo.save(any())).thenReturn(p);

        mockMvc.perform(put("/api/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "Updated desc"))))
                .andExpect(status().isOk());

        verify(projectRepo).save(any());
    }

    @Test
    void testDeleteProjectCascadesRunCleanup() throws Exception {
        Project p = Project.builder()
                .id(1L).name("CardDemo").basePath("/path")
                .programsSubPath("cbl").copybooksSubPath("cpy")
                .createdAt(LocalDateTime.now()).build();
        AnalysisJob run1 = AnalysisJob.builder()
                .id(10L).projectId(1L).batchRunId("run-aaa").status("COMPLETED")
                .folderPath("/path").startedAt(LocalDateTime.now()).build();
        AnalysisJob run2 = AnalysisJob.builder()
                .id(11L).projectId(1L).batchRunId("run-bbb").status("COMPLETED")
                .folderPath("/path").startedAt(LocalDateTime.now()).build();

        when(projectRepo.findById(1L)).thenReturn(Optional.of(p));
        when(jobRepo.findByProjectId(1L)).thenReturn(List.of(run1, run2));

        mockMvc.perform(delete("/api/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.runsDeleted").value(2));

        // Verify ES/Qdrant cleanup called for each run
        verify(runCleanupService).deleteRunData("run-aaa");
        verify(runCleanupService).deleteRunData("run-bbb");
        verify(jobRepo).delete(run1);
        verify(jobRepo).delete(run2);
        verify(projectRepo).delete(p);
    }

    @Test
    void testStartRun() throws Exception {
        Project p = Project.builder()
                .id(1L).name("CardDemo").basePath("/app/carddemo")
                .programsSubPath("cbl").copybooksSubPath("cpy")
                .createdAt(LocalDateTime.now()).build();
        when(projectRepo.findById(1L)).thenReturn(Optional.of(p));

        AnalysisJob savedJob = AnalysisJob.builder()
                .id(10L).projectId(1L).status("PENDING").folderPath("/app/carddemo/cbl")
                .startedAt(LocalDateTime.now()).build();
        when(jobRepo.save(any())).thenReturn(savedJob);

        when(batchProxy.triggerBatch(any(), any(), any(), any()))
                .thenReturn("{\"batchRunId\":\"run-xyz\",\"jobId\":1}");

        mockMvc.perform(post("/api/projects/1/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        verify(batchProxy).triggerBatch(
                eq("/app/carddemo/cbl"), eq("/app/carddemo/cpy"), eq(10L), eq(1L));
    }

    @Test
    void testListRuns() throws Exception {
        when(projectRepo.existsById(1L)).thenReturn(true);
        AnalysisJob run = AnalysisJob.builder()
                .id(10L).projectId(1L).status("COMPLETED").folderPath("/path")
                .startedAt(LocalDateTime.now()).build();
        when(jobRepo.findByProjectIdOrderByStartedAtDesc(1L)).thenReturn(List.of(run));

        mockMvc.perform(get("/api/projects/1/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void testListRunsProjectNotFound() throws Exception {
        when(projectRepo.existsById(999L)).thenReturn(false);

        mockMvc.perform(get("/api/projects/999/runs"))
                .andExpect(status().isNotFound());
    }
}
