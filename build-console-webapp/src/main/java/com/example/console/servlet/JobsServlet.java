package com.example.console.servlet;

import java.io.IOException;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.example.console.client.BuildServiceClient;

/**
 * Displays the jobs for a given pipeline.
 */
@WebServlet(urlPatterns = "/jobs")
public class JobsServlet extends HttpServlet {

    @Inject
    private BuildServiceClient client;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String pipelineIdStr = req.getParameter("pipelineId");
        String appName = req.getParameter("appName");

        if (pipelineIdStr == null || pipelineIdStr.isBlank()) {
            resp.sendRedirect(req.getContextPath() + "/");
            return;
        }

        long pipelineId = Long.parseLong(pipelineIdStr);
        List<JsonObject> jobs = client.getJobs(pipelineId);

        req.setAttribute("pipelineId", pipelineId);
        req.setAttribute("appName", appName);
        req.setAttribute("jobs", jobs);
        req.setAttribute("pageTitle", "Pipeline " + pipelineId + " — Jobs");
        req.getRequestDispatcher("/WEB-INF/views/jobs.jsp").forward(req, resp);
    }
}
