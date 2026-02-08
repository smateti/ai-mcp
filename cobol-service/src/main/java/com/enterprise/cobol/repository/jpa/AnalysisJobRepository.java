package com.enterprise.cobol.repository.jpa;

import com.enterprise.cobol.entity.AnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {

    List<AnalysisJob> findAllByOrderByStartedAtDesc();

    List<AnalysisJob> findByProjectIdOrderByStartedAtDesc(Long projectId);

    Optional<AnalysisJob> findByBatchRunId(String batchRunId);

    List<AnalysisJob> findByProjectId(Long projectId);
}
