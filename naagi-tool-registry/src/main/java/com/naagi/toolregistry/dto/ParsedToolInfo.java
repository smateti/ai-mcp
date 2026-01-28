package com.naagi.toolregistry.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsedToolInfo {
    private String name;
    private String description;
    private String httpMethod;
    private String path;
    private String baseUrl;
    private List<ParameterInfo> parameters = new ArrayList<>();
    private List<ResponseInfo> responses = new ArrayList<>();
}
