package com.example.test4.rest;

import com.example.test4.ejb.Employee;
import com.example.test4.ejb.EmployeeRemote;

import javax.ejb.EJB;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.StringReader;
import java.util.List;

/**
 * REST endpoint that wraps the EmployeeBean EJB.
 * Allows test5web (on a separate Liberty server) to call
 * test4ejb over HTTP instead of IIOP/CORBA.
 */
@Path("/employees")
public class EmployeeResource {

    @EJB
    private EmployeeRemote employeeService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllEmployees() {
        List<Employee> employees = employeeService.findAllEmployees();
        JsonArrayBuilder array = Json.createArrayBuilder();
        for (Employee emp : employees) {
            array.add(toJson(emp));
        }
        return Response.ok(array.build().toString()).build();
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEmployee(@PathParam("id") int id) {
        Employee emp = employeeService.findEmployee(id);
        if (emp == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Employee not found\"}").build();
        }
        return Response.ok(toJson(emp).toString()).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createEmployee(String body) {
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            JsonObject json = reader.readObject();
            String firstName = json.getString("firstName");
            String lastName = json.getString("lastName");
            String department = json.getString("department", "");
            double salary = json.getJsonNumber("salary").doubleValue();
            employeeService.createEmployee(firstName, lastName, department, salary);
            return Response.status(Response.Status.CREATED)
                    .entity("{\"message\":\"Employee created\"}").build();
        }
    }

    @DELETE
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteEmployee(@PathParam("id") int id) {
        employeeService.deleteEmployee(id);
        return Response.ok("{\"message\":\"Employee deleted\"}").build();
    }

    private JsonObject toJson(Employee emp) {
        return Json.createObjectBuilder()
                .add("id", emp.getId())
                .add("firstName", emp.getFirstName())
                .add("lastName", emp.getLastName())
                .add("department", emp.getDepartment() != null ? emp.getDepartment() : "")
                .add("salary", emp.getSalary())
                .build();
    }
}
