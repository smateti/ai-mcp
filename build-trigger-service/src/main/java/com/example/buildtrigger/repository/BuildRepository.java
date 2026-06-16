package com.example.buildtrigger.repository;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import com.example.buildtrigger.model.BuildRecord;

/**
 * JPA repository for pipeline build records.
 * Uses embedded Derby now; swap to DB2 by changing the datasource in server.xml
 * and persistence.xml — no code changes needed.
 */
@ApplicationScoped
public class BuildRepository {

    @PersistenceContext(unitName = "buildPU")
    private EntityManager em;

    @Transactional
    public void save(BuildRecord record) {
        em.persist(record);
    }

    @Transactional
    public void update(BuildRecord record) {
        em.merge(record);
    }

    public BuildRecord findByPipelineId(long pipelineId) {
        List<BuildRecord> results = em.createQuery(
                "SELECT b FROM BuildRecord b WHERE b.pipelineId = :pid ORDER BY b.triggeredAt DESC",
                BuildRecord.class)
                .setParameter("pid", pipelineId)
                .setMaxResults(1)
                .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    public List<BuildRecord> findByAppName(String appName, int limit) {
        return em.createQuery(
                "SELECT b FROM BuildRecord b WHERE b.appName = :name ORDER BY b.triggeredAt DESC",
                BuildRecord.class)
                .setParameter("name", appName)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<BuildRecord> findByAppNameAndEnvironment(String appName, String environment, int limit) {
        if (environment == null || environment.isBlank()) {
            return findByAppName(appName, limit);
        }
        return em.createQuery(
                "SELECT b FROM BuildRecord b WHERE b.appName = :name AND b.environment = :env ORDER BY b.triggeredAt DESC",
                BuildRecord.class)
                .setParameter("name", appName)
                .setParameter("env", environment)
                .setMaxResults(limit)
                .getResultList();
    }
}
