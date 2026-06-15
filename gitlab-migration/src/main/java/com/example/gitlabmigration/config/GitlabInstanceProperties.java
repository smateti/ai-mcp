package com.example.gitlabmigration.config;

import jakarta.validation.constraints.NotBlank;

/**
 * Connection details for a single GitLab instance (source or destination).
 */
public class GitlabInstanceProperties {

    @NotBlank
    private String url;

    @NotBlank
    private String token;

    public String getUrl() { return url; }

    public void setUrl(String url) {
        // Strip trailing slash, matching the Python's cfg[side]["url"].rstrip("/")
        this.url = url != null ? url.replaceAll("/+$", "") : null;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
