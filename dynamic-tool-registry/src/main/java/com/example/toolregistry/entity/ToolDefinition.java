package com.example.toolregistry.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tool_definitions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String toolId;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 2000)
    private String humanReadableDescription;

    @Column(nullable = false)
    private String openApiEndpoint;

    @Column(nullable = false)
    private String httpMethod;

    @Column(nullable = false)
    private String path;

    @Column(length = 500)
    private String baseUrl;

    @OneToMany(mappedBy = "toolDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParameterDefinition> parameters = new ArrayList<>();

    @OneToMany(mappedBy = "toolDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResponseDefinition> responses = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public List<ParameterDefinition> getRootParameters() {
        return parameters.stream()
                .filter(p -> p.getParentParameter() == null)
                .toList();
    }
}
