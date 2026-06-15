package com.example.gitlabmigration.model;

/**
 * Lightweight project metadata fetched from the GitLab API.
 */
public record ProjectInfo(
        int id,
        String pathWithNamespace,
        String name,
        String defaultBranch,
        boolean archived,
        String entityType // "project" or "group" (when loaded from --projects-file)
) {
    public ProjectInfo(int id, String pathWithNamespace, String name,
                       String defaultBranch, boolean archived) {
        this(id, pathWithNamespace, name, defaultBranch, archived, "project");
    }
}
