package com.example.conjur.rest;

import com.example.conjur.lib.ConjurSecretProvider;
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

@Path("/secrets")
@RequestScoped
@Tag(name = "Secrets", description = "Manage secrets via CyberArk Conjur")
public class SecretResource {

    @Inject
    private ConjurSecretProvider conjur;

    @Inject
    @ConfigProperty(name = "db.username", defaultValue = "not-set")
    private String dbUsername;

    @Inject
    @ConfigProperty(name = "db.password", defaultValue = "not-set")
    private String dbPassword;

    @GET
    @Path("/{variableId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get a secret", description = "Retrieves a secret value from Conjur by variable ID")
    @APIResponse(responseCode = "200", description = "Secret found")
    @APIResponse(responseCode = "404", description = "Secret not found")
    public Response getSecret(
            @Parameter(description = "Conjur variable ID (use dot notation, e.g., db.empdb-uid)")
            @PathParam("variableId") String variableId) {

        String conjurPath = variableId.replace(".", "/");
        try {
            String value = conjur.getSecret(conjurPath);
            if (value != null) {
                return Response.ok(Map.of("variableId", conjurPath, "value", value)).build();
            }
        } catch (Exception e) {
            // fall through to 404
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Secret not found: " + conjurPath))
                .build();
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
                "source", "conjur-microprofile-lib (ConjurConfigSource)"
        )).build();
    }

    private String maskValue(String value) {
        if (value == null || value.length() <= 3) return "***";
        return value.substring(0, 2) + "***" + value.substring(value.length() - 1);
    }
}
