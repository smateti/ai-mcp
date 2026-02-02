package com.example.servicedep.controller;

import com.example.servicedep.dto.OperationDto;
import com.example.servicedep.service.MappingService;
import com.example.servicedep.repository.ServiceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
@Tag(name = "Service Operations API", description = "APIs for retrieving service operations/functions")
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final MappingService mappingService;

    /**
     * Get all operations (functions) for a given service.
     * This is the second endpoint in the tool-chaining scenario:
     *   1. GET /api/applications/{appId}/services  → returns serviceIds
     *   2. GET /api/services/{serviceId}/operations → returns operations for that service
     */
    @GetMapping("/{serviceId}/operations")
    @Transactional(readOnly = true)
    @Operation(
        summary = "Get operations for a service",
        description = "Retrieves all operations (functions) for a specific service by its serviceId"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Operations found"),
        @ApiResponse(responseCode = "404", description = "Service not found")
    })
    public ResponseEntity<List<OperationDto>> getServiceOperations(
            @Parameter(description = "Service ID (e.g., SVC-USER-001)")
            @PathVariable String serviceId) {

        return serviceRepository.findByServiceId(serviceId)
                .map(service -> {
                    // Force lazy loading
                    service.getOperations().size();
                    service.getOperations().forEach(op -> op.getDependencies().size());

                    List<OperationDto> operations = service.getOperations().stream()
                            .map(mappingService::toOperationDto)
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(operations);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
