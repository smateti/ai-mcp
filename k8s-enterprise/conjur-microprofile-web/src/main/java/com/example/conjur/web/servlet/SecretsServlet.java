package com.example.conjur.web.servlet;

import com.example.conjur.web.client.ConjurApiClient;
import com.example.conjur.web.client.model.SecretEntry;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.IOException;
import java.util.List;

@WebServlet("/secrets")
public class SecretsServlet extends HttpServlet {

    @Inject
    @RestClient
    private ConjurApiClient api;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        List<SecretEntry> secrets = List.of();
        try {
            secrets = api.listSecrets();
        } catch (Exception e) {
            req.setAttribute("error", e.getMessage());
        }
        req.setAttribute("secrets", secrets);
        req.getRequestDispatcher("/WEB-INF/views/secrets.jsp").forward(req, resp);
    }
}
