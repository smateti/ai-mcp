package com.example.toolregistry.repository;

import com.example.toolregistry.entity.ToolDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ToolDefinitionRepository extends JpaRepository<ToolDefinition, Long> {
    Optional<ToolDefinition> findByToolId(String toolId);
    boolean existsByToolId(String toolId);
}
