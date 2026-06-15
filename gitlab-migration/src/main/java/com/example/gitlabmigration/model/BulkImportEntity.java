package com.example.gitlabmigration.model;

/**
 * A single entity in a bulk import POST payload.
 */
public record BulkImportEntity(
        String sourceFullPath,
        String destinationSlug,
        String destinationNamespace,
        String sourceType,       // "project_entity" or "group_entity"
        Boolean migrateProjects  // only set for group entities
) {}
