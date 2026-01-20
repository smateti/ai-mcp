package com.naag.utilitytools.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CalculatorController {

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> add(@RequestBody Map<String, Number> request) {
        Number a = request.getOrDefault("a", 0);
        Number b = request.getOrDefault("b", 0);
        double result = a.doubleValue() + b.doubleValue();
        return ResponseEntity.ok(Map.of("result", result));
    }

    @GetMapping("/add")
    public ResponseEntity<Map<String, Object>> addGet(
            @RequestParam(defaultValue = "0") double a,
            @RequestParam(defaultValue = "0") double b) {
        return ResponseEntity.ok(Map.of("result", a + b));
    }

    @PostMapping("/subtract")
    public ResponseEntity<Map<String, Object>> subtract(@RequestBody Map<String, Number> request) {
        Number a = request.getOrDefault("a", 0);
        Number b = request.getOrDefault("b", 0);
        double result = a.doubleValue() - b.doubleValue();
        return ResponseEntity.ok(Map.of("result", result));
    }

    @PostMapping("/multiply")
    public ResponseEntity<Map<String, Object>> multiply(@RequestBody Map<String, Number> request) {
        Number a = request.getOrDefault("a", 0);
        Number b = request.getOrDefault("b", 0);
        double result = a.doubleValue() * b.doubleValue();
        return ResponseEntity.ok(Map.of("result", result));
    }

    @PostMapping("/divide")
    public ResponseEntity<Map<String, Object>> divide(@RequestBody Map<String, Number> request) {
        Number a = request.getOrDefault("a", 0);
        Number b = request.getOrDefault("b", 1);
        if (b.doubleValue() == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Division by zero"));
        }
        double result = a.doubleValue() / b.doubleValue();
        return ResponseEntity.ok(Map.of("result", result));
    }
}
