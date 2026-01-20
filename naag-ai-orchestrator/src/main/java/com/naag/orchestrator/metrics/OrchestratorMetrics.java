package com.naag.orchestrator.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Centralized metrics for AI Orchestrator operations.
 * Exposes metrics to Prometheus for Grafana visualization.
 */
@Component
public class OrchestratorMetrics {

    // Timers
    private final Timer orchestrationTimer;
    private final Timer toolSelectionTimer;
    private final Timer llmChatTimer;
    private final Timer toolExecutionTimer;

    // Counters
    private final Counter orchestrationCounter;
    private final Counter orchestrationErrorCounter;
    private final Counter toolSelectionCounter;
    private final Counter highConfidenceCounter;
    private final Counter mediumConfidenceCounter;
    private final Counter lowConfidenceCounter;
    private final Counter toolExecutionCounter;
    private final Counter toolExecutionErrorCounter;

    // Gauges (tracked separately)
    private volatile long lastOrchestrationTimeMs = 0;
    private volatile long lastToolSelectionTimeMs = 0;
    private volatile long lastLlmTimeMs = 0;
    private volatile double lastConfidenceScore = 0;

    public OrchestratorMetrics(MeterRegistry registry) {
        // Timers for operation durations
        this.orchestrationTimer = Timer.builder("orchestrator.orchestration.duration")
                .description("Total orchestration request duration")
                .tags("component", "orchestrator")
                .register(registry);

        this.toolSelectionTimer = Timer.builder("orchestrator.tool.selection.duration")
                .description("Time for LLM to select a tool")
                .tags("component", "tool-selection")
                .register(registry);

        this.llmChatTimer = Timer.builder("orchestrator.llm.chat.duration")
                .description("Time for LLM chat call")
                .tags("component", "llm")
                .register(registry);

        this.toolExecutionTimer = Timer.builder("orchestrator.tool.execution.duration")
                .description("Time to execute selected tool via MCP")
                .tags("component", "mcp")
                .register(registry);

        // Counters
        this.orchestrationCounter = Counter.builder("orchestrator.requests.total")
                .description("Total number of orchestration requests")
                .tags("operation", "orchestrate")
                .register(registry);

        this.orchestrationErrorCounter = Counter.builder("orchestrator.requests.errors")
                .description("Number of failed orchestration requests")
                .tags("operation", "orchestrate")
                .register(registry);

        this.toolSelectionCounter = Counter.builder("orchestrator.tool.selections.total")
                .description("Total number of tool selections")
                .tags("operation", "select")
                .register(registry);

        this.highConfidenceCounter = Counter.builder("orchestrator.confidence.high")
                .description("Number of high confidence selections")
                .tags("confidence", "high")
                .register(registry);

        this.mediumConfidenceCounter = Counter.builder("orchestrator.confidence.medium")
                .description("Number of medium confidence selections")
                .tags("confidence", "medium")
                .register(registry);

        this.lowConfidenceCounter = Counter.builder("orchestrator.confidence.low")
                .description("Number of low confidence selections")
                .tags("confidence", "low")
                .register(registry);

        this.toolExecutionCounter = Counter.builder("orchestrator.tool.executions.total")
                .description("Total number of tool executions")
                .tags("operation", "execute")
                .register(registry);

        this.toolExecutionErrorCounter = Counter.builder("orchestrator.tool.executions.errors")
                .description("Number of failed tool executions")
                .tags("operation", "execute")
                .register(registry);

        // Gauges for last operation values
        Gauge.builder("orchestrator.last.orchestration.time.ms", this, OrchestratorMetrics::getLastOrchestrationTimeMs)
                .description("Last orchestration time in milliseconds")
                .register(registry);

        Gauge.builder("orchestrator.last.tool.selection.time.ms", this, OrchestratorMetrics::getLastToolSelectionTimeMs)
                .description("Last tool selection time in milliseconds")
                .register(registry);

        Gauge.builder("orchestrator.last.llm.time.ms", this, OrchestratorMetrics::getLastLlmTimeMs)
                .description("Last LLM call time in milliseconds")
                .register(registry);

        Gauge.builder("orchestrator.last.confidence.score", this, OrchestratorMetrics::getLastConfidenceScore)
                .description("Last tool selection confidence score")
                .register(registry);
    }

    // Recording methods
    public void recordOrchestrationTime(long durationMs) {
        this.lastOrchestrationTimeMs = durationMs;
        orchestrationTimer.record(durationMs, TimeUnit.MILLISECONDS);
        orchestrationCounter.increment();
    }

    public void recordOrchestrationError() {
        orchestrationErrorCounter.increment();
    }

    public void recordToolSelectionTime(long durationMs) {
        this.lastToolSelectionTimeMs = durationMs;
        toolSelectionTimer.record(durationMs, TimeUnit.MILLISECONDS);
        toolSelectionCounter.increment();
    }

    public void recordLlmChatTime(long durationMs) {
        this.lastLlmTimeMs = durationMs;
        llmChatTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordToolExecutionTime(long durationMs) {
        toolExecutionTimer.record(durationMs, TimeUnit.MILLISECONDS);
        toolExecutionCounter.increment();
    }

    public void recordToolExecutionError() {
        toolExecutionErrorCounter.increment();
    }

    public void recordConfidence(double confidence, boolean isHigh, boolean isLow) {
        this.lastConfidenceScore = confidence;
        if (isHigh) {
            highConfidenceCounter.increment();
        } else if (isLow) {
            lowConfidenceCounter.increment();
        } else {
            mediumConfidenceCounter.increment();
        }
    }

    // Getters for gauges
    public long getLastOrchestrationTimeMs() {
        return lastOrchestrationTimeMs;
    }

    public long getLastToolSelectionTimeMs() {
        return lastToolSelectionTimeMs;
    }

    public long getLastLlmTimeMs() {
        return lastLlmTimeMs;
    }

    public double getLastConfidenceScore() {
        return lastConfidenceScore;
    }
}
