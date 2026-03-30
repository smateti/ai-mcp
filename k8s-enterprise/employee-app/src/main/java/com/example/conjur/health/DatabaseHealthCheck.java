package com.example.conjur.health;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import javax.sql.DataSource;
import java.sql.Connection;

@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    @Resource(lookup = "jdbc/appdb")
    private DataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            return HealthCheckResponse.named("database-connection")
                    .status(valid)
                    .withData("database", conn.getMetaData().getDatabaseProductName())
                    .withData("url", conn.getMetaData().getURL())
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.named("database-connection")
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
