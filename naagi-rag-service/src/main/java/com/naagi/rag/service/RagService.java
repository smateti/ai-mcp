package com.naagi.rag.service;

import com.naagi.rag.chunk.HybridChunker;
import com.naagi.rag.entity.DocumentUpload;
import com.naagi.rag.llm.ChatClient;
import com.naagi.rag.llm.EmbeddingsClient;
import com.naagi.rag.metrics.RagMetrics;
import com.naagi.rag.qdrant.QdrantClient;
import com.naagi.rag.qdrant.QdrantClient.Point;
import com.naagi.rag.qdrant.QdrantClient.SearchResultWithScore;
import com.naagi.rag.rerank.RerankerService;
import com.naagi.rag.rerank.RerankerService.Document;
import com.naagi.rag.rerank.RerankerService.RerankResult;
import com.naagi.rag.repository.DocumentUploadRepository;
import com.naagi.rag.search.BM25Index;
import com.naagi.rag.search.HybridSearchService;
import com.naagi.rag.search.HybridSearchService.HybridResult;
import com.naagi.rag.search.HybridSearchService.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
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
            String text,
            String title
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

    // Hybrid search components
    private final BM25Index bm25Index;
    private final HybridSearchService hybridSearchService;
    private final boolean hybridSearchEnabled;
    private final double hybridDenseWeight;
    private final double hybridSparseWeight;

    // Category mappings for BM25 filtering
    private final Map<String, List<String>> documentCategories = new ConcurrentHashMap<>();

    // Reranking service
    private final RerankerService rerankerService;

    // Document upload repository for title lookup
    private final DocumentUploadRepository documentUploadRepository;

    public RagService(
            @Value("${naagi.rag.chunking.maxChars}") int maxChars,
            @Value("${naagi.rag.chunking.overlapChars}") int overlap,
            @Value("${naagi.rag.chunking.minChars}") int minChars,
            @Value("${naagi.rag.retrieval.topK}") int topK,
            @Value("${naagi.rag.retrieval.minRelevanceScore:0.75}") double minRelevanceScore,
            @Value("${naagi.rag.performance.qdrantBatchSize}") int batchSize,
            @Value("${naagi.rag.performance.maxConcurrentEmbeddings}") int maxEmbed,
            @Value("${naagi.rag.hybrid.enabled:false}") boolean hybridSearchEnabled,
            @Value("${naagi.rag.hybrid.dense-weight:0.7}") double hybridDenseWeight,
            @Value("${naagi.rag.hybrid.sparse-weight:0.3}") double hybridSparseWeight,
            @Value("${naagi.rag.hybrid.rrf-k:60}") double rrfK,
            EmbeddingsClient embed,
            ChatClient chat,
            QdrantClient qdrant,
            RagMetrics metrics,
            RerankerService rerankerService,
            DocumentUploadRepository documentUploadRepository
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

        // Initialize hybrid search
        this.hybridSearchEnabled = hybridSearchEnabled;
        this.hybridDenseWeight = hybridDenseWeight;
        this.hybridSparseWeight = hybridSparseWeight;
        this.bm25Index = new BM25Index();
        this.hybridSearchService = new HybridSearchService(rrfK);

        // Initialize reranker
        this.rerankerService = rerankerService;

        // Initialize document upload repository for title lookup
        this.documentUploadRepository = documentUploadRepository;

        log.info("[RAG] Initialized with minRelevanceScore={}, hybridSearch={}, denseWeight={}, sparseWeight={}, reranker={}",
                minRelevanceScore, hybridSearchEnabled, hybridDenseWeight, hybridSparseWeight, rerankerService.isEnabled());
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

                String chunkId = stableId(docId + ":" + i + ":" + chunk);

                Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("docId", docId);
                payload.put("chunkIndex", i);
                payload.put("text", chunk);
                if (categories != null && !categories.isEmpty()) {
                    payload.put("categories", categories);
                }

                Point p = new Point(chunkId, vec, payload);

                batch.add(p);
                if (batch.size() >= batchSize) {
                    qdrant.upsertBatch(batch);
                    batch.clear();
                }

                // Index in BM25 for hybrid search
                if (hybridSearchEnabled) {
                    bm25Index.index(chunkId, docId, i, chunk);
                    if (categories != null && !categories.isEmpty()) {
                        documentCategories.put(chunkId, new ArrayList<>(categories));
                    }
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
        log.info("[TIMING] Document ingest: {}ms for {} chunks (hybridIndex={})",
                ingestTime, chunks.size(), hybridSearchEnabled);

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
                .map(r -> enrichWithTitle(r.docId(), r.chunkIndex(), r.score(), r.text(), r.title()))
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
                .map(r -> enrichWithTitle(r.docId(), r.chunkIndex(), r.score(), r.text(), r.title()))
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

    /**
     * Streaming version that uses pre-retrieved sources.
     * This is used by CRAG-enabled streaming endpoint where sources are already retrieved
     * and evaluated before deciding to stream.
     *
     * @param question The user's question
     * @param sources Pre-retrieved source chunks
     * @param confidenceCategory The CRAG confidence category (affects prompt)
     * @param onEvent Event callback for streaming
     */
    public void askWithSourcesStreamFromSources(String question, List<SourceChunk> sources,
            com.naagi.rag.crag.RetrievalEvaluator.ConfidenceCategory confidenceCategory,
            java.util.function.Consumer<StreamEvent> onEvent) {

        // Build context from sources
        String contextBlock = sources.stream()
                .map(SourceChunk::text)
                .collect(Collectors.joining("\n\n---\n\n"));

        // Build prompt with CRAG-aware instructions
        String confidenceHint = switch (confidenceCategory) {
            case CORRECT -> "The retrieved context appears highly relevant. Answer confidently based on the context.";
            case AMBIGUOUS -> """
                WARNING: The retrieved context may only be PARTIALLY relevant to the question.
                - If the context does not DIRECTLY address the specific question asked, say "I don't have specific information about that in the knowledge base."
                - Do NOT extrapolate or combine unrelated information to construct an answer.
                - Only answer if you find EXPLICIT information about what was asked.""";
            case INCORRECT -> "The retrieved context has LOW relevance. Say 'I don't have information about that in the knowledge base.' unless you find exact matches.";
        };

        String prompt = """
                You are a helpful assistant answering questions based on the provided documentation.

                RELEVANCE NOTE: %s

                RULES:
                1. ONLY use information that is EXPLICITLY stated in the context below.
                2. Do NOT make up, infer, or extrapolate information that is not directly in the context.
                3. If the context does not contain information that DIRECTLY answers the question, say "I don't have information about that in the knowledge base."
                4. If the context talks about a RELATED but DIFFERENT topic, acknowledge this limitation.
                5. Be conservative - it's better to say you don't know than to provide incorrect information.

                FORMAT:
                - Respond in plain, natural language.
                - If asked about steps or processes, use numbered steps.

                Context:
                %s

                Question: %s

                Answer:""".formatted(confidenceHint, contextBlock, question);

        // Send prompt event for audit trail
        onEvent.accept(new StreamEvent("prompt", null, null, prompt));

        // Stream LLM response
        long llmStart = System.currentTimeMillis();
        chat.chatStream(prompt, 0.2, 256, token -> {
            onEvent.accept(new StreamEvent("token", token, null));
        });
        long llmTime = System.currentTimeMillis() - llmStart;
        log.info("[TIMING] LLM streaming generation (CRAG-enabled): {}ms", llmTime);
        metrics.recordLlmChatTime(llmTime);

        // Send done event
        onEvent.accept(new StreamEvent("done", null, null));
    }

    // ==================== Hybrid Search Methods ====================

    /**
     * Perform hybrid search combining dense (semantic) and sparse (BM25) retrieval.
     * Uses Reciprocal Rank Fusion (RRF) to combine results.
     *
     * @param question The search query
     * @param topK Number of results to return
     * @param category Optional category filter
     * @return List of source chunks ranked by hybrid RRF score
     */
    public List<SourceChunk> hybridSearch(String question, int topK, String category) {
        if (!hybridSearchEnabled) {
            log.warn("[HYBRID] Hybrid search not enabled, falling back to dense-only search");
            return denseOnlySearch(question, topK, category);
        }

        long startTime = System.currentTimeMillis();

        // 1. Dense search (semantic)
        long denseStart = System.currentTimeMillis();
        List<Double> qVec = embed.embed(question);
        List<SearchResultWithScore> denseResults = qdrant.searchWithScores(qVec, topK * 2, category);
        long denseTime = System.currentTimeMillis() - denseStart;

        List<SearchHit> denseHits = denseResults.stream()
                .map(r -> new SearchHit(
                        stableId(r.docId() + ":" + r.chunkIndex() + ":" + r.text()),
                        r.docId(),
                        r.chunkIndex(),
                        r.text(),
                        r.title(),
                        r.score()))
                .toList();

        // 2. Sparse search (BM25)
        long sparseStart = System.currentTimeMillis();
        List<BM25Index.BM25Result> sparseResults;
        if (category != null && !category.isBlank()) {
            sparseResults = bm25Index.search(question, topK * 2, category, documentCategories);
        } else {
            sparseResults = bm25Index.search(question, topK * 2);
        }
        long sparseTime = System.currentTimeMillis() - sparseStart;

        List<SearchHit> sparseHits = sparseResults.stream()
                .map(r -> new SearchHit(r.id(), r.docId(), r.chunkIndex(), r.text(), r.title(), r.score()))
                .toList();

        // 3. Fuse with weighted RRF
        long fusionStart = System.currentTimeMillis();
        List<HybridResult> fusedResults = hybridSearchService.fuseWithWeightedRRF(
                denseHits, sparseHits, hybridDenseWeight, hybridSparseWeight, topK);
        long fusionTime = System.currentTimeMillis() - fusionStart;

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("[HYBRID TIMING] total={}ms (dense={}ms, sparse={}ms, fusion={}ms) " +
                        "denseHits={}, sparseHits={}, fusedResults={}",
                totalTime, denseTime, sparseTime, fusionTime,
                denseHits.size(), sparseHits.size(), fusedResults.size());

        // Log fusion details
        long bothCount = fusedResults.stream().filter(r -> r.inDense() && r.inSparse()).count();
        long denseOnlyCount = fusedResults.stream().filter(r -> r.inDense() && !r.inSparse()).count();
        long sparseOnlyCount = fusedResults.stream().filter(r -> !r.inDense() && r.inSparse()).count();
        log.debug("[HYBRID] Result breakdown: both={}, denseOnly={}, sparseOnly={}",
                bothCount, denseOnlyCount, sparseOnlyCount);

        return fusedResults.stream()
                .map(r -> enrichWithTitle(r.docId(), r.chunkIndex(), r.rrfScore(), r.text(), r.title()))
                .toList();
    }

    /**
     * Fallback to dense-only search when hybrid is disabled
     */
    private List<SourceChunk> denseOnlySearch(String question, int topK, String category) {
        List<Double> qVec = embed.embed(question);
        List<SearchResultWithScore> results = qdrant.searchWithScores(qVec, topK, category);
        return results.stream()
                .map(r -> enrichWithTitle(r.docId(), r.chunkIndex(), r.score(), r.text(), r.title()))
                .toList();
    }

    /**
     * Ask a question using hybrid search
     */
    public QueryResult askWithHybridSearch(String question, int topK, String category) {
        long totalStart = System.currentTimeMillis();

        // Use hybrid search for retrieval
        List<SourceChunk> sources = hybridSearch(question, topK, category);

        // Check if top result meets minimum relevance threshold
        // Note: RRF scores are typically small (0.01-0.03), so we check if we have any results
        if (sources.isEmpty()) {
            log.info("[RAG] No results from hybrid search");
            return new QueryResult(question, "I don't have information about that in the knowledge base.", sources);
        }

        String contextBlock = sources.stream()
                .map(SourceChunk::text)
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

        long totalTime = System.currentTimeMillis() - totalStart;
        log.info("[TIMING] Total hybrid RAG query time: {}ms (llm={}ms)", totalTime, llmTime);

        return new QueryResult(question, answer, sources);
    }

    /**
     * Get BM25 index statistics
     */
    public Map<String, Object> getBM25Stats() {
        Map<String, Object> stats = new HashMap<>(bm25Index.getStats());
        stats.put("hybridSearchEnabled", hybridSearchEnabled);
        stats.put("denseWeight", hybridDenseWeight);
        stats.put("sparseWeight", hybridSparseWeight);
        return stats;
    }

    /**
     * Check if hybrid search is enabled
     */
    public boolean isHybridSearchEnabled() {
        return hybridSearchEnabled;
    }

    /**
     * Clear the BM25 index (useful for reindexing)
     */
    public void clearBM25Index() {
        bm25Index.clear();
        documentCategories.clear();
        log.info("[HYBRID] BM25 index cleared");
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

    // ==================== Re-ranking Methods ====================

    /**
     * Search with re-ranking pipeline:
     * 1. Initial retrieval (dense or hybrid) gets candidate_count results
     * 2. Cross-encoder re-ranks candidates
     * 3. Return top-K re-ranked results
     *
     * @param question The search query
     * @param topK Number of final results to return
     * @param category Optional category filter
     * @return Re-ranked source chunks
     */
    public List<SourceChunk> searchWithReranking(String question, int topK, String category) {
        if (!rerankerService.isEnabled()) {
            log.debug("[RERANK] Reranker disabled, using standard search");
            return hybridSearchEnabled
                    ? hybridSearch(question, topK, category)
                    : denseOnlySearch(question, topK, category);
        }

        long startTime = System.currentTimeMillis();

        // 1. Initial retrieval with more candidates
        int candidateCount = rerankerService.getCandidateCount();
        List<SourceChunk> candidates = hybridSearchEnabled
                ? hybridSearch(question, candidateCount, category)
                : denseOnlySearch(question, candidateCount, category);

        if (candidates.isEmpty()) {
            return List.of();
        }

        long retrievalTime = System.currentTimeMillis() - startTime;

        // 2. Convert to reranker documents
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            SourceChunk chunk = candidates.get(i);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("docId", chunk.docId());
            metadata.put("chunkIndex", chunk.chunkIndex());
            metadata.put("title", chunk.title());
            documents.add(new Document(
                    stableId(chunk.docId() + ":" + chunk.chunkIndex()),
                    chunk.text(),
                    chunk.relevanceScore(),
                    metadata
            ));
        }

        // 3. Re-rank
        long rerankStart = System.currentTimeMillis();
        List<RerankResult> reranked = rerankerService.rerank(question, documents, topK);
        long rerankTime = System.currentTimeMillis() - rerankStart;

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("[RERANK PIPELINE] total={}ms (retrieval={}ms, rerank={}ms) candidates={}, reranked={}",
                totalTime, retrievalTime, rerankTime, candidates.size(), reranked.size());

        // Log rank changes for top results
        for (int i = 0; i < Math.min(3, reranked.size()); i++) {
            RerankResult r = reranked.get(i);
            log.debug("[RERANK] Rank {}: originalRank={}, rerankScore={:.4f}, initialScore={:.4f}",
                    i + 1, r.originalRank() + 1, r.rerankScore(), r.initialScore());
        }

        // 4. Convert back to SourceChunks
        return reranked.stream()
                .map(r -> enrichWithTitle(
                        (String) r.metadata().get("docId"),
                        (Integer) r.metadata().get("chunkIndex"),
                        r.rerankScore(),
                        r.text(),
                        (String) r.metadata().get("title")
                ))
                .toList();
    }

    /**
     * Ask a question using the full retrieval + re-ranking pipeline
     */
    public QueryResult askWithReranking(String question, int topK, String category) {
        long totalStart = System.currentTimeMillis();

        // Use re-ranking pipeline for retrieval
        List<SourceChunk> sources = searchWithReranking(question, topK, category);

        if (sources.isEmpty()) {
            log.info("[RAG] No results from re-ranking pipeline");
            return new QueryResult(question, "I don't have information about that in the knowledge base.", sources);
        }

        String contextBlock = sources.stream()
                .map(SourceChunk::text)
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

        long totalTime = System.currentTimeMillis() - totalStart;
        log.info("[TIMING] Total re-ranking RAG query time: {}ms (llm={}ms)", totalTime, llmTime);

        return new QueryResult(question, answer, sources);
    }

    /**
     * Get reranker statistics
     */
    public Map<String, Object> getRerankerStats() {
        return rerankerService.getStats();
    }

    /**
     * Check if reranker is enabled
     */
    public boolean isRerankerEnabled() {
        return rerankerService.isEnabled();
    }

    /**
     * Enrich a source chunk with title from the database if missing.
     * This is needed for documents that were ingested before the title field was added to Qdrant.
     */
    private SourceChunk enrichWithTitle(String docId, int chunkIndex, double score, String text, String title) {
        String enrichedTitle = title;
        if (enrichedTitle == null || enrichedTitle.isBlank()) {
            try {
                enrichedTitle = documentUploadRepository.findByDocId(docId)
                        .map(DocumentUpload::getTitle)
                        .orElse(null);
            } catch (Exception e) {
                log.debug("Could not lookup title for docId {}: {}", docId, e.getMessage());
            }
        }
        return new SourceChunk(docId, chunkIndex, score, text, enrichedTitle);
    }
}
