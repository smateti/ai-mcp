package com.example.servicedep;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "Service Dependency API",
        version = "1.0.0",
        description = "Mock API for managing application service dependencies. " +
                     "This API provides endpoints to retrieve applications, their services, " +
                     "operations, and inter-service dependencies.",
        contact = @Contact(
            name = "API Support",
            email = "support@example.com"
        )
    )
)
public class ServiceDependencyApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceDependencyApiApplication.class, args);
    }
}
