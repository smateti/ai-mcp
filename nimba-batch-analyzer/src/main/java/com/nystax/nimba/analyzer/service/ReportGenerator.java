package com.nystax.nimba.analyzer.service;

import com.nystax.nimba.analyzer.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ReportGenerator {

    @Value("${nimba.analyzer.report-output-dir:./reports}")
    private String reportOutputDir;

    public void printConsole(ProjectAnalysisReport report) {
        System.out.println();
        System.out.println("=== NIMBA BATCH ANALYSIS REPORT ===");
        System.out.println("Project: " + report.getProjectPath() + " | Analyzed: " + report.getAnalyzedAt());

        if (!report.getWasDependencies().isEmpty()) {
            System.out.println("WAS Function Dependencies: " + report.getWasDependencies().size());
            for (WasDependency dep : report.getWasDependencies()) {
                System.out.println("  - " + dep.getArtifactId() + ":" + dep.getVersion());
            }
        }

        for (AnalysisResult job : report.getJobAnalyses()) {
            JobDefinition jd = job.getJobDefinition();
            System.out.println();
            System.out.println("--- Job: " + jd.getJobId() + " (" + jd.getSteps().size() + " steps) ---");
            System.out.println("  Resumable: " + jd.isResumable()
                    + " | Archive Files: " + jd.isArchiveFiles());

            if (job.getJobListenerClassName() != null) {
                System.out.println("  Job Listener: " + jd.getJobListenerId()
                        + " (" + job.getJobListenerClassName() + ")");
            }

            int stepNum = 1;
            for (StepAnalysis sa : job.getStepAnalyses()) {
                StepDefinition step = sa.getStepDefinition();
                System.out.println();
                System.out.printf("  Step %d: %s [%s] parallelism=%s failOnError=%s%n",
                        stepNum++, step.getStepId(), step.getStepType(),
                        step.getParallelism(), step.isFailOnError());

                if (step.getReader() != null) {
                    ReaderDefinition r = step.getReader();
                    System.out.println("    Reader: " + r.getReaderId() + " (" + r.getReaderType() + ")");
                    if (r.getReaderType() == ReaderType.FRAMEWORK) {
                        String filePath = r.getParameters().get("filePath");
                        if (filePath != null) System.out.println("    File: " + filePath);
                        if (sa.getFileFormat() != null) System.out.println("    Format: " + sa.getFileFormat());
                    }
                    if (sa.isHasDeserializer()) {
                        System.out.println("    Deserializer: " + sa.getDeserializerClassName());
                        if (sa.getDeserializerInsight() != null && sa.getDeserializerInsight().getSummary() != null) {
                            System.out.println("      -> " + sa.getDeserializerInsight().getSummary());
                        }
                    }
                } else {
                    System.out.println("    No Reader (Custom Step - single threaded)");
                }

                if (sa.getProcessorClassName() != null) {
                    System.out.println("    Processor: " + sa.getProcessorClassName());
                    if (sa.getProcessorInsight() != null && sa.getProcessorInsight().getSummary() != null) {
                        System.out.println("      -> " + sa.getProcessorInsight().getSummary());
                    }
                }

                if (!sa.getProcessorFunctionCalls().isEmpty()) {
                    System.out.print("    Function Calls: ");
                    System.out.println(sa.getProcessorFunctionCalls().stream()
                            .map(fc -> fc.getClientClassName() + "." + fc.getMethodCalled() + "()")
                            .distinct()
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("None"));
                }

                if (sa.getProcessErrorThreshold() != null) {
                    System.out.println("    Error Threshold: " + sa.getProcessErrorThreshold());
                }
            }

            if (!job.getBatchExitUsages().isEmpty()) {
                System.out.println();
                System.out.println("  BatchExitException Usages:");
                for (BatchExitUsage exit : job.getBatchExitUsages()) {
                    System.out.printf("    %s:%d - Status: %s, \"%s\"%n",
                            exit.getClassName(), exit.getLineNumber(),
                            exit.getStatusCode(), exit.getMessage());
                }
            }
        }
        System.out.println();
    }

    public Path writeMarkdownReport(ProjectAnalysisReport report) throws IOException {
        Path outputDir = Path.of(reportOutputDir);
        Files.createDirectories(outputDir);

        String projectName = Path.of(report.getProjectPath()).getFileName().toString();
        Path outputFile = outputDir.resolve(projectName + "-analysis.md");

        StringBuilder md = new StringBuilder();
        md.append("# Nimba Batch Analysis Report\n\n");
        md.append("**Project**: ").append(report.getProjectPath()).append("  \n");
        md.append("**Analyzed**: ").append(report.getAnalyzedAt()).append("\n\n");

        // WAS Dependencies
        if (!report.getWasDependencies().isEmpty()) {
            md.append("## WAS Function Dependencies\n\n");
            md.append("| GroupId | ArtifactId | Version |\n");
            md.append("|---------|-----------|--------|\n");
            for (WasDependency dep : report.getWasDependencies()) {
                md.append("| ").append(dep.getGroupId())
                        .append(" | ").append(dep.getArtifactId())
                        .append(" | ").append(dep.getVersion())
                        .append(" |\n");
            }
            md.append("\n");
        }

        // Each job
        for (AnalysisResult job : report.getJobAnalyses()) {
            JobDefinition jd = job.getJobDefinition();
            md.append("## Job: ").append(jd.getJobId()).append("\n\n");
            md.append("- **Resumable**: ").append(jd.isResumable()).append("\n");
            md.append("- **Archive Files**: ").append(jd.isArchiveFiles()).append("\n");
            md.append("- **Steps**: ").append(jd.getSteps().size()).append("\n");
            if (job.getJobListenerClassName() != null) {
                md.append("- **Job Listener**: ").append(jd.getJobListenerId())
                        .append(" (").append(job.getJobListenerClassName()).append(")\n");
            }
            md.append("\n");

            int stepNum = 1;
            for (StepAnalysis sa : job.getStepAnalyses()) {
                StepDefinition step = sa.getStepDefinition();
                md.append("### Step ").append(stepNum++).append(": ").append(step.getStepId()).append("\n\n");
                md.append("- **Type**: ").append(step.getStepType()).append("\n");
                md.append("- **Parallelism**: ").append(step.getParallelism()).append("\n");
                md.append("- **Fail On Error**: ").append(step.isFailOnError()).append("\n\n");

                // Reader
                if (step.getReader() != null) {
                    ReaderDefinition r = step.getReader();
                    md.append("#### Reader\n\n");
                    md.append("- **ID**: ").append(r.getReaderId()).append("\n");
                    md.append("- **Type**: ").append(r.getReaderType()).append("\n");
                    if (!r.getParameters().isEmpty()) {
                        md.append("- **Parameters**: ");
                        r.getParameters().forEach((k, v) -> md.append(k).append("=").append(v).append(", "));
                        md.setLength(md.length() - 2);
                        md.append("\n");
                    }
                    if (sa.getFileFormat() != null) {
                        md.append("- **File Format**: ").append(sa.getFileFormat()).append("\n");
                    }
                    md.append("\n");
                } else {
                    md.append("#### Reader\n\nNo reader - **Custom Step** (single-threaded)\n\n");
                }

                // Deserializer
                if (sa.isHasDeserializer()) {
                    md.append("#### Deserializer: ").append(sa.getDeserializerClassName()).append("\n\n");
                    appendLlmInsight(md, sa.getDeserializerInsight());
                    if (sa.isDeserializerMakesFunctionCalls()) {
                        md.append("**Function Calls**:\n");
                        for (FunctionCallInfo fc : sa.getDeserializerFunctionCalls()) {
                            md.append("- ").append(fc.getClientClassName()).append(".")
                                    .append(fc.getMethodCalled()).append("()\n");
                        }
                    }
                    md.append("\n");
                }

                // Processor
                if (sa.getProcessorClassName() != null) {
                    md.append("#### Processor: ").append(sa.getProcessorClassName()).append("\n\n");
                    appendLlmInsight(md, sa.getProcessorInsight());
                    if (sa.isProcessorMakesFunctionCalls()) {
                        md.append("**Function Calls**:\n");
                        for (FunctionCallInfo fc : sa.getProcessorFunctionCalls()) {
                            md.append("- ").append(fc.getClientClassName()).append(".")
                                    .append(fc.getMethodCalled()).append("() (line ")
                                    .append(fc.getLineNumber()).append(")\n");
                        }
                    }
                    if (sa.getProcessErrorThreshold() != null) {
                        md.append("\n**Error Threshold**: ").append(sa.getProcessErrorThreshold()).append("\n");
                    }
                    md.append("\n");
                }
            }

            // BatchExitException
            if (!job.getBatchExitUsages().isEmpty()) {
                md.append("### BatchExitException Usages\n\n");
                md.append("| Class | Line | Status | Message |\n");
                md.append("|-------|------|--------|--------|\n");
                for (BatchExitUsage exit : job.getBatchExitUsages()) {
                    md.append("| ").append(exit.getClassName())
                            .append(" | ").append(exit.getLineNumber())
                            .append(" | ").append(exit.getStatusCode())
                            .append(" | ").append(exit.getMessage())
                            .append(" |\n");
                }
                md.append("\n");
            }
        }

        Files.writeString(outputFile, md.toString());
        return outputFile;
    }

    private void appendLlmInsight(StringBuilder md, LlmInsight insight) {
        if (insight == null) return;
        if (insight.getSummary() != null)
            md.append("> **Summary**: ").append(insight.getSummary()).append("\n\n");
        if (insight.getBusinessLogic() != null)
            md.append("> **Business Logic**: ").append(insight.getBusinessLogic()).append("\n\n");
        if (insight.getParsingLogic() != null)
            md.append("> **Parsing Logic**: ").append(insight.getParsingLogic()).append("\n\n");
        if (insight.getErrorHandling() != null)
            md.append("> **Error Handling**: ").append(insight.getErrorHandling()).append("\n\n");
        if (insight.getPatterns() != null)
            md.append("> **Patterns**: ").append(insight.getPatterns()).append("\n\n");
        if (insight.getIssues() != null)
            md.append("> **Issues**: ").append(insight.getIssues()).append("\n\n");
    }
}
