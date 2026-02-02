package com.naagi.rag.chunk;

import com.naagi.rag.llm.EmbeddingsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Semantic chunker that splits text at semantic boundaries.
 *
 * Instead of fixed character splits, this chunker:
 * 1. Splits text into sentences
 * 2. Embeds consecutive groups of sentences
 * 3. Detects semantic shift (cosine similarity drop between adjacent groups)
 * 4. Splits at points where the topic changes
 *
 * This keeps semantically related content together, improving retrieval precision.
 */
public class SemanticChunker {

    private static final Logger log = LoggerFactory.getLogger(SemanticChunker.class);

    private final EmbeddingsClient embeddingsClient;
    private final double similarityThreshold;
    private final int maxChunkChars;
    private final int minChunkChars;
    private final int windowSize; // number of sentences per embedding window

    public SemanticChunker(EmbeddingsClient embeddingsClient, double similarityThreshold,
                           int maxChunkChars, int minChunkChars) {
        this.embeddingsClient = embeddingsClient;
        this.similarityThreshold = similarityThreshold;
        this.maxChunkChars = maxChunkChars;
        this.minChunkChars = minChunkChars;
        this.windowSize = 3; // embed 3 sentences at a time for context
    }

    /**
     * Chunk text using semantic boundary detection.
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) return List.of();

        // Split into sentences
        List<String> sentences = splitSentences(text);
        if (sentences.size() <= windowSize) {
            String joined = String.join(" ", sentences).trim();
            return joined.length() >= minChunkChars ? List.of(joined) : List.of();
        }

        // Build sentence windows and embed them
        List<String> windows = buildWindows(sentences);
        List<List<Double>> embeddings = new ArrayList<>();
        for (String window : windows) {
            try {
                embeddings.add(embeddingsClient.embed(window));
            } catch (Exception e) {
                log.warn("[SEMANTIC-CHUNK] Embedding failed for window, using null marker");
                embeddings.add(null);
            }
        }

        // Find split points where cosine similarity drops below threshold
        List<Integer> splitPoints = new ArrayList<>();
        for (int i = 1; i < embeddings.size(); i++) {
            List<Double> prev = embeddings.get(i - 1);
            List<Double> curr = embeddings.get(i);
            if (prev == null || curr == null) continue;

            double sim = cosineSimilarity(prev, curr);
            if (sim < similarityThreshold) {
                // Split at the sentence boundary corresponding to this window transition
                int sentenceIdx = i + windowSize - 1;
                if (sentenceIdx < sentences.size()) {
                    splitPoints.add(sentenceIdx);
                }
            }
        }

        // Build chunks from split points
        List<String> chunks = new ArrayList<>();
        int start = 0;
        for (int splitIdx : splitPoints) {
            String chunk = joinSentences(sentences, start, splitIdx);
            if (chunk.length() >= minChunkChars) {
                // If chunk is too long, fall back to character-based splitting
                if (chunk.length() > maxChunkChars) {
                    chunks.addAll(splitLongChunk(chunk));
                } else {
                    chunks.add(chunk);
                }
            }
            start = splitIdx;
        }

        // Add remaining sentences
        if (start < sentences.size()) {
            String lastChunk = joinSentences(sentences, start, sentences.size());
            if (lastChunk.length() >= minChunkChars) {
                if (lastChunk.length() > maxChunkChars) {
                    chunks.addAll(splitLongChunk(lastChunk));
                } else {
                    chunks.add(lastChunk);
                }
            } else if (!chunks.isEmpty()) {
                // Merge short trailing text with previous chunk
                String prev = chunks.remove(chunks.size() - 1);
                chunks.add(prev + " " + lastChunk);
            }
        }

        log.info("[SEMANTIC-CHUNK] Split {} sentences into {} chunks (splitPoints={})",
                sentences.size(), chunks.size(), splitPoints.size());
        return chunks;
    }

    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        // Split on sentence-ending punctuation followed by whitespace
        String[] parts = text.split("(?<=[.!?])\\s+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private List<String> buildWindows(List<String> sentences) {
        List<String> windows = new ArrayList<>();
        for (int i = 0; i <= sentences.size() - windowSize; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < i + windowSize && j < sentences.size(); j++) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(sentences.get(j));
            }
            windows.add(sb.toString());
        }
        return windows;
    }

    private String joinSentences(List<String> sentences, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to && i < sentences.size(); i++) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(sentences.get(i));
        }
        return sb.toString().trim();
    }

    private List<String> splitLongChunk(String chunk) {
        // Simple fallback: split at paragraph or sentence boundaries within maxChunkChars
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < chunk.length()) {
            int end = Math.min(start + maxChunkChars, chunk.length());
            if (end < chunk.length()) {
                // Try to break at sentence boundary
                int lastPeriod = chunk.lastIndexOf(". ", end);
                if (lastPeriod > start + minChunkChars) {
                    end = lastPeriod + 1;
                }
            }
            String sub = chunk.substring(start, end).trim();
            if (sub.length() >= minChunkChars) {
                result.add(sub);
            }
            start = end;
        }
        return result;
    }

    private static double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }
}
