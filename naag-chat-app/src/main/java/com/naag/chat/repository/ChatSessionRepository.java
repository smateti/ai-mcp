package com.naag.chat.repository;

import com.naag.chat.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {

    List<ChatSessionEntity> findByUserIdOrderByLastMessageAtDesc(String userId);

    List<ChatSessionEntity> findByUserIdAndActiveTrue(String userId);

    List<ChatSessionEntity> findByUserIdAndCategoryIdOrderByLastMessageAtDesc(String userId, String categoryId);

    @Query("SELECT s FROM ChatSessionEntity s WHERE s.userId = :userId AND s.lastMessageAt >= :since ORDER BY s.lastMessageAt DESC")
    List<ChatSessionEntity> findRecentSessions(@Param("userId") String userId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(s) FROM ChatSessionEntity s WHERE s.userId = :userId")
    long countByUserId(@Param("userId") String userId);

    @Query("SELECT s FROM ChatSessionEntity s WHERE s.userId = :userId AND (LOWER(s.title) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<ChatSessionEntity> searchByTitle(@Param("userId") String userId, @Param("query") String query);
}
