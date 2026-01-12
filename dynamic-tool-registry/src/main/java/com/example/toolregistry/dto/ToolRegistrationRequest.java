package com.example.toolregistry.dto;

import lombok.Data;

@Data
public class ToolRegistrationRequest {
    private String toolId;
    private String openApiEndpoint;
    private String path;
    private String httpMethod;
    private String baseUrl;
    private String humanReadableDescription;

    // Arrays for parameter metadata
    private String[] paramNames;
    private String[] paramTypes;
    private Boolean[] paramRequired;
    private String[] paramIn;
    private String[] paramDescriptions;
    private Integer[] paramNestingLevels;
    private String[] paramHumanDescriptions;
    private String[] paramExamples;

    // Arrays for response metadata
    private String[] responseStatusCodes;
    private String[] responseDescriptions;
    private String[] responseTypes;
    private String[] responseHumanDescriptions;

    // Arrays for response parameter metadata
    private String[] responseParamNames;
    private String[] responseParamTypes;
    private String[] responseParamDescriptions;
    private Integer[] responseParamNestingLevels;
    private String[] responseParamStatusCodes;
    private String[] responseParamHumanDescriptions;
    private String[] responseParamExamples;
}
