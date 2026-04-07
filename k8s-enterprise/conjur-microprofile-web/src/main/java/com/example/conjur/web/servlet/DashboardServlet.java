package com.example.conjur.web.servlet;

import com.example.conjur.web.client.ConjurApiClient;
import com.example.conjur.web.client.model.StatusInfo;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.IOException;

@WebServlet("")
public class DashboardServlet extends HttpServlet {

    @Inject
    @RestClient
    private ConjurApiClient api;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            StatusInfo status = api.getStatus();
            req.setAttribute("status", status);
            req.setAttribute("dbCount", status.getKnownDatabases() != null ? status.getKnownDatabases().size() : 0);
            req.setAttribute("secretCount", status.getKnownSecrets() != null ? status.getKnownSecrets().size() : 0);
            req.setAttribute("appCount", status.getKnownApps() != null ? status.getKnownApps().size() : 0);
        } catch (Exception e) {
            req.setAttribute("error", e.getMessage());
            req.setAttribute("status", new StatusInfo());
            req.setAttribute("dbCount", 0);
            req.setAttribute("secretCount", 0);
            req.setAttribute("appCount", 0);
        }
        req.getRequestDispatcher("/WEB-INF/views/dashboard.jsp").forward(req, resp);
    }
}
