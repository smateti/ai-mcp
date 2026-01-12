package com.example.toolregistry.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "parameter_definitions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParameterDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tool_definition_id")
    @JsonIgnore
    private ToolDefinition toolDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_definition_id")
    @JsonIgnore
    private ResponseDefinition responseDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_parameter_id")
    @JsonIgnore
    private ParameterDefinition parentParameter;

    @OneToMany(mappedBy = "parentParameter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParameterDefinition> nestedParameters = new ArrayList<>();

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(length = 2000)
    private String humanReadableDescription;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private Boolean required;

    @Column(name = "param_in")
    private String in;

    private String format;

    @Column(length = 2000)
    private String example;

    @Column(length = 2000)
    private String defaultValue;

    @ElementCollection
    @CollectionTable(name = "parameter_enum_values", joinColumns = @JoinColumn(name = "parameter_id"))
    @Column(name = "enum_value")
    private List<String> enumValues = new ArrayList<>();

    @Column(nullable = false)
    private Integer nestingLevel = 0;
}
