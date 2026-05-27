package com.example.brdagent.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.brdagent.agent.BrdExtractionAgent;
import com.example.brdagent.agent.ExtractionResult;
import com.example.brdagent.scanner.FoxProSourceScanner;
import com.example.brdagent.scanner.SourceFile;

// PROVENANCE: JAX-RS resource — POST /extract kicks off extraction for a unit

/**
 * REST endpoint that triggers BRD extraction for a set of FoxPro source files.
 * <p>
 * Golden slice: processes files sequentially within the request thread.
 * Full orchestration with concurrency will be added in a later phase.
 */
@Path("/extract")
@RequestScoped
public class ExtractionResource {

    private static final Logger LOG = LoggerFactory.getLogger(ExtractionResource.class);

    @Inject
    private FoxProSourceScanner scanner;

    @Inject
    private BrdExtractionAgent agent;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response extract(@Valid ExtractionRequest request) {
        LOG.info("Extraction requested for unit '{}' with {} source paths",
                request.getUnitName(), request.getSourcePaths().size());

        List<SourceFile> files;
        try {
            files = scanner.scan(request.getSourcePaths());
        } catch (IOException e) {
            LOG.error("Failed to scan source paths: {}", e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Failed to scan source paths: " + e.getMessage() + "\"}")
                    .build();
        }

        if (files.isEmpty()) {
            LOG.warn("No .prg files found in the specified paths");
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\": \"No .prg files found in the specified paths\"}")
                    .build();
        }

        LOG.info("Found {} .prg files, starting extraction", files.size());

        List<ExtractionResult> results = new ArrayList<>();
        for (SourceFile file : files) {
            ExtractionResult result = agent.extractFromFile(file);
            results.add(result);
        }

        ExtractionResponse response = new ExtractionResponse(request.getUnitName(), results);
        LOG.info("Extraction complete for unit '{}': {}/{} succeeded",
                request.getUnitName(), response.getFilesSucceeded(), response.getFilesProcessed());

        return Response.ok(response).build();
    }
}
