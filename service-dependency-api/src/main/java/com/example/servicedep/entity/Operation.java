package com.example.servicedep.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "operations")
@Data
@NoArgsConstructor
public class Operation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String operationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 10)
    private String httpMethod; // GET, POST, PUT, DELETE

    @Column(length = 200)
    private String path;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    @JsonIgnore
    private Service service;

    @OneToMany(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OperationDependency> dependencies = new ArrayList<>();

    public Operation(String operationId, String name, String description, String httpMethod, String path) {
        this.operationId = operationId;
        this.name = name;
        this.description = description;
        this.httpMethod = httpMethod;
        this.path = path;
    }
}
