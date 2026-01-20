package com.naag.toolregistry.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseInfo {
    private String statusCode;
    private String description;
    private String type;
    private String schema;
    private List<ParameterInfo> parameters = new ArrayList<>();
}
