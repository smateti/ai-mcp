package com.naagi.rag.retrieval;

import com.naagi.rag.llm.ChatClient;
import com.naagi.rag.llm.ChatMessage;
import com.naagi.rag.llm.ChatRequest;
import com.naagi.rag.llm.ChatResponse;
import com.naagi.rag.llm.EmbeddingsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * HyDE — Hypothetical Document Embeddings.
 *
 * Instead of embedding the raw user query for retrieval, HyDE asks the LLM to generate
 * a hypothetical "ideal answer" paragraph and embeds that instead. This bridges the
 * vocabulary gap between questions and document content, improving retrieval quality.
 *
 * Reference: Gao et al., "Precise Zero-Shot Dense Retrieval without Relevance Labels" (2022)
 */
@Service
public class HyDeService {

    private static final Logger log = LoggerFactory.getLogger(HyDeService.class);

    private final ChatClient chatClient;
    private final EmbeddingsClient embeddingsClient;
    private final boolean enabled;

    private static final String HYDE_PROMPT = """
            Write a short, factual paragraph (3-5 sentences) that would be the ideal answer \
            to the following question. Write as if you are quoting from a technical document. \
            Do not say "I don't know" — generate a plausible answer based on the topic. \
            Only output the paragraph, nothing else.

            Question: %s

            Answer paragraph:""";

    public HyDeService(
            ChatClient chatClient,
            EmbeddingsClient embeddingsClient,
            @Value("${naagi.rag.hyde.enabled:false}") boolean enabled) {
        this.chatClient = chatClient;
        this.embeddingsClient = embeddingsClient;
        this.enabled = enabled;
        log.info("[HyDE] Initialized, enabled={}", enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Generate a hypothetical document embedding for the given query.
     * Falls back to standard query embedding if HyDE generation fails.
     *
     * @param query The user's question
     * @return Embedding vector (either HyDE or standard fallback)
     */
    public List<Double> embedWithHyDE(String query) {
        if (!enabled) {
            return embeddingsClient.embed(query);
        }

        try {
            long start = System.currentTimeMillis();

            // Generate hypothetical answer
            String prompt = HYDE_PROMPT.formatted(query);
            ChatResponse response = chatClient.chat(ChatRequest.of(
                    List.of(
                            ChatMessage.system("You are a technical documentation writer. Generate factual content."),
                            ChatMessage.user(prompt)
                    ),
                    0.3, 256));

            String hypotheticalDoc = response.content();
            if (hypotheticalDoc == null || hypotheticalDoc.isBlank()) {
                log.warn("[HyDE] Empty response, falling back to standard embedding");
                return embeddingsClient.embed(query);
            }

            // Embed the hypothetical document instead of the query
            List<Double> embedding = embeddingsClient.embed(hypotheticalDoc);

            long duration = System.currentTimeMillis() - start;
            log.info("[HyDE] Generated hypothetical doc ({}chars) and embedded in {}ms for query: {}",
                    hypotheticalDoc.length(), duration, query.substring(0, Math.min(80, query.length())));
            log.debug("[HyDE] Hypothetical doc: {}", hypotheticalDoc.substring(0, Math.min(200, hypotheticalDoc.length())));

            return embedding;

        } catch (Exception e) {
            log.warn("[HyDE] Failed ({}), falling back to standard embedding", e.getMessage());
            return embeddingsClient.embed(query);
        }
    }
}
