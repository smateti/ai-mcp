# Chat History Feature Added to MCP Chat App

## Overview

Successfully added Claude-style conversation history to the MCP Chat App! Now you can:
- ✅ Create and manage multiple conversations
- ✅ View conversation history in a sidebar
- ✅ Store all messages persistently in H2 database
- ✅ Beautiful Claude-inspired UI
- ✅ Use MCP tools with full conversation context

## What Was Added

### 1. Database Layer (H2 + JPA)

**New Dependencies:**
- H2 Database (in-memory file storage)
- Spring Data JPA

**Database Tables:**
- `conversations` - Stores chat conversations
- `messages` - Stores individual messages (user/assistant)

**Database Location:** `./data/mcp-chatdb`

### 2. Entity Classes

**Files Created:**
- `Conversation.java` - Conversation entity with title, timestamps, and messages
- `Message.java` - Message entity with role (user/assistant) and content

### 3. Repositories

**Files Created:**
- `ConversationRepository.java` - CRUD for conversations
- `MessageRepository.java` - CRUD for messages

### 4. Services

**Files Created:**
- `ConversationService.java` - Business logic for managing conversations
  - Create conversations
  - Add messages
  - Retrieve conversation history
  - Update titles
  - Delete conversations

### 5. REST API

**New Controller:**
- `ConversationHistoryController.java`

**Endpoints:**
```
GET    /api/conversations              - List all conversations
POST   /api/conversations              - Create new conversation
GET    /api/conversations/{id}/messages - Get messages for a conversation
POST   /api/conversations/{id}/messages - Send message (uses MCP tools)
DELETE /api/conversations/{id}         - Delete conversation
PUT    /api/conversations/{id}/title   - Update conversation title
```

### 6. Chat UI

**File:** `/static/chat.html`

**Features:**
- Left sidebar with conversation list
- Create new conversations with "+" button
- Click to switch between conversations
- Messages persist across page refreshes
- Auto-scroll to latest messages
- Clean, responsive Claude-style design

## How It Works

### Architecture Flow

```
User asks question in UI
        ↓
ConversationHistoryController
        ↓
ConversationService (save user message)
        ↓
ChatService.processMessage()
        ↓
    ┌─────────────┐
    │  MCP Tools  │
    │  Selection  │
    └─────────────┘
        ↓
LlmToolSelectionService (Ollama)
        ↓
Tool Execution via MCP Server
        ↓
Natural Answer Generation
        ↓
ConversationService (save assistant message)
        ↓
Return to UI
```

### Integration with Existing Features

**MCP Tools Integration:**
- When you send a message, it uses the existing `ChatService`
- LLM-powered tool selection works automatically
- Tools execute via MCP server
- Results formatted as natural language

**Session Management:**
- Each conversation gets a unique session ID: `conv_{id}`
- This allows the ChatService to maintain context per conversation

## Configuration

### application.yml

```yaml
spring:
  # H2 Database for chat history
  datasource:
    url: jdbc:h2:file:./data/mcp-chatdb
    driver-class-name: org.h2.Driver
    username: sa
    password:

  h2:
    console:
      enabled: true
      path: /h2-console

  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: update
    show-sql: false
```

### H2 Console Access

- **URL:** http://localhost:8083/h2-console
- **JDBC URL:** `jdbc:h2:file:./data/mcp-chatdb`
- **Username:** `sa`
- **Password:** (empty)

## Usage

### 1. Start the MCP Chat App

```bash
cd mcp-chat-app
java -jar target/mcp-chat-app-1.0.0.jar
```

### 2. Open Chat UI

Navigate to: http://localhost:8083/chat.html

### 3. Create Your First Conversation

1. Click **"+ New Chat"** button
2. Type your message
3. Press Enter or click Send

### 4. Using MCP Tools

The chat automatically uses MCP tools when relevant:

**Example Questions:**
- "Add 3 and 5" → Uses `add` tool
- "What's the current time?" → Uses `get_current_time` tool
- "Get user with id 5" → Uses `jsonplaceholder-user` tool

### 5. Managing Conversations

- **Switch:** Click any conversation in the sidebar
- **New:** Click "+ New Chat"
- **Delete:** (Future feature - can be added)
- **Rename:** (Future feature - can be added)

## API Examples

### Create a Conversation

```bash
curl -X POST http://localhost:8083/api/conversations \
  -H "Content-Type: application/json" \
  -d '{"title":"My First Chat"}'
```

### Send a Message

```bash
curl -X POST http://localhost:8083/api/conversations/1/messages \
  -H "Content-Type: application/json" \
  -d '{"message":"What is 2+2?"}'
```

### Get All Conversations

```bash
curl http://localhost:8083/api/conversations
```

### Get Messages from a Conversation

```bash
curl http://localhost:8083/api/conversations/1/messages
```

## File Structure

```
mcp-chat-app/
├── src/main/java/com/example/chat/
│   ├── entity/
│   │   ├── Conversation.java       ← NEW
│   │   └── Message.java            ← NEW
│   ├── repository/
│   │   ├── ConversationRepository.java    ← NEW
│   │   └── MessageRepository.java         ← NEW
│   ├── service/
│   │   ├── ConversationService.java       ← NEW
│   │   └── ChatService.java        (existing)
│   └── controller/
│       ├── ConversationHistoryController.java  ← NEW
│       ├── ChatController.java     (existing)
│       └── StreamingChatController.java (existing)
├── src/main/resources/
│   ├── application.yml             ← UPDATED
│   └── static/
│       └── chat.html               ← NEW
└── data/
    └── mcp-chatdb.mv.db           ← AUTO-CREATED
```

## Features Comparison

### Before (Original MCP Chat App)
- ❌ No conversation history
- ❌ No persistent storage
- ❌ No UI for chat
- ✅ MCP tool selection
- ✅ Natural language responses

### After (With Chat History)
- ✅ **Full conversation history**
- ✅ **Persistent H2 database**
- ✅ **Beautiful Claude-style UI**
- ✅ **Multiple conversations**
- ✅ MCP tool selection
- ✅ Natural language responses

## Benefits

### 1. Persistent Conversations
- All chats saved to database
- Survive application restarts
- Never lose conversation context

### 2. Better User Experience
- Sidebar with all conversations
- Easy switching between chats
- Visual conversation history

### 3. Context Preservation
- Each conversation maintains its own context
- Session management per conversation
- Better tool usage with context

### 4. Data Management
- Query conversations via SQL
- Export conversation data
- Analyze user interactions

## Testing

### Test the UI
1. Open http://localhost:8083/chat.html
2. Create a new conversation
3. Ask: "Add 5 and 10"
4. Verify it uses the `add` tool
5. Refresh the page
6. Verify conversation is still there

### Test the API
```bash
# Create conversation
CONV_ID=$(curl -s -X POST http://localhost:8083/api/conversations \
  -H "Content-Type: application/json" \
  -d '{"title":"API Test"}' | jq -r '.id')

# Send message
curl -X POST http://localhost:8083/api/conversations/$CONV_ID/messages \
  -H "Content-Type: application/json" \
  -d '{"message":"What is the current time?"}'

# View messages
curl http://localhost:8083/api/conversations/$CONV_ID/messages
```

## Database Schema

### conversations Table
```sql
CREATE TABLE conversations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

### messages Table
```sql
CREATE TABLE messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  role VARCHAR(50) NOT NULL,      -- 'user' or 'assistant'
  content TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  FOREIGN KEY (conversation_id) REFERENCES conversations(id)
);
```

## Future Enhancements

### Potential Features to Add:
1. **Conversation Search** - Search across all conversations
2. **Export Conversations** - Download as JSON/PDF
3. **Conversation Sharing** - Share conversation links
4. **Message Editing** - Edit past messages
5. **Conversation Folders** - Organize conversations
6. **Tags/Labels** - Categorize conversations
7. **Favorites** - Star important conversations
8. **Message Reactions** - Like/dislike responses
9. **Context Window** - Limit messages sent to LLM
10. **Conversation Analytics** - Usage statistics

## Troubleshooting

### Database Issues

**Problem:** Database not found
**Solution:** Check `./data/mcp-chatdb.mv.db` exists. If not, restart app to create it.

**Problem:** Can't connect to H2 console
**Solution:** Ensure app is running and visit http://localhost:8083/h2-console

### API Issues

**Problem:** 404 on /api/conversations
**Solution:** Ensure app is running on port 8083

**Problem:** Conversation not found
**Solution:** Check conversation ID exists with GET /api/conversations

### UI Issues

**Problem:** Chat UI not loading
**Solution:** Check http://localhost:8083/chat.html (not /chat)

**Problem:** Messages not appearing
**Solution:** Check browser console for errors, verify API is working

## Summary

✅ **Chat history fully integrated into MCP Chat App**
✅ **H2 database for persistent storage**
✅ **Beautiful Claude-style UI at /chat.html**
✅ **Full REST API for conversation management**
✅ **Seamless integration with MCP tools**
✅ **Ready to use at http://localhost:8083/chat.html**

The MCP Chat App now has a complete conversation history system with a professional UI, just like Claude!
