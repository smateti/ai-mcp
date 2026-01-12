package com.example.rag.config;

import com.example.rag.qdrant.QdrantClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagClientsConfig {

  @Bean
  public QdrantClient qdrant(
      @Value("${rag.qdrant.baseUrl}") String baseUrl,
      @Value("${rag.qdrant.collection}") String collection,
      @Value("${rag.qdrant.vectorSize}") int vectorSize,
      @Value("${rag.qdrant.distance:Cosine}") String distance
  ) {
    return new QdrantClient(baseUrl, collection, vectorSize, distance);
  }
}
