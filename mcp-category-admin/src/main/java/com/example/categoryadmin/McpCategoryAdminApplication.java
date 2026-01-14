package com.example.categoryadmin;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "MCP Category Admin API",
        version = "1.0.0",
        description = "Admin application for managing MCP tool and document categories. " +
                     "Allows scoping of MCP tools and RAG documents to specific categories " +
                     "for better performance and reduced ambiguity."
    )
)
public class McpCategoryAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpCategoryAdminApplication.class, args);
    }
}
