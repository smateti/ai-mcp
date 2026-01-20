package com.naag.chat.repository;

import com.naag.chat.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, String> {

    List<ChatMessageEntity> findBySessionIdOrderByTimestampAsc(String sessionId);

    @Query("SELECT m FROM ChatMessageEntity m WHERE m.session.id = :sessionId AND m.role = :role ORDER BY m.timestamp ASC")
    List<ChatMessageEntity> findBySessionIdAndRole(@Param("sessionId") String sessionId, @Param("role") String role);

    @Query("SELECT COUNT(m) FROM ChatMessageEntity m WHERE m.session.userId = :userId")
    long countMessagesByUserId(@Param("userId") String userId);

    @Query("SELECT m FROM ChatMessageEntity m WHERE m.session.userId = :userId AND m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<ChatMessageEntity> findRecentMessages(@Param("userId") String userId, @Param("since") LocalDateTime since);

    @Query("SELECT m FROM ChatMessageEntity m WHERE m.session.userId = :userId AND LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY m.timestamp DESC")
    List<ChatMessageEntity> searchByContent(@Param("userId") String userId, @Param("query") String query);
}
