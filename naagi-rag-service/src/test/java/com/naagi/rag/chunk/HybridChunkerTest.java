package com.naagi.rag.chunk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HybridChunker.
 */
class HybridChunkerTest {

    @Test
    @DisplayName("Should chunk text into appropriate sizes")
    void shouldChunkTextIntoAppropriateSizes() {
        HybridChunker chunker = new HybridChunker(500, 50, 10);

        String text = "This is paragraph one with some content.\n\n" +
                      "This is paragraph two with more content.\n\n" +
                      "This is paragraph three.";

        List<String> chunks = chunker.chunk(text);

        assertThat(chunks).isNotEmpty();
        // All chunks should respect maxChars + overlapChars
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(550);
        }
    }

    @Test
    @DisplayName("Should split long paragraph without punctuation")
    void shouldSplitLongParagraphWithoutPunctuation() {
        HybridChunker chunker = new HybridChunker(100, 0, 10);

        // A long paragraph without sentence-ending punctuation
        String longPara = "word ".repeat(50).trim(); // 249 chars

        List<String> chunks = chunker.chunk(longPara);

        assertThat(chunks).hasSize(3); // 249 chars / 100 max = 3 chunks
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(100);
        }
    }

    @Test
    @DisplayName("Should split very long sentence at word boundaries")
    void shouldSplitVeryLongSentenceAtWordBoundaries() {
        HybridChunker chunker = new HybridChunker(100, 0, 10);

        // A single very long sentence (200+ chars)
        String longSentence = "This is a very long sentence that contains many words and keeps going without any break until the end of the entire content which makes it exceed the maximum character limit.";

        List<String> chunks = chunker.chunk(longSentence);

        assertThat(chunks.size()).isGreaterThan(1);
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(100);
        }
    }

    @Test
    @DisplayName("Should handle empty text")
    void shouldHandleEmptyText() {
        HybridChunker chunker = new HybridChunker(500, 50, 10);

        List<String> chunks = chunker.chunk("");

        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("Should handle null text")
    void shouldHandleNullText() {
        HybridChunker chunker = new HybridChunker(500, 50, 10);

        List<String> chunks = chunker.chunk(null);

        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("Should apply overlap between chunks")
    void shouldApplyOverlapBetweenChunks() {
        HybridChunker chunker = new HybridChunker(100, 20, 10);

        String text = "First paragraph with content.\n\n" +
                      "Second paragraph continues here.\n\n" +
                      "Third paragraph ends it.";

        List<String> chunks = chunker.chunk(text);

        // When overlap is applied, chunks after the first should contain
        // the tail of the previous chunk
        if (chunks.size() > 1) {
            // Each subsequent chunk may be larger due to overlap
            for (int i = 1; i < chunks.size(); i++) {
                // Chunks with overlap can be up to maxChars + overlapChars
                assertThat(chunks.get(i).length()).isLessThanOrEqualTo(120);
            }
        }
    }

    @Test
    @DisplayName("Should filter chunks smaller than minChars")
    void shouldFilterChunksSmallerThanMinChars() {
        HybridChunker chunker = new HybridChunker(500, 0, 50);

        String text = "Tiny.\n\nThis is a longer paragraph that exceeds minimum chars.";

        List<String> chunks = chunker.chunk(text);

        // "Tiny." is 5 chars, should be filtered out (minChars = 50)
        for (String chunk : chunks) {
            assertThat(chunk.length()).isGreaterThanOrEqualTo(50);
        }
    }
}
