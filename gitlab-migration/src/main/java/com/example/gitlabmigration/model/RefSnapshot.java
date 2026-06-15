package com.example.gitlabmigration.model;

import java.util.Map;

/**
 * Branch and tag tip commit SHAs for a single project.
 */
public record RefSnapshot(
        Map<String, String> branches,  // branch name -> commit SHA
        Map<String, String> tags,      // tag name -> commit SHA
        String defaultBranch
) {}
