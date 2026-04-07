package com.example.conjur.config;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/**
 * MicroProfile ConfigSource that reads secrets from a properties file
 * written by the Conjur init container at /conjur/secrets/secrets.properties.
 * No Conjur SDK dependency — secrets are fetched by the K8s init container.
 */
public class ConjurConfigSource implements ConfigSource {

    private static final Logger LOG = Logger.getLogger(ConjurConfigSource.class.getName());
    private final Map<String, String> properties = new HashMap<>();

    public ConjurConfigSource() {
        String dir = System.getenv("CONJUR_SECRETS_DIR");
        if (dir == null || dir.isBlank()) dir = "/conjur/secrets";
        Path file = Path.of(dir, "secrets.properties");
        if (Files.exists(file)) {
            try (InputStream is = new FileInputStream(file.toFile())) {
                Properties props = new Properties();
                props.load(is);
                props.forEach((k, v) -> properties.put(k.toString(), v.toString()));
                LOG.info("[ConjurInitSecrets] Loaded " + properties.size() + " secrets from " + file);
            } catch (Exception e) {
                LOG.warning("[ConjurInitSecrets] Failed to load " + file + ": " + e.getMessage());
            }
        } else {
            LOG.info("[ConjurInitSecrets] No secrets file at " + file + " (using defaults)");
        }
    }

    @Override public Map<String, String> getProperties() { return Collections.unmodifiableMap(properties); }
    @Override public Set<String> getPropertyNames() { return properties.keySet(); }
    @Override public String getValue(String propertyName) { return properties.get(propertyName); }
    @Override public String getName() { return "ConjurInitContainerConfigSource"; }
    @Override public int getOrdinal() { return 500; }
}
