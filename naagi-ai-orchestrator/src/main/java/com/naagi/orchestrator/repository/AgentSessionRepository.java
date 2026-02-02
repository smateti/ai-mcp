package com.naagi.orchestrator.repository;

import com.naagi.orchestrator.entity.AgentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentSessionRepository extends JpaRepository<AgentSession, Long> {
    Optional<AgentSession> findBySessionId(String sessionId);
}
