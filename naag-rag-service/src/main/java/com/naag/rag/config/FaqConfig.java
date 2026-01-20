package com.naag.rag.config;

import com.naag.rag.qdrant.FaqQdrantClient;
import com.naag.rag.qdrant.UserQuestionQdrantClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for FAQ and User Questions Qdrant collections.
 */
@Configuration
public class FaqConfig {

    @Value("${naag.rag.qdrant.baseUrl:http://localhost:6333}")
    private String qdrantBaseUrl;

    @Value("${naag.rag.qdrant.vectorSize:768}")
    private int vectorSize;

    @Value("${naag.faq.collection:naag_faq}")
    private String faqCollection;

    @Value("${naag.faq.min-similarity-score:0.85}")
    private double faqMinSimilarityScore;

    @Value("${naag.faq.auto-select-threshold:0.7}")
    private double faqAutoSelectThreshold;

    @Value("${naag.user-questions.collection:naag_user_questions}")
    private String userQuestionsCollection;

    @Value("${naag.user-questions.deduplication-threshold:0.95}")
    private double deduplicationThreshold;

    @Value("${naag.user-questions.store-all-questions:true}")
    private boolean storeAllQuestions;

    @Bean
    @ConditionalOnProperty(name = "naag.faq.enabled", havingValue = "true", matchIfMissing = true)
    public FaqQdrantClient faqQdrantClient() {
        return new FaqQdrantClient(qdrantBaseUrl, faqCollection, vectorSize);
    }

    @Bean
    @ConditionalOnProperty(name = "naag.user-questions.enabled", havingValue = "true", matchIfMissing = true)
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
