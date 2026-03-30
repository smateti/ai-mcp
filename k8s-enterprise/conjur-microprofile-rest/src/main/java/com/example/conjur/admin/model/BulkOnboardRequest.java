package com.example.conjur.admin.model;

import java.util.List;
import java.util.Map;

public class BulkOnboardRequest {

    private List<AppEntry> apps;

    public List<AppEntry> getApps() { return apps; }
    public void setApps(List<AppEntry> apps) { this.apps = apps; }

    public static class AppEntry {
        private String name;
        private String namespace = "apps";
        private List<String> databases;
        private Map<String, String> extraSecrets;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }

        public List<String> getDatabases() { return databases; }
        public void setDatabases(List<String> databases) { this.databases = databases; }

        public Map<String, String> getExtraSecrets() { return extraSecrets; }
        public void setExtraSecrets(Map<String, String> extraSecrets) { this.extraSecrets = extraSecrets; }
    }
}
