package com.example.rag.service;

import com.example.rag.chunk.HybridChunker;
import com.example.rag.llm.ChatClient;
import com.example.rag.llm.EmbeddingsClient;
import com.example.rag.qdrant.QdrantClient;
import com.example.rag.qdrant.QdrantClient.Point;
import com.example.rag.qdrant.QdrantClient.SearchResultWithScore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  /**
   * Result object for RAG queries including answer and source chunks.
   */
  public record QueryResult(
      String question,
      String answer,
      List<SourceChunk> sources
  ) {}

  /**
   * Individual source chunk with metadata.
   */
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

  private final int topK;
  private final int batchSize;
  private final Semaphore embedPermits;

  private volatile boolean loggedEmbeddingDim = false;

  // Cache for RAG query results to avoid expensive LLM calls on repeated questions
  // Key: question + topK + category, Value: QueryResult
  private final Map<String, QueryResult> queryCache = new ConcurrentHashMap<>();
  private static final int MAX_CACHE_SIZE = 500;

  public RagService(
      @Value("${rag.chunking.maxChars}") int maxChars,
      @Value("${rag.chunking.overlapChars}") int overlap,
      @Value("${rag.chunking.minChars}") int minChars,
      @Value("${rag.retrieval.topK}") int topK,
      @Value("${rag.performance.qdrantBatchSize}") int batchSize,
      @Value("${rag.performance.maxConcurrentEmbeddings}") int maxEmbed,
      EmbeddingsClient embed,
      ChatClient chat,
      QdrantClient qdrant
  ) {
    this.chunker = new HybridChunker(maxChars, overlap, minChars);
    this.embed = embed;
    this.chat = chat;
    this.qdrant = qdrant;
    this.topK = topK;
    this.batchSize = batchSize;
    this.embedPermits = new Semaphore(Math.max(1, maxEmbed));
  }

  public int ingest(String docId, String text) {
    return ingest(docId, text, List.of());
  }

  public int ingest(String docId, String text, List<String> categories) {
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
          System.out.println("[RAG] Detected embedding dimension=" + vec.size());
        }
        // Fail fast if embedding dimension doesn't match configured Qdrant collection
        if (vec.size() <= 1) {
          throw new IllegalStateException("Embedding vector looks wrong (dim=" + vec.size() + "). Check embedding server response parsing.");
        }

        // Build payload with categories
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
You are a helpful assistant. Use ONLY the context to answer.
If the answer is not in the context, say "I don't know".

Context:
%s

Question: %s
Answer:
""".formatted(contextBlock, question);

    return chat.chatOnce(prompt, 0.2, 512);
  }

  /**
   * Ask a question and get a detailed result with source chunks and scores.
   * This method is used by the REST API to provide rich responses.
   *
   * @param question the question to answer
   * @param topK number of top chunks to retrieve (overrides default)
   * @return QueryResult containing answer and sources with relevance scores
   */
  public QueryResult askWithSources(String question, int topK) {
    return askWithSources(question, topK, null);
  }

  public QueryResult askWithSources(String question, int topK, String category) {
    long totalStart = System.currentTimeMillis();

    // Build cache key from question + topK + category
    String cacheKey = buildCacheKey(question, topK, category);

    // Check cache first
    QueryResult cached = queryCache.get(cacheKey);
    if (cached != null) {
      long cacheTime = System.currentTimeMillis() - totalStart;
      log.info("[TIMING] Cache HIT - returning cached result in {}ms", cacheTime);
      return cached;
    }

    // Embed the question
    long embedStart = System.currentTimeMillis();
    List<Double> qVec = embed.embed(question);
    long embedTime = System.currentTimeMillis() - embedStart;
    log.info("[TIMING] Embedding generation: {}ms", embedTime);

    // Search with scores
    long searchStart = System.currentTimeMillis();
    List<SearchResultWithScore> results = qdrant.searchWithScores(qVec, topK, category);
    long searchTime = System.currentTimeMillis() - searchStart;
    log.info("[TIMING] Vector search (topK={}): {}ms, found {} results", topK, searchTime, results.size());

    // Build context from results
    String contextBlock = results.stream()
        .map(SearchResultWithScore::text)
        .collect(Collectors.joining("\n\n---\n\n"));

    // Generate answer using LLM with optimized prompt
    String prompt = """
Answer using ONLY the context below. Be concise.

Context:
%s

Q: %s
A:""".formatted(contextBlock, question);

    long llmStart = System.currentTimeMillis();
    // Reduced max tokens from 512 to 256 for faster response
    String answer = chat.chatOnce(prompt, 0.2, 256);
    long llmTime = System.currentTimeMillis() - llmStart;
    log.info("[TIMING] LLM answer generation: {}ms", llmTime);

    // Convert search results to source chunks
    List<SourceChunk> sources = results.stream()
        .map(r -> new SourceChunk(r.docId(), r.chunkIndex(), r.score(), r.text()))
        .collect(Collectors.toList());

    QueryResult result = new QueryResult(question, answer, sources);

    // Only cache high-quality results - skip caching if:
    // 1. No sources found
    // 2. Best relevance score is too low (< 0.65)
    // 3. Answer indicates no information ("I don't know", "no information", etc.)
    boolean shouldCache = !sources.isEmpty()
        && sources.get(0).relevanceScore() >= 0.65
        && !isNoInformationAnswer(answer);

    if (shouldCache && queryCache.size() < MAX_CACHE_SIZE) {
      queryCache.put(cacheKey, result);
      log.info("[CACHE] ✅ Cached result for question: {} (score: {})", question, sources.get(0).relevanceScore());
    } else if (!shouldCache) {
      log.info("[CACHE] ❌ Skipped caching low-quality result - score: {}, isNoInfo: {}, question: {}",
          sources.isEmpty() ? 0.0 : sources.get(0).relevanceScore(),
          isNoInformationAnswer(answer),
          question);
    }

    long totalTime = System.currentTimeMillis() - totalStart;
    log.info("[TIMING] Total RAG query time: {}ms (embed={}ms, search={}ms, llm={}ms)",
             totalTime, embedTime, searchTime, llmTime);

    return result;
  }

  private String buildCacheKey(String question, int topK, String category) {
    String normalized = question.toLowerCase().trim();
    String cat = category == null ? "" : category;
    return normalized + "|" + topK + "|" + cat;
  }

  /**
   * Check if the answer indicates no information was found.
   * These answers should not be cached as they may become valid if documentation is added later.
   */
  private boolean isNoInformationAnswer(String answer) {
    if (answer == null || answer.isEmpty()) {
      return true;
    }

    String lowerAnswer = answer.toLowerCase().trim();

    // Common patterns for "no information" responses
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
