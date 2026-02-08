package com.enterprise.cobol.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long projectId;

    private String runLabel;

    @Column(nullable = false)
    private String folderPath;

    private String copybookPath;

    @Column(nullable = false)
    private String status;

    private Integer programCount;

    private String currentStep;

    private Integer progress;

    private String batchRunId;

    private Long batchJobId;

    private String errorMessage;

    @Column(length = 4000)
    private String customPrompt;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}
