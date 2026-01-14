package com.example.categoryadmin.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoryDocumentDto {
    private Long id;
    private String documentId;
    private String documentName;
    private String documentDescription;
    private String documentType;
    private Boolean enabled;
    private LocalDateTime addedAt;
}
