package com.example.userchat.repository;

import com.example.userchat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    List<ChatSession> findAllByOrderByUpdatedAtDesc();

    List<ChatSession> findByCategoryIdOrderByUpdatedAtDesc(String categoryId);

    long countByCategoryId(String categoryId);
}
