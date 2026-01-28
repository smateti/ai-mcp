package com.naagi.rag.config;

import com.naagi.rag.qdrant.FaqQdrantClient;
import com.naagi.rag.qdrant.UserQuestionQdrantClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for FAQ and User Questions Qdrant collections.
 */
@Configuration
public class FaqConfig {

    @Value("${naagi.rag.qdrant.baseUrl:http://localhost:6333}")
    private String qdrantBaseUrl;

    @Value("${naagi.rag.qdrant.vectorSize:768}")
    private int vectorSize;

    @Value("${naagi.faq.collection:naagi_faq}")
    private String faqCollection;

    @Value("${naagi.faq.min-similarity-score:0.85}")
    private double faqMinSimilarityScore;

    @Value("${naagi.faq.auto-select-threshold:0.7}")
    private double faqAutoSelectThreshold;

    @Value("${naagi.user-questions.collection:naagi_user_questions}")
    private String userQuestionsCollection;

    @Value("${naagi.user-questions.deduplication-threshold:0.95}")
    private double deduplicationThreshold;

    @Value("${naagi.user-questions.store-all-questions:true}")
    private boolean storeAllQuestions;

    @Bean
    @ConditionalOnProperty(name = "naagi.faq.enabled", havingValue = "true", matchIfMissing = true)
    public FaqQdrantClient faqQdrantClient() {
        return new FaqQdrantClient(qdrantBaseUrl, faqCollection, vectorSize);
    }

    @Bean
    @ConditionalOnProperty(name = "naagi.user-questions.enabled", havingValue = "true", matchIfMissing = true)
    public UserQuestionQdrantClient userQuestionQdrantClient() {
        return new UserQuestionQdrantClient(qdrantBaseUrl, userQuestionsCollection, vectorSize);
    }

    // Getters for configuration values

    public double getFaqMinSimilarityScore() {
        return faqMinSimilarityScore;
    }

    public double getFaqAutoSelectThreshold() {
        return faqAutoSelectThreshold;
    }

    public double getDeduplicationThreshold() {
        return deduplicationThreshold;
    }

    public boolean isStoreAllQuestions() {
        return storeAllQuestions;
    }
}
