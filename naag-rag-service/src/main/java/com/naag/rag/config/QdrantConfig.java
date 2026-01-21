package com.naag.rag.config;

import com.naag.rag.qdrant.FaqQdrantClient;
import com.naag.rag.qdrant.QdrantClient;
import com.naag.rag.qdrant.UserQuestionQdrantClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
public class QdrantConfig {

    private static final Logger log = LoggerFactory.getLogger(QdrantConfig.class);

    private final ApplicationContext applicationContext;

    public QdrantConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

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
        try {
            QdrantClient qdrantClient = applicationContext.getBean(QdrantClient.class);
            qdrantClient.ensureCollectionExists();
            log.info("Qdrant RAG chunks collection ready");
        } catch (Exception e) {
            log.warn("Failed to initialize RAG chunks collection (Qdrant may not be available): {}", e.getMessage());
        }

        // Initialize FAQ collection
        try {
            FaqQdrantClient faqQdrantClient = applicationContext.getBean(FaqQdrantClient.class);
            faqQdrantClient.ensureCollectionExists();
            log.info("Qdrant FAQ collection ready");
        } catch (Exception e) {
            log.warn("Failed to initialize FAQ collection: {}", e.getMessage());
        }

        // Initialize User Questions collection
        try {
            UserQuestionQdrantClient userQuestionQdrantClient = applicationContext.getBean(UserQuestionQdrantClient.class);
            userQuestionQdrantClient.ensureCollectionExists();
            log.info("Qdrant User Questions collection ready");
        } catch (Exception e) {
            log.warn("Failed to initialize User Questions collection: {}", e.getMessage());
        }

        log.info("Qdrant collection initialization complete");
    }
}
