package com.example.conjur.config;

import com.cyberark.conjur.api.Conjur;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MicroProfile Config source that fetches configuration values from CyberArk Conjur.
 * Secrets stored in Conjur are exposed as MicroProfile Config properties
 * with the prefix "conjur.secret.".
 */
public class ConjurConfigSource implements ConfigSource {

    private static final Logger LOG = Logger.getLogger(ConjurConfigSource.class.getName());
    private static final String PREFIX = "conjur.secret.";
    private static final int ORDINAL = 500;

    private static final String[] VARIABLE_IDS = {
            "db/username",
            "db/password",
            "db/url",
            "myapp/api-key",
            "myapp/app-name"
    };

    private final Map<String, String> secrets = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    public ConjurConfigSource() {
        loadSecrets();
    }

    private void loadSecrets() {
        String applianceUrl = resolveProperty("CONJUR_APPLIANCE_URL", "conjur.appliance.url", "http://localhost:8080");
        String account = resolveProperty("CONJUR_ACCOUNT", "conjur.account", "myConjurAccount");
        String login = resolveProperty("CONJUR_AUTHN_LOGIN", "conjur.authn.login", "admin");
        String apiKey = resolveProperty("CONJUR_AUTHN_API_KEY", "conjur.authn.api.key", "");

        if (apiKey.isBlank() || "not-set".equals(apiKey)) {
            LOG.warning("CONJUR_AUTHN_API_KEY not set. Conjur ConfigSource will not load secrets.");
            return;
        }

        try {
            System.setProperty("CONJUR_APPLIANCE_URL", applianceUrl);
            System.setProperty("CONJUR_ACCOUNT", account);
            System.setProperty("CONJUR_AUTHN_LOGIN", login);
            System.setProperty("CONJUR_AUTHN_API_KEY", apiKey);

            Conjur conjur = new Conjur();

            for (String variableId : VARIABLE_IDS) {
                try {
                    String value = conjur.variables().retrieveSecret(variableId);
                    String configKey = PREFIX + variableId.replace("/", ".");
                    secrets.put(configKey, value);
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Could not load secret: " + variableId, e);
                }
            }

            loaded = true;
            LOG.info("Loaded " + secrets.size() + " secrets from Conjur");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not connect to Conjur at " + applianceUrl +
                    ". Falling back to default config values.", e);
        }
    }

    private String resolveProperty(String envName, String sysPropName, String defaultValue) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) return envValue;

        String sysProp = System.getProperty(sysPropName);
        if (sysProp != null && !sysProp.isBlank()) return sysProp;

        return defaultValue;
    }

    @Override
    public int getOrdinal() { return ORDINAL; }

    @Override
    public Map<String, String> getProperties() { return Collections.unmodifiableMap(secrets); }

    @Override
    public Set<String> getPropertyNames() { return secrets.keySet(); }

    @Override
    public String getValue(String propertyName) { return secrets.get(propertyName); }

    @Override
    public String getName() { return "ConjurConfigSource"; }

    public boolean isLoaded() { return loaded; }

    public void refresh() {
        secrets.clear();
        loaded = false;
        loadSecrets();
    }
}
