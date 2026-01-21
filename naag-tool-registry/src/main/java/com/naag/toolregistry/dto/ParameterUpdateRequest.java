package com.naag.toolregistry.dto;

import java.util.List;

/**
 * Parameter update request DTO.
 */
public record ParameterUpdateRequest(
        String humanReadableDescription,
        String example,
        List<String> enumValues
) {}
