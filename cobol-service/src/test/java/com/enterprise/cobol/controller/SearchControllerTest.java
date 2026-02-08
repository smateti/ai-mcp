package com.enterprise.cobol.controller;

import com.enterprise.cobol.document.DependencyDocument;
import com.enterprise.cobol.document.ParagraphDocument;
import com.enterprise.cobol.document.ProgramDocument;
import com.enterprise.cobol.repository.es.DependencyDocumentRepository;
import com.enterprise.cobol.repository.es.ParagraphDocumentRepository;
import com.enterprise.cobol.repository.es.ProgramDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SearchControllerTest {

    private MockMvc mockMvc;
    private ProgramDocumentRepository programRepo;
    private ParagraphDocumentRepository paragraphRepo;
    private DependencyDocumentRepository dependencyRepo;

    @BeforeEach
    void setUp() {
        programRepo = mock(ProgramDocumentRepository.class);
        paragraphRepo = mock(ParagraphDocumentRepository.class);
        dependencyRepo = mock(DependencyDocumentRepository.class);

        SearchController controller = new SearchController(programRepo, paragraphRepo, dependencyRepo);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private ProgramDocument sampleProgram(String id, String type, String batchRunId) {
        return ProgramDocument.builder()
                .programId(id)
                .programName(id)
                .programType(type)
                .lineCount(100)
                .paragraphCount(5)
                .batchRunId(batchRunId)
                .build();
    }

    @Test
    void testGetPrograms() throws Exception {
        ProgramDocument p1 = sampleProgram("PROG1", "CICS", "run-1");
        when(programRepo.findAll()).thenReturn(List.of(p1));

        mockMvc.perform(get("/api/search/programs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].programName").value("PROG1"));
    }

    @Test
    void testGetProgramsFilteredByType() throws Exception {
        ProgramDocument p1 = sampleProgram("PROG1", "CICS", "run-1");
        when(programRepo.findByProgramType("CICS")).thenReturn(new ArrayList<>(List.of(p1)));

        mockMvc.perform(get("/api/search/programs").param("type", "CICS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].programType").value("CICS"));

        verify(programRepo).findByProgramType("CICS");
    }

    @Test
    void testGetProgramsFilteredByBatchRunId() throws Exception {
        ProgramDocument p1 = sampleProgram("PROG1", "CICS", "run-abc");
        when(programRepo.findByBatchRunId("run-abc")).thenReturn(new ArrayList<>(List.of(p1)));

        mockMvc.perform(get("/api/search/programs").param("batchRunId", "run-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].programName").value("PROG1"));

        verify(programRepo).findByBatchRunId("run-abc");
    }

    @Test
    void testGetProgramsFilteredByBatchRunIdAndType() throws Exception {
        ProgramDocument p1 = sampleProgram("PROG1", "BATCH", "run-abc");
        when(programRepo.findByBatchRunIdAndProgramType("run-abc", "BATCH")).thenReturn(new ArrayList<>(List.of(p1)));

        mockMvc.perform(get("/api/search/programs")
                        .param("batchRunId", "run-abc")
                        .param("type", "BATCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].programType").value("BATCH"));

        verify(programRepo).findByBatchRunIdAndProgramType("run-abc", "BATCH");
    }

    @Test
    void testGetProgramsWithSearchQuery() throws Exception {
        ProgramDocument p1 = sampleProgram("CBTRN01C", "CICS", "run-1");
        p1.setBusinessSummary("Processes credit card transactions");
        when(programRepo.findAll()).thenReturn(List.of(p1));

        mockMvc.perform(get("/api/search/programs").param("query", "CBTRN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].programName").value("CBTRN01C"));
    }

    @Test
    void testGetProgramsSearchQueryNoMatch() throws Exception {
        ProgramDocument p1 = sampleProgram("CBTRN01C", "CICS", "run-1");
        when(programRepo.findAll()).thenReturn(List.of(p1));

        mockMvc.perform(get("/api/search/programs").param("query", "ZZZZZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testGetProgramById() throws Exception {
        ProgramDocument p1 = sampleProgram("PROG1", "CICS", "run-1");
        p1.setBusinessSummary("A CICS transaction program");
        when(programRepo.findById("PROG1")).thenReturn(Optional.of(p1));

        mockMvc.perform(get("/api/search/programs/PROG1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programName").value("PROG1"))
                .andExpect(jsonPath("$.businessSummary").value("A CICS transaction program"));
    }

    @Test
    void testGetProgramByIdNotFound() throws Exception {
        when(programRepo.findById("MISSING")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/search/programs/MISSING"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetParagraphs() throws Exception {
        ParagraphDocument para = ParagraphDocument.builder()
                .paragraphId("PROG1::MAIN-PARA")
                .programId("PROG1")
                .paragraphName("MAIN-PARA")
                .startLine(10)
                .endLine(20)
                .build();
        when(paragraphRepo.findByProgramId("PROG1")).thenReturn(new ArrayList<>(List.of(para)));

        mockMvc.perform(get("/api/search/programs/PROG1/paragraphs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paragraphName").value("MAIN-PARA"));
    }

    @Test
    void testGetDependencies() throws Exception {
        DependencyDocument dep = DependencyDocument.builder()
                .dependencyId("dep-1")
                .programId("PROG1")
                .dependencyType("CALL")
                .targetName("SUBPROG1")
                .build();
        when(dependencyRepo.findByProgramId("PROG1")).thenReturn(List.of(dep));

        mockMvc.perform(get("/api/search/programs/PROG1/dependencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetName").value("SUBPROG1"));
    }

    @Test
    void testGetDependenciesFilteredByType() throws Exception {
        DependencyDocument dep = DependencyDocument.builder()
                .dependencyId("dep-1")
                .programId("PROG1")
                .dependencyType("CICS")
                .targetName("SEND")
                .build();
        when(dependencyRepo.findByProgramIdAndDependencyType("PROG1", "CICS")).thenReturn(List.of(dep));

        mockMvc.perform(get("/api/search/programs/PROG1/dependencies").param("type", "CICS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dependencyType").value("CICS"));
    }

    @Test
    void testGetStats() throws Exception {
        List<ProgramDocument> programs = List.of(
                sampleProgram("P1", "CICS", "r1"),
                sampleProgram("P2", "BATCH", "r1"),
                sampleProgram("P3", "SUBROUTINE", "r1")
        );
        when(programRepo.findAll()).thenReturn(programs);

        mockMvc.perform(get("/api/search/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrograms").value(3))
                .andExpect(jsonPath("$.cicsPrograms").value(1))
                .andExpect(jsonPath("$.batchPrograms").value(1))
                .andExpect(jsonPath("$.subroutinePrograms").value(1));
    }

    @Test
    void testGetStatsWithBatchRunId() throws Exception {
        List<ProgramDocument> programs = List.of(sampleProgram("P1", "CICS", "run-abc"));
        when(programRepo.findByBatchRunId("run-abc")).thenReturn(programs);

        mockMvc.perform(get("/api/search/stats").param("batchRunId", "run-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrograms").value(1));

        verify(programRepo).findByBatchRunId("run-abc");
        verify(programRepo, never()).findAll();
    }

    @Test
    void testGetDependencyGraph() throws Exception {
        ProgramDocument p1 = sampleProgram("CALLER", "CICS", "r1");
        p1.setCalledPrograms(List.of("SUBPROG"));
        ProgramDocument p2 = sampleProgram("SUBPROG", "SUBROUTINE", "r1");
        when(programRepo.findAll()).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/search/dependency-graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.edges.length()").value(1));
    }

    @Test
    void testGetDependencyGraphWithBatchRunId() throws Exception {
        ProgramDocument p1 = sampleProgram("CALLER", "CICS", "run-abc");
        p1.setCalledPrograms(List.of("EXTERNAL"));
        when(programRepo.findByBatchRunId("run-abc")).thenReturn(List.of(p1));

        mockMvc.perform(get("/api/search/dependency-graph").param("batchRunId", "run-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(2)) // CALLER + EXTERNAL
                .andExpect(jsonPath("$.edges.length()").value(1));

        verify(programRepo).findByBatchRunId("run-abc");
    }

    @Test
    void testGetDependencyGraphExternalNodes() throws Exception {
        ProgramDocument p1 = sampleProgram("CALLER", "CICS", "r1");
        p1.setCalledPrograms(List.of("UNKNOWN_EXT"));
        when(programRepo.findAll()).thenReturn(List.of(p1));

        mockMvc.perform(get("/api/search/dependency-graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[?(@.id == 'UNKNOWN_EXT')].type").value("EXTERNAL"));
    }
}
