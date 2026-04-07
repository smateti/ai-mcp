package com.example.conjur.it;

import com.example.conjur.model.Employee;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/employees")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface EmployeeResourceClient {

    @GET
    List<Employee> getAll();

    @GET
    @Path("/{id}")
    Response getById(@PathParam("id") Long id);

    @POST
    Response create(Employee employee);

    @PUT
    @Path("/{id}")
    Response update(@PathParam("id") Long id, Employee employee);

    @DELETE
    @Path("/{id}")
    Response delete(@PathParam("id") Long id);
}
