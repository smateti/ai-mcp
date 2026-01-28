package com.naagi.rag.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class DocumentSequenceService {

    private static final long DOC_ID_START = 10000L;

    @PersistenceContext
    private EntityManager entityManager;

    private final AtomicLong docIdSequence = new AtomicLong(-1);

    @Transactional
    public synchronized String generateDocId() {
        if (docIdSequence.get() < 0) {
            // Initialize from database
            Long maxId = getMaxDocIdNumber();
            docIdSequence.set(maxId != null ? maxId : DOC_ID_START - 1);
            log.info("Initialized document ID sequence to: {}", docIdSequence.get());
        }
        long nextId = docIdSequence.incrementAndGet();
        return String.valueOf(nextId);
    }

    private Long getMaxDocIdNumber() {
        try {
            // Get all docIds and filter for numeric ones
            var allDocIds = entityManager.createQuery(
                    "SELECT d.docId FROM DocumentUpload d",
                    String.class
            ).getResultList();

            return allDocIds.stream()
                    .filter(id -> id != null && id.matches("\\d+"))
                    .mapToLong(Long::parseLong)
                    .max()
                    .orElse(DOC_ID_START - 1);
        } catch (Exception e) {
            log.warn("Could not determine max doc ID, starting from {}: {}", DOC_ID_START, e.getMessage());
            return DOC_ID_START - 1;
        }
    }
}
