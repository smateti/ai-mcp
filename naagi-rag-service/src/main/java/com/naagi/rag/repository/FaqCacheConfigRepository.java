package com.naagi.rag.repository;

import com.naagi.rag.entity.FaqCacheConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FaqCacheConfigRepository extends JpaRepository<FaqCacheConfig, String> {

    Optional<FaqCacheConfig> findByCategoryId(String categoryId);
}
