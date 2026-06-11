package com.example.buildtrigger.health;

import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import com.example.buildtrigger.gitlab.GitLabClient;

@Readiness
@ApplicationScoped
public class ReadinessCheck implements HealthCheck {

    private static final Logger LOG = Logger.getLogger(ReadinessCheck.class.getName());

    @Inject
    private GitLabClient gitLabClient;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse
                .named("build-trigger-ready");
        try {
            String version = gitLabClient.getVersion();
            builder.up().withData("gitlab-version", version);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Readiness check: GitLab unreachable", e);
            builder.down().withData("error", e.getMessage());
        }
        return builder.build();
    }
}
