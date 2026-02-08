package com.enterprise.cobol.batch;

import com.enterprise.cobol.document.ParagraphDocument;
import com.enterprise.cobol.document.ProgramDocument;
import com.enterprise.cobol.repository.ParagraphDocumentRepository;
import com.enterprise.cobol.repository.ProgramDocumentRepository;
import com.enterprise.cobol.service.EmbeddingService;
import com.enterprise.cobol.service.QdrantService;
import com.enterprise.cobol.service.QdrantService.QdrantPoint;
import com.enterprise.cobol.service.ServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorizeTasklet implements Tasklet {

    private final ProgramDocumentRepository programRepo;
    private final ParagraphDocumentRepository paragraphRepo;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final ServiceClient serviceClient;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        var execCtx = chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext();
        String programIdsStr = execCtx.getString("programIds", "");
        Long serviceJobId = execCtx.containsKey("serviceJobId") ?
                execCtx.getLong("serviceJobId") : null;
        String batchRunId = execCtx.getString("batchRunId", "");
        Long projectId = execCtx.containsKey("projectId") ?
                execCtx.getLong("projectId") : null;

        if (programIdsStr.isEmpty()) {
            log.warn("No program IDs found in execution context");
            return RepeatStatus.FINISHED;
        }

        String[] programIds = programIdsStr.split(",");
        log.info("Step 3: VECTORIZE - {} programs to embed", programIds.length);

        if (serviceJobId != null) {
            serviceClient.updateJobStatus(serviceJobId, "RUNNING", "VECTORIZING", 70, null, null);
        }

        // Ensure Qdrant collection exists
        qdrantService.ensureCollection();

        List<QdrantPoint> points = new ArrayList<>();

        for (int i = 0; i < programIds.length; i++) {
            String programId = programIds[i].trim();
            if (programId.isEmpty()) continue;

            try {
                var programOpt = programRepo.findById(programId);
                if (programOpt.isEmpty()) continue;

                ProgramDocument program = programOpt.get();

                // Embed program summary
                if (program.getBusinessSummary() != null && !program.getBusinessSummary().isEmpty()
                        && !program.getBusinessSummary().startsWith("Analysis failed")) {
                    float[] vector = embeddingService.embedDocument(program.getBusinessSummary());
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("programId", program.getProgramId());
                    payload.put("programName", program.getProgramName());
                    payload.put("programType", program.getProgramType());
                    payload.put("type", "program");
                    payload.put("chunkText", program.getBusinessSummary());
                    if (!batchRunId.isEmpty()) payload.put("batchRunId", batchRunId);
                    if (projectId != null) payload.put("projectId", projectId.toString());

                    points.add(QdrantPoint.builder()
                            .id(UUID.nameUUIDFromBytes(("prog:" + programId).getBytes()).toString())
                            .vector(vector)
                            .payload(payload)
                            .build());

                    log.debug("Embedded program summary: {}", programId);
                }

                // Embed paragraph summaries
                List<ParagraphDocument> paragraphs = paragraphRepo.findByProgramId(programId);
                for (ParagraphDocument para : paragraphs) {
                    if (para.getBusinessSummary() != null && !para.getBusinessSummary().isEmpty()
                            && !para.getBusinessSummary().startsWith("Analysis failed")) {
                        float[] vector = embeddingService.embedDocument(para.getBusinessSummary());
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("programId", program.getProgramId());
                        payload.put("programName", program.getProgramName());
                        payload.put("paragraphName", para.getParagraphName());
                        payload.put("programType", program.getProgramType());
                        payload.put("type", "paragraph");
                        payload.put("chunkText", para.getBusinessSummary());
                        if (!batchRunId.isEmpty()) payload.put("batchRunId", batchRunId);
                        if (projectId != null) payload.put("projectId", projectId.toString());

                        points.add(QdrantPoint.builder()
                                .id(UUID.nameUUIDFromBytes(("para:" + para.getParagraphId()).getBytes()).toString())
                                .vector(vector)
                                .payload(payload)
                                .build());
                    }
                }

                // Batch upsert every 50 points
                if (points.size() >= 50) {
                    qdrantService.upsertPoints(points);
                    log.info("Upserted {} vectors to Qdrant", points.size());
                    points.clear();
                }

                int progress = 70 + (int) ((double)(i + 1) / programIds.length * 28);
                if (serviceJobId != null) {
                    serviceClient.updateJobStatus(serviceJobId, "RUNNING", "VECTORIZING", progress, null, null);
                }

            } catch (Exception e) {
                log.error("Failed to vectorize program {}: {}", programId, e.getMessage());
            }
        }

        // Upsert remaining points
        if (!points.isEmpty()) {
            qdrantService.upsertPoints(points);
            log.info("Upserted final {} vectors to Qdrant", points.size());
        }

        if (serviceJobId != null) {
            serviceClient.updateJobStatus(serviceJobId, "COMPLETED", "DONE", 100, null, null);
        }

        log.info("Step 3 complete: vectorization finished");
        return RepeatStatus.FINISHED;
    }
}
