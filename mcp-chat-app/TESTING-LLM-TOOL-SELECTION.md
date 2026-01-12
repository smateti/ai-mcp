# Testing LLM-Powered Tool Selection

## Quick Test Commands

### Test 1: Time Query (Natural Language)
```bash
curl -X POST http://localhost:8083/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"test","message":"What time is it?"}'
```

**Expected:** LLM selects `get_current_time` with high confidence

---

### Test 2: Echo (Natural Language)
```bash
curl -X POST http://localhost:8083/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"test","message":"Echo hello world"}'
```

**Expected:** LLM selects `echo` with high confidence

---

### Test 3: Add (Slash Command - Fast Path)
```bash
curl -X POST http://localhost:8083/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"test","message":"/add 25 17"}'
```

**Expected:** Direct execution, result = 42.0

---

### Test 4: User API (Slash Command)
```bash
curl -X POST http://localhost:8083/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"test","message":"/jsonplaceholder-user 1"}'
```

**Expected:** User info for Leanne Graham

---

### Test 5: Help Command
```bash
curl -X POST http://localhost:8083/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"test","message":"/help"}'
```

**Expected:** List of all available tools

---

## Browser Testing

1. Open: http://localhost:8083

2. Try these queries:
   - "What time is it?"
   - "Echo test message"
   - "/add 10 20"
   - "/get_current_time"

---

## Response Format

### Successful LLM Selection
```json
{
  "content": "✅ Tool executed: get_current_time\n\nTime: 2026-01-11T23:31:14Z\n\n_LLM selected this tool with 95% confidence_",
  "metadata": {
    "tool": "get_current_time",
    "llmConfidence": 0.95,
    "llmReasoning": "User's question directly asks for current time",
    "selectionMode": "llm"
  }
}
```

### Slash Command (No LLM)
```json
{
  "content": "✅ Tool executed: add\n\nResult: 42.0",
  "metadata": {
    "tool": "add"
  }
}
```

---

## Troubleshooting

**Issue:** LLM not responding
- **Check:** Is Ollama running? `curl http://localhost:11434/api/tags`
- **Fix:** Start Ollama service

**Issue:** Tool not found
- **Check:** Is MCP server running? `curl http://localhost:8082/mcp/test`
- **Fix:** Start MCP server

**Issue:** Chat app not responding
- **Check:** `curl http://localhost:8083/api/chat/health`
- **Fix:** Restart chat app
