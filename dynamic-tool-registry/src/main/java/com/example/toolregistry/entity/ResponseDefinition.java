package com.example.toolregistry.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "response_definitions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tool_definition_id", nullable = false)
    @JsonIgnore
    private ToolDefinition toolDefinition;

    @Column(nullable = false)
    private String statusCode;

    @Column(length = 1000)
    private String description;

    @Column(length = 2000)
    private String humanReadableDescription;

    @Column
    private String type;

    @Column(length = 10000)
    private String schema;

    @OneToMany(mappedBy = "responseDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParameterDefinition> parameters = new ArrayList<>();

    // Helper method to get root parameters (nesting level 0)
    public List<ParameterDefinition> getRootParameters() {
        return parameters.stream()
                .filter(p -> p.getNestingLevel() == 0)
                .toList();
    }
}
