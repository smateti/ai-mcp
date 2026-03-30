package com.example.conjur.lib;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * CDI-injectable provider for on-demand Conjur secret access.
 *
 * <h3>Usage</h3>
 * <pre>
 * &#64;Inject
 * ConjurSecretProvider conjur;
 *
 * // Fetch a secret on-demand (bypasses ConfigSource cache)
 * String apiKey = conjur.getSecret("creds/stripe-api-key");
 *
 * // Re-fetch all mapped secrets (for credential rotation)
 * conjur.refresh();
 *
 * // Check connectivity
 * boolean ok = conjur.isConnected();
 * </pre>
 */
@ApplicationScoped
public class ConjurSecretProvider {

    private static final Logger LOG = Logger.getLogger(ConjurSecretProvider.class.getName());

    private ConjurAuthenticator authenticator;
    private ConjurConfigSource configSource;

    @PostConstruct
    public void init() {
        // Find the ConjurConfigSource registered via SPI
        for (org.eclipse.microprofile.config.spi.ConfigSource cs :
                ServiceLoader.load(org.eclipse.microprofile.config.spi.ConfigSource.class)) {
            if (cs instanceof ConjurConfigSource ccs) {
                configSource = ccs;
                authenticator = ccs.getAuthenticator();
                break;
            }
        }

        // If not found via ServiceLoader (e.g., container already loaded it),
        // create a standalone authenticator
        if (authenticator == null) {
            authenticator = new ConjurAuthenticator();
        }

        LOG.info("ConjurSecretProvider initialized (enabled: " + isEnabled() + ")");
    }

    /**
     * Fetch a secret directly from Conjur (not from cache).
     *
     * @param variableId the Conjur variable path, e.g. "db/empdb-uid"
     * @return the secret value, or null if not found
     */
    public String getSecret(String variableId) {
        if (authenticator == null || !authenticator.isEnabled()) {
            throw new ConjurException("Conjur not configured");
        }
        return authenticator.getSecret(variableId);
    }

    /**
     * Re-fetch all mapped secrets from Conjur and update the ConfigSource cache.
     * Useful after credential rotation.
     */
    public void refresh() {
        if (configSource != null) {
            configSource.refresh();
            LOG.info("Refreshed all Conjur secrets");
        } else {
            LOG.warning("No ConjurConfigSource found — nothing to refresh");
        }
    }

    /**
     * Check if Conjur is reachable and credentials are configured.
     */
    public boolean isConnected() {
        return authenticator != null && authenticator.isEnabled() && authenticator.isReachable();
    }

    public boolean isEnabled() {
        return authenticator != null && authenticator.isEnabled();
    }

    /**
     * Returns the current secret mappings (configKey → conjurPath).
     */
    public Map<String, String> getMappings() {
        if (configSource != null) {
            return configSource.getSecretMappings();
        }
        return Map.of();
    }
}
