package com.example.gitlabmigration.model;

import java.util.List;

/**
 * Per-entity result from a bulk import batch.
 */
public record BulkImportResult(
        String sourceFullPath,
        String status,
        List<String> failures
) {}
