package com.example.conjur.rest;

import com.example.conjur.model.Employee;
import com.example.conjur.repository.EmployeeRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/api")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EmployeeResource {

    @Inject
    private EmployeeRepository repo;

    @Inject @ConfigProperty(name = "db.host-name", defaultValue = "not-configured")
    private String dbHost;

    @Inject @ConfigProperty(name = "db.port", defaultValue = "50000")
    private String dbPort;

    @Inject @ConfigProperty(name = "db.username", defaultValue = "not-configured")
    private String dbUsername;

    @Inject @ConfigProperty(name = "db.password", defaultValue = "not-configured")
    private String dbPassword;

    @Inject @ConfigProperty(name = "db.database-name", defaultValue = "not-configured")
    private String dbName;

    @GET @Path("/employees")
    public List<Employee> getAll() { return repo.findAll(); }

    @GET @Path("/employees/{id}")
    public Response getById(@PathParam("id") Long id) {
        return repo.findById(id)
                .map(e -> Response.ok(e).build())
                .orElse(Response.status(404).entity(Map.of("error", "Not found: " + id)).build());
    }

    @POST @Path("/employees")
    public Response create(Employee emp) {
        return Response.status(201).entity(repo.create(emp)).build();
    }

    @PUT @Path("/employees/{id}")
    public Response update(@PathParam("id") Long id, Employee emp) {
        return repo.findById(id).map(existing -> {
            existing.setFirstName(emp.getFirstName());
            existing.setLastName(emp.getLastName());
            existing.setEmail(emp.getEmail());
            existing.setDepartment(emp.getDepartment());
            existing.setSalary(emp.getSalary());
            existing.setHireDate(emp.getHireDate());
            return Response.ok(repo.update(existing)).build();
        }).orElse(Response.status(404).entity(Map.of("error", "Not found: " + id)).build());
    }

    @DELETE @Path("/employees/{id}")
    public Response delete(@PathParam("id") Long id) {
        repo.delete(id);
        return Response.noContent().build();
    }

    @GET @Path("/db/status")
    public Map<String, Object> dbStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("source", "Conjur Init Container (K8s JWT Auth)");
        status.put("db.host-name", dbHost);
        status.put("db.port", dbPort);
        status.put("db.username", dbUsername);
        status.put("db.password", maskPassword(dbPassword));
        status.put("db.database-name", dbName);
        status.put("configured", !"not-configured".equals(dbHost));
        return status;
    }

    @GET @Path("/health/ready")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> ready() {
        return Map.of("status", "UP", "app", "db2-microprofile-app");
    }

    @GET @Path("/health/live")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> live() {
        return Map.of("status", "UP");
    }

    private String maskPassword(String val) {
        if (val == null || val.length() <= 3 || "not-configured".equals(val)) return val;
        return val.substring(0, 2) + "****" + val.substring(val.length() - 1);
    }
}
