# Testing All MCP Tools - Complete Guide

**Application:** MCP Chat with Full Tool Support
**URL:** http://localhost:8083
**Status:** ✅ All Tools Available

---

## 🎯 Quick Test Commands

Open http://localhost:8083 in your browser and try these commands:

### 1. Get Help
```
/help
```
Shows all available tools with usage examples

### 2. Echo Tool
```
/echo Hello from MCP!
```
**Expected Response:**
```
✅ Tool executed: `echo`

**Echo:** Hello from MCP!
```

### 3. Add Tool
```
/add 42 58
```
**Expected Response:**
```
✅ Tool executed: `add`

**Result:** 100.0
```

### 4. Get Current Time
```
/get_current_time
```
**Expected Response:**
```
✅ Tool executed: `get_current_time`

**Time:** 2026-01-11T22:52:45.123Z
```

### 5. JSONPlaceholder User (External API)
```
/jsonplaceholder-user 1
```
**Expected Response:**
```
✅ Tool executed: `jsonplaceholder-user`

**User Information:**

- **ID:** 1
- **Name:** Leanne Graham
- **Email:** Sincere@april.biz
- **Phone:** 1-770-736-8031 x56442
- **Website:** hildegard.org
- **Company:** Romaguera-Crona
```

### 6. RAG Ingest (via command)
```
/rag_ingest test-ml Machine Learning is a subset of artificial intelligence that enables systems to learn and improve from experience.
```
**Expected Response:**
```
✅ Tool executed: `rag_ingest`

Document 'test-ml' ingested successfully with X chunks
```

### 7. RAG Query (via command)
```
/rag_query What is machine learning?
```
**Expected Response:**
```
✅ Tool executed: `rag_query`

[Answer with sources and relevance scores]
```

---

## 📋 Complete Tool Reference

### Built-in Tools

#### `/echo <message>`
**Purpose:** Echoes back the message
**Arguments:**
- `message` (string, required) - The message to echo

**Examples:**
```
/echo Hello World
/echo This is a test message
/echo 🚀 Testing emojis!
```

**Use Case:** Test MCP connectivity and tool execution

---

#### `/add <number1> <number2>`
**Purpose:** Adds two numbers together
**Arguments:**
- `number1` (number, required) - First number
- `number2` (number, required) - Second number

**Examples:**
```
/add 5 10
/add 3.14 2.86
/add 100 -50
```

**Use Case:** Test numeric parameter handling

---

#### `/get_current_time`
**Purpose:** Returns the current server time
**Arguments:** None

**Examples:**
```
/get_current_time
```

**Use Case:** Test tools with no parameters

---

### External API Tools

#### `/jsonplaceholder-user <userId>`
**Purpose:** Fetches user information from JSONPlaceholder API
**Arguments:**
- `userId` (integer, required) - User ID (valid range: 1-10)

**Examples:**
```
/jsonplaceholder-user 1
/jsonplaceholder-user 5
/jsonplaceholder-user 10
```

**Response Includes:**
- User ID
- Name
- Email
- Phone
- Website
- Company name

**Use Case:** Test external HTTP API integration via MCP

---

### RAG Tools

#### `/rag_ingest <docId> <text>`
**Purpose:** Ingests a document into the RAG knowledge base
**Arguments:**
- `docId` (string, required) - Unique identifier for the document
- `text` (string, required) - Full text content of the document

**Examples:**
```
/rag_ingest python-basics Python is a high-level programming language known for its simplicity and readability.

/rag_ingest ai-guide Artificial Intelligence (AI) refers to the simulation of human intelligence in machines.
```

**Use Case:** Test document ingestion with explicit tool command

---

#### `/rag_query <question> [topK=N]`
**Purpose:** Queries the RAG knowledge base
**Arguments:**
- `question` (string, required) - The question to answer
- `topK` (integer, optional) - Number of chunks to retrieve (default: 5)

**Examples:**
```
/rag_query What is Python?
/rag_query Tell me about artificial intelligence topK=3
/rag_query Explain machine learning topK=10
```

**Use Case:** Test RAG querying with explicit tool command

---

## 🧪 Comprehensive Test Suite

### Test Script (Copy/Paste into Chat)

**Test 1: Help System**
```
/help
```
✅ Should display all available tools

---

**Test 2: Echo Tool**
```
/echo Testing MCP echo functionality
```
✅ Should echo back: "Testing MCP echo functionality"

---

**Test 3: Add Tool - Integers**
```
/add 25 75
```
✅ Should return: 100.0

---

**Test 4: Add Tool - Decimals**
```
/add 3.14159 2.71828
```
✅ Should return: 5.85987

---

**Test 5: Current Time**
```
/get_current_time
```
✅ Should return current ISO timestamp

---

**Test 6: External API - User 1**
```
/jsonplaceholder-user 1
```
✅ Should return Leanne Graham's info

---

**Test 7: External API - User 5**
```
/jsonplaceholder-user 5
```
✅ Should return Chelsey Dietrich's info

---

**Test 8: RAG Ingest via Command**
```
/rag_ingest space-doc Space exploration involves the discovery and exploration of outer space by means of space technology.
```
✅ Should confirm ingestion with chunk count

---

**Test 9: RAG Query via Command**
```
/rag_query What is space exploration?
```
✅ Should return answer with sources

---

**Test 10: Natural RAG Query (No Command)**
```
What is the capital of France?
```
✅ Should auto-detect as query and use rag_query tool

---

**Test 11: Natural Document Ingestion**
```
[Paste 600+ character document here]

Machine learning (ML) is a field of artificial intelligence that uses statistical techniques to give computer systems the ability to "learn" from data, without being explicitly programmed. The name machine learning was coined in 1959 by Arthur Samuel. Evolved from the study of pattern recognition and computational learning theory in artificial intelligence, machine learning explores the study and construction of algorithms that can learn from and make predictions on data.
```
✅ Should auto-detect as ingestion

---

## 🔍 Testing via curl (Alternative)

If you prefer command-line testing:

```bash
# Create session
SESSION_ID=$(curl -s -X POST http://localhost:8083/api/chat/session | grep -o '"sessionId":"[^"]*"' | cut -d'"' -f4)

# Test echo
curl -X POST http://localhost:8083/api/chat/message \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"/echo Hello World\"}"

# Test add
curl -X POST http://localhost:8083/api/chat/message \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"/add 42 58\"}"

# Test get_current_time
curl -X POST http://localhost:8083/api/chat/message \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"/get_current_time\"}"

# Test jsonplaceholder-user
curl -X POST http://localhost:8083/api/chat/message \
  -H "Content-Type: application/json" \
  -d "{\"sessionId\":\"$SESSION_ID\",\"message\":\"/jsonplaceholder-user 1\"}"

# Get chat history
curl "http://localhost:8083/api/chat/history/$SESSION_ID"
```

---

## 📊 Expected Behavior

### Success Indicators
- ✅ Each tool returns `✅ Tool executed: tool_name`
- ✅ Response includes formatted results
- ✅ Metadata shows correct tool name
- ✅ No error messages in response

### Error Indicators
- ❌ `Invalid arguments for tool`
- ❌ `Tool execution failed`
- ❌ `Error: ...`

---

## 🎨 UI Testing

### Visual Elements to Verify

**Tool Execution Badge:**
```
🔧 Tool: [tool_name]
```
Should appear below assistant messages

**Status Indicators:**
- Green dot (●) = Connected to MCP
- Red dot (●) = Disconnected
- Tool count displayed in header

**Message Styling:**
- User messages: Blue background, right-aligned
- Assistant messages: Dark background, left-aligned
- Code blocks: Monospace font, highlighted
- Tool results: Formatted nicely

---

## 🐛 Troubleshooting

### Issue: "Invalid arguments"

**Cause:** Incorrect command syntax

**Solutions:**
```
❌ /add 5                    (missing second argument)
✅ /add 5 10                (correct)

❌ /echo                     (missing message)
✅ /echo Hello              (correct)

❌ /jsonplaceholder-user     (missing userId)
✅ /jsonplaceholder-user 1  (correct)
```

### Issue: "Tool execution failed"

**Possible Causes:**
1. MCP server is down
2. RAG application is down (for RAG tools)
3. Network connectivity issues (for jsonplaceholder)

**Check:**
```bash
curl http://localhost:8082/mcp/test  # MCP server
curl http://localhost:8080/api/rag/health  # RAG app
```

### Issue: "MCP Server disconnected"

**Solution:**
```bash
# Restart MCP server
cd D:\apps\ws\ws8\mcp-spring-boot-server
mvn spring-boot:run
```

---

## 📚 Tool Categories

### 1. **Testing Tools** (echo, add, get_current_time)
- **Purpose:** Verify MCP connectivity
- **Use:** Development and debugging
- **No external dependencies**

### 2. **External API Tools** (jsonplaceholder-user)
- **Purpose:** Demonstrate HTTP API integration
- **Use:** Test external service calls via MCP
- **Requires:** Internet connection

### 3. **RAG Tools** (rag_ingest, rag_query)
- **Purpose:** Document-based Q&A
- **Use:** Production knowledge base queries
- **Requires:** RAG app + Qdrant + Ollama

---

## 🎯 Use Cases

### Development Testing
```
/echo test
/add 1 1
/get_current_time
```
Quick verification that MCP is working

### External API Integration
```
/jsonplaceholder-user 3
```
Test calling external REST APIs through MCP

### Document Q&A
```
/rag_ingest doc1 [content]
/rag_query [question]
```
Or just type naturally!

---

## 🌟 Advanced Tips

### 1. Command History
Press ↑/↓ arrow keys to navigate command history (browser-dependent)

### 2. Multi-line Input
Use Shift+Enter for newlines in messages

### 3. Batch Testing
Open browser DevTools Console:
```javascript
// Send multiple commands
const commands = [
  '/echo test1',
  '/add 1 1',
  '/get_current_time'
];

commands.forEach(cmd => {
  fetch('/api/chat/message', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({sessionId: 'YOUR_SESSION', message: cmd})
  });
});
```

### 4. Tool Performance Monitoring
Check metadata in responses for execution times

---

## ✅ Verification Checklist

- [ ] `/help` shows all 6 tools
- [ ] `/echo` works with various messages
- [ ] `/add` handles integers and decimals
- [ ] `/get_current_time` returns valid timestamp
- [ ] `/jsonplaceholder-user` fetches real user data
- [ ] `/rag_ingest` accepts documents
- [ ] `/rag_query` returns answers with sources
- [ ] Natural language queries work (auto-detect)
- [ ] Error messages are clear and helpful
- [ ] Tool badges appear in UI

---

## 📖 Related Documentation

- **Main Docs:** [MCP-CHAT-APP-COMPLETE.md](MCP-CHAT-APP-COMPLETE.md)
- **Integration Guide:** [RAG-MCP-INTEGRATION-DESIGN.md](../RAG-MCP-INTEGRATION-DESIGN.md)
- **MCP Server:** [mcp-spring-boot-server/README.md](../mcp-spring-boot-server/README.md)

---

**Happy Testing! 🚀**

All tools are ready to use at http://localhost:8083
