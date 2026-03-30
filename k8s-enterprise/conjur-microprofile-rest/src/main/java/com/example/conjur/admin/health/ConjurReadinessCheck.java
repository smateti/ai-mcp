package com.example.conjur.admin.health;

import com.example.conjur.admin.service.ConjurClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class ConjurReadinessCheck implements HealthCheck {

    @Inject
    private ConjurClient conjurClient;

    @Override
    public HealthCheckResponse call() {
        if (!conjurClient.isEnabled()) {
            return HealthCheckResponse.named("conjur-admin-ready")
                    .down()
                    .withData("conjur.enabled", false)
                    .withData("status", "Admin API key not configured")
                    .build();
        }
        boolean reachable = conjurClient.isReachable();
        return HealthCheckResponse.named("conjur-admin-ready")
                .status(reachable)
                .withData("conjur.enabled", true)
                .withData("conjur.reachable", reachable)
                .withData("conjur.url", conjurClient.getApplianceUrl())
                .build();
    }
}
