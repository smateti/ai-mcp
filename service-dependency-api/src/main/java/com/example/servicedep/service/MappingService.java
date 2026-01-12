package com.example.servicedep.service;

import com.example.servicedep.dto.ApplicationDto;
import com.example.servicedep.dto.OperationDependencyDto;
import com.example.servicedep.dto.OperationDto;
import com.example.servicedep.dto.ServiceDto;
import com.example.servicedep.entity.Application;
import com.example.servicedep.entity.Operation;
import com.example.servicedep.entity.OperationDependency;
import com.example.servicedep.entity.Service;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class MappingService {

    public ApplicationDto toApplicationDto(Application app) {
        ApplicationDto dto = new ApplicationDto();
        dto.setApplicationId(app.getApplicationId());
        dto.setName(app.getName());
        dto.setDescription(app.getDescription());
        dto.setOwner(app.getOwner());
        dto.setStatus(app.getStatus());

        // Map services
        if (app.getServices() != null) {
            dto.setServices(app.getServices().stream()
                .map(this::toServiceDto)
                .collect(Collectors.toList()));
        }

        return dto;
    }

    public ServiceDto toServiceDto(Service service) {
        ServiceDto dto = new ServiceDto();
        dto.setServiceId(service.getServiceId());
        dto.setName(service.getName());
        dto.setDescription(service.getDescription());
        dto.setEndpoint(service.getEndpoint());
        dto.setProtocol(service.getProtocol());

        // Map operations
        if (service.getOperations() != null) {
            dto.setOperations(service.getOperations().stream()
                .map(this::toOperationDto)
                .collect(Collectors.toList()));
        }

        return dto;
    }

    public OperationDto toOperationDto(Operation operation) {
        OperationDto dto = new OperationDto();
        dto.setOperationId(operation.getOperationId());
        dto.setName(operation.getName());
        dto.setDescription(operation.getDescription());
        dto.setHttpMethod(operation.getHttpMethod());
        dto.setPath(operation.getPath());

        // Map dependencies
        if (operation.getDependencies() != null) {
            dto.setDependencies(operation.getDependencies().stream()
                .map(this::toOperationDependencyDto)
                .collect(Collectors.toList()));
        }

        return dto;
    }

    public OperationDependencyDto toOperationDependencyDto(OperationDependency dependency) {
        OperationDependencyDto dto = new OperationDependencyDto();
        dto.setDependentApplicationId(dependency.getDependentApplicationId());
        dto.setDependentServiceId(dependency.getDependentServiceId());
        dto.setDependentOperationId(dependency.getDependentOperationId());
        dto.setDependencyType(dependency.getDependencyType());
        dto.setDescription(dependency.getDescription());
        return dto;
    }
}
