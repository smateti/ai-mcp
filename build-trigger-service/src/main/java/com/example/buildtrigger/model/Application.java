package com.example.buildtrigger.model;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "An application registered in the application registry")
public class Application {

    @Schema(description = "Unique application name", example = "order-management")
    private String name;

    @Schema(description = "Application type — determines which trigger repo is used", example = "service")
    private String type;

    @Schema(description = "Services that make up this application")
    private List<String> services;

    @Schema(description = "Environments this application may be deployed to")
    private List<String> environments;

    public Application() {}

    public Application(String name, String type, List<String> services, List<String> environments) {
        this.name = name;
        this.type = type;
        this.services = services;
        this.environments = environments;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<String> getServices() { return services; }
    public void setServices(List<String> services) { this.services = services; }

    public List<String> getEnvironments() { return environments; }
    public void setEnvironments(List<String> environments) { this.environments = environments; }
}
