package com.example.buildtrigger.gitlab;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Factory that provides JAX-RS Clients for source and target GitLab instances.
 * Both share the same truststore (which contains certs for both servers).
 */
@ApplicationScoped
public class GitLabClientFactory {

    private static final Logger LOG = Logger.getLogger(GitLabClientFactory.class.getName());

    // ── Source GitLab config ──
    @Inject @ConfigProperty(name = "gitlab.url", defaultValue = "https://localhost:9043")
    private String sourceUrl;

    @Inject @ConfigProperty(name = "gitlab.token", defaultValue = "")
    private String sourceToken;

    // ── Target GitLab config ──
    @Inject @ConfigProperty(name = "gitlab.target.url", defaultValue = "https://localhost:9044")
    private String targetUrl;

    @Inject @ConfigProperty(name = "gitlab.target.token", defaultValue = "")
    private String targetToken;

    // ── Shared truststore ──
    @Inject @ConfigProperty(name = "gitlab.truststore.path")
    private Optional<String> truststorePath;

    @Inject @ConfigProperty(name = "gitlab.truststore.password", defaultValue = "changeit")
    private Optional<String> truststorePassword;

    @Inject @ConfigProperty(name = "gitlab.connect.timeout", defaultValue = "5000")
    private int connectTimeout;

    @Inject @ConfigProperty(name = "gitlab.read.timeout", defaultValue = "15000")
    private int readTimeout;

    private Client sharedClient;

    @PostConstruct
    void init() {
        try {
            ClientBuilder builder = ClientBuilder.newBuilder()
                    .connectTimeout(connectTimeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeout, java.util.concurrent.TimeUnit.MILLISECONDS);

            if (truststorePath.isPresent() && !truststorePath.get().isBlank()) {
                SSLContext sslContext = buildSslContext();
                builder.sslContext(sslContext);
                LOG.info("GitLabClientFactory using truststore: " + truststorePath.get());
            }

            sharedClient = builder.build();
            LOG.info("GitLabClientFactory ready — source: " + sourceUrl + ", target: " + targetUrl);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to init GitLabClientFactory", e);
            throw new RuntimeException("GitLabClientFactory init failed", e);
        }
    }

    @PreDestroy
    void cleanup() {
        if (sharedClient != null) {
            sharedClient.close();
        }
    }

    public Client getClient() {
        return sharedClient;
    }

    public String getUrl(String instance) {
        return "target".equalsIgnoreCase(instance) ? targetUrl : sourceUrl;
    }

    public String getToken(String instance) {
        return "target".equalsIgnoreCase(instance) ? targetToken : sourceToken;
    }

    private SSLContext buildSslContext() throws Exception {
        KeyStore ts = KeyStore.getInstance("PKCS12");
        try (InputStream is = java.nio.file.Files.newInputStream(
                java.nio.file.Path.of(truststorePath.get()))) {
            ts.load(is, truststorePassword.orElse("changeit").toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), new SecureRandom());
        return ctx;
    }
}
