package com.example.conjur.service;

import com.cyberark.conjur.api.Conjur;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CDI service wrapping the CyberArk Conjur Java API.
 * Reads and writes secrets from Conjur.
 */
@ApplicationScoped
public class ConjurService {

    private static final Logger LOG = Logger.getLogger(ConjurService.class.getName());

    @Inject
    @ConfigProperty(name = "conjur.appliance.url", defaultValue = "http://localhost:8080")
    private String applianceUrl;

    @Inject
    @ConfigProperty(name = "conjur.account", defaultValue = "myConjurAccount")
    private String account;

    @Inject
    @ConfigProperty(name = "conjur.authn.login", defaultValue = "admin")
    private String authnLogin;

    @Inject
    @ConfigProperty(name = "conjur.authn.api.key")
    private Optional<String> authnApiKey;

    private Conjur conjur;
    private boolean conjurEnabled;

    @PostConstruct
    public void init() {
        String apiKey = authnApiKey.orElse("");
        if (apiKey.isBlank() || "not-set".equals(apiKey)) {
            LOG.warning("Conjur API key not configured — Conjur integration disabled.");
            conjurEnabled = false;
            return;
        }
        try {
            System.setProperty("CONJUR_APPLIANCE_URL", applianceUrl);
            System.setProperty("CONJUR_ACCOUNT", account);
            System.setProperty("CONJUR_AUTHN_LOGIN", authnLogin);
            System.setProperty("CONJUR_AUTHN_API_KEY", apiKey);

            conjur = new Conjur();
            conjurEnabled = true;
            LOG.info("Conjur client initialized for account '" + account + "' at " + applianceUrl);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to initialize Conjur client", e);
            conjurEnabled = false;
        }
    }

    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 10, delayUnit = ChronoUnit.SECONDS)
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    public String retrieveSecret(String variableId) {
        if (!conjurEnabled) {
            throw new RuntimeException("Conjur is not enabled");
        }
        try {
            String secret = conjur.variables().retrieveSecret(variableId);
            LOG.fine("Successfully retrieved secret: " + variableId);
            return secret;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to retrieve secret: " + variableId, e);
            throw new RuntimeException("Conjur read failed for variable: " + variableId, e);
        }
    }

    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 5, unit = ChronoUnit.SECONDS)
    public void addSecret(String variableId, String value) {
        if (!conjurEnabled) {
            throw new RuntimeException("Conjur is not enabled");
        }
        try {
            conjur.variables().addSecret(variableId, value);
            LOG.info("Successfully wrote secret: " + variableId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to write secret: " + variableId, e);
            throw new RuntimeException("Conjur write failed for variable: " + variableId, e);
        }
    }

    public Optional<String> getSecret(String variableId) {
        try {
            return Optional.ofNullable(retrieveSecret(variableId));
        } catch (Exception e) {
            LOG.fine("Secret not found or error: " + variableId);
            return Optional.empty();
        }
    }

    public boolean isConjurEnabled() {
        return conjurEnabled;
    }

    public boolean isConjurReachable() {
        if (!conjurEnabled) {
            return false;
        }
        try {
            conjur.variables().retrieveSecret("db/url");
            return true;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            return msg.contains("403") || msg.contains("404") || msg.contains("401");
        }
    }
}
