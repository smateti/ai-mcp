package com.example.buildtrigger.registry;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import com.example.buildtrigger.model.Application;

/**
 * Reads the application registry from registry.json on the classpath.
 * Provides lookup by name and listing of all registered applications.
 */
@ApplicationScoped
public class ApplicationRegistry {

    private static final Logger LOG = Logger.getLogger(ApplicationRegistry.class.getName());

    private final Map<String, Application> applications = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        loadRegistry("/registry.json");
    }

    void loadRegistry(String resourcePath) {
        applications.clear();
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOG.severe("Registry file not found on classpath: " + resourcePath);
                return;
            }
            try (JsonReader reader = Json.createReader(is)) {
                JsonObject root = reader.readObject();
                for (String appName : root.keySet()) {
                    JsonObject appObj = root.getJsonObject(appName);
                    String type = appObj.getString("type");

                    List<String> services = new ArrayList<>();
                    JsonArray svcArray = appObj.getJsonArray("services");
                    if (svcArray != null) {
                        for (JsonValue v : svcArray) {
                            services.add(((JsonString) v).getString());
                        }
                    }

                    List<String> environments = new ArrayList<>();
                    JsonArray envArray = appObj.getJsonArray("environments");
                    if (envArray != null) {
                        for (JsonValue v : envArray) {
                            environments.add(((JsonString) v).getString());
                        }
                    }

                    applications.put(appName, new Application(appName, type, services, environments));
                }
            }
            LOG.info("Loaded " + applications.size() + " applications from registry");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load application registry", e);
        }
    }

    public List<Application> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(applications.values()));
    }

    public Application findByName(String name) {
        return applications.get(name);
    }
}
