package com.nystax.nimba.analyzer.cli;

import com.nystax.nimba.analyzer.model.ProjectAnalysisReport;
import com.nystax.nimba.analyzer.service.BatchAnalysisOrchestrator;
import com.nystax.nimba.analyzer.service.ReportGenerator;
import com.nystax.nimba.analyzer.service.SummaryDocumentGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class CliRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CliRunner.class);

    private final BatchAnalysisOrchestrator orchestrator;
    private final ReportGenerator reportGenerator;
    private final SummaryDocumentGenerator summaryGenerator;

    public CliRunner(BatchAnalysisOrchestrator orchestrator,
                     ReportGenerator reportGenerator,
                     SummaryDocumentGenerator summaryGenerator) {
        this.orchestrator = orchestrator;
        this.reportGenerator = reportGenerator;
        this.summaryGenerator = summaryGenerator;
    }

    @Override
    public void run(String... args) throws Exception {
        String projectPath = null;
        for (int i = 0; i < args.length; i++) {
            if ("--analyze".equals(args[i]) && i + 1 < args.length) {
                projectPath = args[i + 1];
                break;
            }
        }

        if (projectPath == null) return;

        System.out.println("Nimba Batch Analyzer - CLI Mode");
        System.out.println("Analyzing project: " + projectPath);
        System.out.println();

        try {
            ProjectAnalysisReport report = orchestrator.analyze(projectPath);

            // Print to console
            reportGenerator.printConsole(report);

            // Write detailed analysis report
            Path analysisPath = reportGenerator.writeMarkdownReport(report);
            System.out.println("Analysis report saved to: " + analysisPath.toAbsolutePath());

            // Write summary document
            Path summaryPath = summaryGenerator.writeSummaryDocument(report);
            System.out.println("Summary document saved to: " + summaryPath.toAbsolutePath());

        } catch (Exception e) {
            System.err.println("Analysis failed: " + e.getMessage());
            log.error("CLI analysis failed", e);
            System.exit(1);
        }
    }
}
