package com.example.conjur.repository;

import com.example.conjur.model.Employee;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory employee repository with prefilled sample data.
 * Demonstrates a MicroProfile app that would connect to DB2 in production,
 * with credentials injected by the Conjur K8s init container.
 */
@ApplicationScoped
public class EmployeeRepository {

    private final AtomicLong idGen = new AtomicLong(0);
    private final List<Employee> employees = new CopyOnWriteArrayList<>();

    public EmployeeRepository() {
        seed("Alice", "Johnson", "alice.johnson@example.com", "Engineering", "95000", "2022-03-15");
        seed("Bob", "Smith", "bob.smith@example.com", "Marketing", "78000", "2021-07-01");
        seed("Carol", "Williams", "carol.williams@example.com", "Finance", "88000", "2023-01-10");
        seed("David", "Brown", "david.brown@example.com", "Engineering", "102000", "2020-11-20");
        seed("Eve", "Davis", "eve.davis@example.com", "HR", "72000", "2024-02-28");
    }

    private void seed(String first, String last, String email, String dept, String salary, String hire) {
        Employee e = new Employee(first, last, email, dept, new BigDecimal(salary), LocalDate.parse(hire));
        e.setId(idGen.incrementAndGet());
        e.setCreatedAt(LocalDateTime.now());
        employees.add(e);
    }

    public List<Employee> findAll() { return Collections.unmodifiableList(employees); }

    public Optional<Employee> findById(Long id) {
        return employees.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    public Employee create(Employee emp) {
        emp.setId(idGen.incrementAndGet());
        emp.setCreatedAt(LocalDateTime.now());
        employees.add(emp);
        return emp;
    }

    public Employee update(Employee emp) {
        for (int i = 0; i < employees.size(); i++) {
            if (employees.get(i).getId().equals(emp.getId())) {
                employees.set(i, emp);
                return emp;
            }
        }
        return emp;
    }

    public void delete(Long id) {
        employees.removeIf(e -> e.getId().equals(id));
    }
}
