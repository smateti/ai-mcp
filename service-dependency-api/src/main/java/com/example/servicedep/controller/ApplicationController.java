package com.example.servicedep.controller;

import com.example.servicedep.dto.ApplicationDto;
import com.example.servicedep.dto.ServiceDto;
import com.example.servicedep.entity.Application;
import com.example.servicedep.service.ApplicationService;
import com.example.servicedep.service.MappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Tag(name = "Application Dependency API", description = "APIs for managing application service dependencies")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final MappingService mappingService;

    /**
     * Endpoint 1: Get application by ID with all its services
     * Returns the application with list of services, but services don't include operations yet
     */
    @GetMapping("/{applicationId}")
    @Operation(
        summary = "Get application by ID",
        description = "Retrieves an application with all its services (operations not included in this endpoint)"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Application found",
            content = @Content(schema = @Schema(implementation = ApplicationDto.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Application not found"
        )
    })
    public ResponseEntity<ApplicationDto> getApplication(
        @Parameter(description = "Application ID (e.g., APP-USER-MGMT)")
        @PathVariable String applicationId) {

        return applicationService.getApplicationByApplicationId(applicationId)
            .map(app -> {
                ApplicationDto dto = mappingService.toApplicationDto(app);
                // Remove operations from services for this endpoint (just show service list)
                if (dto.getServices() != null) {
                    dto.getServices().forEach(service -> service.setOperations(null));
                }
                return ResponseEntity.ok(dto);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Endpoint 2: Get services for an application with operations and their dependencies
     * Returns detailed information including all operations and their cross-service dependencies
     */
    @GetMapping("/{applicationId}/services")
    @Operation(
        summary = "Get application services with operations and dependencies",
        description = "Retrieves all services for an application including operations and their cross-service dependencies"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Services found with operations and dependencies",
            content = @Content(schema = @Schema(implementation = ServiceDto.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Application not found"
        )
    })
    public ResponseEntity<List<ServiceDto>> getApplicationServicesWithDependencies(
        @Parameter(description = "Application ID (e.g., APP-USER-MGMT)")
        @PathVariable String applicationId) {

        return applicationService.getApplicationWithDetails(applicationId)
            .map(app -> {
                List<ServiceDto> services = app.getServices().stream()
                    .map(mappingService::toServiceDto)
                    .collect(Collectors.toList());
                return ResponseEntity.ok(services);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Bonus endpoint: List all applications (useful for discovery)
     */
    @GetMapping
    @Operation(
        summary = "List all applications",
        description = "Retrieves a list of all applications (without services)"
    )
    @ApiResponse(
        responseCode = "200",
        description = "List of applications",
        content = @Content(schema = @Schema(implementation = ApplicationDto.class))
    )
    public ResponseEntity<List<ApplicationDto>> getAllApplications() {
        List<ApplicationDto> applications = applicationService.getAllApplications().stream()
            .map(app -> {
                ApplicationDto dto = new ApplicationDto();
                dto.setApplicationId(app.getApplicationId());
                dto.setName(app.getName());
                dto.setDescription(app.getDescription());
                dto.setOwner(app.getOwner());
                dto.setStatus(app.getStatus());
                dto.setServices(null); // Don't include services in list view
                return dto;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(applications);
    }
}
