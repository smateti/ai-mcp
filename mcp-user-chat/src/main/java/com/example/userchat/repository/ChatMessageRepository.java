package com.example.userchat.repository;

import com.example.userchat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    long countBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);
}
