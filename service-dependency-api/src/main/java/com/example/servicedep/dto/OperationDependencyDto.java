package com.example.servicedep.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationDependencyDto {
    private String dependentApplicationId;
    private String dependentServiceId;
    private String dependentOperationId;
    private String dependencyType; // SYNC, ASYNC, OPTIONAL
    private String description;
}
