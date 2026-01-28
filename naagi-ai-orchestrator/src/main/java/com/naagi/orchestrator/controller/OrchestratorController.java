package com.naagi.orchestrator.controller;

import com.naagi.orchestrator.model.OrchestrationRequest;
import com.naagi.orchestrator.model.OrchestrationResponse;
import com.naagi.orchestrator.service.OrchestrationService;
import com.naagi.orchestrator.service.StreamingOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class OrchestratorController {

    private final OrchestrationService orchestrationService;
    private final StreamingOrchestrationService streamingOrchestrationService;

    @PostMapping("/orchestrate")
    public ResponseEntity<OrchestrationResponse> orchestrate(@RequestBody OrchestrationRequest request) {
        log.info("Received orchestration request: {}", request.getMessage());
        OrchestrationResponse response = orchestrationService.orchestrate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Streaming orchestration endpoint - streams response tokens
     */
    @PostMapping(value = "/orchestrate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter orchestrateStream(@RequestBody OrchestrationRequest request) {
        log.info("Received streaming orchestration request: {}", request.getMessage());
        SseEmitter emitter = new SseEmitter(120000L); // 2 minute timeout

        new Thread(() -> {
            streamingOrchestrationService.orchestrateStream(request, emitter);
        }).start();

        return emitter;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "naagi-ai-orchestrator"
        ));
    }
}
