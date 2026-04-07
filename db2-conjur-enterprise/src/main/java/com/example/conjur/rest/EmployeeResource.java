package com.example.conjur.rest;

import com.example.conjur.model.Employee;
import com.example.conjur.repository.EmployeeRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@Path("/employees")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Employees", description = "CRUD operations on employees (DB2 via Conjur credentials)")
public class EmployeeResource {

    @Inject
    private EmployeeRepository employeeRepository;

    @GET
    @Operation(summary = "List all employees")
    @APIResponse(responseCode = "200", description = "Employees retrieved")
    public List<Employee> getAll() {
        return employeeRepository.findAll();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get employee by ID")
    @APIResponse(responseCode = "200", description = "Employee found")
    @APIResponse(responseCode = "404", description = "Employee not found")
    public Response getById(@PathParam("id") Long id) {
        return employeeRepository.findById(id)
                .map(e -> Response.ok(e).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Employee not found: " + id))
                        .build());
    }

    @POST
    @Operation(summary = "Create a new employee")
    @APIResponse(responseCode = "201", description = "Employee created")
    public Response create(Employee employee) {
        Employee created = employeeRepository.create(employee);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update an employee")
    @APIResponse(responseCode = "200", description = "Employee updated")
    @APIResponse(responseCode = "404", description = "Employee not found")
    public Response update(@PathParam("id") Long id, Employee employee) {
        return employeeRepository.findById(id)
                .map(existing -> {
                    existing.setFirstName(employee.getFirstName());
                    existing.setLastName(employee.getLastName());
                    existing.setEmail(employee.getEmail());
                    existing.setDepartment(employee.getDepartment());
                    existing.setSalary(employee.getSalary());
                    existing.setHireDate(employee.getHireDate());
                    return Response.ok(employeeRepository.update(existing)).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Employee not found: " + id))
                        .build());
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete an employee")
    @APIResponse(responseCode = "204", description = "Employee deleted")
    public Response delete(@PathParam("id") Long id) {
        employeeRepository.delete(id);
        return Response.noContent().build();
    }
}
