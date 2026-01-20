package com.naag.toolregistry.repository;

import com.naag.toolregistry.entity.ResponseDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResponseDefinitionRepository extends JpaRepository<ResponseDefinition, Long> {
}
