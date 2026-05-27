package com.example.brdagent.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.brdagent.client.LlamaChatRequest;
import com.example.brdagent.client.LlamaChatResponse;
import com.example.brdagent.client.LlamaClient;
import com.example.brdagent.client.LlamaMessage;
import com.example.brdagent.config.PromptLoader;
import com.example.brdagent.scanner.SourceFile;

// PROVENANCE: integration test for BrdExtractionAgent — golden slice step 10, mock LlamaClient

@ExtendWith(MockitoExtension.class)
class BrdExtractionAgentTest {

    /** Canned partial BRD JSON matching the brd-llama.md schema (all fields present). */
    private static final String CANNED_PARTIAL_BRD = """
            {
              "source_file": "legacy/programs/CUSTUPD.prg",
              "functional_requirements": [
                {
                  "id": "FR-temp-001",
                  "title": "Update customer credit limit",
                  "description": "Allows updating an existing customer's credit limit.",
                  "trigger": "Called with customer ID and new credit limit",
                  "business_rules": [
                    "Customer must exist and not be soft-deleted",
                    "Credit limits above 50000 require approval when credit score is below 700"
                  ],
                  "source_snippet": "PROCEDURE UpdateCustomerCredit ... ENDPROC"
                }
              ],
              "data_entities": [
                {
                  "name": "Customer",
                  "fields": [
                    {"name": "cust_id", "type": "identifier", "required": true, "validation": null},
                    {"name": "credit_lim", "type": "number", "required": false, "validation": null}
                  ],
                  "sql_queries": [
                    {"sql": "SELECT customer WHERE cust_id = pCustID", "purpose": "Locate customer record by ID"}
                  ],
                  "source_snippet": "SELECT customer ... REPLACE credit_lim"
                }
              ],
              "business_rules": [
                {
                  "id": "BR-temp-001",
                  "rule": "Credit limit increases above 50000 for low-credit-score customers require approval.",
                  "source_snippet": "IF pNewLimit > 50000 AND customer.cred_score < 700"
                }
              ],
              "ui_elements": [
                {
                  "id": "UI-temp-001",
                  "name": "Credit Limit Update Dialog",
                  "purpose": "Displays approval message when credit limit exceeds threshold",
                  "form_type": "modal dialog",
                  "form_class": null,
                  "library_file": null,
                  "modal": true,
                  "fields_displayed": ["credit_lim", "cred_score"],
                  "user_actions": ["Approve", "Cancel"],
                  "controls": [
                    {"name": "btnApprove", "type": "button", "action": "Approve credit limit change"}
                  ],
                  "components": [],
                  "source_snippet": "WAIT WINDOW \\"Approval required\\" TIMEOUT 3"
                }
              ],
              "menu_items": [],
              "reference_data_usage": [
                {
                  "code": "crd",
                  "context": "Credit score category used to determine approval threshold",
                  "source_snippet": "customer.cred_score < 700"
                }
              ],
              "security_checks": [
                {
                  "resource": "Customer credit limit update",
                  "check_expression": "oUser.oSecurity.GetAccess('CUSTCREDIT')",
                  "description": "User must have CUSTCREDIT access to modify credit limits",
                  "source_snippet": "oUser.oSecurity.GetAccess('CUSTCREDIT') >= 2"
                }
              ],
              "open_questions": [
                "Is there a separate approval workflow beyond the wait window?"
              ]
            }
            """;

    @Mock
    private LlamaClient llamaClient;

    @TempDir
    private Path tempOutputDir;

    private BrdExtractionAgent agent;

    @BeforeEach
    void setUp() {
        PromptLoader promptLoader = new PromptLoader("You are a test system prompt.");
        agent = new BrdExtractionAgent(
                llamaClient, promptLoader,
                "test-model", 0.1, 0.9, 4096, true,
                tempOutputDir.toString()
        );
    }

    @Test
    void happyPathExtractsPartialBrd() {
        LlamaChatResponse response = buildCannedResponse(CANNED_PARTIAL_BRD);
        when(llamaClient.chatCompletion(any(LlamaChatRequest.class))).thenReturn(response);

        SourceFile file = new SourceFile(
                "programs/CUSTUPD.prg", "prg",
                "PROCEDURE UpdateCustomerCredit\n  PARAMETERS pCustID\nENDPROC\n");

        ExtractionResult result = agent.extractFromFile(file);

        assertTrue(result.isSuccess(), "Extraction should succeed");
        assertNotNull(result.getPartialBrd(), "PartialBrd should be populated");
        assertEquals("legacy/programs/CUSTUPD.prg", result.getPartialBrd().getSourceFile());
    }

    @Test
    void happyPathPopulatesFunctionalRequirements() {
        LlamaChatResponse response = buildCannedResponse(CANNED_PARTIAL_BRD);
        when(llamaClient.chatCompletion(any())).thenReturn(response);

        SourceFile file = new SourceFile("programs/CUSTUPD.prg", "prg", "content");
        ExtractionResult result = agent.extractFromFile(file);

        PartialBrd brd = result.getPartialBrd();
        assertEquals(1, brd.getFunctionalRequirements().size());
        assertEquals("FR-temp-001", brd.getFunctionalRequirements().get(0).getId());
        assertEquals("Update customer credit limit", brd.getFunctionalRequirements().get(0).getTitle());
    }

    @Test
    void happyPathPopulatesDataEntities() {
        LlamaChatResponse response = buildCannedResponse(CANNED_PARTIAL_BRD);
        when(llamaClient.chatCompletion(any())).thenReturn(response);

        SourceFile file = new SourceFile("programs/CUSTUPD.prg", "prg", "content");
        ExtractionResult result = agent.extractFromFile(file);

        PartialBrd brd = result.getPartialBrd();
        assertEquals(1, brd.getDataEntities().size());
        assertEquals("Customer", brd.getDataEntities().get(0).getName());
        assertEquals(2, brd.getDataEntities().get(0).getFields().size());
        // sql_queries inside data_entities
        assertEquals(1, brd.getDataEntities().get(0).getSqlQueries().size());
        assertEquals("Locate customer record by ID", brd.getDataEntities().get(0).getSqlQueries().get(0).getPurpose());
    }

    @Test
    void happyPathPopulatesBusinessRules() {
        LlamaChatResponse response = buildCannedResponse(CANNED_PARTIAL_BRD);
        when(llamaClient.chatCompletion(any())).thenReturn(response);

        SourceFile file = new SourceFile("programs/CUSTUPD.prg", "prg", "content");
        ExtractionResult result = agent.extractFromFile(file);

        PartialBrd brd = result.getPartialBrd();
        assertEquals(1, brd.getBusinessRules().size());
        assertEquals("BR-temp-001", brd.getBusinessRules().get(0).getId());
    }

    @Test
    void happyPathPopulatesUiElements() {
        LlamaChatResponse response = buildCannedResponse(CANNED_PARTIAL_BRD);
        when(llamaClient.chatCompletion(any())).thenReturn(response);

        SourceFile file = new SourceFile("programs/CUSTUPD.prg", "prg", "content");
        ExtractionResult result = agent.extractFromFile(file);

        PartialBrd brd = result.getPartialBrd();
        assertEquals(1, brd.getUiElements().size());
        PartialBrd.UiElement ui = brd.getUiElements().get(0);
        assertEquals("UI-temp-001", ui.getId());
        assertEquals("modal dialog", ui.getFormType());
        assertTrue(ui.getModal());
        assertEquals(1, ui.getControls().size());
        assertEquals("button", ui.getControls().get(0).getType());
    }

    @Test
    void happyPathPopulatesReferenceDataUsage() {
        LlamaChatResponse response = buildCannedResponse(CANNED_PARTIAL_BRD);
        when(llamaClient.chatCompletion(any())).thenReturn(response);

        SourceFile file = new SourceFile("programs/CUSTUPD.prg", "prg", "content");
        ExtractionResult result = agent.extractFromFile(file);

        PartialBrd brd = result.getPartialBrd();
        assertEquals(1, brd.getReferenceDataUsage().size());
        assertEquals("crd", brd.getReferenceDataUsage().get(0).getCode());
    }

    @Test
    void happyPathPopulatesSecurityChecks() {
        LlamaChatResponse response = buildCannedResponse(CANNED_PARTIAL_BRD);
        when(llamaClient.chatCompletion(any())).thenReturn(response);

        SourceFile file = new SourceFile("programs/CUSTUPD.prg", "prg", "content");
        ExtractionResult result = agent.extractFromFile(file);

        PartialBrd brd = result.getPartialBrd();
        assertEquals(1, brd.getSecurityChecks().size());
        assertEquals("Customer credit limit update", brd.getSecurityChecks().get(0).getResource());
        assertNotNull(brd.getSecurityChecks().get(0).getCheckExpression());
    }

    @Test
    void happyPathMenuItemsEmptyArray() {
        LlamaChatResponse response = buildCannedResponse(CANNED_PARTIAL_BRD);
        when(llamaClient.chatCompletion(any())).thenReturn(response);

        SourceFile file = new SourceFile("programs/CUSTUPD.prg", "prg", "content");
        ExtractionResult result = agent.extractFromFile(file);

        PartialBrd brd = result.getPartialBrd();
        assertNotNull(brd.getMenuItems());
        assertTrue(brd.getMenuItems().isEmpty());
    }

    @Test
    void happyPathPopulatesOpenQuestions() {
        LlamaChatResponse response = buildCannedResponse(CANNED_PARTIAL_BRD);
        when(llamaClient.chatCompletion(any())).thenReturn(response);

        SourceFile file = new SourceFile("programs/CUSTUPD.prg", "prg", "content");
        ExtractionResult result = agent.extractFromFile(file);

        PartialBrd brd = result.getPartialBrd();
        assertEquals(1, brd.getOpenQuestions().size());
        assertTrue(brd.getOpenQuestions().get(0).contains("approval workflow"));
    }

    @Test
    void happyPathWritesPartialBrdToDisk() {
        LlamaChatResponse response = buildCannedResponse(CANNED_PARTIAL_BRD);
        when(llamaClient.chatCompletion(any())).thenReturn(response);

        SourceFile file = new SourceFile("programs/CUSTUPD.prg", "prg", "content");
        agent.extractFromFile(file);

        Path expected = tempOutputDir.resolve("llama-parts/programs_CUSTUPD.json");
        assertTrue(Files.exists(expected), "Partial BRD file should be written to disk");
    }

    @Test
    void emptyContentReturnsFailure() {
        LlamaChatResponse response = buildCannedResponse("");
        when(llamaClient.chatCompletion(any())).thenReturn(response);

        SourceFile file = new SourceFile("programs/EMPTY.prg", "prg", "* empty");
        ExtractionResult result = agent.extractFromFile(file);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("empty"));
    }

    @Test
    void nullContentReturnsFailure() {
        LlamaChatResponse response = buildCannedResponse(null);
        when(llamaClient.chatCompletion(any())).thenReturn(response);

        SourceFile file = new SourceFile("programs/NULL.prg", "prg", "* null");
        ExtractionResult result = agent.extractFromFile(file);

        assertFalse(result.isSuccess());
    }

    @Test
    void malformedJsonReturnsFailure() {
        LlamaChatResponse response = buildCannedResponse("{ this is not valid json }}}");
        when(llamaClient.chatCompletion(any())).thenReturn(response);

        SourceFile file = new SourceFile("programs/BAD.prg", "prg", "* bad");
        ExtractionResult result = agent.extractFromFile(file);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("JSON parse error")
                || result.getErrorMessage().contains("parse"));
    }

    @Test
    void jsonFencesAreStrippedBeforeParsing() {
        String fencedJson = "```json\n" + CANNED_PARTIAL_BRD.trim() + "\n```";
        LlamaChatResponse response = buildCannedResponse(fencedJson);
        when(llamaClient.chatCompletion(any())).thenReturn(response);

        SourceFile file = new SourceFile("programs/FENCED.prg", "prg", "content");
        ExtractionResult result = agent.extractFromFile(file);

        assertTrue(result.isSuccess(), "Should succeed after stripping fences");
    }

    @Test
    void llamaClientExceptionReturnsFailure() {
        when(llamaClient.chatCompletion(any())).thenThrow(
                new jakarta.ws.rs.ProcessingException("Connection refused"));

        SourceFile file = new SourceFile("programs/DOWN.prg", "prg", "content");
        ExtractionResult result = agent.extractFromFile(file);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Connection refused"));
    }

    @Test
    void stripJsonFencesWithCodeFence() {
        String input = "```json\n{\"key\": \"value\"}\n```";
        String result = agent.stripJsonFences(input);
        assertEquals("{\"key\": \"value\"}", result);
    }

    @Test
    void stripJsonFencesWithPlainFence() {
        String input = "```\n{\"key\": \"value\"}\n```";
        String result = agent.stripJsonFences(input);
        assertEquals("{\"key\": \"value\"}", result);
    }

    @Test
    void stripJsonFencesPassesThroughPlainJson() {
        String input = "{\"key\": \"value\"}";
        String result = agent.stripJsonFences(input);
        assertEquals("{\"key\": \"value\"}", result);
    }

    // --- helpers ---

    private LlamaChatResponse buildCannedResponse(String content) {
        LlamaMessage message = new LlamaMessage("assistant", content);

        LlamaChatResponse.Choice choice = new LlamaChatResponse.Choice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setFinishReason("stop");

        LlamaChatResponse.Usage usage = new LlamaChatResponse.Usage();
        usage.setPromptTokens(100);
        usage.setCompletionTokens(200);
        usage.setTotalTokens(300);

        LlamaChatResponse response = new LlamaChatResponse();
        response.setId("chatcmpl-test");
        response.setObject("chat.completion");
        response.setCreated(1234567890L);
        response.setModel("test-model");
        response.setChoices(List.of(choice));
        response.setUsage(usage);

        return response;
    }
}
