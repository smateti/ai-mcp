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
import java.util.List;

@WebServlet("/apps")
public class AppsServlet extends HttpServlet {

    @Inject
    @RestClient
    private ConjurApiClient api;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        List<String> apps = List.of();
        try {
            StatusInfo status = api.getStatus();
            if (status.getKnownApps() != null) {
                apps = status.getKnownApps();
            }
        } catch (Exception e) {
            req.setAttribute("error", e.getMessage());
        }
        req.setAttribute("apps", apps);
        req.getRequestDispatcher("/WEB-INF/views/apps.jsp").forward(req, resp);
    }
}
