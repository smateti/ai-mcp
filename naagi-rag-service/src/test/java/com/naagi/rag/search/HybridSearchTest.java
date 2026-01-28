package com.naagi.rag.search;

import com.naagi.rag.search.BM25Index.BM25Result;
import com.naagi.rag.search.HybridSearchService.HybridResult;
import com.naagi.rag.search.HybridSearchService.SearchHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HybridSearchTest {

    @Nested
    class BM25IndexTests {

        private BM25Index index;

        @BeforeEach
        void setUp() {
            index = new BM25Index();
        }

        @Test
        void testIndexAndSearch() {
            // Index some documents
            index.index("id1", "doc1", 0, "Spring Boot is a framework for building Java applications");
            index.index("id2", "doc1", 1, "Spring Data provides repository abstractions for data access");
            index.index("id3", "doc2", 0, "Python is a programming language for data science");

            assertEquals(3, index.size());

            // Search for Spring
            List<BM25Result> results = index.search("Spring framework", 5);
            assertFalse(results.isEmpty());

            // First result should be about Spring Boot or Spring Data
            BM25Result top = results.get(0);
            assertTrue(top.text().toLowerCase().contains("spring"));
        }

        @Test
        void testSearchWithNoResults() {
            index.index("id1", "doc1", 0, "Java programming language");

            List<BM25Result> results = index.search("completely unrelated xyz", 5);
            assertTrue(results.isEmpty());
        }

        @Test
        void testRemoveDocument() {
            index.index("id1", "doc1", 0, "Test document one");
            index.index("id2", "doc1", 1, "Test document two");

            assertEquals(2, index.size());

            index.remove("id1");
            assertEquals(1, index.size());

            // Search should only find the remaining document
            List<BM25Result> results = index.search("document", 5);
            assertEquals(1, results.size());
            assertEquals("id2", results.get(0).id());
        }

        @Test
        void testRemoveByDocId() {
            index.index("id1", "doc1", 0, "Document one chunk zero");
            index.index("id2", "doc1", 1, "Document one chunk one");
            index.index("id3", "doc2", 0, "Document two chunk zero");

            assertEquals(3, index.size());

            index.removeByDocId("doc1");
            assertEquals(1, index.size());
        }

        @Test
        void testClear() {
            index.index("id1", "doc1", 0, "Test document");
            index.index("id2", "doc2", 0, "Another document");

            assertEquals(2, index.size());

            index.clear();
            assertEquals(0, index.size());
        }

        @Test
        void testStats() {
            index.index("id1", "doc1", 0, "The quick brown fox jumps over the lazy dog");
            index.index("id2", "doc2", 0, "A quick brown dog runs");

            Map<String, Object> stats = index.getStats();
            assertEquals(2, stats.get("totalDocuments"));
            assertTrue((int) stats.get("vocabularySize") > 0);
            assertTrue((double) stats.get("averageDocumentLength") > 0);
        }
    }

    @Nested
    class RRFFusionTests {

        private HybridSearchService service;

        @BeforeEach
        void setUp() {
            service = new HybridSearchService(60.0);
        }

        @Test
        void testBasicRRFFusion() {
            // Dense results (semantic search)
            List<SearchHit> denseResults = List.of(
                    new SearchHit("id1", "doc1", 0, "text1", "Title 1", 0.95),
                    new SearchHit("id2", "doc1", 1, "text2", "Title 1", 0.85),
                    new SearchHit("id3", "doc2", 0, "text3", "Title 2", 0.75)
            );

            // Sparse results (BM25) - note id2 appears in both
            List<SearchHit> sparseResults = List.of(
                    new SearchHit("id2", "doc1", 1, "text2", "Title 1", 5.5),  // Also in dense
                    new SearchHit("id4", "doc3", 0, "text4", "Title 3", 4.2),
                    new SearchHit("id1", "doc1", 0, "text1", "Title 1", 3.8)   // Also in dense
            );

            List<HybridResult> fused = service.fuseWithRRF(denseResults, sparseResults, 5);

            assertFalse(fused.isEmpty());

            // id2 and id1 should be boosted because they appear in both lists
            HybridResult top = fused.get(0);
            assertTrue(top.inDense() && top.inSparse(),
                    "Top result should appear in both lists");

            // All results should have valid RRF scores
            for (HybridResult r : fused) {
                assertTrue(r.rrfScore() > 0, "RRF score should be positive");
            }
        }

        @Test
        void testWeightedRRFFusion() {
            List<SearchHit> denseResults = List.of(
                    new SearchHit("id1", "doc1", 0, "text1", "Title 1", 0.95)
            );

            List<SearchHit> sparseResults = List.of(
                    new SearchHit("id2", "doc2", 0, "text2", "Title 2", 5.5)
            );

            // With higher dense weight
            List<HybridResult> denseWeighted = service.fuseWithWeightedRRF(
                    denseResults, sparseResults, 0.9, 0.1, 5);

            // With higher sparse weight
            List<HybridResult> sparseWeighted = service.fuseWithWeightedRRF(
                    denseResults, sparseResults, 0.1, 0.9, 5);

            assertEquals(2, denseWeighted.size());
            assertEquals(2, sparseWeighted.size());

            // Dense result should rank higher with dense weighting
            assertEquals("id1", denseWeighted.get(0).id());

            // Sparse result should rank higher with sparse weighting
            assertEquals("id2", sparseWeighted.get(0).id());
        }

        @Test
        void testEmptyResults() {
            List<HybridResult> fused = service.fuseWithRRF(List.of(), List.of(), 5);
            assertTrue(fused.isEmpty());

            // Only dense results
            List<SearchHit> denseOnly = List.of(
                    new SearchHit("id1", "doc1", 0, "text1", "Title 1", 0.9)
            );
            List<HybridResult> denseOnlyFused = service.fuseWithRRF(denseOnly, List.of(), 5);
            assertEquals(1, denseOnlyFused.size());
            assertTrue(denseOnlyFused.get(0).inDense());
            assertFalse(denseOnlyFused.get(0).inSparse());

            // Only sparse results
            List<SearchHit> sparseOnly = List.of(
                    new SearchHit("id2", "doc2", 0, "text2", "Title 2", 5.0)
            );
            List<HybridResult> sparseOnlyFused = service.fuseWithRRF(List.of(), sparseOnly, 5);
            assertEquals(1, sparseOnlyFused.size());
            assertFalse(sparseOnlyFused.get(0).inDense());
            assertTrue(sparseOnlyFused.get(0).inSparse());
        }

        @Test
        void testLinearCombination() {
            List<SearchHit> denseResults = List.of(
                    new SearchHit("id1", "doc1", 0, "text1", "Title 1", 0.9),
                    new SearchHit("id2", "doc1", 1, "text2", "Title 1", 0.5)
            );

            List<SearchHit> sparseResults = List.of(
                    new SearchHit("id2", "doc1", 1, "text2", "Title 1", 10.0),
                    new SearchHit("id3", "doc2", 0, "text3", "Title 2", 5.0)
            );

            List<HybridResult> linear = service.fuseWithLinearCombination(
                    denseResults, sparseResults, 0.5, 5);

            assertFalse(linear.isEmpty());
            // id2 appears in both and should get a boost
            assertTrue(linear.stream().anyMatch(r -> r.id().equals("id2")));
        }

        @Test
        void testTopKLimit() {
            List<SearchHit> denseResults = List.of(
                    new SearchHit("id1", "doc1", 0, "text1", "Title 1", 0.9),
                    new SearchHit("id2", "doc1", 1, "text2", "Title 1", 0.8),
                    new SearchHit("id3", "doc1", 2, "text3", "Title 1", 0.7),
                    new SearchHit("id4", "doc1", 3, "text4", "Title 1", 0.6),
                    new SearchHit("id5", "doc1", 4, "text5", "Title 1", 0.5)
            );

            List<HybridResult> limited = service.fuseWithRRF(denseResults, List.of(), 3);
            assertEquals(3, limited.size());
        }
    }
}
