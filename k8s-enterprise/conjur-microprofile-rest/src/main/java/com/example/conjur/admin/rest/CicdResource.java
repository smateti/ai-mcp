package com.example.conjur.admin.rest;

import com.example.conjur.admin.service.K8sSecretManager;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/cicd")
@RequestScoped
@Tag(name = "CI/CD", description = "K8s secret management for CI/CD pipelines")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CicdResource {

    private static final Logger LOG = Logger.getLogger(CicdResource.class.getName());

    @Inject
    private K8sSecretManager k8sSecretManager;

    @POST
    @Path("/k8s-secret")
    @Operation(summary = "Create K8s secret with Conjur identity",
               description = "Creates a K8s secret containing CONJUR_APPLIANCE_URL, CONJUR_ACCOUNT, "
                           + "CONJUR_AUTHN_LOGIN, CONJUR_AUTHN_API_KEY, and CONJUR_SECRETS")
    public Response createK8sSecret(Map<String, Object> body) {
        String secretName = (String) body.get("secretName");
        String namespace = (String) body.getOrDefault("namespace", "apps");
        String hostPath = (String) body.get("hostPath");
        String apiKey = (String) body.get("apiKey");
        String conjurSecrets = (String) body.get("conjurSecrets");

        if (secretName == null || hostPath == null || apiKey == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "secretName, hostPath, and apiKey are required")).build();
        }

        if (!k8sSecretManager.isAvailable()) {
            return Response.serverError()
                    .entity(Map.of("error", "Kubernetes client not available")).build();
        }

        try {
            String name = k8sSecretManager.createFullAppSecret(secretName, namespace, hostPath, apiKey, conjurSecrets);
            LOG.info("Created K8s secret: " + namespace + "/" + name);
            return Response.ok(Map.of(
                    "message", "K8s secret created",
                    "secretName", name,
                    "namespace", namespace,
                    "hostPath", hostPath
            )).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to create K8s secret", e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to create K8s secret: " + e.getMessage())).build();
        }
    }

    @GET
    @Path("/k8s-secrets")
    @Operation(summary = "List Conjur-managed K8s secrets")
    public Response listK8sSecrets(@QueryParam("namespace") @DefaultValue("apps") String namespace) {
        if (!k8sSecretManager.isAvailable()) {
            return Response.serverError()
                    .entity(Map.of("error", "Kubernetes client not available")).build();
        }

        try {
            List<Map<String, String>> secrets = k8sSecretManager.listManagedSecrets(namespace);
            return Response.ok(secrets).build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to list K8s secrets", e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to list K8s secrets: " + e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/k8s-secret/{namespace}/{name}")
    @Operation(summary = "Delete a Conjur-managed K8s secret")
    public Response deleteK8sSecret(@PathParam("namespace") String namespace,
                                     @PathParam("name") String name) {
        if (!k8sSecretManager.isAvailable()) {
            return Response.serverError()
                    .entity(Map.of("error", "Kubernetes client not available")).build();
        }

        try {
            k8sSecretManager.deleteSecret(namespace, name);
            LOG.info("Deleted K8s secret: " + namespace + "/" + name);
            return Response.ok(Map.of(
                    "message", "K8s secret deleted",
                    "secretName", name,
                    "namespace", namespace
            )).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to delete K8s secret", e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to delete K8s secret: " + e.getMessage())).build();
        }
    }
}
