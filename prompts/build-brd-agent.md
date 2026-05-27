You are building a Java agent application that drives a locally-running 
Llama 3.1 model to extract BRD JSON from FoxPro source code. The agent 
must conform to @prompts/brd-schema.md and use @prompts/brd-llama.md as 
the Llama system prompt.

Target stack:
- Java 17
- MicroProfile 6.x on Open Liberty (latest LTS)
- Maven build with liberty-maven-plugin
- CDI for all components
- JAX-RS for the control API
- MicroProfile Rest Client for llama.cpp HTTP calls
- MicroProfile Config for all configuration
- MicroProfile Health + Metrics + OpenTelemetry
- Jakarta Validation on DTOs
- Jakarta JSON-B + JSON-P for marshalling
- SLF4J for logging
- Deployable to OpenShift via Docker

Architecture (CDI scopes specified):

@ApplicationScoped components:
- LlamaClient — MicroProfile Rest Client targeting OpenAI-compatible 
  /v1/chat/completions on llama.cpp; configurable base URL, model, 
  temperature, top_p, max_tokens; supports JSON mode and optional 
  GBNF grammar constraint
- BrdSchemaValidator — loads brd-schema (JSON Schema form); validates 
  Llama output; returns structured violation list
- PromptLoader — loads brd-llama.md once at startup; caches it
- FoxProSourceScanner — walks legacy/ directory; filters by file type 
  (.prg, .sca, .fra, .mna, .dbf-schema.json); produces a Stream of 
  SourceFile records
- FoxProChunker — for .prg files over a configurable line threshold, 
  splits at PROCEDURE/FUNCTION boundaries; preserves header comments 
  with each chunk; never splits inside a procedure body
- SnippetVerifier — confirms every source_snippet in a partial BRD 
  appears as a substring (whitespace-normalized) of the original file
- IdRenumberer — converts FR-temp-001 etc. to stable FR-001 with 
  deterministic ordering across the unit
- BrdMerger — combines per-file partial BRDs into a unit-level BRD 
  matching brd-schema; deduplicates data_entities by name with 
  field-union semantics; collects open_questions
- ProgressTracker — persists per-file processing state to disk so 
  the agent is resumable after crashes

@RequestScoped:
- BrdExtractionAgent — the core agent loop (described below)

JAX-RS resources:
- POST /extract — kicks off extraction for a unit; body: 
  { "unitName": "string", "sourcePaths": ["string"] }
- GET /extract/{unitName}/status — returns progress
- GET /health/* — MicroProfile Health endpoints
- GET /metrics — MicroProfile Metrics

Agent loop (BrdExtractionAgent.extractFromFile):

  input: SourceFile (path, type, content)
  output: PartialBrd JSON, or ExtractionFailure

  1. Build user message: FILE_PATH, FILE_TYPE, CONTENT blocks
  2. Call LlamaClient with system=brd-llama.md, user=above, json_mode=true
  3. Parse response as JSON
     - On parse failure: log raw response, retry up to 2 times with 
       an appended corrective system message: "Your previous response 
       was not valid JSON. Return ONLY the JSON object, no prose."
  4. Validate against brd-schema (partial-schema subset for per-file output)
     - On validation failure: retry once with a corrective message 
       containing the specific violation list
  5. SnippetVerifier checks every source_snippet against the file
     - On hallucinated snippet: retry once with a corrective message 
       listing the missing snippets and instructing the model to drop 
       those entries rather than invent
  6. On success: write partial to brd/llama-parts/<file>.json
  7. On unrecoverable failure after retries: write a failure record 
     to brd/llama-failures/<file>.json with all retry attempts and 
     the final state, then continue to the next file

  Cap total retries at 4. Track retry count in metrics.

Orchestration (BrdUnitExtractor):
- Process files within a unit using a bounded ManagedExecutorService 
  (CompletionService pattern); concurrency limit from MP Config 
  (default 2, tuned to llama.cpp server's slot count)
- For chunked files: process chunks sequentially per file (preserves 
  model warmup), but different files in parallel
- After all files processed: BrdMerger → IdRenumberer → write 
  brd/llama/<unit>.json

Configuration (microprofile-config.properties):
- llama.endpoint=http://localhost:8000
- llama.model=llama-3.1-8b-instruct
- llama.temperature=0.1
- llama.top_p=0.9
- llama.max_tokens=4096
- llama.timeout_seconds=300
- llama.json_mode=true
- llama.gbnf_grammar_path=  # optional, empty = disabled
- agent.concurrency=2
- agent.max_retries=4
- agent.chunk_line_threshold=400
- paths.source_root=./legacy
- paths.output_root=./brd
- paths.schema=./prompts/brd-schema.json
- paths.system_prompt=./prompts/brd-llama.md

Metrics to expose:
- brd_files_processed_total (counter, labels: file_type, outcome)
- brd_extraction_duration_seconds (histogram, labels: file_type)
- brd_llama_tokens_total (counter, labels: direction=prompt|completion)
- brd_validation_failures_total (counter, labels: failure_type)
- brd_retry_count (histogram)
- brd_hallucinated_snippets_total (counter)

Health checks:
- LlamaConnectivityCheck — calls /v1/models on llama.cpp endpoint
- DiskSpaceCheck — verifies output directory writable with > 1GB free
- PromptFilesCheck — verifies schema + system prompt files exist and parse

Testing:
- Unit tests for FoxProChunker (procedure boundaries, nested procedures, 
  header comment preservation, files with no procedures)
- Unit tests for BrdMerger (entity dedup, field union, ID renumbering)
- Unit tests for SnippetVerifier (whitespace normalization, multi-line 
  snippets, Unicode)
- Unit tests for BrdSchemaValidator
- Integration test with a mock LlamaClient (@InjectMock or wiremock) 
  returning canned responses for happy/parse-error/schema-error/
  hallucination/timeout paths — verify retry logic
- One end-to-end test with a 3-file FoxPro mini-app fixture in 
  src/test/resources/fixtures/mini-app/ and a real llama.cpp endpoint 
  (toggleable via Maven profile, skipped in CI by default)

Project layout:
  src/main/java/com/<org>/brdagent/
    client/         — LlamaClient + DTOs
    scanner/        — FoxProSourceScanner, FoxProChunker
    agent/          — BrdExtractionAgent, retry policy
    validation/     — BrdSchemaValidator, SnippetVerifier
    merge/          — BrdMerger, IdRenumberer
    orchestration/  — BrdUnitExtractor, ProgressTracker
    rest/           — JAX-RS resources, DTOs
    config/         — Config beans, health checks
    metrics/        — Metrics producers
  src/main/liberty/config/server.xml
  src/main/resources/META-INF/microprofile-config.properties
  src/main/resources/brd-schema.json    — JSON Schema derived from 
                                          brd-schema.md
  src/test/...
  Dockerfile
  openshift/                            — Deployment, Service, Route, 
                                          ConfigMap, Secret manifests
  pom.xml

Quality requirements:
- No static mutable state, no swallowed exceptions, no System.out
- All HTTP calls have explicit connect + read timeouts
- All file I/O uses try-with-resources
- All log statements use SLF4J parameterized form, never string concat
- Every public method on @ApplicationScoped beans has a unit test
- LlamaClient response parsing is defensive: handle empty content, 
  truncated JSON (max_tokens hit), trailing whitespace, 
```json fences (strip them), tool-call-style responses
- Resumability: re-running extraction skips files already in 
  brd/llama-parts/ unless --force flag is set
- No secret values in microprofile-config.properties; sensitive values 
  come from environment variables and are documented in README

Build first the smallest end-to-end slice (golden slice):
  1. pom.xml with all dependencies
  2. server.xml configured for Liberty + MicroProfile features
  3. LlamaClient + request/response DTOs
  4. FoxProSourceScanner (one file type only: .prg)
  5. BrdExtractionAgent with no retry logic, no chunking
  6. One JAX-RS endpoint (POST /extract) that processes a single file
  7. One unit test + one integration test against a mock LlamaClient

Get that running with `mvn liberty:dev` against a real llama.cpp 
instance. Confirm it produces a valid partial BRD for one .prg file. 
Only then expand to chunking, retry loop, merge, full file-type 
support, observability, OpenShift manifests.

For each piece of code, add a `// PROVENANCE:` comment indicating which 
requirement above it implements (e.g. // PROVENANCE: agent loop step 5, 
snippet verification). This makes review tractable.

End your work with a status doc at docs/agent-build-status.md listing:
- Decisions you made that the spec didn't pin down
- Things you couldn't determine and need a human answer for
- Any place where you punted on a quality requirement and why
- Suggested next iteration
```