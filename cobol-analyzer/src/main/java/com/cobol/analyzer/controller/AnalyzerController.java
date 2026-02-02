package com.cobol.analyzer.controller;

import com.cobol.analyzer.model.AnalysisResult;
import com.cobol.analyzer.service.LlmAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
public class AnalyzerController {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerController.class);

    private final LlmAnalysisService llmAnalysisService;

    public AnalyzerController(LlmAnalysisService llmAnalysisService) {
        this.llmAnalysisService = llmAnalysisService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping("/analyze")
    @ResponseBody
    public ResponseEntity<AnalysisResult> analyzeFile(@RequestParam("file") MultipartFile file) {
        try {
            log.info("Analyzing uploaded file: {}", file.getOriginalFilename());

            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            AnalysisResult result = llmAnalysisService.analyzeCobolProgram(
                    file.getOriginalFilename(),
                    content
            );

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error analyzing file: {}", e.getMessage());
            AnalysisResult errorResult = new AnalysisResult();
            errorResult.setFileName(file.getOriginalFilename());
            errorResult.setOverallSummary("Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    @PostMapping("/analyze/text")
    @ResponseBody
    public ResponseEntity<AnalysisResult> analyzeText(@RequestBody Map<String, String> request) {
        try {
            String code = request.get("code");
            String fileName = request.getOrDefault("fileName", "uploaded.cbl");

            log.info("Analyzing pasted code: {} chars", code.length());

            AnalysisResult result = llmAnalysisService.analyzeCobolProgram(fileName, code);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error analyzing code: {}", e.getMessage());
            AnalysisResult errorResult = new AnalysisResult();
            errorResult.setOverallSummary("Error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResult);
        }
    }

    @GetMapping("/api/health")
    @ResponseBody
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }
}
