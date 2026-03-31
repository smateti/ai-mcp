package com.example.conjur.lib;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * Handles Conjur authentication and secret retrieval using java.net.http.HttpClient.
 * No external dependencies — uses only JDK standard library.
 *
 * <p>Supports two authentication methods (auto-detected from env vars):
 * <ul>
 *   <li><b>API Key auth</b> ({@code authn}): set CONJUR_AUTHN_LOGIN + CONJUR_AUTHN_API_KEY</li>
 *   <li><b>JWT auth</b> ({@code authn-jwt/{service-id}}): set CONJUR_AUTHN_JWT_SERVICE_ID + CONJUR_JWT_TOKEN_PATH</li>
 * </ul>
 *
 * <p>If both are configured, JWT auth takes priority.
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>CONJUR_APPLIANCE_URL — Conjur server URL (default: http://conjur-server.conjur-system.svc.cluster.local)</li>
 *   <li>CONJUR_ACCOUNT — Conjur account name (default: myConjurAccount)</li>
 *   <li>CONJUR_AUTHN_LOGIN — authentication identity for API key auth</li>
 *   <li>CONJUR_AUTHN_API_KEY — API key for authentication</li>
 *   <li>CONJUR_AUTHN_JWT_SERVICE_ID — JWT authenticator service ID (e.g. "openshift")</li>
 *   <li>CONJUR_JWT_TOKEN_PATH — path to JWT token file (default: /var/run/secrets/kubernetes.io/serviceaccount/token)</li>
 *   <li>CONJUR_TOKEN_TTL — token cache TTL in seconds (default: 360)</li>
 * </ul>
 */
public class ConjurAuthenticator {

    private static final Logger LOG = Logger.getLogger(ConjurAuthenticator.class.getName());
    private static final String DEFAULT_JWT_TOKEN_PATH =
            "/var/run/secrets/kubernetes.io/serviceaccount/token";

    private final String applianceUrl;
    private final String account;
    private final String authnLogin;
    private final String apiKey;
    private final String jwtServiceId;
    private final String jwtTokenPath;
    private final int tokenTtlSeconds;
    private final boolean useJwt;

    private final HttpClient httpClient;
    private String cachedToken;
    private Instant tokenTimestamp;

    public ConjurAuthenticator() {
        this.applianceUrl = env("CONJUR_APPLIANCE_URL",
                "http://conjur-server.conjur-system.svc.cluster.local");
        this.account = env("CONJUR_ACCOUNT", "myConjurAccount");
        this.authnLogin = env("CONJUR_AUTHN_LOGIN", "");
        this.apiKey = env("CONJUR_AUTHN_API_KEY", "");
        this.jwtServiceId = env("CONJUR_AUTHN_JWT_SERVICE_ID", "");
        this.jwtTokenPath = env("CONJUR_JWT_TOKEN_PATH", DEFAULT_JWT_TOKEN_PATH);
        this.tokenTtlSeconds = Integer.parseInt(env("CONJUR_TOKEN_TTL", "360"));

        // JWT takes priority if configured
        this.useJwt = jwtServiceId != null && !jwtServiceId.isBlank();
        if (this.useJwt && isApiKeyConfigured()) {
            LOG.warning("Both JWT and API key auth configured — using JWT auth (authn-jwt/"
                    + jwtServiceId + ")");
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isEnabled() {
        return useJwt || isApiKeyConfigured();
    }

    private boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"not-set".equals(apiKey)
                && authnLogin != null && !authnLogin.isBlank();
    }

    public String getAuthMethod() {
        if (useJwt) return "authn-jwt/" + jwtServiceId;
        if (isApiKeyConfigured()) return "authn";
        return "none";
    }

    public synchronized void authenticate() {
        if (useJwt) {
            authenticateJwt();
        } else {
            authenticateApiKey();
        }
    }

    private void authenticateApiKey() {
        String url = applianceUrl + "/authn/" + account + "/"
                + encode(authnLogin) + "/authenticate";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(apiKey))
                .header("Content-Type", "text/plain")
                .header("Accept", "*/*")
                .timeout(Duration.ofSeconds(10))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                cachedToken = Base64.getEncoder().encodeToString(
                        response.body().getBytes(StandardCharsets.UTF_8));
                tokenTimestamp = Instant.now();
                LOG.info("Authenticated with Conjur via API key as '" + authnLogin + "'");
            } else {
                throw new ConjurException("Conjur API key auth failed: HTTP " + response.statusCode()
                        + " - " + response.body());
            }
        } catch (ConjurException e) {
            throw e;
        } catch (Exception e) {
            cachedToken = null;
            tokenTimestamp = null;
            throw new ConjurException("Conjur API key auth error", e);
        }
    }

    private void authenticateJwt() {
        String jwtToken;
        try {
            jwtToken = Files.readString(Path.of(jwtTokenPath)).trim();
        } catch (Exception e) {
            throw new ConjurException("Failed to read JWT token from " + jwtTokenPath, e);
        }

        // POST /authn-jwt/{service-id}/{account}/authenticate with jwt=<token> in body
        String url = applianceUrl + "/authn-jwt/" + encode(jwtServiceId)
                + "/" + account + "/authenticate";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString("jwt=" + jwtToken))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "*/*")
                .timeout(Duration.ofSeconds(10))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                cachedToken = Base64.getEncoder().encodeToString(
                        response.body().getBytes(StandardCharsets.UTF_8));
                tokenTimestamp = Instant.now();
                LOG.info("Authenticated with Conjur via JWT (authn-jwt/" + jwtServiceId + ")");
            } else {
                throw new ConjurException("Conjur JWT auth failed: HTTP " + response.statusCode()
                        + " - " + response.body());
            }
        } catch (ConjurException e) {
            throw e;
        } catch (Exception e) {
            cachedToken = null;
            tokenTimestamp = null;
            throw new ConjurException("Conjur JWT auth error", e);
        }
    }

    /**
     * Retrieves a secret value from Conjur by variable ID.
     *
     * @param variableId the Conjur variable path, e.g. "db/empdb-uid"
     * @return the secret value, or null if not found (HTTP 404)
     */
    public String getSecret(String variableId) {
        ensureAuthenticated();
        String url = applianceUrl + "/secrets/" + account + "/variable/" + encode(variableId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Authorization", authHeader())
                .timeout(Duration.ofSeconds(10))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            } else if (response.statusCode() == 404) {
                return null;
            } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                // Re-authenticate and retry once
                authenticate();
                HttpRequest retry = HttpRequest.newBuilder()
                        .uri(URI.create(url)).GET()
                        .header("Authorization", authHeader())
                        .timeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> r2 = httpClient.send(retry,
                        HttpResponse.BodyHandlers.ofString());
                if (r2.statusCode() == 200) return r2.body();
                if (r2.statusCode() == 404) return null;
                throw new ConjurException("GET secret failed after retry: HTTP " + r2.statusCode());
            } else {
                throw new ConjurException("GET secret failed: HTTP " + response.statusCode()
                        + " - " + response.body());
            }
        } catch (ConjurException e) {
            throw e;
        } catch (Exception e) {
            throw new ConjurException("Error reading secret: " + variableId, e);
        }
    }

    public boolean isReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(applianceUrl + "/"))
                    .GET().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    public String getApplianceUrl() { return applianceUrl; }
    public String getAccount() { return account; }
    public String getAuthnLogin() { return authnLogin; }

    private void ensureAuthenticated() {
        if (!isEnabled()) {
            throw new ConjurException("Conjur not configured: set CONJUR_AUTHN_JWT_SERVICE_ID (JWT) "
                    + "or CONJUR_AUTHN_LOGIN + CONJUR_AUTHN_API_KEY (API key)");
        }
        if (cachedToken == null || isTokenExpired()) {
            authenticate();
        }
    }

    private boolean isTokenExpired() {
        return tokenTimestamp == null
                || Instant.now().isAfter(tokenTimestamp.plusSeconds(tokenTtlSeconds));
    }

    private String authHeader() {
        return "Token token=\"" + cachedToken + "\"";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String env(String name, String defaultValue) {
        String val = System.getenv(name);
        if (val != null && !val.isBlank()) return val;
        String sysProp = System.getProperty(name);
        if (sysProp != null && !sysProp.isBlank()) return sysProp;
        return defaultValue;
    }
}
