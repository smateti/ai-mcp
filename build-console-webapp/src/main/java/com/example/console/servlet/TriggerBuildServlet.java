package com.example.console.servlet;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.example.console.client.BuildServiceClient;

/**
 * Handles POST to trigger a new pipeline build,
 * then redirects to the build history page.
 */
@WebServlet(urlPatterns = "/trigger")
public class TriggerBuildServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(TriggerBuildServlet.class.getName());

    @Inject
    private BuildServiceClient client;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String appName = req.getParameter("appName");
        String environment = req.getParameter("environment");
        String gitlabInstance = req.getParameter("gitlabInstance");

        try {
            JsonObject result = client.triggerBuild(appName, environment, gitlabInstance);
            long pipelineId = result.getJsonNumber("pipelineId").longValue();
            String instance = result.getString("gitlabInstance", "source");
            LOG.info("Triggered pipeline " + pipelineId + " on " + instance
                    + " for " + appName + "/" + environment);
            req.getSession().setAttribute("flashMessage",
                    "Pipeline " + pipelineId + " triggered on "
                    + instance.toUpperCase() + " for " + appName + " (" + environment + ")");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to trigger build", e);
            req.getSession().setAttribute("flashError",
                    "Failed to trigger build: " + e.getMessage());
        }

        resp.sendRedirect(req.getContextPath() + "/history?appName=" + appName);
    }
}
