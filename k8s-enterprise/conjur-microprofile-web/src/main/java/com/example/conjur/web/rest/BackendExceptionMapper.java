package com.example.conjur.web.rest;

import com.example.conjur.web.client.BackendException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Catches BackendException thrown by the MicroProfile REST Client
 * (via BackendResponseExceptionMapper) and forwards the backend's
 * error response body to the browser.
 */
@Provider
public class BackendExceptionMapper implements ExceptionMapper<BackendException> {

    @Override
    public Response toResponse(BackendException ex) {
        int status = ex.getStatusCode();
        String body = ex.getResponseBody();

        if (body != null && !body.isEmpty()) {
            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(body)
                    .build();
        }

        String message = ex.getMessage() != null ? ex.getMessage() : "Backend error";
        String json = "{\"error\":\"" + message.replace("\"", "\\\"") + "\"}";
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(json)
                .build();
    }
}
