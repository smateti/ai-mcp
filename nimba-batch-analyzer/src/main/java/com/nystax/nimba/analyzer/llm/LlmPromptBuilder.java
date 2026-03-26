package com.nystax.nimba.analyzer.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LlmPromptBuilder {

    public static final String SYSTEM_PROMPT = """
            You are a Java code analyst specializing in the Nimba batch processing framework. \
            You analyze Java source code for batch jobs. Be thorough and precise. \
            Focus on what the code does, not how it's written. \
            Always respond in the exact format requested.""";

    @Value("${nimba.analyzer.max-source-size:12000}")
    private int maxSourceSize;

    public String buildDeserializerPrompt(String sourceCode, String className) {
        return String.format("""
                Analyze this Nimba deserializer class "%s" in detail for documentation purposes.

                Answer each question thoroughly:
                1. SUMMARY: A 2-3 sentence description of what this deserializer does, including \
                input format and output object type.
                2. PARSING_LOGIC: Describe the exact parsing approach:
                   - What input format does it handle? (fixed-width with column positions, \
                CSV with delimiter, XML with element paths, JSON, etc.)
                   - Does it use a transformer class? If so, which one and what does it do?
                   - Are there any header/trailer record handling patterns?
                3. FIELD_MAPPING: List each field extracted and how it maps to the output object. \
                Format as: inputField/position -> outputObject.property (type)
                4. RECORD_STRUCTURE: Describe the output record/object structure. What class is \
                returned? What are its key properties?
                5. VALIDATION: What input validation is performed? (null checks, format validation, \
                required fields, range checks, etc.)
                6. FUNCTION_CALLS: Does it call any external services, microservice clients, or \
                WAS function clients? List each with class name and method.

                Source code:
                ```java
                %s
                ```

                Respond in this exact format:
                SUMMARY: <answer>
                PARSING_LOGIC: <answer>
                FIELD_MAPPING: <answer>
                RECORD_STRUCTURE: <answer>
                VALIDATION: <answer or "None">
                FUNCTION_CALLS: <answer or "None">
                """, className, truncateSource(sourceCode));
    }

    public String buildProcessorPrompt(String sourceCode, String className) {
        return String.format("""
                Analyze this Nimba processor class "%s" in detail for documentation purposes.

                Answer each question thoroughly:
                1. SUMMARY: A 2-3 sentence description of what this processor does, including \
                its input, processing, and output.
                2. BUSINESS_LOGIC: Describe the step-by-step business logic:
                   - What input does it receive?
                   - What processing steps does it perform in order?
                   - What conditions or branches affect the logic?
                   - What is the final result or side effect?
                3. CONDITIONAL_LOGIC: Does this processor handle different record types or conditions \
                differently? Pay special attention to Nimbus function calls (classes ending in \
                "Function" with .execute() calls from gov.nystax.nimbus.function.client). \
                For each condition or record type, describe what happens and which \
                Nimbus function calls or other service calls are made. Use this format for each:
                   IF <condition/record type> THEN <action and Nimbus function/service calls made>
                   If there is no conditional processing, say "None - processes all records uniformly".
                4. DATA_TRANSFORMATIONS: What data transformations occur?
                   - Object-to-object mappings
                   - Type conversions
                   - Data enrichment from external sources
                   - Aggregation or filtering
                5. DB_OPERATIONS: Does it perform any database operations? Describe:
                   - Table names and operation types (SELECT, INSERT, UPDATE, DELETE)
                   - Query patterns and parameters
                6. FUNCTION_CALLS: Does it call any external services, microservice clients, or \
                WAS function clients? For each, list:
                   - Client class name and method called
                   - What data is sent and what response is expected
                   - Under what condition is this call made
                7. ERROR_HANDLING: How does it handle errors?
                   - Does it use BatchExitException? With what status codes?
                   - What exceptions are caught vs. propagated?
                   - Are there retry patterns or fallback logic?
                8. OUTPUT: What does the processor return or produce?
                   - Return value type and content
                   - Side effects (files written, messages sent, context variables set)
                9. PATTERNS: Notable patterns (data transformation, validation, aggregation, \
                context variable usage, etc.)
                10. ISSUES: Potential issues (missing null checks, hardcoded values, performance \
                concerns, thread safety issues)

                Source code:
                ```java
                %s
                ```

                Respond in this exact format:
                SUMMARY: <answer>
                BUSINESS_LOGIC: <answer>
                CONDITIONAL_LOGIC: <answer or "None - processes all records uniformly">
                DATA_TRANSFORMATIONS: <answer or "None">
                DB_OPERATIONS: <answer or "None">
                FUNCTION_CALLS: <answer or "None">
                ERROR_HANDLING: <answer>
                OUTPUT: <answer>
                PATTERNS: <answer or "None">
                ISSUES: <answer or "None">
                """, className, truncateSource(sourceCode));
    }

    public String buildCustomReaderPrompt(String sourceCode, String className) {
        return String.format("""
                Analyze this Nimba custom reader class "%s" in detail for documentation purposes.

                Answer each question thoroughly:
                1. SUMMARY: A 2-3 sentence description of what this reader does and what data \
                it provides to downstream processors.
                2. DATA_SOURCE: What data source does it read from?
                   - Type: file, database, API, Kafka, message queue, etc.
                   - Connection details: JNDI names, URLs, table names, file paths
                3. FILE_FORMAT: If reading a file, describe the format in detail:
                   - Format type (fixed-width, CSV, delimited, XML, JSON)
                   - Delimiter characters, encoding, record separators
                   - Header/trailer handling
                4. QUERY_PATTERN: If reading from a database or API:
                   - SQL queries or API endpoints used
                   - Pagination or batching strategy
                   - Filter criteria or parameters
                5. CONNECTION_DETAILS: How does it manage connections?
                   - Connection pooling, datasource configuration
                   - Resource cleanup and closing
                6. FUNCTION_CALLS: Does it call any external services? List each with class \
                name and method.

                Source code:
                ```java
                %s
                ```

                Respond in this exact format:
                SUMMARY: <answer>
                DATA_SOURCE: <answer>
                FILE_FORMAT: <answer or "N/A">
                QUERY_PATTERN: <answer or "N/A">
                CONNECTION_DETAILS: <answer or "N/A">
                FUNCTION_CALLS: <answer or "None">
                """, className, truncateSource(sourceCode));
    }

    public String buildJobListenerPrompt(String sourceCode, String className) {
        return String.format("""
                Analyze this Nimba JobListener class "%s" in detail for documentation purposes.

                Answer each question thoroughly:
                1. SUMMARY: A 2-3 sentence description of what this listener does and why \
                it exists in the job lifecycle.
                2. ON_JOB_START: What does the onJobStart method do?
                   - What resources does it initialize?
                   - What setup or validation occurs?
                   - Does it set context variables for downstream steps?
                3. ON_JOB_FINISH: What does the onJobFinish method do?
                   - What cleanup occurs?
                   - Does it send notifications or update status?
                   - Does it handle success vs. failure differently?
                4. RESOURCE_MANAGEMENT: What resources are managed across the job lifecycle?
                   - Database connections, file handles, external connections
                   - Temporary files or directories
                   - State tracking variables
                5. FUNCTION_CALLS: Does it call any external services? List each with class \
                name and method.

                Source code:
                ```java
                %s
                ```

                Respond in this exact format:
                SUMMARY: <answer>
                ON_JOB_START: <answer>
                ON_JOB_FINISH: <answer>
                RESOURCE_MANAGEMENT: <answer or "None">
                FUNCTION_CALLS: <answer or "None">
                """, className, truncateSource(sourceCode));
    }

    public String buildJobSummaryPrompt(String jobAnalysisData) {
        return String.format("""
                Based on the following analysis data for a single Nimba batch job, write a concise \
                job-level summary in Markdown format. Do NOT include top-level headings (no # or ##).

                The summary MUST cover:

                1. **Purpose** - What this job does in 2-3 sentences. Describe the business function.

                2. **Nimbus Function Calls** (HIGH PRIORITY) - This is the most important section. \
                Nimbus functions contain the core business logic of the application. For EACH Nimbus \
                function call found (marked as "NIMBUS FUNCTIONS CALLED" in the data):
                   - Which step calls the function and what class makes the call
                   - What conditions or record types trigger the function call
                   - What data/parameters are passed to the function
                   - What the function does (e.g., sends email, posts payment, queries profile)
                   - If a step has conditional logic, describe EXACTLY which conditions lead to \
                     which Nimbus function calls (e.g., "IF record type is X, THEN call FunctionA; \
                     IF record type is Y, THEN call FunctionB")
                If no Nimbus functions are called, write "None".

                3. **Step-by-Step Flow** - A plain-English narrative of the complete flow. \
                Describe what happens when the job starts, what each step does in sequence, \
                how data flows between steps (especially when a processor reads from another step's \
                output or input via the source attribute), and how the job completes. \
                Emphasize which steps invoke Nimbus functions and under what conditions.

                4. **Data Flow** - Input sources (files, databases, APIs), data formats, \
                transformations through each step, datasource names used, and output destinations.

                5. **External Integrations** - Other WAS function/service calls beyond Nimbus functions. \
                Write "None" if no additional external calls.

                6. **Error Handling** - Error thresholds, BatchExitException usages with status codes, \
                failOnError settings, and resume/recovery behavior.

                7. **Operational Details** - Parallelism settings, resume capability, file archival, \
                and notable configuration parameters.

                Write it as a readable narrative for someone unfamiliar with the job. \
                Use bullet points where appropriate. Be specific -- reference actual class names and step IDs. \
                Give the highest importance and detail to Nimbus function calls and the conditions that trigger them.

                Job analysis data:
                %s
                """, truncateSource(jobAnalysisData));
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
