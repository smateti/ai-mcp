package com.naagi.toolregistry.repository;

import com.naagi.toolregistry.entity.AgentDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentDefinitionRepository extends JpaRepository<AgentDefinition, Long> {
    Optional<AgentDefinition> findByAgentId(String agentId);
    List<AgentDefinition> findByCategoryId(String categoryId);
    List<AgentDefinition> findByActiveTrue();
    List<AgentDefinition> findByCategoryIdAndActiveTrue(String categoryId);
    List<AgentDefinition> findByAgentType(String agentType);
    boolean existsByAgentId(String agentId);
    boolean existsByName(String name);
}
