package com.example.brdagent.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.brdagent.client.LlamaChatRequest;
import com.example.brdagent.client.LlamaChatResponse;
import com.example.brdagent.client.LlamaClient;
import com.example.brdagent.client.LlamaMessage;
import com.example.brdagent.config.PromptLoader;
import com.example.brdagent.scanner.SourceFile;

// PROVENANCE: agent loop steps 1-3 + step 6 (write partial), golden slice — no retry, no chunking

/**
 * Core extraction agent. Sends a single FoxPro source file to the LLM and
 * parses the response as a {@link PartialBrd}.
 * <p>
 * Golden slice: no retry logic, no chunking, no validation against schema,
 * no snippet verification. These will be added in later phases.
 */
@RequestScoped
public class BrdExtractionAgent {

    private static final Logger LOG = LoggerFactory.getLogger(BrdExtractionAgent.class);

    /** Matches ```json ... ``` fences the LLM sometimes wraps around output. */
    private static final Pattern JSON_FENCE = Pattern.compile(
            "^\\s*```(?:json)?\\s*\\n?(.*?)\\n?\\s*```\\s*$", Pattern.DOTALL);

    @Inject
    @RestClient
    private LlamaClient llamaClient;

    @Inject
    private PromptLoader promptLoader;

    @Inject
    @ConfigProperty(name = "llama.model", defaultValue = "llama-3.1-70b-instruct")
    private String model;

    @Inject
    @ConfigProperty(name = "llama.temperature", defaultValue = "0.1")
    private double temperature;

    @Inject
    @ConfigProperty(name = "llama.top_p", defaultValue = "0.9")
    private double topP;

    @Inject
    @ConfigProperty(name = "llama.max_tokens", defaultValue = "4096")
    private int maxTokens;

    @Inject
    @ConfigProperty(name = "llama.json_mode", defaultValue = "true")
    private boolean jsonMode;

    @Inject
    @ConfigProperty(name = "paths.output_root", defaultValue = "./brd")
    private String outputRoot;

    BrdExtractionAgent() {
        // CDI
    }

    // Test constructor
    BrdExtractionAgent(LlamaClient llamaClient, PromptLoader promptLoader,
                       String model, double temperature, double topP,
                       int maxTokens, boolean jsonMode, String outputRoot) {
        this.llamaClient = llamaClient;
        this.promptLoader = promptLoader;
        this.model = model;
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.jsonMode = jsonMode;
        this.outputRoot = outputRoot;
    }

    /**
     * Extracts a partial BRD from a single source file.
     *
     * @param file the source file to process
     * @return extraction result (success with partial BRD, or failure with error)
     */
    public ExtractionResult extractFromFile(SourceFile file) {
        LOG.info("Extracting BRD from: {} (type={})", file.path(), file.fileType());
        try {
            // Step 1: build user message
            String userMessage = buildUserMessage(file);

            // Step 2: call LLM
            LlamaChatRequest request = buildRequest(userMessage);
            LlamaChatResponse response = llamaClient.chatCompletion(request);

            // Step 3: extract and parse content
            String rawContent = response.extractContent();
            if (rawContent == null || rawContent.isBlank()) {
                return ExtractionResult.failure(file.path(), "LLM returned empty content");
            }

            String content = stripJsonFences(rawContent.trim());
            PartialBrd partialBrd = deserialize(content);

            // Step 6: write partial to disk
            writePartialBrd(file, content);

            LOG.info("Extraction succeeded for: {} (FRs={}, entities={}, BRs={})",
                    file.path(),
                    size(partialBrd.getFunctionalRequirements()),
                    size(partialBrd.getDataEntities()),
                    size(partialBrd.getBusinessRules()));

            return ExtractionResult.success(file.path(), partialBrd);

        } catch (JsonbException e) {
            LOG.error("JSON parse failure for {}: {}", file.path(), e.getMessage());
            return ExtractionResult.failure(file.path(), "JSON parse error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Extraction failed for {}: {}", file.path(), e.getMessage(), e);
            return ExtractionResult.failure(file.path(), e.getMessage());
        }
    }

    // PROVENANCE: agent loop step 1 — build user message with FILE_PATH, FILE_TYPE, CONTENT blocks
    private String buildUserMessage(SourceFile file) {
        return "FILE_PATH: " + file.path() + "\n"
                + "FILE_TYPE: " + file.fileType() + "\n"
                + "CONTENT:\n"
                + file.content();
    }

    // PROVENANCE: agent loop step 2 — build LlamaChatRequest with system + user messages
    private LlamaChatRequest buildRequest(String userMessage) {
        String systemPrompt = promptLoader.getSystemPrompt();
        List<LlamaMessage> messages = List.of(
                new LlamaMessage("system", systemPrompt),
                new LlamaMessage("user", userMessage)
        );
        return new LlamaChatRequest(model, messages, temperature, topP, maxTokens, jsonMode);
    }

    // PROVENANCE: quality requirement — strip ```json fences, handle trailing whitespace
    String stripJsonFences(String raw) {
        var matcher = JSON_FENCE.matcher(raw);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return raw;
    }

    private PartialBrd deserialize(String json) {
        try (Jsonb jsonb = JsonbBuilder.create()) {
            return jsonb.fromJson(json, PartialBrd.class);
        } catch (Exception e) {
            if (e instanceof JsonbException) {
                throw (JsonbException) e;
            }
            throw new JsonbException("Failed to deserialize partial BRD", e);
        }
    }

    // PROVENANCE: agent loop step 6 — write partial to brd/llama-parts/<file>.json
    private void writePartialBrd(SourceFile file, String jsonContent) throws IOException {
        Path outputDir = Path.of(outputRoot).toAbsolutePath().normalize()
                .resolve("llama-parts");
        Files.createDirectories(outputDir);

        // Derive output filename from source path: "programs/CUSTUPD.prg" → "programs_CUSTUPD.json"
        String stem = file.path()
                .replace('/', '_')
                .replace('\\', '_')
                .replaceAll("\\.[^.]+$", "");
        Path outputFile = outputDir.resolve(stem + ".json");

        Files.writeString(outputFile, jsonContent, StandardCharsets.UTF_8);
        LOG.debug("Wrote partial BRD to: {}", outputFile);
    }

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }
}
