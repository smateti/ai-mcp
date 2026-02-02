package com.naagi.rag.graph;

import com.naagi.rag.entity.ExtractedEntity;
import com.naagi.rag.entity.EntityRelationship;
import com.naagi.rag.repository.ExtractedEntityRepository;
import com.naagi.rag.repository.EntityRelationshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Graph-augmented retrieval service.
 *
 * When a query mentions known entities, traverses entity relationships to find
 * related document IDs and chunks. These are combined with vector search results
 * to improve retrieval coverage for entity-centric queries.
 *
 * Phase 1: H2-backed entity graph (simple but functional).
 * Phase 2: Optional Neo4j integration for production scale.
 */
@Service
public class GraphSearchService {

    private static final Logger log = LoggerFactory.getLogger(GraphSearchService.class);

    private final ExtractedEntityRepository entityRepository;
    private final EntityRelationshipRepository relationshipRepository;
    private final boolean enabled;
    private final int maxHops;

    public GraphSearchService(
            ExtractedEntityRepository entityRepository,
            EntityRelationshipRepository relationshipRepository,
            @Value("${naagi.rag.graph-search.enabled:false}") boolean enabled,
            @Value("${naagi.rag.graph-search.max-hops:2}") int maxHops) {
        this.entityRepository = entityRepository;
        this.relationshipRepository = relationshipRepository;
        this.enabled = enabled;
        this.maxHops = maxHops;
        log.info("[GRAPH-SEARCH] Initialized, enabled={}, maxHops={}", enabled, maxHops);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Find related document IDs by identifying entity mentions in the query
     * and traversing the entity graph.
     *
     * @param query      the user's search query
     * @param categoryId optional category filter
     * @return set of related docIds that should be boosted in retrieval
     */
    public Set<String> findRelatedDocIds(String query, String categoryId) {
        if (!enabled || query == null || query.isBlank()) return Set.of();

        long start = System.currentTimeMillis();

        // Find entities mentioned in the query
        List<ExtractedEntity> matchedEntities = findEntitiesInQuery(query, categoryId);
        if (matchedEntities.isEmpty()) {
            return Set.of();
        }

        log.debug("[GRAPH-SEARCH] Found {} entity matches in query", matchedEntities.size());

        // Traverse relationships to find related entities and their documents
        Set<String> relatedDocIds = new LinkedHashSet<>();
        Set<String> visitedEntityIds = new HashSet<>();

        for (ExtractedEntity entity : matchedEntities) {
            relatedDocIds.add(entity.getDocId());
            traverseGraph(entity.getId(), 0, relatedDocIds, visitedEntityIds);
        }

        long duration = System.currentTimeMillis() - start;
        log.info("[GRAPH-SEARCH] Query matched {} entities, found {} related docs ({}ms)",
                matchedEntities.size(), relatedDocIds.size(), duration);

        return relatedDocIds;
    }

    /**
     * Get entity context for a query — returns a text summary of matched entities
     * and their relationships to augment the LLM context.
     */
    public String getEntityContext(String query, String categoryId) {
        if (!enabled || query == null || query.isBlank()) return "";

        List<ExtractedEntity> matchedEntities = findEntitiesInQuery(query, categoryId);
        if (matchedEntities.isEmpty()) return "";

        StringBuilder context = new StringBuilder();
        context.append("Related entities:\n");

        for (ExtractedEntity entity : matchedEntities) {
            context.append("- ").append(entity.getName())
                    .append(" (").append(entity.getEntityType()).append(")")
                    .append(": ").append(entity.getDescription() != null ? entity.getDescription() : "")
                    .append("\n");

            // Add relationships
            List<EntityRelationship> rels = relationshipRepository.findByEntityId(entity.getId());
            for (EntityRelationship rel : rels) {
                String otherEntityId = rel.getSourceEntityId().equals(entity.getId())
                        ? rel.getTargetEntityId() : rel.getSourceEntityId();
                entityRepository.findById(otherEntityId).ifPresent(other ->
                        context.append("  → ").append(rel.getRelationshipType())
                                .append(" ").append(other.getName()).append("\n"));
            }
        }

        return context.toString().trim();
    }

    private List<ExtractedEntity> findEntitiesInQuery(String query, String categoryId) {
        // Tokenize query into potential entity mentions (2-3 word ngrams + single words)
        String lower = query.toLowerCase();
        String[] words = lower.split("\\s+");

        Set<String> searchTerms = new LinkedHashSet<>();
        // Add 3-grams, 2-grams, and single words
        for (int n = Math.min(3, words.length); n >= 1; n--) {
            for (int i = 0; i <= words.length - n; i++) {
                String ngram = String.join(" ", Arrays.copyOfRange(words, i, i + n));
                // Skip common stop words as standalone terms
                if (n == 1 && isStopWord(ngram)) continue;
                searchTerms.add(ngram);
            }
        }

        List<ExtractedEntity> results = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (String term : searchTerms) {
            if (term.length() < 3) continue;

            List<ExtractedEntity> matches;
            if (categoryId != null && !categoryId.isBlank()) {
                matches = entityRepository.searchByNameAndCategory(term, categoryId);
            } else {
                matches = entityRepository.searchByName(term);
            }

            for (ExtractedEntity match : matches) {
                if (seenIds.add(match.getId())) {
                    results.add(match);
                }
            }

            if (results.size() >= 10) break;
        }

        return results;
    }

    private void traverseGraph(String entityId, int depth, Set<String> docIds, Set<String> visited) {
        if (depth >= maxHops || visited.contains(entityId)) return;
        visited.add(entityId);

        List<EntityRelationship> rels = relationshipRepository.findByEntityId(entityId);
        for (EntityRelationship rel : rels) {
            String neighborId = rel.getSourceEntityId().equals(entityId)
                    ? rel.getTargetEntityId() : rel.getSourceEntityId();

            if (!visited.contains(neighborId)) {
                entityRepository.findById(neighborId).ifPresent(neighbor -> {
                    docIds.add(neighbor.getDocId());
                    traverseGraph(neighbor.getId(), depth + 1, docIds, visited);
                });
            }
        }
    }

    private static boolean isStopWord(String word) {
        return Set.of("the", "a", "an", "is", "are", "was", "were", "be", "been",
                "being", "have", "has", "had", "do", "does", "did", "will", "would",
                "could", "should", "may", "might", "shall", "can", "need", "dare",
                "to", "of", "in", "for", "on", "with", "at", "by", "from", "as",
                "into", "about", "between", "through", "during", "before", "after",
                "and", "but", "or", "nor", "not", "so", "yet", "both", "either",
                "neither", "each", "every", "all", "any", "few", "more", "most",
                "other", "some", "such", "no", "only", "own", "same", "than",
                "too", "very", "just", "because", "if", "when", "where", "how",
                "what", "which", "who", "whom", "this", "that", "these", "those",
                "i", "me", "my", "we", "our", "you", "your", "he", "him", "his",
                "she", "her", "it", "its", "they", "them", "their").contains(word);
    }
}
