package com.naagi.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch configuration.
 * Actual ES connection management is handled by ElasticsearchHealthChecker
 * which periodically checks availability and lazily initializes the connection.
 */
@Configuration
@Slf4j
public class ElasticsearchConfig {
    // Configuration is handled by ElasticsearchHealthChecker
}
