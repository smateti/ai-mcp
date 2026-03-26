package com.nystax.nimba.analyzer.service;

import com.nystax.nimba.analyzer.llm.LlamaClient;
import com.nystax.nimba.analyzer.llm.LlmPromptBuilder;
import com.nystax.nimba.analyzer.llm.LlmResponseParser;
import com.nystax.nimba.analyzer.model.*;
import com.nystax.nimba.analyzer.parser.JobXmlParser;
import com.nystax.nimba.analyzer.parser.NimbaAnnotationResolver;
import com.nystax.nimba.analyzer.parser.PomDependencyParser;
import com.nystax.nimba.analyzer.scanner.ProjectFiles;
import com.nystax.nimba.analyzer.scanner.ProjectScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BatchAnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BatchAnalysisOrchestrator.class);

    private final ProjectScanner projectScanner;
    private final JobXmlParser jobXmlParser;
    private final PomDependencyParser pomDependencyParser;
    private final NimbaAnnotationResolver annotationResolver;
    private final JavaSourceAnalyzer javaSourceAnalyzer;
    private final LlamaClient llamaClient;
    private final LlmPromptBuilder promptBuilder;
    private final SummaryDocumentGenerator summaryGenerator;

    public BatchAnalysisOrchestrator(ProjectScanner projectScanner,
                                     JobXmlParser jobXmlParser,
                                     PomDependencyParser pomDependencyParser,
                                     NimbaAnnotationResolver annotationResolver,
                                     JavaSourceAnalyzer javaSourceAnalyzer,
                                     LlamaClient llamaClient,
                                     LlmPromptBuilder promptBuilder,
                                     SummaryDocumentGenerator summaryGenerator) {
        this.projectScanner = projectScanner;
        this.jobXmlParser = jobXmlParser;
        this.pomDependencyParser = pomDependencyParser;
        this.annotationResolver = annotationResolver;
        this.javaSourceAnalyzer = javaSourceAnalyzer;
        this.llamaClient = llamaClient;
        this.promptBuilder = promptBuilder;
        this.summaryGenerator = summaryGenerator;
    }

    public ProjectAnalysisReport analyze(String projectPath) throws IOException {
        Path root = Path.of(projectPath);
        log.info("Starting analysis of project: {}", root);

        // Phase 1: Scan project
        ProjectFiles files = projectScanner.scan(root);

        // Phase 2: Parse POM for WAS dependencies
        List<WasDependency> wasDeps = pomDependencyParser.extractWasDependencies(files.getPomXml());

        // Phase 3: Resolve @Nimba annotations
        Map<String, String> nimbaIdMap = annotationResolver.resolveAll(files.getJavaSourceFiles());

        // Phase 4: Analyze each job XML + generate per-job summary
        List<AnalysisResult> jobResults = new ArrayList<>();
        for (Path jobXml : files.getJobXmlFiles()) {
            try {
                AnalysisResult result = analyzeJob(jobXml, files.getJavaSourceFiles(), nimbaIdMap);

                // Generate job-level summary via LLM
                String jobSummary = summaryGenerator.generateJobSummary(result);
                result.setJobSummary(jobSummary);

                jobResults.add(result);
            } catch (Exception e) {
                log.error("Failed to analyze job XML {}: {}", jobXml, e.getMessage());
            }
        }

        // Phase 5: Build report
        ProjectAnalysisReport report = new ProjectAnalysisReport();
        report.setProjectPath(projectPath);
        report.setAnalyzedAt(LocalDateTime.now());
        report.setWasDependencies(wasDeps);
        report.setJobAnalyses(jobResults);

        log.info("Analysis complete: {} jobs analyzed", jobResults.size());
        return report;
    }

    private AnalysisResult analyzeJob(Path jobXml, List<Path> javaFiles, Map<String, String> nimbaIdMap) {
        JobDefinition jobDef = jobXmlParser.parse(jobXml);
        log.info("Analyzing job: {} ({} steps)", jobDef.getJobId(), jobDef.getSteps().size());

        AnalysisResult result = new AnalysisResult();
        result.setJobDefinition(jobDef);

        // Collect source files relevant to this job
        List<Path> jobSourceFiles = new ArrayList<>();

        // Analyze job listener
        if (jobDef.getJobListenerId() != null) {
            String listenerClass = nimbaIdMap.get(jobDef.getJobListenerId());
            result.setJobListenerClassName(listenerClass);
            if (listenerClass != null) {
                Path src = annotationResolver.findSourceFile(jobDef.getJobListenerId(), javaFiles, nimbaIdMap);
                if (src != null) {
                    jobSourceFiles.add(src);
                    result.setJobListenerInsight(analyzeLlm(src, listenerClass, "listener"));
                }
            }
        }

        // Analyze each step
        List<StepAnalysis> stepAnalyses = new ArrayList<>();
        for (StepDefinition step : jobDef.getSteps()) {
            StepAnalysis sa = analyzeStep(step, javaFiles, nimbaIdMap);
            stepAnalyses.add(sa);
            collectStepSourceFiles(step, sa, javaFiles, nimbaIdMap, jobSourceFiles);
        }
        result.setStepAnalyses(stepAnalyses);

        // Find BatchExitException usages only in this job's source files
        List<BatchExitUsage> exitUsages = javaSourceAnalyzer.findBatchExitUsages(jobSourceFiles);
        result.setBatchExitUsages(exitUsages);

        return result;
    }

    private void collectStepSourceFiles(StepDefinition step, StepAnalysis sa,
                                        List<Path> javaFiles, Map<String, String> nimbaIdMap,
                                        List<Path> collected) {
        // Processor source
        if (step.getProcessor() != null) {
            String procId = step.getProcessor().getProcessorId();
            String procClass = nimbaIdMap.get(procId);
            if (procClass != null) {
                Path src = annotationResolver.findSourceFile(procId, javaFiles, nimbaIdMap);
                if (src != null && !collected.contains(src)) collected.add(src);
            }
        }
        // Deserializer source
        if (sa.getDeserializerClassName() != null) {
            Path src = annotationResolver.findSourceFileByClassName(sa.getDeserializerClassName(), javaFiles);
            if (src != null && !collected.contains(src)) collected.add(src);
        }
        // Custom reader source
        if (step.getReader() != null && step.getReader().getReaderType() == com.nystax.nimba.analyzer.model.ReaderType.CUSTOM) {
            String readerClass = nimbaIdMap.get(step.getReader().getReaderId());
            if (readerClass != null) {
                Path src = annotationResolver.findSourceFile(step.getReader().getReaderId(), javaFiles, nimbaIdMap);
                if (src != null && !collected.contains(src)) collected.add(src);
            }
        }
    }

    private StepAnalysis analyzeStep(StepDefinition step, List<Path> javaFiles, Map<String, String> nimbaIdMap) {
        log.info("  Analyzing step: {} ({})", step.getStepId(), step.getStepType());

        StepAnalysis sa = new StepAnalysis();
        sa.setStepDefinition(step);

        // Extract processErrorThreshold
        if (step.getProcessor() != null && step.getProcessor().getParameters() != null) {
            sa.setProcessErrorThreshold(
                    step.getProcessor().getParameters().getOrDefault("processErrorThreshold", "1000 (default)"));
        }

        // Analyze reader
        if (step.getReader() != null) {
            ReaderDefinition reader = step.getReader();

            if (reader.getReaderType() == ReaderType.FRAMEWORK) {
                sa.setFileFormat(determineFileFormat(reader));

                // Check for deserializer in reader parameters
                String deserializerId = reader.getParameters().get("deserializer");
                if (deserializerId != null) {
                    sa.setHasDeserializer(true);
                    analyzeDeserializer(deserializerId, sa, javaFiles, nimbaIdMap);
                }
            } else {
                // Custom reader
                String readerClass = nimbaIdMap.get(reader.getReaderId());
                if (readerClass != null) {
                    Path src = annotationResolver.findSourceFile(reader.getReaderId(), javaFiles, nimbaIdMap);
                    if (src != null) {
                        sa.setReaderInsight(analyzeLlm(src, readerClass, "reader"));
                        sa.setDeserializerFunctionCalls(javaSourceAnalyzer.findAllFunctionCalls(src));

                        // Static analysis: datasource names in custom reader
                        List<String> dsNames = javaSourceAnalyzer.findDatasourceNames(src);
                        if (!dsNames.isEmpty()) {
                            sa.getDatasourceNames().addAll(dsNames);
                        }

                        // Static analysis: Nimbus function calls in custom reader
                        List<String> readerFuncs = javaSourceAnalyzer.findNimbusFunctionCalls(src);
                        if (!readerFuncs.isEmpty()) {
                            sa.getNimbusFunctions().addAll(readerFuncs);
                        }

                        // Static analysis: SQL queries in custom reader
                        List<String> readerSql = javaSourceAnalyzer.findSqlQueries(src);
                        if (!readerSql.isEmpty()) {
                            sa.getSqlQueries().addAll(readerSql);
                        }
                    }
                }
            }
        }

        // Analyze processor
        if (step.getProcessor() != null) {
            String procId = step.getProcessor().getProcessorId();
            String procClass = nimbaIdMap.get(procId);
            sa.setProcessorClassName(procClass);

            if (procClass != null) {
                Path procSource = annotationResolver.findSourceFile(procId, javaFiles, nimbaIdMap);
                if (procSource != null) {
                    // Static analysis: function calls
                    List<FunctionCallInfo> calls = javaSourceAnalyzer.findAllFunctionCalls(procSource);
                    sa.setProcessorFunctionCalls(calls);
                    sa.setProcessorMakesFunctionCalls(!calls.isEmpty());

                    // Static analysis: datasource names
                    List<String> dsNames = javaSourceAnalyzer.findDatasourceNames(procSource);
                    if (!dsNames.isEmpty()) {
                        sa.getDatasourceNames().addAll(dsNames);
                    }

                    // Static analysis: Nimbus function calls in processor
                    List<String> procFuncs = javaSourceAnalyzer.findNimbusFunctionCalls(procSource);
                    if (!procFuncs.isEmpty()) {
                        sa.getNimbusFunctions().addAll(procFuncs);
                    }

                    // Static analysis: SQL queries in processor
                    List<String> procSql = javaSourceAnalyzer.findSqlQueries(procSource);
                    if (!procSql.isEmpty()) {
                        sa.getSqlQueries().addAll(procSql);
                    }

                    // LLM analysis
                    sa.setProcessorInsight(analyzeLlm(procSource, procClass, "processor"));
                }
            }
        }

        return sa;
    }

    private void analyzeDeserializer(String deserializerId, StepAnalysis sa,
                                     List<Path> javaFiles, Map<String, String> nimbaIdMap) {
        String className;
        Path source;

        // Check if deserializerId is a fully qualified class name (contains dots)
        if (deserializerId.contains(".")) {
            className = deserializerId;
            source = annotationResolver.findSourceFileByClassName(className, javaFiles);
        } else {
            // Try @Nimba annotation lookup
            className = nimbaIdMap.get(deserializerId);
            source = (className != null)
                    ? annotationResolver.findSourceFile(deserializerId, javaFiles, nimbaIdMap)
                    : null;
        }

        sa.setDeserializerClassName(className);
        if (source != null) {
            List<FunctionCallInfo> calls = javaSourceAnalyzer.findAllFunctionCalls(source);
            sa.setDeserializerFunctionCalls(calls);
            sa.setDeserializerMakesFunctionCalls(!calls.isEmpty());
            sa.setDeserializerInsight(analyzeLlm(source, className, "deserializer"));

            // Static analysis: Nimbus function calls in deserializer
            List<String> desFuncs = javaSourceAnalyzer.findNimbusFunctionCalls(source);
            if (!desFuncs.isEmpty()) {
                sa.getNimbusFunctions().addAll(desFuncs);
            }

            // Static analysis: SQL queries in deserializer
            List<String> desSql = javaSourceAnalyzer.findSqlQueries(source);
            if (!desSql.isEmpty()) {
                sa.getSqlQueries().addAll(desSql);
            }
        }
    }

    private LlmInsight analyzeLlm(Path sourceFile, String className, String type) {
        try {
            String src = Files.readString(sourceFile);
            String response;
            switch (type) {
                case "processor":
                    response = llamaClient.analyze(LlmPromptBuilder.SYSTEM_PROMPT,
                            promptBuilder.buildProcessorPrompt(src, className));
                    return LlmResponseParser.toProcessorInsight(response, className);
                case "deserializer":
                    response = llamaClient.analyze(LlmPromptBuilder.SYSTEM_PROMPT,
                            promptBuilder.buildDeserializerPrompt(src, className));
                    return LlmResponseParser.toDeserializerInsight(response, className);
                case "reader":
                    response = llamaClient.analyze(LlmPromptBuilder.SYSTEM_PROMPT,
                            promptBuilder.buildCustomReaderPrompt(src, className));
                    return LlmResponseParser.toReaderInsight(response, className);
                case "listener":
                    response = llamaClient.analyze(LlmPromptBuilder.SYSTEM_PROMPT,
                            promptBuilder.buildJobListenerPrompt(src, className));
                    return LlmResponseParser.toListenerInsight(response, className);
                default:
                    return null;
            }
        } catch (IOException e) {
            log.warn("Failed to read source for LLM analysis: {}", sourceFile);
            return null;
        }
    }

    private String determineFileFormat(ReaderDefinition reader) {
        String filePath = reader.getParameters().get("filePath");
        if (filePath == null) return "Unknown";
        if (filePath.endsWith(".csv")) return "CSV";
        if (filePath.endsWith(".xml")) return "XML";
        if (filePath.endsWith(".json")) return "JSON";
        if (filePath.endsWith(".txt")) return "Text";
        // Framework line reader implies line-based format
        if (reader.getReaderId().contains("Line")) return "Line-based text";
        return "Determined by deserializer";
    }
}
