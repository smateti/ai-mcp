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

    // CRAG Metrics
    private final Timer cragQueryTimer;
    private final Counter cragCorrectCounter;
    private final Counter cragAmbiguousCounter;
    private final Counter cragIncorrectCounter;
    private final Counter cragQueryExpansionCounter;
    private final Counter cragRetryCounter;

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

        // CRAG Metrics
        this.cragQueryTimer = Timer.builder("rag.crag.query.duration")
                .description("Time for CRAG query processing")
                .tags("operation", "crag")
                .register(registry);

        this.cragCorrectCounter = Counter.builder("rag.crag.confidence.total")
                .description("Number of CRAG queries with CORRECT confidence")
                .tags("category", "correct")
                .register(registry);

        this.cragAmbiguousCounter = Counter.builder("rag.crag.confidence.total")
                .description("Number of CRAG queries with AMBIGUOUS confidence")
                .tags("category", "ambiguous")
                .register(registry);

        this.cragIncorrectCounter = Counter.builder("rag.crag.confidence.total")
                .description("Number of CRAG queries with INCORRECT confidence")
                .tags("category", "incorrect")
                .register(registry);

        this.cragQueryExpansionCounter = Counter.builder("rag.crag.query.expansion")
                .description("Number of query expansions performed")
                .tags("strategy", "expansion")
                .register(registry);

        this.cragRetryCounter = Counter.builder("rag.crag.retries")
                .description("Number of CRAG retrieval retries")
                .tags("operation", "retry")
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

    // CRAG metrics recording
    public void recordCragQuery(long durationMs, String category, int retries, boolean usedExpansion) {
        cragQueryTimer.record(durationMs, TimeUnit.MILLISECONDS);

        switch (category) {
            case "CORRECT" -> cragCorrectCounter.increment();
            case "AMBIGUOUS" -> cragAmbiguousCounter.increment();
            case "INCORRECT" -> cragIncorrectCounter.increment();
        }

        if (retries > 0) {
            cragRetryCounter.increment(retries);
        }

        if (usedExpansion) {
            cragQueryExpansionCounter.increment();
        }
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
