package com.naagi.chat.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.naagi.chat.repository.AuditLogElasticsearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.repository.support.ElasticsearchRepositoryFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Periodically checks Elasticsearch availability and manages lazy connection.
 * When ES becomes available, it creates the repository.
 * When ES goes down, it marks the repository as unavailable.
 */
@Component
@Slf4j
@EnableScheduling
@ConditionalOnExpression("'${naagi.persistence.audit.type:H2}' == 'ELASTICSEARCH' or '${naagi.persistence.audit.type:H2}' == 'BOTH'")
public class ElasticsearchHealthChecker {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUri;

    @Value("${naagi.elasticsearch.health-check-interval-seconds:60}")
    private int healthCheckIntervalSeconds;

    private volatile boolean elasticsearchAvailable = false;
    private volatile AuditLogElasticsearchRepository repository = null;
    private volatile RestClient restClient = null;

    @PostConstruct
    public void init() {
        log.info("Elasticsearch health checker initialized - checking every {} seconds", healthCheckIntervalSeconds);
        checkAndConnect();
    }

    @PreDestroy
    public void cleanup() {
        if (restClient != null) {
            try {
                restClient.close();
                log.info("Elasticsearch RestClient closed");
            } catch (Exception e) {
                log.warn("Error closing RestClient: {}", e.getMessage());
            }
        }
    }

    /**
     * Periodic health check - runs every minute by default
     */
    @Scheduled(fixedDelayString = "${naagi.elasticsearch.health-check-interval-seconds:60}000")
    public void periodicHealthCheck() {
        boolean wasAvailable = elasticsearchAvailable;
        boolean isNowAvailable = pingElasticsearch();

        if (!wasAvailable && isNowAvailable) {
            log.info("Elasticsearch became available at {} - initializing connection", elasticsearchUri);
            checkAndConnect();
        } else if (wasAvailable && !isNowAvailable) {
            log.warn("Elasticsearch is no longer available at {} - will retry", elasticsearchUri);
            elasticsearchAvailable = false;
            // Don't destroy repository - it may recover
        }
    }

    private void checkAndConnect() {
        if (pingElasticsearch()) {
            elasticsearchAvailable = true;
            if (repository == null) {
                initializeRepository();
            }
        } else {
            elasticsearchAvailable = false;
            log.debug("Elasticsearch not available at {}", elasticsearchUri);
        }
    }

    private boolean pingElasticsearch() {
        try {
            URL url = new URL(elasticsearchUri);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private synchronized void initializeRepository() {
        if (repository != null) {
            return; // Already initialized
        }

        try {
            URL url = new URL(elasticsearchUri);
            restClient = RestClient.builder(
                    new HttpHost(url.getHost(), url.getPort(), url.getProtocol())
            ).build();

            RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            ElasticsearchClient client = new ElasticsearchClient(transport);
            ElasticsearchTemplate template = new ElasticsearchTemplate(client);

            ElasticsearchRepositoryFactory factory = new ElasticsearchRepositoryFactory(template);
            repository = factory.getRepository(AuditLogElasticsearchRepository.class);

            log.info("Elasticsearch repository initialized successfully");
        } catch (Exception e) {
            log.warn("Failed to initialize Elasticsearch repository: {}", e.getMessage());
            repository = null;
        }
    }

    /**
     * Get the repository if available
     */
    public AuditLogElasticsearchRepository getRepository() {
        return elasticsearchAvailable ? repository : null;
    }

    /**
     * Check if Elasticsearch is currently available
     */
    public boolean isAvailable() {
        return elasticsearchAvailable && repository != null;
    }
}
