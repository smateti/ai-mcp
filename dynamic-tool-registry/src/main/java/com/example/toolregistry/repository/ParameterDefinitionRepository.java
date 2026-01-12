package com.example.toolregistry.repository;

import com.example.toolregistry.entity.ParameterDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParameterDefinitionRepository extends JpaRepository<ParameterDefinition, Long> {
}
