package com.example.buildtrigger.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "BUILD_RECORDS")
public class BuildRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PIPELINE_ID", nullable = false)
    private Long pipelineId;

    @Column(name = "APP_NAME", nullable = false)
    private String appName;

    @Column(name = "ENVIRONMENT", nullable = false)
    private String environment;

    @Column(name = "APP_TYPE", nullable = false)
    private String appType;

    @Column(name = "GITLAB_PROJECT_ID", nullable = false)
    private Long gitlabProjectId;

    @Column(name = "TRIGGERED_AT", nullable = false)
    private LocalDateTime triggeredAt;

    @Column(name = "TRIGGERED_BY")
    private String triggeredBy;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "STARTED_AT")
    private String startedAt;

    @Column(name = "FINISHED_AT")
    private String finishedAt;

    @Column(name = "DURATION")
    private Double duration;

    @Column(name = "WEB_URL", length = 1024)
    private String webUrl;

    @Column(name = "GITLAB_INSTANCE")
    private String gitlabInstance;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPipelineId() { return pipelineId; }
    public void setPipelineId(Long pipelineId) { this.pipelineId = pipelineId; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getAppType() { return appType; }
    public void setAppType(String appType) { this.appType = appType; }

    public Long getGitlabProjectId() { return gitlabProjectId; }
    public void setGitlabProjectId(Long gitlabProjectId) { this.gitlabProjectId = gitlabProjectId; }

    public LocalDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(LocalDateTime triggeredAt) { this.triggeredAt = triggeredAt; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }

    public Double getDuration() { return duration; }
    public void setDuration(Double duration) { this.duration = duration; }

    public String getWebUrl() { return webUrl; }
    public void setWebUrl(String webUrl) { this.webUrl = webUrl; }

    public String getGitlabInstance() { return gitlabInstance; }
    public void setGitlabInstance(String gitlabInstance) { this.gitlabInstance = gitlabInstance; }
}
