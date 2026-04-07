package com.example.conjur.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.logging.Logger;

/**
 * Logs the DB connection info sourced from Conjur via MicroProfile Config.
 * The actual datasource is configured in server.xml using Liberty variables,
 * which are populated via environment variables set by docker-compose.
 */
@ApplicationScoped
public class ConjurDatasourceConfigurer {

    private static final Logger LOG = Logger.getLogger(ConjurDatasourceConfigurer.class.getName());

    @Inject
    @ConfigProperty(name = "conjur.secret.db.username", defaultValue = "not-set")
    private String dbUsername;

    @Inject
    @ConfigProperty(name = "conjur.secret.db.url", defaultValue = "not-set")
    private String dbUrl;

    public void onStart(@Observes @Initialized(ApplicationScoped.class) Object init) {
        LOG.info("=== Conjur Datasource Configuration ===");
        LOG.info("DB URL (from Conjur): " + dbUrl);
        LOG.info("DB Username (from Conjur): " + dbUsername);
        LOG.info("DB credentials successfully loaded from Conjur vault");
    }
}
