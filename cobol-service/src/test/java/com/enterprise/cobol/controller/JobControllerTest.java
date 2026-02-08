package com.enterprise.cobol.controller;

import com.enterprise.cobol.entity.AnalysisJob;
import com.enterprise.cobol.repository.jpa.AnalysisJobRepository;
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

class JobControllerTest {

    private MockMvc mockMvc;
    private AnalysisJobRepository jobRepo;
    private BatchProxyService batchProxy;
    private RunCleanupService runCleanupService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jobRepo = mock(AnalysisJobRepository.class);
        batchProxy = mock(BatchProxyService.class);
        runCleanupService = mock(RunCleanupService.class);
        objectMapper = new ObjectMapper();

        JobController controller = new JobController(jobRepo, batchProxy, runCleanupService, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testRunJob() throws Exception {
        AnalysisJob savedJob = AnalysisJob.builder()
                .id(1L).status("PENDING").folderPath("/path/to/cobol")
                .startedAt(LocalDateTime.now()).build();
        when(jobRepo.save(any())).thenReturn(savedJob);
        when(batchProxy.triggerBatch(any(), any(), any()))
                .thenReturn("{\"batchRunId\":\"run-abc\",\"jobId\":1}");

        mockMvc.perform(post("/api/jobs/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "folderPath", "/path/to/cobol"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(batchProxy).triggerBatch(eq("/path/to/cobol"), eq(""), eq(1L));
    }

    @Test
    void testRunJobMissingFolderPath() throws Exception {
        mockMvc.perform(post("/api/jobs/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "copybookPath", "/some/path"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("folderPath is required"));
    }

    @Test
    void testCreateJob() throws Exception {
        AnalysisJob savedJob = AnalysisJob.builder()
                .id(5L).status("RUNNING").folderPath("/path")
                .startedAt(LocalDateTime.now()).build();
        when(jobRepo.save(any())).thenReturn(savedJob);

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "folderPath", "/path",
                                "batchRunId", "run-123",
                                "status", "RUNNING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void testUpdateJob() throws Exception {
        AnalysisJob job = AnalysisJob.builder()
                .id(1L).status("RUNNING").folderPath("/path")
                .startedAt(LocalDateTime.now()).build();
        when(jobRepo.findById(1L)).thenReturn(Optional.of(job));
        when(jobRepo.save(any())).thenReturn(job);

        mockMvc.perform(put("/api/jobs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "COMPLETED",
                                "currentStep", "DONE",
                                "progress", 100))))
                .andExpect(status().isOk());

        verify(jobRepo).save(any());
    }

    @Test
    void testUpdateJobNotFound() throws Exception {
        when(jobRepo.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/jobs/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "COMPLETED"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetJob() throws Exception {
        AnalysisJob job = AnalysisJob.builder()
                .id(1L).status("COMPLETED").folderPath("/path")
                .batchRunId("run-abc").progress(100)
                .startedAt(LocalDateTime.now()).build();
        when(jobRepo.findById(1L)).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.batchRunId").value("run-abc"));
    }

    @Test
    void testGetJobNotFound() throws Exception {
        when(jobRepo.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/jobs/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testListJobs() throws Exception {
        AnalysisJob job = AnalysisJob.builder()
                .id(1L).status("COMPLETED").folderPath("/path")
                .startedAt(LocalDateTime.now()).build();
        when(jobRepo.findAllByOrderByStartedAtDesc()).thenReturn(List.of(job));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void testDeleteJobCascade() throws Exception {
        AnalysisJob job = AnalysisJob.builder()
                .id(1L).status("COMPLETED").folderPath("/path")
                .batchRunId("run-abc")
                .startedAt(LocalDateTime.now()).build();
        when(jobRepo.findById(1L)).thenReturn(Optional.of(job));

        mockMvc.perform(delete("/api/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        verify(runCleanupService).deleteRunData("run-abc");
        verify(jobRepo).delete(job);
    }

    @Test
    void testDeleteJobNoBatchRunId() throws Exception {
        AnalysisJob job = AnalysisJob.builder()
                .id(1L).status("FAILED").folderPath("/path")
                .batchRunId(null)
                .startedAt(LocalDateTime.now()).build();
        when(jobRepo.findById(1L)).thenReturn(Optional.of(job));

        mockMvc.perform(delete("/api/jobs/1"))
                .andExpect(status().isOk());

        // Should NOT call cleanup when batchRunId is null
        verify(runCleanupService, never()).deleteRunData(any());
        verify(jobRepo).delete(job);
    }

    @Test
    void testUpdateJobIgnoresTerminalStatus() throws Exception {
        // When a job is STOPPED, batch should not overwrite it back to RUNNING
        AnalysisJob job = AnalysisJob.builder()
                .id(1L).status("STOPPED").folderPath("/path")
                .startedAt(LocalDateTime.now()).build();
        when(jobRepo.findById(1L)).thenReturn(Optional.of(job));

        mockMvc.perform(put("/api/jobs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "RUNNING",
                                "progress", 50))))
                .andExpect(status().isOk());

        // Should NOT save — terminal status is protected
        verify(jobRepo, never()).save(any());
    }

    @Test
    void testRunJobBatchFailure() throws Exception {
        AnalysisJob savedJob = AnalysisJob.builder()
                .id(1L).status("PENDING").folderPath("/path")
                .startedAt(LocalDateTime.now()).build();
        when(jobRepo.save(any())).thenReturn(savedJob);
        when(batchProxy.triggerBatch(any(), any(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        mockMvc.perform(post("/api/jobs/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "folderPath", "/path"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }
}
