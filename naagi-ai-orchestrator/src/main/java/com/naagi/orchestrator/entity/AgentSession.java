package com.naagi.orchestrator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agent_sessions")
@Getter
@Setter
@NoArgsConstructor
public class AgentSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sessionId;

    @Column(nullable = false, length = 4000)
    private String userMessage;

    private String categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(columnDefinition = "TEXT")
    private String finalAnswer;

    private int totalSteps;

    private int totalToolCalls;

    private long totalDurationMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("stepNumber ASC")
    private List<AgentStep> steps = new ArrayList<>();

    public AgentSession(String userMessage, String categoryId) {
        this.sessionId = UUID.randomUUID().toString();
        this.userMessage = userMessage;
        this.categoryId = categoryId;
        this.status = SessionStatus.RUNNING;
        this.createdAt = LocalDateTime.now();
    }

    public void addStep(AgentStep step) {
        step.setSession(this);
        this.steps.add(step);
        this.totalSteps = this.steps.size();
    }

    public void complete(String answer) {
        this.finalAnswer = answer;
        this.status = SessionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.totalDurationMs = java.time.Duration.between(createdAt, completedAt).toMillis();
    }

    public void fail(String reason) {
        this.finalAnswer = reason;
        this.status = SessionStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.totalDurationMs = java.time.Duration.between(createdAt, completedAt).toMillis();
    }

    public void maxStepsReached(String bestAnswer) {
        this.finalAnswer = bestAnswer;
        this.status = SessionStatus.MAX_STEPS_REACHED;
        this.completedAt = LocalDateTime.now();
        this.totalDurationMs = java.time.Duration.between(createdAt, completedAt).toMillis();
    }

    public boolean isCompleted() {
        return status == SessionStatus.COMPLETED;
    }
}
