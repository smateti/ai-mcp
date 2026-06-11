package com.example.hello;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * MicroProfile Health liveness check.
 * Exposed at /health/live by the runtime.
 */
@Liveness
@ApplicationScoped
public class HealthLiveCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("hello-service-live")
                .up()
                .build();
    }
}
