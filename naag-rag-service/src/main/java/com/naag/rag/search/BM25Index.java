package com.naag.rag.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory BM25 index for sparse/lexical retrieval.
 * Thread-safe implementation supporting concurrent reads and writes.
 */
public class BM25Index {

    private static final Logger log = LoggerFactory.getLogger(BM25Index.class);

    // BM25 tuning parameters
    private static final double K1 = 1.5;  // Term frequency saturation (1.2-2.0 typical)
    private static final double B = 0.75;  // Length normalization (0.75 typical)

    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> invertedIndex = new ConcurrentHashMap<>();
    private volatile double avgDocLength = 0;
    private volatile int totalDocs = 0;

    /**
     * Indexed document record
     */
    public record Document(
            String id,
            String docId,
            int chunkIndex,
            String text,
            Map<String, Integer> termFrequencies,
            int length
    ) {}

    /**
     * BM25 search result
     */
    public record BM25Result(
            String id,
            String docId,
            int chunkIndex,
            String text,
            double score
    ) {}

    /**
     * Tokenize text into terms.
     * - Lowercase
     * - Remove punctuation
     * - Split on whitespace
     * - Filter short tokens and stopwords
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        return Arrays.stream(text.toLowerCase()
                        .replaceAll("[^a-z0-9\\s]", " ")
                        .split("\\s+"))
                .filter(t -> !t.isBlank() && t.length() > 2)
                .filter(t -> !isStopword(t))
                .collect(Collectors.toList());
    }

    /**
     * Common English stopwords to filter out
     */
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "all",
            "can", "had", "her", "was", "one", "our", "out", "has",
            "have", "been", "were", "they", "this", "that", "with",
            "from", "will", "would", "there", "their", "what", "about",
            "which", "when", "make", "like", "time", "just", "know",
            "take", "into", "year", "your", "some", "could", "them",
            "than", "then", "now", "look", "only", "come", "its",
            "over", "also", "back", "after", "use", "two", "how",
            "first", "well", "way", "even", "new", "want", "because",
            "any", "these", "give", "most", "being"
    );

    private boolean isStopword(String term) {
        return STOPWORDS.contains(term);
    }

    /**
     * Index a document chunk
     */
    public synchronized void index(String id, String docId, int chunkIndex, String text) {
        // Remove existing document if re-indexing
        if (documents.containsKey(id)) {
            remove(id);
        }

        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return;
        }

        // Count term frequencies
        Map<String, Integer> termFreq = new HashMap<>();
        for (String token : tokens) {
            termFreq.merge(token, 1, Integer::sum);
        }

        Document doc = new Document(id, docId, chunkIndex, text, termFreq, tokens.size());
        documents.put(id, doc);

        // Update inverted index
        for (String term : termFreq.keySet()) {
            invertedIndex.computeIfAbsent(term, k -> new ConcurrentHashMap<>())
                    .put(id, termFreq.get(term));
        }

        // Update statistics
        totalDocs++;
        recalculateAvgLength();

        log.debug("Indexed document {} with {} tokens", id, tokens.size());
    }

    /**
     * Batch index multiple documents
     */
    public void indexBatch(List<DocumentToIndex> docs) {
        for (DocumentToIndex doc : docs) {
            index(doc.id(), doc.docId(), doc.chunkIndex(), doc.text());
        }
        log.info("Batch indexed {} documents, total index size: {}", docs.size(), totalDocs);
    }

    public record DocumentToIndex(String id, String docId, int chunkIndex, String text) {}

    /**
     * Search using BM25 scoring algorithm
     *
     * BM25 Formula:
     * score(D,Q) = Σ IDF(qi) * (f(qi,D) * (k1 + 1)) / (f(qi,D) + k1 * (1 - b + b * |D|/avgdl))
     *
     * Where:
     * - IDF(qi) = log((N - n(qi) + 0.5) / (n(qi) + 0.5) + 1)
     * - f(qi,D) = frequency of term qi in document D
     * - |D| = document length
     * - avgdl = average document length
     */
    public List<BM25Result> search(String query, int topK) {
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty() || totalDocs == 0) {
            return List.of();
        }

        Map<String, Double> scores = new HashMap<>();

        for (String term : queryTerms) {
            Map<String, Integer> postings = invertedIndex.get(term);
            if (postings == null || postings.isEmpty()) {
                continue;
            }

            // IDF calculation: log((N - df + 0.5) / (df + 0.5) + 1)
            int df = postings.size();
            double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1);

            for (Map.Entry<String, Integer> entry : postings.entrySet()) {
                String docId = entry.getKey();
                int tf = entry.getValue();
                Document doc = documents.get(docId);

                if (doc == null) continue;

                // BM25 term score calculation
                double docLength = doc.length();
                double lengthNorm = 1 - B + B * (docLength / avgDocLength);
                double tfNorm = (tf * (K1 + 1)) / (tf + K1 * lengthNorm);
                double termScore = idf * tfNorm;

                scores.merge(docId, termScore, Double::sum);
            }
        }

        // Sort by score descending and return top K
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    Document doc = documents.get(e.getKey());
                    return new BM25Result(
                            doc.id(),
                            doc.docId(),
                            doc.chunkIndex(),
                            doc.text(),
                            e.getValue()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Search with category filter
     */
    public List<BM25Result> search(String query, int topK, String categoryFilter,
                                    Map<String, List<String>> docCategories) {
        if (categoryFilter == null || categoryFilter.isBlank()) {
            return search(query, topK);
        }

        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty() || totalDocs == 0) {
            return List.of();
        }

        Map<String, Double> scores = new HashMap<>();

        for (String term : queryTerms) {
            Map<String, Integer> postings = invertedIndex.get(term);
            if (postings == null || postings.isEmpty()) {
                continue;
            }

            int df = postings.size();
            double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1);

            for (Map.Entry<String, Integer> entry : postings.entrySet()) {
                String docId = entry.getKey();
                Document doc = documents.get(docId);

                if (doc == null) continue;

                // Check category filter
                List<String> categories = docCategories.get(docId);
                if (categories == null || !categories.contains(categoryFilter)) {
                    continue;
                }

                int tf = entry.getValue();
                double docLength = doc.length();
                double lengthNorm = 1 - B + B * (docLength / avgDocLength);
                double tfNorm = (tf * (K1 + 1)) / (tf + K1 * lengthNorm);
                double termScore = idf * tfNorm;

                scores.merge(docId, termScore, Double::sum);
            }
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    Document doc = documents.get(e.getKey());
                    return new BM25Result(
                            doc.id(),
                            doc.docId(),
                            doc.chunkIndex(),
                            doc.text(),
                            e.getValue()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Remove a document from the index
     */
    public synchronized void remove(String id) {
        Document doc = documents.remove(id);
        if (doc == null) {
            return;
        }

        // Remove from inverted index
        for (String term : doc.termFrequencies().keySet()) {
            Map<String, Integer> postings = invertedIndex.get(term);
            if (postings != null) {
                postings.remove(id);
                if (postings.isEmpty()) {
                    invertedIndex.remove(term);
                }
            }
        }

        totalDocs--;
        recalculateAvgLength();

        log.debug("Removed document {} from index", id);
    }

    /**
     * Remove all documents for a given docId
     */
    public synchronized void removeByDocId(String docId) {
        List<String> toRemove = documents.values().stream()
                .filter(d -> d.docId().equals(docId))
                .map(Document::id)
                .toList();

        for (String id : toRemove) {
            remove(id);
        }

        log.info("Removed {} chunks for docId {}", toRemove.size(), docId);
    }

    /**
     * Clear the entire index
     */
    public synchronized void clear() {
        documents.clear();
        invertedIndex.clear();
        totalDocs = 0;
        avgDocLength = 0;
        log.info("Cleared BM25 index");
    }

    private void recalculateAvgLength() {
        if (documents.isEmpty()) {
            avgDocLength = 0;
        } else {
            avgDocLength = documents.values().stream()
                    .mapToInt(Document::length)
                    .average()
                    .orElse(0);
        }
    }

    public int size() {
        return documents.size();
    }

    public int getVocabularySize() {
        return invertedIndex.size();
    }

    public double getAverageDocumentLength() {
        return avgDocLength;
    }

    /**
     * Get index statistics
     */
    public Map<String, Object> getStats() {
        return Map.of(
                "totalDocuments", totalDocs,
                "vocabularySize", invertedIndex.size(),
                "averageDocumentLength", avgDocLength
        );
    }
}
