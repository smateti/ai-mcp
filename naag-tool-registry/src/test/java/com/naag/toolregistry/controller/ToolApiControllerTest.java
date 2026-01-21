package com.naag.toolregistry.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naag.toolregistry.dto.ParameterUpdateRequest;
import com.naag.toolregistry.dto.ResponseUpdateRequest;
import com.naag.toolregistry.entity.ParameterDefinition;
import com.naag.toolregistry.entity.ResponseDefinition;
import com.naag.toolregistry.entity.ToolDefinition;
import com.naag.toolregistry.service.ToolRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ToolApiController.class)
class ToolApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ToolRegistrationService toolRegistrationService;

    private ToolDefinition testTool;

    @BeforeEach
    void setUp() {
        testTool = new ToolDefinition();
        testTool.setId(1L);
        testTool.setToolId("test-tool");
        testTool.setName("Test Tool");
        testTool.setDescription("Test description");
        testTool.setBaseUrl("http://localhost:8080");
        testTool.setPath("/api/test");
        testTool.setHttpMethod("GET");

        ParameterDefinition param = new ParameterDefinition();
        param.setId(100L);
        param.setName("testParam");
        param.setType("string");
        param.setRequired(true);
        param.setNestingLevel(0);
        param.setNestedParameters(new ArrayList<>());
        testTool.setParameters(new ArrayList<>(List.of(param)));

        ResponseDefinition response = new ResponseDefinition();
        response.setId(200L);
        response.setStatusCode("200");
        response.setDescription("Success");
        response.setParameters(new ArrayList<>());
        testTool.setResponses(new ArrayList<>(List.of(response)));
    }

    @Nested
    @DisplayName("GET /api/tools Tests")
    class GetAllToolsTests {

        @Test
        @DisplayName("Should return all tools")
        void shouldReturnAllTools() throws Exception {
            when(toolRegistrationService.getAllTools()).thenReturn(List.of(testTool));

            mockMvc.perform(get("/api/tools"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].toolId").value("test-tool"))
                    .andExpect(jsonPath("$[0].name").value("Test Tool"));
        }

        @Test
        @DisplayName("Should return empty list when no tools")
        void shouldReturnEmptyListWhenNoTools() throws Exception {
            when(toolRegistrationService.getAllTools()).thenReturn(List.of());

            mockMvc.perform(get("/api/tools"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/tools/by-tool-id/{toolId} Tests")
    class GetToolByToolIdTests {

        @Test
        @DisplayName("Should return tool by toolId")
        void shouldReturnToolByToolId() throws Exception {
            when(toolRegistrationService.getToolByToolId("test-tool")).thenReturn(testTool);

            mockMvc.perform(get("/api/tools/by-tool-id/test-tool"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.toolId").value("test-tool"))
                    .andExpect(jsonPath("$.name").value("Test Tool"));
        }

        @Test
        @DisplayName("Should return 404 when tool not found")
        void shouldReturn404WhenToolNotFound() throws Exception {
            when(toolRegistrationService.getToolByToolId("non-existent"))
                    .thenThrow(new IllegalArgumentException("Tool not found"));

            mockMvc.perform(get("/api/tools/by-tool-id/non-existent"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/tools/by-tool-id/{toolId} Tests")
    class UpdateToolByToolIdTests {

        @Test
        @DisplayName("Should update tool by toolId")
        void shouldUpdateToolByToolId() throws Exception {
            when(toolRegistrationService.updateToolByToolId(eq("test-tool"), any()))
                    .thenReturn(testTool);

            String requestBody = """
                {
                    "description": "Updated description",
                    "humanReadableDescription": "Updated human desc",
                    "categoryId": "cat-1"
                }
                """;

            mockMvc.perform(put("/api/tools/by-tool-id/test-tool")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(toolRegistrationService).updateToolByToolId(eq("test-tool"), any());
        }

        @Test
        @DisplayName("Should return 404 when updating non-existent tool")
        void shouldReturn404WhenUpdatingNonExistentTool() throws Exception {
            when(toolRegistrationService.updateToolByToolId(eq("non-existent"), any()))
                    .thenThrow(new IllegalArgumentException("Tool not found"));

            String requestBody = """
                {
                    "description": "Updated description"
                }
                """;

            mockMvc.perform(put("/api/tools/by-tool-id/non-existent")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/tools/{toolId}/parameters/{parameterId}/description Tests")
    class UpdateParameterDescriptionTests {

        @Test
        @DisplayName("Should update parameter human description")
        void shouldUpdateParameterHumanDescription() throws Exception {
            doNothing().when(toolRegistrationService).updateParameterHumanDescription(eq(100L), any());

            String requestBody = """
                {
                    "humanReadableDescription": "Updated param description"
                }
                """;

            mockMvc.perform(put("/api/tools/test-tool/parameters/100/description")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(toolRegistrationService).updateParameterHumanDescription(100L, "Updated param description");
        }

        @Test
        @DisplayName("Should return 404 when parameter not found")
        void shouldReturn404WhenParamNotFound() throws Exception {
            doThrow(new IllegalArgumentException("Parameter not found"))
                    .when(toolRegistrationService).updateParameterHumanDescription(eq(999L), any());

            String requestBody = """
                {
                    "humanReadableDescription": "test"
                }
                """;

            mockMvc.perform(put("/api/tools/test-tool/parameters/999/description")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/tools/{toolId}/parameters/{parameterId}/example Tests")
    class UpdateParameterExampleTests {

        @Test
        @DisplayName("Should update parameter example")
        void shouldUpdateParameterExample() throws Exception {
            doNothing().when(toolRegistrationService).updateParameterExample(eq(100L), any());

            String requestBody = """
                {
                    "example": "sample-value-123"
                }
                """;

            mockMvc.perform(put("/api/tools/test-tool/parameters/100/example")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(toolRegistrationService).updateParameterExample(100L, "sample-value-123");
        }
    }

    @Nested
    @DisplayName("PUT /api/tools/{toolId}/parameters/{parameterId}/enum Tests")
    class UpdateParameterEnumTests {

        @Test
        @DisplayName("Should update parameter enum values")
        void shouldUpdateParameterEnumValues() throws Exception {
            doNothing().when(toolRegistrationService).updateParameterEnumValues(eq(100L), any());

            String requestBody = """
                {
                    "enumValues": ["RED", "GREEN", "BLUE"]
                }
                """;

            mockMvc.perform(put("/api/tools/test-tool/parameters/100/enum")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(toolRegistrationService).updateParameterEnumValues(eq(100L), eq(Arrays.asList("RED", "GREEN", "BLUE")));
        }

        @Test
        @DisplayName("Should handle empty enum values")
        void shouldHandleEmptyEnumValues() throws Exception {
            doNothing().when(toolRegistrationService).updateParameterEnumValues(eq(100L), any());

            String requestBody = """
                {
                    "enumValues": []
                }
                """;

            mockMvc.perform(put("/api/tools/test-tool/parameters/100/enum")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(toolRegistrationService).updateParameterEnumValues(eq(100L), eq(List.of()));
        }
    }

    @Nested
    @DisplayName("PUT /api/tools/{toolId}/parameters/{parameterId} Tests")
    class FullParameterUpdateTests {

        @Test
        @DisplayName("Should perform full parameter update")
        void shouldPerformFullParameterUpdate() throws Exception {
            doNothing().when(toolRegistrationService).updateParameter(eq(100L), any(ParameterUpdateRequest.class));

            String requestBody = """
                {
                    "humanReadableDescription": "Full update description",
                    "example": "full-example",
                    "enumValues": ["OPTION_A", "OPTION_B"]
                }
                """;

            mockMvc.perform(put("/api/tools/test-tool/parameters/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(toolRegistrationService).updateParameter(eq(100L), any(ParameterUpdateRequest.class));
        }

        @Test
        @DisplayName("Should handle partial update request")
        void shouldHandlePartialUpdateRequest() throws Exception {
            doNothing().when(toolRegistrationService).updateParameter(eq(100L), any(ParameterUpdateRequest.class));

            String requestBody = """
                {
                    "example": "only-example"
                }
                """;

            mockMvc.perform(put("/api/tools/test-tool/parameters/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(toolRegistrationService).updateParameter(eq(100L), any(ParameterUpdateRequest.class));
        }

        @Test
        @DisplayName("Should return 404 when parameter not found in full update")
        void shouldReturn404WhenParamNotFoundInFullUpdate() throws Exception {
            doThrow(new IllegalArgumentException("Parameter not found"))
                    .when(toolRegistrationService).updateParameter(eq(999L), any(ParameterUpdateRequest.class));

            String requestBody = """
                {
                    "humanReadableDescription": "test"
                }
                """;

            mockMvc.perform(put("/api/tools/test-tool/parameters/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/tools/{toolId}/responses/{responseId}/description Tests")
    class UpdateResponseDescriptionTests {

        @Test
        @DisplayName("Should update response human description")
        void shouldUpdateResponseHumanDescription() throws Exception {
            doNothing().when(toolRegistrationService).updateResponseHumanDescription(eq(200L), any());

            String requestBody = """
                {
                    "humanReadableDescription": "Updated response description"
                }
                """;

            mockMvc.perform(put("/api/tools/test-tool/responses/200/description")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(toolRegistrationService).updateResponseHumanDescription(200L, "Updated response description");
        }

        @Test
        @DisplayName("Should return 404 when response not found")
        void shouldReturn404WhenResponseNotFound() throws Exception {
            doThrow(new IllegalArgumentException("Response not found"))
                    .when(toolRegistrationService).updateResponseHumanDescription(eq(999L), any());

            String requestBody = """
                {
                    "humanReadableDescription": "test"
                }
                """;

            mockMvc.perform(put("/api/tools/test-tool/responses/999/description")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/tools/{id} Tests")
    class DeleteToolTests {

        @Test
        @DisplayName("Should delete tool")
        void shouldDeleteTool() throws Exception {
            doNothing().when(toolRegistrationService).deleteTool(1L);

            mockMvc.perform(delete("/api/tools/1"))
                    .andExpect(status().isOk());

            verify(toolRegistrationService).deleteTool(1L);
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent tool")
        void shouldReturn404WhenDeletingNonExistentTool() throws Exception {
            doThrow(new IllegalArgumentException("Tool not found"))
                    .when(toolRegistrationService).deleteTool(999L);

            mockMvc.perform(delete("/api/tools/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/tools/check-duplicate Tests")
    class CheckDuplicateTests {

        @Test
        @DisplayName("Should return exists true when tool exists")
        void shouldReturnExistsTrueWhenToolExists() throws Exception {
            when(toolRegistrationService.toolExists(any(), any(), any())).thenReturn(true);

            mockMvc.perform(get("/api/tools/check-duplicate")
                            .param("openApiEndpoint", "http://example.com/api")
                            .param("path", "/test")
                            .param("httpMethod", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(true));
        }

        @Test
        @DisplayName("Should return exists false when tool does not exist")
        void shouldReturnExistsFalseWhenToolDoesNotExist() throws Exception {
            when(toolRegistrationService.toolExists(any(), any(), any())).thenReturn(false);

            mockMvc.perform(get("/api/tools/check-duplicate")
                            .param("openApiEndpoint", "http://example.com/api")
                            .param("path", "/new")
                            .param("httpMethod", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exists").value(false));
        }
    }
}
