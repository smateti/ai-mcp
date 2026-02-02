package com.naagi.rag.graph;

import com.naagi.rag.entity.ExtractedEntity;
import com.naagi.rag.entity.EntityRelationship;
import com.naagi.rag.llm.ChatClient;
import com.naagi.rag.llm.ChatMessage;
import com.naagi.rag.llm.ChatRequest;
import com.naagi.rag.llm.ChatResponse;
import com.naagi.rag.repository.ExtractedEntityRepository;
import com.naagi.rag.repository.EntityRelationshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts entities and relationships from document chunks during ingestion.
 *
 * Uses LLM to identify entities (systems, concepts, people, APIs, processes)
 * and their relationships from text. Results are stored in H2 for graph-augmented retrieval.
 */
@Service
public class EntityExtractionService {

    private static final Logger log = LoggerFactory.getLogger(EntityExtractionService.class);

    private final ChatClient chatClient;
    private final ExtractedEntityRepository entityRepository;
    private final EntityRelationshipRepository relationshipRepository;
    private final boolean enabled;

    private static final String EXTRACTION_PROMPT = """
            Extract entities and relationships from the following text.

            ENTITY TYPES: SYSTEM, CONCEPT, PROCESS, API, PERSON, ORGANIZATION, TECHNOLOGY, METRIC, CONFIG

            OUTPUT FORMAT (one per line):
            ENTITY: <name> | <type> | <brief description>
            REL: <source entity> | <relationship> | <target entity>

            RULES:
            - Only extract clearly mentioned entities (do not infer)
            - Use concise entity names (1-3 words)
            - Relationships should be verbs or short phrases (e.g., "uses", "depends on", "produces")
            - Max 10 entities and 10 relationships per chunk

            Text:
            %s

            Extracted:""";

    // Patterns for parsing LLM output
    private static final Pattern ENTITY_PATTERN =
            Pattern.compile("^ENTITY:\\s*(.+?)\\s*\\|\\s*(.+?)\\s*\\|\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern REL_PATTERN =
            Pattern.compile("^REL:\\s*(.+?)\\s*\\|\\s*(.+?)\\s*\\|\\s*(.+)$", Pattern.MULTILINE);

    public EntityExtractionService(
            ChatClient chatClient,
            ExtractedEntityRepository entityRepository,
            EntityRelationshipRepository relationshipRepository,
            @Value("${naagi.rag.entity-extraction.enabled:false}") boolean enabled) {
        this.chatClient = chatClient;
        this.entityRepository = entityRepository;
        this.relationshipRepository = relationshipRepository;
        this.enabled = enabled;
        log.info("[ENTITY-EXTRACT] Initialized, enabled={}", enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Extract entities and relationships from a document's chunks.
     * Called during document ingestion after chunking.
     */
    public void extractFromChunks(String docId, String categoryId, List<String> chunks) {
        if (!enabled) return;

        log.info("[ENTITY-EXTRACT] Extracting entities from {} chunks for doc={}", chunks.size(), docId);
        long start = System.currentTimeMillis();

        Map<String, ExtractedEntity> entityMap = new HashMap<>();
        List<EntityRelationship> relationships = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            if (chunk.length() < 50) continue;

            try {
                String output = callLlmForExtraction(chunk);
                if (output == null || output.isBlank()) continue;

                // Parse entities
                Matcher entityMatcher = ENTITY_PATTERN.matcher(output);
                while (entityMatcher.find()) {
                    String name = entityMatcher.group(1).trim();
                    String type = entityMatcher.group(2).trim().toUpperCase();
                    String desc = entityMatcher.group(3).trim();

                    String key = name.toLowerCase();
                    if (entityMap.containsKey(key)) {
                        ExtractedEntity existing = entityMap.get(key);
                        existing.setMentionCount(existing.getMentionCount() + 1);
                    } else {
                        entityMap.put(key, ExtractedEntity.builder()
                                .name(name)
                                .entityType(type)
                                .description(desc)
                                .docId(docId)
                                .categoryId(categoryId)
                                .mentionCount(1)
                                .build());
                    }
                }

                // Parse relationships
                Matcher relMatcher = REL_PATTERN.matcher(output);
                while (relMatcher.find()) {
                    String source = relMatcher.group(1).trim();
                    String relType = relMatcher.group(2).trim();
                    String target = relMatcher.group(3).trim();

                    relationships.add(EntityRelationship.builder()
                            .sourceEntityId(source.toLowerCase())
                            .targetEntityId(target.toLowerCase())
                            .relationshipType(relType)
                            .docId(docId)
                            .chunkId(docId + "_chunk_" + i)
                            .build());
                }

            } catch (Exception e) {
                log.warn("[ENTITY-EXTRACT] Failed on chunk {} of doc={}: {}", i, docId, e.getMessage());
            }
        }

        // Persist entities
        List<ExtractedEntity> savedEntities = new ArrayList<>();
        for (ExtractedEntity entity : entityMap.values()) {
            Optional<ExtractedEntity> existing =
                    entityRepository.findByNameIgnoreCaseAndDocId(entity.getName(), docId);
            if (existing.isPresent()) {
                ExtractedEntity e = existing.get();
                e.setMentionCount(e.getMentionCount() + entity.getMentionCount());
                savedEntities.add(entityRepository.save(e));
            } else {
                savedEntities.add(entityRepository.save(entity));
            }
        }

        // Build name→id lookup for relationship persistence
        Map<String, String> nameToId = new HashMap<>();
        for (ExtractedEntity e : savedEntities) {
            nameToId.put(e.getName().toLowerCase(), e.getId());
        }

        // Persist relationships (resolve entity names to IDs)
        int relCount = 0;
        for (EntityRelationship rel : relationships) {
            String sourceId = nameToId.get(rel.getSourceEntityId());
            String targetId = nameToId.get(rel.getTargetEntityId());
            if (sourceId != null && targetId != null) {
                rel.setSourceEntityId(sourceId);
                rel.setTargetEntityId(targetId);
                relationshipRepository.save(rel);
                relCount++;
            }
        }

        long duration = System.currentTimeMillis() - start;
        log.info("[ENTITY-EXTRACT] Extracted {} entities, {} relationships from doc={} ({}ms)",
                savedEntities.size(), relCount, docId, duration);
    }

    private String callLlmForExtraction(String chunk) {
        try {
            String prompt = EXTRACTION_PROMPT.formatted(
                    chunk.substring(0, Math.min(2000, chunk.length())));

            ChatResponse response = chatClient.chat(ChatRequest.of(
                    List.of(
                            ChatMessage.system("You are an entity extraction assistant. Extract entities and relationships from text."),
                            ChatMessage.user(prompt)
                    ),
                    0.1, 512));

            return response.content();
        } catch (Exception e) {
            log.debug("[ENTITY-EXTRACT] LLM call failed: {}", e.getMessage());
            return null;
        }
    }
}
