package com.naag.mcpgateway.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tool {
    private String name;
    private String description;
    private JsonNode inputSchema;
}
