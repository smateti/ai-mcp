package com.example.servicedep.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDto {
    private String serviceId;
    private String name;
    private String description;
    private String endpoint;
    private String protocol;
    private List<OperationDto> operations;
}
