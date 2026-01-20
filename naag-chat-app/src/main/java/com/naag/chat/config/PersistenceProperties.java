package com.naag.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "naag.persistence")
public class PersistenceProperties {

    private HistoryConfig history = new HistoryConfig();
    private AuditConfig audit = new AuditConfig();

    @Data
    public static class HistoryConfig {
        private StorageType type = StorageType.H2;
        private boolean enabled = true;
    }

    @Data
    public static class AuditConfig {
        private StorageType type = StorageType.H2;
        private boolean enabled = true;
        private boolean logPrompts = true;
        private boolean logResponses = true;
        private int retentionDays = 90;
    }

    public enum StorageType {
        H2,
        ELASTICSEARCH,
        BOTH  // Write to both for redundancy
    }
}
