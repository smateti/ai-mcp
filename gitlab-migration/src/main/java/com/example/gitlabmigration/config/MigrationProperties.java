package com.example.gitlabmigration.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Root configuration properties bound from application.yml under the "migration" prefix.
 * Replaces the Python toolkit's migration_config.json.
 */
@ConfigurationProperties(prefix = "migration")
@Validated
public class MigrationProperties {

    @Valid @NotNull
    private GitlabInstanceProperties source;

    @Valid @NotNull
    private GitlabInstanceProperties destination;

    private String topLevelGroup;
    private int batchSize = 10;
    private int sleepBetweenBatchesSeconds = 300;
    private int pollIntervalSeconds = 30;
    private int batchTimeoutSeconds = 3600;
    private String stateFile = "transfer_state.json";
    private String reportsDir = "reports";
    private String sourceUrlForImport;

    @Valid
    private SslProperties ssl = new SslProperties();

    public GitlabInstanceProperties getSource() { return source; }
    public void setSource(GitlabInstanceProperties source) { this.source = source; }

    public GitlabInstanceProperties getDestination() { return destination; }
    public void setDestination(GitlabInstanceProperties destination) { this.destination = destination; }

    public String getTopLevelGroup() { return topLevelGroup; }
    public void setTopLevelGroup(String topLevelGroup) { this.topLevelGroup = topLevelGroup; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getSleepBetweenBatchesSeconds() { return sleepBetweenBatchesSeconds; }
    public void setSleepBetweenBatchesSeconds(int sleepBetweenBatchesSeconds) {
        this.sleepBetweenBatchesSeconds = sleepBetweenBatchesSeconds;
    }

    public int getPollIntervalSeconds() { return pollIntervalSeconds; }
    public void setPollIntervalSeconds(int pollIntervalSeconds) {
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    public int getBatchTimeoutSeconds() { return batchTimeoutSeconds; }
    public void setBatchTimeoutSeconds(int batchTimeoutSeconds) {
        this.batchTimeoutSeconds = batchTimeoutSeconds;
    }

    public String getStateFile() { return stateFile; }
    public void setStateFile(String stateFile) { this.stateFile = stateFile; }

    public String getReportsDir() { return reportsDir; }
    public void setReportsDir(String reportsDir) { this.reportsDir = reportsDir; }

    /**
     * Optional override URL for the source instance used in bulk import payloads.
     * When set, this URL is sent to the destination GitLab for it to pull from the source.
     * Useful when the destination server reaches the source via a different hostname
     * than the one our app uses (e.g. Docker container name vs localhost).
     * When null/blank, falls back to source.url.
     */
    public String getSourceUrlForImport() {
        return sourceUrlForImport != null && !sourceUrlForImport.isBlank()
                ? sourceUrlForImport : source.getUrl();
    }
    public void setSourceUrlForImport(String sourceUrlForImport) {
        this.sourceUrlForImport = sourceUrlForImport;
    }

    public SslProperties getSsl() { return ssl; }
    public void setSsl(SslProperties ssl) { this.ssl = ssl; }
}
