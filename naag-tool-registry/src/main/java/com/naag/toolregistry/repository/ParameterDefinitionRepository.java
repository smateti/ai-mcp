package com.naag.toolregistry.repository;

import com.naag.toolregistry.entity.ParameterDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParameterDefinitionRepository extends JpaRepository<ParameterDefinition, Long> {
}
