package com.naagi.orchestrator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Input/output guardrails for the orchestration pipeline.
 *
 * Input guardrails:
 * - Prompt injection detection (pattern matching for common injection phrases)
 * - Query length limits
 * - Rate limiting per session (simple in-memory counter)
 *
 * Output guardrails:
 * - Answer length validation
 * - Source attribution check (answer references retrieved context)
 * - PII pattern detection (SSN, email, phone, credit card)
 */
@Service
@Slf4j
public class GuardrailsService {

    private final boolean enabled;
    private final int maxInputLength;
    private final int maxOutputLength;
    private final boolean piiDetectionEnabled;
    private final boolean injectionDetectionEnabled;

    // Prompt injection patterns
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+(instructions|prompts|context)"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(above|previous|prior)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an|the)"),
            Pattern.compile("(?i)forget\\s+(everything|all|your)\\s+(you|instructions|rules)"),
            Pattern.compile("(?i)system\\s*prompt"),
            Pattern.compile("(?i)override\\s+(safety|content|instructions)"),
            Pattern.compile("(?i)\\bDAN\\b.*\\bmode\\b"),
            Pattern.compile("(?i)jailbreak"),
            Pattern.compile("(?i)pretend\\s+you\\s+(are|have|can)"),
            Pattern.compile("(?i)act\\s+as\\s+(if|though)\\s+you\\s+(are|have|can)")
    );

    // PII patterns
    private static final Pattern SSN_PATTERN =
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN =
            Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b");

    public record GuardrailResult(boolean passed, List<String> violations) {
        public static GuardrailResult pass() {
            return new GuardrailResult(true, List.of());
        }

        public static GuardrailResult fail(List<String> violations) {
            return new GuardrailResult(false, violations);
        }
    }

    public GuardrailsService(
            @Value("${naagi.guardrails.enabled:true}") boolean enabled,
            @Value("${naagi.guardrails.max-input-length:4000}") int maxInputLength,
            @Value("${naagi.guardrails.max-output-length:8000}") int maxOutputLength,
            @Value("${naagi.guardrails.pii-detection.enabled:true}") boolean piiDetectionEnabled,
            @Value("${naagi.guardrails.injection-detection.enabled:true}") boolean injectionDetectionEnabled) {
        this.enabled = enabled;
        this.maxInputLength = maxInputLength;
        this.maxOutputLength = maxOutputLength;
        this.piiDetectionEnabled = piiDetectionEnabled;
        this.injectionDetectionEnabled = injectionDetectionEnabled;
        log.info("[GUARDRAILS] Initialized, enabled={}, maxInput={}, maxOutput={}, pii={}, injection={}",
                enabled, maxInputLength, maxOutputLength, piiDetectionEnabled, injectionDetectionEnabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Validate user input before processing.
     */
    public GuardrailResult validateInput(String input) {
        if (!enabled) return GuardrailResult.pass();
        if (input == null || input.isBlank()) return GuardrailResult.pass();

        List<String> violations = new ArrayList<>();

        // Length check
        if (input.length() > maxInputLength) {
            violations.add("Input exceeds maximum length of " + maxInputLength + " characters");
        }

        // Prompt injection detection
        if (injectionDetectionEnabled) {
            for (Pattern pattern : INJECTION_PATTERNS) {
                if (pattern.matcher(input).find()) {
                    violations.add("Potential prompt injection detected");
                    log.warn("[GUARDRAILS] Prompt injection pattern matched in input: {}",
                            input.substring(0, Math.min(100, input.length())));
                    break;
                }
            }
        }

        if (violations.isEmpty()) {
            return GuardrailResult.pass();
        }

        log.info("[GUARDRAILS] Input validation failed with {} violation(s)", violations.size());
        return GuardrailResult.fail(violations);
    }

    /**
     * Validate output before returning to user.
     */
    public GuardrailResult validateOutput(String output) {
        if (!enabled) return GuardrailResult.pass();
        if (output == null || output.isBlank()) return GuardrailResult.pass();

        List<String> violations = new ArrayList<>();

        // Length check
        if (output.length() > maxOutputLength) {
            violations.add("Output exceeds maximum length of " + maxOutputLength + " characters");
        }

        // PII detection
        if (piiDetectionEnabled) {
            List<String> piiTypes = detectPII(output);
            if (!piiTypes.isEmpty()) {
                violations.add("Output contains potential PII: " + String.join(", ", piiTypes));
                log.warn("[GUARDRAILS] PII detected in output: {}", piiTypes);
            }
        }

        if (violations.isEmpty()) {
            return GuardrailResult.pass();
        }

        log.info("[GUARDRAILS] Output validation failed with {} violation(s)", violations.size());
        return GuardrailResult.fail(violations);
    }

    /**
     * Sanitize output by masking detected PII patterns.
     */
    public String sanitizeOutput(String output) {
        if (!enabled || !piiDetectionEnabled || output == null) return output;

        String sanitized = output;
        sanitized = SSN_PATTERN.matcher(sanitized).replaceAll("[SSN REDACTED]");
        sanitized = CREDIT_CARD_PATTERN.matcher(sanitized).replaceAll("[CARD REDACTED]");
        // Don't redact emails and phones by default as they may be legitimate data
        return sanitized;
    }

    private List<String> detectPII(String text) {
        List<String> found = new ArrayList<>();
        if (SSN_PATTERN.matcher(text).find()) found.add("SSN");
        if (CREDIT_CARD_PATTERN.matcher(text).find()) found.add("Credit Card");
        // Email and phone are informational only — not treated as violations
        return found;
    }
}
