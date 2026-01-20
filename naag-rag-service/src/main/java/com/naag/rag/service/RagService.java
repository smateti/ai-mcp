package com.naag.rag.service;

import com.naag.rag.chunk.HybridChunker;
import com.naag.rag.llm.ChatClient;
import com.naag.rag.llm.EmbeddingsClient;
import com.naag.rag.metrics.RagMetrics;
import com.naag.rag.qdrant.QdrantClient;
import com.naag.rag.qdrant.QdrantClient.Point;
import com.naag.rag.qdrant.QdrantClient.SearchResultWithScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    public record QueryResult(
            String question,
            String answer,
            List<SourceChunk> sources
    ) {}

    public record SourceChunk(
            String docId,
            int chunkIndex,
            double relevanceScore,
            String text
    ) {}

    private final HybridChunker chunker;
    private final EmbeddingsClient embed;
    private final ChatClient chat;
    private final QdrantClient qdrant;
    private final RagMetrics metrics;

    private final int topK;
    private final int batchSize;
    private final Semaphore embedPermits;

    private volatile boolean loggedEmbeddingDim = false;

    private final Map<String, QueryResult> queryCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 500;

    private final double minRelevanceScore;

    public RagService(
            @Value("${naag.rag.chunking.maxChars}") int maxChars,
            @Value("${naag.rag.chunking.overlapChars}") int overlap,
            @Value("${naag.rag.chunking.minChars}") int minChars,
            @Value("${naag.rag.retrieval.topK}") int topK,
            @Value("${naag.rag.retrieval.minRelevanceScore:0.75}") double minRelevanceScore,
            @Value("${naag.rag.performance.qdrantBatchSize}") int batchSize,
            @Value("${naag.rag.performance.maxConcurrentEmbeddings}") int maxEmbed,
            EmbeddingsClient embed,
            ChatClient chat,
            QdrantClient qdrant,
            RagMetrics metrics
    ) {
        this.chunker = new HybridChunker(maxChars, overlap, minChars);
        this.embed = embed;
        this.chat = chat;
        this.qdrant = qdrant;
        this.metrics = metrics;
        this.topK = topK;
        this.minRelevanceScore = minRelevanceScore;
        this.batchSize = batchSize;
        this.embedPermits = new Semaphore(Math.max(1, maxEmbed));
        log.info("[RAG] Initialized with minRelevanceScore={}", minRelevanceScore);
    }

    public int ingest(String docId, String text) {
        return ingest(docId, text, List.of());
    }

    public int ingest(String docId, String text, List<String> categories) {
        long ingestStart = System.currentTimeMillis();
        List<String> chunks = chunker.chunk(text);
        if (chunks.isEmpty()) return 0;

        List<Point> batch = new ArrayList<>(batchSize);

        for (int i = 0; i < chunks.size(); i++) {
            embedPermits.acquireUninterruptibly();
            try {
                String chunk = chunks.get(i);
                List<Double> vec = embed.embed(chunk);

                if (!loggedEmbeddingDim) {
                    loggedEmbeddingDim = true;
                    log.info("[RAG] Detected embedding dimension={}", vec.size());
                }
                if (vec.size() <= 1) {
                    throw new IllegalStateException("Embedding vector looks wrong (dim=" + vec.size() + "). Check embedding server response parsing.");
                }

                Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("docId", docId);
                payload.put("chunkIndex", i);
                payload.put("text", chunk);
                if (categories != null && !categories.isEmpty()) {
                    payload.put("categories", categories);
                }

                Point p = new Point(
                        stableId(docId + ":" + i + ":" + chunk),
                        vec,
                        payload
                );

                batch.add(p);
                if (batch.size() >= batchSize) {
                    qdrant.upsertBatch(batch);
                    batch.clear();
                }
            } finally {
                embedPermits.release();
            }
        }

        if (!batch.isEmpty()) {
            qdrant.upsertBatch(batch);
        }

        long ingestTime = System.currentTimeMillis() - ingestStart;
        metrics.recordIngestTime(ingestTime);
        metrics.recordDocumentIngested(chunks.size());
        log.info("[TIMING] Document ingest: {}ms for {} chunks", ingestTime, chunks.size());

        return chunks.size();
    }

    public String ask(String question) {
        return ask(question, null);
    }

    public String ask(String question, String category) {
        List<Double> qVec = embed.embed(question);
        List<String> ctx = qdrant.searchPayloadTexts(qVec, topK, category);

        String contextBlock = String.join("\n\n---\n\n", ctx);

        String prompt = """
                You are a helpful assistant answering questions based on the provided documentation.

                RULES:
                1. Use the information from the context below to answer the question.
                2. Do not make up information that is not in the context.
                3. If the context does not contain relevant information, say "I don't have information about that in the knowledge base."

                FORMAT:
                - Respond in plain, natural language.

                Context:
                %s

                Question: %s

                Answer:""".formatted(contextBlock, question);

        return chat.chatOnce(prompt, 0.2, 512);
    }

    public QueryResult askWithSources(String question, int topK) {
        return askWithSources(question, topK, null);
    }

    public QueryResult askWithSources(String question, int topK, String category) {
        long totalStart = System.currentTimeMillis();

        String cacheKey = buildCacheKey(question, topK, category);

        QueryResult cached = queryCache.get(cacheKey);
        if (cached != null) {
            long cacheTime = System.currentTimeMillis() - totalStart;
            log.info("[TIMING] Cache HIT - returning cached result in {}ms", cacheTime);
            metrics.recordCacheHit();
            return cached;
        }
        metrics.recordCacheMiss();

        long embedStart = System.currentTimeMillis();
        List<Double> qVec = embed.embed(question);
        long embedTime = System.currentTimeMillis() - embedStart;
        log.info("[TIMING] Embedding generation: {}ms", embedTime);
        metrics.recordEmbeddingTime(embedTime);

        long searchStart = System.currentTimeMillis();
        List<SearchResultWithScore> results = qdrant.searchWithScores(qVec, topK, category);
        long searchTime = System.currentTimeMillis() - searchStart;
        log.info("[TIMING] Vector search (topK={}): {}ms, found {} results", topK, searchTime, results.size());
        metrics.recordVectorSearchTime(searchTime);

        List<SourceChunk> sources = results.stream()
                .map(r -> new SourceChunk(r.docId(), r.chunkIndex(), r.score(), r.text()))
                .collect(Collectors.toList());

        // Check if top result meets minimum relevance threshold
        double topScore = sources.isEmpty() ? 0.0 : sources.get(0).relevanceScore();
        if (topScore < minRelevanceScore) {
            log.info("[RAG] Top relevance score {} is below threshold {} - returning no information response",
                    topScore, minRelevanceScore);
            return new QueryResult(question, "I don't have information about that in the knowledge base.", sources);
        }

        String contextBlock = results.stream()
                .map(SearchResultWithScore::text)
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = """
                You are a helpful assistant answering questions based on the provided documentation.

                RULES:
                1. Use the information from the context below to answer the question.
                2. Do not make up information that is not in the context.
                3. If the context does not contain relevant information, say "I don't have information about that in the knowledge base."

                FORMAT:
                - Respond in plain, natural language.
                - If asked about steps or processes, use numbered steps.

                Context:
                %s

                Question: %s

                Answer:""".formatted(contextBlock, question);

        long llmStart = System.currentTimeMillis();
        String answer = chat.chatOnce(prompt, 0.2, 256);
        long llmTime = System.currentTimeMillis() - llmStart;
        log.info("[TIMING] LLM answer generation: {}ms", llmTime);
        metrics.recordLlmChatTime(llmTime);

        QueryResult result = new QueryResult(question, answer, sources);

        boolean shouldCache = !sources.isEmpty()
                && sources.get(0).relevanceScore() >= 0.65
                && !isNoInformationAnswer(answer);

        if (shouldCache && queryCache.size() < MAX_CACHE_SIZE) {
            queryCache.put(cacheKey, result);
            log.info("[CACHE] Cached result for question: {} (score: {})", question, sources.get(0).relevanceScore());
        } else if (!shouldCache) {
            log.info("[CACHE] Skipped caching low-quality result - score: {}, isNoInfo: {}, question: {}",
                    sources.isEmpty() ? 0.0 : sources.get(0).relevanceScore(),
                    isNoInformationAnswer(answer),
                    question);
        }

        long totalTime = System.currentTimeMillis() - totalStart;
        log.info("[TIMING] Total RAG query time: {}ms (embed={}ms, search={}ms, llm={}ms)",
                totalTime, embedTime, searchTime, llmTime);
        metrics.recordQueryTime(totalTime);

        return result;
    }

    private String buildCacheKey(String question, int topK, String category) {
        String normalized = question.toLowerCase().trim();
        String cat = category == null ? "" : category;
        return normalized + "|" + topK + "|" + cat;
    }

    private boolean isNoInformationAnswer(String answer) {
        if (answer == null || answer.isEmpty()) {
            return true;
        }

        String lowerAnswer = answer.toLowerCase().trim();

        return lowerAnswer.contains("i don't know")
                || lowerAnswer.contains("i do not know")
                || lowerAnswer.contains("no information")
                || lowerAnswer.contains("not in the context")
                || lowerAnswer.contains("context does not provide")
                || lowerAnswer.contains("cannot find")
                || lowerAnswer.contains("is not available")
                || lowerAnswer.contains("there is no")
                || lowerAnswer.matches("^(i don't know|unknown|not found)\\.?$");
    }

    /**
     * Streaming version of askWithSources.
     * Returns sources immediately, then streams the answer token by token.
     */
    public void askWithSourcesStream(String question, int topK, String category,
                                      java.util.function.Consumer<StreamEvent> onEvent) {
        long totalStart = System.currentTimeMillis();

        // Embedding
        long embedStart = System.currentTimeMillis();
        List<Double> qVec = embed.embed(question);
        long embedTime = System.currentTimeMillis() - embedStart;
        log.info("[TIMING] Embedding generation: {}ms", embedTime);
        metrics.recordEmbeddingTime(embedTime);

        // Vector search
        long searchStart = System.currentTimeMillis();
        List<SearchResultWithScore> results = qdrant.searchWithScores(qVec, topK, category);
        long searchTime = System.currentTimeMillis() - searchStart;
        log.info("[TIMING] Vector search (topK={}): {}ms, found {} results", topK, searchTime, results.size());
        metrics.recordVectorSearchTime(searchTime);

        // Send sources event first
        List<SourceChunk> sources = results.stream()
                .map(r -> new SourceChunk(r.docId(), r.chunkIndex(), r.score(), r.text()))
                .collect(Collectors.toList());
        onEvent.accept(new StreamEvent("sources", null, sources));

        // Check if top result meets minimum relevance threshold
        double topScore = sources.isEmpty() ? 0.0 : sources.get(0).relevanceScore();
        if (topScore < minRelevanceScore) {
            log.info("[RAG] Top relevance score {} is below threshold {} - returning no information response",
                    topScore, minRelevanceScore);
            String noInfoPrompt = "No relevant context found (score: " + topScore + ")";
            onEvent.accept(new StreamEvent("prompt", null, null, noInfoPrompt));
            onEvent.accept(new StreamEvent("token", "I don't have information about that in the knowledge base.", null));
            onEvent.accept(new StreamEvent("done", null, null));  // Send done event to complete the stream
            return;
        }

        // Build prompt
        String contextBlock = results.stream()
                .map(SearchResultWithScore::text)
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = """
                You are a helpful assistant answering questions based on the provided documentation.

                RULES:
                1. Use the information from the context below to answer the question.
                2. Do not make up information that is not in the context.
                3. If the context does not contain relevant information, say "I don't have information about that in the knowledge base."

                FORMAT:
                - Respond in plain, natural language.
                - If asked about steps or processes, use numbered steps.

                Context:
                %s

                Question: %s

                Answer:""".formatted(contextBlock, question);

        // Send prompt event for audit trail
        onEvent.accept(new StreamEvent("prompt", null, null, prompt));

        // Stream LLM response
        long llmStart = System.currentTimeMillis();
        chat.chatStream(prompt, 0.2, 256, token -> {
            onEvent.accept(new StreamEvent("token", token, null));
        });
        long llmTime = System.currentTimeMillis() - llmStart;
        log.info("[TIMING] LLM streaming generation: {}ms", llmTime);
        metrics.recordLlmChatTime(llmTime);

        // Send done event
        onEvent.accept(new StreamEvent("done", null, null));

        long totalTime = System.currentTimeMillis() - totalStart;
        log.info("[TIMING] Total RAG streaming query time: {}ms (embed={}ms, search={}ms, llm={}ms)",
                totalTime, embedTime, searchTime, llmTime);
        metrics.recordQueryTime(totalTime);
    }

    /**
     * Event types for streaming response
     */
    public record StreamEvent(
            String type,      // "sources", "token", "done", "error", "prompt"
            String token,     // token content (for type="token")
            List<SourceChunk> sources,  // sources (for type="sources")
            String prompt     // LLM prompt (for type="prompt")
    ) {
        // Constructor without prompt for backward compatibility
        public StreamEvent(String type, String token, List<SourceChunk> sources) {
            this(type, token, sources, null);
        }
    }

    private static String stableId(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
