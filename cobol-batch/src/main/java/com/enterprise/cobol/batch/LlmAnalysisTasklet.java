package com.enterprise.cobol.batch;

import com.enterprise.cobol.document.ParagraphDocument;
import com.enterprise.cobol.document.ProgramDocument;
import com.enterprise.cobol.repository.ParagraphDocumentRepository;
import com.enterprise.cobol.repository.ProgramDocumentRepository;
import com.enterprise.cobol.service.CopybookResolver;
import com.enterprise.cobol.service.CobolParserService;
import com.enterprise.cobol.service.LlmAnalysisService;
import com.enterprise.cobol.service.ServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmAnalysisTasklet implements Tasklet {

    private final ProgramDocumentRepository programRepo;
    private final ParagraphDocumentRepository paragraphRepo;
    private final LlmAnalysisService llmService;
    private final ServiceClient serviceClient;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        var execCtx = chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext();
        String programIdsStr = execCtx.getString("programIds", "");
        Long serviceJobId = execCtx.containsKey("serviceJobId") ?
                execCtx.getLong("serviceJobId") : null;
        String customPrompt = execCtx.containsKey("customPrompt") ?
                execCtx.getString("customPrompt") : null;

        if (programIdsStr.isEmpty()) {
            log.warn("No program IDs found in execution context");
            return RepeatStatus.FINISHED;
        }

        String[] programIds = programIdsStr.split(",");
        log.info("Step 2: LLM ANALYSIS - {} programs to analyze", programIds.length);

        if (serviceJobId != null) {
            serviceClient.updateJobStatus(serviceJobId, "RUNNING", "LLM_ANALYSIS", 30, null, null);
        }

        for (int i = 0; i < programIds.length; i++) {
            String programId = programIds[i].trim();
            if (programId.isEmpty()) continue;

            try {
                log.info("Analyzing [{}/{}]: {}", i + 1, programIds.length, programId);

                var programOpt = programRepo.findById(programId);
                if (programOpt.isEmpty()) {
                    log.warn("Program not found in ES: {}", programId);
                    continue;
                }

                ProgramDocument program = programOpt.get();

                // Build parser data structures for LLM context
                List<CobolParserService.Dependency> deps = buildDependencies(program);
                Map<String, String> copybooks = buildCopybookContext(program);

                CobolParserService.ParsedProgram parsedProgram = CobolParserService.ParsedProgram.builder()
                        .programName(program.getProgramName())
                        .programType(program.getProgramType())
                        .author(program.getAuthor())
                        .paragraphs(List.of())
                        .build();

                // Get paragraphs for this program
                List<ParagraphDocument> paragraphs = paragraphRepo.findByProgramId(programId);
                List<CobolParserService.ParsedParagraph> parsedParagraphs = paragraphs.stream()
                        .map(p -> CobolParserService.ParsedParagraph.builder()
                                .paragraphName(p.getParagraphName())
                                .sourceCode(p.getSourceCode())
                                .build())
                        .toList();
                parsedProgram.setParagraphs(parsedParagraphs);
                parsedProgram.setDependencies(deps);
                parsedProgram.setDataStructures(program.getDataStructures());
                parsedProgram.setSqlStatements(program.getSqlStatements());
                parsedProgram.setConditionNames(program.getConditionNames());
                parsedProgram.setFileOperations(program.getFileOperations());

                // Generate program business summary
                String businessSummary = llmService.analyzeProgram(parsedProgram, copybooks, deps);
                program.setBusinessSummary(businessSummary);
                programRepo.save(program);
                log.info("  Program summary generated for {}", programId);

                // Extract business rules via LLM (reads source code directly)
                List<String> extractedRules = llmService.extractBusinessRules(parsedProgram, copybooks, customPrompt);
                program.setExtractedBusinessRules(extractedRules);
                programRepo.save(program);
                log.info("  Business rules extracted for {} ({} rules)", programId, extractedRules.size());

                // Generate paragraph summaries
                String programContext = program.getProgramName() + " (" + program.getProgramType() + "): "
                        + (businessSummary.length() > 200 ? businessSummary.substring(0, 200) : businessSummary);

                for (int j = 0; j < paragraphs.size(); j++) {
                    ParagraphDocument para = paragraphs.get(j);
                    try {
                        String paraSummary = llmService.analyzeParagraph(
                                para.getSourceCode(), para.getParagraphName(), programContext, copybooks,
                                para.getBusinessRules(), para.getDataAccess(), para.getCalculations());
                        para.setBusinessSummary(paraSummary);
                        paragraphRepo.save(para);
                        log.debug("  Paragraph [{}/{}]: {} done", j + 1, paragraphs.size(), para.getParagraphName());
                    } catch (Exception e) {
                        log.warn("  Failed to analyze paragraph {}: {}", para.getParagraphName(), e.getMessage());
                    }
                }

                int progress = 30 + (int) ((double)(i + 1) / programIds.length * 40);
                if (serviceJobId != null) {
                    serviceClient.updateJobStatus(serviceJobId, "RUNNING", "LLM_ANALYSIS", progress, null, null);
                }

            } catch (Exception e) {
                log.error("Failed to analyze program {}: {}", programId, e.getMessage());
            }
        }

        log.info("Step 2 complete: LLM analysis finished for {} programs", programIds.length);
        return RepeatStatus.FINISHED;
    }

    private List<CobolParserService.Dependency> buildDependencies(ProgramDocument program) {
        var deps = new java.util.ArrayList<CobolParserService.Dependency>();
        if (program.getCalledPrograms() != null) {
            for (String called : program.getCalledPrograms()) {
                deps.add(CobolParserService.Dependency.builder()
                        .type("CALL").targetName(called).details(Map.of()).build());
            }
        }
        if (program.getCopybooks() != null) {
            for (String cpy : program.getCopybooks()) {
                deps.add(CobolParserService.Dependency.builder()
                        .type("COPY").targetName(cpy).details(Map.of()).build());
            }
        }
        if (program.isUsesCics()) {
            deps.add(CobolParserService.Dependency.builder()
                    .type("CICS").targetName("CICS").details(Map.of()).build());
        }
        if (program.isUsesDb2()) {
            deps.add(CobolParserService.Dependency.builder()
                    .type("DB2").targetName("DB2").details(Map.of()).build());
        }
        return deps;
    }

    private Map<String, String> buildCopybookContext(ProgramDocument program) {
        // Copybooks are stored in program.getCopybooks() as names only
        // Content was resolved during parse step but not stored per-copybook
        // Return empty map - the LLM will rely on paragraph source code
        return new HashMap<>();
    }
}
