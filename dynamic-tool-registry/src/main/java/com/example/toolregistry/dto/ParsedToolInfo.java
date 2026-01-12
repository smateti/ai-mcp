package com.example.toolregistry.dto;

import lombok.Data;

import java.util.List;

@Data
public class ParsedToolInfo {
    private String name;
    private String description;
    private String path;
    private String httpMethod;
    private String baseUrl;
    private List<ParameterInfo> parameters;
    private List<ResponseInfo> responses;
}
