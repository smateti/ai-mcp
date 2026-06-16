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
 * Displays build history for a given application.
 */
@WebServlet(urlPatterns = "/history")
public class HistoryServlet extends HttpServlet {

    @Inject
    private BuildServiceClient client;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String appName = req.getParameter("appName");
        String environment = req.getParameter("environment");

        if (appName == null || appName.isBlank()) {
            resp.sendRedirect(req.getContextPath() + "/");
            return;
        }

        List<JsonObject> builds = client.getBuilds(appName, environment, 50);

        req.setAttribute("appName", appName);
        req.setAttribute("environment", environment);
        req.setAttribute("builds", builds);
        req.setAttribute("pageTitle", "Build History — " + appName);

        // Consume flash messages from session
        if (req.getSession().getAttribute("flashMessage") != null) {
            req.setAttribute("flashMessage", req.getSession().getAttribute("flashMessage"));
            req.getSession().removeAttribute("flashMessage");
        }
        if (req.getSession().getAttribute("flashError") != null) {
            req.setAttribute("flashError", req.getSession().getAttribute("flashError"));
            req.getSession().removeAttribute("flashError");
        }

        req.getRequestDispatcher("/WEB-INF/views/history.jsp").forward(req, resp);
    }
}
