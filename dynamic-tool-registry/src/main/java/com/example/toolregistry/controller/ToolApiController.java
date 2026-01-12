package com.example.toolregistry.controller;

import com.example.toolregistry.dto.ParsedToolInfo;
import com.example.toolregistry.dto.ToolRegistrationRequest;
import com.example.toolregistry.entity.ToolDefinition;
import com.example.toolregistry.service.ToolRegistrationService;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolApiController {

    private final ToolRegistrationService toolRegistrationService;

    @PostMapping("/register")
    public ResponseEntity<ToolDefinition> registerTool(@RequestBody ToolRegistrationRequest request) {
        try {
            ToolDefinition tool = toolRegistrationService.registerTool(request);
            return ResponseEntity.ok(tool);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/preview")
    public ResponseEntity<ParsedToolInfo> previewTool(@RequestBody ToolRegistrationRequest request) {
        try {
            ParsedToolInfo preview = toolRegistrationService.previewTool(request);
            return ResponseEntity.ok(preview);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ToolDefinition>> getAllTools() {
        return ResponseEntity.ok(toolRegistrationService.getAllTools());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ToolDefinition> getToolById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(toolRegistrationService.getToolById(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/by-tool-id/{toolId}")
    public ResponseEntity<ToolDefinition> getToolByToolId(@PathVariable String toolId) {
        try {
            return ResponseEntity.ok(toolRegistrationService.getToolByToolId(toolId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/description")
    public ResponseEntity<ToolDefinition> updateToolDescription(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            ToolDefinition updated = toolRegistrationService.updateToolDescription(
                    id,
                    request.get("description")
            );
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}/parameters/{parameterName}/description")
    public ResponseEntity<Void> updateParameterDescription(
            @PathVariable Long id,
            @PathVariable String parameterName,
            @RequestBody Map<String, String> request) {
        try {
            toolRegistrationService.updateParameterDescription(
                    id,
                    parameterName,
                    request.get("description")
            );
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTool(@PathVariable Long id) {
        try {
            toolRegistrationService.deleteTool(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/openapi/paths")
    public ResponseEntity<Map<String, Object>> getOpenApiPaths(@RequestParam String openApiUrl) {
        try {
            OpenAPI openAPI = new OpenAPIV3Parser().read(openApiUrl);
            if (openAPI == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Failed to parse OpenAPI specification"));
            }

            Map<String, Object> result = new HashMap<>();
            List<Map<String, Object>> paths = new ArrayList<>();

            if (openAPI.getPaths() != null) {
                openAPI.getPaths().forEach((path, pathItem) -> {
                    Map<String, Object> pathInfo = new HashMap<>();
                    pathInfo.put("path", path);
                    pathInfo.put("methods", getAvailableMethods(pathItem));
                    paths.add(pathInfo);
                });
            }

            result.put("paths", paths);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Error parsing OpenAPI: " + e.getMessage()));
        }
    }

    @GetMapping("/check-duplicate")
    public ResponseEntity<Map<String, Boolean>> checkDuplicate(
            @RequestParam String openApiEndpoint,
            @RequestParam String path,
            @RequestParam String httpMethod) {
        boolean exists = toolRegistrationService.toolExists(openApiEndpoint, path, httpMethod);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    private List<String> getAvailableMethods(PathItem pathItem) {
        List<String> methods = new ArrayList<>();
        if (pathItem.getGet() != null) methods.add("GET");
        if (pathItem.getPost() != null) methods.add("POST");
        if (pathItem.getPut() != null) methods.add("PUT");
        if (pathItem.getDelete() != null) methods.add("DELETE");
        if (pathItem.getPatch() != null) methods.add("PATCH");
        return methods;
    }
}
