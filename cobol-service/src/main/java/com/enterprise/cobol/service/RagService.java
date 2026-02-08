package com.enterprise.cobol.service;

import com.enterprise.cobol.entity.SavedQuery;
import com.enterprise.cobol.repository.jpa.SavedQueryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagService {

    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final SavedQueryRepository savedQueryRepo;
    private final WebClient llmWebClient;
    private final ObjectMapper objectMapper;

    @Value("${llm.rag-model}")
    private String ragModel;

    @Value("${llm.timeout-seconds}")
    private int timeoutSeconds;

    @Value("${llm.temperature}")
    private double temperature;

    @Value("${llm.max-tokens}")
    private int maxTokens;

    private static final String RAG_SYSTEM_PROMPT = """
            You are a COBOL mainframe expert helping with microservice modernization.
            Answer questions about the COBOL codebase using ONLY the context provided.
            Reference specific program names and paragraph names in your answer.
            If the context doesn't contain enough information, say so.
            Be concise and specific.
            """;

    public RagService(EmbeddingService embeddingService,
                      QdrantService qdrantService,
                      SavedQueryRepository savedQueryRepo,
                      @Value("${llm.base-url}") String llmBaseUrl,
                      ObjectMapper objectMapper) {
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
        this.savedQueryRepo = savedQueryRepo;
        this.llmWebClient = WebClient.builder().baseUrl(llmBaseUrl).build();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> askQuestion(String question) {
        return askQuestion(question, null);
    }

    public Map<String, Object> askQuestion(String question, String batchRunId) {
        // 1. Embed the question
        float[] queryVector = embeddingService.embedQuery(question);

        // 2. Search Qdrant for top-10 relevant chunks (filtered by batchRunId if provided)
        List<QdrantService.SearchResult> results = qdrantService.search(queryVector, 10, batchRunId);

        // 3. Build context from retrieved chunks
        String context = results.stream()
                .map(r -> String.format("[%s - %s]\n%s",
                        r.getPayload().getOrDefault("programName", "?"),
                        r.getPayload().getOrDefault("paragraphName", "program-level"),
                        r.getPayload().getOrDefault("chunkText", "")))
                .collect(Collectors.joining("\n\n"));

        // 4. Call LLM with augmented context
        String answer = callLlm(context, question);

        // 5. Build sources list
        List<Map<String, Object>> sources = results.stream()
                .map(r -> {
                    Map<String, Object> source = new HashMap<>(r.getPayload());
                    source.put("score", r.getScore());
                    return source;
                }).toList();

        // 6. Save to H2
        try {
            SavedQuery saved = SavedQuery.builder()
                    .question(question)
                    .answer(answer)
                    .sources(objectMapper.writeValueAsString(sources))
                    .createdAt(LocalDateTime.now())
                    .build();
            savedQueryRepo.save(saved);
        } catch (Exception e) {
            log.warn("Failed to save query: {}", e.getMessage());
        }

        return Map.of(
                "answer", answer,
                "sources", sources,
                "question", question
        );
    }

    private String callLlm(String context, String question) {
        try {
            String userPrompt = "Context:\n" + context + "\n\nQuestion: " + question;

            Map<String, Object> request = Map.of(
                    "model", ragModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", RAG_SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", temperature,
                    "max_tokens", maxTokens
            );

            String response = llmWebClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("RAG LLM call failed: {}", e.getMessage());
            return "I couldn't generate an answer. Error: " + e.getMessage();
        }
    }
}
