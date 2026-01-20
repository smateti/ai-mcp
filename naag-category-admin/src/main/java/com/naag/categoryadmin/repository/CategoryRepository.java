package com.naag.categoryadmin.repository;

import com.naag.categoryadmin.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {

    List<Category> findByActiveTrue();

    List<Category> findByActiveFalse();

    boolean existsByName(String name);

    List<Category> findByNameContainingIgnoreCase(String name);
}
