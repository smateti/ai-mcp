package com.example.buildtrigger.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Pipeline build information returned to the caller")
public class BuildResponse {

    @Schema(description = "GitLab pipeline ID")
    private Long pipelineId;

    @Schema(description = "Application name")
    private String appName;

    @Schema(description = "Target environment")
    private String environment;

    @Schema(description = "Application type")
    private String appType;

    @Schema(description = "Pipeline status (created, pending, running, success, failed, canceled)")
    private String status;

    @Schema(description = "When the pipeline was triggered")
    private String triggeredAt;

    @Schema(description = "Who triggered the pipeline")
    private String triggeredBy;

    @Schema(description = "When the pipeline started executing")
    private String startedAt;

    @Schema(description = "When the pipeline finished")
    private String finishedAt;

    @Schema(description = "Pipeline duration in seconds")
    private Double duration;

    @Schema(description = "Direct link to the pipeline in GitLab")
    private String webUrl;

    public BuildResponse() {}

    public BuildResponse(BuildRecord record) {
        this.pipelineId = record.getPipelineId();
        this.appName = record.getAppName();
        this.environment = record.getEnvironment();
        this.appType = record.getAppType();
        this.status = record.getStatus();
        this.triggeredAt = record.getTriggeredAt() != null ? record.getTriggeredAt().toString() : null;
        this.triggeredBy = record.getTriggeredBy();
        this.startedAt = record.getStartedAt();
        this.finishedAt = record.getFinishedAt();
        this.duration = record.getDuration();
        this.webUrl = record.getWebUrl();
    }

    public Long getPipelineId() { return pipelineId; }
    public void setPipelineId(Long pipelineId) { this.pipelineId = pipelineId; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getAppType() { return appType; }
    public void setAppType(String appType) { this.appType = appType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(String triggeredAt) { this.triggeredAt = triggeredAt; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }

    public Double getDuration() { return duration; }
    public void setDuration(Double duration) { this.duration = duration; }

    public String getWebUrl() { return webUrl; }
    public void setWebUrl(String webUrl) { this.webUrl = webUrl; }
}
