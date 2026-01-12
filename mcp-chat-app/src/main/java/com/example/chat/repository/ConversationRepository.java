package com.example.chat.repository;

import com.example.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

  // Find all conversations ordered by most recently updated first
  List<Conversation> findAllByOrderByUpdatedAtDesc();
}
