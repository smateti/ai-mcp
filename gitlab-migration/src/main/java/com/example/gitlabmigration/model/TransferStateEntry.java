package com.example.gitlabmigration.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-project state entry in transfer_state.json for resume support.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransferStateEntry(
        String status,             // "finished", "failed", "timeout", "submitted"
        @JsonProperty("bulk_import_id") Integer bulkImportId,
        String note,
        @JsonProperty("updated_at") String updatedAt
) {}
