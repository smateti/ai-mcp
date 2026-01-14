package com.example.categoryadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCategoryRequest {

    @NotBlank(message = "Category ID is required")
    @Size(min = 3, max = 50, message = "Category ID must be between 3 and 50 characters")
    private String categoryId;

    @NotBlank(message = "Category name is required")
    @Size(min = 3, max = 100, message = "Category name must be between 3 and 100 characters")
    private String name;

    private String description;

    private Integer displayOrder = 0;
}
