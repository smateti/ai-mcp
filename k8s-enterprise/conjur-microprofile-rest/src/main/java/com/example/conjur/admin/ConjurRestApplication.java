package com.example.conjur.admin;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@ApplicationPath("/api")
@OpenAPIDefinition(
    info = @Info(
        title = "Conjur Admin REST API",
        version = "1.0.0",
        description = "REST API for managing CyberArk Conjur OSS — policies, application hosts, and database credentials",
        contact = @Contact(name = "Platform Team")
    )
)
public class ConjurRestApplication extends Application {
}
