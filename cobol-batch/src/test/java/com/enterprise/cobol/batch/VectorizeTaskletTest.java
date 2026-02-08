package com.enterprise.cobol.batch;

import com.enterprise.cobol.document.ParagraphDocument;
import com.enterprise.cobol.document.ProgramDocument;
import com.enterprise.cobol.repository.ParagraphDocumentRepository;
import com.enterprise.cobol.repository.ProgramDocumentRepository;
import com.enterprise.cobol.service.EmbeddingService;
import com.enterprise.cobol.service.QdrantService;
import com.enterprise.cobol.service.QdrantService.QdrantPoint;
import com.enterprise.cobol.service.ServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VectorizeTaskletTest {

    private VectorizeTasklet tasklet;
    private ProgramDocumentRepository programRepo;
    private ParagraphDocumentRepository paragraphRepo;
    private EmbeddingService embeddingService;
    private QdrantService qdrantService;
    private ServiceClient serviceClient;

    @BeforeEach
    void setUp() {
        programRepo = mock(ProgramDocumentRepository.class);
        paragraphRepo = mock(ParagraphDocumentRepository.class);
        embeddingService = mock(EmbeddingService.class);
        qdrantService = mock(QdrantService.class);
        serviceClient = mock(ServiceClient.class);
        tasklet = new VectorizeTasklet(programRepo, paragraphRepo, embeddingService, qdrantService, serviceClient);
    }

    @Test
    void testVectorizeProgramsWithMetadata() throws Exception {
        // Set up execution context
        ExecutionContext jobExecCtx = new ExecutionContext();
        jobExecCtx.putString("programIds", "PROG1");
        jobExecCtx.putLong("serviceJobId", 1L);
        jobExecCtx.putString("batchRunId", "run-123");
        jobExecCtx.putLong("projectId", 5L);

        ChunkContext chunkContext = mockChunkContext(jobExecCtx);

        // Program with a business summary
        ProgramDocument prog = ProgramDocument.builder()
                .programId("PROG1")
                .programName("PROG1")
                .programType("CICS")
                .businessSummary("Processes credit card transactions")
                .build();
        when(programRepo.findById("PROG1")).thenReturn(Optional.of(prog));
        when(paragraphRepo.findByProgramId("PROG1")).thenReturn(List.of());
        when(embeddingService.embedDocument(any())).thenReturn(new float[768]);

        tasklet.execute(mock(StepContribution.class), chunkContext);

        // Verify Qdrant upsert was called with correct payload
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QdrantPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(qdrantService).upsertPoints(captor.capture());

        List<QdrantPoint> points = captor.getValue();
        assertThat(points).hasSize(1);
        assertThat(points.get(0).getPayload()).containsEntry("batchRunId", "run-123");
        assertThat(points.get(0).getPayload()).containsEntry("projectId", "5");
        assertThat(points.get(0).getPayload()).containsEntry("programName", "PROG1");
        assertThat(points.get(0).getPayload()).containsEntry("type", "program");
    }

    @Test
    void testSkipsProgramsWithoutSummary() throws Exception {
        ExecutionContext jobExecCtx = new ExecutionContext();
        jobExecCtx.putString("programIds", "PROG1");

        ChunkContext chunkContext = mockChunkContext(jobExecCtx);

        ProgramDocument prog = ProgramDocument.builder()
                .programId("PROG1")
                .programName("PROG1")
                .businessSummary(null)
                .build();
        when(programRepo.findById("PROG1")).thenReturn(Optional.of(prog));
        when(paragraphRepo.findByProgramId("PROG1")).thenReturn(List.of());

        tasklet.execute(mock(StepContribution.class), chunkContext);

        // No embedding should be called
        verify(embeddingService, never()).embedDocument(any());
        verify(qdrantService, never()).upsertPoints(any());
    }

    @Test
    void testSkipsProgramsWithFailedAnalysis() throws Exception {
        ExecutionContext jobExecCtx = new ExecutionContext();
        jobExecCtx.putString("programIds", "PROG1");

        ChunkContext chunkContext = mockChunkContext(jobExecCtx);

        ProgramDocument prog = ProgramDocument.builder()
                .programId("PROG1")
                .programName("PROG1")
                .businessSummary("Analysis failed: timeout")
                .build();
        when(programRepo.findById("PROG1")).thenReturn(Optional.of(prog));
        when(paragraphRepo.findByProgramId("PROG1")).thenReturn(List.of());

        tasklet.execute(mock(StepContribution.class), chunkContext);

        verify(embeddingService, never()).embedDocument(any());
    }

    @Test
    void testVectorizeParagraphs() throws Exception {
        ExecutionContext jobExecCtx = new ExecutionContext();
        jobExecCtx.putString("programIds", "PROG1");
        jobExecCtx.putString("batchRunId", "run-456");

        ChunkContext chunkContext = mockChunkContext(jobExecCtx);

        ProgramDocument prog = ProgramDocument.builder()
                .programId("PROG1")
                .programName("PROG1")
                .programType("BATCH")
                .businessSummary("Batch program")
                .build();
        when(programRepo.findById("PROG1")).thenReturn(Optional.of(prog));

        ParagraphDocument para = ParagraphDocument.builder()
                .paragraphId("PROG1::MAIN-PARA")
                .programId("PROG1")
                .paragraphName("MAIN-PARA")
                .businessSummary("Processes main logic")
                .build();
        when(paragraphRepo.findByProgramId("PROG1")).thenReturn(List.of(para));
        when(embeddingService.embedDocument(any())).thenReturn(new float[768]);

        tasklet.execute(mock(StepContribution.class), chunkContext);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QdrantPoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(qdrantService).upsertPoints(captor.capture());

        List<QdrantPoint> points = captor.getValue();
        // 1 program + 1 paragraph = 2 points
        assertThat(points).hasSize(2);
        assertThat(points.stream().anyMatch(p -> "paragraph".equals(p.getPayload().get("type")))).isTrue();
        assertThat(points.stream().anyMatch(p -> "program".equals(p.getPayload().get("type")))).isTrue();
    }

    @Test
    void testEmptyProgramIdsSkipsProcessing() throws Exception {
        ExecutionContext jobExecCtx = new ExecutionContext();
        jobExecCtx.putString("programIds", "");

        ChunkContext chunkContext = mockChunkContext(jobExecCtx);
        tasklet.execute(mock(StepContribution.class), chunkContext);

        verify(programRepo, never()).findById(any());
        verify(qdrantService, never()).upsertPoints(any());
    }

    @Test
    void testUpdatesJobStatusOnCompletion() throws Exception {
        ExecutionContext jobExecCtx = new ExecutionContext();
        jobExecCtx.putString("programIds", "PROG1");
        jobExecCtx.putLong("serviceJobId", 42L);

        ChunkContext chunkContext = mockChunkContext(jobExecCtx);

        ProgramDocument prog = ProgramDocument.builder()
                .programId("PROG1")
                .programName("PROG1")
                .businessSummary("Summary")
                .build();
        when(programRepo.findById("PROG1")).thenReturn(Optional.of(prog));
        when(paragraphRepo.findByProgramId("PROG1")).thenReturn(List.of());
        when(embeddingService.embedDocument(any())).thenReturn(new float[768]);

        tasklet.execute(mock(StepContribution.class), chunkContext);

        // Verify status updated to COMPLETED
        verify(serviceClient).updateJobStatus(eq(42L), eq("COMPLETED"), eq("DONE"), eq(100), isNull(), isNull());
    }

    private ChunkContext mockChunkContext(ExecutionContext jobExecCtx) {
        JobExecution jobExecution = mock(JobExecution.class);
        when(jobExecution.getExecutionContext()).thenReturn(jobExecCtx);

        StepExecution stepExecution = mock(StepExecution.class);
        when(stepExecution.getJobExecution()).thenReturn(jobExecution);

        StepContext stepContext = mock(StepContext.class);
        when(stepContext.getStepExecution()).thenReturn(stepExecution);

        ChunkContext chunkContext = mock(ChunkContext.class);
        when(chunkContext.getStepContext()).thenReturn(stepContext);

        return chunkContext;
    }
}
