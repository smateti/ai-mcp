package com.example.mcp.controller;

import com.example.mcp.model.Tool;
import com.example.mcp.service.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Slf4j
public class McpTestController {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @GetMapping("/test")
    public String showTestPage(Model model) {
        List<Tool> tools = toolRegistry.getAllTools();
        model.addAttribute("tools", tools);
        return "mcp/test";
    }

    /**
     * Refresh tools from the dynamic tool registry
     */
    @PostMapping("/refresh-tools")
    @ResponseBody
    public Map<String, Object> refreshTools() {
        try {
            log.info("Refreshing tools from registry");
            toolRegistry.refreshToolsFromRegistry();
            List<Tool> tools = toolRegistry.getAllTools();
            return Map.of(
                "success", true,
                "message", "Tools refreshed successfully",
                "toolCount", tools.size()
            );
        } catch (Exception e) {
            log.error("Failed to refresh tools", e);
            return Map.of(
                "success", false,
                "message", "Failed to refresh tools: " + e.getMessage()
            );
        }
    }

    @PostMapping("/execute")
    @ResponseBody
    public Map<String, Object> executeJsonRpc(@RequestBody Map<String, Object> request) {
        try {
            String method = (String) request.get("method");
            Map<String, Object> params = (Map<String, Object>) request.get("params");
            Object id = request.get("id");

            log.info("Executing MCP method: {} with params: {}", method, params);

            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            switch (method) {
                case "initialize":
                    response.put("result", handleInitialize(params));
                    break;
                case "tools/list":
                    response.put("result", handleToolsList());
                    break;
                case "tools/call":
                    response.put("result", handleToolsCall(params));
                    break;
                default:
                    response.put("error", Map.of(
                        "code", -32601,
                        "message", "Method not found: " + method
                    ));
            }

            return response;

        } catch (Exception e) {
            log.error("Error executing JSON-RPC request", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("jsonrpc", "2.0");
            errorResponse.put("id", request.get("id"));
            errorResponse.put("error", Map.of(
                "code", -32603,
                "message", "Internal error: " + e.getMessage()
            ));
            return errorResponse;
        }
    }

    private Map<String, Object> handleInitialize(Map<String, Object> params) {
        return Map.of(
            "protocolVersion", "2024-11-05",
            "capabilities", Map.of(
                "tools", Map.of("listChanged", true)
            ),
            "serverInfo", Map.of(
                "name", "mcp-spring-boot-server",
                "version", "1.0.0"
            )
        );
    }

    private Map<String, Object> handleToolsList() {
        List<Tool> tools = toolRegistry.getAllTools();
        List<Map<String, Object>> toolList = tools.stream()
            .map(tool -> {
                Map<String, Object> toolMap = new HashMap<>();
                toolMap.put("name", tool.getName());
                toolMap.put("description", tool.getDescription());
                toolMap.put("inputSchema", tool.getInputSchema());
                return toolMap;
            })
            .toList();

        return Map.of("tools", toolList);
    }

    private Map<String, Object> handleToolsCall(Map<String, Object> params) {
        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        try {
            JsonNode argumentsNode = objectMapper.valueToTree(arguments);
            Object result = toolRegistry.executeTool(toolName, argumentsNode);

            List<Map<String, Object>> content = List.of(
                Map.of(
                    "type", "text",
                    "text", objectMapper.writeValueAsString(result)
                )
            );

            return Map.of("content", content);

        } catch (Exception e) {
            log.error("Error executing tool: {}", toolName, e);
            throw new RuntimeException("Tool execution failed: " + e.getMessage());
        }
    }

    @GetMapping("/tool/{toolName}/schema")
    @ResponseBody
    public Map<String, Object> getToolSchema(@PathVariable String toolName) {
        List<Tool> tools = toolRegistry.getAllTools();
        Tool tool = tools.stream()
            .filter(t -> t.getName().equals(toolName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + toolName));

        Map<String, Object> response = new HashMap<>();
        response.put("name", tool.getName());
        response.put("description", tool.getDescription());
        response.put("inputSchema", tool.getInputSchema());
        return response;
    }
}
