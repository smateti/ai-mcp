package com.example.buildtrigger.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request body for triggering a new pipeline build")
public class BuildRequest {

    @Schema(description = "Application name from the registry", example = "order-management", required = true)
    private String appName;

    @Schema(description = "Target deployment environment", example = "dev", required = true)
    private String environment;

    @Schema(description = "GitLab instance: 'source' (default) or 'target'", example = "source")
    private String gitlabInstance;

    public BuildRequest() {}

    public BuildRequest(String appName, String environment) {
        this.appName = appName;
        this.environment = environment;
    }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getGitlabInstance() { return gitlabInstance; }
    public void setGitlabInstance(String gitlabInstance) { this.gitlabInstance = gitlabInstance; }
}
