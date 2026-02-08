package com.enterprise.cobol.repository.jpa;

import com.enterprise.cobol.entity.SavedQuery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavedQueryRepository extends JpaRepository<SavedQuery, Long> {

    List<SavedQuery> findAllByOrderByCreatedAtDesc();
}
