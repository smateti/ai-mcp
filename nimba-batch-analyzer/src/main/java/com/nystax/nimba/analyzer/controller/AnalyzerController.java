package com.nystax.nimba.analyzer.controller;

import com.nystax.nimba.analyzer.model.ProjectAnalysisReport;
import com.nystax.nimba.analyzer.service.BatchAnalysisOrchestrator;
import com.nystax.nimba.analyzer.service.ReportGenerator;
import com.nystax.nimba.analyzer.service.SummaryDocumentGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

@RestController
@RequestMapping("/api")
public class AnalyzerController {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerController.class);

    private final BatchAnalysisOrchestrator orchestrator;
    private final ReportGenerator reportGenerator;
    private final SummaryDocumentGenerator summaryGenerator;

    public AnalyzerController(BatchAnalysisOrchestrator orchestrator,
                              ReportGenerator reportGenerator,
                              SummaryDocumentGenerator summaryGenerator) {
        this.orchestrator = orchestrator;
        this.reportGenerator = reportGenerator;
        this.summaryGenerator = summaryGenerator;
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponse> analyze(@RequestBody AnalyzeRequest request) {
        log.info("REST: Analyzing project: {}", request.getProjectPath());
        AnalyzeResponse response = new AnalyzeResponse();

        try {
            ProjectAnalysisReport report = orchestrator.analyze(request.getProjectPath());

            Path analysisPath = reportGenerator.writeMarkdownReport(report);
            Path summaryPath = summaryGenerator.writeSummaryDocument(report);

            response.setSuccess(true);
            response.setReport(report);
            response.setAnalysisReportPath(analysisPath.toAbsolutePath().toString());
            response.setSummaryDocumentPath(summaryPath.toAbsolutePath().toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Analysis failed: {}", e.getMessage(), e);
            response.setSuccess(false);
            response.setError(e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
