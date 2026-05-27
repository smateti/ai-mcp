package com.example.brdagent.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

// PROVENANCE: LlamaClient — type-safe MP Rest Client targeting llama.cpp /v1/chat/completions

/**
 * MicroProfile Rest Client interface for the OpenAI-compatible chat completions API
 * exposed by llama.cpp.
 * <p>
 * Configuration via {@code llama/mp-rest/url} and {@code llama/mp-rest/readTimeout}
 * in microprofile-config.properties.
 */
@RegisterRestClient(configKey = "llama")
@Path("/v1")
public interface LlamaClient {

    @POST
    @Path("/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    LlamaChatResponse chatCompletion(LlamaChatRequest request);
}
