package com.example.conjur.it;

import com.example.conjur.model.Employee;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.microshed.testing.SharedContainerConfig;
import org.microshed.testing.jaxrs.RESTClient;
import org.microshed.testing.jupiter.MicroShedTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicroShedTest
@SharedContainerConfig(AppContainerConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EmployeeResourceIT {

    @RESTClient
    public static EmployeeResourceClient employeeResource;

    private static Long createdEmployeeId;

    @Test
    @Order(1)
    public void testGetAllEmployees() {
        List<Employee> employees = employeeResource.getAll();
        assertNotNull(employees);
        assertTrue(employees.size() >= 3, "Should have at least 3 seed employees");
    }

    @Test
    @Order(2)
    public void testGetEmployeeById() {
        Response response = employeeResource.getById(1L);
        assertEquals(200, response.getStatus());
    }

    @Test
    @Order(3)
    public void testGetEmployeeNotFound() {
        Response response = employeeResource.getById(9999L);
        assertEquals(404, response.getStatus());
    }

    @Test
    @Order(4)
    public void testCreateEmployee() {
        Employee newEmployee = new Employee(
                "Integration", "Tester", "integration.tester@example.com",
                "QA", new BigDecimal("75000.00"), LocalDate.of(2024, 1, 15));
        Response response = employeeResource.create(newEmployee);
        assertEquals(201, response.getStatus());

        Employee created = response.readEntity(Employee.class);
        assertNotNull(created.getId());
        assertEquals("Integration", created.getFirstName());
        assertEquals("Tester", created.getLastName());
        assertEquals(new BigDecimal("75000.00"), created.getSalary());
        createdEmployeeId = created.getId();
    }

    @Test
    @Order(5)
    public void testUpdateEmployee() {
        assertNotNull(createdEmployeeId, "Created employee ID should be set from testCreateEmployee");

        Employee updated = new Employee(
                "Updated", "Tester", "updated.tester@example.com",
                "Engineering", new BigDecimal("85000.00"), LocalDate.of(2024, 1, 15));
        Response response = employeeResource.update(createdEmployeeId, updated);
        assertEquals(200, response.getStatus());

        Employee result = response.readEntity(Employee.class);
        assertEquals("Updated", result.getFirstName());
        assertEquals(new BigDecimal("85000.00"), result.getSalary());
    }

    @Test
    @Order(6)
    public void testDeleteEmployee() {
        assertNotNull(createdEmployeeId, "Created employee ID should be set from testCreateEmployee");

        Response response = employeeResource.delete(createdEmployeeId);
        assertEquals(204, response.getStatus());

        Response getResponse = employeeResource.getById(createdEmployeeId);
        assertEquals(404, getResponse.getStatus());
    }

    @Test
    @Order(7)
    public void testUpdateNonExistentEmployee() {
        Employee employee = new Employee(
                "Ghost", "Employee", "ghost@example.com",
                "None", new BigDecimal("0.01"), null);
        Response response = employeeResource.update(99999L, employee);
        assertEquals(404, response.getStatus());
    }
}
