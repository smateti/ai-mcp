package com.example.categoryadmin.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryDto {
    private Long id;
    private String categoryId;
    private String name;
    private String description;
    private Boolean active;
    private Integer displayOrder;
    private Integer toolCount;
    private Integer documentCount;
    private List<CategoryToolDto> tools = new ArrayList<>();
    private List<CategoryDocumentDto> documents = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
