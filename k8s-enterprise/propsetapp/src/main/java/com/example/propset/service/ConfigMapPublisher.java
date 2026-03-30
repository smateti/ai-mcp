package com.example.propset.service;

import com.example.propset.model.DatasourceConfig;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ConfigMapPublisher {

    private static final Logger LOG = Logger.getLogger(ConfigMapPublisher.class.getName());

    @Inject
    @ConfigProperty(name = "propset.config.path", defaultValue = "/config/datasources.yaml")
    private String configPath;

    private KubernetesClient kubeClient;
    private List<DatasourceConfig> datasources = new ArrayList<>();
    private final Map<String, String> publishStatus = new LinkedHashMap<>();

    public void onStartup(@Observes @Initialized(ApplicationScoped.class) Object init) {
        // Triggers eager initialization at app startup
    }

    @PostConstruct
    public void init() {
        try {
            kubeClient = new KubernetesClientBuilder().build();
            LOG.info("Kubernetes client initialized");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to create Kubernetes client", e);
            return;
        }
        loadAndPublish();
    }

    @PreDestroy
    public void cleanup() {
        if (kubeClient != null) {
            kubeClient.close();
        }
    }

    @SuppressWarnings("unchecked")
    public void loadAndPublish() {
        datasources.clear();
        publishStatus.clear();

        try (InputStream is = new FileInputStream(configPath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            List<Map<String, Object>> entries = (List<Map<String, Object>>) root.get("datasources");

            if (entries == null) {
                LOG.warning("No datasources found in config file: " + configPath);
                return;
            }

            for (Map<String, Object> entry : entries) {
                DatasourceConfig ds = new DatasourceConfig();
                ds.setName((String) entry.get("name"));
                ds.setDescription((String) entry.get("description"));
                ds.setConfigMapName((String) entry.get("configMapName"));
                ds.setConfigMapNamespace((String) entry.get("configMapNamespace"));

                Map<String, Object> props = (Map<String, Object>) entry.get("properties");
                if (props != null) {
                    Map<String, String> stringProps = new LinkedHashMap<>();
                    props.forEach((k, v) -> stringProps.put(k, String.valueOf(v)));
                    ds.setProperties(stringProps);
                }

                datasources.add(ds);
                publishConfigMap(ds);
            }

            LOG.info("Published " + datasources.size() + " ConfigMap(s) from " + configPath);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load datasource config from " + configPath, e);
        }
    }

    private void publishConfigMap(DatasourceConfig ds) {
        try {
            ConfigMap configMap = new ConfigMapBuilder()
                    .withNewMetadata()
                        .withName(ds.getConfigMapName())
                        .withNamespace(ds.getConfigMapNamespace())
                        .addToLabels("managed-by", "propsetapp")
                        .addToLabels("datasource", ds.getName())
                    .endMetadata()
                    .withData(ds.getProperties())
                    .build();

            kubeClient.configMaps()
                    .inNamespace(ds.getConfigMapNamespace())
                    .resource(configMap)
                    .serverSideApply();

            String key = ds.getConfigMapNamespace() + "/" + ds.getConfigMapName();
            publishStatus.put(key, "published");
            LOG.info("Published ConfigMap: " + key + " with " + ds.getProperties().size() + " properties");
        } catch (Exception e) {
            String key = ds.getConfigMapNamespace() + "/" + ds.getConfigMapName();
            publishStatus.put(key, "failed: " + e.getMessage());
            LOG.log(Level.SEVERE, "Failed to publish ConfigMap: " + key, e);
        }
    }

    public List<DatasourceConfig> getDatasources() {
        return Collections.unmodifiableList(datasources);
    }

    public Map<String, String> getPublishStatus() {
        return Collections.unmodifiableMap(publishStatus);
    }
}
