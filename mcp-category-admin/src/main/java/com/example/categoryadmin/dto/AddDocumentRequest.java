package com.example.categoryadmin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddDocumentRequest {

    @NotBlank(message = "Document ID is required")
    private String documentId;

    private String documentName;

    private String documentDescription;

    private String documentType;
}
