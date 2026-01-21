package com.naag.toolregistry.entity;

import com.fasterxml.jackson.annotation.JsonGetter;
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

    @Column(length = 1000)
    private String description;

    @Column(length = 1000)
    private String humanReadableDescription;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private Boolean required = false;

    @Column(name = "param_in")
    private String in;

    private String format;

    @Column(length = 1000)
    private String example;

    @Column(nullable = false)
    private Integer nestingLevel = 0;

    @Column(length = 2000)
    private String enumValues;

    /**
     * Returns the effective description for AI model consumption.
     * If humanReadableDescription is set, uses that; otherwise uses the original description.
     * If enum values are present, appends them as "Allowed values: X, Y, Z".
     */
    @JsonGetter("effectiveDescription")
    public String getEffectiveDescription() {
        String baseDesc = (humanReadableDescription != null && !humanReadableDescription.isBlank())
                ? humanReadableDescription
                : description;

        if (baseDesc == null) {
            baseDesc = "";
        }

        if (enumValues != null && !enumValues.isBlank()) {
            String formattedEnums = enumValues.replace(",", ", ");
            if (!baseDesc.isBlank()) {
                return baseDesc + ". Allowed values: " + formattedEnums;
            } else {
                return "Allowed values: " + formattedEnums;
            }
        }

        return baseDesc;
    }
}
