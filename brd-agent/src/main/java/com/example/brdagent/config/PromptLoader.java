package com.example.brdagent.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// PROVENANCE: PromptLoader — loads brd-llama.md once at startup, caches it

/**
 * Loads the LLM system prompt from disk at startup and caches it for the
 * lifetime of the application.
 */
@ApplicationScoped
public class PromptLoader {

    private static final Logger LOG = LoggerFactory.getLogger(PromptLoader.class);

    @Inject
    @ConfigProperty(name = "paths.system_prompt", defaultValue = "./prompts/brd-llama.md")
    private String systemPromptPath;

    private String systemPrompt;

    PromptLoader() {
        // CDI
    }

    // Test constructor
    public PromptLoader(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    @PostConstruct
    void init() {
        if (systemPrompt != null) {
            return; // already set via test constructor
        }
        Path path = Path.of(systemPromptPath).toAbsolutePath().normalize();
        LOG.info("Loading system prompt from: {}", path);
        try {
            systemPrompt = Files.readString(path, StandardCharsets.UTF_8);
            LOG.info("System prompt loaded ({} chars)", systemPrompt.length());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load system prompt: " + path, e);
        }
    }

    /**
     * Returns the cached system prompt content.
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }
}
