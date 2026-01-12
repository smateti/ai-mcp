package com.example.toolregistry.repository;

import com.example.toolregistry.entity.ResponseDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResponseDefinitionRepository extends JpaRepository<ResponseDefinition, Long> {
}
