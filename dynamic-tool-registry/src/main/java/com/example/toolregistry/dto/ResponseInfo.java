package com.example.toolregistry.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ResponseInfo {
    private String statusCode;
    private String description;
    private String type;
    private String schema;
    private List<ParameterInfo> parameters = new ArrayList<>();
}
