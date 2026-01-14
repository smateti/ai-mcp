package com.example.categoryadmin.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoryToolDto {
    private Long id;
    private String toolId;
    private String toolName;
    private String toolDescription;
    private Boolean enabled;
    private Integer priority;
    private LocalDateTime addedAt;
}
