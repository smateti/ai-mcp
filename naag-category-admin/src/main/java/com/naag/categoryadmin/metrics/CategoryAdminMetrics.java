package com.naag.categoryadmin.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Centralized metrics for Category Admin operations.
 * Exposes metrics to Prometheus for Grafana visualization.
 */
@Component
public class CategoryAdminMetrics {

    // Counters
    private final Counter categoriesCreatedCounter;
    private final Counter categoriesUpdatedCounter;
    private final Counter categoriesDeletedCounter;
    private final Counter documentsUploadedCounter;
    private final Counter documentsIngestedCounter;

    // Timers
    private final Timer documentParsingTimer;
    private final Timer ragIngestionTimer;

    // Gauges (tracked separately)
    private volatile int totalCategoryCount = 0;
    private volatile int totalDocumentCount = 0;

    public CategoryAdminMetrics(MeterRegistry registry) {
        // Counters
        this.categoriesCreatedCounter = Counter.builder("categoryadmin.categories.created")
                .description("Total number of categories created")
                .tags("operation", "create")
                .register(registry);

        this.categoriesUpdatedCounter = Counter.builder("categoryadmin.categories.updated")
                .description("Total number of categories updated")
                .tags("operation", "update")
                .register(registry);

        this.categoriesDeletedCounter = Counter.builder("categoryadmin.categories.deleted")
                .description("Total number of categories deleted")
                .tags("operation", "delete")
                .register(registry);

        this.documentsUploadedCounter = Counter.builder("categoryadmin.documents.uploaded")
                .description("Total number of documents uploaded")
                .tags("operation", "upload")
                .register(registry);

        this.documentsIngestedCounter = Counter.builder("categoryadmin.documents.ingested")
                .description("Total number of documents ingested to RAG")
                .tags("operation", "ingest")
                .register(registry);

        // Timers
        this.documentParsingTimer = Timer.builder("categoryadmin.document.parsing.duration")
                .description("Time to parse uploaded documents")
                .tags("component", "parser")
                .register(registry);

        this.ragIngestionTimer = Timer.builder("categoryadmin.rag.ingestion.duration")
                .description("Time to ingest documents to RAG service")
                .tags("component", "rag-client")
                .register(registry);

        // Gauges
        Gauge.builder("categoryadmin.categories.total", this, CategoryAdminMetrics::getTotalCategoryCount)
                .description("Total number of categories")
                .register(registry);

        Gauge.builder("categoryadmin.documents.total", this, CategoryAdminMetrics::getTotalDocumentCount)
                .description("Total number of documents")
                .register(registry);
    }

    // Recording methods
    public void recordCategoryCreated() {
        categoriesCreatedCounter.increment();
    }

    public void recordCategoryUpdated() {
        categoriesUpdatedCounter.increment();
    }

    public void recordCategoryDeleted() {
        categoriesDeletedCounter.increment();
    }

    public void recordDocumentUploaded() {
        documentsUploadedCounter.increment();
    }

    public void recordDocumentIngested() {
        documentsIngestedCounter.increment();
    }

    public void recordDocumentParsingTime(long durationMs) {
        documentParsingTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordRagIngestionTime(long durationMs) {
        ragIngestionTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void setTotalCategoryCount(int count) {
        this.totalCategoryCount = count;
    }

    public void setTotalDocumentCount(int count) {
        this.totalDocumentCount = count;
    }

    // Getters for gauges
    public int getTotalCategoryCount() {
        return totalCategoryCount;
    }

    public int getTotalDocumentCount() {
        return totalDocumentCount;
    }
}
