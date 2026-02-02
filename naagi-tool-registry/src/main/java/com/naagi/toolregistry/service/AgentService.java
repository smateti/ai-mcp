package com.naagi.toolregistry.service;

import com.naagi.toolregistry.entity.*;
import com.naagi.toolregistry.repository.AgentDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentService {

    private static final long AGENT_ID_START = 50000L;

    private final AgentDefinitionRepository agentRepository;
    private final AtomicLong agentIdSequence = new AtomicLong(-1);

    // ── Agent CRUD ──────────────────────────────────────────────

    @Transactional
    public AgentDefinition createAgent(AgentDefinition agent) {
        if (agent.getAgentId() == null || agent.getAgentId().isBlank()) {
            agent.setAgentId(generateAgentId());
        }
        if (agent.getSkills() == null) agent.setSkills(new ArrayList<>());
        if (agent.getTools() == null) agent.setTools(new ArrayList<>());
        if (agent.getPinnedDocuments() == null) agent.setPinnedDocuments(new ArrayList<>());

        AgentDefinition saved = agentRepository.save(agent);
        log.info("Created agent: {} ({})", saved.getName(), saved.getAgentId());
        return saved;
    }

    public List<AgentDefinition> listAgents(String categoryId, Boolean active) {
        if (categoryId != null && active != null && active) {
            return agentRepository.findByCategoryIdAndActiveTrue(categoryId);
        }
        if (categoryId != null) {
            return agentRepository.findByCategoryId(categoryId);
        }
        if (active != null && active) {
            return agentRepository.findByActiveTrue();
        }
        return agentRepository.findAll();
    }

    public Optional<AgentDefinition> getAgent(String agentId) {
        return agentRepository.findByAgentId(agentId);
    }

    @Transactional
    public AgentDefinition updateAgent(String agentId, Map<String, Object> updates) {
        AgentDefinition agent = agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        if (updates.containsKey("name")) agent.setName((String) updates.get("name"));
        if (updates.containsKey("description")) agent.setDescription((String) updates.get("description"));
        if (updates.containsKey("role")) agent.setRole((String) updates.get("role"));
        if (updates.containsKey("agentType")) agent.setAgentType((String) updates.get("agentType"));
        if (updates.containsKey("categoryId")) agent.setCategoryId((String) updates.get("categoryId"));
        if (updates.containsKey("endpoint")) agent.setEndpoint((String) updates.get("endpoint"));
        if (updates.containsKey("healthCheckUrl")) agent.setHealthCheckUrl((String) updates.get("healthCheckUrl"));
        if (updates.containsKey("documentAccess")) agent.setDocumentAccess((String) updates.get("documentAccess"));
        if (updates.containsKey("maxSteps")) agent.setMaxSteps(((Number) updates.get("maxSteps")).intValue());
        if (updates.containsKey("parallelToolCalls")) agent.setParallelToolCalls((Boolean) updates.get("parallelToolCalls"));
        if (updates.containsKey("planningEnabled")) agent.setPlanningEnabled((Boolean) updates.get("planningEnabled"));
        if (updates.containsKey("reflectionEnabled")) agent.setReflectionEnabled((Boolean) updates.get("reflectionEnabled"));
        if (updates.containsKey("systemPromptOverride")) agent.setSystemPromptOverride((String) updates.get("systemPromptOverride"));
        if (updates.containsKey("status")) agent.setStatus((String) updates.get("status"));
        if (updates.containsKey("active")) agent.setActive((Boolean) updates.get("active"));

        AgentDefinition saved = agentRepository.save(agent);
        log.info("Updated agent: {}", agentId);
        return saved;
    }

    @Transactional
    public void deleteAgent(String agentId) {
        AgentDefinition agent = agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
        agentRepository.delete(agent);
        log.info("Deleted agent: {}", agentId);
    }

    // ── Tool Assignment ─────────────────────────────────────────

    @Transactional
    public AgentTool assignTool(String agentId, String toolId, String customDescription, boolean required) {
        AgentDefinition agent = agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        // Check if tool already assigned
        boolean alreadyAssigned = agent.getTools().stream()
                .anyMatch(t -> t.getToolId().equals(toolId));
        if (alreadyAssigned) {
            throw new IllegalArgumentException("Tool " + toolId + " already assigned to agent " + agentId);
        }

        AgentTool agentTool = AgentTool.builder()
                .agent(agent)
                .toolId(toolId)
                .customDescription(customDescription)
                .required(required)
                .build();

        agent.getTools().add(agentTool);
        agentRepository.save(agent);
        log.info("Assigned tool {} to agent {}", toolId, agentId);
        return agentTool;
    }

    @Transactional
    public void removeTool(String agentId, String toolId) {
        AgentDefinition agent = agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        boolean removed = agent.getTools().removeIf(t -> t.getToolId().equals(toolId));
        if (!removed) {
            throw new IllegalArgumentException("Tool " + toolId + " not found on agent " + agentId);
        }
        agentRepository.save(agent);
        log.info("Removed tool {} from agent {}", toolId, agentId);
    }

    @Transactional
    public AgentTool setToolRestrictions(String agentId, String toolId, List<AgentToolRestriction> restrictions) {
        AgentDefinition agent = agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        AgentTool agentTool = agent.getTools().stream()
                .filter(t -> t.getToolId().equals(toolId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tool " + toolId + " not assigned to agent " + agentId));

        agentTool.getRestrictions().clear();
        for (AgentToolRestriction r : restrictions) {
            r.setAgentTool(agentTool);
            agentTool.getRestrictions().add(r);
        }

        agentRepository.save(agent);
        log.info("Set {} restrictions on tool {} for agent {}", restrictions.size(), toolId, agentId);
        return agentTool;
    }

    // ── Skills ──────────────────────────────────────────────────

    @Transactional
    public AgentSkill addSkill(String agentId, AgentSkill skill) {
        AgentDefinition agent = agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        skill.setAgent(agent);
        agent.getSkills().add(skill);
        agentRepository.save(agent);
        log.info("Added skill '{}' to agent {}", skill.getName(), agentId);
        return skill;
    }

    @Transactional
    public AgentSkill updateSkill(String agentId, Long skillId, Map<String, Object> updates) {
        AgentDefinition agent = agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        AgentSkill skill = agent.getSkills().stream()
                .filter(s -> s.getId().equals(skillId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));

        if (updates.containsKey("name")) skill.setName((String) updates.get("name"));
        if (updates.containsKey("description")) skill.setDescription((String) updates.get("description"));
        if (updates.containsKey("tags")) skill.setTags((String) updates.get("tags"));
        if (updates.containsKey("examples")) skill.setExamples((String) updates.get("examples"));

        agentRepository.save(agent);
        log.info("Updated skill {} on agent {}", skillId, agentId);
        return skill;
    }

    @Transactional
    public void removeSkill(String agentId, Long skillId) {
        AgentDefinition agent = agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        boolean removed = agent.getSkills().removeIf(s -> s.getId().equals(skillId));
        if (!removed) {
            throw new IllegalArgumentException("Skill not found: " + skillId);
        }
        agentRepository.save(agent);
        log.info("Removed skill {} from agent {}", skillId, agentId);
    }

    // ── Pinned Documents ────────────────────────────────────────

    @Transactional
    public AgentPinnedDocument pinDocument(String agentId, AgentPinnedDocument doc) {
        AgentDefinition agent = agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        doc.setAgent(agent);
        agent.getPinnedDocuments().add(doc);
        agentRepository.save(agent);
        log.info("Pinned document '{}' to agent {}", doc.getDocId(), agentId);
        return doc;
    }

    @Transactional
    public void unpinDocument(String agentId, String docId) {
        AgentDefinition agent = agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        boolean removed = agent.getPinnedDocuments().removeIf(d -> d.getDocId().equals(docId));
        if (!removed) {
            throw new IllegalArgumentException("Document " + docId + " not pinned to agent " + agentId);
        }
        agentRepository.save(agent);
        log.info("Unpinned document {} from agent {}", docId, agentId);
    }

    // ── Agent Card (A2A format) ─────────────────────────────────

    public Map<String, Object> getAgentCard(String agentId) {
        AgentDefinition agent = agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("agentId", agent.getAgentId());
        card.put("name", agent.getName());
        card.put("description", agent.getDescription());
        card.put("agentType", agent.getAgentType());
        card.put("status", agent.getStatus());

        if (agent.getEndpoint() != null) {
            card.put("endpoint", agent.getEndpoint());
        }

        // Skills
        List<Map<String, Object>> skillList = agent.getSkills().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.getName());
            m.put("description", s.getDescription());
            if (s.getTags() != null) m.put("tags", Arrays.asList(s.getTags().split(",")));
            if (s.getExamples() != null) m.put("examples", s.getExamples());
            return m;
        }).toList();
        card.put("skills", skillList);

        // Tools
        List<Map<String, Object>> toolList = agent.getTools().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("toolId", t.getToolId());
            if (t.getCustomDescription() != null) m.put("customDescription", t.getCustomDescription());
            m.put("required", t.isRequired());
            if (!t.getRestrictions().isEmpty()) {
                m.put("restrictions", t.getRestrictions().stream().map(r -> {
                    Map<String, Object> rm = new LinkedHashMap<>();
                    rm.put("paramName", r.getParamName());
                    rm.put("restrictionType", r.getRestrictionType());
                    rm.put("value", r.getValue());
                    return rm;
                }).toList());
            }
            return m;
        }).toList();
        card.put("tools", toolList);

        // Pinned documents
        List<Map<String, Object>> docList = agent.getPinnedDocuments().stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("docId", d.getDocId());
            m.put("docTitle", d.getDocTitle());
            m.put("maxTokens", d.getMaxTokens());
            m.put("priority", d.getPriority());
            return m;
        }).toList();
        card.put("pinnedDocuments", docList);

        return card;
    }

    // ── ID Generation ───────────────────────────────────────────

    private synchronized String generateAgentId() {
        if (agentIdSequence.get() < 0) {
            Long maxId = agentRepository.findAll().stream()
                    .map(AgentDefinition::getAgentId)
                    .filter(id -> id != null && id.matches("\\d+"))
                    .mapToLong(Long::parseLong)
                    .max()
                    .orElse(AGENT_ID_START - 1);
            agentIdSequence.set(maxId);
            log.info("Initialized agent ID sequence to: {}", agentIdSequence.get());
        }
        return String.valueOf(agentIdSequence.incrementAndGet());
    }
}
