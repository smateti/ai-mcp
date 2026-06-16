package com.example.buildtrigger.rest;

import java.util.Collection;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.example.buildtrigger.model.Application;
import com.example.buildtrigger.registry.ApplicationRegistry;

@Path("/applications")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationResource {

    @Inject
    private ApplicationRegistry registry;

    @GET
    @Operation(summary = "List all registered applications",
               description = "Returns every application defined in registry.json with its type, services, and valid environments.")
    @APIResponse(responseCode = "200",
                 description = "List of applications",
                 content = @Content(schema = @Schema(implementation = Application.class)))
    public Collection<Application> listAll() {
        return registry.getAll();
    }
}
