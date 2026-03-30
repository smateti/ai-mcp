package com.example.conjur.admin.rest;

import com.example.conjur.admin.model.*;
import com.example.conjur.admin.service.ConjurClient;
import com.example.conjur.admin.service.K8sSecretManager;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.StringReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Path("/onboard")
@RequestScoped
@Tag(name = "Onboarding", description = "Bulk registration of databases, applications, and K8s secrets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OnboardResource {

    private static final Logger LOG = Logger.getLogger(OnboardResource.class.getName());

    @Inject
    private ConjurClient conjurClient;

    @Inject
    private K8sSecretManager k8sSecretManager;

    @POST
    @Path("/databases")
    @Operation(summary = "Register multiple databases",
               description = "Creates Conjur variables ({name}-uid, {name}-pwd) for each database in a single policy append")
    @APIResponse(responseCode = "200", description = "Bulk database registration results")
    public Response bulkRegisterDatabases(BulkDatabaseRequest request) {
        if (request.getDatabases() == null || request.getDatabases().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Body must contain 'databases' array")).build();
        }

        BulkResult result = new BulkResult();

        // Register each database individually (Conjur rejects combined anchor YAML)
        for (String dbName : request.getDatabases()) {
            try {
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
                        """.formatted(dbName, dbName);

                conjurClient.appendPolicy("db", policyYaml);
                conjurClient.addKnownDatabase(dbName);
                result.addResult(Map.of(
                        "name", dbName,
                        "variables", List.of("db/" + dbName + "-uid", "db/" + dbName + "-pwd"),
                        "status", "created"
                ));
                LOG.info("Registered database: " + dbName);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to register database: " + dbName, e);
                result.addError(dbName + ": " + e.getMessage());
            }
        }

        return Response.ok(result).build();
    }

    @POST
    @Path("/apps")
    @Operation(summary = "Register multiple applications",
               description = "Creates Conjur host identities for each app and returns API keys (save them — shown only once)")
    @APIResponse(responseCode = "200", description = "Bulk app registration results with API keys")
    public Response bulkRegisterApps(BulkAppRequest request) {
        if (request.getApps() == null || request.getApps().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Body must contain 'apps' array")).build();
        }

        BulkResult result = new BulkResult();

        // Build combined YAML policy for all hosts
        StringBuilder policyYaml = new StringBuilder();
        for (String appName : request.getApps()) {
            policyYaml.append("""
                    - !host %s

                    - !grant
                      role: !layer
                      member: !host %s

                    """.formatted(appName, appName));
        }

        try {
            String response = conjurClient.appendPolicy("apps", policyYaml.toString());
            LOG.info("Bulk registered " + request.getApps().size() + " apps: " + response);

            // Parse created_roles to extract API keys per host
            Map<String, String> apiKeys = extractAllHostApiKeys(response);

            for (String appName : request.getApps()) {
                conjurClient.addKnownApp(appName);
                String hostId = "apps/" + appName;
                String apiKey = findApiKeyForHost(apiKeys, appName);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", appName);
                entry.put("hostId", hostId);
                entry.put("apiKey", apiKey);
                entry.put("status", apiKey != null ? "created" : "created (key not found)");
                result.addResult(entry);
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Bulk app registration failed", e);
            result.addError("Policy append failed: " + e.getMessage());
        }

        return Response.ok(result).build();
    }

    @POST
    @Path("/full")
    @Operation(summary = "Full onboarding",
               description = "Registers databases, apps, and creates per-app K8s Secrets in one operation. "
                           + "Returns API keys for all apps (save them — shown only once).")
    @APIResponse(responseCode = "200", description = "Full onboarding results")
    public Response fullOnboard(BulkOnboardRequest request) {
        if (request.getApps() == null || request.getApps().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Body must contain 'apps' array")).build();
        }

        Map<String, Object> fullResult = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        // Step 1: Collect and deduplicate all databases
        Set<String> allDatabases = new LinkedHashSet<>();
        for (BulkOnboardRequest.AppEntry app : request.getApps()) {
            if (app.getDatabases() != null) {
                allDatabases.addAll(app.getDatabases());
            }
        }

        // Step 2: Register all databases (one at a time — Conjur rejects combined anchor YAML)
        if (!allDatabases.isEmpty()) {
            int dbRegistered = 0;
            for (String dbName : allDatabases) {
                try {
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
                            """.formatted(dbName, dbName);
                    conjurClient.appendPolicy("db", policyYaml);
                    conjurClient.addKnownDatabase(dbName);
                    dbRegistered++;
                } catch (Exception e) {
                    errors.add("Database '" + dbName + "': " + e.getMessage());
                }
            }
            fullResult.put("databases", Map.of("registered", dbRegistered, "names", allDatabases));
        }

        // Step 3: Register all apps
        List<String> appNames = request.getApps().stream()
                .map(BulkOnboardRequest.AppEntry::getName)
                .collect(Collectors.toList());

        Map<String, String> apiKeys = new LinkedHashMap<>();
        try {
            StringBuilder policyYaml = new StringBuilder();
            for (String appName : appNames) {
                policyYaml.append("""
                        - !host %s

                        - !grant
                          role: !layer
                          member: !host %s

                        """.formatted(appName, appName));
            }
            String response = conjurClient.appendPolicy("apps", policyYaml.toString());
            apiKeys = extractAllHostApiKeys(response);
            appNames.forEach(conjurClient::addKnownApp);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "App registration failed", e);
            errors.add("App registration failed: " + e.getMessage());
        }

        // Step 4: Create per-app K8s Secrets
        List<Map<String, Object>> appResults = new ArrayList<>();
        for (BulkOnboardRequest.AppEntry app : request.getApps()) {
            Map<String, Object> appResult = new LinkedHashMap<>();
            appResult.put("name", app.getName());
            appResult.put("hostId", "apps/" + app.getName());

            String apiKey = findApiKeyForHost(apiKeys, app.getName());
            appResult.put("apiKey", apiKey);

            // Build CONJUR_SECRETS mapping for this app
            StringBuilder secretsMapping = new StringBuilder();
            if (app.getDatabases() != null) {
                for (String db : app.getDatabases()) {
                    if (secretsMapping.length() > 0) secretsMapping.append(",");
                    secretsMapping.append("db.username=db/").append(db).append("-uid,");
                    secretsMapping.append("db.password=db/").append(db).append("-pwd");
                }
            }
            if (app.getExtraSecrets() != null) {
                for (Map.Entry<String, String> extra : app.getExtraSecrets().entrySet()) {
                    if (secretsMapping.length() > 0) secretsMapping.append(",");
                    secretsMapping.append(extra.getKey()).append("=").append(extra.getValue());
                }
            }
            appResult.put("conjurSecretsMapping", secretsMapping.toString());

            // Create K8s Secret
            if (apiKey != null && k8sSecretManager.isAvailable()) {
                try {
                    String secretName = k8sSecretManager.createAppSecret(
                            app.getName(), app.getNamespace(), apiKey);
                    appResult.put("k8sSecret", secretName);
                    appResult.put("status", "created");
                } catch (Exception e) {
                    appResult.put("k8sSecret", "failed: " + e.getMessage());
                    appResult.put("status", "partial (K8s Secret failed)");
                    errors.add("K8s Secret for " + app.getName() + ": " + e.getMessage());
                }
            } else if (apiKey == null) {
                appResult.put("status", "partial (no API key)");
            } else {
                appResult.put("status", "partial (K8s client unavailable)");
            }

            appResults.add(appResult);
        }

        fullResult.put("apps", appResults);
        fullResult.put("totalApps", appResults.size());
        fullResult.put("errors", errors);

        return Response.ok(fullResult).build();
    }

    private Map<String, String> extractAllHostApiKeys(String jsonResponse) {
        Map<String, String> apiKeys = new LinkedHashMap<>();
        try (JsonReader reader = Json.createReader(new StringReader(jsonResponse))) {
            JsonObject root = reader.readObject();
            JsonObject createdRoles = root.getJsonObject("created_roles");
            if (createdRoles != null) {
                for (String key : createdRoles.keySet()) {
                    JsonObject role = createdRoles.getJsonObject(key);
                    if (role != null && role.containsKey("api_key")) {
                        apiKeys.put(key, role.getString("api_key"));
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not parse API keys from response: " + e.getMessage());
        }
        return apiKeys;
    }

    private String findApiKeyForHost(Map<String, String> apiKeys, String appName) {
        for (Map.Entry<String, String> entry : apiKeys.entrySet()) {
            if (entry.getKey().contains("host") && entry.getKey().contains(appName)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
