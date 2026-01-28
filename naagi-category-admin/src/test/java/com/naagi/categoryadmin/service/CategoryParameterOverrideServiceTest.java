package com.naagi.categoryadmin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.categoryadmin.client.ToolRegistryClient;
import com.naagi.categoryadmin.dto.CategoryToolOverview;
import com.naagi.categoryadmin.dto.MergedToolDefinition;
import com.naagi.categoryadmin.model.CategoryParameterOverride;
import com.naagi.categoryadmin.model.CategoryToolOverride;
import com.naagi.categoryadmin.repository.CategoryParameterOverrideRepository;
import com.naagi.categoryadmin.repository.CategoryToolOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CategoryParameterOverrideService.
 */
@ExtendWith(MockitoExtension.class)
class CategoryParameterOverrideServiceTest {

    @Mock
    private CategoryParameterOverrideRepository overrideRepository;

    @Mock
    private CategoryToolOverrideRepository toolOverrideRepository;

    @Mock
    private ToolRegistryClient toolRegistryClient;

    private CategoryParameterOverrideService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new CategoryParameterOverrideService(overrideRepository, toolOverrideRepository, toolRegistryClient);

        // Default: no tool-level overrides unless specifically mocked
        lenient().when(toolOverrideRepository.findByCategoryIdAndToolIdAndActiveTrue(any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(toolOverrideRepository.findByCategoryIdAndActiveTrue(any()))
                .thenReturn(List.of());
    }

    @Nested
    @DisplayName("Override CRUD Tests")
    class OverrideCrudTests {

        @Test
        @DisplayName("Should create new override when not exists")
        void shouldCreateNewOverride() {
            CategoryParameterOverride override = CategoryParameterOverride.builder()
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .parameterPath("appType")
                    .lockedValue("BATCH")
                    .build();

            when(overrideRepository.findByCategoryIdAndToolIdAndParameterPath("cat-1", "tool-1", "appType"))
                    .thenReturn(Optional.empty());
            when(overrideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CategoryParameterOverride result = service.createOrUpdateOverride(override);

            assertThat(result.getLockedValue()).isEqualTo("BATCH");
            verify(overrideRepository).save(override);
        }

        @Test
        @DisplayName("Should update existing override")
        void shouldUpdateExistingOverride() {
            CategoryParameterOverride existing = CategoryParameterOverride.builder()
                    .id(1L)
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .parameterPath("appType")
                    .lockedValue("SERVICE")
                    .build();

            CategoryParameterOverride updated = CategoryParameterOverride.builder()
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .parameterPath("appType")
                    .lockedValue("BATCH")
                    .build();

            when(overrideRepository.findByCategoryIdAndToolIdAndParameterPath("cat-1", "tool-1", "appType"))
                    .thenReturn(Optional.of(existing));
            when(overrideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CategoryParameterOverride result = service.createOrUpdateOverride(updated);

            assertThat(result.getLockedValue()).isEqualTo("BATCH");
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should delete override by ID")
        void shouldDeleteOverrideById() {
            service.deleteOverride(1L);
            verify(overrideRepository).deleteById(1L);
        }

        @Test
        @DisplayName("Should delete all overrides for tool in category")
        void shouldDeleteOverridesForTool() {
            service.deleteOverridesForTool("cat-1", "tool-1");
            verify(overrideRepository).deleteByCategoryIdAndToolId("cat-1", "tool-1");
        }

        @Test
        @DisplayName("Should get overrides for tool")
        void shouldGetOverridesForTool() {
            List<CategoryParameterOverride> expected = List.of(
                    CategoryParameterOverride.builder().parameterPath("param1").build(),
                    CategoryParameterOverride.builder().parameterPath("param2").build()
            );
            when(overrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(expected);

            List<CategoryParameterOverride> result = service.getOverridesForTool("cat-1", "tool-1");

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Merge Logic Tests")
    class MergeLogicTests {

        @Test
        @DisplayName("Should return empty when tool not found")
        void shouldReturnEmptyWhenToolNotFound() {
            when(toolRegistryClient.getToolDetails("unknown")).thenReturn(Optional.empty());

            Optional<MergedToolDefinition> result = service.getMergedToolDefinition("cat-1", "unknown");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should merge tool with locked value override")
        void shouldMergeToolWithLockedValueOverride() throws Exception {
            String toolJson = """
                {
                    "toolId": "tool-1",
                    "name": "Test Tool",
                    "description": "A test tool",
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "parameters": [
                        {
                            "name": "appType",
                            "type": "string",
                            "description": "Application type",
                            "enumValues": "BATCH,SERVICE,UI",
                            "required": true
                        }
                    ]
                }
                """;
            JsonNode toolNode = objectMapper.readTree(toolJson);
            when(toolRegistryClient.getToolDetails("tool-1")).thenReturn(Optional.of(toolNode));

            CategoryParameterOverride override = CategoryParameterOverride.builder()
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .parameterPath("appType")
                    .lockedValue("BATCH")
                    .active(true)
                    .build();
            when(overrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(List.of(override));

            Optional<MergedToolDefinition> result = service.getMergedToolDefinition("cat-1", "tool-1");

            assertThat(result).isPresent();
            MergedToolDefinition merged = result.get();
            assertThat(merged.getToolId()).isEqualTo("tool-1");
            assertThat(merged.getParameters()).hasSize(1);

            MergedToolDefinition.MergedParameter param = merged.getParameters().get(0);
            assertThat(param.getName()).isEqualTo("appType");
            assertThat(param.isLocked()).isTrue();
            assertThat(param.getLockedValue()).isEqualTo("BATCH");
            assertThat(param.getEffectiveDescription()).contains("[LOCKED to: BATCH]");
        }

        @Test
        @DisplayName("Should merge tool with enum override")
        void shouldMergeToolWithEnumOverride() throws Exception {
            String toolJson = """
                {
                    "toolId": "tool-1",
                    "name": "Test Tool",
                    "httpMethod": "POST",
                    "path": "/api/test",
                    "parameters": [
                        {
                            "name": "status",
                            "type": "string",
                            "description": "Status field",
                            "enumValues": "ACTIVE,INACTIVE,PENDING,DELETED"
                        }
                    ]
                }
                """;
            JsonNode toolNode = objectMapper.readTree(toolJson);
            when(toolRegistryClient.getToolDetails("tool-1")).thenReturn(Optional.of(toolNode));

            CategoryParameterOverride override = CategoryParameterOverride.builder()
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .parameterPath("status")
                    .enumValues("ACTIVE,INACTIVE")
                    .active(true)
                    .build();
            when(overrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(List.of(override));

            Optional<MergedToolDefinition> result = service.getMergedToolDefinition("cat-1", "tool-1");

            assertThat(result).isPresent();
            MergedToolDefinition.MergedParameter param = result.get().getParameters().get(0);
            assertThat(param.getEnumValues()).isEqualTo("ACTIVE,INACTIVE");
            assertThat(param.isEnumOverridden()).isTrue();
            assertThat(param.isLocked()).isFalse();
        }

        @Test
        @DisplayName("Should merge tool with description override")
        void shouldMergeToolWithDescriptionOverride() throws Exception {
            String toolJson = """
                {
                    "toolId": "tool-1",
                    "name": "Test Tool",
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "parameters": [
                        {
                            "name": "userId",
                            "type": "string",
                            "description": "Original description"
                        }
                    ]
                }
                """;
            JsonNode toolNode = objectMapper.readTree(toolJson);
            when(toolRegistryClient.getToolDetails("tool-1")).thenReturn(Optional.of(toolNode));

            CategoryParameterOverride override = CategoryParameterOverride.builder()
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .parameterPath("userId")
                    .humanReadableDescription("Category-specific description")
                    .active(true)
                    .build();
            when(overrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(List.of(override));

            Optional<MergedToolDefinition> result = service.getMergedToolDefinition("cat-1", "tool-1");

            assertThat(result).isPresent();
            MergedToolDefinition.MergedParameter param = result.get().getParameters().get(0);
            assertThat(param.getHumanReadableDescription()).isEqualTo("Category-specific description");
            assertThat(param.isDescriptionOverridden()).isTrue();
        }

        @Test
        @DisplayName("Should preserve original values when no override")
        void shouldPreserveOriginalValuesWhenNoOverride() throws Exception {
            String toolJson = """
                {
                    "toolId": "tool-1",
                    "name": "Test Tool",
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "parameters": [
                        {
                            "name": "param1",
                            "type": "string",
                            "description": "Original description",
                            "example": "original-example",
                            "enumValues": "A,B,C"
                        }
                    ]
                }
                """;
            JsonNode toolNode = objectMapper.readTree(toolJson);
            when(toolRegistryClient.getToolDetails("tool-1")).thenReturn(Optional.of(toolNode));
            when(overrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(List.of());

            Optional<MergedToolDefinition> result = service.getMergedToolDefinition("cat-1", "tool-1");

            assertThat(result).isPresent();
            MergedToolDefinition.MergedParameter param = result.get().getParameters().get(0);
            assertThat(param.getDescription()).isEqualTo("Original description");
            assertThat(param.getExample()).isEqualTo("original-example");
            assertThat(param.getEnumValues()).isEqualTo("A,B,C");
            assertThat(param.isLocked()).isFalse();
            assertThat(param.isDescriptionOverridden()).isFalse();
            assertThat(param.isEnumOverridden()).isFalse();
        }

        @Test
        @DisplayName("Should handle nested parameters")
        void shouldHandleNestedParameters() throws Exception {
            String toolJson = """
                {
                    "toolId": "tool-1",
                    "name": "Test Tool",
                    "httpMethod": "POST",
                    "path": "/api/test",
                    "parameters": [
                        {
                            "name": "config",
                            "type": "object",
                            "nestedParameters": [
                                {
                                    "name": "appType",
                                    "type": "string",
                                    "enumValues": "BATCH,SERVICE"
                                }
                            ]
                        }
                    ]
                }
                """;
            JsonNode toolNode = objectMapper.readTree(toolJson);
            when(toolRegistryClient.getToolDetails("tool-1")).thenReturn(Optional.of(toolNode));

            CategoryParameterOverride override = CategoryParameterOverride.builder()
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .parameterPath("config.appType")
                    .lockedValue("BATCH")
                    .active(true)
                    .build();
            when(overrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(List.of(override));

            Optional<MergedToolDefinition> result = service.getMergedToolDefinition("cat-1", "tool-1");

            assertThat(result).isPresent();
            MergedToolDefinition.MergedParameter configParam = result.get().getParameters().get(0);
            assertThat(configParam.getName()).isEqualTo("config");
            assertThat(configParam.getNestedParameters()).hasSize(1);

            MergedToolDefinition.MergedParameter nestedParam = configParam.getNestedParameters().get(0);
            assertThat(nestedParam.getName()).isEqualTo("appType");
            assertThat(nestedParam.getParameterPath()).isEqualTo("config.appType");
            assertThat(nestedParam.isLocked()).isTrue();
            assertThat(nestedParam.getLockedValue()).isEqualTo("BATCH");
        }
    }

    @Nested
    @DisplayName("Tool Overview Tests")
    class ToolOverviewTests {

        @Test
        @DisplayName("Should return overview with override statistics")
        void shouldReturnOverviewWithStatistics() throws Exception {
            String toolJson = """
                {
                    "toolId": "tool-1",
                    "name": "Test Tool",
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "baseUrl": "http://localhost:8080",
                    "parameters": [
                        {"name": "param1", "type": "string"},
                        {"name": "param2", "type": "string"},
                        {"name": "param3", "type": "string"}
                    ]
                }
                """;
            JsonNode toolNode = objectMapper.readTree(toolJson);
            when(toolRegistryClient.getToolDetails("tool-1")).thenReturn(Optional.of(toolNode));

            List<CategoryParameterOverride> overrides = List.of(
                    CategoryParameterOverride.builder()
                            .id(1L)
                            .parameterPath("param1")
                            .lockedValue("locked")
                            .active(true)
                            .build(),
                    CategoryParameterOverride.builder()
                            .id(2L)
                            .parameterPath("param2")
                            .enumValues("A,B")
                            .active(true)
                            .build()
            );
            when(overrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(overrides);

            List<CategoryToolOverview> results = service.getToolOverviewsForCategory("cat-1", List.of("tool-1"));

            assertThat(results).hasSize(1);
            CategoryToolOverview overview = results.get(0);
            assertThat(overview.getToolId()).isEqualTo("tool-1");
            assertThat(overview.getTotalParameters()).isEqualTo(3);
            assertThat(overview.getLockedParameters()).isEqualTo(1);
            assertThat(overview.getOverriddenParameters()).isEqualTo(1);
            assertThat(overview.getOverrideSummaries()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Effective Description Tests")
    class EffectiveDescriptionTests {

        @Test
        @DisplayName("Should format effective description with locked value")
        void shouldFormatEffectiveDescriptionWithLockedValue() throws Exception {
            String toolJson = """
                {
                    "toolId": "tool-1",
                    "name": "Test Tool",
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "parameters": [
                        {
                            "name": "appType",
                            "type": "string",
                            "description": "Application type"
                        }
                    ]
                }
                """;
            JsonNode toolNode = objectMapper.readTree(toolJson);
            when(toolRegistryClient.getToolDetails("tool-1")).thenReturn(Optional.of(toolNode));

            CategoryParameterOverride override = CategoryParameterOverride.builder()
                    .parameterPath("appType")
                    .lockedValue("BATCH")
                    .active(true)
                    .build();
            when(overrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(List.of(override));

            Optional<MergedToolDefinition> result = service.getMergedToolDefinition("cat-1", "tool-1");

            MergedToolDefinition.MergedParameter param = result.get().getParameters().get(0);
            assertThat(param.getEffectiveDescription()).isEqualTo("Application type. [LOCKED to: BATCH]");
        }

        @Test
        @DisplayName("Should format effective description with enum values")
        void shouldFormatEffectiveDescriptionWithEnumValues() throws Exception {
            String toolJson = """
                {
                    "toolId": "tool-1",
                    "name": "Test Tool",
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "parameters": [
                        {
                            "name": "status",
                            "type": "string",
                            "description": "Status field"
                        }
                    ]
                }
                """;
            JsonNode toolNode = objectMapper.readTree(toolJson);
            when(toolRegistryClient.getToolDetails("tool-1")).thenReturn(Optional.of(toolNode));

            CategoryParameterOverride override = CategoryParameterOverride.builder()
                    .parameterPath("status")
                    .enumValues("ACTIVE,INACTIVE")
                    .active(true)
                    .build();
            when(overrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(List.of(override));

            Optional<MergedToolDefinition> result = service.getMergedToolDefinition("cat-1", "tool-1");

            MergedToolDefinition.MergedParameter param = result.get().getParameters().get(0);
            assertThat(param.getEffectiveDescription()).isEqualTo("Status field. Allowed values: ACTIVE, INACTIVE");
        }
    }

    @Nested
    @DisplayName("Tool-Level Override CRUD Tests")
    class ToolLevelOverrideCrudTests {

        @Test
        @DisplayName("Should create new tool override when not exists")
        void shouldCreateNewToolOverride() {
            CategoryToolOverride override = CategoryToolOverride.builder()
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .whenToUse("Use for batch job queries")
                    .whenNotToUse("Do not use for real-time status")
                    .build();

            when(toolOverrideRepository.findByCategoryIdAndToolId("cat-1", "tool-1"))
                    .thenReturn(Optional.empty());
            when(toolOverrideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CategoryToolOverride result = service.createOrUpdateToolOverride(override);

            assertThat(result.getWhenToUse()).isEqualTo("Use for batch job queries");
            assertThat(result.getWhenNotToUse()).isEqualTo("Do not use for real-time status");
            verify(toolOverrideRepository).save(override);
        }

        @Test
        @DisplayName("Should update existing tool override")
        void shouldUpdateExistingToolOverride() {
            CategoryToolOverride existing = CategoryToolOverride.builder()
                    .id(1L)
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .whenToUse("Old guidance")
                    .active(true)
                    .build();

            CategoryToolOverride updated = CategoryToolOverride.builder()
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .whenToUse("New guidance")
                    .whenNotToUse("Avoid for X")
                    .priorityScore(80)
                    .active(true)
                    .build();

            when(toolOverrideRepository.findByCategoryIdAndToolId("cat-1", "tool-1"))
                    .thenReturn(Optional.of(existing));
            when(toolOverrideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CategoryToolOverride result = service.createOrUpdateToolOverride(updated);

            assertThat(existing.getWhenToUse()).isEqualTo("New guidance");
            assertThat(existing.getWhenNotToUse()).isEqualTo("Avoid for X");
            assertThat(existing.getPriorityScore()).isEqualTo(80);
            verify(toolOverrideRepository).save(existing);
        }

        @Test
        @DisplayName("Should get tool override")
        void shouldGetToolOverride() {
            CategoryToolOverride override = CategoryToolOverride.builder()
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .whenToUse("Use for batch")
                    .build();

            when(toolOverrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(Optional.of(override));

            Optional<CategoryToolOverride> result = service.getToolOverride("cat-1", "tool-1");

            assertThat(result).isPresent();
            assertThat(result.get().getWhenToUse()).isEqualTo("Use for batch");
        }

        @Test
        @DisplayName("Should delete tool override")
        void shouldDeleteToolOverride() {
            service.deleteToolOverride("cat-1", "tool-1");

            verify(toolOverrideRepository).deleteByCategoryIdAndToolId("cat-1", "tool-1");
        }
    }

    @Nested
    @DisplayName("Tool-Level Override Merge Tests")
    class ToolLevelOverrideMergeTests {

        @Test
        @DisplayName("Should merge tool with whenToUse guidance")
        void shouldMergeToolWithWhenToUseGuidance() throws Exception {
            String toolJson = """
                {
                    "toolId": "tool-1",
                    "name": "Test Tool",
                    "description": "A test tool",
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "parameters": []
                }
                """;
            JsonNode toolNode = objectMapper.readTree(toolJson);
            when(toolRegistryClient.getToolDetails("tool-1")).thenReturn(Optional.of(toolNode));
            when(overrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(List.of());

            CategoryToolOverride toolOverride = CategoryToolOverride.builder()
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .whenToUse("Use for batch job queries in this category")
                    .whenNotToUse("Do not use for real-time status checks")
                    .active(true)
                    .build();
            when(toolOverrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(Optional.of(toolOverride));

            Optional<MergedToolDefinition> result = service.getMergedToolDefinition("cat-1", "tool-1");

            assertThat(result).isPresent();
            MergedToolDefinition merged = result.get();
            assertThat(merged.getWhenToUse()).isEqualTo("Use for batch job queries in this category");
            assertThat(merged.getWhenNotToUse()).isEqualTo("Do not use for real-time status checks");
            assertThat(merged.getEffectiveToolDescription())
                    .contains("A test tool")
                    .contains("USE WHEN: Use for batch job queries in this category")
                    .contains("DO NOT USE WHEN: Do not use for real-time status checks");
        }

        @Test
        @DisplayName("Should merge tool with description override")
        void shouldMergeToolWithDescriptionOverride() throws Exception {
            String toolJson = """
                {
                    "toolId": "tool-1",
                    "name": "Test Tool",
                    "description": "Original description",
                    "humanReadableDescription": "Original human readable",
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "parameters": []
                }
                """;
            JsonNode toolNode = objectMapper.readTree(toolJson);
            when(toolRegistryClient.getToolDetails("tool-1")).thenReturn(Optional.of(toolNode));
            when(overrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(List.of());

            CategoryToolOverride toolOverride = CategoryToolOverride.builder()
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .humanReadableDescription("Category-specific description for batch operations")
                    .active(true)
                    .build();
            when(toolOverrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(Optional.of(toolOverride));

            Optional<MergedToolDefinition> result = service.getMergedToolDefinition("cat-1", "tool-1");

            assertThat(result).isPresent();
            MergedToolDefinition merged = result.get();
            assertThat(merged.getHumanReadableDescription()).isEqualTo("Category-specific description for batch operations");
            assertThat(merged.isToolDescriptionOverridden()).isTrue();
            assertThat(merged.getEffectiveToolDescription()).isEqualTo("Category-specific description for batch operations");
        }

        @Test
        @DisplayName("Should merge tool with priority score and usage examples")
        void shouldMergeToolWithPriorityAndExamples() throws Exception {
            String toolJson = """
                {
                    "toolId": "tool-1",
                    "name": "Test Tool",
                    "description": "A test tool",
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "parameters": []
                }
                """;
            JsonNode toolNode = objectMapper.readTree(toolJson);
            when(toolRegistryClient.getToolDetails("tool-1")).thenReturn(Optional.of(toolNode));
            when(overrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(List.of());

            CategoryToolOverride toolOverride = CategoryToolOverride.builder()
                    .categoryId("cat-1")
                    .toolId("tool-1")
                    .usageExamples("getAppInfo(appId='batch-123', appType='BATCH')")
                    .priorityScore(90)
                    .active(true)
                    .build();
            when(toolOverrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(Optional.of(toolOverride));

            Optional<MergedToolDefinition> result = service.getMergedToolDefinition("cat-1", "tool-1");

            assertThat(result).isPresent();
            MergedToolDefinition merged = result.get();
            assertThat(merged.getUsageExamples()).isEqualTo("getAppInfo(appId='batch-123', appType='BATCH')");
            assertThat(merged.getPriorityScore()).isEqualTo(90);
        }

        @Test
        @DisplayName("Should merge multiple tools with different tool overrides")
        void shouldMergeMultipleToolsWithDifferentOverrides() throws Exception {
            String tool1Json = """
                {
                    "toolId": "tool-1",
                    "name": "Tool One",
                    "description": "First tool",
                    "httpMethod": "GET",
                    "path": "/api/one",
                    "parameters": []
                }
                """;
            String tool2Json = """
                {
                    "toolId": "tool-2",
                    "name": "Tool Two",
                    "description": "Second tool",
                    "httpMethod": "POST",
                    "path": "/api/two",
                    "parameters": []
                }
                """;
            when(toolRegistryClient.getToolDetails("tool-1")).thenReturn(Optional.of(objectMapper.readTree(tool1Json)));
            when(toolRegistryClient.getToolDetails("tool-2")).thenReturn(Optional.of(objectMapper.readTree(tool2Json)));
            when(overrideRepository.findByCategoryIdAndActiveTrue("cat-1")).thenReturn(List.of());

            List<CategoryToolOverride> toolOverrides = List.of(
                    CategoryToolOverride.builder()
                            .categoryId("cat-1")
                            .toolId("tool-1")
                            .whenToUse("Use for reading data")
                            .priorityScore(80)
                            .active(true)
                            .build(),
                    CategoryToolOverride.builder()
                            .categoryId("cat-1")
                            .toolId("tool-2")
                            .whenToUse("Use for writing data")
                            .priorityScore(70)
                            .active(true)
                            .build()
            );
            when(toolOverrideRepository.findByCategoryIdAndActiveTrue("cat-1")).thenReturn(toolOverrides);

            List<MergedToolDefinition> results = service.getMergedToolsForCategory("cat-1", List.of("tool-1", "tool-2"));

            assertThat(results).hasSize(2);

            MergedToolDefinition tool1 = results.stream().filter(t -> t.getToolId().equals("tool-1")).findFirst().orElseThrow();
            assertThat(tool1.getWhenToUse()).isEqualTo("Use for reading data");
            assertThat(tool1.getPriorityScore()).isEqualTo(80);

            MergedToolDefinition tool2 = results.stream().filter(t -> t.getToolId().equals("tool-2")).findFirst().orElseThrow();
            assertThat(tool2.getWhenToUse()).isEqualTo("Use for writing data");
            assertThat(tool2.getPriorityScore()).isEqualTo(70);
        }

        @Test
        @DisplayName("Should not set override flags when no tool override exists")
        void shouldNotSetOverrideFlagsWhenNoToolOverride() throws Exception {
            String toolJson = """
                {
                    "toolId": "tool-1",
                    "name": "Test Tool",
                    "description": "A test tool",
                    "humanReadableDescription": "Human readable desc",
                    "httpMethod": "GET",
                    "path": "/api/test",
                    "parameters": []
                }
                """;
            JsonNode toolNode = objectMapper.readTree(toolJson);
            when(toolRegistryClient.getToolDetails("tool-1")).thenReturn(Optional.of(toolNode));
            when(overrideRepository.findByCategoryIdAndToolIdAndActiveTrue("cat-1", "tool-1"))
                    .thenReturn(List.of());
            // No tool override (using default from setUp)

            Optional<MergedToolDefinition> result = service.getMergedToolDefinition("cat-1", "tool-1");

            assertThat(result).isPresent();
            MergedToolDefinition merged = result.get();
            assertThat(merged.getHumanReadableDescription()).isEqualTo("Human readable desc");
            assertThat(merged.isToolDescriptionOverridden()).isFalse();
            assertThat(merged.getWhenToUse()).isNull();
            assertThat(merged.getWhenNotToUse()).isNull();
            assertThat(merged.getPriorityScore()).isNull();
            assertThat(merged.getEffectiveToolDescription()).isEqualTo("Human readable desc");
        }
    }
}
