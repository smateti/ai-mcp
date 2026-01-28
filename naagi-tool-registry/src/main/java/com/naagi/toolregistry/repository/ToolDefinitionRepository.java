package com.naagi.toolregistry.repository;

import com.naagi.toolregistry.entity.ToolDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ToolDefinitionRepository extends JpaRepository<ToolDefinition, Long> {
    Optional<ToolDefinition> findByToolId(String toolId);
    Optional<ToolDefinition> findByName(String name);
    boolean existsByToolId(String toolId);
    boolean existsByName(String name);
    List<ToolDefinition> findByCategoryId(String categoryId);
}
