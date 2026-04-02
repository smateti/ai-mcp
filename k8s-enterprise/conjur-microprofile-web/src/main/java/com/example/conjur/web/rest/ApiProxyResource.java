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

    // Setup wizard proxies

    @POST
    @Path("/setup/root")
    public Map<String, Object> createRootPolicy(Map<String, Object> body) {
        return api.createRootPolicy(body);
    }

    @POST
    @Path("/setup/environments")
    public Map<String, Object> createEnvironments(Map<String, Object> body) {
        return api.createEnvironments(body);
    }

    @POST
    @Path("/setup/product")
    public Map<String, Object> createProduct(Map<String, Object> body) {
        return api.createProduct(body);
    }

    @POST
    @Path("/authenticators/jwt/setup")
    public Map<String, Object> setupJwtAuthenticator(Map<String, Object> body) {
        return api.setupJwtAuthenticator(body);
    }

    @GET
    @Path("/authenticators")
    public Response listAuthenticators() {
        return Response.ok(api.listAuthenticators()).type(MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("/authenticators/jwt/{serviceId}/enroll")
    public Map<String, Object> enrollHosts(@PathParam("serviceId") String serviceId, List<String> hostPaths) {
        return api.enrollHosts(serviceId, hostPaths);
    }

    @GET
    @Path("/resources")
    public Response listResources(@QueryParam("kind") String kind, @QueryParam("search") String search) {
        return Response.ok(api.listResources(kind, search)).type(MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("/resources/database")
    public Map<String, Object> registerEnterpriseDatabase(Map<String, Object> body) {
        return api.registerEnterpriseDatabase(body);
    }

    @POST
    @Path("/resources/kafka")
    public Map<String, Object> registerKafka(Map<String, Object> body) {
        return api.registerKafka(body);
    }

    @POST
    @Path("/resources/infrastructure")
    public Map<String, Object> registerInfrastructure(Map<String, Object> body) {
        return api.registerInfrastructure(body);
    }

    @POST
    @Path("/hosts/register")
    public Map<String, Object> registerHost(Map<String, Object> body) {
        return api.registerHost(body);
    }

    @POST
    @Path("/access/grant")
    public Map<String, Object> grantAccess(Map<String, Object> body) {
        return api.grantAccess(body);
    }

    @POST
    @Path("/secrets/bulk")
    public Map<String, Object> bulkSetSecrets(Map<String, Object> body) {
        return api.bulkSetSecrets(body);
    }

    @POST
    @Path("/policies/load")
    public Map<String, Object> loadGenericPolicy(Map<String, String> body) {
        return api.loadGenericPolicy(body);
    }

    // Explorer proxies

    @GET
    @Path("/resources/role/{roleId: .+}")
    public Response showRole(@PathParam("roleId") String roleId) {
        return Response.ok(api.showRole(roleId)).type(MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("/setup/apptype")
    public Map<String, Object> addAppType(Map<String, Object> body) {
        return api.addAppType(body);
    }

    // CI/CD proxies

    @POST
    @Path("/cicd/k8s-secret")
    public Map<String, Object> createK8sSecret(Map<String, Object> body) {
        return api.createK8sSecret(body);
    }

    @GET
    @Path("/cicd/k8s-secrets")
    public Response listK8sSecrets(@QueryParam("namespace") @DefaultValue("apps") String namespace) {
        return Response.ok(api.listK8sSecrets(namespace)).type(MediaType.APPLICATION_JSON).build();
    }

    @DELETE
    @Path("/cicd/k8s-secret/{namespace}/{name}")
    public Map<String, Object> deleteK8sSecret(@PathParam("namespace") String namespace,
                                                @PathParam("name") String name) {
        return api.deleteK8sSecret(namespace, name);
    }

    // Variable management proxy

    @POST
    @Path("/resources/variable")
    public Map<String, Object> createVariables(Map<String, Object> body) {
        return api.createVariables(body);
    }

    // Reference data proxies

    @GET
    @Path("/refdata/all")
    public Response getRefDataAll() {
        return Response.ok(api.getRefDataAll()).type(MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/refdata/{type}")
    public Response getRefData(@PathParam("type") String type) {
        return Response.ok(api.getRefData(type)).type(MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("/refdata/{type}")
    public Map<String, Object> createRefData(@PathParam("type") String type, Map<String, String> body) {
        return api.createRefData(type, body);
    }

    @PUT
    @Path("/refdata/{type}/{id}")
    public Map<String, Object> updateRefData(@PathParam("type") String type, @PathParam("id") long id, Map<String, String> body) {
        return api.updateRefData(type, id, body);
    }

    @DELETE
    @Path("/refdata/{type}/{id}")
    public Map<String, Object> deleteRefData(@PathParam("type") String type, @PathParam("id") long id) {
        return api.deleteRefData(type, id);
    }

    // Sample App deploy proxies

    @POST
    @Path("/sample-app/deploy")
    public Map<String, Object> deploySampleApp(Map<String, Object> body) {
        return api.deploySampleApp(body);
    }

    @DELETE
    @Path("/sample-app/undeploy/{name}")
    public Map<String, Object> undeploySampleApp(@PathParam("name") String name,
                                                  @QueryParam("namespace") @DefaultValue("apps") String namespace) {
        return api.undeploySampleApp(name, namespace);
    }

    @GET
    @Path("/sample-app/status/{name}")
    public Response getSampleAppStatus(@PathParam("name") String name,
                                        @QueryParam("namespace") @DefaultValue("apps") String namespace) {
        return Response.ok(api.getSampleAppStatus(name, namespace)).type(MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("/sample-app/logs/{name}")
    public Response getSampleAppLogs(@PathParam("name") String name,
                                      @QueryParam("namespace") @DefaultValue("apps") String namespace,
                                      @QueryParam("container") @DefaultValue("conjur-init") String container) {
        return Response.ok(api.getSampleAppLogs(name, namespace, container)).type(MediaType.APPLICATION_JSON).build();
    }
}
