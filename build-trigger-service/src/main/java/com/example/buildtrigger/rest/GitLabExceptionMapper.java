package com.example.buildtrigger.rest;

import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import com.example.buildtrigger.model.ErrorResponse;
import com.example.buildtrigger.model.GitLabException;

/**
 * Maps GitLabException to a structured JSON error response.
 */
@Provider
public class GitLabExceptionMapper implements ExceptionMapper<GitLabException> {

    private static final Logger LOG = Logger.getLogger(GitLabExceptionMapper.class.getName());

    @Override
    public Response toResponse(GitLabException ex) {
        LOG.log(Level.WARNING, "GitLab API error: {0} (status {1})",
                new Object[]{ex.getMessage(), ex.getStatusCode()});

        int status = mapStatus(ex.getStatusCode());
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(ex.getMessage(), status))
                .build();
    }

    private int mapStatus(int gitlabStatus) {
        return switch (gitlabStatus) {
            case 401, 403 -> 502; // upstream auth issue → bad gateway
            case 404 -> 404;
            case 429 -> 503; // rate limited → service unavailable
            default -> gitlabStatus >= 500 ? 502 : 500;
        };
    }
}
