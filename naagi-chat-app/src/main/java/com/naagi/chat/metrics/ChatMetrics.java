package com.naagi.chat.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Centralized metrics for Chat App operations.
 * Exposes metrics to Prometheus for Grafana visualization.
 */
@Component
public class ChatMetrics {

    // Timers
    private final Timer messageProcessingTimer;
    private final Timer orchestratorCallTimer;

    // Counters
    private final Counter messageCounter;
    private final Counter messageErrorCounter;
    private final Counter sessionCreatedCounter;
    private final Counter sessionDeletedCounter;

    // Gauges (tracked separately)
    private volatile long lastMessageProcessingTimeMs = 0;
    private volatile int activeSessionCount = 0;

    public ChatMetrics(MeterRegistry registry) {
        // Timers for operation durations
        this.messageProcessingTimer = Timer.builder("chat.message.processing.duration")
                .description("Time to process a chat message end-to-end")
                .tags("component", "chat")
                .register(registry);

        this.orchestratorCallTimer = Timer.builder("chat.orchestrator.call.duration")
                .description("Time for orchestrator API call")
                .tags("component", "orchestrator-client")
                .register(registry);

        // Counters
        this.messageCounter = Counter.builder("chat.messages.total")
                .description("Total number of chat messages processed")
                .tags("operation", "message")
                .register(registry);

        this.messageErrorCounter = Counter.builder("chat.messages.errors")
                .description("Number of failed message processing")
                .tags("operation", "message")
                .register(registry);

        this.sessionCreatedCounter = Counter.builder("chat.sessions.created")
                .description("Number of sessions created")
                .tags("operation", "session")
                .register(registry);

        this.sessionDeletedCounter = Counter.builder("chat.sessions.deleted")
                .description("Number of sessions deleted")
                .tags("operation", "session")
                .register(registry);

        // Gauges for last operation values
        Gauge.builder("chat.last.message.processing.time.ms", this, ChatMetrics::getLastMessageProcessingTimeMs)
                .description("Last message processing time in milliseconds")
                .register(registry);

        Gauge.builder("chat.active.sessions", this, ChatMetrics::getActiveSessionCount)
                .description("Number of active sessions in cache")
                .register(registry);
    }

    // Recording methods
    public void recordMessageProcessingTime(long durationMs) {
        this.lastMessageProcessingTimeMs = durationMs;
        messageProcessingTimer.record(durationMs, TimeUnit.MILLISECONDS);
        messageCounter.increment();
    }

    public void recordMessageError() {
        messageErrorCounter.increment();
    }

    public void recordOrchestratorCallTime(long durationMs) {
        orchestratorCallTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordSessionCreated() {
        sessionCreatedCounter.increment();
        activeSessionCount++;
    }

    public void recordSessionDeleted() {
        sessionDeletedCounter.increment();
        if (activeSessionCount > 0) {
            activeSessionCount--;
        }
    }

    public void setActiveSessionCount(int count) {
        this.activeSessionCount = count;
    }

    // Getters for gauges
    public long getLastMessageProcessingTimeMs() {
        return lastMessageProcessingTimeMs;
    }

    public int getActiveSessionCount() {
        return activeSessionCount;
    }
}
