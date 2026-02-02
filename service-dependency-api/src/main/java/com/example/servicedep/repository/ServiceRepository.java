package com.example.servicedep.repository;

import com.example.servicedep.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {
    List<Service> findByApplicationId(Long applicationId);
    Optional<Service> findByServiceId(String serviceId);
}
