package com.naagi.rag.rerank;

import com.naagi.rag.rerank.RerankerService.Document;
import com.naagi.rag.rerank.RerankerService.RerankResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RerankerServiceTest {

    @Nested
    class DisabledRerankerTests {

        @Test
        void testDisabledRerankerReturnsOriginalOrder() {
            RerankerService service = new RerankerService(
                    false, // disabled
                    "local",
                    "http://localhost:8001",
                    "test-model",
                    "",
                    50,
                    0.0
            );

            List<Document> docs = List.of(
                    new Document("id1", "First document about Java", 0.9, Map.of()),
                    new Document("id2", "Second document about Python", 0.8, Map.of()),
                    new Document("id3", "Third document about Go", 0.7, Map.of())
            );

            List<RerankResult> results = service.rerank("programming languages", docs, 3);

            assertEquals(3, results.size());
            // Original order should be preserved
            assertEquals("id1", results.get(0).id());
            assertEquals("id2", results.get(1).id());
            assertEquals("id3", results.get(2).id());
            // Scores should be initial scores (since no re-ranking)
            assertEquals(0.9, results.get(0).rerankScore(), 0.001);
        }

        @Test
        void testDisabledRerankerRespectsTopK() {
            RerankerService service = new RerankerService(
                    false,
                    "local",
                    "http://localhost:8001",
                    "test-model",
                    "",
                    50,
                    0.0
            );

            List<Document> docs = List.of(
                    new Document("id1", "Doc 1", 0.9, Map.of()),
                    new Document("id2", "Doc 2", 0.8, Map.of()),
                    new Document("id3", "Doc 3", 0.7, Map.of()),
                    new Document("id4", "Doc 4", 0.6, Map.of()),
                    new Document("id5", "Doc 5", 0.5, Map.of())
            );

            List<RerankResult> results = service.rerank("test query", docs, 2);

            assertEquals(2, results.size());
            assertEquals("id1", results.get(0).id());
            assertEquals("id2", results.get(1).id());
        }
    }

    @Nested
    class DocumentRecordTests {

        @Test
        void testDocumentRecord() {
            Map<String, Object> metadata = Map.of("source", "test", "page", 1);
            Document doc = new Document("doc-123", "Some text content", 0.85, metadata);

            assertEquals("doc-123", doc.id());
            assertEquals("Some text content", doc.text());
            assertEquals(0.85, doc.initialScore(), 0.001);
            assertEquals("test", doc.metadata().get("source"));
            assertEquals(1, doc.metadata().get("page"));
        }
    }

    @Nested
    class RerankResultRecordTests {

        @Test
        void testRerankResultRecord() {
            Map<String, Object> metadata = Map.of("docId", "doc1", "chunkIndex", 0);
            RerankResult result = new RerankResult(
                    "chunk-id",
                    "Result text",
                    0.95,  // rerankScore
                    0.75,  // initialScore
                    5,     // originalRank
                    0,     // newRank
                    metadata
            );

            assertEquals("chunk-id", result.id());
            assertEquals("Result text", result.text());
            assertEquals(0.95, result.rerankScore(), 0.001);
            assertEquals(0.75, result.initialScore(), 0.001);
            assertEquals(5, result.originalRank());
            assertEquals(0, result.newRank());
            assertEquals("doc1", result.metadata().get("docId"));
        }

        @Test
        void testRankImprovement() {
            RerankResult improved = new RerankResult(
                    "id1", "text", 0.9, 0.5, 10, 1, Map.of()
            );
            // Document went from rank 10 to rank 1 - significant improvement
            assertTrue(improved.originalRank() > improved.newRank());

            RerankResult declined = new RerankResult(
                    "id2", "text", 0.3, 0.9, 1, 8, Map.of()
            );
            // Document went from rank 1 to rank 8 - significant decline
            assertTrue(declined.originalRank() < declined.newRank());
        }
    }

    @Nested
    class EmptyInputTests {

        @Test
        void testEmptyDocumentList() {
            RerankerService service = new RerankerService(
                    true,
                    "local",
                    "http://localhost:8001",
                    "test-model",
                    "",
                    50,
                    0.0
            );

            List<RerankResult> results = service.rerank("test query", List.of(), 5);

            assertTrue(results.isEmpty());
        }
    }

    @Nested
    class ConfigurationTests {

        @Test
        void testServiceConfiguration() {
            RerankerService service = new RerankerService(
                    true,
                    "cohere",
                    "http://localhost:8001",
                    "rerank-english-v3.0",
                    "test-api-key",
                    100,
                    0.5
            );

            assertTrue(service.isEnabled());
            assertEquals("cohere", service.getProvider());
            assertEquals(100, service.getCandidateCount());
        }

        @Test
        void testGetStats() {
            RerankerService service = new RerankerService(
                    true,
                    "jina",
                    "http://localhost:8001",
                    "jina-reranker-v2",
                    "api-key",
                    75,
                    0.3
            );

            Map<String, Object> stats = service.getStats();

            assertEquals(true, stats.get("enabled"));
            assertEquals("jina", stats.get("provider"));
            assertEquals("jina-reranker-v2", stats.get("model"));
            assertEquals(75, stats.get("candidateCount"));
            assertEquals(0.3, stats.get("minScore"));
        }
    }

    @Nested
    class MinScoreFilterTests {

        @Test
        void testMinScoreFiltering() {
            // With minScore = 0.0, disabled reranker just returns original
            RerankerService service = new RerankerService(
                    false,
                    "local",
                    "http://localhost:8001",
                    "test-model",
                    "",
                    50,
                    0.0
            );

            List<Document> docs = List.of(
                    new Document("id1", "High relevance doc", 0.9, Map.of()),
                    new Document("id2", "Medium relevance doc", 0.5, Map.of()),
                    new Document("id3", "Low relevance doc", 0.1, Map.of())
            );

            List<RerankResult> results = service.rerank("query", docs, 3);

            // All should be returned when minScore is 0.0
            assertEquals(3, results.size());
        }
    }

    @Nested
    class ProviderSelectionTests {

        @Test
        void testLocalProvider() {
            RerankerService service = new RerankerService(
                    true, "local", "http://localhost:8001", "bge-reranker-base", "", 50, 0.0
            );
            assertEquals("local", service.getProvider());
        }

        @Test
        void testCohereProvider() {
            RerankerService service = new RerankerService(
                    true, "cohere", "http://localhost:8001", "rerank-english-v3.0", "key", 50, 0.0
            );
            assertEquals("cohere", service.getProvider());
        }

        @Test
        void testJinaProvider() {
            RerankerService service = new RerankerService(
                    true, "jina", "http://localhost:8001", "jina-reranker-v2", "key", 50, 0.0
            );
            assertEquals("jina", service.getProvider());
        }

        @Test
        void testLlmProvider() {
            RerankerService service = new RerankerService(
                    true, "llm", "http://localhost:8000", "llama3", "", 50, 0.0
            );
            assertEquals("llm", service.getProvider());
        }
    }
}
