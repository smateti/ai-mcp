package com.example.categoryadmin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddToolRequest {

    @NotBlank(message = "Tool ID is required")
    private String toolId;

    private String toolName;

    private String toolDescription;

    private Integer priority = 0;
}
