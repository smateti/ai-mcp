package com.example.conjur.web.client;

import jakarta.ws.rs.WebApplicationException;

/**
 * Exception that preserves the backend's error response body.
 */
public class BackendException extends WebApplicationException {

    private final int status;
    private final String responseBody;

    public BackendException(int status, String responseBody) {
        super(responseBody, status);
        this.status = status;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return status;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
