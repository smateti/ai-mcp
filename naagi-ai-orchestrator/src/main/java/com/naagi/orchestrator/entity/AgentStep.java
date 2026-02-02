package com.naagi.orchestrator.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_steps")
@Getter
@Setter
@NoArgsConstructor
public class AgentStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AgentSession session;

    @Column(nullable = false)
    private int stepNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepType type;

    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String toolArguments;

    @Column(columnDefinition = "TEXT")
    private String toolResult;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    private long durationMs;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private AgentStep(int stepNumber, StepType type, String toolName,
                      String toolArguments, String toolResult, String reasoning, long durationMs) {
        this.stepNumber = stepNumber;
        this.type = type;
        this.toolName = toolName;
        this.toolArguments = toolArguments;
        this.toolResult = toolResult;
        this.reasoning = reasoning;
        this.durationMs = durationMs;
        this.createdAt = LocalDateTime.now();
    }

    public static AgentStep toolCall(int stepNumber, String toolName,
                                     String arguments, String result, long durationMs) {
        return new AgentStep(stepNumber, StepType.TOOL_CALL, toolName,
                arguments, result, null, durationMs);
    }

    public static AgentStep finalAnswer(int stepNumber, String answer) {
        return new AgentStep(stepNumber, StepType.FINAL_ANSWER, null,
                null, null, answer, 0);
    }

    public static AgentStep plan(int stepNumber, String planJson) {
        return new AgentStep(stepNumber, StepType.PLAN, null,
                null, null, planJson, 0);
    }

    public static AgentStep reflection(int stepNumber, String reflectionText) {
        return new AgentStep(stepNumber, StepType.REFLECTION, null,
                null, null, reflectionText, 0);
    }
}
