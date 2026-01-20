package com.naag.toolregistry.repository;

import com.naag.toolregistry.entity.ToolDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ToolDefinitionRepository extends JpaRepository<ToolDefinition, Long> {
    Optional<ToolDefinition> findByToolId(String toolId);
    boolean existsByToolId(String toolId);
    List<ToolDefinition> findByCategoryId(String categoryId);
}
