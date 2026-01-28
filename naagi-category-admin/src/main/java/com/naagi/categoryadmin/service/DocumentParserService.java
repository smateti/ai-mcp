package com.naagi.categoryadmin.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for parsing document files (PDF, Word, Markdown, Text) and extracting text content.
 */
@Service
@Slf4j
public class DocumentParserService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "txt", "md", "markdown", "text"
    );

    /**
     * Parse a document file and extract its text content.
     *
     * @param file The uploaded file
     * @return ParseResult containing the extracted text and metadata
     */
    public ParseResult parseDocument(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            return new ParseResult(false, null, "No filename provided", null);
        }

        String extension = getFileExtension(filename).toLowerCase();
        log.info("Parsing document: {} with extension: {}", filename, extension);

        try {
            String text = switch (extension) {
                case "pdf" -> extractTextFromPdf(file.getInputStream());
                case "doc" -> extractTextFromDoc(file.getInputStream());
                case "docx" -> extractTextFromDocx(file.getInputStream());
                case "txt", "text" -> new String(file.getBytes(), StandardCharsets.UTF_8);
                case "md", "markdown" -> extractTextFromMarkdown(file.getBytes());
                default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
            };

            if (text == null || text.isBlank()) {
                return new ParseResult(false, null, "No text content could be extracted from the document", extension);
            }

            // Clean up the text
            text = cleanText(text);

            log.info("Successfully extracted {} characters from {}", text.length(), filename);
            return new ParseResult(true, text, null, extension);

        } catch (IllegalArgumentException e) {
            log.warn("Unsupported file type: {}", extension);
            return new ParseResult(false, null, e.getMessage(), extension);
        } catch (Exception e) {
            log.error("Failed to parse document: {}", filename, e);
            return new ParseResult(false, null, "Failed to parse document: " + e.getMessage(), extension);
        }
    }

    /**
     * Extract text from a PDF file.
     */
    private String extractTextFromPdf(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * Extract text from a .doc file (legacy Word format).
     */
    private String extractTextFromDoc(InputStream inputStream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    /**
     * Extract text from a .docx file (modern Word format).
     */
    private String extractTextFromDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
        }
    }

    /**
     * Extract text from a Markdown file.
     * Converts markdown to plain text while preserving structure.
     */
    private String extractTextFromMarkdown(byte[] bytes) {
        String markdown = new String(bytes, StandardCharsets.UTF_8);

        // Parse markdown and render as plain text
        Parser parser = Parser.builder().build();
        Node document = parser.parse(markdown);
        TextContentRenderer renderer = TextContentRenderer.builder().build();

        return renderer.render(document);
    }

    /**
     * Get the file extension from a filename.
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * Clean up extracted text by removing excessive whitespace.
     */
    private String cleanText(String text) {
        if (text == null) return null;

        // Replace multiple consecutive newlines with double newline
        text = text.replaceAll("\\n{3,}", "\n\n");

        // Replace multiple spaces with single space
        text = text.replaceAll(" {2,}", " ");

        // Trim leading/trailing whitespace from each line
        text = text.lines()
                .map(String::trim)
                .collect(Collectors.joining("\n"));

        return text.trim();
    }

    /**
     * Check if a file type is supported.
     */
    public boolean isSupported(String filename) {
        if (filename == null) return false;
        String extension = getFileExtension(filename).toLowerCase();
        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    /**
     * Get the document type from filename.
     */
    public String getDocumentType(String filename) {
        if (filename == null) return "unknown";
        String extension = getFileExtension(filename).toLowerCase();
        return switch (extension) {
            case "pdf" -> "pdf";
            case "doc", "docx" -> "word";
            case "txt", "text" -> "text";
            case "md", "markdown" -> "markdown";
            default -> "unknown";
        };
    }

    /**
     * Get supported file extensions as a comma-separated string for UI display.
     */
    public String getSupportedExtensions() {
        return String.join(", ", SUPPORTED_EXTENSIONS.stream()
                .map(ext -> "." + ext)
                .sorted()
                .toList());
    }

    /**
     * Get accept attribute value for HTML file input.
     */
    public String getAcceptAttribute() {
        return ".pdf,.doc,.docx,.txt,.text,.md,.markdown";
    }

    /**
     * Result of document parsing.
     */
    public record ParseResult(
            boolean success,
            String text,
            String errorMessage,
            String fileType
    ) {}
}
