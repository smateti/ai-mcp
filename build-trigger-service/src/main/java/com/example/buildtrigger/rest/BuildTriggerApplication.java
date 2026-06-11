package com.example.buildtrigger.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.Contact;

@ApplicationPath("/api")
@OpenAPIDefinition(
    info = @Info(
        title = "Build Trigger Service",
        version = "1.0.0",
        description = "REST API for triggering GitLab CI/CD pipelines and viewing build history",
        contact = @Contact(name = "Platform Team")
    )
)
public class BuildTriggerApplication extends Application {
}
