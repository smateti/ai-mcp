package com.example.conjur.repository;

import com.example.conjur.model.Employee;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class EmployeeRepository {

    private static final Logger LOG = Logger.getLogger(EmployeeRepository.class.getName());

    @Resource(lookup = "jdbc/appdb")
    private DataSource dataSource;

    public List<Employee> findAll() {
        List<Employee> employees = new ArrayList<>();
        String sql = "SELECT id, first_name, last_name, email, department, salary, hire_date, created_at FROM employees ORDER BY id";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                employees.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to query employees", e);
            throw new RuntimeException("Database query failed", e);
        }
        return employees;
    }

    public Optional<Employee> findById(Long id) {
        String sql = "SELECT id, first_name, last_name, email, department, salary, hire_date, created_at FROM employees WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to find employee: " + id, e);
            throw new RuntimeException("Database query failed", e);
        }
        return Optional.empty();
    }

    public Employee create(Employee employee) {
        String sql = "INSERT INTO employees (first_name, last_name, email, department, salary, hire_date) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, employee.getFirstName());
            ps.setString(2, employee.getLastName());
            ps.setString(3, employee.getEmail());
            ps.setString(4, employee.getDepartment());
            ps.setBigDecimal(5, employee.getSalary());
            ps.setDate(6, employee.getHireDate() != null ? Date.valueOf(employee.getHireDate()) : null);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    employee.setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to create employee", e);
            throw new RuntimeException("Database insert failed", e);
        }
        return findById(employee.getId()).orElse(employee);
    }

    public Employee update(Employee employee) {
        String sql = "UPDATE employees SET first_name = ?, last_name = ?, email = ?, department = ?, salary = ?, hire_date = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, employee.getFirstName());
            ps.setString(2, employee.getLastName());
            ps.setString(3, employee.getEmail());
            ps.setString(4, employee.getDepartment());
            ps.setBigDecimal(5, employee.getSalary());
            ps.setDate(6, employee.getHireDate() != null ? Date.valueOf(employee.getHireDate()) : null);
            ps.setLong(7, employee.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to update employee: " + employee.getId(), e);
            throw new RuntimeException("Database update failed", e);
        }
        return findById(employee.getId()).orElse(employee);
    }

    public void delete(Long id) {
        String sql = "DELETE FROM employees WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to delete employee: " + id, e);
            throw new RuntimeException("Database delete failed", e);
        }
    }

    private Employee mapRow(ResultSet rs) throws SQLException {
        Employee e = new Employee();
        e.setId(rs.getLong("id"));
        e.setFirstName(rs.getString("first_name"));
        e.setLastName(rs.getString("last_name"));
        e.setEmail(rs.getString("email"));
        e.setDepartment(rs.getString("department"));
        e.setSalary(rs.getBigDecimal("salary"));
        Date hireDate = rs.getDate("hire_date");
        if (hireDate != null) {
            e.setHireDate(hireDate.toLocalDate());
        }
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            e.setCreatedAt(createdAt.toLocalDateTime());
        }
        return e;
    }
}
