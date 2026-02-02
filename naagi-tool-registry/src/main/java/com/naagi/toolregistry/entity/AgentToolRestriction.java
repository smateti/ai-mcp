package com.naagi.toolregistry.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_tool_restrictions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentToolRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_tool_id", nullable = false)
    @JsonIgnore
    private AgentTool agentTool;

    @Column(nullable = false)
    private String paramName;

    @Column(nullable = false, length = 50)
    private String restrictionType; // LOCKED, ENUM_FILTER, HIDDEN

    @Column(name = "restriction_value", length = 500)
    private String value; // Locked value or comma-separated enum values
}
