package com.example.conjur.web.client;

import com.example.conjur.web.client.model.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "conjur-api")
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ConjurApiClient {

    @GET
    @Path("/status")
    StatusInfo getStatus();

    @GET
    @Path("/secrets")
    List<SecretEntry> listSecrets();

    @GET
    @Path("/secrets/{variableId}")
    SecretEntry getSecret(@PathParam("variableId") String variableId);

    @PUT
    @Path("/secrets/{variableId}")
    Map<String, String> putSecret(@PathParam("variableId") String variableId, Map<String, String> body);

    @GET
    @Path("/secrets/db/{dbname}")
    DbCredentials getDbCredentials(@PathParam("dbname") String dbname);

    @PUT
    @Path("/secrets/db/{dbname}")
    Map<String, String> putDbCredentials(@PathParam("dbname") String dbname, Map<String, String> body);

    @GET
    @Path("/policies")
    Map<String, Object> getPolicies();

    @POST
    @Path("/policies/register-database")
    PolicyResult registerDatabase(Map<String, String> body);

    @POST
    @Path("/policies/register-app")
    AppRegistration registerApp(Map<String, String> body);

    // Bulk onboarding endpoints

    @POST
    @Path("/onboard/databases")
    Map<String, Object> bulkRegisterDatabases(Map<String, Object> body);

    @POST
    @Path("/onboard/apps")
    Map<String, Object> bulkRegisterApps(Map<String, Object> body);

    @POST
    @Path("/onboard/full")
    Map<String, Object> fullOnboard(Map<String, Object> body);
}
