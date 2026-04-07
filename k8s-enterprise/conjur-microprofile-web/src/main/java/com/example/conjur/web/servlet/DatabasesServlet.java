package com.example.conjur.web.servlet;

import com.example.conjur.web.client.ConjurApiClient;
import com.example.conjur.web.client.model.DbCredentials;
import com.example.conjur.web.client.model.StatusInfo;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/databases")
public class DatabasesServlet extends HttpServlet {

    @Inject
    @RestClient
    private ConjurApiClient api;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        List<Map<String, String>> databases = new ArrayList<>();
        try {
            StatusInfo status = api.getStatus();
            if (status.getKnownDatabases() != null) {
                for (String db : status.getKnownDatabases()) {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("name", db);
                    try {
                        DbCredentials creds = api.getDbCredentials(db);
                        entry.put("username", creds.getUsername());
                        entry.put("password", creds.getPassword());
                        entry.put("status", "active");
                    } catch (Exception e) {
                        entry.put("username", "");
                        entry.put("password", "");
                        entry.put("status", "error");
                        entry.put("error", e.getMessage());
                    }
                    databases.add(entry);
                }
            }
        } catch (Exception e) {
            req.setAttribute("error", e.getMessage());
        }
        req.setAttribute("databases", databases);
        req.getRequestDispatcher("/WEB-INF/views/databases.jsp").forward(req, resp);
    }
}
