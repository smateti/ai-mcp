package com.naag.toolregistry.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class SequenceGeneratorService {

    private static final long TOOL_ID_START = 10000L;

    @PersistenceContext
    private EntityManager entityManager;

    private final AtomicLong toolIdSequence = new AtomicLong(-1);

    @Transactional
    public synchronized String generateToolId() {
        if (toolIdSequence.get() < 0) {
            // Initialize from database
            Long maxId = getMaxToolIdNumber();
            toolIdSequence.set(maxId != null ? maxId : TOOL_ID_START - 1);
            log.info("Initialized tool ID sequence to: {}", toolIdSequence.get());
        }
        long nextId = toolIdSequence.incrementAndGet();
        return String.valueOf(nextId);
    }

    private Long getMaxToolIdNumber() {
        try {
            // Find max numeric tool ID from existing tools
            var result = entityManager.createQuery(
                    "SELECT MAX(CAST(t.toolId AS long)) FROM ToolDefinition t WHERE t.toolId NOT LIKE '%[^0-9]%'",
                    Long.class
            ).getSingleResult();

            if (result == null) {
                // Try a different approach - get all and filter
                var allToolIds = entityManager.createQuery(
                        "SELECT t.toolId FROM ToolDefinition t",
                        String.class
                ).getResultList();

                return allToolIds.stream()
                        .filter(id -> id != null && id.matches("\\d+"))
                        .mapToLong(Long::parseLong)
                        .max()
                        .orElse(TOOL_ID_START - 1);
            }
            return result;
        } catch (Exception e) {
            log.warn("Could not determine max tool ID, starting from {}: {}", TOOL_ID_START, e.getMessage());
            return TOOL_ID_START - 1;
        }
    }
}
