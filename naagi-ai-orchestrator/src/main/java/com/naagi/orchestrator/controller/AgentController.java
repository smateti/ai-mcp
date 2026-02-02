package com.naagi.orchestrator.controller;

import com.naagi.orchestrator.coordinator.AgentSelectionResult;
import com.naagi.orchestrator.coordinator.CoordinatorService;
import com.naagi.orchestrator.entity.AgentSession;
import com.naagi.orchestrator.model.AgentConfig;
import com.naagi.orchestrator.repository.AgentSessionRepository;
import com.naagi.orchestrator.service.AgentExecutor;
import com.naagi.orchestrator.service.AgentStreamExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@CrossOrigin
@Slf4j
public class AgentController {

    private final AgentExecutor agentExecutor;
    private final AgentStreamExecutor agentStreamExecutor;
    private final CoordinatorService coordinatorService;
    private final AgentSessionRepository sessionRepository;

    public AgentController(AgentExecutor agentExecutor,
                           AgentStreamExecutor agentStreamExecutor,
                           CoordinatorService coordinatorService,
                           AgentSessionRepository sessionRepository) {
        this.agentExecutor = agentExecutor;
        this.agentStreamExecutor = agentStreamExecutor;
        this.coordinatorService = coordinatorService;
        this.sessionRepository = sessionRepository;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String categoryId = request.get("categoryId");

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
        }

        log.info("[AGENT-API] Execute request: message='{}', categoryId='{}'", message, categoryId);

        AgentSession session = agentExecutor.execute(message, categoryId);

        return ResponseEntity.ok(Map.of(
                "sessionId", session.getSessionId(),
                "status", session.getStatus().name(),
                "answer", session.getFinalAnswer() != null ? session.getFinalAnswer() : "",
                "totalSteps", session.getTotalSteps(),
                "totalToolCalls", session.getTotalToolCalls(),
                "durationMs", session.getTotalDurationMs(),
                "steps", session.getSteps().stream().map(step -> Map.of(
                        "stepNumber", step.getStepNumber(),
                        "type", step.getType().name(),
                        "toolName", step.getToolName() != null ? step.getToolName() : "",
                        "toolArguments", step.getToolArguments() != null ? step.getToolArguments() : "",
                        "toolResult", step.getToolResult() != null ? step.getToolResult() : "",
                        "durationMs", step.getDurationMs()
                )).toList()
        ));
    }

    @PostMapping(value = "/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeStream(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String categoryId = request.get("categoryId");

        SseEmitter emitter = new SseEmitter(120_000L);

        if (message == null || message.isBlank()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"message\":\"message is required\"}"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        log.info("[AGENT-API] Stream request: message='{}', categoryId='{}'", message, categoryId);

        // Use coordinator to select the right agent, then stream via that agent
        // Note: no conversation context passed here — context is opt-in via reply-to only
        Thread.startVirtualThread(() -> {
            AgentSelectionResult selection = coordinatorService.selectAgent(message, categoryId);
            if (selection.hasAgent()) {
                AgentConfig agent = selection.getSelectedAgent();
                log.info("[AGENT-API] Coordinator selected agent {} ({}) via {}",
                        agent.getAgentId(), agent.getName(), selection.getStrategy());
                // Emit agent_selected SSE event
                try {
                    String selectionData = String.format(
                            "{\"agentId\":\"%s\",\"agentName\":\"%s\",\"strategy\":\"%s\",\"selectionTimeMs\":%d,\"reasoning\":\"%s\"}",
                            agent.getAgentId(), agent.getName(), selection.getStrategy(),
                            selection.getSelectionTimeMs(), selection.getReasoning());
                    emitter.send(SseEmitter.event().name("agent_selected").data(selectionData));
                } catch (Exception e) {
                    log.warn("[AGENT-API] Failed to emit agent_selected event: {}", e.getMessage());
                }
                agentStreamExecutor.executeStream(message, categoryId, null, emitter, agent);
            } else {
                // No agent registered — use global defaults
                log.info("[AGENT-API] No agent found for category {}, using global defaults", categoryId);
                agentStreamExecutor.executeStream(message, categoryId, null, emitter);
            }
        });

        return emitter;
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSession(@PathVariable String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .map(session -> ResponseEntity.ok(Map.of(
                        "sessionId", session.getSessionId(),
                        "status", session.getStatus().name(),
                        "userMessage", session.getUserMessage(),
                        "answer", session.getFinalAnswer() != null ? session.getFinalAnswer() : "",
                        "totalSteps", session.getTotalSteps(),
                        "totalToolCalls", session.getTotalToolCalls(),
                        "durationMs", session.getTotalDurationMs(),
                        "createdAt", session.getCreatedAt().toString(),
                        "completedAt", session.getCompletedAt() != null ? session.getCompletedAt().toString() : "",
                        "steps", session.getSteps().stream().map(step -> Map.of(
                                "stepNumber", step.getStepNumber(),
                                "type", step.getType().name(),
                                "toolName", step.getToolName() != null ? step.getToolName() : "",
                                "toolArguments", step.getToolArguments() != null ? step.getToolArguments() : "",
                                "toolResult", step.getToolResult() != null ? step.getToolResult() : "",
                                "durationMs", step.getDurationMs()
                        )).toList()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
