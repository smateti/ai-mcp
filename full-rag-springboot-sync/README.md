# Full RAG Sync (Spring Boot + JSP + java.net.http + Jackson)

This project is the "full RAG ready" version with **all synchronous** HTTP calls (no async / CompletableFuture).

## Features
- PDF/DOCX ingestion
- Chunking with overlap
- Embeddings via llama.cpp embedding server: POST http://localhost:8081/embedding
- Qdrant REST upsert/search: http://localhost:6333
- Chat via llama.cpp OpenAI-style chat completions (non-stream): POST http://localhost:8000/v1/chat/completions
- JSP UI:
  - GET `/`
  - POST `/upload`
  - POST `/ask`

## Run
```bash
mvn spring-boot:run
```

Open:
http://localhost:8080/

## Upload limits
Configured in `application.yml`:
- max-file-size: 200MB
- max-request-size: 200MB

## IMPORTANT: Create Qdrant collection with correct vector size
Vector size MUST equal embedding dimension (length of embedding[0]).

Example (size=384):
```bash
curl -X PUT "http://localhost:6333/collections/rag_chunks" \
  -H "Content-Type: application/json" \
  -d '{"vectors":{"size":384,"distance":"Cosine"}}'
```

## Qdrant collection auto-create
This version will automatically ensure the collection exists before upsert/search.

Set these in `application.yml`:
- `rag.qdrant.vectorSize` (required): embedding dimension
- `rag.qdrant.distance`: `Cosine` (default), `Dot`, or `Euclid`

## Troubleshooting: Qdrant "expected dim: 384, got 1"
If you see: `Vector dimension error: expected dim: 384, got 1`, it means your app is sending a 1-element vector like `[0.0]`.
This project parses embedding responses that may be 2D (`"embedding":[[...]]`) or 1D (`"embedding":[...]`) and flattens to 1D.

On first ingest, the app prints:
`[RAG] Detected embedding dimension=...`
Ensure `rag.qdrant.vectorSize` matches that number.

## LLM provider switch (llama.cpp vs Ollama)
Set `rag.llm.provider` in `application.yml`:
- `llamacpp` (default): uses llama.cpp REST endpoints (`/embedding`, `/v1/chat/completions`)
- `ollama`: uses Ollama REST endpoints (`/api/embed`, `/api/chat`)

Restart the app after changing provider.
