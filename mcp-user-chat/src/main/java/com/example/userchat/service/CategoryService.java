package com.example.userchat.service;

import com.example.userchat.dto.CategoryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class CategoryService {

    @Value("${services.category-admin}")
    private String categoryAdminUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<CategoryDto> getActiveCategories() {
        try {
            String url = categoryAdminUrl + "/api/categories/active";
            ResponseEntity<List<CategoryDto>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<CategoryDto>>() {}
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch categories", e);
            return Collections.emptyList();
        }
    }

    public List<String> getEnabledToolsForCategory(String categoryId) {
        try {
            String url = categoryAdminUrl + "/api/categories/" + categoryId + "/tools/enabled";
            ResponseEntity<EnabledToolsResponse> response = restTemplate.getForEntity(
                url,
                EnabledToolsResponse.class
            );
            return response.getBody() != null ? response.getBody().getToolIds() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch tools for category: {}", categoryId, e);
            return Collections.emptyList();
        }
    }

    public List<String> getEnabledDocumentsForCategory(String categoryId) {
        try {
            String url = categoryAdminUrl + "/api/categories/" + categoryId + "/documents/enabled";
            ResponseEntity<EnabledDocumentsResponse> response = restTemplate.getForEntity(
                url,
                EnabledDocumentsResponse.class
            );
            return response.getBody() != null ? response.getBody().getDocumentIds() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch documents for category: {}", categoryId, e);
            return Collections.emptyList();
        }
    }

    public static class EnabledToolsResponse {
        private List<String> toolIds;

        public List<String> getToolIds() {
            return toolIds;
        }

        public void setToolIds(List<String> toolIds) {
            this.toolIds = toolIds;
        }
    }

    public static class EnabledDocumentsResponse {
        private List<String> documentIds;

        public List<String> getDocumentIds() {
            return documentIds;
        }

        public void setDocumentIds(List<String> documentIds) {
            this.documentIds = documentIds;
        }
    }
}
