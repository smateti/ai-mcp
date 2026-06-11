package com.example.hello;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * MicroProfile Health readiness check.
 * Exposed at /health/ready by the runtime.
 */
@Readiness
@ApplicationScoped
public class HealthReadyCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("hello-service-ready")
                .up()
                .withData("description", "Service is ready to accept requests")
                .build();
    }
}
