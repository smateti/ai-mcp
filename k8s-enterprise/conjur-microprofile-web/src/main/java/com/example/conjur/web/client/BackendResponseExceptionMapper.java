package com.example.conjur.web.client;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

/**
 * Intercepts error responses from the backend REST client and preserves
 * the response body in the exception message so the proxy can forward it.
 */
public class BackendResponseExceptionMapper implements ResponseExceptionMapper<BackendException> {

    @Override
    public BackendException toThrowable(Response response) {
        int status = response.getStatus();
        String body = null;
        try {
            body = response.readEntity(String.class);
        } catch (Exception ignored) {
        }
        return new BackendException(status, body);
    }

    @Override
    public boolean handles(int status, MultivaluedMap<String, Object> headers) {
        return status >= 400;
    }
}
