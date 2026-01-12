package com.example.toolregistry.service;

import com.example.toolregistry.dto.ParsedToolInfo;
import com.example.toolregistry.dto.ToolRegistrationRequest;
import com.example.toolregistry.entity.ParameterDefinition;
import com.example.toolregistry.entity.ResponseDefinition;
import com.example.toolregistry.entity.ToolDefinition;
import com.example.toolregistry.repository.ToolDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolRegistrationService {

    private final OpenApiParserService openApiParserService;
    private final ToolDefinitionRepository toolDefinitionRepository;

    @Transactional
    public ToolDefinition registerTool(ToolRegistrationRequest request) {
        if (toolDefinitionRepository.existsByToolId(request.getToolId())) {
            throw new IllegalArgumentException("Tool with ID " + request.getToolId() + " already exists");
        }

        ParsedToolInfo parsedInfo = openApiParserService.parseOpenApiEndpoint(
                request.getOpenApiEndpoint(),
                request.getPath(),
                request.getHttpMethod()
        );

        ToolDefinition toolDefinition = new ToolDefinition();
        toolDefinition.setToolId(request.getToolId());
        toolDefinition.setName(parsedInfo.getName());
        toolDefinition.setDescription(parsedInfo.getDescription());

        // Copy OpenAPI description to human-readable if not provided by user
        if (request.getHumanReadableDescription() != null && !request.getHumanReadableDescription().trim().isEmpty()) {
            toolDefinition.setHumanReadableDescription(request.getHumanReadableDescription());
        } else if (parsedInfo.getDescription() != null && !parsedInfo.getDescription().trim().isEmpty()) {
            toolDefinition.setHumanReadableDescription(parsedInfo.getDescription());
        }

        toolDefinition.setOpenApiEndpoint(request.getOpenApiEndpoint());
        toolDefinition.setHttpMethod(parsedInfo.getHttpMethod());
        toolDefinition.setPath(parsedInfo.getPath());
        toolDefinition.setBaseUrl(request.getBaseUrl() != null ? request.getBaseUrl() : parsedInfo.getBaseUrl());

        List<ParameterDefinition> parameters = parsedInfo.getParameters().stream()
                .map(paramInfo -> convertToParameterDefinition(paramInfo, toolDefinition, null))
                .collect(Collectors.toList());

        // Apply human-readable descriptions and examples from the request
        if (request.getParamNames() != null && request.getParamNames().length > 0) {
            applyHumanAnnotations(parameters, request);
        } else {
            // If no user input, copy all OpenAPI descriptions to human-readable
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

                    // Convert response parameters
                    if (respInfo.getParameters() != null && !respInfo.getParameters().isEmpty()) {
                        List<ParameterDefinition> responseParams = respInfo.getParameters().stream()
                                .map(paramInfo -> convertToResponseParameterDefinition(paramInfo, response, null))
                                .collect(Collectors.toList());
                        response.setParameters(responseParams);
                    }

                    return response;
                })
                .collect(Collectors.toList());

        // Apply human-readable descriptions to responses
        if (request.getResponseStatusCodes() != null && request.getResponseStatusCodes().length > 0) {
            applyResponseHumanAnnotations(responses, request);
        } else {
            // If no user input, copy all OpenAPI response descriptions to human-readable
            for (ResponseDefinition response : responses) {
                if (response.getDescription() != null && !response.getDescription().trim().isEmpty()) {
                    response.setHumanReadableDescription(response.getDescription());
                }
                // Also copy descriptions for response parameters
                if (!response.getParameters().isEmpty()) {
                    copyDescriptionsToHumanReadable(response.getParameters());
                }
            }
        }

        // Apply human-readable descriptions to response parameters
        if (request.getResponseParamNames() != null && request.getResponseParamNames().length > 0) {
            applyResponseParameterHumanAnnotations(responses, request);
        }

        toolDefinition.setParameters(parameters);
        toolDefinition.setResponses(responses);

        return toolDefinitionRepository.save(toolDefinition);
    }

    private void copyDescriptionsToHumanReadable(List<ParameterDefinition> parameters) {
        for (ParameterDefinition param : parameters) {
            if (param.getDescription() != null && !param.getDescription().trim().isEmpty()) {
                param.setHumanReadableDescription(param.getDescription());
            }
            // Recursively process nested parameters
            if (!param.getNestedParameters().isEmpty()) {
                copyDescriptionsToHumanReadable(param.getNestedParameters());
            }
        }
    }

    private void applyResponseHumanAnnotations(List<ResponseDefinition> responses, ToolRegistrationRequest request) {
        String[] statusCodes = request.getResponseStatusCodes();
        String[] humanDescriptions = request.getResponseHumanDescriptions();

        if (humanDescriptions != null) {
            for (int i = 0; i < statusCodes.length && i < humanDescriptions.length; i++) {
                String statusCode = statusCodes[i];
                String humanDesc = humanDescriptions[i];

                // Find and update the response
                for (ResponseDefinition response : responses) {
                    if (response.getStatusCode().equals(statusCode)) {
                        if (humanDesc != null && !humanDesc.trim().isEmpty()) {
                            response.setHumanReadableDescription(humanDesc);
                        } else if (response.getDescription() != null && !response.getDescription().trim().isEmpty()) {
                            // Copy OpenAPI description to human-readable if user didn't provide one
                            response.setHumanReadableDescription(response.getDescription());
                        }
                        break;
                    }
                }
            }
        }
    }

    private void applyResponseParameterHumanAnnotations(List<ResponseDefinition> responses, ToolRegistrationRequest request) {
        String[] names = request.getResponseParamNames();
        Integer[] nestingLevels = request.getResponseParamNestingLevels();
        String[] statusCodes = request.getResponseParamStatusCodes();
        String[] humanDescriptions = request.getResponseParamHumanDescriptions();
        String[] examples = request.getResponseParamExamples();

        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            Integer nestingLevel = nestingLevels != null && i < nestingLevels.length ? nestingLevels[i] : 0;
            String statusCode = statusCodes != null && i < statusCodes.length ? statusCodes[i] : null;
            String humanDesc = humanDescriptions != null && i < humanDescriptions.length ? humanDescriptions[i] : null;
            String example = examples != null && i < examples.length ? examples[i] : null;

            // Find the response by status code
            for (ResponseDefinition response : responses) {
                if (statusCode != null && response.getStatusCode().equals(statusCode)) {
                    // Find and update the parameter within this response
                    findAndUpdateParameter(response.getParameters(), name, nestingLevel, humanDesc, example);
                    break;
                }
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

            // Find and update the parameter
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
                    // Copy OpenAPI description to human-readable if user didn't provide one
                    param.setHumanReadableDescription(param.getDescription());
                }
                if (example != null && !example.trim().isEmpty()) {
                    param.setExample(example);
                }
                return true;
            }
            // Search in nested parameters
            if (!param.getNestedParameters().isEmpty()) {
                if (findAndUpdateParameter(param.getNestedParameters(), name, nestingLevel, humanDesc, example)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Transactional
    public ToolDefinition updateToolDescription(Long id, String description) {
        ToolDefinition tool = toolDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found with id: " + id));

        tool.setDescription(description);
        return toolDefinitionRepository.save(tool);
    }

    @Transactional
    public ToolDefinition updateToolDescriptions(Long id, String description, String humanReadableDescription, String baseUrl) {
        ToolDefinition tool = toolDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found with id: " + id));

        if (description != null) {
            tool.setDescription(description);
        }
        if (humanReadableDescription != null) {
            tool.setHumanReadableDescription(humanReadableDescription);
        }
        if (baseUrl != null) {
            tool.setBaseUrl(baseUrl);
        }
        return toolDefinitionRepository.save(tool);
    }

    @Transactional
    public void updateParameterDescription(Long toolId, String parameterName, String description) {
        ToolDefinition tool = toolDefinitionRepository.findById(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found with id: " + toolId));

        updateParameterDescriptionRecursive(tool.getParameters(), parameterName, description);
        toolDefinitionRepository.save(tool);
    }

    @Transactional
    public void updateParameterHumanDescription(Long parameterId, String humanDescription) {
        ToolDefinition tool = toolDefinitionRepository.findAll().stream()
                .filter(t -> containsParameterId(t.getParameters(), parameterId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Parameter not found with id: " + parameterId));

        updateParameterHumanDescriptionRecursive(tool.getParameters(), parameterId, humanDescription);
        toolDefinitionRepository.save(tool);
    }

    @Transactional
    public void updateParameterExample(Long parameterId, String example) {
        ToolDefinition tool = toolDefinitionRepository.findAll().stream()
                .filter(t -> containsParameterId(t.getParameters(), parameterId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Parameter not found with id: " + parameterId));

        updateParameterExampleRecursive(tool.getParameters(), parameterId, example);
        toolDefinitionRepository.save(tool);
    }

    @Transactional
    public void updateResponseHumanDescription(Long responseId, String humanDescription) {
        ToolDefinition tool = toolDefinitionRepository.findAll().stream()
                .filter(t -> t.getResponses().stream().anyMatch(r -> r.getId().equals(responseId)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Response not found with id: " + responseId));

        for (ResponseDefinition response : tool.getResponses()) {
            if (response.getId().equals(responseId)) {
                response.setHumanReadableDescription(humanDescription);
                break;
            }
        }
        toolDefinitionRepository.save(tool);
    }

    private void updateParameterDescriptionRecursive(List<ParameterDefinition> parameters, String parameterName, String description) {
        for (ParameterDefinition param : parameters) {
            if (param.getName().equals(parameterName)) {
                param.setDescription(description);
                return;
            }
            if (!param.getNestedParameters().isEmpty()) {
                updateParameterDescriptionRecursive(param.getNestedParameters(), parameterName, description);
            }
        }
    }

    private void updateParameterHumanDescriptionRecursive(List<ParameterDefinition> parameters, Long parameterId, String humanDescription) {
        for (ParameterDefinition param : parameters) {
            if (param.getId().equals(parameterId)) {
                param.setHumanReadableDescription(humanDescription);
                return;
            }
            if (!param.getNestedParameters().isEmpty()) {
                updateParameterHumanDescriptionRecursive(param.getNestedParameters(), parameterId, humanDescription);
            }
        }
    }

    private void updateParameterExampleRecursive(List<ParameterDefinition> parameters, Long parameterId, String example) {
        for (ParameterDefinition param : parameters) {
            if (param.getId().equals(parameterId)) {
                param.setExample(example);
                return;
            }
            if (!param.getNestedParameters().isEmpty()) {
                updateParameterExampleRecursive(param.getNestedParameters(), parameterId, example);
            }
        }
    }

    private boolean containsParameterId(List<ParameterDefinition> parameters, Long parameterId) {
        for (ParameterDefinition param : parameters) {
            if (param.getId().equals(parameterId)) {
                return true;
            }
            if (!param.getNestedParameters().isEmpty() && containsParameterId(param.getNestedParameters(), parameterId)) {
                return true;
            }
        }
        return false;
    }

    public List<ToolDefinition> getAllTools() {
        return toolDefinitionRepository.findAll();
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
    }

    public ParsedToolInfo previewTool(ToolRegistrationRequest request) {
        return openApiParserService.parseOpenApiEndpoint(
                request.getOpenApiEndpoint(),
                request.getPath(),
                request.getHttpMethod()
        );
    }

    public boolean toolExists(String openApiEndpoint, String path, String httpMethod) {
        return toolDefinitionRepository.findAll().stream()
                .anyMatch(tool -> tool.getOpenApiEndpoint().equals(openApiEndpoint) &&
                        tool.getPath().equals(path) &&
                        tool.getHttpMethod().equalsIgnoreCase(httpMethod));
    }

    private ParameterDefinition convertToParameterDefinition(
            com.example.toolregistry.dto.ParameterInfo paramInfo,
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

        // Recursively convert nested parameters
        if (paramInfo.getNestedParameters() != null && !paramInfo.getNestedParameters().isEmpty()) {
            List<ParameterDefinition> nestedParams = paramInfo.getNestedParameters().stream()
                    .map(nestedInfo -> convertToParameterDefinition(nestedInfo, toolDefinition, param))
                    .collect(Collectors.toList());
            param.setNestedParameters(nestedParams);
        }

        return param;
    }

    private ParameterDefinition convertToResponseParameterDefinition(
            com.example.toolregistry.dto.ParameterInfo paramInfo,
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

        // Recursively convert nested parameters
        if (paramInfo.getNestedParameters() != null && !paramInfo.getNestedParameters().isEmpty()) {
            List<ParameterDefinition> nestedParams = paramInfo.getNestedParameters().stream()
                    .map(nestedInfo -> convertToResponseParameterDefinition(nestedInfo, responseDefinition, param))
                    .collect(Collectors.toList());
            param.setNestedParameters(nestedParams);
        }

        return param;
    }
}
