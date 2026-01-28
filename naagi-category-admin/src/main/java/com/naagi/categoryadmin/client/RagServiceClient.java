package com.naagi.categoryadmin.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naagi.categoryadmin.model.RagDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class RagServiceClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RagServiceClient(
            @Value("${naagi.services.rag.url}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public JsonNode ingestDocument(String docId, String text, String categoryId, Map<String, Object> metadata) {
        try {
            Map<String, Object> body = Map.of(
                    "docId", docId,
                    "text", text,
                    "categoryId", categoryId != null ? categoryId : "",
                    "metadata", metadata != null ? metadata : Map.of()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/rag/ingest"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Failed to ingest document: HTTP {}", response.statusCode());
                throw new RuntimeException("Failed to ingest document: " + response.body());
            }
        } catch (Exception e) {
            log.error("Error ingesting document", e);
            throw new RuntimeException("Error ingesting document", e);
        }
    }

    public JsonNode query(String question, String categoryId, int topK) {
        try {
            Map<String, Object> body = Map.of(
                    "question", question,
                    "categoryId", categoryId != null ? categoryId : "",
                    "topK", topK
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/rag/query"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Failed to query RAG: HTTP {}", response.statusCode());
                throw new RuntimeException("Failed to query: " + response.body());
            }
        } catch (Exception e) {
            log.error("Error querying RAG", e);
            throw new RuntimeException("Error querying", e);
        }
    }

    public List<RagDocument> getDocumentsByCategory(String categoryId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/rag/documents?categoryId=" + categoryId))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readValue(response.body(), new TypeReference<>() {});
            } else {
                log.error("Failed to get documents for category {}: HTTP {}", categoryId, response.statusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching documents for category {}", categoryId, e);
            return Collections.emptyList();
        }
    }

    public void deleteDocument(String docId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/rag/documents/" + docId))
                    .timeout(Duration.ofSeconds(30))
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                log.error("Failed to delete document {}: HTTP {}", docId, response.statusCode());
                throw new RuntimeException("Failed to delete document");
            }
        } catch (Exception e) {
            log.error("Error deleting document {}", docId, e);
            throw new RuntimeException("Error deleting document", e);
        }
    }

    public JsonNode getStats() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/rag/stats"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Failed to get RAG stats: HTTP {}", response.statusCode());
                return objectMapper.createObjectNode();
            }
        } catch (Exception e) {
            log.error("Error fetching RAG stats", e);
            return objectMapper.createObjectNode();
        }
    }

    // Enhanced Document Upload API Methods

    public JsonNode uploadDocumentWithPreview(String docId, String title, String content, String categoryId) {
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("docId", docId);
            body.put("title", title != null ? title : "");
            body.put("content", content);
            body.put("categoryId", categoryId != null ? categoryId : "");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/documents/upload"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Failed to upload document: HTTP {} - {}", response.statusCode(), response.body());
                String errorMsg = extractErrorMessage(response.body());
                throw new RuntimeException(errorMsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error uploading document", e);
            throw new RuntimeException("Error uploading document: " + e.getMessage(), e);
        }
    }

    public JsonNode getDocumentUploads(String status) {
        try {
            String url = baseUrl + "/api/documents/uploads";
            if (status != null && !status.isBlank()) {
                url += "?status=" + status;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Failed to get document uploads: HTTP {}", response.statusCode());
                return objectMapper.createArrayNode();
            }
        } catch (Exception e) {
            log.error("Error fetching document uploads", e);
            return objectMapper.createArrayNode();
        }
    }

    public JsonNode getUploadDetails(String uploadId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/documents/uploads/" + uploadId))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Failed to get upload details: HTTP {}", response.statusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Error fetching upload details", e);
            return null;
        }
    }

    public String findUploadIdByDocId(String docId) {
        try {
            JsonNode uploads = getDocumentUploads(null);
            if (uploads != null && uploads.isArray()) {
                for (JsonNode upload : uploads) {
                    if (upload.has("docId") && docId.equals(upload.get("docId").asText())) {
                        return upload.get("id").asText();
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error finding upload by docId {}", docId, e);
            return null;
        }
    }

    public boolean checkTitleExists(String title) {
        try {
            String encodedTitle = java.net.URLEncoder.encode(title, java.nio.charset.StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/documents/check-title?title=" + encodedTitle))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode result = objectMapper.readTree(response.body());
                return result.has("exists") && result.get("exists").asBoolean();
            }
            return false;
        } catch (Exception e) {
            log.error("Error checking title existence for '{}'", title, e);
            return false;
        }
    }

    public JsonNode getDocumentInfo(String docId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/rag/documents/" + docId))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Failed to get document info for {}: HTTP {}", docId, response.statusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("Error fetching document info for {}", docId, e);
            return null;
        }
    }

    public JsonNode getUploadQAPairs(String uploadId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/documents/uploads/" + uploadId + "/qa"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Failed to get Q&A pairs: HTTP {}", response.statusCode());
                return objectMapper.createArrayNode();
            }
        } catch (Exception e) {
            log.error("Error fetching Q&A pairs", e);
            return objectMapper.createArrayNode();
        }
    }

    public JsonNode approveUpload(String uploadId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/documents/uploads/" + uploadId + "/approve"))
                    .timeout(Duration.ofMinutes(10)) // Large documents may take time to re-embed
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("Error approving upload", e);
            throw new RuntimeException("Error approving upload", e);
        }
    }

    public JsonNode retryUpload(String uploadId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/documents/uploads/" + uploadId + "/retry"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("Error retrying upload", e);
            throw new RuntimeException("Error retrying upload", e);
        }
    }

    public void deleteUpload(String uploadId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/documents/uploads/" + uploadId))
                    .timeout(Duration.ofSeconds(30))
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 204) {
                log.error("Failed to delete upload {}: HTTP {}", uploadId, response.statusCode());
                throw new RuntimeException("Failed to delete upload");
            }
        } catch (Exception e) {
            log.error("Error deleting upload {}", uploadId, e);
            throw new RuntimeException("Error deleting upload", e);
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getSseEndpoint() {
        return baseUrl + "/api/documents/events";
    }

    public String getUploadSseEndpoint(String uploadId) {
        return baseUrl + "/api/documents/uploads/" + uploadId + "/events";
    }

    /**
     * Get document count for a specific category.
     */
    public int getDocumentCountForCategory(String categoryId) {
        try {
            List<RagDocument> docs = getDocumentsByCategory(categoryId);
            return docs != null ? docs.size() : 0;
        } catch (Exception e) {
            log.warn("Could not get document count for category {}", categoryId, e);
            return 0;
        }
    }

    /**
     * Get document counts for all categories (returns Map of categoryId -> count).
     */
    public Map<String, Integer> getDocumentCountsByCategory() {
        Map<String, Integer> counts = new java.util.HashMap<>();
        try {
            // Get all documents and count by category
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/rag/documents"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode docs = objectMapper.readTree(response.body());
                if (docs.isArray()) {
                    // Note: Documents don't have categories directly in Qdrant
                    // We need to count based on uploads which have categoryId
                    // For now, return empty - will be populated from uploads
                }
            }
        } catch (Exception e) {
            log.warn("Could not get document counts by category", e);
        }
        return counts;
    }

    /**
     * Get upload counts per category (from document upload history).
     */
    public Map<String, Integer> getUploadCountsByCategory() {
        Map<String, Integer> counts = new java.util.HashMap<>();
        try {
            JsonNode uploads = getDocumentUploads(null);
            if (uploads != null && uploads.isArray()) {
                for (JsonNode upload : uploads) {
                    if (upload.has("categoryId") && !upload.get("categoryId").isNull()) {
                        String catId = upload.get("categoryId").asText();
                        if (!catId.isBlank()) {
                            counts.merge(catId, 1, Integer::sum);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not get upload counts by category", e);
        }
        return counts;
    }

    /**
     * Get total upload count (not filtered by category).
     */
    public int getTotalUploadCount() {
        try {
            JsonNode uploads = getDocumentUploads(null);
            if (uploads != null && uploads.isArray()) {
                return uploads.size();
            }
        } catch (Exception e) {
            log.warn("Could not get total upload count", e);
        }
        return 0;
    }

    /**
     * Get upload count by status.
     */
    public Map<String, Integer> getUploadCountsByStatus() {
        Map<String, Integer> counts = new java.util.HashMap<>();
        try {
            JsonNode uploads = getDocumentUploads(null);
            if (uploads != null && uploads.isArray()) {
                for (JsonNode upload : uploads) {
                    if (upload.has("status") && !upload.get("status").isNull()) {
                        String status = upload.get("status").asText();
                        counts.merge(status, 1, Integer::sum);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not get upload counts by status", e);
        }
        return counts;
    }

    /**
     * Get all documents from RAG (Qdrant), including those not tracked in uploads.
     * Returns a list of document info with docId, chunkCount, and title.
     */
    public JsonNode getAllRagDocuments() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/rag/documents"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            }
        } catch (Exception e) {
            log.warn("Could not get RAG documents", e);
        }
        return objectMapper.createArrayNode();
    }

    /**
     * Get all documents merged: RAG documents + upload metadata.
     * This provides a complete view of all documents in the knowledge base,
     * including uploads that are pending review (not yet in RAG).
     */
    public JsonNode getMergedDocuments(String categoryFilter) {
        try {
            // Get all RAG documents
            JsonNode ragDocs = getAllRagDocuments();

            // Get all uploads for metadata
            JsonNode uploads = getDocumentUploads(null);

            // Build a map of docId -> upload info
            java.util.Map<String, JsonNode> uploadMap = new java.util.HashMap<>();
            if (uploads != null && uploads.isArray()) {
                for (JsonNode upload : uploads) {
                    if (upload.has("docId")) {
                        uploadMap.put(upload.get("docId").asText(), upload);
                    }
                }
            }

            // Track which docIds we've already processed (from RAG)
            java.util.Set<String> processedDocIds = new java.util.HashSet<>();

            // Merge: for each RAG doc, add upload metadata if available
            var arrayNode = objectMapper.createArrayNode();
            if (ragDocs != null && ragDocs.isArray()) {
                for (JsonNode ragDoc : ragDocs) {
                    String docId = ragDoc.has("docId") ? ragDoc.get("docId").asText() : "";
                    processedDocIds.add(docId);

                    var merged = objectMapper.createObjectNode();
                    merged.put("docId", docId);
                    merged.put("chunkCount", ragDoc.has("chunkCount") ? ragDoc.get("chunkCount").asInt() : 0);
                    merged.put("title", ragDoc.has("title") ? ragDoc.get("title").asText() : docId);
                    merged.put("inRag", true);

                    // Add upload metadata if exists
                    JsonNode upload = uploadMap.get(docId);
                    if (upload != null) {
                        merged.put("hasUploadRecord", true);
                        merged.put("uploadId", upload.has("id") ? upload.get("id").asText() : "");
                        merged.put("categoryId", upload.has("categoryId") ? upload.get("categoryId").asText() : "");
                        merged.put("status", upload.has("status") ? upload.get("status").asText() : "MOVED_TO_RAG");
                        merged.put("createdAt", upload.has("createdAt") ? upload.get("createdAt").asText() : "");
                        merged.put("questionsGenerated", upload.has("questionsGenerated") ? upload.get("questionsGenerated").asInt() : 0);
                        merged.put("questionsValidated", upload.has("questionsValidated") ? upload.get("questionsValidated").asInt() : 0);
                    } else {
                        merged.put("hasUploadRecord", false);
                        merged.put("uploadId", "");
                        // Get categoryId from RAG document's categories array (first category)
                        String ragCategoryId = "";
                        if (ragDoc.has("categories") && ragDoc.get("categories").isArray() && ragDoc.get("categories").size() > 0) {
                            ragCategoryId = ragDoc.get("categories").get(0).asText();
                        }
                        merged.put("categoryId", ragCategoryId);
                        merged.put("status", "DIRECT_INGEST");
                        merged.put("createdAt", "");
                        merged.put("questionsGenerated", 0);
                        merged.put("questionsValidated", 0);
                    }

                    // Apply category filter if specified
                    if (categoryFilter == null || categoryFilter.isBlank()) {
                        arrayNode.add(merged);
                    } else {
                        String docCategoryId = merged.get("categoryId").asText();
                        if (categoryFilter.equals(docCategoryId)) {
                            arrayNode.add(merged);
                        }
                    }
                }
            }

            // Now add uploads that are NOT yet in RAG (PENDING, GENERATING_QA, VALIDATING_QA, READY_FOR_REVIEW, FAILED)
            if (uploads != null && uploads.isArray()) {
                for (JsonNode upload : uploads) {
                    String docId = upload.has("docId") ? upload.get("docId").asText() : "";

                    // Skip if already processed from RAG documents
                    if (processedDocIds.contains(docId)) {
                        continue;
                    }

                    String status = upload.has("status") ? upload.get("status").asText() : "";

                    // Skip DELETED uploads
                    if ("DELETED".equals(status)) {
                        continue;
                    }

                    var merged = objectMapper.createObjectNode();
                    merged.put("docId", docId);
                    merged.put("chunkCount", upload.has("totalChunks") ? upload.get("totalChunks").asInt() : 0);
                    merged.put("title", upload.has("title") ? upload.get("title").asText() : docId);
                    merged.put("inRag", false);
                    merged.put("hasUploadRecord", true);
                    merged.put("uploadId", upload.has("id") ? upload.get("id").asText() : "");
                    merged.put("categoryId", upload.has("categoryId") ? upload.get("categoryId").asText() : "");
                    merged.put("status", status);
                    merged.put("createdAt", upload.has("createdAt") ? upload.get("createdAt").asText() : "");
                    merged.put("questionsGenerated", upload.has("questionsGenerated") ? upload.get("questionsGenerated").asInt() : 0);
                    merged.put("questionsValidated", upload.has("questionsValidated") ? upload.get("questionsValidated").asInt() : 0);

                    // Apply category filter if specified
                    if (categoryFilter == null || categoryFilter.isBlank()) {
                        arrayNode.add(merged);
                    } else {
                        String docCategoryId = merged.get("categoryId").asText();
                        if (categoryFilter.equals(docCategoryId)) {
                            arrayNode.add(merged);
                        }
                    }
                }
            }

            return arrayNode;
        } catch (Exception e) {
            log.warn("Could not get merged documents", e);
            return objectMapper.createArrayNode();
        }
    }

    /**
     * Generate additional Q&A pairs for an existing document.
     */
    public JsonNode generateMoreQA(String uploadId, int fineGrainCount, int summaryCount) {
        try {
            Map<String, Object> body = Map.of(
                    "fineGrainCount", fineGrainCount,
                    "summaryCount", summaryCount
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/documents/uploads/" + uploadId + "/generate-more-qa"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Failed to generate more Q&A: HTTP {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to generate Q&A: " + extractErrorMessage(response.body()));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generating more Q&A for upload {}", uploadId, e);
            throw new RuntimeException("Error generating Q&A: " + e.getMessage(), e);
        }
    }

    /**
     * Document-scoped chat - ask questions about a specific document.
     */
    public JsonNode documentChat(String uploadId, String question) {
        try {
            Map<String, Object> body = Map.of("question", question);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/documents/uploads/" + uploadId + "/chat"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Failed to chat: HTTP {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Chat failed: " + extractErrorMessage(response.body()));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in document chat for upload {}", uploadId, e);
            throw new RuntimeException("Chat error: " + e.getMessage(), e);
        }
    }

    /**
     * Get the streaming chat endpoint URL for a document.
     */
    public String getDocumentChatStreamEndpoint(String uploadId) {
        return baseUrl + "/api/documents/uploads/" + uploadId + "/chat/stream";
    }

    /**
     * Add a Q&A pair to FAQ.
     */
    public JsonNode addQAToFaq(String uploadId, Long qaId) {
        try {
            Map<String, Object> body = Map.of(
                    "qaIds", List.of(qaId),
                    "selected", true
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/faq-management/review/" + uploadId + "/select"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Failed to add Q&A to FAQ: HTTP {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to add to FAQ: " + extractErrorMessage(response.body()));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error adding Q&A {} to FAQ", qaId, e);
            throw new RuntimeException("Error adding to FAQ: " + e.getMessage(), e);
        }
    }

    /**
     * Approve selected FAQs and push to Qdrant.
     */
    public JsonNode approveSelectedFaqs(String uploadId, String approvedBy) {
        try {
            Map<String, Object> body = Map.of("approvedBy", approvedBy != null ? approvedBy : "admin");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/faq-management/review/" + uploadId + "/approve"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Failed to approve FAQs: HTTP {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to approve FAQs: " + extractErrorMessage(response.body()));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error approving FAQs for upload {}", uploadId, e);
            throw new RuntimeException("Error approving FAQs: " + e.getMessage(), e);
        }
    }

    /**
     * Update the title of a document upload.
     */
    public JsonNode updateUploadTitle(String uploadId, String title) {
        try {
            Map<String, Object> body = Map.of("title", title);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/documents/uploads/" + uploadId + "/title"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else {
                log.error("Failed to update title: HTTP {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to update title: " + extractErrorMessage(response.body()));
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating title for upload {}", uploadId, e);
            throw new RuntimeException("Error updating title: " + e.getMessage(), e);
        }
    }

    /**
     * Extract error message from JSON response body.
     */
    private String extractErrorMessage(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            if (json.has("error")) {
                return json.get("error").asText();
            }
            if (json.has("message")) {
                return json.get("message").asText();
            }
            return responseBody;
        } catch (Exception e) {
            return responseBody;
        }
    }
}
