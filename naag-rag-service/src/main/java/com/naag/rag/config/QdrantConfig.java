package com.naag.rag.config;

import com.naag.rag.qdrant.FaqQdrantClient;
import com.naag.rag.qdrant.QdrantClient;
import com.naag.rag.qdrant.UserQuestionQdrantClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
public class QdrantConfig {

    private static final Logger log = LoggerFactory.getLogger(QdrantConfig.class);

    @Autowired(required = false)
    private QdrantClient qdrantClient;

    @Autowired(required = false)
    private FaqQdrantClient faqQdrantClient;

    @Autowired(required = false)
    private UserQuestionQdrantClient userQuestionQdrantClient;

    @Bean
    public QdrantClient qdrantClient(
            @Value("${naag.rag.qdrant.baseUrl}") String baseUrl,
            @Value("${naag.rag.qdrant.collection}") String collection,
            @Value("${naag.rag.qdrant.vectorSize}") int vectorSize,
            @Value("${naag.rag.qdrant.distance}") String distance
    ) {
        return new QdrantClient(baseUrl, collection, vectorSize, distance);
    }

    /**
     * Initialize all Qdrant collections on application startup.
     * This ensures collections exist before any queries are made.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeCollections() {
        log.info("Initializing Qdrant collections on startup...");

        // Initialize main RAG chunks collection
        if (qdrantClient != null) {
            try {
                qdrantClient.ensureCollectionExists();
                log.info("Qdrant RAG chunks collection ready");
            } catch (Exception e) {
                log.warn("Failed to initialize RAG chunks collection (Qdrant may not be available): {}", e.getMessage());
            }
        }

        // Initialize FAQ collection
        if (faqQdrantClient != null) {
            try {
                faqQdrantClient.ensureCollectionExists();
                log.info("Qdrant FAQ collection ready");
            } catch (Exception e) {
                log.warn("Failed to initialize FAQ collection: {}", e.getMessage());
            }
        }

        // Initialize User Questions collection
        if (userQuestionQdrantClient != null) {
            try {
                userQuestionQdrantClient.ensureCollectionExists();
                log.info("Qdrant User Questions collection ready");
            } catch (Exception e) {
                log.warn("Failed to initialize User Questions collection: {}", e.getMessage());
            }
        }

        log.info("Qdrant collection initialization complete");
    }
}
