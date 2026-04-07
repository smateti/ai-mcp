package com.example.conjur.health;

import com.example.conjur.service.ConjurService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class ConjurHealthCheck implements HealthCheck {

    @Inject
    private ConjurService conjurService;

    @Override
    public HealthCheckResponse call() {
        if (!conjurService.isConjurEnabled()) {
            return HealthCheckResponse.named("conjur-connection")
                    .up()
                    .withData("conjur.enabled", false)
                    .withData("status", "skipped — no API key configured")
                    .build();
        }
        boolean reachable = conjurService.isConjurReachable();
        return HealthCheckResponse.named("conjur-connection")
                .status(reachable)
                .withData("conjur.enabled", true)
                .withData("conjur.reachable", reachable)
                .build();
    }
}
