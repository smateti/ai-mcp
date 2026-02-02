package com.naagi.llm.repository;

import com.naagi.llm.entity.ConversationTurn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationTurnRepository extends JpaRepository<ConversationTurn, Long> {
}
