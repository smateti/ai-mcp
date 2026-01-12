package com.example.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegistryResponseDefinition {
    private Long id;
    private String statusCode;
    private String description;
    private String type;
    private String schema;
}
