package com.example.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegistryToolDefinition {
    private Long id;
    private String toolId;
    private String name;
    private String description;
    private String humanReadableDescription;
    private String openApiEndpoint;
    private String httpMethod;
    private String path;
    private String baseUrl;
    private List<RegistryParameterDefinition> parameters;
    private List<RegistryResponseDefinition> responses;
}
