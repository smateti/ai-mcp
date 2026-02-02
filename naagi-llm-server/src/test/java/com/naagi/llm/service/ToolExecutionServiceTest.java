package com.naagi.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionServiceTest {

    private static HttpServer httpServer;
    private static int port;
    private ToolExecutionService service;

    @BeforeAll
    static void startServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = httpServer.getAddress().getPort();

        httpServer.createContext("/api/search", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String response = "{\"query\":\"" + (query != null ? query : "") + "\",\"results\":[]}";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        httpServer.createContext("/api/items/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1);
            String response = "{\"id\":\"" + id + "\",\"name\":\"item\"}";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        httpServer.createContext("/api/create", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String response = "{\"created\":true,\"body\":\"" + new String(body).replace("\"", "'") + "\"}";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        httpServer.createContext("/api/error", exchange -> {
            String response = "Internal Server Error";
            exchange.sendResponseHeaders(500, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        httpServer.start();
    }

    @AfterAll
    static void stopServer() {
        if (httpServer != null) httpServer.stop(0);
    }

    @BeforeEach
    void setUp() {
        service = new ToolExecutionService(new ObjectMapper());
    }

    @Test
    void executeTool_getWithQueryParams() {
        String result = service.executeTool(
                "http://localhost:" + port + "/api/search",
                "GET",
                "{\"containerName\":\"app-inv\",\"size\":10}");

        assertTrue(result.contains("containerName=app-inv"));
        assertTrue(result.contains("size=10"));
    }

    @Test
    void executeTool_getWithPathParams() {
        String result = service.executeTool(
                "http://localhost:" + port + "/api/items/{id}",
                "GET",
                "{\"id\":\"abc-123\"}");

        assertTrue(result.contains("\"id\":\"abc-123\""));
    }

    @Test
    void executeTool_postWithJsonBody() {
        String result = service.executeTool(
                "http://localhost:" + port + "/api/create",
                "POST",
                "{\"name\":\"test\",\"value\":42}");

        assertTrue(result.contains("created"));
    }

    @Test
    void executeTool_httpError_returnsErrorString() {
        String result = service.executeTool(
                "http://localhost:" + port + "/api/error",
                "GET",
                "{}");

        assertTrue(result.startsWith("ERROR:"));
    }

    @Test
    void executeTool_nullArgs_sendsEmptyParams() {
        String result = service.executeTool(
                "http://localhost:" + port + "/api/search",
                "GET",
                null);

        assertNotNull(result);
        assertFalse(result.startsWith("ERROR"));
    }

    @Test
    void executeTool_blankArgs_sendsEmptyParams() {
        String result = service.executeTool(
                "http://localhost:" + port + "/api/search",
                "GET",
                "  ");

        assertNotNull(result);
    }

    @Test
    void executeTool_connectionRefused_returnsError() {
        String result = service.executeTool(
                "http://localhost:1/api/nothing",
                "GET",
                "{}");

        assertTrue(result.startsWith("ERROR:"));
    }

    @Test
    void executeTool_getSkipsBlankValues() {
        String result = service.executeTool(
                "http://localhost:" + port + "/api/search",
                "GET",
                "{\"name\":\"test\",\"empty\":\"\"}");

        assertTrue(result.contains("name=test"));
        assertFalse(result.contains("empty="));
    }
}
