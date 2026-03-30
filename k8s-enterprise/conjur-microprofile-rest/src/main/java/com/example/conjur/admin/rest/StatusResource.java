package com.example.conjur.admin.rest;

import com.example.conjur.admin.service.ConjurClient;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/status")
@RequestScoped
@Tag(name = "Status", description = "Conjur connection and configuration status")
@Produces(MediaType.APPLICATION_JSON)
public class StatusResource {

    @Inject
    private ConjurClient conjurClient;

    @GET
    @Operation(summary = "Connection status",
               description = "Shows Conjur server connectivity, account details, and configured databases/apps")
    @APIResponse(responseCode = "200", description = "Status returned")
    public Response getStatus() {
        Map<String, Object> info = conjurClient.getInfo();
        info.put("knownSecrets", conjurClient.getKnownSecrets());
        info.put("knownDatabases", conjurClient.getKnownDatabases());
        info.put("knownApps", conjurClient.getKnownApps());
        return Response.ok(info).build();
    }
}
