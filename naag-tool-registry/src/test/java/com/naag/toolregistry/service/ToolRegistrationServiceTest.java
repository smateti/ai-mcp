package com.naag.toolregistry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naag.toolregistry.dto.ParameterUpdateRequest;
import com.naag.toolregistry.entity.ParameterDefinition;
import com.naag.toolregistry.entity.ResponseDefinition;
import com.naag.toolregistry.entity.ToolDefinition;
import com.naag.toolregistry.metrics.ToolRegistryMetrics;
import com.naag.toolregistry.repository.ToolDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolRegistrationServiceTest {

    @Mock
    private ToolDefinitionRepository toolDefinitionRepository;

    @Mock
    private OpenApiParserService openApiParserService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ToolRegistryMetrics metrics;

    @InjectMocks
    private ToolRegistrationService toolRegistrationService;

    private ToolDefinition testTool;
    private ParameterDefinition testParam;
    private ParameterDefinition nestedParam;
    private ResponseDefinition testResponse;
    private ParameterDefinition responseParam;

    @BeforeEach
    void setUp() {
        // Create test tool
        testTool = new ToolDefinition();
        testTool.setId(1L);
        testTool.setToolId("test-tool");
        testTool.setName("Test Tool");
        testTool.setDescription("Test description");
        testTool.setBaseUrl("http://localhost:8080");
        testTool.setPath("/api/test");
        testTool.setHttpMethod("GET");

        // Create test parameter
        testParam = new ParameterDefinition();
        testParam.setId(100L);
        testParam.setName("testParam");
        testParam.setType("string");
        testParam.setRequired(true);
        testParam.setDescription("Test parameter description");
        testParam.setNestingLevel(0);
        testParam.setToolDefinition(testTool);
        testParam.setNestedParameters(new ArrayList<>());

        // Create nested parameter
        nestedParam = new ParameterDefinition();
        nestedParam.setId(101L);
        nestedParam.setName("nestedParam");
        nestedParam.setType("integer");
        nestedParam.setRequired(false);
        nestedParam.setDescription("Nested parameter");
        nestedParam.setNestingLevel(1);
        nestedParam.setParentParameter(testParam);
        nestedParam.setNestedParameters(new ArrayList<>());
        testParam.getNestedParameters().add(nestedParam);

        // Create response
        testResponse = new ResponseDefinition();
        testResponse.setId(200L);
        testResponse.setStatusCode("200");
        testResponse.setDescription("Success response");
        testResponse.setToolDefinition(testTool);

        // Create response parameter
        responseParam = new ParameterDefinition();
        responseParam.setId(201L);
        responseParam.setName("responseField");
        responseParam.setType("string");
        responseParam.setRequired(false);
        responseParam.setDescription("Response field");
        responseParam.setNestingLevel(0);
        responseParam.setResponseDefinition(testResponse);
        responseParam.setNestedParameters(new ArrayList<>());
        testResponse.setParameters(new ArrayList<>(List.of(responseParam)));

        // Set up tool with parameters and responses
        testTool.setParameters(new ArrayList<>(List.of(testParam)));
        testTool.setResponses(new ArrayList<>(List.of(testResponse)));
    }

    @Nested
    @DisplayName("Parameter Human Description Update Tests")
    class ParameterHumanDescriptionTests {

        @Test
        @DisplayName("Should update human description for top-level parameter")
        void shouldUpdateHumanDescriptionForTopLevelParam() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            // When
            toolRegistrationService.updateParameterHumanDescription(100L, "Updated human description");

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            assertThat(savedTool.getParameters().get(0).getHumanReadableDescription())
                    .isEqualTo("Updated human description");
        }

        @Test
        @DisplayName("Should update human description for nested parameter")
        void shouldUpdateHumanDescriptionForNestedParam() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            // When
            toolRegistrationService.updateParameterHumanDescription(101L, "Nested description updated");

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            ParameterDefinition nested = savedTool.getParameters().get(0).getNestedParameters().get(0);
            assertThat(nested.getHumanReadableDescription()).isEqualTo("Nested description updated");
        }

        @Test
        @DisplayName("Should throw exception when parameter not found")
        void shouldThrowExceptionWhenParamNotFound() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));

            // When/Then
            assertThatThrownBy(() -> toolRegistrationService.updateParameterHumanDescription(999L, "test"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Parameter not found");
        }
    }

    @Nested
    @DisplayName("Parameter Example Update Tests")
    class ParameterExampleTests {

        @Test
        @DisplayName("Should update example for parameter")
        void shouldUpdateExampleForParam() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            // When
            toolRegistrationService.updateParameterExample(100L, "example-value-123");

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            assertThat(savedTool.getParameters().get(0).getExample()).isEqualTo("example-value-123");
        }

        @Test
        @DisplayName("Should update example for nested parameter")
        void shouldUpdateExampleForNestedParam() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            // When
            toolRegistrationService.updateParameterExample(101L, "42");

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            ParameterDefinition nested = savedTool.getParameters().get(0).getNestedParameters().get(0);
            assertThat(nested.getExample()).isEqualTo("42");
        }
    }

    @Nested
    @DisplayName("Parameter Enum Values Update Tests")
    class ParameterEnumValuesTests {

        @Test
        @DisplayName("Should update enum values as comma-separated string")
        void shouldUpdateEnumValues() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);
            List<String> enumValues = Arrays.asList("RED", "GREEN", "BLUE");

            // When
            toolRegistrationService.updateParameterEnumValues(100L, enumValues);

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            assertThat(savedTool.getParameters().get(0).getEnumValues()).isEqualTo("RED,GREEN,BLUE");
        }

        @Test
        @DisplayName("Should set null when enum values is empty list")
        void shouldSetNullWhenEnumValuesEmpty() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            // When
            toolRegistrationService.updateParameterEnumValues(100L, List.of());

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            assertThat(savedTool.getParameters().get(0).getEnumValues()).isNull();
        }

        @Test
        @DisplayName("Should set null when enum values is null")
        void shouldSetNullWhenEnumValuesNull() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            // When
            toolRegistrationService.updateParameterEnumValues(100L, null);

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            assertThat(savedTool.getParameters().get(0).getEnumValues()).isNull();
        }
    }

    @Nested
    @DisplayName("Full Parameter Update Tests")
    class FullParameterUpdateTests {

        @Test
        @DisplayName("Should update all fields in full parameter update")
        void shouldUpdateAllFields() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            ParameterUpdateRequest request = new ParameterUpdateRequest(
                    "New human description",
                    "example-value",
                    Arrays.asList("OPTION_A", "OPTION_B")
            );

            // When
            toolRegistrationService.updateParameter(100L, request);

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            ParameterDefinition param = savedTool.getParameters().get(0);
            assertThat(param.getHumanReadableDescription()).isEqualTo("New human description");
            assertThat(param.getExample()).isEqualTo("example-value");
            assertThat(param.getEnumValues()).isEqualTo("OPTION_A,OPTION_B");
        }

        @Test
        @DisplayName("Should only update non-blank fields")
        void shouldOnlyUpdateNonBlankFields() {
            // Given
            testParam.setHumanReadableDescription("Original description");
            testParam.setExample("original-example");
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            ParameterUpdateRequest request = new ParameterUpdateRequest(
                    "",  // blank - should not update
                    "new-example",
                    null  // null - should not update
            );

            // When
            toolRegistrationService.updateParameter(100L, request);

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            ParameterDefinition param = savedTool.getParameters().get(0);
            assertThat(param.getHumanReadableDescription()).isEqualTo("Original description");
            assertThat(param.getExample()).isEqualTo("new-example");
        }

        @Test
        @DisplayName("Should update nested parameter in full update")
        void shouldUpdateNestedParamInFullUpdate() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            ParameterUpdateRequest request = new ParameterUpdateRequest(
                    "Nested updated description",
                    "99",
                    Arrays.asList("LOW", "MEDIUM", "HIGH")
            );

            // When
            toolRegistrationService.updateParameter(101L, request);

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            ParameterDefinition nested = savedTool.getParameters().get(0).getNestedParameters().get(0);
            assertThat(nested.getHumanReadableDescription()).isEqualTo("Nested updated description");
            assertThat(nested.getExample()).isEqualTo("99");
            assertThat(nested.getEnumValues()).isEqualTo("LOW,MEDIUM,HIGH");
        }
    }

    @Nested
    @DisplayName("Response Human Description Update Tests")
    class ResponseHumanDescriptionTests {

        @Test
        @DisplayName("Should update response human description")
        void shouldUpdateResponseHumanDescription() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            // When
            toolRegistrationService.updateResponseHumanDescription(200L, "Updated response description");

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            assertThat(savedTool.getResponses().get(0).getHumanReadableDescription())
                    .isEqualTo("Updated response description");
        }

        @Test
        @DisplayName("Should throw exception when response not found")
        void shouldThrowExceptionWhenResponseNotFound() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));

            // When/Then
            assertThatThrownBy(() -> toolRegistrationService.updateResponseHumanDescription(999L, "test"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Response not found");
        }
    }

    @Nested
    @DisplayName("Response Parameter Update Tests")
    class ResponseParameterTests {

        @Test
        @DisplayName("Should update response parameter human description")
        void shouldUpdateResponseParamHumanDescription() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            // When
            toolRegistrationService.updateParameterHumanDescription(201L, "Response field description");

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            ParameterDefinition respParam = savedTool.getResponses().get(0).getParameters().get(0);
            assertThat(respParam.getHumanReadableDescription()).isEqualTo("Response field description");
        }

        @Test
        @DisplayName("Should update response parameter in full update")
        void shouldUpdateResponseParamInFullUpdate() {
            // Given
            when(toolDefinitionRepository.findAll()).thenReturn(List.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            ParameterUpdateRequest request = new ParameterUpdateRequest(
                    "Updated response field desc",
                    "sample-output",
                    Arrays.asList("SUCCESS", "FAILURE")
            );

            // When
            toolRegistrationService.updateParameter(201L, request);

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            ParameterDefinition respParam = savedTool.getResponses().get(0).getParameters().get(0);
            assertThat(respParam.getHumanReadableDescription()).isEqualTo("Updated response field desc");
            assertThat(respParam.getExample()).isEqualTo("sample-output");
            assertThat(respParam.getEnumValues()).isEqualTo("SUCCESS,FAILURE");
        }
    }

    @Nested
    @DisplayName("Tool Update By ToolId Tests")
    class ToolUpdateByToolIdTests {

        @Test
        @DisplayName("Should update tool by toolId")
        void shouldUpdateToolByToolId() {
            // Given
            when(toolDefinitionRepository.findByToolId("test-tool")).thenReturn(Optional.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            var request = new com.naag.toolregistry.controller.ToolApiController.ToolUpdateRequest(
                    "New description",
                    "New human readable",
                    "category-1"
            );

            // When
            ToolDefinition result = toolRegistrationService.updateToolByToolId("test-tool", request);

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            assertThat(savedTool.getDescription()).isEqualTo("New description");
            assertThat(savedTool.getHumanReadableDescription()).isEqualTo("New human readable");
            assertThat(savedTool.getCategoryId()).isEqualTo("category-1");
        }

        @Test
        @DisplayName("Should throw exception when tool not found by toolId")
        void shouldThrowExceptionWhenToolNotFoundByToolId() {
            // Given
            when(toolDefinitionRepository.findByToolId("non-existent")).thenReturn(Optional.empty());

            var request = new com.naag.toolregistry.controller.ToolApiController.ToolUpdateRequest(
                    "desc", "human", "cat"
            );

            // When/Then
            assertThatThrownBy(() -> toolRegistrationService.updateToolByToolId("non-existent", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Tool not found with toolId");
        }

        @Test
        @DisplayName("Should clear categoryId when blank string provided")
        void shouldClearCategoryIdWhenBlank() {
            // Given
            testTool.setCategoryId("existing-category");
            when(toolDefinitionRepository.findByToolId("test-tool")).thenReturn(Optional.of(testTool));
            when(toolDefinitionRepository.save(any())).thenReturn(testTool);

            var request = new com.naag.toolregistry.controller.ToolApiController.ToolUpdateRequest(
                    null,
                    null,
                    ""  // empty string should clear
            );

            // When
            toolRegistrationService.updateToolByToolId("test-tool", request);

            // Then
            ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
            verify(toolDefinitionRepository).save(captor.capture());

            ToolDefinition savedTool = captor.getValue();
            assertThat(savedTool.getCategoryId()).isNull();
        }
    }
}
