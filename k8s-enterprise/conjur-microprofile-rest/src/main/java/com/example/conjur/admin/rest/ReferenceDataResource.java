package com.example.conjur.admin.rest;

import com.example.conjur.admin.service.ReferenceDataRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.Set;

@Path("/refdata")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReferenceDataResource {

    private static final Set<String> VALID_TYPES = Set.of(
            "organizations", "environments", "products", "apptypes", "resourcetypes");

    @Inject
    private ReferenceDataRepository repo;

    @GET
    @Path("/all")
    public Response getAll() {
        return Response.ok(repo.getAll()).build();
    }

    @GET
    @Path("/{type}")
    public Response list(@PathParam("type") String type) {
        if (!VALID_TYPES.contains(type)) {
            return Response.status(400).entity(Map.of("error", "Invalid type: " + type,
                    "validTypes", VALID_TYPES)).build();
        }
        return Response.ok(repo.list(type)).build();
    }

    @POST
    @Path("/{type}")
    public Response create(@PathParam("type") String type, Map<String, String> body) {
        if (!VALID_TYPES.contains(type)) {
            return Response.status(400).entity(Map.of("error", "Invalid type: " + type)).build();
        }
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return Response.status(400).entity(Map.of("error", "name is required")).build();
        }
        String description = body.getOrDefault("description", "");
        String fields = body.getOrDefault("fields", "");
        Map<String, Object> result = repo.create(type, name.trim(), description, fields);
        if (result.containsKey("error")) {
            return Response.status(409).entity(result).build();
        }
        return Response.status(201).entity(result).build();
    }

    @PUT
    @Path("/{type}/{id}")
    public Response update(@PathParam("type") String type, @PathParam("id") long id, Map<String, String> body) {
        if (!VALID_TYPES.contains(type)) {
            return Response.status(400).entity(Map.of("error", "Invalid type: " + type)).build();
        }
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return Response.status(400).entity(Map.of("error", "name is required")).build();
        }
        String description = body.getOrDefault("description", "");
        String fields = body.getOrDefault("fields", "");
        Map<String, Object> result = repo.update(id, name.trim(), description, fields);
        if (result.containsKey("error")) {
            return Response.status(404).entity(result).build();
        }
        return Response.ok(result).build();
    }

    @DELETE
    @Path("/{type}/{id}")
    public Response delete(@PathParam("type") String type, @PathParam("id") long id) {
        if (!VALID_TYPES.contains(type)) {
            return Response.status(400).entity(Map.of("error", "Invalid type: " + type)).build();
        }
        Map<String, Object> result = repo.delete(id);
        if (result.containsKey("error")) {
            return Response.status(404).entity(result).build();
        }
        return Response.ok(result).build();
    }
}
