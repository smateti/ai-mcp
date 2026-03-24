package com.nystax.nimba.analyzer.service;

import com.nystax.nimba.analyzer.llm.LlamaClient;
import com.nystax.nimba.analyzer.llm.LlmPromptBuilder;
import com.nystax.nimba.analyzer.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SummaryDocumentGenerator {

    private static final Logger log = LoggerFactory.getLogger(SummaryDocumentGenerator.class);

    private final LlamaClient llamaClient;
    private final LlmPromptBuilder promptBuilder;

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
                    appendInsightField(sb, "Summary", job.getJobListenerInsight().getSummary());
                    appendInsightField(sb, "On Job Start", job.getJobListenerInsight().getOnJobStart());
                    appendInsightField(sb, "On Job Finish", job.getJobListenerInsight().getOnJobFinish());
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

                    // Custom reader insight
                    if (sa.getReaderInsight() != null) {
                        appendInsightField(sb, "Reader Summary", sa.getReaderInsight().getSummary());
                        appendInsightField(sb, "Data Source", sa.getReaderInsight().getDataSource());
                        appendInsightField(sb, "Query Pattern", sa.getReaderInsight().getQueryPattern());
                    }
                } else {
                    sb.append("    Reader: None (Custom step, single-threaded)\n");
                }

                if (sa.isHasDeserializer() && sa.getDeserializerClassName() != null) {
                    sb.append("    Deserializer: ").append(sa.getDeserializerClassName()).append("\n");
                    if (sa.getDeserializerInsight() != null) {
                        appendInsightField(sb, "Summary", sa.getDeserializerInsight().getSummary());
                        appendInsightField(sb, "Parsing", sa.getDeserializerInsight().getParsingLogic());
                        appendInsightField(sb, "Field Mapping", sa.getDeserializerInsight().getFieldMapping());
                        appendInsightField(sb, "Record Structure", sa.getDeserializerInsight().getRecordStructure());
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
                        appendInsightField(sb, "Summary", sa.getProcessorInsight().getSummary());
                        appendInsightField(sb, "Logic", sa.getProcessorInsight().getBusinessLogic());
                        appendInsightField(sb, "Conditional Logic", sa.getProcessorInsight().getConditionalLogic());
                        appendInsightField(sb, "Data Transformations", sa.getProcessorInsight().getDataTransformations());
                        appendInsightField(sb, "DB Operations", sa.getProcessorInsight().getDbOperations());
                        appendInsightField(sb, "Output", sa.getProcessorInsight().getOutputDescription());
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

    private void appendInsightField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("      ").append(label).append(": ").append(value).append("\n");
        }
    }
}
