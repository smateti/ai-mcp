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
 * Displays the list of registered applications.
 * Landing page of the Build Console.
 */
@WebServlet(urlPatterns = {"/", "/applications"})
public class ApplicationsServlet extends HttpServlet {

    @Inject
    private BuildServiceClient client;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        List<JsonObject> apps = client.getApplications();
        req.setAttribute("applications", apps);
        req.setAttribute("pageTitle", "Applications");
        req.getRequestDispatcher("/WEB-INF/views/applications.jsp").forward(req, resp);
    }
}
