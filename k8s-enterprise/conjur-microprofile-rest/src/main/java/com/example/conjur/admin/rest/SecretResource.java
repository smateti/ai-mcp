package com.example.conjur.admin.rest;

import com.example.conjur.admin.model.BulkSecretRequest;
import com.example.conjur.admin.model.DbCredentials;
import com.example.conjur.admin.model.SecretEntry;
import com.example.conjur.admin.service.ConjurClient;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/secrets")
@RequestScoped
@Tag(name = "Secrets", description = "Manage Conjur vault secrets and database credentials")
@Produces(MediaType.APPLICATION_JSON)
public class SecretResource {

    private static final Logger LOG = Logger.getLogger(SecretResource.class.getName());

    @Inject
    private ConjurClient conjurClient;

    @GET
    @Operation(summary = "List known secrets",
               description = "Retrieves all secrets from the configured known-secrets list with their current values")
    @APIResponse(responseCode = "200", description = "Secret list returned")
    public Response listSecrets() {
        List<SecretEntry> entries = new ArrayList<>();
        for (String variableId : conjurClient.getKnownSecrets()) {
            SecretEntry entry = new SecretEntry();
            entry.setVariableId(variableId);
            try {
                String value = conjurClient.getSecret(variableId);
                entry.setValue(value);
                entry.setFound(value != null);
            } catch (Exception e) {
                entry.setFound(false);
                entry.setError(e.getMessage());
            }
            entries.add(entry);
        }
        return Response.ok(entries).build();
    }

    @GET
    @Path("/{variableId}")
    @Operation(summary = "Read a secret",
               description = "Reads a single secret value from Conjur by variable ID (use dot notation, e.g., db.empdb-uid)")
    @APIResponse(responseCode = "200", description = "Secret found")
    @APIResponse(responseCode = "404", description = "Secret not found")
    public Response getSecret(
            @Parameter(description = "Variable ID — dots are converted to slashes (e.g., db.empdb-uid becomes db/empdb-uid)")
            @PathParam("variableId") String variableId) {

        String conjurPath = variableId.replace(".", "/");
        try {
            String value = conjurClient.getSecret(conjurPath);
            if (value != null) {
                return Response.ok(new SecretEntry(conjurPath, value, true)).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Secret not found: " + conjurPath)).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PUT
    @Path("/{variableId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Write a secret",
               description = "Creates or updates a secret value in Conjur")
    @APIResponse(responseCode = "200", description = "Secret written successfully")
    @APIResponse(responseCode = "400", description = "Missing value field")
    public Response putSecret(
            @Parameter(description = "Variable ID (dots converted to slashes)")
            @PathParam("variableId") String variableId,
            Map<String, String> body) {

        String value = body.get("value");
        if (value == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Request body must contain 'value' field")).build();
        }
        String conjurPath = variableId.replace(".", "/");
        try {
            conjurClient.setSecret(conjurPath, value);
            return Response.ok(Map.of("message", "Secret written", "variableId", conjurPath)).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/dbs/{dbname}")
    @Operation(summary = "Read DB credentials",
               description = "Reads the username/password pair for a database (convention: dbs/{name}-uid and dbs/{name}-pwd)")
    @APIResponse(responseCode = "200", description = "Credentials returned")
    public Response getDbCredentials(
            @Parameter(description = "Database name, e.g., empdb")
            @PathParam("dbname") String dbname) {

        DbCredentials creds = new DbCredentials(dbname);
        try {
            creds.setUsername(conjurClient.getSecret("dbs/" + dbname + "-uid"));
            creds.setPassword(conjurClient.getSecret("dbs/" + dbname + "-pwd"));
            creds.setFound(creds.getUsername() != null || creds.getPassword() != null);
        } catch (Exception e) {
            creds.setFound(false);
            creds.setError(e.getMessage());
        }
        return Response.ok(creds).build();
    }

    @PUT
    @Path("/dbs/{dbname}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Write DB credentials",
               description = "Writes or updates the username/password pair for a database")
    @APIResponse(responseCode = "200", description = "Credentials written")
    @APIResponse(responseCode = "400", description = "Missing username or password")
    public Response putDbCredentials(
            @Parameter(description = "Database name, e.g., empdb")
            @PathParam("dbname") String dbname,
            Map<String, String> body) {

        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Body must contain 'username' and 'password' fields")).build();
        }
        try {
            conjurClient.setSecret("dbs/" + dbname + "-uid", username);
            conjurClient.setSecret("dbs/" + dbname + "-pwd", password);
            return Response.ok(Map.of(
                    "message", "DB credentials written",
                    "database", dbname,
                    "uidVariable", "dbs/" + dbname + "-uid",
                    "pwdVariable", "dbs/" + dbname + "-pwd"
            )).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @POST
    @Path("/bulk")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set multiple secrets",
               description = "Sets multiple secret values in a single request")
    public Response bulkSetSecrets(BulkSecretRequest request) {
        if (request.getSecrets() == null || request.getSecrets().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "secrets list is required")).build();
        }

        List<String> success = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (BulkSecretRequest.SecretValue sv : request.getSecrets()) {
            try {
                conjurClient.setSecret(sv.getVariableId(), sv.getValue());
                success.add(sv.getVariableId());
            } catch (Exception e) {
                errors.add(sv.getVariableId() + ": " + e.getMessage());
            }
        }

        LOG.info("Bulk set secrets: " + success.size() + " ok, " + errors.size() + " failed");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", success.size() + " secrets set successfully");
        result.put("set", success);
        result.put("errors", errors);
        return Response.ok(result).build();
    }
}
