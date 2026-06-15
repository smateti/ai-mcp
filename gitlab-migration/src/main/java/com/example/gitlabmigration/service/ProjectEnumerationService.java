package com.example.gitlabmigration.service;

import com.example.gitlabmigration.client.GitlabApiClient;
import com.example.gitlabmigration.config.MigrationProperties;
import com.example.gitlabmigration.model.ProjectInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates projects to migrate, either from the source GitLab group
 * or from a user-supplied file of paths.
 */
@Service
public class ProjectEnumerationService {

    private static final Logger log = LoggerFactory.getLogger(ProjectEnumerationService.class);

    private final GitlabApiClient apiClient;
    private final MigrationProperties props;

    public ProjectEnumerationService(GitlabApiClient apiClient, MigrationProperties props) {
        this.apiClient = apiClient;
        this.props = props;
    }

    /** Enumerate all projects under the configured top-level group. */
    public List<ProjectInfo> listFromSource(RestClient srcClient) {
        log.info("Enumerating projects under group '{}' on {} ...",
                props.getTopLevelGroup(), props.getSource().getUrl());
        List<ProjectInfo> projects = apiClient.listAllProjects(
                srcClient, props.getSource().getUrl(), props.getTopLevelGroup());
        log.info("Found {} projects on source.", projects.size());
        return projects;
    }

    /**
     * Load project paths from a file (one path_with_namespace per line).
     * Lines starting with # are treated as comments.
     */
    public List<String> loadPathsFromFile(Path file) throws IOException {
        List<String> paths = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                paths.add(trimmed);
            }
        }
        return paths;
    }

    /**
     * Classify and build ProjectInfo records from a file of paths.
     * Each path is checked against the source to determine if it's a group or project.
     */
    public List<ProjectInfo> classifyPathsFromFile(RestClient srcClient, Path file,
                                                     FailureCallback onFailure) throws IOException {
        List<String> wanted = loadPathsFromFile(file);
        List<ProjectInfo> projects = new ArrayList<>();
        for (String path : wanted) {
            String kind = apiClient.classifyPath(srcClient, props.getSource().getUrl(), path);
            if (kind == null) {
                log.error("Path '{}' is neither a group nor a project on source - skipping.", path);
                onFailure.recordFailure(path, "path not found on source as group or project");
                continue;
            }
            if ("group".equals(kind)) {
                log.warn("Path '{}' is a SUBGROUP: it will be imported as a group_entity "
                        + "including ALL nested projects.", path);
            }
            projects.add(new ProjectInfo(0, path, path, null, false, kind));
        }
        log.info("Loaded {} valid paths from {}", projects.size(), file);
        return projects;
    }

    @FunctionalInterface
    public interface FailureCallback {
        void recordFailure(String projectPath, String reason);
    }
}
