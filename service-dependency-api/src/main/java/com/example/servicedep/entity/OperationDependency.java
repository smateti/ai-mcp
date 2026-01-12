package com.example.servicedep.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "operation_dependencies")
@Data
@NoArgsConstructor
public class OperationDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id", nullable = false)
    @JsonIgnore
    private Operation operation;

    @Column(nullable = false, length = 50)
    private String dependentApplicationId;

    @Column(nullable = false, length = 50)
    private String dependentServiceId;

    @Column(nullable = false, length = 50)
    private String dependentOperationId;

    @Column(length = 20)
    private String dependencyType; // SYNC, ASYNC, OPTIONAL

    @Column(length = 500)
    private String description;

    public OperationDependency(String dependentApplicationId, String dependentServiceId,
                                String dependentOperationId, String dependencyType, String description) {
        this.dependentApplicationId = dependentApplicationId;
        this.dependentServiceId = dependentServiceId;
        this.dependentOperationId = dependentOperationId;
        this.dependencyType = dependencyType;
        this.description = description;
    }
}
