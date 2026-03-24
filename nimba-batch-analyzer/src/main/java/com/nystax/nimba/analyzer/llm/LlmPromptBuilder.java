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
                differently? For each condition or record type, describe what happens and which \
                function calls are made. Use this format for each:
                   IF <condition/record type> THEN <action and function calls made>
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

    public String buildProjectSummaryPrompt(String analysisData) {
        return String.format("""
                Based on the following analysis data from a Nimba batch project, write a comprehensive \
                project documentation in Markdown format.

                The document MUST include these sections with detailed content:

                1. **Project Overview** - What this batch application does, its purpose, and the \
                business domain it serves. Summarize the overall architecture (number of jobs, \
                types of processing).

                2. **Job Flow Summary** - For EACH job, provide a plain-English narrative of the \
                complete step-by-step flow. Describe what happens when the job starts, what each \
                step does, how data flows between steps, and how the job completes. Include what \
                conditions trigger different function calls. Group related jobs if they share patterns.

                3. **Data Flow** - Describe the complete data pipeline:
                   - Input sources (files, databases, APIs)
                   - Data formats and record structures
                   - Transformation chain through each step
                   - Output destinations and formats

                4. **External Integrations** - For each WAS function dependency:
                   - What service it connects to
                   - Which jobs/steps use it
                   - What data is sent and received
                   - Purpose of the integration

                5. **Error Handling Strategy** - Comprehensive error handling documentation:
                   - Error thresholds per step and their implications
                   - BatchExitException usage with status codes and meanings
                   - failOnError settings and their impact
                   - Recovery and resume capabilities

                6. **Operational Details** - Runtime and deployment information:
                   - File archival configuration
                   - Parallelism settings and thread usage
                   - Resume capability and checkpoint behavior
                   - Configuration parameters and their purposes

                7. **Technical Notes** - Any notable patterns, potential issues, or recommendations \
                observed across the codebase.

                Write it as a readable technical document for someone unfamiliar with the project. \
                Use clear headings, bullet points, and tables where appropriate. \
                Be specific -- reference actual class names, job IDs, and step IDs.

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
