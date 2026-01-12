package com.example.mcp.transport;

import com.example.mcp.protocol.JsonRpcRequest;
import com.example.mcp.protocol.JsonRpcResponse;
import com.example.mcp.service.McpProtocolHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Component
public class StdioTransport implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(StdioTransport.class);
    private final ObjectMapper objectMapper;
    private final McpProtocolHandler protocolHandler;

    public StdioTransport(ObjectMapper objectMapper, McpProtocolHandler protocolHandler) {
        this.objectMapper = objectMapper;
        this.protocolHandler = protocolHandler;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("MCP Server started - listening on stdio");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    JsonRpcRequest request = objectMapper.readValue(line, JsonRpcRequest.class);
                    JsonRpcResponse response = protocolHandler.handleRequest(request);

                    String responseJson = objectMapper.writeValueAsString(response);
                    System.out.println(responseJson);
                    System.out.flush();
                } catch (Exception e) {
                    logger.error("Error processing request: {}", line, e);
                    JsonRpcResponse errorResponse = JsonRpcResponse.error(null, -32700, "Parse error");
                    System.out.println(objectMapper.writeValueAsString(errorResponse));
                    System.out.flush();
                }
            }
        }

        logger.info("MCP Server shutting down");
    }
}
