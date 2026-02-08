package com.enterprise.cobol.controller;

import com.enterprise.cobol.document.DependencyDocument;
import com.enterprise.cobol.document.ParagraphDocument;
import com.enterprise.cobol.document.ProgramDocument;
import com.enterprise.cobol.repository.es.DependencyDocumentRepository;
import com.enterprise.cobol.repository.es.ParagraphDocumentRepository;
import com.enterprise.cobol.repository.es.ProgramDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final ProgramDocumentRepository programRepo;
    private final ParagraphDocumentRepository paragraphRepo;
    private final DependencyDocumentRepository dependencyRepo;

    @GetMapping("/programs")
    public ResponseEntity<List<ProgramDocument>> getPrograms(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String batchRunId) {
        List<ProgramDocument> programs;

        if (batchRunId != null && !batchRunId.isEmpty()) {
            if (type != null && !type.isEmpty() && !"all".equalsIgnoreCase(type)) {
                programs = programRepo.findByBatchRunIdAndProgramType(batchRunId, type.toUpperCase());
            } else {
                programs = programRepo.findByBatchRunId(batchRunId);
            }
        } else if (type != null && !type.isEmpty() && !"all".equalsIgnoreCase(type)) {
            programs = programRepo.findByProgramType(type.toUpperCase());
        } else {
            programs = StreamSupport.stream(programRepo.findAll().spliterator(), false)
                    .collect(Collectors.toList());
        }

        if (query != null && !query.isEmpty()) {
            String q = query.toUpperCase();
            programs = programs.stream()
                    .filter(p -> p.getProgramName().contains(q) ||
                            (p.getBusinessSummary() != null && p.getBusinessSummary().toUpperCase().contains(q)))
                    .collect(Collectors.toList());
        }

        programs.sort(Comparator.comparing(ProgramDocument::getProgramName));
        return ResponseEntity.ok(programs);
    }

    @GetMapping("/programs/{id}")
    public ResponseEntity<ProgramDocument> getProgram(@PathVariable String id) {
        return programRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/programs/{id}/paragraphs")
    public ResponseEntity<List<ParagraphDocument>> getParagraphs(@PathVariable String id) {
        List<ParagraphDocument> paragraphs = paragraphRepo.findByProgramId(id);
        paragraphs.sort(Comparator.comparingInt(ParagraphDocument::getStartLine));
        return ResponseEntity.ok(paragraphs);
    }

    @GetMapping("/programs/{id}/dependencies")
    public ResponseEntity<List<DependencyDocument>> getDependencies(
            @PathVariable String id,
            @RequestParam(required = false) String type) {
        List<DependencyDocument> deps;
        if (type != null && !type.isEmpty()) {
            deps = dependencyRepo.findByProgramIdAndDependencyType(id, type.toUpperCase());
        } else {
            deps = dependencyRepo.findByProgramId(id);
        }
        return ResponseEntity.ok(deps);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam(required = false) String batchRunId) {
        List<ProgramDocument> allPrograms;

        if (batchRunId != null && !batchRunId.isEmpty()) {
            allPrograms = programRepo.findByBatchRunId(batchRunId);
        } else {
            allPrograms = StreamSupport
                    .stream(programRepo.findAll().spliterator(), false)
                    .collect(Collectors.toList());
        }

        long totalPrograms = allPrograms.size();
        long cicsCount = allPrograms.stream().filter(p -> "CICS".equals(p.getProgramType())).count();
        long batchCount = allPrograms.stream().filter(p -> "BATCH".equals(p.getProgramType())).count();
        long subCount = allPrograms.stream().filter(p -> "SUBROUTINE".equals(p.getProgramType())).count();
        long totalLines = allPrograms.stream().mapToLong(ProgramDocument::getLineCount).sum();
        long totalParagraphs = allPrograms.stream().mapToLong(ProgramDocument::getParagraphCount).sum();

        long withSummary = allPrograms.stream()
                .filter(p -> p.getBusinessSummary() != null && !p.getBusinessSummary().isEmpty())
                .count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalPrograms", totalPrograms);
        stats.put("cicsPrograms", cicsCount);
        stats.put("batchPrograms", batchCount);
        stats.put("subroutinePrograms", subCount);
        stats.put("totalLines", totalLines);
        stats.put("totalParagraphs", totalParagraphs);
        stats.put("analyzedPrograms", withSummary);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/dependency-graph")
    public ResponseEntity<Map<String, Object>> getDependencyGraph(
            @RequestParam(required = false) String batchRunId) {
        List<ProgramDocument> allPrograms;

        if (batchRunId != null && !batchRunId.isEmpty()) {
            allPrograms = programRepo.findByBatchRunId(batchRunId);
        } else {
            allPrograms = StreamSupport
                    .stream(programRepo.findAll().spliterator(), false)
                    .collect(Collectors.toList());
        }

        Set<String> analyzedNames = allPrograms.stream()
                .map(ProgramDocument::getProgramName).collect(Collectors.toSet());

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        for (ProgramDocument prog : allPrograms) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", prog.getProgramName());
            node.put("label", prog.getProgramName());
            node.put("type", prog.getProgramType());
            node.put("lineCount", prog.getLineCount());
            nodes.add(node);

            if (prog.getCalledPrograms() != null) {
                for (String called : prog.getCalledPrograms()) {
                    if (!analyzedNames.contains(called)) {
                        Map<String, Object> extNode = new HashMap<>();
                        extNode.put("id", called);
                        extNode.put("label", called);
                        extNode.put("type", "EXTERNAL");
                        extNode.put("lineCount", 0);
                        nodes.add(extNode);
                        analyzedNames.add(called);
                    }

                    Map<String, Object> edge = new HashMap<>();
                    edge.put("source", prog.getProgramName());
                    edge.put("target", called);
                    edge.put("type", "CALL");
                    edges.add(edge);
                }
            }
        }

        return ResponseEntity.ok(Map.of("nodes", nodes, "edges", edges));
    }
}
