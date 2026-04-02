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

    // Setup wizard endpoints

    @POST
    @Path("/setup/root")
    Map<String, Object> createRootPolicy(Map<String, Object> body);

    @POST
    @Path("/setup/environments")
    Map<String, Object> createEnvironments(Map<String, Object> body);

    @POST
    @Path("/setup/product")
    Map<String, Object> createProduct(Map<String, Object> body);

    @POST
    @Path("/authenticators/jwt/setup")
    Map<String, Object> setupJwtAuthenticator(Map<String, Object> body);

    @GET
    @Path("/authenticators")
    String listAuthenticators();

    @POST
    @Path("/authenticators/jwt/{serviceId}/enroll")
    Map<String, Object> enrollHosts(@PathParam("serviceId") String serviceId, List<String> hostPaths);

    @GET
    @Path("/resources")
    String listResources(@QueryParam("kind") String kind, @QueryParam("search") String search);

    @POST
    @Path("/resources/database")
    Map<String, Object> registerEnterpriseDatabase(Map<String, Object> body);

    @POST
    @Path("/resources/kafka")
    Map<String, Object> registerKafka(Map<String, Object> body);

    @POST
    @Path("/resources/infrastructure")
    Map<String, Object> registerInfrastructure(Map<String, Object> body);

    @POST
    @Path("/hosts/register")
    Map<String, Object> registerHost(Map<String, Object> body);

    @POST
    @Path("/access/grant")
    Map<String, Object> grantAccess(Map<String, Object> body);

    @POST
    @Path("/secrets/bulk")
    Map<String, Object> bulkSetSecrets(Map<String, Object> body);

    @POST
    @Path("/policies/load")
    Map<String, Object> loadGenericPolicy(Map<String, String> body);

    // Explorer endpoints

    @GET
    @Path("/resources/role/{roleId: .+}")
    String showRole(@PathParam("roleId") String roleId);

    @POST
    @Path("/setup/apptype")
    Map<String, Object> addAppType(Map<String, Object> body);

    // CI/CD endpoints

    @POST
    @Path("/cicd/k8s-secret")
    Map<String, Object> createK8sSecret(Map<String, Object> body);

    @GET
    @Path("/cicd/k8s-secrets")
    String listK8sSecrets(@QueryParam("namespace") String namespace);

    @DELETE
    @Path("/cicd/k8s-secret/{namespace}/{name}")
    Map<String, Object> deleteK8sSecret(@PathParam("namespace") String namespace, @PathParam("name") String name);

    // Variable management

    @POST
    @Path("/resources/variable")
    Map<String, Object> createVariables(Map<String, Object> body);

    // Reference data endpoints

    @GET
    @Path("/refdata/all")
    String getRefDataAll();

    @GET
    @Path("/refdata/{type}")
    String getRefData(@PathParam("type") String type);

    @POST
    @Path("/refdata/{type}")
    Map<String, Object> createRefData(@PathParam("type") String type, Map<String, String> body);

    @PUT
    @Path("/refdata/{type}/{id}")
    Map<String, Object> updateRefData(@PathParam("type") String type, @PathParam("id") long id, Map<String, String> body);

    @DELETE
    @Path("/refdata/{type}/{id}")
    Map<String, Object> deleteRefData(@PathParam("type") String type, @PathParam("id") long id);

    // Sample App deploy endpoints

    @POST
    @Path("/sample-app/deploy")
    Map<String, Object> deploySampleApp(Map<String, Object> body);

    @DELETE
    @Path("/sample-app/undeploy/{name}")
    Map<String, Object> undeploySampleApp(@PathParam("name") String name, @QueryParam("namespace") String namespace);

    @GET
    @Path("/sample-app/status/{name}")
    String getSampleAppStatus(@PathParam("name") String name, @QueryParam("namespace") String namespace);

    @GET
    @Path("/sample-app/logs/{name}")
    String getSampleAppLogs(@PathParam("name") String name, @QueryParam("namespace") String namespace, @QueryParam("container") String container);
}
