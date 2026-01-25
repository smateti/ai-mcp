package com.example.servicedep.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDto {
    private String applicationId;
    private String name;
    private String description;
    private String owner;
    private String status;
    private String appType; // BATCH, MICROSERVICE, UI
    private List<ServiceDto> services;
}
