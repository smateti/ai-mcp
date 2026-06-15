package com.example.gitlabmigration.model;

/**
 * Counts of issues, merge requests, labels, and milestones for a project.
 * A value of -1 means the instance did not report X-Total.
 */
public record MetadataCounts(
        int issues,
        int mergeRequests,
        int labels,
        int milestones
) {}
