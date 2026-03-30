package com.example.conjur.web.rest;

import com.example.conjur.web.client.ConjurApiClient;
import com.example.conjur.web.client.model.AppRegistration;
import com.example.conjur.web.client.model.DbCredentials;
import com.example.conjur.web.client.model.PolicyResult;
import com.example.conjur.web.client.model.SecretEntry;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Proxies /api/* calls from the browser to the conjur-microprofile-rest service.
 * This allows the web UI JavaScript to use relative paths without CORS issues.
 */
@Path("/api")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApiProxyResource {

    @Inject
    @RestClient
    private ConjurApiClient api;

    @GET
    @Path("/status")
    public Response status() {
        return Response.ok(api.getStatus()).build();
    }

    @GET
    @Path("/secrets")
    public List<SecretEntry> listSecrets() {
        return api.listSecrets();
    }

    @GET
    @Path("/secrets/{variableId}")
    public SecretEntry getSecret(@PathParam("variableId") String variableId) {
        return api.getSecret(variableId);
    }

    @PUT
    @Path("/secrets/{variableId}")
    public Map<String, String> putSecret(@PathParam("variableId") String variableId, Map<String, String> body) {
        return api.putSecret(variableId, body);
    }

    @GET
    @Path("/secrets/db/{dbname}")
    public DbCredentials getDbCredentials(@PathParam("dbname") String dbname) {
        return api.getDbCredentials(dbname);
    }

    @PUT
    @Path("/secrets/db/{dbname}")
    public Map<String, String> putDbCredentials(@PathParam("dbname") String dbname, Map<String, String> body) {
        return api.putDbCredentials(dbname, body);
    }

    @GET
    @Path("/policies")
    public Map<String, Object> getPolicies() {
        return api.getPolicies();
    }

    @POST
    @Path("/policies/register-database")
    public PolicyResult registerDatabase(Map<String, String> body) {
        return api.registerDatabase(body);
    }

    @POST
    @Path("/policies/register-app")
    public AppRegistration registerApp(Map<String, String> body) {
        return api.registerApp(body);
    }

    // Bulk onboarding proxies

    @POST
    @Path("/onboard/databases")
    public Map<String, Object> bulkRegisterDatabases(Map<String, Object> body) {
        return api.bulkRegisterDatabases(body);
    }

    @POST
    @Path("/onboard/apps")
    public Map<String, Object> bulkRegisterApps(Map<String, Object> body) {
        return api.bulkRegisterApps(body);
    }

    @POST
    @Path("/onboard/full")
    public Map<String, Object> fullOnboard(Map<String, Object> body) {
        return api.fullOnboard(body);
    }
}
