<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
  <title>Full RAG Sync (Non-stream)</title>
  <style>
    body { font-family: Arial, sans-serif; margin: 24px; }
    input[type=text] { width: 720px; }
    pre { white-space: pre-wrap; border: 1px solid #ddd; padding: 12px; }
    .ok { color: #0a7; }
  </style>
</head>
<body>
<h2>Full RAG Sync (PDF/DOCX → Qdrant → llama.cpp)</h2>

<h3>1) Upload PDF / DOCX</h3>
<form method="post" action="/upload" enctype="multipart/form-data">
  Doc ID:
  <input type="text" name="docId" value="doc1"/>
  <br/><br/>
  File:
  <input type="file" name="file"/>
  <br/><br/>
  <button type="submit">Ingest</button>
</form>

<p class="ok">${msg}</p>

<hr/>

<h3>2) Ask</h3>
<form method="post" action="/ask">
  Question:
  <br/><br/>
  <input type="text" name="question" value="${question}"/>
  <button type="submit">Ask</button>
</form>

<h3>Answer</h3>
<pre>${answer}</pre>

<hr/>
<p>
  <b>Note:</b> Ensure Qdrant collection exists with the correct vector size (embedding dimension).
  See README.md for steps.
</p>
</body>
</html>
