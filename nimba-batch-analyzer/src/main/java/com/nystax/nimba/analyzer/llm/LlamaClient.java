package com.nystax.nimba.analyzer.llm;

import com.nystax.nimba.analyzer.config.LlamaClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Component
public class LlamaClient {

    private static final Logger log = LoggerFactory.getLogger(LlamaClient.class);

    private final WebClient webClient;
    private final LlamaClientProperties properties;

    public LlamaClient(WebClient.Builder webClientBuilder, LlamaClientProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .codecs(config -> config.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    public String analyze(String systemPrompt, String userPrompt) {
        if (!properties.isEnabled()) {
            return "[LLM analysis disabled]";
        }

        LlmRequest request = new LlmRequest(
                properties.getModel(),
                List.of(
                        new LlmRequest.Message("system", systemPrompt),
                        new LlmRequest.Message("user", userPrompt)
                ),
                properties.getMaxTokens(),
                properties.getTemperature()
        );

        try {
            log.debug("Sending LLM request ({} chars user prompt)", userPrompt.length());
            LlmResponse response = webClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(LlmResponse.class)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .block();

            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String content = response.getChoices().get(0).getMessage().getContent();
                log.debug("LLM response received ({} chars)", content.length());
                return content;
            }
            return "[LLM returned empty response]";
        } catch (Exception e) {
            log.warn("LLM analysis failed: {}", e.getMessage());
            return "[LLM analysis failed: " + e.getMessage() + "]";
        }
    }
}
