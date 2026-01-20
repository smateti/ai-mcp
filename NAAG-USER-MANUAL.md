# NAAG User Manual

**NAAG = Nimbus AI Agent Platform**

This manual provides guidance for users of the NAAG chat interface.

---

## Table of Contents

1. [Getting Started](#1-getting-started)
2. [Chat Interface Overview](#2-chat-interface-overview)
3. [Sending Messages](#3-sending-messages)
4. [Using Categories](#4-using-categories)
5. [Working with Tools](#5-working-with-tools)
6. [Asking Questions (RAG)](#6-asking-questions-rag)
7. [Chat History](#7-chat-history)
8. [My Activity](#8-my-activity)
9. [Settings](#9-settings)
10. [Tips and Best Practices](#10-tips-and-best-practices)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Getting Started

### Accessing NAAG Chat

Open your web browser and navigate to:

```
http://localhost:8087
```

### What Can NAAG Do?

NAAG is an AI-powered assistant that can:

| Capability | Example |
|------------|---------|
| Answer questions | "What is machine learning?" |
| Perform calculations | "What is 25 plus 17?" |
| Execute tools | "What time is it?" |
| Search knowledge base | "Find information about project X" |
| Echo messages | "Echo hello world" |

### Your First Conversation

1. Type a message in the input box at the bottom
2. Press Enter or click the Send button
3. Wait for NAAG's response
4. Continue the conversation naturally

---

## 2. Chat Interface Overview

### Main Components

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  NAAG Chat                                     [Category: General ▼] [⚙️]   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                           Chat Messages Area                                 │
│                     (Your conversation appears here)                         │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────┐ [📤]  │
│  │ Type your message...                                             │       │
│  └─────────────────────────────────────────────────────────────────┘       │
│                                                                             │
│  [📜 History] [🔧 Tools] [📊 My Activity]              User: your-name     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Interface Elements

| Element | Purpose |
|---------|---------|
| Category Dropdown | Select which category of tools/documents to use |
| Settings (gear icon) | Adjust your preferences |
| Message Input | Type your questions and commands |
| Send Button | Send your message |
| History | View past conversations |
| Tools | See available tools |
| My Activity | View your usage statistics |

---

## 3. Sending Messages

### Natural Language

Simply type your question or request naturally:

- "What is the capital of France?"
- "Can you help me understand machine learning?"
- "Add 50 and 75 together"
- "What's the current time?"

### Explicit Tool Commands

You can explicitly call tools using slash commands:

```
/tool_name parameter1 parameter2
```

Examples:
- `/add 25 17` - Adds 25 and 17
- `/echo Hello world` - Echoes "Hello world"
- `/get_current_time` - Shows current time
- `/rag_query What is AI?` - Searches the knowledge base

### Message Formatting

- Messages support **Markdown** formatting
- Use `code` for inline code
- Use code blocks for longer code snippets

---

## 4. Using Categories

### What are Categories?

Categories organize tools and knowledge by topic:

| Category | Description |
|----------|-------------|
| General | General-purpose tools and knowledge |
| Engineering | Technical documentation and tools |
| HR | Human resources information |
| Finance | Financial tools and documents |
| IT Support | Technical support knowledge |

### Selecting a Category

1. Click the **Category** dropdown in the top-right
2. Select the category that matches your needs
3. Available tools and knowledge will be filtered

### Why Use Categories?

- **Focused responses**: AI considers only relevant tools
- **Better answers**: RAG searches category-specific documents
- **Faster**: Fewer tools to evaluate means faster responses

### Tips

- Use "General" if unsure which category to choose
- Switch categories based on your current task
- Ask your administrator about available categories

---

## 5. Working with Tools

### Understanding Tool Responses

When NAAG uses a tool, you'll see:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 🤖 NAAG                                              10:30 AM            │
│ The sum of 25 and 17 is **42**.                                         │
│                                                                         │
│ ┌───────────────────────────────────────────────────────────────┐      │
│ │ 🔧 Tool: add | ⏱️ 123ms | ✅ Success | Confidence: 95%        │      │
│ └───────────────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────┘
```

### Tool Execution Info

| Field | Meaning |
|-------|---------|
| Tool | Which tool was used |
| Time | How long it took |
| Status | Success or failure |
| Confidence | How sure the AI was about using this tool |

### Viewing Available Tools

1. Click **🔧 Tools** at the bottom of the chat
2. Browse available tools for your selected category
3. See tool descriptions and examples
4. Click **Try it** to use a tool

### Common Tools

| Tool | Purpose | Example |
|------|---------|---------|
| add | Add two numbers | "What is 100 plus 200?" |
| echo | Repeat a message | "Echo: Hello world" |
| get_current_time | Get current time | "What time is it?" |
| rag_query | Search knowledge base | "What is machine learning?" |

### When Tools Are Used

The AI automatically selects tools based on your message:

| Your Message | Tool Selected |
|--------------|---------------|
| "Add 5 and 10" | add |
| "What time is it?" | get_current_time |
| "Repeat: hello" | echo |
| "What is Python?" | rag_query |

---

## 6. Asking Questions (RAG)

### What is RAG?

RAG (Retrieval-Augmented Generation) searches a knowledge base to answer your questions with accurate, sourced information.

### RAG Responses

When NAAG uses RAG, you'll see sources:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 🤖 NAAG                                              10:32 AM            │
│ Machine learning is a subset of artificial intelligence that enables    │
│ systems to learn and improve from experience without being explicitly   │
│ programmed...                                                           │
│                                                                         │
│ 📚 Sources:                                                             │
│ • ml-fundamentals.pdf (pages 1-3)                                       │
│ • ai-overview.docx (section 2)                                          │
│                                                                         │
│ ┌───────────────────────────────────────────────────────────────┐      │
│ │ 📖 RAG Query | ⏱️ 456ms | ✅ Success | 3 sources found        │      │
│ └───────────────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────────┘
```

### Understanding Sources

| Element | Meaning |
|---------|---------|
| Document name | Source document |
| Location | Page numbers or sections |
| Sources found | Number of relevant chunks |

### Tips for Better RAG Results

1. **Be specific**: "What are the system requirements for Project X?" vs "Tell me about Project X"
2. **Use keywords**: Include relevant technical terms
3. **Select the right category**: Ensure you're in the correct category
4. **Ask follow-up questions**: Refine your query based on initial results

### Example RAG Queries

| Query | Type |
|-------|------|
| "What is the deployment process?" | Process question |
| "How do I configure the database connection?" | How-to question |
| "What are the security requirements?" | Specification question |
| "Explain the architecture of system X" | Conceptual question |

---

## 7. Chat History

### Accessing History

1. Click **📜 History** at the bottom of the chat
2. Browse your past conversations

### History Features

| Feature | Description |
|---------|-------------|
| Search | Find conversations by keyword |
| Date grouping | Conversations grouped by day |
| Preview | See conversation summary |
| Load | Click to restore a conversation |

### History Organization

Conversations are organized by:

- **Today**: Current day's conversations
- **Yesterday**: Previous day's conversations
- **This Week**: Earlier this week
- **Older**: Older conversations

### Managing History

- Click on a conversation to view/continue it
- Use search to find specific conversations
- History is saved automatically

---

## 8. My Activity

### Accessing Activity

Click **📊 My Activity** at the bottom of the chat.

### Activity Overview

| Metric | Description |
|--------|-------------|
| Total Chats | Number of chat messages |
| Tool Calls | Number of tool executions |
| RAG Queries | Number of knowledge searches |
| Avg Response | Average response time |

### Activity History

View your recent interactions:

| Column | Description |
|--------|-------------|
| Time | When the action occurred |
| Type | CHAT, TOOL, or RAG |
| Question | What you asked |
| Status | Success or failure |

### Exporting Your Data

1. Click **Export CSV** or **Export JSON**
2. Download your activity data
3. Use for personal records or analysis

---

## 9. Settings

### Accessing Settings

Click the **⚙️** (gear icon) in the top-right corner.

### Appearance Settings

| Setting | Options |
|---------|---------|
| Theme | Light or Dark |
| Font Size | Small, Medium, Large |
| Show timestamps | On/Off |
| Show tool info | On/Off |

### Chat Behavior Settings

| Setting | Description |
|---------|-------------|
| Default category | Category selected on start |
| Show RAG sources | Display document sources |
| Auto-scroll | Scroll to new messages |
| Stream responses | Show responses as they generate |

### Data & Privacy

| Action | Description |
|--------|-------------|
| Clear Chat History | Delete all your conversations |
| Export My Data | Download all your data |

---

## 10. Tips and Best Practices

### Getting Better Responses

1. **Be clear and specific**
   - Good: "What is the deployment process for the production server?"
   - Less good: "How do I deploy?"

2. **Use the right category**
   - Select the category that matches your topic
   - Switch categories when changing topics

3. **Ask one question at a time**
   - Focused questions get focused answers
   - Break complex questions into parts

4. **Provide context when needed**
   - "Regarding Project X, what are the requirements?"
   - "Following up on our earlier discussion..."

### Using Tools Effectively

1. **Let the AI choose**
   - Usually, just describe what you want
   - The AI will select the appropriate tool

2. **Use explicit commands for precision**
   - `/add 5 10` guarantees the add tool
   - Useful when natural language is ambiguous

3. **Check the confidence score**
   - High confidence (>80%): AI is sure about the tool
   - Low confidence (<50%): Consider rephrasing

### Working with RAG

1. **Use relevant keywords**
   - Include technical terms from your domain
   - Mention specific document or project names

2. **Review the sources**
   - Check which documents were used
   - Sources help verify accuracy

3. **Ask follow-up questions**
   - "Can you tell me more about section 2?"
   - "What does the document say about X?"

### General Tips

| Tip | Benefit |
|-----|---------|
| Start simple | Build up complexity gradually |
| Review tool info | Understand what tools are available |
| Check history | Avoid repeating questions |
| Use categories | Get more relevant responses |

---

## 11. Troubleshooting

### Common Issues

#### No Response

**Symptoms**: Message sent but no response

**Solutions**:
1. Check your internet connection
2. Refresh the page
3. Try again in a few seconds
4. Contact your administrator

#### Slow Responses

**Symptoms**: Responses take a long time

**Possible Causes**:
- Large knowledge base
- Complex query
- Server load

**Solutions**:
1. Be more specific in your query
2. Try a different category
3. Wait and try again

#### Wrong Tool Selected

**Symptoms**: AI uses unexpected tool

**Solutions**:
1. Rephrase your question more clearly
2. Use explicit tool command: `/tool_name params`
3. Check if the right category is selected

#### RAG Returns No Results

**Symptoms**: "No relevant information found"

**Solutions**:
1. Try different keywords
2. Check your category selection
3. Ask a more general question first
4. Contact your administrator about available documents

#### Tool Execution Failed

**Symptoms**: Tool shows failed status

**Solutions**:
1. Check the error message
2. Verify your parameters are correct
3. Try again
4. Contact your administrator

### Error Messages

| Error | Meaning | Solution |
|-------|---------|----------|
| "Service unavailable" | Backend service is down | Wait and retry |
| "Tool not found" | Tool doesn't exist | Check available tools |
| "Invalid parameters" | Wrong input format | Review tool requirements |
| "Timeout" | Request took too long | Simplify your request |

### Getting Help

1. Click **🔧 Tools** to see available tools and examples
2. Try `/help` for command help
3. Ask: "What can you help me with?"
4. Contact your administrator for additional support

---

## Appendix: Quick Reference

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Enter | Send message |
| Shift+Enter | New line in message |
| Ctrl+K | Clear chat |
| Esc | Close panels |

### Common Commands

| Command | Purpose |
|---------|---------|
| `/help` | Show available commands |
| `/tools` | List available tools |
| `/add a b` | Add two numbers |
| `/echo message` | Echo a message |
| `/get_current_time` | Show current time |
| `/rag_query question` | Search knowledge base |

### Message Types

| Icon | Type |
|------|------|
| 👤 | Your message |
| 🤖 | NAAG response |
| 🔧 | Tool execution |
| 📖 | RAG query |
| ⚠️ | Warning/Error |

### Status Indicators

| Status | Meaning |
|--------|---------|
| ✅ Success | Operation completed successfully |
| ❌ Failed | Operation failed |
| ⏳ Processing | In progress |
| ⚠️ Warning | Completed with issues |
