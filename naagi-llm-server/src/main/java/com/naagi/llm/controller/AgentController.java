package com.naagi.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.llm.model.AgentRequest;
import com.naagi.llm.model.AgentResponse;
import com.naagi.llm.service.AgentExecutorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/v1/agents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
@Tag(name = "Agentic Execution", description = "Server-side ReAct agent loop with tool execution via HTTP endpoints")
public class AgentController {

    private final AgentExecutorService agentExecutorService;
    private final ObjectMapper objectMapper;

    @PostMapping("/execute")
    @Operation(
            summary = "Execute agentic chat",
            description = "Run a ReAct agent loop. Provide a user message and tools with HTTP endpoints. "
                    + "The server calls the LLM, executes tool calls via HTTP, feeds results back, and repeats "
                    + "until the LLM produces a final answer or maxSteps is reached. "
                    + "When stream=true, returns SSE events: session_start, agent_step, tool_result, token, done."
    )
    @ApiResponse(responseCode = "200", description = "Agent response with answer, steps, totalSteps, totalToolCalls")
    @ApiResponse(responseCode = "500", description = "Agent execution failed")
    public Object execute(@RequestBody AgentRequest request) {
        log.info("[AGENT] Execute request: tools={}, stream={}",
                request.tools() != null ? request.tools().size() : 0,
                request.stream());

        if (request.stream()) {
            return streamExecute(request);
        }

        try {
            AgentResponse response = agentExecutorService.execute(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[AGENT] Execution failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private SseEmitter streamExecute(AgentRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        Thread.startVirtualThread(() -> {
            agentExecutorService.executeStream(request, emitter);
        });

        return emitter;
    }
}
