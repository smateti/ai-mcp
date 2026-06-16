package com.example.hello;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * CDI bean that produces greetings.  Reads the application name from
 * MicroProfile Config — the value is injected from build.properties
 * (written by the pipeline's prepare stage) or from the ConfigMap at runtime.
 */
@ApplicationScoped
public class GreetingService {

    @Inject
    @ConfigProperty(name = "app.name", defaultValue = "hello-service")
    private String appName;

    @Inject
    @ConfigProperty(name = "app.environment", defaultValue = "local")
    private String environment;

    @Inject
    @ConfigProperty(name = "app.version", defaultValue = "dev")
    private String appVersion;

    public String greet(String name) {
        return String.format("Hello, %s! From %s v%s [%s]", name, appName, appVersion, environment);
    }

    public String getAppName() {
        return appName;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getAppVersion() {
        return appVersion;
    }
}
