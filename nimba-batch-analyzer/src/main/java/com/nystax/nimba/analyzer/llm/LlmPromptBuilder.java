package com.nystax.nimba.analyzer.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LlmPromptBuilder {

    public static final String SYSTEM_PROMPT = """
            You are a Java code analyst specializing in the Nimba batch processing framework. \
            You analyze Java source code for batch jobs. Be concise and precise. \
            Focus on what the code does, not how it's written. \
            Always respond in the exact format requested.""";

    @Value("${nimba.analyzer.max-source-size:8000}")
    private int maxSourceSize;

    public String buildDeserializerPrompt(String sourceCode, String className) {
        return String.format("""
                Analyze this Nimba deserializer class "%s".

                Answer these questions concisely:
                1. PARSING_LOGIC: What data format does it parse and how? (e.g., fixed-width, CSV, delimited, XML)
                2. FIELD_MAPPING: What fields/objects does it produce?
                3. FUNCTION_CALLS: Does it call any external services or microservice clients? List them.
                4. SUMMARY: One-sentence description of what this deserializer does.

                Source code:
                ```java
                %s
                ```

                Respond in this exact format:
                PARSING_LOGIC: <answer>
                FIELD_MAPPING: <answer>
                FUNCTION_CALLS: <answer or "None">
                SUMMARY: <answer>
                """, className, truncateSource(sourceCode));
    }

    public String buildProcessorPrompt(String sourceCode, String className) {
        return String.format("""
                Analyze this Nimba processor class "%s".

                Answer these questions concisely:
                1. BUSINESS_LOGIC: What business logic does this processor perform?
                2. FUNCTION_CALLS: Does it call any external services, microservice clients, or WAS function clients? \
                List each client class and method called.
                3. ERROR_HANDLING: How does it handle errors? Does it use BatchExitException?
                4. PATTERNS: Any notable patterns (data transformation, validation, aggregation, etc.)?
                5. ISSUES: Any potential issues (missing null checks, hardcoded values, performance concerns)?
                6. SUMMARY: One-sentence description of what this processor does.

                Source code:
                ```java
                %s
                ```

                Respond in this exact format:
                BUSINESS_LOGIC: <answer>
                FUNCTION_CALLS: <answer or "None">
                ERROR_HANDLING: <answer>
                PATTERNS: <answer or "None">
                ISSUES: <answer or "None">
                SUMMARY: <answer>
                """, className, truncateSource(sourceCode));
    }

    public String buildCustomReaderPrompt(String sourceCode, String className) {
        return String.format("""
                Analyze this Nimba custom reader class "%s".

                Answer these questions concisely:
                1. DATA_SOURCE: What data source does it read from (file, database, API, Kafka, etc.)?
                2. FILE_FORMAT: If reading a file, what format (fixed-width, CSV, delimited, XML, JSON)?
                3. FUNCTION_CALLS: Does it call any external services? List them.
                4. SUMMARY: One-sentence description.

                Source code:
                ```java
                %s
                ```

                Respond in this exact format:
                DATA_SOURCE: <answer>
                FILE_FORMAT: <answer or "N/A">
                FUNCTION_CALLS: <answer or "None">
                SUMMARY: <answer>
                """, className, truncateSource(sourceCode));
    }

    public String buildJobListenerPrompt(String sourceCode, String className) {
        return String.format("""
                Analyze this Nimba JobListener class "%s".

                Answer these questions concisely:
                1. ON_JOB_START: What does the onJobStart method do?
                2. ON_JOB_FINISH: What does the onJobFinish method do?
                3. FUNCTION_CALLS: Does it call any external services? List them.
                4. SUMMARY: One-sentence description of what this listener does.

                Source code:
                ```java
                %s
                ```

                Respond in this exact format:
                ON_JOB_START: <answer>
                ON_JOB_FINISH: <answer>
                FUNCTION_CALLS: <answer or "None">
                SUMMARY: <answer>
                """, className, truncateSource(sourceCode));
    }

    public String buildProjectSummaryPrompt(String analysisData) {
        return String.format("""
                Based on the following analysis data from a Nimba batch project, write a comprehensive \
                project summary document in Markdown format.

                The document should include these sections:
                1. **Project Overview** - What this batch application does, its purpose
                2. **Job Flow Summary** - For each job, a plain-English narrative of the step-by-step flow
                3. **Data Flow** - What data enters, how it's transformed, where it goes
                4. **External Integrations** - Which microservices (WAS functions) are called and what they do
                5. **Error Handling Strategy** - Error thresholds, failOnError, BatchExitException usage
                6. **Operational Details** - File archival, parallelism, resume capability, configuration

                Write it as a readable document for someone unfamiliar with the project. \
                Use clear, professional language.

                Analysis data:
                %s
                """, truncateSource(analysisData));
    }

    private String truncateSource(String source) {
        if (source.length() <= maxSourceSize) {
            return source;
        }
        int importEnd = source.indexOf("public class");
        if (importEnd < 0) importEnd = 0;
        String header = source.substring(0, Math.min(importEnd + 500, source.length()));
        int remaining = maxSourceSize - header.length() - 50;
        if (remaining > 0 && source.length() > remaining) {
            return header + "\n... [truncated middle section] ...\n"
                    + source.substring(source.length() - remaining);
        }
        return source.substring(0, maxSourceSize) + "\n... [truncated] ...";
    }
}
