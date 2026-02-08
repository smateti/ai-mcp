package com.enterprise.cobol.batch;

import com.enterprise.cobol.document.DependencyDocument;
import com.enterprise.cobol.document.ParagraphDocument;
import com.enterprise.cobol.document.ProgramDocument;
import com.enterprise.cobol.repository.DependencyDocumentRepository;
import com.enterprise.cobol.repository.ParagraphDocumentRepository;
import com.enterprise.cobol.repository.ProgramDocumentRepository;
import com.enterprise.cobol.service.CobolParserService;
import com.enterprise.cobol.service.CopybookResolver;
import com.enterprise.cobol.service.ServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CobolParseTasklet implements Tasklet {

    private final CobolParserService parserService;
    private final CopybookResolver copybookResolver;
    private final ProgramDocumentRepository programRepo;
    private final ParagraphDocumentRepository paragraphRepo;
    private final DependencyDocumentRepository dependencyRepo;
    private final ServiceClient serviceClient;

    @Value("${cobol.file-patterns}")
    private String filePatterns;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        var jobParams = chunkContext.getStepContext().getJobParameters();
        String folderPath = (String) jobParams.get("folderPath");
        String copybookPath = (String) jobParams.get("copybookPath");
        String batchRunId = (String) jobParams.get("batchRunId");
        Long serviceJobId = jobParams.get("serviceJobId") != null ?
                Long.parseLong(jobParams.get("serviceJobId").toString()) : null;
        Long projectId = jobParams.get("projectId") != null ?
                Long.parseLong(jobParams.get("projectId").toString()) : null;
        String customPrompt = (String) jobParams.get("customPrompt");

        log.info("Step 1: PARSE - folder={}, copybook={}, batchRunId={}, projectId={}", folderPath, copybookPath, batchRunId, projectId);

        if (serviceJobId != null) {
            serviceClient.updateJobStatus(serviceJobId, "RUNNING", "PARSING", 0, null, null);
        }

        Path folder = Path.of(folderPath);
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Folder not found: " + folderPath);
        }

        // Build copybook map
        List<Path> copybookDirs = new ArrayList<>();
        if (copybookPath != null && !copybookPath.isEmpty()) {
            for (String dir : copybookPath.split(",")) {
                Path cpyDir = Path.of(dir.trim());
                if (Files.isDirectory(cpyDir)) {
                    copybookDirs.add(cpyDir);
                }
            }
        }
        // Also check for cpy subdirectory next to source
        Path siblingCpy = folder.getParent() != null ? folder.getParent().resolve("cpy") : null;
        if (siblingCpy != null && Files.isDirectory(siblingCpy) && !copybookDirs.contains(siblingCpy)) {
            copybookDirs.add(siblingCpy);
        }
        Path siblingCpyBms = folder.getParent() != null ? folder.getParent().resolve("cpy-bms") : null;
        if (siblingCpyBms != null && Files.isDirectory(siblingCpyBms) && !copybookDirs.contains(siblingCpyBms)) {
            copybookDirs.add(siblingCpyBms);
        }

        Map<String, Path> copybookMap = copybookResolver.buildCopybookMap(copybookDirs);

        // Scan for COBOL files
        List<Path> cobolFiles = scanForCobolFiles(folder);
        log.info("Found {} COBOL files in {}", cobolFiles.size(), folderPath);

        if (serviceJobId != null) {
            serviceClient.updateJobStatus(serviceJobId, "RUNNING", "PARSING", 5, cobolFiles.size(), null);
        }

        List<String> programIds = new ArrayList<>();

        for (int i = 0; i < cobolFiles.size(); i++) {
            Path file = cobolFiles.get(i);
            try {
                log.info("Parsing [{}/{}]: {}", i + 1, cobolFiles.size(), file.getFileName());

                CobolParserService.ParsedProgram parsed = parserService.parseProgram(file);
                Map<String, String> resolvedCopybooks = copybookResolver.resolveAll(parsed.getSourceCode(), copybookMap);

                // Store program document
                ProgramDocument progDoc = ProgramDocument.builder()
                        .programId(parsed.getProgramId())
                        .programName(parsed.getProgramName())
                        .programType(parsed.getProgramType())
                        .author(parsed.getAuthor())
                        .lineCount(parsed.getLineCount())
                        .paragraphCount(parsed.getParagraphCount())
                        .usesCics(parsed.isUsesCics())
                        .usesDb2(parsed.isUsesDb2())
                        .usesIdms(parsed.isUsesIdms())
                        .usesIms(parsed.isUsesIms())
                        .usesMq(parsed.isUsesMq())
                        .calledPrograms(parsed.getCalledPrograms())
                        .copybooks(parsed.getCopybooks())
                        .sourceCode(parsed.getSourceCode())
                        .dataStructures(parsed.getDataStructures())
                        .sqlStatements(parsed.getSqlStatements())
                        .conditionNames(parsed.getConditionNames())
                        .fileOperations(parsed.getFileOperations())
                        .analyzedAt(LocalDateTime.now())
                        .batchRunId(batchRunId)
                        .projectId(projectId)
                        .build();
                programRepo.save(progDoc);
                programIds.add(parsed.getProgramId());

                // Store paragraph documents
                for (var para : parsed.getParagraphs()) {
                    ParagraphDocument paraDoc = ParagraphDocument.builder()
                            .paragraphId(parsed.getProgramId() + "::" + para.getParagraphName())
                            .programId(parsed.getProgramId())
                            .programName(parsed.getProgramName())
                            .paragraphName(para.getParagraphName())
                            .type(para.getType())
                            .sourceCode(para.getSourceCode())
                            .startLine(para.getStartLine())
                            .endLine(para.getEndLine())
                            .lineCount(para.getLineCount())
                            .performsCalls(para.getPerformsCalls())
                            .hasExecSql(para.isHasExecSql())
                            .hasExecCics(para.isHasExecCics())
                            .hasCallStatement(para.isHasCallStatement())
                            .businessRules(para.getBusinessRules())
                            .dataAccess(para.getDataAccess())
                            .calculations(para.getCalculations())
                            .analyzedAt(LocalDateTime.now())
                            .batchRunId(batchRunId)
                            .projectId(projectId)
                            .build();
                    paragraphRepo.save(paraDoc);
                }

                // Store dependency documents
                for (var dep : parsed.getDependencies()) {
                    DependencyDocument depDoc = DependencyDocument.builder()
                            .dependencyId(UUID.randomUUID().toString())
                            .programId(parsed.getProgramId())
                            .programName(parsed.getProgramName())
                            .dependencyType(dep.getType())
                            .targetName(dep.getTargetName())
                            .details(dep.getDetails())
                            .callingContext(dep.getCallingContext())
                            .analyzedAt(LocalDateTime.now())
                            .batchRunId(batchRunId)
                            .projectId(projectId)
                            .build();
                    dependencyRepo.save(depDoc);
                }

                int progress = 5 + (int) ((double)(i + 1) / cobolFiles.size() * 25);
                if (serviceJobId != null) {
                    serviceClient.updateJobStatus(serviceJobId, "RUNNING", "PARSING", progress, null, null);
                }

            } catch (Exception e) {
                log.error("Failed to parse {}: {}", file.getFileName(), e.getMessage());
            }
        }

        // Store program IDs in JOB execution context for next steps
        var jobExecCtx = chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext();
        jobExecCtx.put("programIds", String.join(",", programIds));
        jobExecCtx.put("batchRunId", batchRunId);
        if (serviceJobId != null) {
            jobExecCtx.put("serviceJobId", serviceJobId);
        }
        if (projectId != null) {
            jobExecCtx.put("projectId", projectId);
        }
        if (customPrompt != null && !customPrompt.isEmpty()) {
            jobExecCtx.putString("customPrompt", customPrompt);
        }

        log.info("Step 1 complete: parsed {} programs, stored in ES", programIds.size());
        return RepeatStatus.FINISHED;
    }

    private List<Path> scanForCobolFiles(Path folder) throws IOException {
        List<Path> files = new ArrayList<>();
        Set<String> patterns = new HashSet<>();
        for (String p : filePatterns.split(",")) {
            patterns.add(p.trim().toLowerCase());
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    String name = file.getFileName().toString().toLowerCase();
                    for (String pattern : patterns) {
                        String ext = pattern.replace("*", "");
                        if (name.endsWith(ext)) {
                            files.add(file);
                            break;
                        }
                    }
                }
            }
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));
        return files;
    }
}
