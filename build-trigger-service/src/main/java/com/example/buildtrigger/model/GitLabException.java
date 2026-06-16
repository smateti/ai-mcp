package com.example.buildtrigger.model;

public class GitLabException extends RuntimeException {

    private final int statusCode;

    public GitLabException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public GitLabException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
