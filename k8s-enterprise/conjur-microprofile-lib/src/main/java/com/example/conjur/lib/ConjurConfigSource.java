package com.example.conjur.lib;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MicroProfile ConfigSource that automatically fetches secrets from CyberArk Conjur
 * and maps them to standard MicroProfile config properties.
 *
 * <h3>Configuration</h3>
 * Secrets are discovered via the {@code CONJUR_SECRETS} environment variable:
 * <pre>
 * CONJUR_SECRETS=db.username=db/empdb-uid,db.password=db/empdb-pwd,smtp.pass=creds/smtp-pwd
 * </pre>
 * Format: {@code configKey=conjurVariablePath} pairs, comma-separated.
 *
 * <p>Each secret is fetched from Conjur at startup and mapped to the corresponding
 * MicroProfile config property name. System properties are also set so Liberty
 * server.xml variables (e.g., {@code ${db.username}}) resolve correctly.
 *
 * <h3>Required Environment Variables</h3>
 * <ul>
 *   <li>{@code CONJUR_APPLIANCE_URL} — Conjur server URL</li>
 *   <li>{@code CONJUR_ACCOUNT} — Conjur account name</li>
 *   <li>{@code CONJUR_AUTHN_LOGIN} — host identity (e.g., host/apps/myapp)</li>
 *   <li>{@code CONJUR_AUTHN_API_KEY} — API key for authentication</li>
 *   <li>{@code CONJUR_SECRETS} — secret mappings (configKey=conjurPath,...)</li>
 * </ul>
 *
 * <h3>Ordinal</h3>
 * This source has ordinal 500, which overrides microprofile-config.properties (100)
 * and environment variables (300) but can be overridden by system properties (400+).
 */
public class ConjurConfigSource implements ConfigSource {

    private static final Logger LOG = Logger.getLogger(ConjurConfigSource.class.getName());
    private static final int ORDINAL = 500;

    private final Map<String, String> secrets = new ConcurrentHashMap<>();
    private final Map<String, String> secretMappings = new LinkedHashMap<>();
    private volatile boolean loaded = false;
    private ConjurAuthenticator authenticator;

    public ConjurConfigSource() {
        parseSecretMappings();
        loadSecrets();
    }

    /**
     * Parse CONJUR_SECRETS env var into configKey → conjurVariablePath mappings.
     * Also supports legacy DB_CONJUR_UID_KEY / DB_CONJUR_PWD_KEY env vars
     * for backward compatibility with existing employee-app pattern.
     */
    private void parseSecretMappings() {
        // New convention: CONJUR_SECRETS=configKey=conjurPath,configKey2=conjurPath2
        String secretsEnv = System.getenv("CONJUR_SECRETS");
        if (secretsEnv != null && !secretsEnv.isBlank()) {
            for (String pair : secretsEnv.split(",")) {
                pair = pair.trim();
                int eq = pair.indexOf('=');
                if (eq > 0 && eq < pair.length() - 1) {
                    String configKey = pair.substring(0, eq).trim();
                    String conjurPath = pair.substring(eq + 1).trim();
                    secretMappings.put(configKey, conjurPath);
                }
            }
        }

        // Legacy backward compatibility: DB_CONJUR_UID_KEY and DB_CONJUR_PWD_KEY
        String uidKey = System.getenv("DB_CONJUR_UID_KEY");
        String pwdKey = System.getenv("DB_CONJUR_PWD_KEY");
        if (uidKey != null && !uidKey.isBlank() && !secretMappings.containsValue(uidKey)) {
            secretMappings.putIfAbsent("db.username", uidKey);
        }
        if (pwdKey != null && !pwdKey.isBlank() && !secretMappings.containsValue(pwdKey)) {
            secretMappings.putIfAbsent("db.password", pwdKey);
        }

        if (secretMappings.isEmpty()) {
            LOG.info("No CONJUR_SECRETS mappings found. Set CONJUR_SECRETS env var to enable.");
        } else {
            LOG.info("Conjur secret mappings: " + secretMappings.size() + " entries");
        }
    }

    private void loadSecrets() {
        if (secretMappings.isEmpty()) return;

        try {
            authenticator = new ConjurAuthenticator();
            if (!authenticator.isEnabled()) {
                LOG.warning("Conjur not configured (missing CONJUR_AUTHN_LOGIN or CONJUR_AUTHN_API_KEY). "
                        + "Falling back to default config values.");
                return;
            }

            int fetched = 0;
            for (Map.Entry<String, String> entry : secretMappings.entrySet()) {
                String configKey = entry.getKey();
                String conjurPath = entry.getValue();
                try {
                    String value = authenticator.getSecret(conjurPath);
                    if (value != null) {
                        secrets.put(configKey, value);
                        // Set system property so Liberty server.xml variables resolve
                        System.setProperty(configKey, value);
                        fetched++;
                        LOG.info("Fetched secret '" + configKey + "' from Conjur (path: " + conjurPath + ")");
                    } else {
                        LOG.warning("Secret not found in Conjur: " + conjurPath);
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to fetch secret '" + conjurPath + "': " + e.getMessage());
                }
            }

            loaded = fetched > 0;
            LOG.info("Loaded " + fetched + "/" + secretMappings.size() + " secrets from Conjur "
                    + "(account: " + authenticator.getAccount() + ", login: " + authenticator.getAuthnLogin() + ")");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not connect to Conjur. Falling back to default config values.", e);
        }
    }

    /**
     * Re-fetch all mapped secrets from Conjur. Useful for credential rotation.
     */
    public void refresh() {
        secrets.clear();
        loaded = false;
        loadSecrets();
    }

    public boolean isLoaded() { return loaded; }

    public Map<String, String> getSecretMappings() {
        return Collections.unmodifiableMap(secretMappings);
    }

    ConjurAuthenticator getAuthenticator() { return authenticator; }

    // ========== ConfigSource SPI ==========

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
}
