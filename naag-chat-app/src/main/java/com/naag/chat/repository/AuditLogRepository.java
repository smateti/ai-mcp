package com.naag.chat.repository;

import com.naag.chat.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, String> {

    List<AuditLogEntity> findByUserIdOrderByTimestampDesc(String userId);

    Page<AuditLogEntity> findByUserIdOrderByTimestampDesc(String userId, Pageable pageable);

    List<AuditLogEntity> findBySessionIdOrderByTimestampAsc(String sessionId);

    List<AuditLogEntity> findByUserIdAndActionOrderByTimestampDesc(String userId, String action);

    @Query("SELECT a FROM AuditLogEntity a WHERE a.userId = :userId AND a.timestamp BETWEEN :start AND :end ORDER BY a.timestamp DESC")
    List<AuditLogEntity> findByUserIdAndDateRange(
            @Param("userId") String userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT a FROM AuditLogEntity a WHERE a.userId = :userId AND a.success = false ORDER BY a.timestamp DESC")
    List<AuditLogEntity> findFailedByUserId(@Param("userId") String userId);

    @Query("SELECT AVG(a.processingTimeMs) FROM AuditLogEntity a WHERE a.userId = :userId AND a.action = 'MESSAGE_PROCESSED'")
    Double getAverageProcessingTime(@Param("userId") String userId);

    @Query("SELECT COUNT(a) FROM AuditLogEntity a WHERE a.userId = :userId AND a.action = :action")
    long countByUserIdAndAction(@Param("userId") String userId, @Param("action") String action);

    @Query("SELECT a.selectedTool, COUNT(a) FROM AuditLogEntity a WHERE a.userId = :userId AND a.selectedTool IS NOT NULL GROUP BY a.selectedTool ORDER BY COUNT(a) DESC")
    List<Object[]> getToolUsageStats(@Param("userId") String userId);

    /**
     * Find audit logs by question text (exact or containing match)
     */
    @Query("SELECT a FROM AuditLogEntity a WHERE LOWER(a.userQuestion) LIKE LOWER(CONCAT('%', :question, '%')) AND a.assistantResponse IS NOT NULL ORDER BY a.timestamp DESC")
    List<AuditLogEntity> findByQuestionContaining(@Param("question") String question);

    /**
     * Find the most recent audit log for an exact question
     */
    @Query("SELECT a FROM AuditLogEntity a WHERE a.userQuestion = :question AND a.assistantResponse IS NOT NULL ORDER BY a.timestamp DESC")
    List<AuditLogEntity> findByExactQuestion(@Param("question") String question);
}
