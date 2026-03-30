package com.example.propset.model;

import java.util.Map;

public class DatasourceConfig {

    private String name;
    private String description;
    private String configMapName;
    private String configMapNamespace;
    private Map<String, String> properties;

    public DatasourceConfig() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getConfigMapName() { return configMapName; }
    public void setConfigMapName(String configMapName) { this.configMapName = configMapName; }

    public String getConfigMapNamespace() { return configMapNamespace; }
    public void setConfigMapNamespace(String configMapNamespace) { this.configMapNamespace = configMapNamespace; }

    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }
}
