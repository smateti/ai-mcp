package com.example.toolregistry;

import com.example.toolregistry.dto.ParsedToolInfo;
import com.example.toolregistry.service.OpenApiParserService;
import org.junit.jupiter.api.Test;

public class OpenApiParserTest {

    @Test
    public void testParseLocalFile() {
        OpenApiParserService parser = new OpenApiParserService();

        // Test with GET endpoint with path parameter
        ParsedToolInfo result1 = parser.parseOpenApiEndpoint(
                "file:///c:/tmp/a.json",
                "/api/propset/list/{propSetId}",
                "GET"
        );

        System.out.println("=== Test 1: GET /api/propset/list/{propSetId} ===");
        System.out.println("Name: " + result1.getName());
        System.out.println("Description: " + result1.getDescription());
        System.out.println("Parameters:");
        result1.getParameters().forEach(p ->
                System.out.println("  - " + p.getName() + " (" + p.getType() + ", " + p.getIn() + ", required: " + p.getRequired() + ")")
        );

        // Test with GET endpoint with query parameter
        ParsedToolInfo result2 = parser.parseOpenApiEndpoint(
                "file:///c:/tmp/a.json",
                "/api/propset/prop/values",
                "GET"
        );

        System.out.println("\n=== Test 2: GET /api/propset/prop/values ===");
        System.out.println("Name: " + result2.getName());
        System.out.println("Parameters:");
        result2.getParameters().forEach(p ->
                System.out.println("  - " + p.getName() + " (" + p.getType() + ", " + p.getIn() + ", required: " + p.getRequired() + ")")
        );

        // Test with POST endpoint with request body
        ParsedToolInfo result3 = parser.parseOpenApiEndpoint(
                "file:///c:/tmp/a.json",
                "/api/propset/save",
                "POST"
        );

        System.out.println("\n=== Test 3: POST /api/propset/save ===");
        System.out.println("Name: " + result3.getName());
        System.out.println("Parameters:");
        result3.getParameters().forEach(p ->
                System.out.println("  - " + p.getName() + " (" + p.getType() + ", " + p.getIn() + ", required: " + p.getRequired() + ")")
        );
    }
}
