package com.example.userchat.dto;

import lombok.Data;

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
}
