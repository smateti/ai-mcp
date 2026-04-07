package com.example.conjur.admin.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Generates Conjur policy YAML from typed inputs.
 * Each method produces YAML matching the templates in kubernetes/conjur/policies/.
 */
@ApplicationScoped
public class PolicyGenerator {

    // ========== Root Policy ==========

    public String generateRootPolicy(String orgName, List<String> extraBranches) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("""
                - !policy
                  id: conjur

                - !policy
                  id: %s
                """.formatted(orgName));

        if (extraBranches != null) {
            for (String branch : extraBranches) {
                yaml.append("""

                - !policy
                  id: %s
                """.formatted(branch));
            }
        }
        return yaml.toString();
    }

    // ========== JWT Authenticator ==========

    public String generateAuthnJwtBranch() {
        return """
                - !policy
                  id: authn-jwt
                """;
    }

    /**
     * Generates JWT authenticator policy. Only creates variables that will be used.
     * IMPORTANT: Every variable created MUST have a value set, otherwise Conjur
     * throws CONJ00037E. Optional variables (identity-path, audience) are added
     * separately via generateJwtOptionalVariable() when values are provided.
     */
    public String generateJwtAuthenticator(String serviceId) {
        return """
                - !policy
                  id: %s
                  body:
                    - !variable jwks-uri
                    - !variable token-app-property
                    - !variable issuer
                    - !variable identity-path
                    - !variable audience

                    - !webservice

                    - !group
                      id: authenticatable
                      annotations:
                        description: "Hosts permitted to use authn-jwt/%s"

                    - !permit
                      role: !group authenticatable
                      privilege: [ read, authenticate ]
                      resource: !webservice
                """.formatted(serviceId, serviceId);
    }

    // ========== Environments ==========

    public String generateEnvironmentsBranch() {
        return """
                - !policy
                  id: environments
                """;
    }

    public String generateEnvironments(List<String> environments) {
        StringBuilder yaml = new StringBuilder();
        for (String env : environments) {
            yaml.append("""
                    - !policy
                      id: %s
                      annotations:
                        description: "%s environment"

                    """.formatted(env, capitalize(env)));
        }
        return yaml.toString();
    }

    public String generateEnvironmentProducts(String productName) {
        return """
                - !policy
                  id: products
                  body:
                    - !policy
                      id: %s
                """.formatted(productName);
    }

    // ========== Product Structure ==========

    public String generateProductRegistration(String productName) {
        return """
                - !policy
                  id: %s
                """.formatted(productName);
    }

    public String generateProductStructure(List<String> appTypes, List<String> resourceTypes) {
        StringBuilder yaml = new StringBuilder();

        // Apps section
        yaml.append("- !policy\n  id: apps\n  body:\n");
        for (String type : appTypes) {
            yaml.append("    - !policy\n      id: ").append(type).append("\n");
        }

        // Resources section
        yaml.append("\n- !policy\n  id: resources\n  body:\n");
        for (String type : resourceTypes) {
            yaml.append("    - !policy\n      id: ").append(type).append("\n");
        }

        // Resources-readers layer
        yaml.append("\n- !layer\n  id: resources-readers\n");

        return yaml.toString();
    }

    public String generateSingleAppType(String appType) {
        return "- !policy\n  id: %s\n".formatted(appType);
    }

    // ========== Database Resource ==========

    public String generateDatabaseResource(String dbName) {
        return """
                - !policy
                  id: %s
                  body:
                    - !variable host-name
                    - !variable username
                    - !variable password
                    - !variable port
                    - !variable database-name

                    - !group readers

                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable host-name
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable username
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable password
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable port
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable database-name
                """.formatted(dbName);
    }

    // ========== Kafka Resource ==========

    public String generateKafkaResource(String clusterName) {
        return """
                - !policy
                  id: %s
                  body:
                    - !variable bootstrap-servers
                    - !variable sasl-username
                    - !variable sasl-password
                    - !variable sasl-mechanism
                    - !variable security-protocol
                    - !variable keystore-password
                    - !variable truststore-password
                    - !variable schema-registry-url
                    - !variable schema-registry-key
                    - !variable schema-registry-secret

                    - !group readers

                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable bootstrap-servers
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable sasl-username
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable sasl-password
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable sasl-mechanism
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable security-protocol
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable keystore-password
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable truststore-password
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable schema-registry-url
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable schema-registry-key
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable schema-registry-secret
                """.formatted(clusterName);
    }

    // ========== Infrastructure Resources ==========

    public String generateApiResource(String name) {
        return """
                - !policy
                  id: %s
                  body:
                    - !variable key
                    - !variable secret
                    - !variable webhook-secret

                    - !group readers

                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable key
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable secret
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable webhook-secret
                """.formatted(name);
    }

    public String generateSmtpResource(String name) {
        return """
                - !policy
                  id: %s
                  body:
                    - !variable host
                    - !variable port
                    - !variable username
                    - !variable password
                    - !variable from-address

                    - !group readers

                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable host
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable port
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable username
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable password
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable from-address
                """.formatted(name);
    }

    public String generateLdapResource(String name) {
        return """
                - !policy
                  id: %s
                  body:
                    - !variable url
                    - !variable base-dn
                    - !variable bind-dn
                    - !variable bind-password

                    - !group readers

                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable url
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable base-dn
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable bind-dn
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable bind-password
                """.formatted(name);
    }

    public String generateOauthResource(String name) {
        return """
                - !policy
                  id: %s
                  body:
                    - !variable tenant-id
                    - !variable client-id
                    - !variable client-secret
                    - !variable token-endpoint

                    - !group readers

                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable tenant-id
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable client-id
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable client-secret
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable token-endpoint
                """.formatted(name);
    }

    public String generateCertsResource(String name) {
        return """
                - !policy
                  id: %s
                  body:
                    - !variable keystore-password
                    - !variable truststore-password
                    - !variable keystore-type

                    - !group readers

                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable keystore-password
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable truststore-password
                    - !permit
                      role: !group readers
                      privilege: [ read, execute ]
                      resource: !variable keystore-type
                """.formatted(name);
    }

    // ========== Custom Variables ==========

    public String generateCustomVariables(List<String> variableNames) {
        StringBuilder yaml = new StringBuilder();
        for (String name : variableNames) {
            yaml.append("- !variable ").append(name).append("\n");
        }
        return yaml.toString();
    }

    // ========== App Hosts ==========

    /**
     * Generate host policy with JWT annotations.
     * For K8s/OpenShift hosts, uses the "sub" annotation matching the K8s JWT sub claim
     * format: system:serviceaccount:{namespace}:{serviceAccountName}.
     * IMPORTANT: Conjur requires at least one authn-jwt annotation on the host (CONJ00099E).
     * The "sub" claim is a top-level JWT claim that works reliably; nested claims like
     * kubernetes.io/namespace do NOT work as annotations.
     */
    public String generateAppHost(String hostId, String appType, String namespace,
                                   String serviceAccount, String jwtServiceId) {
        if ("openshift".equals(appType) || "kubernetes".equals(appType)) {
            String svcId = jwtServiceId != null ? jwtServiceId : appType;
            String subClaim = "system:serviceaccount:" + namespace + ":" + serviceAccount;
            return """
                    - !host
                      id: %s
                      annotations:
                        authn-jwt/%s/sub: "%s"
                    """.formatted(hostId, svcId, subClaim);
        } else {
            return """
                    - !host
                      id: %s
                      annotations:
                        description: "%s application"
                    """.formatted(hostId, appType);
        }
    }

    // ========== Delegation / Access Grants ==========

    public String generateDbAccessGrant(String dbName, String appType, String appHost) {
        return generateResourceAccessGrant("dbs", dbName, appType, appHost);
    }

    public String generateResourceAccessGrant(String resourceType, String resourceName,
                                               String appType, String appHost) {
        return """
                - !grant
                  role: !group resources/%s/%s/readers
                  member: !host apps/%s/%s
                """.formatted(resourceType, resourceName, appType, appHost);
    }

    public String generateSharedResourceGrant(String appType, String appHost) {
        return """
                - !grant
                  role: !layer resources-readers
                  member: !host apps/%s/%s
                """.formatted(appType, appHost);
    }

    public String generateSharedResourcePermit(String resourcePath) {
        return """
                - !permit
                  role: !layer resources-readers
                  privilege: [ read, execute ]
                  resource: !variable %s
                """.formatted(resourcePath);
    }

    // ========== JWT Enrollment ==========

    public String generateJwtEnrollment(String serviceId, String fullHostPath) {
        return """
                - !grant
                  role: !group conjur/authn-jwt/%s/authenticatable
                  member: !host %s
                """.formatted(serviceId, fullHostPath);
    }

    // ========== Helpers ==========

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
