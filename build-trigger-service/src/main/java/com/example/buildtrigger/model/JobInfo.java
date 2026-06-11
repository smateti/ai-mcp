package com.example.buildtrigger.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A single CI/CD job within a pipeline")
public class JobInfo {

    @Schema(description = "GitLab job ID")
    private Long id;

    @Schema(description = "Job name", example = "prepare")
    private String name;

    @Schema(description = "Pipeline stage this job belongs to", example = "prepare")
    private String stage;

    @Schema(description = "Job status", example = "success")
    private String status;

    @Schema(description = "Job duration in seconds")
    private Double duration;

    @Schema(description = "Direct link to the job console log in GitLab")
    private String webUrl;

    public JobInfo() {}

    public JobInfo(Long id, String name, String stage, String status, Double duration, String webUrl) {
        this.id = id;
        this.name = name;
        this.stage = stage;
        this.status = status;
        this.duration = duration;
        this.webUrl = webUrl;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getDuration() { return duration; }
    public void setDuration(Double duration) { this.duration = duration; }

    public String getWebUrl() { return webUrl; }
    public void setWebUrl(String webUrl) { this.webUrl = webUrl; }
}
