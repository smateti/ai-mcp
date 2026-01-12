package com.example.toolregistry.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParameterInfo {
    private String name;
    private String description;
    private String type;
    private Boolean required;
    private String in;
    private String format;
    private String example;
    private Integer nestingLevel = 0;
    private List<String> enumValues = new ArrayList<>();
    private List<ParameterInfo> nestedParameters = new ArrayList<>();
}
