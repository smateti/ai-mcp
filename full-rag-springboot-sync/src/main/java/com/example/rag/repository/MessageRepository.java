package com.example.rag.repository;

import com.example.rag.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

  // Find all messages for a conversation ordered by creation time
  List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
