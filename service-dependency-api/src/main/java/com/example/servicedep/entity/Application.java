package com.example.servicedep.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "applications")
@Data
@NoArgsConstructor
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String applicationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String owner;

    @Column(length = 20)
    private String status; // ACTIVE, INACTIVE, DEPRECATED

    @Column(length = 20)
    private String appType; // BATCH, MICROSERVICE, UI

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Service> services = new ArrayList<>();

    public Application(String applicationId, String name, String description, String owner, String status, String appType) {
        this.applicationId = applicationId;
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.status = status;
        this.appType = appType;
    }
}
