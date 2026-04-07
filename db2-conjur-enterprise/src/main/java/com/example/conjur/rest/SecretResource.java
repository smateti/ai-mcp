package com.example.conjur.rest;

import com.example.conjur.service.ConjurService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;
import java.util.Optional;

@Path("/secrets")
@RequestScoped
@Tag(name = "Secrets", description = "Manage secrets via CyberArk Conjur")
public class SecretResource {

    @Inject
    private ConjurService conjurService;

    @Inject
    @ConfigProperty(name = "conjur.secret.db.username", defaultValue = "not-set")
    private String dbUsername;

    @Inject
    @ConfigProperty(name = "conjur.secret.db.password", defaultValue = "not-set")
    private String dbPassword;

    @Inject
    @ConfigProperty(name = "conjur.secret.db.url", defaultValue = "not-set")
    private String dbUrl;

    @GET
    @Path("/{variableId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get a secret", description = "Retrieves a secret value from Conjur by variable ID")
    @APIResponse(responseCode = "200", description = "Secret found")
    @APIResponse(responseCode = "404", description = "Secret not found")
    public Response getSecret(
            @Parameter(description = "Conjur variable ID (use dot notation, e.g., db.password)")
            @PathParam("variableId") String variableId) {

        String conjurPath = variableId.replace(".", "/");
        Optional<String> value = conjurService.getSecret(conjurPath);

        if (value.isPresent()) {
            return Response.ok(Map.of("variableId", conjurPath, "value", value.get())).build();
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Secret not found: " + conjurPath))
                .build();
    }

    @PUT
    @Path("/{variableId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Write a secret", description = "Writes or updates a secret value in Conjur")
    @APIResponse(responseCode = "200", description = "Secret written successfully")
    @APIResponse(responseCode = "500", description = "Failed to write to Conjur")
    public Response putSecret(
            @Parameter(description = "Conjur variable ID (use dot notation, e.g., db.password)")
            @PathParam("variableId") String variableId,
            Map<String, String> body) {
        try {
            String value = body.get("value");
            if (value == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Request body must contain 'value' field"))
                        .build();
            }
            String conjurPath = variableId.replace(".", "/");
            conjurService.addSecret(conjurPath, value);
            return Response.ok(Map.of("message", "Secret written", "variableId", conjurPath)).build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("error", "Failed to write secret: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/config/db")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get DB config", description = "Shows DB credentials injected via MicroProfile Config from Conjur")
    @APIResponse(responseCode = "200", description = "DB config returned")
    public Response getDbConfig() {
        return Response.ok(Map.of(
                "db.username", dbUsername,
                "db.password.masked", maskValue(dbPassword),
                "db.url", dbUrl,
                "source", "MicroProfile Config (Conjur ConfigSource)"
        )).build();
    }

    private String maskValue(String value) {
        if (value == null || value.length() <= 3) return "***";
        return value.substring(0, 2) + "***" + value.substring(value.length() - 1);
    }
}
