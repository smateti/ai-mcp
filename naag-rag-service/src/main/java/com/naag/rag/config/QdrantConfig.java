package com.naag.rag.config;

import com.naag.rag.qdrant.QdrantClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QdrantConfig {

    @Bean
    public QdrantClient qdrantClient(
            @Value("${naag.rag.qdrant.baseUrl}") String baseUrl,
            @Value("${naag.rag.qdrant.collection}") String collection,
            @Value("${naag.rag.qdrant.vectorSize}") int vectorSize,
            @Value("${naag.rag.qdrant.distance}") String distance
    ) {
        return new QdrantClient(baseUrl, collection, vectorSize, distance);
    }
}
