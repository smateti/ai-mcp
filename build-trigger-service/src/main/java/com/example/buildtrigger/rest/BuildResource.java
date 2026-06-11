package com.example.buildtrigger.rest;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.example.buildtrigger.gitlab.GitLabClient;
import com.example.buildtrigger.model.Application;
import com.example.buildtrigger.model.BuildRecord;
import com.example.buildtrigger.model.BuildRequest;
import com.example.buildtrigger.model.BuildResponse;
import com.example.buildtrigger.model.ErrorResponse;
import com.example.buildtrigger.model.JobInfo;
import com.example.buildtrigger.registry.ApplicationRegistry;
import com.example.buildtrigger.repository.BuildRepository;

@Path("/builds")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class BuildResource {

    private static final Logger LOG = Logger.getLogger(BuildResource.class.getName());

    @Inject
    private GitLabClient gitLabClient;

    @Inject
    private ApplicationRegistry registry;

    @Inject
    private BuildRepository buildRepo;

    @Inject
    @ConfigProperty(name = "gitlab.default.ref", defaultValue = "main")
    private String defaultRef;

    /**
     * Trigger a new pipeline build.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Trigger a pipeline build",
               description = "Looks up the application in the registry, resolves the GitLab trigger project, and starts a new pipeline via the GitLab API.")
    @APIResponse(responseCode = "201", description = "Pipeline triggered",
                 content = @Content(schema = @Schema(implementation = BuildResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
                 content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Application not found",
                 content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response triggerBuild(BuildRequest request) {
        // Validate request
        if (request.getAppName() == null || request.getAppName().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("appName is required", 400))
                    .build();
        }
        if (request.getEnvironment() == null || request.getEnvironment().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("environment is required", 400))
                    .build();
        }

        // Look up the application in the registry
        Application app = registry.findByName(request.getAppName());
        if (app == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(
                            "Application not found: " + request.getAppName(), 404))
                    .build();
        }

        // Validate environment
        if (!app.getEnvironments().contains(request.getEnvironment())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(
                            "Invalid environment '" + request.getEnvironment()
                            + "'. Valid: " + app.getEnvironments(), 400))
                    .build();
        }

        // Resolve the GitLab project ID for this app type's trigger repo
        long projectId = resolveProjectId(app.getType());

        // Trigger the pipeline
        BuildRecord record = gitLabClient.triggerPipeline(
                projectId, request.getAppName(), request.getEnvironment(), defaultRef);
        record.setAppType(app.getType());

        // Persist the build record
        buildRepo.save(record);

        LOG.info("Pipeline " + record.getPipelineId() + " triggered for "
                + request.getAppName() + "/" + request.getEnvironment());

        return Response.status(Response.Status.CREATED)
                .entity(new BuildResponse(record))
                .build();
    }

    /**
     * Get build history for an application.
     */
    @GET
    @Operation(summary = "Get build history",
               description = "Returns recent builds for the given application, optionally filtered by environment.")
    @APIResponse(responseCode = "200", description = "Build history",
                 content = @Content(schema = @Schema(implementation = BuildResponse.class)))
    public List<BuildResponse> getBuilds(
            @Parameter(description = "Application name", required = true)
            @QueryParam("appName") String appName,
            @Parameter(description = "Environment filter (optional)")
            @QueryParam("environment") String environment,
            @Parameter(description = "Max results (default 20)")
            @QueryParam("limit") @DefaultValue("20") int limit) {

        List<BuildRecord> records = buildRepo.findByAppNameAndEnvironment(
                appName, environment, limit);

        return records.stream()
                .map(BuildResponse::new)
                .collect(Collectors.toList());
    }

    /**
     * Get jobs for a specific pipeline.
     */
    @GET
    @Path("/{pipelineId}/jobs")
    @Operation(summary = "Get pipeline jobs",
               description = "Fetches the list of jobs for a given pipeline from GitLab.")
    @APIResponse(responseCode = "200", description = "Job list",
                 content = @Content(schema = @Schema(implementation = JobInfo.class)))
    @APIResponse(responseCode = "404", description = "Pipeline not found locally",
                 content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response getJobs(
            @Parameter(description = "Pipeline ID")
            @PathParam("pipelineId") long pipelineId) {

        BuildRecord record = buildRepo.findByPipelineId(pipelineId);
        if (record == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(
                            "No local record for pipeline " + pipelineId, 404))
                    .build();
        }

        List<JobInfo> jobs = gitLabClient.getJobs(
                record.getGitlabProjectId(), pipelineId);
        return Response.ok(jobs).build();
    }

    /**
     * Refresh the status of a pipeline from GitLab.
     */
    @GET
    @Path("/{pipelineId}/refresh")
    @Operation(summary = "Refresh pipeline status",
               description = "Fetches the latest pipeline status from GitLab and updates the local record.")
    @APIResponse(responseCode = "200", description = "Updated build status",
                 content = @Content(schema = @Schema(implementation = BuildResponse.class)))
    @APIResponse(responseCode = "404", description = "Pipeline not found locally",
                 content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Response refreshStatus(
            @Parameter(description = "Pipeline ID")
            @PathParam("pipelineId") long pipelineId) {

        BuildRecord local = buildRepo.findByPipelineId(pipelineId);
        if (local == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(
                            "No local record for pipeline " + pipelineId, 404))
                    .build();
        }

        try {
            BuildRecord remote = gitLabClient.getPipeline(
                    local.getGitlabProjectId(), pipelineId);
            // Merge remote fields into local record
            local.setStatus(remote.getStatus());
            local.setStartedAt(remote.getStartedAt());
            local.setFinishedAt(remote.getFinishedAt());
            local.setDuration(remote.getDuration());
            buildRepo.update(local);

            return Response.ok(new BuildResponse(local)).build();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to refresh pipeline " + pipelineId, e);
            // Return current local state
            return Response.ok(new BuildResponse(local)).build();
        }
    }

    /* ------------------------------------------------------------------ */

    @Inject
    @ConfigProperty(name = "gitlab.trigger-repo.service", defaultValue = "2")
    private long serviceProjectId;

    @Inject
    @ConfigProperty(name = "gitlab.trigger-repo.batch", defaultValue = "3")
    private long batchProjectId;

    @Inject
    @ConfigProperty(name = "gitlab.trigger-repo.default", defaultValue = "2")
    private long defaultProjectId;

    private long resolveProjectId(String appType) {
        return switch (appType) {
            case "service" -> serviceProjectId;
            case "batch" -> batchProjectId;
            default -> defaultProjectId;
        };
    }
}
