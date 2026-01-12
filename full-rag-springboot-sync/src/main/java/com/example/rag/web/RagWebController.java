package com.example.rag.web;

import com.example.rag.ingest.DocumentTextExtractor;
import com.example.rag.service.RagService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class RagWebController {

  private final RagService rag;

  public RagWebController(RagService rag) {
    this.rag = rag;
  }

  @GetMapping("/")
  public String home() {
    return "index";
  }

  @PostMapping("/upload")
  public String upload(@RequestParam String docId,
                       @RequestParam MultipartFile file,
                       Model model) throws Exception {
    String text = DocumentTextExtractor.extract(file.getOriginalFilename(), file.getInputStream());
    int chunks = rag.ingest(docId, text);
    model.addAttribute("msg", "Ingested " + chunks + " chunks for docId=" + docId);
    return "index";
  }

  @PostMapping("/ask")
  public String ask(@RequestParam String question, Model model) {
    String answer = rag.ask(question);
    model.addAttribute("question", question);
    model.addAttribute("answer", answer);
    return "index";
  }
}
