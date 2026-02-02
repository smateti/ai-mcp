package com.naagi.toolregistry.controller;

import com.naagi.toolregistry.entity.*;
import com.naagi.toolregistry.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AgentApiController {

    private final AgentService agentService;

    // ── Agent CRUD ──────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<?> createAgent(@RequestBody AgentDefinition agent) {
        try {
            AgentDefinition created = agentService.createAgent(agent);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> listAgents(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(agentService.listAgents(categoryId, active));
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<?> getAgent(@PathVariable String agentId) {
        return agentService.getAgent(agentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{agentId}")
    public ResponseEntity<?> updateAgent(@PathVariable String agentId, @RequestBody Map<String, Object> updates) {
        try {
            AgentDefinition updated = agentService.updateAgent(agentId, updates);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{agentId}")
    public ResponseEntity<?> deleteAgent(@PathVariable String agentId) {
        try {
            agentService.deleteAgent(agentId);
            return ResponseEntity.ok(Map.of("status", "deleted", "agentId", agentId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Tool Assignment ─────────────────────────────────────────

    @PostMapping("/{agentId}/tools")
    public ResponseEntity<?> assignTool(@PathVariable String agentId, @RequestBody Map<String, Object> request) {
        try {
            String toolId = (String) request.get("toolId");
            String customDescription = (String) request.get("customDescription");
            boolean required = Boolean.TRUE.equals(request.get("required"));

            AgentTool assigned = agentService.assignTool(agentId, toolId, customDescription, required);
            return ResponseEntity.ok(assigned);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{agentId}/tools/{toolId}")
    public ResponseEntity<?> removeTool(@PathVariable String agentId, @PathVariable String toolId) {
        try {
            agentService.removeTool(agentId, toolId);
            return ResponseEntity.ok(Map.of("status", "removed", "toolId", toolId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{agentId}/tools/{toolId}/restrictions")
    public ResponseEntity<?> setToolRestrictions(
            @PathVariable String agentId,
            @PathVariable String toolId,
            @RequestBody List<AgentToolRestriction> restrictions) {
        try {
            AgentTool updated = agentService.setToolRestrictions(agentId, toolId, restrictions);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    // ── Skills ──────────────────────────────────────────────────

    @PostMapping("/{agentId}/skills")
    public ResponseEntity<?> addSkill(@PathVariable String agentId, @RequestBody AgentSkill skill) {
        try {
            AgentSkill added = agentService.addSkill(agentId, skill);
            return ResponseEntity.ok(added);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    @PutMapping("/{agentId}/skills/{skillId}")
    public ResponseEntity<?> updateSkill(
            @PathVariable String agentId,
            @PathVariable Long skillId,
            @RequestBody Map<String, Object> updates) {
        try {
            AgentSkill updated = agentService.updateSkill(agentId, skillId, updates);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{agentId}/skills/{skillId}")
    public ResponseEntity<?> removeSkill(@PathVariable String agentId, @PathVariable Long skillId) {
        try {
            agentService.removeSkill(agentId, skillId);
            return ResponseEntity.ok(Map.of("status", "removed", "skillId", skillId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Pinned Documents ────────────────────────────────────────

    @PostMapping("/{agentId}/pinned-documents")
    public ResponseEntity<?> pinDocument(@PathVariable String agentId, @RequestBody AgentPinnedDocument doc) {
        try {
            AgentPinnedDocument pinned = agentService.pinDocument(agentId, doc);
            return ResponseEntity.ok(pinned);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{agentId}/pinned-documents/{docId}")
    public ResponseEntity<?> unpinDocument(@PathVariable String agentId, @PathVariable String docId) {
        try {
            agentService.unpinDocument(agentId, docId);
            return ResponseEntity.ok(Map.of("status", "unpinned", "docId", docId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Utility ─────────────────────────────────────────────────

    @GetMapping("/{agentId}/card")
    public ResponseEntity<?> getAgentCard(@PathVariable String agentId) {
        try {
            return ResponseEntity.ok(agentService.getAgentCard(agentId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        long count = agentService.listAgents(null, null).size();
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "agentCount", count
        ));
    }
}
