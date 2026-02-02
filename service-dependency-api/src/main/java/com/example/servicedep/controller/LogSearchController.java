package com.example.servicedep.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
@Tag(name = "Application Logs", description = "Search application logs stored in Elasticsearch")
public class LogSearchController {

    private static final String ES_URL = "http://localhost:9200";
    private static final String INDEX = "app-logs";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @GetMapping("/search")
    @Operation(
            summary = "Search application logs",
            description = "Search the app-logs Elasticsearch index by containerName, namespace, level, correlationId, or free-text query. "
                    + "Returns matching log entries sorted by timestamp descending. All parameters are optional filters."
    )
    @ApiResponse(responseCode = "200", description = "Log entries matching the search criteria")
    @ApiResponse(responseCode = "500", description = "Elasticsearch query failed")
    public ResponseEntity<String> searchLogs(
            @Parameter(description = "Container/application name to filter by (e.g. app-inventory)")
            @RequestParam(required = false) String containerName,

            @Parameter(description = "Kubernetes namespace to filter by (e.g. app-lambda-inventory)")
            @RequestParam(required = false) String namespace,

            @Parameter(description = "Log level to filter by (INFO, DEBUG, ERROR, WARN)")
            @RequestParam(required = false) String level,

            @Parameter(description = "Correlation ID (UUID) to trace requests across services")
            @RequestParam(required = false) String correlationId,

            @Parameter(description = "Free-text search query to match against log messages")
            @RequestParam(required = false) String query,

            @Parameter(description = "Maximum number of results to return (default 20)")
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        try {
            String esQuery = buildQuery(containerName, namespace, level, correlationId, query, size);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ES_URL + "/" + INDEX + "/_search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(esQuery))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(resp.body());

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private String buildQuery(String containerName, String namespace, String level,
                              String correlationId, String query, int size) {
        List<String> filters = new ArrayList<>();

        if (containerName != null && !containerName.isBlank()) {
            filters.add("{\"term\": {\"containerName\": \"" + escapeJson(containerName) + "\"}}");
        }
        if (namespace != null && !namespace.isBlank()) {
            filters.add("{\"term\": {\"namespace\": \"" + escapeJson(namespace) + "\"}}");
        }
        if (level != null && !level.isBlank()) {
            filters.add("{\"term\": {\"level\": \"" + escapeJson(level.toUpperCase()) + "\"}}");
        }
        if (correlationId != null && !correlationId.isBlank()) {
            filters.add("{\"term\": {\"correlationId\": \"" + escapeJson(correlationId) + "\"}}");
        }
        if (query != null && !query.isBlank()) {
            filters.add("{\"match\": {\"message\": \"" + escapeJson(query) + "\"}}");
        }

        String queryBody;
        if (filters.isEmpty()) {
            queryBody = "{\"match_all\": {}}";
        } else if (filters.size() == 1) {
            queryBody = filters.get(0);
        } else {
            queryBody = "{\"bool\": {\"must\": [" + String.join(",", filters) + "]}}";
        }

        return "{\"query\": " + queryBody + ", \"size\": " + size
                + ", \"sort\": [{\"timestamp\": {\"order\": \"desc\"}}]"
                + ", \"_source\": [\"containerName\", \"namespace\", \"message\", \"timestamp\", \"level\", \"correlationId\", \"serviceName\", \"environment\"]}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
