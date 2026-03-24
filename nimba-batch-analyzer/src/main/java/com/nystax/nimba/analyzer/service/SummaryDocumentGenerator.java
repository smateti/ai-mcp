package com.nystax.nimba.analyzer.service;

import com.nystax.nimba.analyzer.llm.LlamaClient;
import com.nystax.nimba.analyzer.llm.LlmPromptBuilder;
import com.nystax.nimba.analyzer.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class SummaryDocumentGenerator {

    private static final Logger log = LoggerFactory.getLogger(SummaryDocumentGenerator.class);

    private final LlamaClient llamaClient;
    private final LlmPromptBuilder promptBuilder;

    @Value("${nimba.analyzer.report-output-dir:./reports}")
    private String reportOutputDir;

    public SummaryDocumentGenerator(LlamaClient llamaClient, LlmPromptBuilder promptBuilder) {
        this.llamaClient = llamaClient;
        this.promptBuilder = promptBuilder;
    }

    public String generateSummary(ProjectAnalysisReport report) {
        log.info("Generating project summary document via LLM...");

        String analysisData = buildAnalysisDataText(report);
        String summary = llamaClient.analyze(
                LlmPromptBuilder.SYSTEM_PROMPT,
                promptBuilder.buildProjectSummaryPrompt(analysisData));

        return summary;
    }

    public Path writeSummaryDocument(ProjectAnalysisReport report) throws IOException {
        Path outputDir = Path.of(reportOutputDir);
        Files.createDirectories(outputDir);

        String projectName = Path.of(report.getProjectPath()).getFileName().toString();
        Path outputFile = outputDir.resolve(projectName + "-summary.md");

        String content = report.getProjectSummary();
        if (content == null || content.isBlank()) {
            content = "# Project Summary\n\nSummary generation was not available.\n";
        }

        Files.writeString(outputFile, content);
        log.info("Summary document written to: {}", outputFile);
        return outputFile;
    }

    private String buildAnalysisDataText(ProjectAnalysisReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("PROJECT: ").append(report.getProjectPath()).append("\n\n");

        // WAS Dependencies
        if (!report.getWasDependencies().isEmpty()) {
            sb.append("WAS FUNCTION DEPENDENCIES:\n");
            for (WasDependency dep : report.getWasDependencies()) {
                sb.append("  - ").append(dep.getArtifactId()).append(" (").append(dep.getVersion()).append(")\n");
            }
            sb.append("\n");
        }

        // Jobs
        for (AnalysisResult job : report.getJobAnalyses()) {
            JobDefinition jd = job.getJobDefinition();
            sb.append("JOB: ").append(jd.getJobId()).append("\n");
            sb.append("  Resumable: ").append(jd.isResumable()).append("\n");
            sb.append("  Archive Files: ").append(jd.isArchiveFiles()).append("\n");
            sb.append("  Steps: ").append(jd.getSteps().size()).append("\n");

            if (job.getJobListenerClassName() != null) {
                sb.append("  Job Listener: ").append(job.getJobListenerClassName()).append("\n");
                if (job.getJobListenerInsight() != null) {
                    sb.append("    Summary: ").append(job.getJobListenerInsight().getSummary()).append("\n");
                }
            }

            int stepNum = 1;
            for (StepAnalysis sa : job.getStepAnalyses()) {
                StepDefinition step = sa.getStepDefinition();
                sb.append("\n  STEP ").append(stepNum++).append(": ").append(step.getStepId())
                        .append(" [").append(step.getStepType()).append("]\n");
                sb.append("    Parallelism: ").append(step.getParallelism()).append("\n");
                sb.append("    FailOnError: ").append(step.isFailOnError()).append("\n");

                if (step.getReader() != null) {
                    sb.append("    Reader: ").append(step.getReader().getReaderId())
                            .append(" (").append(step.getReader().getReaderType()).append(")\n");
                    if (sa.getFileFormat() != null) {
                        sb.append("    File Format: ").append(sa.getFileFormat()).append("\n");
                    }
                    step.getReader().getParameters().forEach((k, v) ->
                            sb.append("    Param ").append(k).append(": ").append(v).append("\n"));
                } else {
                    sb.append("    Reader: None (Custom step, single-threaded)\n");
                }

                if (sa.isHasDeserializer() && sa.getDeserializerClassName() != null) {
                    sb.append("    Deserializer: ").append(sa.getDeserializerClassName()).append("\n");
                    if (sa.getDeserializerInsight() != null) {
                        sb.append("      Summary: ").append(sa.getDeserializerInsight().getSummary()).append("\n");
                        if (sa.getDeserializerInsight().getParsingLogic() != null) {
                            sb.append("      Parsing: ").append(sa.getDeserializerInsight().getParsingLogic()).append("\n");
                        }
                    }
                    if (sa.isDeserializerMakesFunctionCalls()) {
                        sb.append("      Function Calls: ");
                        sa.getDeserializerFunctionCalls().forEach(fc ->
                                sb.append(fc.getClientClassName()).append(".").append(fc.getMethodCalled()).append("() "));
                        sb.append("\n");
                    }
                }

                if (sa.getProcessorClassName() != null) {
                    sb.append("    Processor: ").append(sa.getProcessorClassName()).append("\n");
                    if (sa.getProcessorInsight() != null) {
                        sb.append("      Summary: ").append(sa.getProcessorInsight().getSummary()).append("\n");
                        if (sa.getProcessorInsight().getBusinessLogic() != null) {
                            sb.append("      Logic: ").append(sa.getProcessorInsight().getBusinessLogic()).append("\n");
                        }
                    }
                    if (sa.isProcessorMakesFunctionCalls()) {
                        sb.append("      Function Calls: ");
                        sa.getProcessorFunctionCalls().forEach(fc ->
                                sb.append(fc.getClientClassName()).append(".").append(fc.getMethodCalled()).append("() "));
                        sb.append("\n");
                    }
                }

                if (sa.getProcessErrorThreshold() != null) {
                    sb.append("    Error Threshold: ").append(sa.getProcessErrorThreshold()).append("\n");
                }
            }

            // Batch exits
            if (!job.getBatchExitUsages().isEmpty()) {
                sb.append("\n  BATCH EXIT USAGES:\n");
                for (BatchExitUsage exit : job.getBatchExitUsages()) {
                    sb.append("    ").append(exit.getClassName()).append(":").append(exit.getLineNumber())
                            .append(" Status=").append(exit.getStatusCode())
                            .append(" Message=\"").append(exit.getMessage()).append("\"\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
