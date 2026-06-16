package com.example.buildtrigger.gitlab;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import com.example.buildtrigger.model.BuildRecord;
import com.example.buildtrigger.model.GitLabException;
import com.example.buildtrigger.model.JobInfo;

/**
 * CDI bean that wraps the GitLab REST API using JAX-RS Client.
 * Supports both source and target GitLab instances, plus mock mode.
 */
@ApplicationScoped
public class GitLabClient {

    private static final Logger LOG = Logger.getLogger(GitLabClient.class.getName());

    @Inject
    @ConfigProperty(name = "gitlab.url", defaultValue = "https://localhost:9043")
    private String gitlabUrl;

    @Inject
    @ConfigProperty(name = "gitlab.token", defaultValue = "")
    private String gitlabToken;

    @Inject
    @ConfigProperty(name = "gitlab.mock", defaultValue = "false")
    private boolean mockMode;

    @Inject
    private GitLabClientFactory clientFactory;

    private Client client;

    @PostConstruct
    void init() {
        if (mockMode) {
            LOG.info("GitLabClient running in MOCK mode — no real API calls will be made");
            return;
        }
        client = clientFactory.getClient();
        LOG.info("GitLabClient initialised — source: " + gitlabUrl);
    }

    @PreDestroy
    void cleanup() {
        // Client lifecycle managed by factory
    }

    /* ------------------------------------------------------------------ */
    /*  Public API — source instance (default)                             */
    /* ------------------------------------------------------------------ */

    @Timeout(10000)
    @Retry(maxRetries = 2, delay = 500)
    public BuildRecord triggerPipeline(long projectId, String appName,
                                       String environment, String ref) {
        return triggerPipeline(projectId, appName, environment, ref, "source");
    }

    @Timeout(8000)
    @Retry(maxRetries = 2, delay = 500)
    public BuildRecord getPipeline(long projectId, long pipelineId) {
        return getPipeline(projectId, pipelineId, "source");
    }

    @Timeout(8000)
    @Retry(maxRetries = 2, delay = 500)
    public List<JobInfo> getJobs(long projectId, long pipelineId) {
        return getJobs(projectId, pipelineId, "source");
    }

    @Timeout(5000)
    public String getVersion() {
        return getVersion("source");
    }

    /* ------------------------------------------------------------------ */
    /*  Public API — instance-aware                                        */
    /* ------------------------------------------------------------------ */

    public BuildRecord triggerPipeline(long projectId, String appName,
                                       String environment, String ref,
                                       String instance) {
        if (mockMode) {
            return mockTrigger(projectId, appName, environment);
        }

        String url = clientFactory.getUrl(instance);
        String token = clientFactory.getToken(instance);

        String payload = buildTriggerPayload(appName, environment, ref);
        Response resp = client.target(url)
                .path("/api/v4/projects/{id}/pipeline")
                .resolveTemplate("id", projectId)
                .request(MediaType.APPLICATION_JSON)
                .header("PRIVATE-TOKEN", token)
                .post(Entity.json(payload));

        try {
            if (resp.getStatus() != 201) {
                String body = resp.readEntity(String.class);
                throw new GitLabException(
                        "Failed to trigger pipeline on " + instance + ": " + body, resp.getStatus());
            }
            JsonObject json = resp.readEntity(JsonObject.class);
            BuildRecord record = toRecord(json, appName, environment, projectId);
            record.setGitlabInstance(instance);
            return record;
        } finally {
            resp.close();
        }
    }

    public BuildRecord getPipeline(long projectId, long pipelineId, String instance) {
        if (mockMode) {
            return mockPipelineStatus(projectId, pipelineId);
        }

        String url = clientFactory.getUrl(instance);
        String token = clientFactory.getToken(instance);

        Response resp = client.target(url)
                .path("/api/v4/projects/{pid}/pipelines/{plid}")
                .resolveTemplate("pid", projectId)
                .resolveTemplate("plid", pipelineId)
                .request(MediaType.APPLICATION_JSON)
                .header("PRIVATE-TOKEN", token)
                .get();

        try {
            if (resp.getStatus() != 200) {
                String body = resp.readEntity(String.class);
                throw new GitLabException(
                        "Failed to get pipeline from " + instance + ": " + body, resp.getStatus());
            }
            JsonObject json = resp.readEntity(JsonObject.class);
            return toRecord(json, null, null, projectId);
        } finally {
            resp.close();
        }
    }

    public List<JobInfo> getJobs(long projectId, long pipelineId, String instance) {
        if (mockMode) {
            return mockJobs();
        }

        String url = clientFactory.getUrl(instance);
        String token = clientFactory.getToken(instance);

        Response resp = client.target(url)
                .path("/api/v4/projects/{pid}/pipelines/{plid}/jobs")
                .resolveTemplate("pid", projectId)
                .resolveTemplate("plid", pipelineId)
                .queryParam("per_page", 100)
                .request(MediaType.APPLICATION_JSON)
                .header("PRIVATE-TOKEN", token)
                .get();

        try {
            if (resp.getStatus() != 200) {
                String body = resp.readEntity(String.class);
                throw new GitLabException(
                        "Failed to get jobs from " + instance + ": " + body, resp.getStatus());
            }
            JsonArray arr = resp.readEntity(JsonArray.class);
            List<JobInfo> jobs = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                jobs.add(toJobInfo(arr.getJsonObject(i)));
            }
            return jobs;
        } finally {
            resp.close();
        }
    }

    public String getVersion(String instance) {
        if (mockMode) {
            return "mock-17.0.0";
        }
        String url = clientFactory.getUrl(instance);
        String token = clientFactory.getToken(instance);

        Response resp = client.target(url)
                .path("/api/v4/version")
                .request(MediaType.APPLICATION_JSON)
                .header("PRIVATE-TOKEN", token)
                .get();
        try {
            if (resp.getStatus() != 200) {
                throw new GitLabException("Version check failed on " + instance, resp.getStatus());
            }
            JsonObject json = resp.readEntity(JsonObject.class);
            return json.getString("version", "unknown");
        } finally {
            resp.close();
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Helpers                                                            */
    /* ------------------------------------------------------------------ */

    private String buildTriggerPayload(String appName, String env, String ref) {
        return """
                {
                  "ref": "%s",
                  "variables": [
                    { "key": "APP_NAME",    "value": "%s" },
                    { "key": "ENVIRONMENT", "value": "%s" }
                  ]
                }
                """.formatted(ref != null ? ref : "main", appName, env);
    }

    private BuildRecord toRecord(JsonObject json, String appName,
                                  String environment, long projectId) {
        BuildRecord r = new BuildRecord();
        r.setPipelineId(json.getJsonNumber("id").longValue());
        r.setAppName(appName);
        r.setEnvironment(environment);
        r.setGitlabProjectId(projectId);
        r.setStatus(json.getString("status", "unknown"));
        r.setWebUrl(json.getString("web_url", ""));
        r.setTriggeredAt(LocalDateTime.now());
        r.setTriggeredBy("ui");

        if (json.containsKey("started_at") && !json.isNull("started_at")) {
            r.setStartedAt(json.getString("started_at"));
        }
        if (json.containsKey("finished_at") && !json.isNull("finished_at")) {
            r.setFinishedAt(json.getString("finished_at"));
        }
        if (json.containsKey("duration") && !json.isNull("duration")) {
            r.setDuration(json.getJsonNumber("duration").doubleValue());
        }
        return r;
    }

    private JobInfo toJobInfo(JsonObject json) {
        JobInfo j = new JobInfo();
        j.setId(json.getJsonNumber("id").longValue());
        j.setName(json.getString("name", ""));
        j.setStage(json.getString("stage", ""));
        j.setStatus(json.getString("status", ""));
        j.setWebUrl(json.getString("web_url", ""));
        if (json.containsKey("duration") && !json.isNull("duration")) {
            j.setDuration(json.getJsonNumber("duration").doubleValue());
        }
        return j;
    }

    /* ------------------------------------------------------------------ */
    /*  Mock responses                                                     */
    /* ------------------------------------------------------------------ */

    private long mockPipelineCounter = 1000;

    private BuildRecord mockTrigger(long projectId, String appName, String env) {
        BuildRecord r = new BuildRecord();
        r.setPipelineId(++mockPipelineCounter);
        r.setAppName(appName);
        r.setEnvironment(env);
        r.setGitlabProjectId(projectId);
        r.setStatus("pending");
        r.setWebUrl(gitlabUrl + "/mock/pipeline/" + r.getPipelineId());
        r.setTriggeredAt(LocalDateTime.now());
        r.setTriggeredBy("ui-mock");
        LOG.info("MOCK: triggered pipeline " + r.getPipelineId()
                + " for " + appName + "/" + env);
        return r;
    }

    private BuildRecord mockPipelineStatus(long projectId, long pipelineId) {
        BuildRecord r = new BuildRecord();
        r.setPipelineId(pipelineId);
        r.setGitlabProjectId(projectId);
        r.setStatus("success");
        r.setWebUrl(gitlabUrl + "/mock/pipeline/" + pipelineId);
        r.setTriggeredAt(LocalDateTime.now().minusMinutes(5));
        r.setStartedAt(LocalDateTime.now().minusMinutes(4).toString());
        r.setFinishedAt(LocalDateTime.now().minusMinutes(1).toString());
        r.setDuration(180.0);
        return r;
    }

    private List<JobInfo> mockJobs() {
        List<JobInfo> jobs = new ArrayList<>();
        String[] stages = {"prepare", "build", "test", "package", "deploy", "verify"};
        long id = 5000;
        for (String stage : stages) {
            JobInfo j = new JobInfo();
            j.setId(id++);
            j.setName(stage + "-job");
            j.setStage(stage);
            j.setStatus("success");
            j.setDuration(30.0 + id % 60);
            j.setWebUrl(gitlabUrl + "/mock/job/" + j.getId());
            jobs.add(j);
        }
        return jobs;
    }
}
