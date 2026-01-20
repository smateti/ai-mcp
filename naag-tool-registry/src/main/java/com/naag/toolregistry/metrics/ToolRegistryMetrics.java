package com.naag.toolregistry.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Centralized metrics for Tool Registry operations.
 * Exposes metrics to Prometheus for Grafana visualization.
 */
@Component
public class ToolRegistryMetrics {

    // Timers
    private final Timer toolRegistrationTimer;
    private final Timer openApiParsingTimer;

    // Counters
    private final Counter toolsRegisteredCounter;
    private final Counter toolsDeletedCounter;
    private final Counter toolsUpdatedCounter;
    private final Counter registrationErrorCounter;

    // Gauges (tracked separately)
    private volatile int totalToolCount = 0;

    public ToolRegistryMetrics(MeterRegistry registry) {
        // Timers for operation durations
        this.toolRegistrationTimer = Timer.builder("toolregistry.registration.duration")
                .description("Time to register a tool")
                .tags("component", "registry")
                .register(registry);

        this.openApiParsingTimer = Timer.builder("toolregistry.openapi.parsing.duration")
                .description("Time to parse OpenAPI specification")
                .tags("component", "parser")
                .register(registry);

        // Counters
        this.toolsRegisteredCounter = Counter.builder("toolregistry.tools.registered")
                .description("Total number of tools registered")
                .tags("operation", "register")
                .register(registry);

        this.toolsDeletedCounter = Counter.builder("toolregistry.tools.deleted")
                .description("Total number of tools deleted")
                .tags("operation", "delete")
                .register(registry);

        this.toolsUpdatedCounter = Counter.builder("toolregistry.tools.updated")
                .description("Total number of tools updated")
                .tags("operation", "update")
                .register(registry);

        this.registrationErrorCounter = Counter.builder("toolregistry.registration.errors")
                .description("Number of registration errors")
                .tags("operation", "register")
                .register(registry);

        // Gauges for current state
        Gauge.builder("toolregistry.tools.total", this, ToolRegistryMetrics::getTotalToolCount)
                .description("Total number of registered tools")
                .register(registry);
    }

    // Recording methods
    public void recordToolRegistration(long durationMs) {
        toolRegistrationTimer.record(durationMs, TimeUnit.MILLISECONDS);
        toolsRegisteredCounter.increment();
    }

    public void recordOpenApiParsing(long durationMs) {
        openApiParsingTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordToolDeleted() {
        toolsDeletedCounter.increment();
    }

    public void recordToolUpdated() {
        toolsUpdatedCounter.increment();
    }

    public void recordRegistrationError() {
        registrationErrorCounter.increment();
    }

    public void setTotalToolCount(int count) {
        this.totalToolCount = count;
    }

    // Getters for gauges
    public int getTotalToolCount() {
        return totalToolCount;
    }
}
