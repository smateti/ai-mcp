package com.example.conjur.admin.service;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages per-app K8s Secrets for Conjur identity distribution.
 * Creates secrets named "conjur-{appname}" in the target namespace
 * with the app's Conjur authentication credentials.
 */
@ApplicationScoped
public class K8sSecretManager {

    private static final Logger LOG = Logger.getLogger(K8sSecretManager.class.getName());

    @Inject
    @ConfigProperty(name = "conjur.appliance.url",
                    defaultValue = "http://conjur-server.conjur-system.svc.cluster.local")
    private String applianceUrl;

    @Inject
    @ConfigProperty(name = "conjur.account", defaultValue = "myConjurAccount")
    private String account;

    private KubernetesClient kubeClient;

    @PostConstruct
    public void init() {
        try {
            kubeClient = new KubernetesClientBuilder().build();
            LOG.info("K8sSecretManager initialized");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to create Kubernetes client — K8s Secret creation disabled", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (kubeClient != null) {
            kubeClient.close();
        }
    }

    /**
     * Creates or updates a K8s Secret with Conjur identity for an application.
     *
     * @param appName   application name (used in secret name: conjur-{appname})
     * @param namespace K8s namespace
     * @param apiKey    the Conjur host API key
     * @return the K8s Secret name
     */
    public String createAppSecret(String appName, String namespace, String apiKey) {
        if (kubeClient == null) {
            throw new RuntimeException("Kubernetes client not available");
        }

        String secretName = "conjur-" + appName;
        String authnLogin = "host/apps/" + appName;

        Map<String, String> data = new LinkedHashMap<>();
        data.put("CONJUR_APPLIANCE_URL", encode(applianceUrl));
        data.put("CONJUR_ACCOUNT", encode(account));
        data.put("CONJUR_AUTHN_LOGIN", encode(authnLogin));
        data.put("CONJUR_AUTHN_API_KEY", encode(apiKey));

        Secret secret = new SecretBuilder()
                .withNewMetadata()
                    .withName(secretName)
                    .withNamespace(namespace)
                    .addToLabels("managed-by", "conjur-admin")
                    .addToLabels("app", appName)
                .endMetadata()
                .withType("Opaque")
                .withData(data)
                .build();

        kubeClient.secrets()
                .inNamespace(namespace)
                .resource(secret)
                .serverSideApply();

        LOG.info("Created/updated K8s Secret: " + namespace + "/" + secretName);
        return secretName;
    }

    /**
     * Deletes a per-app Conjur K8s Secret.
     */
    public void deleteAppSecret(String appName, String namespace) {
        if (kubeClient == null) return;
        String secretName = "conjur-" + appName;
        kubeClient.secrets().inNamespace(namespace).withName(secretName).delete();
        LOG.info("Deleted K8s Secret: " + namespace + "/" + secretName);
    }

    public boolean isAvailable() {
        return kubeClient != null;
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes());
    }
}
