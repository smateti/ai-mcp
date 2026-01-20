package com.naag.categoryadmin.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Tool {
    private String id;  // Will be populated from toolId
    private String name;
    private String description;
    private String serviceUrl;
    private String method;
    private String path;
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;
    private String categoryId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean active;

    // Custom setters to handle JSON field name differences from tool registry
    @JsonSetter("toolId")
    public void setToolId(String toolId) {
        this.id = toolId;
    }

    @JsonSetter("baseUrl")
    public void setBaseUrl(String baseUrl) {
        this.serviceUrl = baseUrl;
    }

    @JsonSetter("httpMethod")
    public void setHttpMethod(String httpMethod) {
        this.method = httpMethod;
    }
}
