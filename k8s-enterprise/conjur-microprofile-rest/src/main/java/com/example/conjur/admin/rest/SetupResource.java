package com.example.conjur.admin.rest;

import com.example.conjur.admin.model.*;
import com.example.conjur.admin.model.AppTypeSetup;
import com.example.conjur.admin.service.ConjurClient;
import com.example.conjur.admin.service.PolicyGenerator;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/setup")
@RequestScoped
@Tag(name = "Setup", description = "Initial Conjur organization setup")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SetupResource {

    private static final Logger LOG = Logger.getLogger(SetupResource.class.getName());

    @Inject
    private ConjurClient conjurClient;

    @Inject
    private PolicyGenerator policyGenerator;

    @POST
    @Path("/root")
    @Operation(summary = "Create root policy",
               description = "Creates the top-level root policy with conjur and organization branches")
    public Response createRootPolicy(RootPolicySetup setup) {
        if (setup.getOrgName() == null || setup.getOrgName().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "orgName is required")).build();
        }

        try {
            String yaml = policyGenerator.generateRootPolicy(setup.getOrgName(), setup.getExtraBranches());
            String response = conjurClient.loadPolicy("root", yaml);
            LOG.info("Created root policy for org '" + setup.getOrgName() + "'");

            PolicyResult result = new PolicyResult();
            result.setMessage("Root policy created with org '" + setup.getOrgName() + "'");
            result.setBranch("root");
            result.setRawResponse(response);
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to create root policy", e);
            PolicyResult result = new PolicyResult();
            result.setError("Failed to create root policy: " + e.getMessage());
            return Response.serverError().entity(result).build();
        }
    }

    @POST
    @Path("/environments")
    @Operation(summary = "Create environment branches",
               description = "Creates dev/qa/prod environment branches under the organization")
    public Response createEnvironments(EnvironmentSetup setup) {
        if (setup.getOrgName() == null || setup.getOrgName().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "orgName is required")).build();
        }
        if (setup.getEnvironments() == null || setup.getEnvironments().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "environments list is required")).build();
        }

        try {
            String yaml = policyGenerator.generateEnvironments(setup.getEnvironments());
            String response = conjurClient.appendPolicy(setup.getOrgName(), yaml);
            LOG.info("Created environments " + setup.getEnvironments() + " under " + setup.getOrgName());

            PolicyResult result = new PolicyResult();
            result.setMessage("Environments created: " + setup.getEnvironments());
            result.setBranch(setup.getOrgName());
            result.setRawResponse(response);
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to create environments", e);
            PolicyResult result = new PolicyResult();
            result.setError("Failed to create environments: " + e.getMessage());
            return Response.serverError().entity(result).build();
        }
    }

    @POST
    @Path("/product")
    @Operation(summary = "Create product structure",
               description = "Creates a full product with apps, resources, and access layer")
    public Response createProduct(ProductSetup setup) {
        if (setup.getOrgName() == null || setup.getProductName() == null || setup.getEnvironment() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "orgName, environment, and productName are required")).build();
        }

        List<String> appTypes = setup.getAppTypes() != null ? setup.getAppTypes()
                : List.of("openshift", "batch", "websphere");
        List<String> resourceTypes = setup.getResourceTypes() != null ? setup.getResourceTypes()
                : List.of("dbs", "kafka", "api", "smtp", "ldap", "oauth", "certs");

        String envBranch = setup.getOrgName() + "/" + setup.getEnvironment();
        String productsBranch = envBranch + "/products";

        try {
            // Step 1: Ensure products branch and register product under env
            String envYaml = policyGenerator.generateEnvironmentProducts(setup.getProductName());
            conjurClient.appendPolicy(envBranch, envYaml);

            // Step 2: Create product structure
            String structYaml = policyGenerator.generateProductStructure(appTypes, resourceTypes);
            String response = conjurClient.appendPolicy(
                    productsBranch + "/" + setup.getProductName(), structYaml);

            LOG.info("Created product '" + setup.getProductName() + "' under " + envBranch);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "Product '" + setup.getProductName() + "' created successfully");
            result.put("branch", productsBranch + "/" + setup.getProductName());
            result.put("appTypes", appTypes);
            result.put("resourceTypes", resourceTypes);
            result.put("rawResponse", response);
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to create product", e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to create product: " + e.getMessage())).build();
        }
    }

    @POST
    @Path("/apptype")
    @Operation(summary = "Add app type to existing product",
               description = "Appends a new app type branch under an existing product's apps policy")
    public Response addAppType(AppTypeSetup setup) {
        if (setup.getOrgName() == null || setup.getEnvironment() == null
                || setup.getProduct() == null || setup.getAppType() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "orgName, environment, product, and appType are required")).build();
        }

        String appsBranch = setup.getOrgName() + "/" + setup.getEnvironment()
                + "/products/" + setup.getProduct() + "/apps";

        try {
            String yaml = policyGenerator.generateSingleAppType(setup.getAppType());
            String response = conjurClient.appendPolicy(appsBranch, yaml);
            LOG.info("Added app type '" + setup.getAppType() + "' to " + appsBranch);

            PolicyResult result = new PolicyResult();
            result.setMessage("App type '" + setup.getAppType() + "' added successfully");
            result.setBranch(appsBranch);
            result.setRawResponse(response);
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to add app type", e);
            PolicyResult result = new PolicyResult();
            result.setError("Failed to add app type: " + e.getMessage());
            return Response.serverError().entity(result).build();
        }
    }
}
