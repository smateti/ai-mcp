package com.naagi.categoryadmin.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class CategorySequenceService {

    private static final long CATEGORY_ID_START = 10000L;

    @PersistenceContext
    private EntityManager entityManager;

    private final AtomicLong categoryIdSequence = new AtomicLong(-1);

    @Transactional
    public synchronized String generateCategoryId() {
        if (categoryIdSequence.get() < 0) {
            // Initialize from database
            Long maxId = getMaxCategoryIdNumber();
            categoryIdSequence.set(maxId != null ? maxId : CATEGORY_ID_START - 1);
            log.info("Initialized category ID sequence to: {}", categoryIdSequence.get());
        }
        long nextId = categoryIdSequence.incrementAndGet();
        return String.valueOf(nextId);
    }

    private Long getMaxCategoryIdNumber() {
        try {
            // Get all category IDs and filter for numeric ones
            var allCategoryIds = entityManager.createQuery(
                    "SELECT c.id FROM Category c",
                    String.class
            ).getResultList();

            return allCategoryIds.stream()
                    .filter(id -> id != null && id.matches("\\d+"))
                    .mapToLong(Long::parseLong)
                    .max()
                    .orElse(CATEGORY_ID_START - 1);
        } catch (Exception e) {
            log.warn("Could not determine max category ID, starting from {}: {}", CATEGORY_ID_START, e.getMessage());
            return CATEGORY_ID_START - 1;
        }
    }
}
