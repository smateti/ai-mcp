package com.example.conjur.admin.rest;

import com.example.conjur.admin.model.JwtAuthenticatorSetup;
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

@Path("/authenticators")
@RequestScoped
@Tag(name = "Authenticators", description = "JWT authenticator management")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthenticatorResource {

    private static final Logger LOG = Logger.getLogger(AuthenticatorResource.class.getName());

    @Inject
    private ConjurClient conjurClient;

    @Inject
    private PolicyGenerator policyGenerator;

    @POST
    @Path("/jwt/setup")
    @Operation(summary = "Setup JWT authenticator",
               description = "Creates JWT authenticator policy and sets configuration variables")
    public Response setupJwtAuthenticator(JwtAuthenticatorSetup setup) {
        if (setup.getServiceId() == null || setup.getServiceId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "serviceId is required (e.g., 'kubernetes' or 'openshift')")).build();
        }

        String sid = setup.getServiceId();
        List<String> steps = new ArrayList<>();

        try {
            // Step 1: Create authn-jwt branch under conjur
            String branchYaml = policyGenerator.generateAuthnJwtBranch();
            try {
                conjurClient.appendPolicy("conjur", branchYaml);
                steps.add("Created conjur/authn-jwt branch");
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("409")) {
                    steps.add("conjur/authn-jwt branch already exists (OK)");
                } else {
                    steps.add("conjur/authn-jwt branch: " + e.getMessage() + " (continuing)");
                }
            }

            // Step 2: Create authenticator policy
            String authYaml = policyGenerator.generateJwtAuthenticator(sid);
            String response = conjurClient.appendPolicy("conjur/authn-jwt", authYaml);
            steps.add("Created authn-jwt/" + sid + " policy with webservice and authenticatable group");

            // Step 3: Set variable values
            // IMPORTANT: Every variable in the policy MUST have a value set.
            // Conjur throws CONJ00037E for variables without values.
            String varPrefix = "conjur/authn-jwt/" + sid + "/";
            List<String> varsSet = new ArrayList<>();

            String jwksUri = (setup.getJwksUri() != null && !setup.getJwksUri().isBlank())
                    ? setup.getJwksUri() : "https://kubernetes.default.svc/openid/v1/jwks";
            conjurClient.setSecret(varPrefix + "jwks-uri", jwksUri);
            varsSet.add("jwks-uri");

            String tokenAppProp = (setup.getTokenAppProperty() != null && !setup.getTokenAppProperty().isBlank())
                    ? setup.getTokenAppProperty() : "sub";
            conjurClient.setSecret(varPrefix + "token-app-property", tokenAppProp);
            varsSet.add("token-app-property");

            String issuer = (setup.getIssuer() != null && !setup.getIssuer().isBlank())
                    ? setup.getIssuer() : "https://kubernetes.default.svc.cluster.local";
            conjurClient.setSecret(varPrefix + "issuer", issuer);
            varsSet.add("issuer");

            String identityPath = (setup.getIdentityPath() != null && !setup.getIdentityPath().isBlank())
                    ? setup.getIdentityPath() : "apps";
            conjurClient.setSecret(varPrefix + "identity-path", identityPath);
            varsSet.add("identity-path");

            String audience = (setup.getAudience() != null && !setup.getAudience().isBlank())
                    ? setup.getAudience() : "https://kubernetes.default.svc.cluster.local";
            conjurClient.setSecret(varPrefix + "audience", audience);
            varsSet.add("audience");

            steps.add("Set variables: " + varsSet);

            LOG.info("JWT authenticator '" + sid + "' setup complete");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "JWT authenticator '" + sid + "' created successfully");
            result.put("serviceId", sid);
            result.put("steps", steps);
            result.put("variablesSet", varsSet);
            result.put("rawResponse", response);
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to setup JWT authenticator: " + sid, e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", "Failed to setup JWT authenticator: " + e.getMessage());
            result.put("completedSteps", steps);
            return Response.serverError().entity(result).build();
        }
    }

    @GET
    @Operation(summary = "List authenticators",
               description = "Lists configured JWT authenticators from Conjur resources")
    public Response listAuthenticators() {
        try {
            String json = conjurClient.listResources("webservice", "authn-jwt");
            return Response.ok(json).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to list authenticators", e);
            return Response.serverError()
                    .entity(Map.of("error", "Failed to list authenticators: " + e.getMessage())).build();
        }
    }

    @POST
    @Path("/jwt/{serviceId}/enroll")
    @Operation(summary = "Enroll hosts in JWT authenticator")
    public Response enrollHosts(@PathParam("serviceId") String serviceId, List<String> hostPaths) {
        if (hostPaths == null || hostPaths.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Provide a list of host paths to enroll")).build();
        }

        List<String> enrolled = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String hostPath : hostPaths) {
            try {
                String yaml = policyGenerator.generateJwtEnrollment(serviceId, hostPath);
                conjurClient.appendPolicy("root", yaml);
                enrolled.add(hostPath);
            } catch (Exception e) {
                errors.add(hostPath + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serviceId", serviceId);
        result.put("enrolled", enrolled);
        result.put("errors", errors);
        return Response.ok(result).build();
    }
}
