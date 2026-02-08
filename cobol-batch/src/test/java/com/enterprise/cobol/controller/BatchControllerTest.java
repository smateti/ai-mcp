package com.enterprise.cobol.controller;

import com.enterprise.cobol.service.ServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BatchControllerTest {

    private MockMvc mockMvc;
    private JobLauncher jobLauncher;
    private Job cobolAnalysisJob;
    private JobExplorer jobExplorer;
    private JobOperator jobOperator;
    private ServiceClient serviceClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jobLauncher = mock(JobLauncher.class);
        cobolAnalysisJob = mock(Job.class);
        jobExplorer = mock(JobExplorer.class);
        jobOperator = mock(JobOperator.class);
        serviceClient = mock(ServiceClient.class);
        objectMapper = new ObjectMapper();

        BatchController controller = new BatchController(jobLauncher, cobolAnalysisJob, jobExplorer, jobOperator, serviceClient);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testRunBatchSuccess() throws Exception {
        JobExecution mockExecution = mock(JobExecution.class);
        when(mockExecution.getJobId()).thenReturn(1L);
        when(mockExecution.getStatus()).thenReturn(BatchStatus.STARTED);
        when(jobLauncher.run(any(), any())).thenReturn(mockExecution);
        when(serviceClient.createJob(any(), any(), any())).thenReturn("{\"id\": 42}");

        mockMvc.perform(post("/api/batch/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "folderPath", "/path/to/cobol"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(1))
                .andExpect(jsonPath("$.batchRunId").exists())
                .andExpect(jsonPath("$.status").value("STARTED"));

        verify(jobLauncher).run(eq(cobolAnalysisJob), any());
    }

    @Test
    void testRunBatchMissingFolderPath() throws Exception {
        mockMvc.perform(post("/api/batch/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "copybookPath", "/path/to/cpy"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("folderPath is required"));
    }

    @Test
    void testRunBatchWithServiceJobId() throws Exception {
        JobExecution mockExecution = mock(JobExecution.class);
        when(mockExecution.getJobId()).thenReturn(2L);
        when(mockExecution.getStatus()).thenReturn(BatchStatus.STARTED);
        when(jobLauncher.run(any(), any())).thenReturn(mockExecution);

        mockMvc.perform(post("/api/batch/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "folderPath", "/path/to/cobol",
                                "serviceJobId", "99"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(2));

        // Should NOT call serviceClient.createJob when serviceJobId is provided
        verify(serviceClient, never()).createJob(any(), any(), any());
    }

    @Test
    void testRunBatchWithProjectId() throws Exception {
        JobExecution mockExecution = mock(JobExecution.class);
        when(mockExecution.getJobId()).thenReturn(3L);
        when(mockExecution.getStatus()).thenReturn(BatchStatus.STARTED);
        when(jobLauncher.run(any(), any())).thenReturn(mockExecution);

        mockMvc.perform(post("/api/batch/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "folderPath", "/path/to/cobol",
                                "serviceJobId", "10",
                                "projectId", "5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"));

        verify(jobLauncher).run(eq(cobolAnalysisJob), any());
    }

    @Test
    void testRunBatchEmptyFolderPath() throws Exception {
        mockMvc.perform(post("/api/batch/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "folderPath", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("folderPath is required"));
    }

    @Test
    void testGetJobStatusNotFound() throws Exception {
        when(jobExplorer.getJobExecution(999L)).thenReturn(null);

        mockMvc.perform(get("/api/batch/status/999"))
                .andExpect(status().isNotFound());
    }
}
