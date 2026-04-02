package com.example.conjur.admin.rest;

import com.example.conjur.admin.model.AccessGrant;
import com.example.conjur.admin.model.PolicyResult;
import com.example.conjur.admin.service.ConjurClient;
import com.example.conjur.admin.service.PolicyGenerator;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/access")
@RequestScoped
@Tag(name = "Access", description = "Access grants and delegation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AccessResource {

    private static final Logger LOG = Logger.getLogger(AccessResource.class.getName());

    @Inject
    private ConjurClient conjurClient;

    @Inject
    private PolicyGenerator policyGenerator;

    @POST
    @Path("/grant")
    @Operation(summary = "Grant access",
               description = "Grants app host access to DB, shared resources, or JWT authenticator")
    public Response grantAccess(AccessGrant grant) {
        if (grant.getTargetType() == null || grant.getTargetType().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "targetType is required (db, shared, jwt)")).build();
        }

        try {
            String yaml;
            String branch;

            switch (grant.getTargetType()) {
                case "db" -> {
                    yaml = policyGenerator.generateDbAccessGrant(
                            grant.getTargetResource(), grant.getAppType(), grant.getHostId());
                    branch = grant.getOrgName() + "/" + grant.getEnvironment()
                            + "/products/" + grant.getProduct();
                }
                case "shared" -> {
                    yaml = policyGenerator.generateSharedResourceGrant(
                            grant.getAppType(), grant.getHostId());
                    branch = grant.getOrgName() + "/" + grant.getEnvironment()
                            + "/products/" + grant.getProduct();
                }
                case "jwt" -> {
                    String fullHostPath = grant.getOrgName() + "/" + grant.getEnvironment()
                            + "/products/" + grant.getProduct()
                            + "/apps/" + grant.getAppType() + "/" + grant.getHostId();
                    yaml = policyGenerator.generateJwtEnrollment(
                            grant.getTargetResource(), fullHostPath);
                    branch = "root";
                }
                default -> {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Unknown targetType: " + grant.getTargetType()
                                    + ". Use 'db', 'shared', or 'jwt'")).build();
                }
            }

            String response = conjurClient.appendPolicy(branch, yaml);
            LOG.info("Granted " + grant.getTargetType() + " access for "
                    + grant.getHostId() + " at " + branch);

            PolicyResult result = new PolicyResult();
            result.setMessage(grant.getTargetType() + " access granted for " + grant.getHostId());
            result.setBranch(branch);
            result.setRawResponse(response);
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to grant access", e);
            PolicyResult result = new PolicyResult();
            result.setError("Failed to grant access: " + e.getMessage());
            return Response.serverError().entity(result).build();
        }
    }
}
