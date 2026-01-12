package com.example.toolregistry.service;

import com.example.toolregistry.dto.ParsedToolInfo;
import com.example.toolregistry.dto.ParameterInfo;
import com.example.toolregistry.dto.ResponseInfo;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OpenApiParserService {

    public ParsedToolInfo parseOpenApiEndpoint(String openApiUrl, String path, String method) {
        log.info("Parsing OpenAPI from: {}, path: {}, method: {}", openApiUrl, path, method);

        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(openApiUrl, null, null);

        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            log.warn("OpenAPI parsing warnings: {}", result.getMessages());
        }

        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI == null) {
            throw new IllegalArgumentException("Failed to parse OpenAPI specification from: " + openApiUrl);
        }

        log.debug("Available paths: {}", openAPI.getPaths().keySet());

        PathItem pathItem = openAPI.getPaths().get(path);
        if (pathItem == null) {
            throw new IllegalArgumentException("Path not found in OpenAPI spec: " + path);
        }

        Operation operation = getOperation(pathItem, method);
        if (operation == null) {
            throw new IllegalArgumentException("Method " + method + " not found for path: " + path);
        }

        log.debug("Operation found: {}", operation);

        ParsedToolInfo toolInfo = new ParsedToolInfo();
        toolInfo.setName(operation.getOperationId() != null ? operation.getOperationId() : method + "_" + path.replaceAll("/", "_"));
        toolInfo.setDescription(operation.getDescription() != null ? operation.getDescription() : operation.getSummary());
        toolInfo.setPath(path);
        toolInfo.setHttpMethod(method.toUpperCase());
        toolInfo.setBaseUrl(extractBaseUrl(openAPI));
        toolInfo.setParameters(parseParameters(operation, openAPI));
        toolInfo.setResponses(parseResponses(operation, openAPI));

        log.info("Parsed tool: {} with {} parameters", toolInfo.getName(), toolInfo.getParameters().size());

        return toolInfo;
    }

    private Operation getOperation(PathItem pathItem, String method) {
        return switch (method.toUpperCase()) {
            case "GET" -> pathItem.getGet();
            case "POST" -> pathItem.getPost();
            case "PUT" -> pathItem.getPut();
            case "DELETE" -> pathItem.getDelete();
            case "PATCH" -> pathItem.getPatch();
            default -> null;
        };
    }

    private List<ParameterInfo> parseParameters(Operation operation, OpenAPI openAPI) {
        List<ParameterInfo> parameters = new ArrayList<>();

        if (operation.getParameters() != null) {
            log.debug("Found {} parameters", operation.getParameters().size());
            for (Parameter param : operation.getParameters()) {
                ParameterInfo paramInfo = new ParameterInfo();
                paramInfo.setName(param.getName());
                paramInfo.setDescription(param.getDescription());
                paramInfo.setRequired(param.getRequired() != null ? param.getRequired() : false);
                paramInfo.setIn(param.getIn());

                if (param.getSchema() != null) {
                    paramInfo.setType(param.getSchema().getType());
                    paramInfo.setFormat(param.getSchema().getFormat());
                    if (param.getExample() != null) {
                        paramInfo.setExample(param.getExample().toString());
                    }

                    // Extract enum values if present
                    if (param.getSchema().getEnum() != null && !param.getSchema().getEnum().isEmpty()) {
                        List<String> enumValues = new ArrayList<>();
                        for (Object enumValue : param.getSchema().getEnum()) {
                            enumValues.add(enumValue.toString());
                        }
                        paramInfo.setEnumValues(enumValues);
                        log.debug("Found enum values for parameter {}: {}", param.getName(), enumValues);
                    }

                    // Handle array types
                    if ("array".equals(param.getSchema().getType()) && param.getSchema().getItems() != null) {
                        paramInfo.setType("array[" + param.getSchema().getItems().getType() + "]");
                    }
                }

                log.debug("Parsed parameter: {} ({})", paramInfo.getName(), paramInfo.getType());
                parameters.add(paramInfo);
            }
        } else {
            log.debug("No parameters found in operation");
        }

        // Parse request body parameters
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            log.debug("Found request body");
            Map<String, MediaType> content = operation.getRequestBody().getContent();
            content.forEach((contentType, mediaType) -> {
                if (mediaType.getSchema() != null) {
                    log.debug("Parsing schema for content type: {}", contentType);
                    parseSchemaProperties(mediaType.getSchema(), parameters, "body", openAPI);
                }
            });
        }

        return parameters;
    }


    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void parseSchemaProperties(Schema schema, List<ParameterInfo> parameters, String in, OpenAPI openAPI) {
        parseSchemaPropertiesRecursive(schema, parameters, in, openAPI, 0);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void parseSchemaPropertiesRecursive(Schema schema, List<ParameterInfo> parameters, String in, OpenAPI openAPI, int nestingLevel) {
        // Handle $ref references
        if (schema.get$ref() != null) {
            log.debug("Found schema reference: {}", schema.get$ref());
            String refName = schema.get$ref().replace("#/components/schemas/", "");
            if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
                Schema refSchema = openAPI.getComponents().getSchemas().get(refName);
                if (refSchema != null) {
                    log.debug("Resolved reference to: {}", refName);
                    parseSchemaPropertiesRecursive(refSchema, parameters, in, openAPI, nestingLevel);
                    return;
                }
            }
        }

        if (schema.getProperties() != null) {
            log.debug("Found {} properties in schema at nesting level {}", schema.getProperties().size(), nestingLevel);
            schema.getProperties().forEach((propName, propSchema) -> {
                ParameterInfo paramInfo = new ParameterInfo();
                paramInfo.setName(String.valueOf(propName));
                paramInfo.setNestingLevel(nestingLevel);

                Schema actualPropSchema = (Schema) propSchema;
                paramInfo.setDescription(actualPropSchema.getDescription());
                paramInfo.setType(actualPropSchema.getType());
                paramInfo.setFormat(actualPropSchema.getFormat());
                paramInfo.setIn(in);
                paramInfo.setRequired(schema.getRequired() != null && schema.getRequired().contains(String.valueOf(propName)));

                if (actualPropSchema.getExample() != null) {
                    paramInfo.setExample(actualPropSchema.getExample().toString());
                }

                // Extract enum values if present
                if (actualPropSchema.getEnum() != null && !actualPropSchema.getEnum().isEmpty()) {
                    List<String> enumValues = new ArrayList<>();
                    for (Object enumValue : actualPropSchema.getEnum()) {
                        enumValues.add(enumValue.toString());
                    }
                    paramInfo.setEnumValues(enumValues);
                    log.debug("Found enum values for {}: {}", propName, enumValues);
                }

                // Handle nested object with properties
                if ("object".equals(actualPropSchema.getType()) && actualPropSchema.getProperties() != null) {
                    log.debug("Found nested object: {} at level {}", propName, nestingLevel);
                    parseSchemaPropertiesRecursive(actualPropSchema, paramInfo.getNestedParameters(), in, openAPI, nestingLevel + 1);
                }

                // Handle nested object references
                if (actualPropSchema.get$ref() != null) {
                    String refName = actualPropSchema.get$ref().replace("#/components/schemas/", "");
                    paramInfo.setType("object (" + refName + ")");

                    // Recursively parse the referenced schema
                    if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
                        Schema refSchema = openAPI.getComponents().getSchemas().get(refName);
                        if (refSchema != null) {
                            log.debug("Recursively parsing referenced schema: {}", refName);
                            parseSchemaPropertiesRecursive(refSchema, paramInfo.getNestedParameters(), in, openAPI, nestingLevel + 1);
                        }
                    }
                }

                // Handle array types
                if ("array".equals(actualPropSchema.getType()) && actualPropSchema.getItems() != null) {
                    Schema items = actualPropSchema.getItems();
                    if (items.get$ref() != null) {
                        String refName = items.get$ref().replace("#/components/schemas/", "");
                        paramInfo.setType("array[" + refName + "]");

                        // Recursively parse the array item schema
                        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
                            Schema refSchema = openAPI.getComponents().getSchemas().get(refName);
                            if (refSchema != null) {
                                log.debug("Recursively parsing array item schema: {}", refName);
                                parseSchemaPropertiesRecursive(refSchema, paramInfo.getNestedParameters(), in, openAPI, nestingLevel + 1);
                            }
                        }
                    } else if ("object".equals(items.getType()) && items.getProperties() != null) {
                        paramInfo.setType("array[object]");
                        parseSchemaPropertiesRecursive(items, paramInfo.getNestedParameters(), in, openAPI, nestingLevel + 1);
                    } else {
                        paramInfo.setType("array[" + items.getType() + "]");
                    }
                }

                log.debug("Parsed property: {} ({}) at level {}", paramInfo.getName(), paramInfo.getType(), nestingLevel);
                parameters.add(paramInfo);
            });
        } else {
            log.debug("No properties found in schema at nesting level {}", nestingLevel);
        }
    }

    private String extractBaseUrl(OpenAPI openAPI) {
        if (openAPI.getServers() != null && !openAPI.getServers().isEmpty()) {
            return openAPI.getServers().get(0).getUrl();
        }
        return null;
    }

    private List<ResponseInfo> parseResponses(Operation operation, OpenAPI openAPI) {
        List<ResponseInfo> responses = new ArrayList<>();

        if (operation.getResponses() != null) {
            operation.getResponses().forEach((statusCode, apiResponse) -> {
                ResponseInfo responseInfo = new ResponseInfo();
                responseInfo.setStatusCode(statusCode);
                responseInfo.setDescription(apiResponse.getDescription());

                if (apiResponse.getContent() != null) {
                    apiResponse.getContent().forEach((contentType, mediaType) -> {
                        if (mediaType.getSchema() != null) {
                            responseInfo.setType(mediaType.getSchema().getType());
                            responseInfo.setSchema(mediaType.getSchema().toString());

                            // Parse response schema properties recursively
                            List<ParameterInfo> responseParameters = new ArrayList<>();
                            parseResponseSchema(mediaType.getSchema(), responseParameters, openAPI, 0);
                            responseInfo.setParameters(responseParameters);
                        }
                    });
                }

                responses.add(responseInfo);
            });
        }

        return responses;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void parseResponseSchema(Schema schema, List<ParameterInfo> parameters, OpenAPI openAPI, int nestingLevel) {
        // Handle $ref references
        if (schema.get$ref() != null) {
            String refName = schema.get$ref().replace("#/components/schemas/", "");
            if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
                Schema refSchema = openAPI.getComponents().getSchemas().get(refName);
                if (refSchema != null) {
                    parseResponseSchema(refSchema, parameters, openAPI, nestingLevel);
                    return;
                }
            }
        }

        if (schema.getProperties() != null) {
            schema.getProperties().forEach((propName, propSchema) -> {
                ParameterInfo paramInfo = new ParameterInfo();
                paramInfo.setName(String.valueOf(propName));
                paramInfo.setNestingLevel(nestingLevel);

                Schema actualPropSchema = (Schema) propSchema;
                paramInfo.setType(actualPropSchema.getType() != null ? actualPropSchema.getType() : "object");
                paramInfo.setDescription(actualPropSchema.getDescription());
                paramInfo.setExample(actualPropSchema.getExample() != null ? actualPropSchema.getExample().toString() : null);
                paramInfo.setRequired(false); // Response properties are typically not "required" in the same sense
                paramInfo.setIn("response");

                // Extract enum values if present
                if (actualPropSchema.getEnum() != null && !actualPropSchema.getEnum().isEmpty()) {
                    List<String> enumValues = new ArrayList<>();
                    for (Object enumValue : actualPropSchema.getEnum()) {
                        enumValues.add(enumValue.toString());
                    }
                    paramInfo.setEnumValues(enumValues);
                    log.debug("Found enum values in response for {}: {}", propName, enumValues);
                }

                // Handle nested objects with properties
                if ("object".equals(actualPropSchema.getType()) && actualPropSchema.getProperties() != null) {
                    parseResponseSchema(actualPropSchema, paramInfo.getNestedParameters(), openAPI, nestingLevel + 1);
                }

                // Handle nested object references
                if (actualPropSchema.get$ref() != null) {
                    String refName = actualPropSchema.get$ref().replace("#/components/schemas/", "");
                    if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
                        Schema refSchema = openAPI.getComponents().getSchemas().get(refName);
                        if (refSchema != null) {
                            parseResponseSchema(refSchema, paramInfo.getNestedParameters(), openAPI, nestingLevel + 1);
                        }
                    }
                }

                // Handle arrays
                if ("array".equals(actualPropSchema.getType()) && actualPropSchema.getItems() != null) {
                    Schema items = actualPropSchema.getItems();
                    if (items.get$ref() != null) {
                        String refName = items.get$ref().replace("#/components/schemas/", "");
                        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
                            Schema refSchema = openAPI.getComponents().getSchemas().get(refName);
                            if (refSchema != null) {
                                parseResponseSchema(refSchema, paramInfo.getNestedParameters(), openAPI, nestingLevel + 1);
                            }
                        }
                    } else if (items.getProperties() != null) {
                        parseResponseSchema(items, paramInfo.getNestedParameters(), openAPI, nestingLevel + 1);
                    }
                }

                parameters.add(paramInfo);
            });
        }
    }
}
