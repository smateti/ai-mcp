package com.naag.categoryadmin.repository;

import com.naag.categoryadmin.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    Page<AuditLog> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    Page<AuditLog> findByActionOrderByTimestampDesc(String action, Pageable pageable);

    Page<AuditLog> findByEntityTypeOrderByTimestampDesc(String entityType, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId, Pageable pageable);

    Page<AuditLog> findByCategoryIdOrderByTimestampDesc(String categoryId, Pageable pageable);

    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);

    List<AuditLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    List<AuditLog> findByTimestampAfter(LocalDateTime since);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:entityType IS NULL OR a.entityType = :entityType) AND " +
           "(:categoryId IS NULL OR a.categoryId = :categoryId) AND " +
           "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR a.timestamp <= :endDate) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findByFilters(
            @Param("userId") String userId,
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("categoryId") String categoryId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    @Query("SELECT DISTINCT a.userId FROM AuditLog a ORDER BY a.userId")
    List<String> findDistinctUserIds();

    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> findDistinctActions();

    @Query("SELECT DISTINCT a.entityType FROM AuditLog a ORDER BY a.entityType")
    List<String> findDistinctEntityTypes();

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since")
    long countSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.status = 'FAILURE' AND a.timestamp >= :since")
    long countFailuresSince(@Param("since") LocalDateTime since);

    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since GROUP BY a.action ORDER BY COUNT(a) DESC")
    List<Object[]> countByActionSince(@Param("since") LocalDateTime since);

    @Query("SELECT a.userId, COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since GROUP BY a.userId ORDER BY COUNT(a) DESC")
    List<Object[]> countByUserSince(@Param("since") LocalDateTime since);
}
