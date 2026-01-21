package com.naag.categoryadmin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.naag.categoryadmin.client.ChatAppClient;
import com.naag.categoryadmin.client.RagServiceClient;
import com.naag.categoryadmin.client.ToolRegistryClient;
import com.naag.categoryadmin.service.AuditLogService;
import com.naag.categoryadmin.service.CategoryService;
import com.naag.categoryadmin.service.DocumentParserService;
import com.naag.categoryadmin.service.SetupDataInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminController bulk edit functionality.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerBulkEditTest {

    @Mock
    private CategoryService categoryService;

    @Mock
    private ToolRegistryClient toolRegistryClient;

    @Mock
    private RagServiceClient ragServiceClient;

    @Mock
    private DocumentParserService documentParserService;

    @Mock
    private SetupDataInitializer setupDataInitializer;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ChatAppClient chatAppClient;

    @InjectMocks
    private AdminController adminController;

    private ObjectMapper objectMapper;
    private JsonNode testToolJson;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Create test tool JSON
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("id", 1);
        tool.put("toolId", "test-tool");
        tool.put("name", "Test Tool");
        tool.put("description", "Test description");
        tool.put("baseUrl", "http://localhost:8080");
        tool.put("path", "/api/test");
        tool.put("httpMethod", "GET");

        // Add parameters
        ArrayNode parameters = tool.putArray("parameters");
        ObjectNode param1 = parameters.addObject();
        param1.put("id", 100);
        param1.put("name", "param1");
        param1.put("type", "string");
        param1.put("required", true);
        param1.put("nestingLevel", 0);
        param1.put("description", "First parameter");
        param1.putNull("humanReadableDescription");
        param1.putNull("example");
        param1.putNull("enumValues");
        param1.putArray("nestedParameters");

        ObjectNode param2 = parameters.addObject();
        param2.put("id", 101);
        param2.put("name", "param2");
        param2.put("type", "integer");
        param2.put("required", false);
        param2.put("nestingLevel", 0);
        param2.put("description", "Second parameter");
        param2.put("humanReadableDescription", "A number parameter");
        param2.put("example", "42");
        param2.putNull("enumValues");
        param2.putArray("nestedParameters");

        // Add responses
        ArrayNode responses = tool.putArray("responses");
        ObjectNode response = responses.addObject();
        response.put("id", 200);
        response.put("statusCode", "200");
        response.put("description", "Success");

        ArrayNode responseParams = response.putArray("parameters");
        ObjectNode respParam = responseParams.addObject();
        respParam.put("id", 300);
        respParam.put("name", "result");
        respParam.put("type", "object");
        respParam.put("nestingLevel", 0);
        respParam.put("description", "Result object");
        respParam.putNull("humanReadableDescription");
        respParam.putNull("example");
        respParam.putArray("nestedParameters");

        testToolJson = tool;
    }

    @Nested
    @DisplayName("GET /tools/{id}/bulk-edit Tests")
    class BulkEditFormTests {

        @Test
        @DisplayName("Should load tool data for bulk edit form")
        void shouldLoadToolDataForBulkEditForm() {
            when(toolRegistryClient.getToolDetails("test-tool")).thenReturn(Optional.of(testToolJson));

            Model model = new ConcurrentModel();
            String viewName = adminController.bulkEditParametersForm("test-tool", model);

            assertThat(viewName).isEqualTo("tools/bulk-edit-params");
            assertThat(model.getAttribute("activePage")).isEqualTo("tools");
            assertThat(model.getAttribute("tool")).isNotNull();
            verify(toolRegistryClient).getToolDetails("test-tool");
        }

        @Test
        @DisplayName("Should set error when tool not found")
        void shouldSetErrorWhenToolNotFound() {
            when(toolRegistryClient.getToolDetails("nonexistent")).thenReturn(Optional.empty());

            Model model = new ConcurrentModel();
            String viewName = adminController.bulkEditParametersForm("nonexistent", model);

            assertThat(viewName).isEqualTo("tools/bulk-edit-params");
            assertThat(model.getAttribute("error")).isNotNull();
            assertThat(model.getAttribute("error").toString()).contains("not found");
        }

        @Test
        @DisplayName("Should handle exception when loading tool")
        void shouldHandleExceptionWhenLoadingTool() {
            when(toolRegistryClient.getToolDetails("error-tool"))
                    .thenThrow(new RuntimeException("Connection failed"));

            Model model = new ConcurrentModel();
            String viewName = adminController.bulkEditParametersForm("error-tool", model);

            assertThat(viewName).isEqualTo("tools/bulk-edit-params");
            assertThat(model.getAttribute("error")).isNotNull();
            assertThat(model.getAttribute("error").toString()).contains("Failed to load tool");
        }

        @Test
        @DisplayName("Should convert tool JSON to Map for template")
        void shouldConvertToolJsonToMapForTemplate() {
            when(toolRegistryClient.getToolDetails("test-tool")).thenReturn(Optional.of(testToolJson));

            Model model = new ConcurrentModel();
            adminController.bulkEditParametersForm("test-tool", model);

            Object tool = model.getAttribute("tool");
            assertThat(tool).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> toolMap = (Map<String, Object>) tool;
            assertThat(toolMap.get("toolId")).isEqualTo("test-tool");
            assertThat(toolMap.get("name")).isEqualTo("Test Tool");
        }
    }

    @Nested
    @DisplayName("POST /tools/{id}/bulk-edit Tests")
    class BulkEditSubmitTests {

        @Test
        @DisplayName("Should update multiple input parameters")
        void shouldUpdateMultipleInputParameters() {
            Map<String, String> params = new HashMap<>();
            params.put("inputParams[0].id", "100");
            params.put("inputParams[0].name", "param1");
            params.put("inputParams[0].humanReadableDescription", "Updated description 1");
            params.put("inputParams[0].example", "example1");
            params.put("inputParams[0].enumValues", "");
            params.put("inputParams[1].id", "101");
            params.put("inputParams[1].name", "param2");
            params.put("inputParams[1].humanReadableDescription", "Updated description 2");
            params.put("inputParams[1].example", "example2");
            params.put("inputParams[1].enumValues", "a,b,c");

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            String result = adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            assertThat(result).isEqualTo("redirect:/tools/test-tool/bulk-edit");
            assertThat(redirectAttributes.getFlashAttributes()).containsKey("success");

            verify(toolRegistryClient).updateParameter(eq("test-tool"), eq(100L),
                    eq("Updated description 1"), eq("example1"), isNull());
            verify(toolRegistryClient).updateParameter(eq("test-tool"), eq(101L),
                    eq("Updated description 2"), eq("example2"), eq(Arrays.asList("a", "b", "c")));
        }

        @Test
        @DisplayName("Should update response parameters")
        void shouldUpdateResponseParameters() {
            Map<String, String> params = new HashMap<>();
            params.put("responseParams[0_0].id", "300");
            params.put("responseParams[0_0].name", "result");
            params.put("responseParams[0_0].humanReadableDescription", "Response result description");
            params.put("responseParams[0_0].example", "{\"key\": \"value\"}");
            params.put("responseParams[0_0].enumValues", "");

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            String result = adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            assertThat(result).isEqualTo("redirect:/tools/test-tool/bulk-edit");
            verify(toolRegistryClient).updateParameter(eq("test-tool"), eq(300L),
                    eq("Response result description"), eq("{\"key\": \"value\"}"), isNull());
        }

        @Test
        @DisplayName("Should update nested input parameters")
        void shouldUpdateNestedInputParameters() {
            Map<String, String> params = new HashMap<>();
            params.put("nestedInputParams[0_0].id", "150");
            params.put("nestedInputParams[0_0].name", "nestedParam");
            params.put("nestedInputParams[0_0].humanReadableDescription", "Nested param description");
            params.put("nestedInputParams[0_0].example", "nested-example");
            params.put("nestedInputParams[0_0].enumValues", "");

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            verify(toolRegistryClient).updateParameter(eq("test-tool"), eq(150L),
                    eq("Nested param description"), eq("nested-example"), isNull());
        }

        @Test
        @DisplayName("Should skip parameters with all empty values")
        void shouldSkipParametersWithAllEmptyValues() {
            Map<String, String> params = new HashMap<>();
            // Parameter with all empty values - should be skipped
            params.put("inputParams[0].id", "100");
            params.put("inputParams[0].name", "param1");
            params.put("inputParams[0].humanReadableDescription", "");
            params.put("inputParams[0].example", "");
            params.put("inputParams[0].enumValues", "");
            // Parameter with a value - should be updated
            params.put("inputParams[1].id", "101");
            params.put("inputParams[1].name", "param2");
            params.put("inputParams[1].humanReadableDescription", "Has description");
            params.put("inputParams[1].example", "");
            params.put("inputParams[1].enumValues", "");

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            // Only param 101 with humanReadableDescription should be updated
            verify(toolRegistryClient, times(1)).updateParameter(
                    eq("test-tool"), eq(101L), eq("Has description"), eq(""), isNull());
            // Param 100 should not be updated (all values empty)
            verify(toolRegistryClient, never()).updateParameter(
                    eq("test-tool"), eq(100L), eq(""), eq(""), isNull());
        }

        @Test
        @DisplayName("Should handle enum values correctly")
        void shouldHandleEnumValuesCorrectly() {
            Map<String, String> params = new HashMap<>();
            params.put("inputParams[0].id", "100");
            params.put("inputParams[0].name", "status");
            params.put("inputParams[0].humanReadableDescription", "Status field");
            params.put("inputParams[0].example", "ACTIVE");
            params.put("inputParams[0].enumValues", "ACTIVE,INACTIVE,PENDING");

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            verify(toolRegistryClient).updateParameter(
                    eq("test-tool"),
                    eq(100L),
                    eq("Status field"),
                    eq("ACTIVE"),
                    eq(Arrays.asList("ACTIVE", "INACTIVE", "PENDING")));
        }

        @Test
        @DisplayName("Should handle update failure gracefully")
        void shouldHandleUpdateFailureGracefully() {
            doThrow(new RuntimeException("Update failed"))
                    .when(toolRegistryClient).updateParameter(anyString(), anyLong(), any(), any(), any());

            Map<String, String> params = new HashMap<>();
            params.put("inputParams[0].id", "100");
            params.put("inputParams[0].name", "param1");
            params.put("inputParams[0].humanReadableDescription", "Description");
            params.put("inputParams[0].example", "");
            params.put("inputParams[0].enumValues", "");

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            String result = adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            assertThat(result).isEqualTo("redirect:/tools/test-tool/bulk-edit");
            assertThat(redirectAttributes.getFlashAttributes()).containsKey("error");
        }

        @Test
        @DisplayName("Should handle empty form submission")
        void shouldHandleEmptyFormSubmission() {
            Map<String, String> params = new HashMap<>();

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            String result = adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            assertThat(result).isEqualTo("redirect:/tools/test-tool/bulk-edit");
            assertThat(redirectAttributes.getFlashAttributes()).containsKey("success");

            verify(toolRegistryClient, never()).updateParameter(anyString(), anyLong(), any(), any(), any());
        }

        @Test
        @DisplayName("Should handle mixed input and response parameters")
        void shouldHandleMixedInputAndResponseParameters() {
            Map<String, String> params = new HashMap<>();
            // Input parameter
            params.put("inputParams[0].id", "100");
            params.put("inputParams[0].name", "inputParam");
            params.put("inputParams[0].humanReadableDescription", "Input description");
            params.put("inputParams[0].example", "input-example");
            params.put("inputParams[0].enumValues", "");
            // Response parameter
            params.put("responseParams[0_0].id", "300");
            params.put("responseParams[0_0].name", "responseParam");
            params.put("responseParams[0_0].humanReadableDescription", "Response description");
            params.put("responseParams[0_0].example", "response-example");
            params.put("responseParams[0_0].enumValues", "");

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            // Both should be updated
            verify(toolRegistryClient).updateParameter(eq("test-tool"), eq(100L),
                    eq("Input description"), eq("input-example"), isNull());
            verify(toolRegistryClient).updateParameter(eq("test-tool"), eq(300L),
                    eq("Response description"), eq("response-example"), isNull());
        }

        @Test
        @DisplayName("Should report correct update count in success message")
        void shouldReportCorrectUpdateCountInSuccessMessage() {
            Map<String, String> params = new HashMap<>();
            params.put("inputParams[0].id", "100");
            params.put("inputParams[0].humanReadableDescription", "Desc 1");
            params.put("inputParams[0].example", "");
            params.put("inputParams[0].enumValues", "");
            params.put("inputParams[1].id", "101");
            params.put("inputParams[1].humanReadableDescription", "Desc 2");
            params.put("inputParams[1].example", "");
            params.put("inputParams[1].enumValues", "");
            params.put("inputParams[2].id", "102");
            params.put("inputParams[2].humanReadableDescription", "Desc 3");
            params.put("inputParams[2].example", "");
            params.put("inputParams[2].enumValues", "");

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            String successMessage = (String) redirectAttributes.getFlashAttributes().get("success");
            assertThat(successMessage).contains("3 parameters updated");
            verify(toolRegistryClient, times(3)).updateParameter(anyString(), anyLong(), any(), any(), any());
        }

        @Test
        @DisplayName("Should handle parameter with only example value")
        void shouldHandleParameterWithOnlyExampleValue() {
            Map<String, String> params = new HashMap<>();
            params.put("inputParams[0].id", "100");
            params.put("inputParams[0].name", "param1");
            params.put("inputParams[0].humanReadableDescription", "");
            params.put("inputParams[0].example", "only-example");
            params.put("inputParams[0].enumValues", "");

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            verify(toolRegistryClient).updateParameter(eq("test-tool"), eq(100L),
                    eq(""), eq("only-example"), isNull());
        }

        @Test
        @DisplayName("Should handle deeply nested parameters")
        void shouldHandleDeeplyNestedParameters() {
            Map<String, String> params = new HashMap<>();
            params.put("nestedResponseParams[0_0_1].id", "500");
            params.put("nestedResponseParams[0_0_1].name", "deepNested");
            params.put("nestedResponseParams[0_0_1].humanReadableDescription", "Deep nested description");
            params.put("nestedResponseParams[0_0_1].example", "deep-example");
            params.put("nestedResponseParams[0_0_1].enumValues", "");

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            verify(toolRegistryClient).updateParameter(eq("test-tool"), eq(500L),
                    eq("Deep nested description"), eq("deep-example"), isNull());
        }

        @Test
        @DisplayName("Should handle invalid parameter ID gracefully")
        void shouldHandleInvalidParameterIdGracefully() {
            Map<String, String> params = new HashMap<>();
            params.put("inputParams[0].id", "not-a-number");
            params.put("inputParams[0].humanReadableDescription", "Description");

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            String result = adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            assertThat(result).isEqualTo("redirect:/tools/test-tool/bulk-edit");
            assertThat(redirectAttributes.getFlashAttributes()).containsKey("error");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null enum values")
        void shouldHandleNullEnumValues() {
            Map<String, String> params = new HashMap<>();
            params.put("inputParams[0].id", "100");
            params.put("inputParams[0].humanReadableDescription", "Description");
            params.put("inputParams[0].example", "example");
            // enumValues not included (null)

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            verify(toolRegistryClient).updateParameter(eq("test-tool"), eq(100L),
                    eq("Description"), eq("example"), isNull());
        }

        @Test
        @DisplayName("Should handle special characters in values")
        void shouldHandleSpecialCharactersInValues() {
            Map<String, String> params = new HashMap<>();
            params.put("inputParams[0].id", "100");
            params.put("inputParams[0].humanReadableDescription", "Description with \"quotes\" and 'apostrophes'");
            params.put("inputParams[0].example", "{\"json\": \"value\"}");
            params.put("inputParams[0].enumValues", "");

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            verify(toolRegistryClient).updateParameter(eq("test-tool"), eq(100L),
                    eq("Description with \"quotes\" and 'apostrophes'"), eq("{\"json\": \"value\"}"), isNull());
        }

        @Test
        @DisplayName("Should handle whitespace-only values as empty")
        void shouldHandleWhitespaceOnlyValuesAsEmpty() {
            Map<String, String> params = new HashMap<>();
            params.put("inputParams[0].id", "100");
            params.put("inputParams[0].humanReadableDescription", "   ");
            params.put("inputParams[0].example", "  ");
            params.put("inputParams[0].enumValues", "");

            RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
            adminController.bulkEditParameters("test-tool", params, redirectAttributes);

            // Whitespace-only should be treated as empty, so no update
            verify(toolRegistryClient, never()).updateParameter(anyString(), anyLong(), any(), any(), any());
        }
    }
}
