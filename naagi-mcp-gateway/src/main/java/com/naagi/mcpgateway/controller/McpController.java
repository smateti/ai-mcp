package com.naagi.mcpgateway.controller;

import com.naagi.mcpgateway.protocol.JsonRpcRequest;
import com.naagi.mcpgateway.protocol.JsonRpcResponse;
import com.naagi.mcpgateway.service.McpProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class McpController {

    private final McpProtocolHandler mcpProtocolHandler;

    @PostMapping("/execute")
    public ResponseEntity<JsonRpcResponse> execute(@RequestBody JsonRpcRequest request) {
        log.info("Received MCP request: method={}", request.getMethod());
        JsonRpcResponse response = mcpProtocolHandler.handleRequest(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "naagi-mcp-gateway"
        ));
    }
}
