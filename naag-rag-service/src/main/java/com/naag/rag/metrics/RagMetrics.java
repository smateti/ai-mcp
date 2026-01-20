package com.naag.rag.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Centralized metrics for RAG service operations.
 * Exposes metrics to Prometheus for Grafana visualization.
 */
@Component
public class RagMetrics {

    // Timers
    private final Timer embeddingTimer;
    private final Timer vectorSearchTimer;
    private final Timer llmChatTimer;
    private final Timer ragQueryTimer;
    private final Timer documentIngestTimer;

    // Counters
    private final Counter ragQueryCounter;
    private final Counter ragQueryErrorCounter;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter documentIngestCounter;
    private final Counter chunkCreatedCounter;

    // Gauges (tracked separately)
    private volatile long lastEmbeddingTimeMs = 0;
    private volatile long lastVectorSearchTimeMs = 0;
    private volatile long lastLlmTimeMs = 0;
    private volatile long lastTotalQueryTimeMs = 0;

    public RagMetrics(MeterRegistry registry) {
        // Timers for operation durations
        this.embeddingTimer = Timer.builder("rag.embedding.duration")
                .description("Time to generate embeddings")
                .tags("component", "embedding")
                .register(registry);

        this.vectorSearchTimer = Timer.builder("rag.vector.search.duration")
                .description("Time for vector similarity search in Qdrant")
                .tags("component", "qdrant")
                .register(registry);

        this.llmChatTimer = Timer.builder("rag.llm.chat.duration")
                .description("Time for LLM to generate response")
                .tags("component", "llm")
                .register(registry);

        this.ragQueryTimer = Timer.builder("rag.query.duration")
                .description("Total RAG query duration")
                .tags("operation", "query")
                .register(registry);

        this.documentIngestTimer = Timer.builder("rag.ingest.duration")
                .description("Time to ingest a document")
                .tags("operation", "ingest")
                .register(registry);

        // Counters
        this.ragQueryCounter = Counter.builder("rag.query.total")
                .description("Total number of RAG queries")
                .tags("operation", "query")
                .register(registry);

        this.ragQueryErrorCounter = Counter.builder("rag.query.errors")
                .description("Number of failed RAG queries")
                .tags("operation", "query")
                .register(registry);

        this.cacheHitCounter = Counter.builder("rag.cache.hits")
                .description("Number of cache hits")
                .tags("cache", "query")
                .register(registry);

        this.cacheMissCounter = Counter.builder("rag.cache.misses")
                .description("Number of cache misses")
                .tags("cache", "query")
                .register(registry);

        this.documentIngestCounter = Counter.builder("rag.documents.ingested")
                .description("Number of documents ingested")
                .tags("operation", "ingest")
                .register(registry);

        this.chunkCreatedCounter = Counter.builder("rag.chunks.created")
                .description("Number of chunks created")
                .tags("operation", "ingest")
                .register(registry);

        // Gauges for last operation times (for real-time monitoring)
        Gauge.builder("rag.last.embedding.time.ms", this, RagMetrics::getLastEmbeddingTimeMs)
                .description("Last embedding generation time in milliseconds")
                .register(registry);

        Gauge.builder("rag.last.vector.search.time.ms", this, RagMetrics::getLastVectorSearchTimeMs)
                .description("Last vector search time in milliseconds")
                .register(registry);

        Gauge.builder("rag.last.llm.time.ms", this, RagMetrics::getLastLlmTimeMs)
                .description("Last LLM response time in milliseconds")
                .register(registry);

        Gauge.builder("rag.last.total.query.time.ms", this, RagMetrics::getLastTotalQueryTimeMs)
                .description("Last total query time in milliseconds")
                .register(registry);
    }

    // Recording methods
    public void recordEmbeddingTime(long durationMs) {
        this.lastEmbeddingTimeMs = durationMs;
        embeddingTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordVectorSearchTime(long durationMs) {
        this.lastVectorSearchTimeMs = durationMs;
        vectorSearchTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordLlmChatTime(long durationMs) {
        this.lastLlmTimeMs = durationMs;
        llmChatTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordQueryTime(long durationMs) {
        this.lastTotalQueryTimeMs = durationMs;
        ragQueryTimer.record(durationMs, TimeUnit.MILLISECONDS);
        ragQueryCounter.increment();
    }

    public void recordQueryError() {
        ragQueryErrorCounter.increment();
    }

    public void recordCacheHit() {
        cacheHitCounter.increment();
    }

    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }

    public void recordDocumentIngested(int chunkCount) {
        documentIngestCounter.increment();
        chunkCreatedCounter.increment(chunkCount);
    }

    public void recordIngestTime(long durationMs) {
        documentIngestTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    // Getters for gauges
    public long getLastEmbeddingTimeMs() {
        return lastEmbeddingTimeMs;
    }

    public long getLastVectorSearchTimeMs() {
        return lastVectorSearchTimeMs;
    }

    public long getLastLlmTimeMs() {
        return lastLlmTimeMs;
    }

    public long getLastTotalQueryTimeMs() {
        return lastTotalQueryTimeMs;
    }
}
