package com.example.jms;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;

@Path("/mdb")
@RequestScoped
public class MdbControlResource {

    private static final Logger LOG = Logger.getLogger(MdbControlResource.class.getName());

    @Inject
    private MdbControl mdbControl;

    @POST
    @Path("/pause")
    @Produces(MediaType.APPLICATION_JSON)
    public Response pause() {
        mdbControl.pause();
        LOG.info("MDB consumer PAUSED");
        return Response.ok("{\"status\":\"paused\"}").build();
    }

    @POST
    @Path("/resume")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resume() {
        mdbControl.resume();
        LOG.info("MDB consumer RESUMED");
        return Response.ok("{\"status\":\"resumed\"}").build();
    }

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response status() {
        boolean paused = mdbControl.isPaused();
        return Response.ok("{\"status\":\"" + (paused ? "paused" : "running") + "\"}").build();
    }
}
