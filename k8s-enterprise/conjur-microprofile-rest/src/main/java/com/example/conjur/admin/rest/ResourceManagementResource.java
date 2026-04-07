package com.example.conjur.admin.rest;

import com.example.conjur.admin.model.PolicyResult;
import com.example.conjur.admin.model.ResourceSetup;
import com.example.conjur.admin.service.ConjurClient;
import com.example.conjur.admin.service.PolicyGenerator;
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

@Path("/resources")
@RequestScoped
@Tag(name = "Resources", description = "Conjur resource management")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ResourceManagementResource {

    private static final Logger LOG = Logger.getLogger(ResourceManagementResource.class.getName());

    @Inject
    private ConjurClient conjurClient;

    @Inject
    private PolicyGenerator policyGenerator;

    @GET
    @Operation(summary = "List all Conjur resources")
    public Response listResources(@QueryParam("kind") String kind,
                                   @QueryParam("search") String search) {
        try {
            String json = conjurClient.listResources(kind, search);
            return Response.ok(json).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to list resources", e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to list resources: " + e.getMessage())).build();
        }
    }

    @GET
    @Path("/{kind}")
    @Operation(summary = "List resources by kind")
    public Response listResourcesByKind(@PathParam("kind") String kind) {
        try {
            String json = conjurClient.listResources(kind, null);
            return Response.ok(json).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to list resources of kind: " + kind, e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to list resources: " + e.getMessage())).build();
        }
    }

    @GET
    @Path("/role/{roleId: .+}")
    @Operation(summary = "Show role details with members and memberships")
    public Response showRole(@PathParam("roleId") String roleId) {
        try {
            String json = conjurClient.showRole(roleId);
            if (json == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Role not found: " + roleId)).build();
            }
            return Response.ok(json).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to show role: " + roleId, e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to show role: " + e.getMessage())).build();
        }
    }

    @POST
    @Path("/database")
    @Operation(summary = "Register enterprise database resource",
               description = "Creates DB with url, username, password, port, database-name, driver-class, readers group")
    public Response registerDatabase(ResourceSetup setup) {
        String branch = resourceBranch(setup, "dbs");
        if (branch == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "orgName, environment, product, and resourceName are required")).build();
        }

        try {
            String yaml = policyGenerator.generateDatabaseResource(setup.getResourceName());
            String response = conjurClient.appendPolicy(branch, yaml);
            LOG.info("Registered database '" + setup.getResourceName() + "' at " + branch);

            // Set initial secrets if provided
            if (setup.getInitialSecrets() != null) {
                String varPrefix = branch + "/" + setup.getResourceName() + "/";
                for (Map.Entry<String, String> entry : setup.getInitialSecrets().entrySet()) {
                    conjurClient.setSecret(varPrefix + entry.getKey(), entry.getValue());
                }
            }

            PolicyResult result = new PolicyResult();
            result.setMessage("Database '" + setup.getResourceName() + "' registered");
            result.setBranch(branch);
            result.setRawResponse(response);
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to register database", e);
            PolicyResult result = new PolicyResult();
            result.setError("Failed to register database: " + e.getMessage());
            return Response.serverError().entity(result).build();
        }
    }

    @POST
    @Path("/kafka")
    @Operation(summary = "Register Kafka cluster resource")
    public Response registerKafka(ResourceSetup setup) {
        String branch = resourceBranch(setup, "kafka");
        if (branch == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "orgName, environment, product, and resourceName are required")).build();
        }

        try {
            String yaml = policyGenerator.generateKafkaResource(setup.getResourceName());
            String response = conjurClient.appendPolicy(branch, yaml);

            if (setup.getInitialSecrets() != null) {
                String varPrefix = branch + "/" + setup.getResourceName() + "/";
                for (Map.Entry<String, String> entry : setup.getInitialSecrets().entrySet()) {
                    conjurClient.setSecret(varPrefix + entry.getKey(), entry.getValue());
                }
            }

            PolicyResult result = new PolicyResult();
            result.setMessage("Kafka cluster '" + setup.getResourceName() + "' registered");
            result.setBranch(branch);
            result.setRawResponse(response);
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to register Kafka", e);
            PolicyResult result = new PolicyResult();
            result.setError("Failed to register Kafka: " + e.getMessage());
            return Response.serverError().entity(result).build();
        }
    }

    @POST
    @Path("/infrastructure")
    @Operation(summary = "Register infrastructure resource (SMTP, LDAP, OAuth, API, Certs)")
    public Response registerInfrastructure(ResourceSetup setup) {
        if (setup.getResourceType() == null || setup.getResourceType().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "resourceType is required (api, smtp, ldap, oauth, certs)")).build();
        }

        String branch = resourceBranch(setup, setup.getResourceType());
        if (branch == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "orgName, environment, product, and resourceName are required")).build();
        }

        try {
            String yaml = switch (setup.getResourceType()) {
                case "api" -> policyGenerator.generateApiResource(setup.getResourceName());
                case "smtp" -> policyGenerator.generateSmtpResource(setup.getResourceName());
                case "ldap" -> policyGenerator.generateLdapResource(setup.getResourceName());
                case "oauth" -> policyGenerator.generateOauthResource(setup.getResourceName());
                case "certs" -> policyGenerator.generateCertsResource(setup.getResourceName());
                default -> throw new IllegalArgumentException("Unknown resource type: " + setup.getResourceType());
            };

            String response = conjurClient.appendPolicy(branch, yaml);

            if (setup.getInitialSecrets() != null) {
                String varPrefix = branch + "/" + setup.getResourceName() + "/";
                for (Map.Entry<String, String> entry : setup.getInitialSecrets().entrySet()) {
                    conjurClient.setSecret(varPrefix + entry.getKey(), entry.getValue());
                }
            }

            PolicyResult result = new PolicyResult();
            result.setMessage(setup.getResourceType() + " resource '" + setup.getResourceName() + "' registered");
            result.setBranch(branch);
            result.setRawResponse(response);
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to register infrastructure resource", e);
            PolicyResult result = new PolicyResult();
            result.setError("Failed to register resource: " + e.getMessage());
            return Response.serverError().entity(result).build();
        }
    }

    @POST
    @Path("/variable")
    @Operation(summary = "Create custom variables at a branch")
    public Response createVariables(Map<String, Object> body) {
        String orgName = (String) body.get("orgName");
        String environment = (String) body.get("environment");
        String product = (String) body.get("product");
        String branch = (String) body.get("branch");
        @SuppressWarnings("unchecked")
        List<String> variables = (List<String>) body.get("variables");

        if (variables == null || variables.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "variables list is required")).build();
        }

        // Use explicit branch if provided, otherwise compute from org/env/product
        String targetBranch;
        if (branch != null && !branch.isBlank()) {
            targetBranch = branch;
        } else if (orgName != null && environment != null && product != null) {
            String resourceType = (String) body.getOrDefault("resourceType", "");
            targetBranch = orgName + "/environments/" + environment + "/products/" + product;
            if (!resourceType.isBlank()) {
                targetBranch += "/resources/" + resourceType;
            }
        } else {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Either 'branch' or 'orgName/environment/product' are required")).build();
        }

        try {
            String yaml = policyGenerator.generateCustomVariables(variables);
            String response = conjurClient.appendPolicy(targetBranch, yaml);
            LOG.info("Created " + variables.size() + " variables at " + targetBranch);

            return Response.ok(Map.of(
                    "message", variables.size() + " variables created",
                    "branch", targetBranch,
                    "variables", variables,
                    "rawResponse", response
            )).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to create variables", e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to create variables: " + e.getMessage())).build();
        }
    }

    private String resourceBranch(ResourceSetup setup, String resourceType) {
        if (setup.getOrgName() == null || setup.getEnvironment() == null
                || setup.getProduct() == null || setup.getResourceName() == null) {
            return null;
        }
        return setup.getOrgName() + "/environments/" + setup.getEnvironment()
                + "/products/" + setup.getProduct()
                + "/resources/" + resourceType;
    }
}
