package com.example.rag.service;

import com.example.rag.chunk.HybridChunker;
import com.example.rag.llm.ChatClient;
import com.example.rag.llm.EmbeddingsClient;
import com.example.rag.qdrant.QdrantClient;
import com.example.rag.qdrant.QdrantClient.Point;
import com.example.rag.qdrant.QdrantClient.SearchResultWithScore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Service
public class RagService {

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
    // Embed the question
    List<Double> qVec = embed.embed(question);

    // Search with scores
    List<SearchResultWithScore> results = qdrant.searchWithScores(qVec, topK, category);

    // Build context from results
    String contextBlock = results.stream()
        .map(SearchResultWithScore::text)
        .collect(Collectors.joining("\n\n---\n\n"));

    // Generate answer using LLM
    String prompt = """
You are a helpful assistant. Use ONLY the context to answer.
If the answer is not in the context, say "I don't know".

Context:
%s

Question: %s
Answer:
""".formatted(contextBlock, question);

    String answer = chat.chatOnce(prompt, 0.2, 512);

    // Convert search results to source chunks
    List<SourceChunk> sources = results.stream()
        .map(r -> new SourceChunk(r.docId(), r.chunkIndex(), r.score(), r.text()))
        .collect(Collectors.toList());

    return new QueryResult(question, answer, sources);
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
