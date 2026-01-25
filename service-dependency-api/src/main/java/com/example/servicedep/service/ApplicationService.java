package com.example.servicedep.service;

import com.example.servicedep.entity.Application;
import com.example.servicedep.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;

    /**
     * Get all applications
     */
    public List<Application> getAllApplications() {
        return applicationRepository.findAll();
    }

    /**
     * Get applications by type, or all if type is null
     */
    public List<Application> getApplicationsByType(String appType) {
        if (appType == null || appType.isBlank()) {
            return applicationRepository.findAll();
        }
        return applicationRepository.findByAppType(appType.toUpperCase());
    }

    /**
     * Get application by applicationId (not database ID)
     */
    public Optional<Application> getApplicationByApplicationId(String applicationId) {
        return applicationRepository.findByApplicationId(applicationId);
    }

    /**
     * Get application with all its services, operations, and dependencies
     * Uses transactional context to load lazy collections
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<Application> getApplicationWithDetails(String applicationId) {
        Optional<Application> appOpt = applicationRepository.findByApplicationId(applicationId);
        // Force lazy loading of collections within transaction
        appOpt.ifPresent(app -> {
            app.getServices().size(); // Initialize services
            app.getServices().forEach(service -> {
                service.getOperations().size(); // Initialize operations
                service.getOperations().forEach(operation -> {
                    operation.getDependencies().size(); // Initialize dependencies
                });
            });
        });
        return appOpt;
    }
}
