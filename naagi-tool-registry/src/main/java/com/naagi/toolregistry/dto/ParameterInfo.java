package com.naagi.toolregistry.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParameterInfo {
    private String name;
    private String description;
    private String type;
    private Boolean required = false;
    private String in;
    private String format;
    private String example;
    private Integer nestingLevel = 0;
    private String enumValues;
    private List<ParameterInfo> nestedParameters = new ArrayList<>();

    public void setEnumValues(List<String> values) {
        if (values != null && !values.isEmpty()) {
            this.enumValues = String.join(",", values);
        }
    }
}
