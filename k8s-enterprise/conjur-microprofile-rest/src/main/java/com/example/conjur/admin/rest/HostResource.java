package com.example.conjur.admin.rest;

import com.example.conjur.admin.model.AppRegistration;
import com.example.conjur.admin.model.HostSetup;
import com.example.conjur.admin.service.ConjurClient;
import com.example.conjur.admin.service.PolicyGenerator;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.StringReader;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/hosts")
@RequestScoped
@Tag(name = "Hosts", description = "Application host registration")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class HostResource {

    private static final Logger LOG = Logger.getLogger(HostResource.class.getName());

    @Inject
    private ConjurClient conjurClient;

    @Inject
    private PolicyGenerator policyGenerator;

    @POST
    @Path("/register")
    @Operation(summary = "Register application host",
               description = "Creates host identity with JWT annotations or API key auth")
    public Response registerHost(HostSetup setup) {
        if (setup.getOrgName() == null || setup.getEnvironment() == null
                || setup.getProduct() == null || setup.getAppType() == null || setup.getHostId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "orgName, environment, product, appType, and hostId are required")).build();
        }

        String branch = setup.getOrgName() + "/environments/" + setup.getEnvironment()
                + "/products/" + setup.getProduct()
                + "/apps/" + setup.getAppType();

        try {
            // authMethod determines annotation style: "kubernetes"/"openshift" get JWT annotations,
            // "authn" or null gets a plain host with description annotation
            String authMethod = setup.getAuthMethod();
            String hostAppType = (authMethod != null && !authMethod.isBlank()) ? authMethod : setup.getAppType();
            String jwtServiceId = ("kubernetes".equals(authMethod) || "openshift".equals(authMethod)) ? authMethod : null;

            String yaml = policyGenerator.generateAppHost(
                    setup.getHostId(),
                    hostAppType,
                    setup.getNamespace() != null ? setup.getNamespace() : "apps",
                    setup.getServiceAccount() != null ? setup.getServiceAccount() : setup.getHostId() + "-sa",
                    jwtServiceId
            );

            String response = conjurClient.appendPolicy(branch, yaml);
            LOG.info("Registered host '" + setup.getHostId() + "' at " + branch);

            AppRegistration reg = new AppRegistration();
            reg.setName(setup.getHostId());
            reg.setHostId(branch + "/" + setup.getHostId());
            reg.setMessage("Host '" + setup.getHostId() + "' registered successfully");

            // Extract API key from response
            String apiKey = extractHostApiKey(response, setup.getHostId());
            reg.setApiKey(apiKey);

            return Response.ok(reg).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to register host", e);
            AppRegistration reg = new AppRegistration();
            reg.setName(setup.getHostId());
            reg.setError("Failed to register host: " + e.getMessage());
            return Response.serverError().entity(reg).build();
        }
    }

    private String extractHostApiKey(String jsonResponse, String hostId) {
        try (JsonReader reader = Json.createReader(new StringReader(jsonResponse))) {
            JsonObject root = reader.readObject();
            JsonObject createdRoles = root.getJsonObject("created_roles");
            if (createdRoles != null) {
                for (String key : createdRoles.keySet()) {
                    if (key.contains("host") && key.contains(hostId)) {
                        JsonObject role = createdRoles.getJsonObject(key);
                        if (role != null && role.containsKey("api_key")) {
                            return role.getString("api_key");
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not parse API key from response: " + e.getMessage());
        }
        return null;
    }
}
