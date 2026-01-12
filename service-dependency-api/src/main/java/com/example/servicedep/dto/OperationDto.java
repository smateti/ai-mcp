package com.example.servicedep.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationDto {
    private String operationId;
    private String name;
    private String description;
    private String httpMethod;
    private String path;
    private List<OperationDependencyDto> dependencies;
}
