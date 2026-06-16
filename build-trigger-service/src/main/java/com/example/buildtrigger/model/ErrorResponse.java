package com.example.buildtrigger.model;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Error response returned when a request fails")
public class ErrorResponse {

    @Schema(description = "Error message describing what went wrong")
    private String error;

    @Schema(description = "HTTP status code")
    private int status;

    public ErrorResponse() {}

    public ErrorResponse(String error, int status) {
        this.error = error;
        this.status = status;
    }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
}
