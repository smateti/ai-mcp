package com.naagi.toolregistry.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agent_tools")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentTool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    @JsonIgnore
    private AgentDefinition agent;

    @Column(nullable = false)
    private String toolId; // Reference to ToolDefinition.toolId

    @Column(length = 1000)
    private String customDescription; // Agent-specific description override

    @Builder.Default
    private boolean required = false;

    @OneToMany(mappedBy = "agentTool", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AgentToolRestriction> restrictions = new ArrayList<>();
}
