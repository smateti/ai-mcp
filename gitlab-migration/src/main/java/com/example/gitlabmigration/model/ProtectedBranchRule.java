package com.example.gitlabmigration.model;

/**
 * A protected branch rule saved before lifting protections.
 */
public record ProtectedBranchRule(
        String name,
        Integer pushAccessLevel,
        Integer mergeAccessLevel
) {}
