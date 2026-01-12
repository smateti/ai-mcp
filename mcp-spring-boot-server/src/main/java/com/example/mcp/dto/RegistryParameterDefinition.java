package com.example.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegistryParameterDefinition {
    private Long id;
    private String name;
    private String description;
    private String humanReadableDescription;
    private String type;
    private Boolean required;
    private String in;
    private String format;
    private String example;
    private String defaultValue;
    private List<String> enumValues;
    private Integer nestingLevel;
    private List<RegistryParameterDefinition> nestedParameters = new ArrayList<>();
}
