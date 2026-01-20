package com.naag.utilitytools.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class EchoController {

    @PostMapping("/echo")
    public ResponseEntity<Map<String, String>> echo(@RequestBody Map<String, String> request) {
        String message = request.getOrDefault("message", "");
        return ResponseEntity.ok(Map.of("echo", message));
    }

    @GetMapping("/echo")
    public ResponseEntity<Map<String, String>> echoGet(@RequestParam(defaultValue = "") String message) {
        return ResponseEntity.ok(Map.of("echo", message));
    }
}
