package com.example.conjur.admin.rest;

import com.example.conjur.admin.model.AppRegistration;
import com.example.conjur.admin.model.PolicyResult;
import com.example.conjur.admin.service.ConjurClient;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/policies")
@RequestScoped
@Tag(name = "Policies", description = "Register databases and applications in Conjur")
@Produces(MediaType.APPLICATION_JSON)
public class PolicyResource {

    private static final Logger LOG = Logger.getLogger(PolicyResource.class.getName());

    @Inject
    private ConjurClient conjurClient;

    @GET
    @Operation(summary = "List policy structure",
               description = "Shows the known Conjur policy branches, variables, and application hosts")
    @APIResponse(responseCode = "200", description = "Policy info returned")
    public Response getPolicies() {
        Map<String, Object> policies = new LinkedHashMap<>();
        policies.put("account", conjurClient.getAccount());
        policies.put("branches", new String[]{"root", "db", "apps"});
        policies.put("description", Map.of(
                "root", "Top-level policy defining 'db' and 'apps' branches",
                "db", "Database secrets (convention: {dbname}-uid, {dbname}-pwd)",
                "apps", "Application host identities and layer for access grants"
        ));
        policies.put("knownDatabases", conjurClient.getKnownDatabases());
        policies.put("knownApps", conjurClient.getKnownApps());
        policies.put("knownVariables", conjurClient.getKnownSecrets());
        return Response.ok(policies).build();
    }

    @POST
    @Path("/register-database")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Register a new database",
               description = "Creates Conjur variables for DB credentials ({name}-uid and {name}-pwd) "
                           + "in the 'db' policy branch with read permissions for the apps layer")
    @APIResponse(responseCode = "200", description = "Database registered, variables created")
    @APIResponse(responseCode = "400", description = "Missing database name")
    public Response registerDatabase(
            @RequestBody(description = "Database registration request with 'name' field")
            Map<String, String> body) {

        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Body must contain 'name' field")).build();
        }

        // Generate Conjur policy YAML for database variables
        String policyYaml = """
                - &variables
                  - !variable %s-uid
                  - !variable %s-pwd

                - !group secrets-readers

                - !permit
                  resource: *variables
                  privileges: [ read, execute ]
                  roles: !group secrets-readers

                - !grant
                  role: !group secrets-readers
                  member: !layer /apps
                """.formatted(name, name);

        try {
            String response = conjurClient.appendPolicy("db", policyYaml);
            LOG.info("Registered database '" + name + "' in Conjur: " + response);

            conjurClient.addKnownDatabase(name);

            PolicyResult result = new PolicyResult();
            result.setMessage("Database '" + name + "' registered successfully");
            result.setBranch("db");
            result.setCreatedVariables(List.of("db/" + name + "-uid", "db/" + name + "-pwd"));
            result.setRawResponse(response);
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to register database: " + name, e);
            PolicyResult result = new PolicyResult();
            result.setError("Failed to register database: " + e.getMessage());
            result.setBranch("db");
            return Response.serverError().entity(result).build();
        }
    }

    @POST
    @Path("/register-app")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Register a new application",
               description = "Creates a Conjur host identity for an application and grants it "
                           + "to the apps layer. Returns the host API key (save it — shown only once).")
    @APIResponse(responseCode = "200", description = "Application registered, API key returned")
    @APIResponse(responseCode = "400", description = "Missing application name")
    public Response registerApp(
            @RequestBody(description = "App registration request with 'name' field")
            Map<String, String> body) {

        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Body must contain 'name' field")).build();
        }

        // Generate Conjur policy YAML for host identity
        String policyYaml = """
                - !host %s

                - !grant
                  role: !layer
                  member: !host %s
                """.formatted(name, name);

        try {
            String response = conjurClient.appendPolicy("apps", policyYaml);
            LOG.info("Registered app '" + name + "' in Conjur: " + response);

            conjurClient.addKnownApp(name);

            AppRegistration reg = new AppRegistration();
            reg.setName(name);
            reg.setHostId("apps/" + name);
            reg.setMessage("Application '" + name + "' registered successfully");

            // Parse API key from created_roles in response
            String apiKey = extractHostApiKey(response, name);
            reg.setApiKey(apiKey);

            return Response.ok(reg).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to register app: " + name, e);
            AppRegistration reg = new AppRegistration();
            reg.setName(name);
            reg.setError("Failed to register app: " + e.getMessage());
            return Response.serverError().entity(reg).build();
        }
    }

    @POST
    @Path("/load")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Load generic policy YAML",
               description = "Loads arbitrary policy YAML to a branch using PUT (replace), POST (append), or PATCH (update)")
    public Response loadPolicy(Map<String, String> body) {
        String branch = body.get("branch");
        String yaml = body.get("yaml");
        String method = body.get("method");

        if (branch == null || yaml == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "branch and yaml are required")).build();
        }
        if (method == null) method = "POST";

        try {
            String response = switch (method.toUpperCase()) {
                case "PUT" -> conjurClient.loadPolicy(branch, yaml);
                case "POST" -> conjurClient.appendPolicy(branch, yaml);
                case "PATCH" -> conjurClient.patchPolicy(branch, yaml);
                default -> throw new IllegalArgumentException("method must be PUT, POST, or PATCH");
            };

            PolicyResult result = new PolicyResult();
            result.setMessage("Policy loaded (" + method.toUpperCase() + ") at " + branch);
            result.setBranch(branch);
            result.setRawResponse(response);
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load policy at " + branch, e);
            PolicyResult result = new PolicyResult();
            result.setError("Failed to load policy: " + e.getMessage());
            return Response.serverError().entity(result).build();
        }
    }

    private String extractHostApiKey(String jsonResponse, String appName) {
        try (JsonReader reader = Json.createReader(new StringReader(jsonResponse))) {
            JsonObject root = reader.readObject();
            JsonObject createdRoles = root.getJsonObject("created_roles");
            if (createdRoles != null) {
                for (String key : createdRoles.keySet()) {
                    if (key.contains("host") && key.contains(appName)) {
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
