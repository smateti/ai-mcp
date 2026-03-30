package com.example.propset.rest;

import com.example.propset.model.DatasourceConfig;
import com.example.propset.service.ConfigMapPublisher;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/propset")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class PropSetResource {

    @Inject
    private ConfigMapPublisher publisher;

    @GET
    @Path("/status")
    public Response getStatus() {
        List<DatasourceConfig> datasources = publisher.getDatasources();
        Map<String, String> status = publisher.getPublishStatus();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("datasourceCount", datasources.size());
        response.put("publishStatus", status);

        List<Map<String, Object>> dsInfo = datasources.stream().map(ds -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", ds.getName());
            info.put("description", ds.getDescription());
            info.put("configMapName", ds.getConfigMapName());
            info.put("configMapNamespace", ds.getConfigMapNamespace());
            info.put("properties", ds.getProperties());
            return info;
        }).toList();

        response.put("datasources", dsInfo);
        return Response.ok(response).build();
    }

    @POST
    @Path("/refresh")
    public Response refresh() {
        publisher.loadAndPublish();
        return Response.ok(Map.of(
                "message", "ConfigMaps refreshed",
                "publishStatus", publisher.getPublishStatus()
        )).build();
    }
}
