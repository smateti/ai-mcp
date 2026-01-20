package com.naag.toolregistry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naag.toolregistry.controller.ToolApiController.SimpleToolRequest;
import com.naag.toolregistry.controller.ToolApiController.ToolUpdateRequest;
import com.naag.toolregistry.dto.ParsedToolInfo;
import com.naag.toolregistry.dto.ToolRegistrationRequest;
import com.naag.toolregistry.entity.ParameterDefinition;
import com.naag.toolregistry.entity.ResponseDefinition;
import com.naag.toolregistry.entity.ToolDefinition;
import com.naag.toolregistry.metrics.ToolRegistryMetrics;
import com.naag.toolregistry.repository.ToolDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolRegistrationService {

    private final OpenApiParserService openApiParserService;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final ObjectMapper objectMapper;
    private final ToolRegistryMetrics metrics;

    @Transactional
    public ToolDefinition registerTool(ToolRegistrationRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            if (toolDefinitionRepository.existsByToolId(request.getToolId())) {
                throw new IllegalArgumentException("Tool with ID " + request.getToolId() + " already exists");
            }

            long parseStart = System.currentTimeMillis();
            ParsedToolInfo parsedInfo = openApiParserService.parseOpenApiEndpoint(
                    request.getOpenApiEndpoint(),
                    request.getPath(),
                    request.getHttpMethod()
            );
            metrics.recordOpenApiParsing(System.currentTimeMillis() - parseStart);

        ToolDefinition toolDefinition = new ToolDefinition();
        toolDefinition.setToolId(request.getToolId());
        toolDefinition.setName(parsedInfo.getName());
        toolDefinition.setDescription(parsedInfo.getDescription());

        if (request.getHumanReadableDescription() != null && !request.getHumanReadableDescription().trim().isEmpty()) {
            toolDefinition.setHumanReadableDescription(request.getHumanReadableDescription());
        } else if (parsedInfo.getDescription() != null && !parsedInfo.getDescription().trim().isEmpty()) {
            toolDefinition.setHumanReadableDescription(parsedInfo.getDescription());
        }

        toolDefinition.setOpenApiEndpoint(request.getOpenApiEndpoint());
        toolDefinition.setHttpMethod(parsedInfo.getHttpMethod());
        toolDefinition.setPath(parsedInfo.getPath());
        toolDefinition.setBaseUrl(request.getBaseUrl() != null ? request.getBaseUrl() : parsedInfo.getBaseUrl());
        toolDefinition.setCategoryId(request.getCategoryId());

        List<ParameterDefinition> parameters = parsedInfo.getParameters().stream()
                .map(paramInfo -> convertToParameterDefinition(paramInfo, toolDefinition, null))
                .collect(Collectors.toList());

        if (request.getParamNames() != null && request.getParamNames().length > 0) {
            applyHumanAnnotations(parameters, request);
        } else {
            copyDescriptionsToHumanReadable(parameters);
        }

        List<ResponseDefinition> responses = parsedInfo.getResponses().stream()
                .map(respInfo -> {
                    ResponseDefinition response = new ResponseDefinition();
                    response.setToolDefinition(toolDefinition);
                    response.setStatusCode(respInfo.getStatusCode());
                    response.setDescription(respInfo.getDescription());
                    response.setType(respInfo.getType());
                    response.setSchema(respInfo.getSchema());

                    if (respInfo.getParameters() != null && !respInfo.getParameters().isEmpty()) {
                        List<ParameterDefinition> responseParams = respInfo.getParameters().stream()
                                .map(paramInfo -> convertToResponseParameterDefinition(paramInfo, response, null))
                                .collect(Collectors.toList());
                        response.setParameters(responseParams);
                    }

                    return response;
                })
                .collect(Collectors.toList());

            toolDefinition.setParameters(parameters);
            toolDefinition.setResponses(responses);

            ToolDefinition saved = toolDefinitionRepository.save(toolDefinition);
            metrics.recordToolRegistration(System.currentTimeMillis() - startTime);
            updateToolCount();
            return saved;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            metrics.recordRegistrationError();
            throw e;
        }
    }

    /**
     * Register a tool from OpenAPI content (file upload).
     * Similar to registerTool but parses from content instead of URL.
     */
    @Transactional
    public ToolDefinition registerToolFromContent(ToolRegistrationRequest request, String openApiContent) {
        long startTime = System.currentTimeMillis();
        try {
            if (toolDefinitionRepository.existsByToolId(request.getToolId())) {
                throw new IllegalArgumentException("Tool with ID " + request.getToolId() + " already exists");
            }

            long parseStart = System.currentTimeMillis();
            ParsedToolInfo parsedInfo = openApiParserService.parseOpenApiFromContent(
                    openApiContent,
                    request.getPath(),
                    request.getHttpMethod()
            );
            metrics.recordOpenApiParsing(System.currentTimeMillis() - parseStart);

            ToolDefinition toolDefinition = new ToolDefinition();
            toolDefinition.setToolId(request.getToolId());
            toolDefinition.setName(parsedInfo.getName());
            toolDefinition.setDescription(parsedInfo.getDescription());

            if (request.getHumanReadableDescription() != null && !request.getHumanReadableDescription().trim().isEmpty()) {
                toolDefinition.setHumanReadableDescription(request.getHumanReadableDescription());
            } else if (parsedInfo.getDescription() != null && !parsedInfo.getDescription().trim().isEmpty()) {
                toolDefinition.setHumanReadableDescription(parsedInfo.getDescription());
            }

            toolDefinition.setOpenApiEndpoint(request.getOpenApiEndpoint());
            toolDefinition.setHttpMethod(parsedInfo.getHttpMethod());
            toolDefinition.setPath(parsedInfo.getPath());
            // For content-based registration, baseUrl must be provided in the request
            toolDefinition.setBaseUrl(request.getBaseUrl() != null ? request.getBaseUrl() : parsedInfo.getBaseUrl());
            toolDefinition.setCategoryId(request.getCategoryId());

            List<ParameterDefinition> parameters = parsedInfo.getParameters().stream()
                    .map(paramInfo -> convertToParameterDefinition(paramInfo, toolDefinition, null))
                    .collect(Collectors.toList());

            if (request.getParamNames() != null && request.getParamNames().length > 0) {
                applyHumanAnnotations(parameters, request);
            } else {
                copyDescriptionsToHumanReadable(parameters);
            }

            List<ResponseDefinition> responses = parsedInfo.getResponses().stream()
                    .map(respInfo -> {
                        ResponseDefinition response = new ResponseDefinition();
                        response.setToolDefinition(toolDefinition);
                        response.setStatusCode(respInfo.getStatusCode());
                        response.setDescription(respInfo.getDescription());
                        response.setType(respInfo.getType());
                        response.setSchema(respInfo.getSchema());

                        if (respInfo.getParameters() != null && !respInfo.getParameters().isEmpty()) {
                            List<ParameterDefinition> responseParams = respInfo.getParameters().stream()
                                    .map(paramInfo -> convertToResponseParameterDefinition(paramInfo, response, null))
                                    .collect(Collectors.toList());
                            response.setParameters(responseParams);
                        }

                        return response;
                    })
                    .collect(Collectors.toList());

            toolDefinition.setParameters(parameters);
            toolDefinition.setResponses(responses);

            ToolDefinition saved = toolDefinitionRepository.save(toolDefinition);
            metrics.recordToolRegistration(System.currentTimeMillis() - startTime);
            updateToolCount();
            return saved;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            metrics.recordRegistrationError();
            throw e;
        }
    }

    private void copyDescriptionsToHumanReadable(List<ParameterDefinition> parameters) {
        for (ParameterDefinition param : parameters) {
            if (param.getDescription() != null && !param.getDescription().trim().isEmpty()) {
                param.setHumanReadableDescription(param.getDescription());
            }
            if (!param.getNestedParameters().isEmpty()) {
                copyDescriptionsToHumanReadable(param.getNestedParameters());
            }
        }
    }

    private void applyHumanAnnotations(List<ParameterDefinition> parameters, ToolRegistrationRequest request) {
        String[] names = request.getParamNames();
        Integer[] nestingLevels = request.getParamNestingLevels();
        String[] humanDescriptions = request.getParamHumanDescriptions();
        String[] examples = request.getParamExamples();

        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            Integer nestingLevel = nestingLevels != null && i < nestingLevels.length ? nestingLevels[i] : 0;
            String humanDesc = humanDescriptions != null && i < humanDescriptions.length ? humanDescriptions[i] : null;
            String example = examples != null && i < examples.length ? examples[i] : null;

            findAndUpdateParameter(parameters, name, nestingLevel, humanDesc, example);
        }
    }

    private boolean findAndUpdateParameter(List<ParameterDefinition> parameters, String name, Integer nestingLevel,
                                           String humanDesc, String example) {
        for (ParameterDefinition param : parameters) {
            if (param.getName().equals(name) && param.getNestingLevel().equals(nestingLevel)) {
                if (humanDesc != null && !humanDesc.trim().isEmpty()) {
                    param.setHumanReadableDescription(humanDesc);
                } else if (param.getDescription() != null && !param.getDescription().trim().isEmpty()) {
                    param.setHumanReadableDescription(param.getDescription());
                }
                if (example != null && !example.trim().isEmpty()) {
                    param.setExample(example);
                }
                return true;
            }
            if (!param.getNestedParameters().isEmpty()) {
                if (findAndUpdateParameter(param.getNestedParameters(), name, nestingLevel, humanDesc, example)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Create or update a simple tool without OpenAPI parsing.
     * Useful for registering public APIs manually.
     * If the tool already exists, it will be updated.
     */
    @Transactional
    public ToolDefinition createSimpleTool(SimpleToolRequest request) {
        log.info("Creating/updating simple tool: {}", request.toolId());

        if (request.toolId() == null || request.toolId().isBlank()) {
            throw new IllegalArgumentException("Tool ID is required");
        }

        // Check if tool already exists - if so, update it
        ToolDefinition toolDefinition = toolDefinitionRepository.findByToolId(request.toolId())
                .orElse(new ToolDefinition());

        boolean isUpdate = toolDefinition.getId() != null;

        toolDefinition.setToolId(request.toolId());
        toolDefinition.setName(request.name() != null ? request.name() : request.toolId());
        toolDefinition.setDescription(request.description());
        toolDefinition.setHumanReadableDescription(request.description());
        toolDefinition.setOpenApiEndpoint(request.baseUrl() != null ? request.baseUrl() : "manual-registration");
        toolDefinition.setHttpMethod(request.httpMethod() != null ? request.httpMethod() : "GET");
        toolDefinition.setPath(request.path() != null ? request.path() : "/");
        toolDefinition.setBaseUrl(request.baseUrl());
        toolDefinition.setCategoryId(request.categoryId());

        // Parse inputSchema and create ParameterDefinition entries
        if (request.inputSchema() != null) {
            // Clear existing parameters if updating
            if (isUpdate) {
                toolDefinition.getParameters().clear();
            }
            List<ParameterDefinition> parameters = parseInputSchemaToParameters(request.inputSchema(), toolDefinition);
            toolDefinition.getParameters().addAll(parameters);
        }

        ToolDefinition saved = toolDefinitionRepository.save(toolDefinition);
        log.info("{} simple tool: {} (id={})", isUpdate ? "Updated" : "Created", saved.getToolId(), saved.getId());
        return saved;
    }

    /**
     * Parse a JSON Schema-like inputSchema map into ParameterDefinition entities.
     */
    @SuppressWarnings("unchecked")
    private List<ParameterDefinition> parseInputSchemaToParameters(Map<String, Object> inputSchema, ToolDefinition toolDefinition) {
        List<ParameterDefinition> parameters = new ArrayList<>();

        Object propertiesObj = inputSchema.get("properties");
        if (!(propertiesObj instanceof Map)) {
            return parameters;
        }

        Map<String, Object> properties = (Map<String, Object>) propertiesObj;
        List<String> requiredFields = new ArrayList<>();
        Object requiredObj = inputSchema.get("required");
        if (requiredObj instanceof List) {
            for (Object r : (List<?>) requiredObj) {
                requiredFields.add(r.toString());
            }
        }

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String paramName = entry.getKey();
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            Map<String, Object> paramSchema = (Map<String, Object>) entry.getValue();

            ParameterDefinition param = new ParameterDefinition();
            param.setToolDefinition(toolDefinition);
            param.setName(paramName);
            param.setType(paramSchema.get("type") != null ? paramSchema.get("type").toString() : "string");
            param.setDescription(paramSchema.get("description") != null ? paramSchema.get("description").toString() : null);
            param.setHumanReadableDescription(param.getDescription());
            param.setRequired(requiredFields.contains(paramName));
            param.setIn("body");
            param.setNestingLevel(0);

            if (paramSchema.get("default") != null) {
                param.setExample(paramSchema.get("default").toString());
            }

            parameters.add(param);
        }

        return parameters;
    }

    public List<ToolDefinition> getAllTools() {
        return toolDefinitionRepository.findAll();
    }

    public List<ToolDefinition> getToolsByCategory(String categoryId) {
        return toolDefinitionRepository.findByCategoryId(categoryId);
    }

    public ToolDefinition getToolById(Long id) {
        return toolDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found with id: " + id));
    }

    public ToolDefinition getToolByToolId(String toolId) {
        return toolDefinitionRepository.findByToolId(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found with toolId: " + toolId));
    }

    @Transactional
    public void deleteTool(Long id) {
        toolDefinitionRepository.deleteById(id);
        metrics.recordToolDeleted();
        updateToolCount();
    }

    /**
     * Update a tool's description and/or category.
     */
    @Transactional
    public ToolDefinition updateTool(Long id, ToolUpdateRequest request) {
        ToolDefinition tool = toolDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found with id: " + id));

        if (request.description() != null && !request.description().isBlank()) {
            tool.setDescription(request.description());
        }
        if (request.humanReadableDescription() != null && !request.humanReadableDescription().isBlank()) {
            tool.setHumanReadableDescription(request.humanReadableDescription());
        }
        if (request.categoryId() != null) {
            tool.setCategoryId(request.categoryId().isBlank() ? null : request.categoryId());
        }

        log.info("Updated tool: {} (id={})", tool.getToolId(), tool.getId());
        metrics.recordToolUpdated();
        return toolDefinitionRepository.save(tool);
    }

    public ParsedToolInfo previewTool(ToolRegistrationRequest request) {
        return openApiParserService.parseOpenApiEndpoint(
                request.getOpenApiEndpoint(),
                request.getPath(),
                request.getHttpMethod()
        );
    }

    public ParsedToolInfo previewToolFromContent(String toolId, String openApiContent, String path, String httpMethod) {
        return openApiParserService.parseOpenApiFromContent(openApiContent, path, httpMethod);
    }

    public boolean toolExists(String openApiEndpoint, String path, String httpMethod) {
        return toolDefinitionRepository.findAll().stream()
                .anyMatch(tool -> tool.getOpenApiEndpoint().equals(openApiEndpoint) &&
                        tool.getPath().equals(path) &&
                        tool.getHttpMethod().equalsIgnoreCase(httpMethod));
    }

    private ParameterDefinition convertToParameterDefinition(
            com.naag.toolregistry.dto.ParameterInfo paramInfo,
            ToolDefinition toolDefinition,
            ParameterDefinition parentParameter) {

        ParameterDefinition param = new ParameterDefinition();
        param.setToolDefinition(toolDefinition);
        param.setParentParameter(parentParameter);
        param.setName(paramInfo.getName());
        param.setDescription(paramInfo.getDescription());
        param.setType(paramInfo.getType());
        param.setRequired(paramInfo.getRequired());
        param.setIn(paramInfo.getIn());
        param.setFormat(paramInfo.getFormat());
        param.setExample(paramInfo.getExample());
        param.setNestingLevel(paramInfo.getNestingLevel());
        param.setEnumValues(paramInfo.getEnumValues());

        if (paramInfo.getNestedParameters() != null && !paramInfo.getNestedParameters().isEmpty()) {
            List<ParameterDefinition> nestedParams = paramInfo.getNestedParameters().stream()
                    .map(nestedInfo -> convertToParameterDefinition(nestedInfo, toolDefinition, param))
                    .collect(Collectors.toList());
            param.setNestedParameters(nestedParams);
        }

        return param;
    }

    private ParameterDefinition convertToResponseParameterDefinition(
            com.naag.toolregistry.dto.ParameterInfo paramInfo,
            ResponseDefinition responseDefinition,
            ParameterDefinition parentParameter) {

        ParameterDefinition param = new ParameterDefinition();
        param.setResponseDefinition(responseDefinition);
        param.setParentParameter(parentParameter);
        param.setName(paramInfo.getName());
        param.setDescription(paramInfo.getDescription());
        param.setType(paramInfo.getType());
        param.setRequired(paramInfo.getRequired());
        param.setIn(paramInfo.getIn());
        param.setFormat(paramInfo.getFormat());
        param.setExample(paramInfo.getExample());
        param.setNestingLevel(paramInfo.getNestingLevel());
        param.setEnumValues(paramInfo.getEnumValues());

        if (paramInfo.getNestedParameters() != null && !paramInfo.getNestedParameters().isEmpty()) {
            List<ParameterDefinition> nestedParams = paramInfo.getNestedParameters().stream()
                    .map(nestedInfo -> convertToResponseParameterDefinition(nestedInfo, responseDefinition, param))
                    .collect(Collectors.toList());
            param.setNestedParameters(nestedParams);
        }

        return param;
    }

    private void updateToolCount() {
        metrics.setTotalToolCount((int) toolDefinitionRepository.count());
    }
}
