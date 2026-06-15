package com.example.gitlabmigration.client;

import com.example.gitlabmigration.model.MetadataCounts;
import com.example.gitlabmigration.model.ProjectInfo;
import com.example.gitlabmigration.model.RefSnapshot;
import com.example.gitlabmigration.util.PathEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;

/**
 * High-level GitLab REST API v4 operations.
 * Provides paginated GET, project lookup, ref fetching, and metadata counts.
 */
@Component
public class GitlabApiClient {

    /**
     * GET every page of a paginated GitLab API collection.
     * Follows the X-Next-Page response header until exhausted.
     */
    public List<JsonNode> getPaginated(RestClient client, String baseUrl,
                                        String path, Map<String, String> params) {
        List<JsonNode> allItems = new ArrayList<>();
        String page = "1";
        Map<String, String> queryParams = new HashMap<>(params != null ? params : Map.of());

        while (page != null) {
            queryParams.put("per_page", "100");
            queryParams.put("page", page);

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl + path);
            queryParams.forEach(uriBuilder::queryParam);
            String uri = uriBuilder.build(false).toUriString();

            ResponseEntity<JsonNode> response = client.get()
                    .uri(URI.create(uri))
                    .retrieve()
                    .toEntity(JsonNode.class);

            JsonNode body = response.getBody();
            if (body != null && body.isArray()) {
                body.forEach(allItems::add);
            }

            List<String> nextPage = response.getHeaders().get("X-Next-Page");
            page = (nextPage != null && !nextPage.isEmpty() && !nextPage.get(0).isBlank())
                    ? nextPage.get(0) : null;

            // Reset query params for next iteration (avoid duplicates)
            queryParams = new HashMap<>(params != null ? params : Map.of());
        }
        return allItems;
    }

    /**
     * Enumerate all projects under a group (or all projects on the instance).
     */
    public List<ProjectInfo> listAllProjects(RestClient client, String baseUrl,
                                              String topLevelGroup) {
        String path;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("archived", "false");
        params.put("simple", "false");
        params.put("order_by", "path");
        params.put("sort", "asc");

        if (topLevelGroup != null && !topLevelGroup.isBlank()) {
            path = "/api/v4/groups/" + PathEncoder.encode(topLevelGroup) + "/projects";
            params.put("include_subgroups", "true");
        } else {
            path = "/api/v4/projects";
        }

        List<JsonNode> raw = getPaginated(client, baseUrl, path, params);
        List<ProjectInfo> projects = new ArrayList<>();
        for (JsonNode p : raw) {
            projects.add(new ProjectInfo(
                    p.get("id").asInt(),
                    p.get("path_with_namespace").asText(),
                    p.get("name").asText(),
                    p.has("default_branch") && !p.get("default_branch").isNull()
                            ? p.get("default_branch").asText() : null,
                    p.path("archived").asBoolean(false)
            ));
        }
        return projects;
    }

    /**
     * Check if a source path is a group or a project.
     * Returns "group", "project", or null if the path exists as neither.
     */
    public String classifyPath(RestClient srcClient, String baseUrl, String path) {
        String encoded = PathEncoder.encode(path);
        try {
            srcClient.get()
                    .uri(URI.create(baseUrl + "/api/v4/groups/" + encoded + "?with_projects=false"))
                    .retrieve()
                    .toBodilessEntity();
            return "group";
        } catch (HttpClientErrorException.NotFound ignored) {
            // Not a group, try project
        }
        try {
            srcClient.get()
                    .uri(URI.create(baseUrl + "/api/v4/projects/" + encoded))
                    .retrieve()
                    .toBodilessEntity();
            return "project";
        } catch (HttpClientErrorException.NotFound ignored) {
            // Not a project either
        }
        return null;
    }

    /**
     * GET a project by full path. Returns the JSON response, or null on 404.
     */
    public JsonNode getProject(RestClient client, String baseUrl, String projectPath) {
        try {
            return client.get()
                    .uri(URI.create(baseUrl + "/api/v4/projects/" + PathEncoder.encode(projectPath)))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    /**
     * Fetch all branch and tag tip commit SHAs for a project.
     * Returns null if the project does not exist (404).
     */
    public RefSnapshot getRefs(RestClient client, String baseUrl, String projectPath) {
        String pid = PathEncoder.encode(projectPath);

        // Check project exists and get default branch
        JsonNode project = getProject(client, baseUrl, projectPath);
        if (project == null) return null;

        String defaultBranch = project.has("default_branch") && !project.get("default_branch").isNull()
                ? project.get("default_branch").asText() : null;

        List<JsonNode> branches = getPaginated(client, baseUrl,
                "/api/v4/projects/" + pid + "/repository/branches", null);
        List<JsonNode> tags = getPaginated(client, baseUrl,
                "/api/v4/projects/" + pid + "/repository/tags", null);

        Map<String, String> branchMap = new LinkedHashMap<>();
        for (JsonNode b : branches) {
            branchMap.put(b.get("name").asText(), b.get("commit").get("id").asText());
        }
        Map<String, String> tagMap = new LinkedHashMap<>();
        for (JsonNode t : tags) {
            tagMap.put(t.get("name").asText(), t.get("commit").get("id").asText());
        }

        return new RefSnapshot(branchMap, tagMap, defaultBranch);
    }

    /**
     * Fetch item COUNTS for issues, MRs, labels, and milestones.
     * Uses per_page=1 and reads the X-Total response header.
     * Returns -1 for a kind if X-Total is not reported.
     */
    public MetadataCounts getMetadataCounts(RestClient client, String baseUrl,
                                             String projectPath) {
        String pid = PathEncoder.encode(projectPath);
        int issues = fetchCount(client, baseUrl, pid, "issues",
                Map.of("state", "all", "scope", "all"));
        int mrs = fetchCount(client, baseUrl, pid, "merge_requests",
                Map.of("state", "all", "scope", "all"));
        int labels = fetchCount(client, baseUrl, pid, "labels", Map.of());
        int milestones = fetchCount(client, baseUrl, pid, "milestones",
                Map.of("state", "all"));

        return new MetadataCounts(issues, mrs, labels, milestones);
    }

    private int fetchCount(RestClient client, String baseUrl, String pid,
                           String subpath, Map<String, String> extraParams) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/api/v4/projects/" + pid + "/" + subpath);
        extraParams.forEach(uriBuilder::queryParam);
        uriBuilder.queryParam("per_page", "1");

        ResponseEntity<JsonNode> response = client.get()
                .uri(URI.create(uriBuilder.build(false).toUriString()))
                .retrieve()
                .toEntity(JsonNode.class);

        List<String> total = response.getHeaders().get("X-Total");
        if (total != null && !total.isEmpty()) {
            try {
                return Integer.parseInt(total.get(0));
            } catch (NumberFormatException ignored) {
                // Fall through
            }
        }
        return -1;
    }
}
