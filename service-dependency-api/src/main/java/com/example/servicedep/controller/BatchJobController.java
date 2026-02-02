package com.example.servicedep.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

@RestController
@RequestMapping("/api/batch-jobs")
@Tag(name = "Batch Job Runs", description = "Query batch job run instances from application logs")
public class BatchJobController {

    private static final String ES_URL = "http://localhost:9200";
    private static final String INDEX = "app-logs";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GetMapping("/{appName}/runs")
    @Operation(
            summary = "List batch job run instances",
            description = "Returns all job run instances (correlation IDs) for a batch application within a time range. "
                    + "Each run includes the job instance ID (correlation ID), start time, end time, and log count."
    )
    @ApiResponse(responseCode = "200", description = "List of job run instances")
    @ApiResponse(responseCode = "500", description = "Elasticsearch query failed")
    public ResponseEntity<String> getJobRuns(
            @Parameter(description = "Container/application name (e.g. app-inventory)")
            @PathVariable String appName,

            @Parameter(description = "Start of time range in ISO 8601 format (e.g. 2026-01-30T00:00:00Z)")
            @RequestParam String startTime,

            @Parameter(description = "End of time range in ISO 8601 format (e.g. 2026-01-31T00:00:00Z)")
            @RequestParam String endTime
    ) {
        try {
            String esQuery = "{\"size\": 0, \"query\": {\"bool\": {\"must\": ["
                    + "{\"term\": {\"containerName\": \"" + escapeJson(appName) + "\"}},"
                    + "{\"range\": {\"timestamp\": {\"gte\": \"" + escapeJson(startTime) + "\", \"lte\": \"" + escapeJson(endTime) + "\"}}}"
                    + "]}}, \"aggs\": {\"job_runs\": {\"terms\": {\"field\": \"correlationId\", \"size\": 1000, \"order\": {\"start_time\": \"desc\"}},"
                    + "\"aggs\": {\"start_time\": {\"min\": {\"field\": \"timestamp\"}}, \"end_time\": {\"max\": {\"field\": \"timestamp\"}}}}}}";

            HttpResponse<String> resp = queryEs(esQuery);
            JsonNode root = MAPPER.readTree(resp.body());

            ObjectNode result = MAPPER.createObjectNode();
            result.put("appName", appName);

            ArrayNode runs = result.putArray("runs");
            JsonNode buckets = root.at("/aggregations/job_runs/buckets");

            if (buckets.isArray()) {
                result.put("totalRuns", buckets.size());
                for (JsonNode bucket : buckets) {
                    ObjectNode run = runs.addObject();
                    run.put("jobInstanceId", bucket.get("key").asText());
                    run.put("startTime", bucket.at("/start_time/value_as_string").asText());
                    run.put("endTime", bucket.at("/end_time/value_as_string").asText());
                    run.put("logCount", bucket.get("doc_count").asInt());
                }
            } else {
                result.put("totalRuns", 0);
            }

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(MAPPER.writeValueAsString(result));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    @GetMapping("/runs/{jobInstanceId}")
    @Operation(
            summary = "Get batch job run details",
            description = "Returns details of a specific job run by its instance ID (correlation ID). "
                    + "Includes start time, end time, execution status (RUNNING/COMPLETED), "
                    + "result status (SUCCESS/FAIL), and all log entries."
    )
    @ApiResponse(responseCode = "200", description = "Job run details with logs")
    @ApiResponse(responseCode = "404", description = "No logs found for this job instance ID")
    @ApiResponse(responseCode = "500", description = "Elasticsearch query failed")
    public ResponseEntity<String> getJobDetails(
            @Parameter(description = "Job instance ID (correlation ID / UUID)")
            @PathVariable String jobInstanceId
    ) {
        try {
            String esQuery = "{\"query\": {\"term\": {\"correlationId\": \"" + escapeJson(jobInstanceId) + "\"}},"
                    + "\"size\": 100, \"sort\": [{\"timestamp\": {\"order\": \"asc\"}}],"
                    + "\"_source\": [\"containerName\", \"namespace\", \"message\", \"timestamp\", \"level\", \"serviceName\", \"environment\"]}";

            HttpResponse<String> resp = queryEs(esQuery);
            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode hits = root.at("/hits/hits");

            if (!hits.isArray() || hits.isEmpty()) {
                return ResponseEntity.status(404)
                        .header("Content-Type", "application/json")
                        .body("{\"error\": \"No logs found for job instance " + escapeJson(jobInstanceId) + "\"}");
            }

            JsonNode firstSource = hits.get(0).get("_source");
            JsonNode lastSource = hits.get(hits.size() - 1).get("_source");

            String startTime = firstSource.get("timestamp").asText();
            String endTime = lastSource.get("timestamp").asText();
            String containerName = firstSource.get("containerName").asText();
            String namespace = firstSource.has("namespace") ? firstSource.get("namespace").asText() : "";

            boolean hasError = false;
            ObjectNode result = MAPPER.createObjectNode();
            result.put("jobInstanceId", jobInstanceId);
            result.put("appName", containerName);
            result.put("namespace", namespace);
            result.put("startTime", startTime);
            result.put("endTime", endTime);

            ArrayNode logs = result.putArray("logs");
            for (JsonNode hit : hits) {
                JsonNode src = hit.get("_source");
                ObjectNode logEntry = logs.addObject();
                logEntry.put("timestamp", src.get("timestamp").asText());
                logEntry.put("level", src.get("level").asText());
                logEntry.put("message", src.get("message").asText());
                if ("ERROR".equals(src.get("level").asText())) {
                    hasError = true;
                }
            }

            result.put("logCount", hits.size());
            result.put("executionStatus", startTime.equals(endTime) ? "RUNNING" : "COMPLETED");
            result.put("resultStatus", hasError ? "FAIL" : "SUCCESS");

            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(MAPPER.writeValueAsString(result));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private HttpResponse<String> queryEs(String query) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ES_URL + "/" + INDEX + "/_search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(query))
                .timeout(Duration.ofSeconds(10))
                .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String escapeJson(String value) {
        if (value == null) return "null";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
