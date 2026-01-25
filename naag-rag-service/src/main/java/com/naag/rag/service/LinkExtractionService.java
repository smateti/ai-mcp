package com.naag.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for extracting links/URLs from document content.
 * Supports various formats: URLs, markdown links, HTML links, and document references.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LinkExtractionService {

    private final ObjectMapper objectMapper;

    // Pattern for standard URLs (http, https, ftp)
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for markdown links [text](url)
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile(
            "\\[([^\\]]+)\\]\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for HTML links <a href="url">
    private static final Pattern HTML_LINK_PATTERN = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for document references (e.g., "see section 3.2", "refer to appendix A")
    private static final Pattern DOC_REFERENCE_PATTERN = Pattern.compile(
            "(?:see|refer to|as described in|defined in|specified in)\\s+(?:section|chapter|appendix|table|figure)\\s+([\\w.]+)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Extract all links from document content.
     * Returns a JSON string containing the extracted links.
     */
    public String extractLinksAsJson(String content) {
        List<ExtractedLink> links = extractLinks(content);
        try {
            return objectMapper.writeValueAsString(links);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize extracted links", e);
            return "[]";
        }
    }

    /**
     * Extract all links from document content.
     */
    public List<ExtractedLink> extractLinks(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        Map<String, ExtractedLink> linkMap = new LinkedHashMap<>(); // Preserve order, deduplicate

        // Extract standard URLs
        extractUrls(content, linkMap);

        // Extract markdown links
        extractMarkdownLinks(content, linkMap);

        // Extract HTML links
        extractHtmlLinks(content, linkMap);

        // Extract internal document references
        extractDocReferences(content, linkMap);

        return new ArrayList<>(linkMap.values());
    }

    private void extractUrls(String content, Map<String, ExtractedLink> linkMap) {
        Matcher matcher = URL_PATTERN.matcher(content);
        while (matcher.find()) {
            String url = cleanUrl(matcher.group());
            if (isValidUrl(url) && !linkMap.containsKey(url)) {
                linkMap.put(url, new ExtractedLink(url, null, categorizeLink(url)));
            }
        }
    }

    private void extractMarkdownLinks(String content, Map<String, ExtractedLink> linkMap) {
        Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(content);
        while (matcher.find()) {
            String text = matcher.group(1).trim();
            String url = matcher.group(2).trim();
            if (isValidUrl(url)) {
                // Update with text if we already have this URL, or add new
                linkMap.put(url, new ExtractedLink(url, text, categorizeLink(url)));
            }
        }
    }

    private void extractHtmlLinks(String content, Map<String, ExtractedLink> linkMap) {
        Matcher matcher = HTML_LINK_PATTERN.matcher(content);
        while (matcher.find()) {
            String url = matcher.group(1).trim();
            if (isValidUrl(url) && !linkMap.containsKey(url)) {
                linkMap.put(url, new ExtractedLink(url, null, categorizeLink(url)));
            }
        }
    }

    private void extractDocReferences(String content, Map<String, ExtractedLink> linkMap) {
        Matcher matcher = DOC_REFERENCE_PATTERN.matcher(content);
        while (matcher.find()) {
            String reference = matcher.group(0).trim();
            String refId = "internal:" + matcher.group(1).trim().toLowerCase();
            if (!linkMap.containsKey(refId)) {
                linkMap.put(refId, new ExtractedLink(refId, reference, LinkType.INTERNAL_REFERENCE));
            }
        }
    }

    private String cleanUrl(String url) {
        // Remove trailing punctuation that's not part of URL
        return url.replaceAll("[.,;:!?)]+$", "");
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        // Skip common non-document URLs
        if (url.contains("example.com") || url.contains("localhost")) {
            return false;
        }
        // Must be http/https or internal reference
        return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("internal:");
    }

    private LinkType categorizeLink(String url) {
        String lowerUrl = url.toLowerCase();

        // Document types
        if (lowerUrl.endsWith(".pdf")) return LinkType.PDF_DOCUMENT;
        if (lowerUrl.endsWith(".doc") || lowerUrl.endsWith(".docx")) return LinkType.WORD_DOCUMENT;
        if (lowerUrl.endsWith(".md")) return LinkType.MARKDOWN_DOCUMENT;
        if (lowerUrl.endsWith(".html") || lowerUrl.endsWith(".htm")) return LinkType.HTML_DOCUMENT;
        if (lowerUrl.endsWith(".txt")) return LinkType.TEXT_DOCUMENT;

        // Specification/Standard URLs
        if (lowerUrl.contains("spec") || lowerUrl.contains("standard") ||
            lowerUrl.contains("rfc") || lowerUrl.contains("w3.org") ||
            lowerUrl.contains("ietf.org") || lowerUrl.contains("iso.org")) {
            return LinkType.SPECIFICATION;
        }

        // API documentation
        if (lowerUrl.contains("api") || lowerUrl.contains("swagger") ||
            lowerUrl.contains("openapi") || lowerUrl.contains("/docs")) {
            return LinkType.API_DOCUMENTATION;
        }

        // Code repositories
        if (lowerUrl.contains("github.com") || lowerUrl.contains("gitlab.com") ||
            lowerUrl.contains("bitbucket.org")) {
            return LinkType.CODE_REPOSITORY;
        }

        return LinkType.EXTERNAL_LINK;
    }

    /**
     * Filter links to only include ingestable documents.
     */
    public List<ExtractedLink> getIngestableLinks(List<ExtractedLink> links) {
        return links.stream()
                .filter(link -> link.type() == LinkType.PDF_DOCUMENT ||
                               link.type() == LinkType.MARKDOWN_DOCUMENT ||
                               link.type() == LinkType.HTML_DOCUMENT ||
                               link.type() == LinkType.TEXT_DOCUMENT ||
                               link.type() == LinkType.SPECIFICATION)
                .collect(Collectors.toList());
    }

    /**
     * Get a summary of extracted links by type.
     */
    public Map<LinkType, Long> getLinkSummary(List<ExtractedLink> links) {
        return links.stream()
                .collect(Collectors.groupingBy(ExtractedLink::type, Collectors.counting()));
    }

    // Record for extracted link data
    public record ExtractedLink(
            String url,
            String text,
            LinkType type
    ) {}

    public enum LinkType {
        PDF_DOCUMENT,
        WORD_DOCUMENT,
        MARKDOWN_DOCUMENT,
        HTML_DOCUMENT,
        TEXT_DOCUMENT,
        SPECIFICATION,
        API_DOCUMENTATION,
        CODE_REPOSITORY,
        INTERNAL_REFERENCE,
        EXTERNAL_LINK
    }
}
