package com.example.test4.ejb;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stateless session bean that uses plain JDBC against an H2 file-based database.
 * No JPA - just raw SQL via a container-managed DataSource.
 */
@Stateless
public class EmployeeBean implements EmployeeRemote {

    private static final Logger LOG = Logger.getLogger(EmployeeBean.class.getName());

    @Resource(lookup = "jdbc/h2DB")
    private DataSource dataSource;

    @PostConstruct
    public void init() {
        initTable();
    }

    private void initTable() {
        String ddl = "CREATE TABLE IF NOT EXISTS EMPLOYEE ("
                + "ID INT AUTO_INCREMENT PRIMARY KEY, "
                + "FIRST_NAME VARCHAR(100) NOT NULL, "
                + "LAST_NAME VARCHAR(100) NOT NULL, "
                + "DEPARTMENT VARCHAR(100), "
                + "SALARY DECIMAL(10,2)"
                + ")";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
            LOG.info("EMPLOYEE table ready.");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to initialize EMPLOYEE table", e);
        }
    }

    @Override
    public void createEmployee(String firstName, String lastName, String department, double salary) {
        String sql = "INSERT INTO EMPLOYEE (FIRST_NAME, LAST_NAME, DEPARTMENT, SALARY) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, firstName);
            ps.setString(2, lastName);
            ps.setString(3, department);
            ps.setDouble(4, salary);
            ps.executeUpdate();
            LOG.info("Created employee: " + firstName + " " + lastName);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to create employee", e);
            throw new RuntimeException("Failed to create employee", e);
        }
    }

    @Override
    public Employee findEmployee(int id) {
        String sql = "SELECT * FROM EMPLOYEE WHERE ID = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to find employee id=" + id, e);
            throw new RuntimeException("Failed to find employee", e);
        }
        return null;
    }

    @Override
    public List<Employee> findAllEmployees() {
        String sql = "SELECT * FROM EMPLOYEE ORDER BY ID";
        List<Employee> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to list employees", e);
            throw new RuntimeException("Failed to list employees", e);
        }
        return list;
    }

    @Override
    public void updateEmployee(int id, String firstName, String lastName, String department, double salary) {
        String sql = "UPDATE EMPLOYEE SET FIRST_NAME=?, LAST_NAME=?, DEPARTMENT=?, SALARY=? WHERE ID=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, firstName);
            ps.setString(2, lastName);
            ps.setString(3, department);
            ps.setDouble(4, salary);
            ps.setInt(5, id);
            ps.executeUpdate();
            LOG.info("Updated employee id=" + id);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to update employee id=" + id, e);
            throw new RuntimeException("Failed to update employee", e);
        }
    }

    @Override
    public void deleteEmployee(int id) {
        String sql = "DELETE FROM EMPLOYEE WHERE ID = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
            LOG.info("Deleted employee id=" + id);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Failed to delete employee id=" + id, e);
            throw new RuntimeException("Failed to delete employee", e);
        }
    }

    private Employee mapRow(ResultSet rs) throws SQLException {
        return new Employee(
                rs.getInt("ID"),
                rs.getString("FIRST_NAME"),
                rs.getString("LAST_NAME"),
                rs.getString("DEPARTMENT"),
                rs.getDouble("SALARY")
        );
    }
}
