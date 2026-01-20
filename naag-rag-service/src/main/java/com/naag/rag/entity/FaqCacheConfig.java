package com.naag.rag.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "faq_cache_config")
public class FaqCacheConfig {

    @Id
    private String categoryId;

    @Builder.Default
    private int cacheExpiryMinutes = 5; // Default 5 minutes

    @Builder.Default
    private boolean cacheEnabled = true;

    private LocalDateTime lastRefreshedAt;

    private LocalDateTime configUpdatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        configUpdatedAt = LocalDateTime.now();
    }
}
