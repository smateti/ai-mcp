package com.naag.utilitytools.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TimeController {

    @GetMapping("/time")
    public ResponseEntity<Map<String, Object>> getCurrentTime(
            @RequestParam(defaultValue = "UTC") String timezone) {
        try {
            ZoneId zoneId = ZoneId.of(timezone);
            ZonedDateTime now = ZonedDateTime.now(zoneId);

            return ResponseEntity.ok(Map.of(
                    "datetime", now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME),
                    "date", now.format(DateTimeFormatter.ISO_DATE),
                    "time", now.format(DateTimeFormatter.ISO_TIME),
                    "timestamp", now.toEpochSecond(),
                    "timezone", timezone
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid timezone: " + timezone));
        }
    }

    @PostMapping("/time")
    public ResponseEntity<Map<String, Object>> getCurrentTimePost(@RequestBody(required = false) Map<String, String> request) {
        String timezone = request != null ? request.getOrDefault("timezone", "UTC") : "UTC";
        return getCurrentTime(timezone);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "naag-utility-tools",
                "time", LocalDateTime.now().toString()
        ));
    }
}
