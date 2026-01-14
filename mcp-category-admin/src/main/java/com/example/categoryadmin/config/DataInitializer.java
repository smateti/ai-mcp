package com.example.categoryadmin.config;

import com.example.categoryadmin.entity.Category;
import com.example.categoryadmin.entity.CategoryDocument;
import com.example.categoryadmin.entity.CategoryTool;
import com.example.categoryadmin.repository.CategoryDocumentRepository;
import com.example.categoryadmin.repository.CategoryRepository;
import com.example.categoryadmin.repository.CategoryToolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final CategoryToolRepository categoryToolRepository;
    private final CategoryDocumentRepository categoryDocumentRepository;

    @Override
    public void run(String... args) {
        if (categoryRepository.count() > 0) {
            log.info("Data already initialized, skipping...");
            return;
        }

        log.info("Initializing sample data...");

        // Category 1: Batch App Development
        Category batchDev = new Category(
            "batch-dev",
            "Batch App Development",
            "Tools and documents for developing Nimbus batch applications"
        );
        batchDev.setDisplayOrder(1);
        batchDev = categoryRepository.save(batchDev);

        // Add tools for batch development
        addTool(batchDev, "rag_query", "RAG Query", "Query batch development documents", 10);
        addTool(batchDev, "rag_ingest", "RAG Ingest", "Ingest batch documentation", 9);

        // Add documents for batch development
        addDocument(batchDev, "doc1", "Nimbus Batch Documentation", "Complete guide for Nimbus batch development", "pdf");

        // Category 2: Service Development
        Category serviceDev = new Category(
            "service-dev",
            "Service Development",
            "Tools and documents for developing microservices and REST APIs"
        );
        serviceDev.setDisplayOrder(2);
        serviceDev = categoryRepository.save(serviceDev);

        // Add tools for service development
        addTool(serviceDev, "get_application_by_id", "Get Application", "Retrieve application details", 10);
        addTool(serviceDev, "get_application_services_with_dependencies", "Get Services & Dependencies", "View service dependencies", 9);
        addTool(serviceDev, "jsonplaceholder-user", "JSONPlaceholder User", "Test REST API calls", 8);
        addTool(serviceDev, "rag_query", "RAG Query", "Query service development docs", 7);

        // Add documents for service development
        addDocument(serviceDev, "service-arch", "Microservices Architecture Guide", "Best practices for service development", "markdown");
        addDocument(serviceDev, "rest-api", "REST API Design Guidelines", "API design standards", "markdown");

        // Category 3: UI Development
        Category uiDev = new Category(
            "ui-dev",
            "UI Development",
            "Tools and documents for frontend development"
        );
        uiDev.setDisplayOrder(3);
        uiDev = categoryRepository.save(uiDev);

        // Add tools for UI development
        addTool(uiDev, "rag_query", "RAG Query", "Query UI development docs", 10);

        // Add documents for UI development
        addDocument(uiDev, "react-guide", "React Development Guide", "React best practices and patterns", "markdown");
        addDocument(uiDev, "css-standards", "CSS Standards", "CSS coding standards", "markdown");

        // Category 4: General Utilities
        Category utilities = new Category(
            "utilities",
            "General Utilities",
            "General purpose tools available across all categories"
        );
        utilities.setDisplayOrder(4);
        utilities = categoryRepository.save(utilities);

        // Add general tools
        addTool(utilities, "echo", "Echo", "Echo back messages", 5);
        addTool(utilities, "add", "Add Numbers", "Add two numbers", 5);
        addTool(utilities, "get_current_time", "Get Current Time", "Get server time", 5);

        log.info("Sample data initialized successfully!");
        log.info("Created {} categories", categoryRepository.count());
    }

    private void addTool(Category category, String toolId, String toolName, String description, int priority) {
        CategoryTool tool = new CategoryTool(category, toolId, toolName, description);
        tool.setPriority(priority);
        categoryToolRepository.save(tool);
    }

    private void addDocument(Category category, String docId, String docName, String description, String type) {
        CategoryDocument document = new CategoryDocument(category, docId, docName, description);
        document.setDocumentType(type);
        categoryDocumentRepository.save(document);
    }
}
