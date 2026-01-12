package com.example.rag.ingest;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.InputStream;

public final class DocumentTextExtractor {

  public static String extract(String filename, InputStream in) {
    String f = filename == null ? "" : filename.toLowerCase();
    try {
      if (f.endsWith(".pdf")) return extractPdf(in);
      if (f.endsWith(".docx")) return extractDocx(in);
      throw new IllegalArgumentException("Unsupported file type: " + filename);
    } catch (Exception e) {
      throw new RuntimeException("Failed to extract: " + filename, e);
    }
  }

  private static String extractPdf(InputStream in) throws Exception {
    try (PDDocument doc = PDDocument.load(in)) {
      return new PDFTextStripper().getText(doc);
    }
  }

  private static String extractDocx(InputStream in) throws Exception {
    try (XWPFDocument doc = new XWPFDocument(in)) {
      StringBuilder sb = new StringBuilder();
      doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
      return sb.toString();
    }
  }

  private DocumentTextExtractor() {}
}
