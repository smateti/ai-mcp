package com.nystax.nimba.analyzer.llm;

import com.nystax.nimba.analyzer.model.LlmInsight;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LlmResponseParser {

    private static final Pattern KEY_VALUE = Pattern.compile("^([A-Z_]+):\\s*(.*)");

    public static Map<String, String> parseStructuredResponse(String response) {
        Map<String, String> result = new LinkedHashMap<>();
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : response.split("\n")) {
            Matcher m = KEY_VALUE.matcher(line.trim());
            if (m.matches()) {
                if (currentKey != null) {
                    result.put(currentKey, currentValue.toString().trim());
                }
                currentKey = m.group(1);
                currentValue = new StringBuilder(m.group(2));
            } else if (currentKey != null) {
                currentValue.append(" ").append(line.trim());
            }
        }
        if (currentKey != null) {
            result.put(currentKey, currentValue.toString().trim());
        }
        return result;
    }

    public static LlmInsight toProcessorInsight(String response, String className) {
        Map<String, String> parsed = parseStructuredResponse(response);
        LlmInsight insight = new LlmInsight();
        insight.setClassName(className);
        insight.setSummary(parsed.getOrDefault("SUMMARY", response));
        insight.setBusinessLogic(parsed.get("BUSINESS_LOGIC"));
        insight.setConditionalLogic(parsed.get("CONDITIONAL_LOGIC"));
        insight.setDataTransformations(parsed.get("DATA_TRANSFORMATIONS"));
        insight.setDbOperations(parsed.get("DB_OPERATIONS"));
        insight.setFunctionCalls(parsed.get("FUNCTION_CALLS"));
        insight.setErrorHandling(parsed.get("ERROR_HANDLING"));
        insight.setOutputDescription(parsed.get("OUTPUT"));
        insight.setPatterns(parsed.get("PATTERNS"));
        insight.setIssues(parsed.get("ISSUES"));
        return insight;
    }

    public static LlmInsight toDeserializerInsight(String response, String className) {
        Map<String, String> parsed = parseStructuredResponse(response);
        LlmInsight insight = new LlmInsight();
        insight.setClassName(className);
        insight.setSummary(parsed.getOrDefault("SUMMARY", response));
        insight.setParsingLogic(parsed.get("PARSING_LOGIC"));
        insight.setFieldMapping(parsed.get("FIELD_MAPPING"));
        insight.setRecordStructure(parsed.get("RECORD_STRUCTURE"));
        insight.setValidationRules(parsed.get("VALIDATION"));
        insight.setFunctionCalls(parsed.get("FUNCTION_CALLS"));
        return insight;
    }

    public static LlmInsight toReaderInsight(String response, String className) {
        Map<String, String> parsed = parseStructuredResponse(response);
        LlmInsight insight = new LlmInsight();
        insight.setClassName(className);
        insight.setSummary(parsed.getOrDefault("SUMMARY", response));
        insight.setDataSource(parsed.get("DATA_SOURCE"));
        insight.setParsingLogic(parsed.get("FILE_FORMAT"));
        insight.setQueryPattern(parsed.get("QUERY_PATTERN"));
        insight.setConnectionDetails(parsed.get("CONNECTION_DETAILS"));
        insight.setFunctionCalls(parsed.get("FUNCTION_CALLS"));
        return insight;
    }

    public static LlmInsight toListenerInsight(String response, String className) {
        Map<String, String> parsed = parseStructuredResponse(response);
        LlmInsight insight = new LlmInsight();
        insight.setClassName(className);
        insight.setSummary(parsed.getOrDefault("SUMMARY", response));
        insight.setOnJobStart(parsed.get("ON_JOB_START"));
        insight.setOnJobFinish(parsed.get("ON_JOB_FINISH"));
        insight.setResourceManagement(parsed.get("RESOURCE_MANAGEMENT"));
        insight.setFunctionCalls(parsed.get("FUNCTION_CALLS"));
        return insight;
    }
}
