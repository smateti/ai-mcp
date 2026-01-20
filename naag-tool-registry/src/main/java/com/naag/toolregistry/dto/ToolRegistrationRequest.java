package com.naag.toolregistry.dto;

import lombok.Data;

@Data
public class ToolRegistrationRequest {
    private String toolId;
    private String openApiEndpoint;
    private String path;
    private String httpMethod;
    private String baseUrl;
    private String humanReadableDescription;
    private String categoryId;

    // Parameter annotations
    private String[] paramNames;
    private Integer[] paramNestingLevels;
    private String[] paramHumanDescriptions;
    private String[] paramExamples;

    // Response annotations
    private String[] responseStatusCodes;
    private String[] responseHumanDescriptions;

    // Response parameter annotations
    private String[] responseParamNames;
    private Integer[] responseParamNestingLevels;
    private String[] responseParamStatusCodes;
    private String[] responseParamHumanDescriptions;
    private String[] responseParamExamples;
}
