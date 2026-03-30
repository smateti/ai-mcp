package com.example.conjur.admin.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ConjurClient {

    private static final Logger LOG = Logger.getLogger(ConjurClient.class.getName());

    @Inject
    @ConfigProperty(name = "conjur.appliance.url",
                    defaultValue = "http://conjur-server.conjur-system.svc.cluster.local")
    private String applianceUrl;

    @Inject
    @ConfigProperty(name = "conjur.account", defaultValue = "myConjurAccount")
    private String account;

    @Inject
    @ConfigProperty(name = "conjur.authn.login", defaultValue = "admin")
    private String authnLogin;

    @Inject
    @ConfigProperty(name = "conjur.admin.api.key", defaultValue = "not-set")
    private String adminApiKey;

    @Inject
    @ConfigProperty(name = "conjur.token.ttl.seconds", defaultValue = "360")
    private int tokenTtlSeconds;

    @Inject
    @ConfigProperty(name = "conjur.known.secrets", defaultValue = "db/empdb-uid,db/empdb-pwd")
    private String knownSecretsConfig;

    @Inject
    @ConfigProperty(name = "conjur.known.databases", defaultValue = "empdb")
    private String knownDatabasesConfig;

    @Inject
    @ConfigProperty(name = "conjur.known.apps", defaultValue = "liberty-app")
    private String knownAppsConfig;

    private HttpClient httpClient;
    private String cachedToken;
    private Instant tokenTimestamp;
    private boolean enabled;

    @PostConstruct
    public void init() {
        enabled = adminApiKey != null
               && !adminApiKey.isBlank()
               && !"not-set".equals(adminApiKey);

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        if (enabled) {
            LOG.info("ConjurClient initialized for account '" + account + "' at " + applianceUrl);
        } else {
            LOG.warning("ConjurClient disabled: CONJUR_ADMIN_API_KEY not set");
        }
    }

    // ========== Authentication ==========

    public synchronized void authenticate() {
        String url = applianceUrl + "/authn/" + account + "/" + encode(authnLogin) + "/authenticate";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(adminApiKey))
                .header("Content-Type", "text/plain")
                .header("Accept", "*/*")
                .timeout(Duration.ofSeconds(10))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                cachedToken = Base64.getEncoder().encodeToString(
                        response.body().getBytes(StandardCharsets.UTF_8));
                tokenTimestamp = Instant.now();
                LOG.info("Authenticated with Conjur as '" + authnLogin + "'");
            } else {
                throw new RuntimeException("Conjur auth failed: HTTP " + response.statusCode()
                        + " - " + response.body());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            cachedToken = null;
            tokenTimestamp = null;
            throw new RuntimeException("Conjur auth error", e);
        }
    }

    private void ensureAuthenticated() {
        if (!enabled) throw new RuntimeException("ConjurClient is not enabled (API key not set)");
        if (cachedToken == null || isTokenExpired()) {
            authenticate();
        }
    }

    private boolean isTokenExpired() {
        return tokenTimestamp == null || Instant.now().isAfter(tokenTimestamp.plusSeconds(tokenTtlSeconds));
    }

    private String authHeader() {
        return "Token token=\"" + cachedToken + "\"";
    }

    // ========== Secret Operations ==========

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
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            } else if (response.statusCode() == 404) {
                return null;
            } else if (response.statusCode() == 401 || response.statusCode() == 403) {
                authenticate();
                HttpRequest retry = HttpRequest.newBuilder()
                        .uri(URI.create(url)).GET()
                        .header("Authorization", authHeader())
                        .timeout(Duration.ofSeconds(10)).build();
                HttpResponse<String> r2 = httpClient.send(retry, HttpResponse.BodyHandlers.ofString());
                if (r2.statusCode() == 200) return r2.body();
                if (r2.statusCode() == 404) return null;
                throw new RuntimeException("Conjur GET secret failed after retry: HTTP " + r2.statusCode());
            } else {
                throw new RuntimeException("Conjur GET secret failed: HTTP " + response.statusCode());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error reading secret: " + variableId, e);
        }
    }

    public void setSecret(String variableId, String value) {
        ensureAuthenticated();
        String url = applianceUrl + "/secrets/" + account + "/variable/" + encode(variableId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(value))
                .header("Authorization", authHeader())
                .header("Content-Type", "text/plain")
                .timeout(Duration.ofSeconds(10))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201 && response.statusCode() != 200) {
                throw new RuntimeException("Conjur POST secret failed: HTTP " + response.statusCode()
                        + " - " + response.body());
            }
            LOG.info("Set secret: " + variableId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error writing secret: " + variableId, e);
        }
    }

    // ========== Policy Operations ==========

    /**
     * PUT /policies/{account}/policy/{branch} — replace policy.
     */
    public String loadPolicy(String branch, String yamlBody) {
        return policyRequest("PUT", branch, yamlBody);
    }

    /**
     * POST /policies/{account}/policy/{branch} — append to policy.
     */
    public String appendPolicy(String branch, String yamlBody) {
        return policyRequest("POST", branch, yamlBody);
    }

    private String policyRequest(String method, String branch, String yamlBody) {
        ensureAuthenticated();
        String url = applianceUrl + "/policies/" + account + "/policy/" + encode(branch);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader())
                .header("Content-Type", "application/x-yaml")
                .timeout(Duration.ofSeconds(15));

        if ("PUT".equals(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(yamlBody));
        } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(yamlBody));
        }

        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            LOG.info("Policy " + method + " to '" + branch + "': HTTP " + response.statusCode());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("Conjur policy " + method + " failed: HTTP "
                        + response.statusCode() + " - " + response.body());
            }
            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error loading policy to branch: " + branch, e);
        }
    }

    // ========== Info / Health ==========

    public Map<String, Object> getInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("conjurUrl", applianceUrl);
        info.put("account", account);
        info.put("authnLogin", authnLogin);
        info.put("enabled", enabled);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(applianceUrl + "/"))
                    .GET().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            info.put("reachable", response.statusCode() < 500);
            info.put("httpStatus", response.statusCode());
        } catch (Exception e) {
            info.put("reachable", false);
            info.put("error", e.getMessage());
        }
        info.put("authenticated", cachedToken != null && !isTokenExpired());
        return info;
    }

    public boolean isReachable() {
        if (!enabled) return false;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(applianceUrl + "/"))
                    .GET().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getApplianceUrl() { return applianceUrl; }
    public String getAccount() { return account; }

    public List<String> getKnownSecrets() {
        return parseList(knownSecretsConfig);
    }

    public List<String> getKnownDatabases() {
        return parseList(knownDatabasesConfig);
    }

    public List<String> getKnownApps() {
        return parseList(knownAppsConfig);
    }

    public void addKnownSecret(String variableId) {
        if (!getKnownSecrets().contains(variableId)) {
            knownSecretsConfig = knownSecretsConfig.isBlank() ? variableId : knownSecretsConfig + "," + variableId;
        }
    }

    public void addKnownDatabase(String dbName) {
        if (!getKnownDatabases().contains(dbName)) {
            knownDatabasesConfig = knownDatabasesConfig.isBlank() ? dbName : knownDatabasesConfig + "," + dbName;
            addKnownSecret("db/" + dbName + "-uid");
            addKnownSecret("db/" + dbName + "-pwd");
        }
    }

    public void addKnownApp(String appName) {
        if (!getKnownApps().contains(appName)) {
            knownAppsConfig = knownAppsConfig.isBlank() ? appName : knownAppsConfig + "," + appName;
        }
    }

    private static List<String> parseList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
