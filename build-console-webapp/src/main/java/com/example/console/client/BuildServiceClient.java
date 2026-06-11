package com.example.console.client;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * CDI bean that calls the build-trigger-service REST API.
 */
@ApplicationScoped
public class BuildServiceClient {

    private static final Logger LOG = Logger.getLogger(BuildServiceClient.class.getName());

    @Inject
    @ConfigProperty(name = "build.service.url", defaultValue = "http://localhost:9081")
    private String serviceUrl;

    private Client client;

    @PostConstruct
    void init() {
        client = ClientBuilder.newBuilder()
                .connectTimeout(5000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(15000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
        LOG.info("BuildServiceClient initialised — target: " + serviceUrl);
    }

    @PreDestroy
    void cleanup() {
        if (client != null) {
            client.close();
        }
    }

    /**
     * GET /api/applications
     */
    public List<JsonObject> getApplications() {
        try {
            Response resp = client.target(serviceUrl)
                    .path("/api/applications")
                    .request(MediaType.APPLICATION_JSON)
                    .get();
            if (resp.getStatus() == 200) {
                String body = resp.readEntity(String.class);
                try (JsonReader reader = Json.createReader(new StringReader(body))) {
                    JsonArray arr = reader.readArray();
                    List<JsonObject> list = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        list.add(arr.getJsonObject(i));
                    }
                    return list;
                }
            }
            LOG.warning("Failed to get applications: HTTP " + resp.getStatus());
            return Collections.emptyList();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error calling /api/applications", e);
            return Collections.emptyList();
        }
    }

    /**
     * POST /api/builds
     */
    public JsonObject triggerBuild(String appName, String environment) {
        String payload = Json.createObjectBuilder()
                .add("appName", appName)
                .add("environment", environment)
                .build()
                .toString();

        Response resp = client.target(serviceUrl)
                .path("/api/builds")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(payload));

        String body = resp.readEntity(String.class);
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        }
    }

    /**
     * GET /api/builds?appName=X&environment=Y&limit=Z
     */
    public List<JsonObject> getBuilds(String appName, String environment, int limit) {
        try {
            var target = client.target(serviceUrl)
                    .path("/api/builds")
                    .queryParam("appName", appName)
                    .queryParam("limit", limit);

            if (environment != null && !environment.isBlank()) {
                target = target.queryParam("environment", environment);
            }

            Response resp = target.request(MediaType.APPLICATION_JSON).get();
            if (resp.getStatus() == 200) {
                String body = resp.readEntity(String.class);
                try (JsonReader reader = Json.createReader(new StringReader(body))) {
                    JsonArray arr = reader.readArray();
                    List<JsonObject> list = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        list.add(arr.getJsonObject(i));
                    }
                    return list;
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error calling /api/builds", e);
            return Collections.emptyList();
        }
    }

    /**
     * GET /api/builds/{pipelineId}/jobs
     */
    public List<JsonObject> getJobs(long pipelineId) {
        try {
            Response resp = client.target(serviceUrl)
                    .path("/api/builds/{id}/jobs")
                    .resolveTemplate("id", pipelineId)
                    .request(MediaType.APPLICATION_JSON)
                    .get();

            if (resp.getStatus() == 200) {
                String body = resp.readEntity(String.class);
                try (JsonReader reader = Json.createReader(new StringReader(body))) {
                    JsonArray arr = reader.readArray();
                    List<JsonObject> list = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        list.add(arr.getJsonObject(i));
                    }
                    return list;
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error calling /api/builds/{id}/jobs", e);
            return Collections.emptyList();
        }
    }

    /**
     * GET /api/builds/{pipelineId}/refresh
     */
    public JsonObject refreshBuild(long pipelineId) {
        Response resp = client.target(serviceUrl)
                .path("/api/builds/{id}/refresh")
                .resolveTemplate("id", pipelineId)
                .request(MediaType.APPLICATION_JSON)
                .get();

        String body = resp.readEntity(String.class);
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        }
    }
}
