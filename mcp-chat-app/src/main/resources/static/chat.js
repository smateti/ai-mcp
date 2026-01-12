// Chat Application Logic
class ChatApp {
    constructor() {
        this.sessionId = null;
        this.baseUrl = '/api/chat';
        this.isLoading = false;

        this.chatContainer = document.getElementById('chatContainer');
        this.messageInput = document.getElementById('messageInput');
        this.sendButton = document.getElementById('sendButton');
        this.statusDot = document.getElementById('statusDot');
        this.statusText = document.getElementById('statusText');

        this.init();
    }

    async init() {
        // Initialize session
        await this.createSession();

        // Check health
        await this.checkHealth();

        // Setup event listeners
        this.sendButton.addEventListener('click', () => this.sendMessage());
        this.messageInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.sendMessage();
            }
        });

        // Auto-resize textarea
        this.messageInput.addEventListener('input', () => {
            this.messageInput.style.height = 'auto';
            this.messageInput.style.height = this.messageInput.scrollHeight + 'px';
        });

        // Focus input
        this.messageInput.focus();
    }

    async createSession() {
        try {
            const response = await fetch(`${this.baseUrl}/session`, {
                method: 'POST'
            });
            const data = await response.json();
            this.sessionId = data.sessionId;
            console.log('Session created:', this.sessionId);
        } catch (error) {
            console.error('Failed to create session:', error);
            this.updateStatus(false, 'Failed to create session');
        }
    }

    async checkHealth() {
        try {
            const response = await fetch(`${this.baseUrl}/health`);
            const data = await response.json();

            const isHealthy = data.status === 'healthy' && data.mcpConnected;
            this.updateStatus(
                isHealthy,
                isHealthy
                    ? `Connected (${data.toolsAvailable} tools available)`
                    : 'MCP Server disconnected'
            );
        } catch (error) {
            this.updateStatus(false, 'Connection failed');
        }
    }

    updateStatus(connected, text) {
        this.statusDot.className = 'status-dot' + (connected ? '' : ' disconnected');
        this.statusText.textContent = text;
    }

    async sendMessage() {
        const message = this.messageInput.value.trim();
        if (!message || this.isLoading) return;

        // Add user message to UI
        this.addMessage('user', message);

        // Clear input
        this.messageInput.value = '';
        this.messageInput.style.height = 'auto';

        // Show loading
        this.setLoading(true);
        const loadingElement = this.showLoading();

        try {
            const response = await fetch(`${this.baseUrl}/message`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    sessionId: this.sessionId,
                    message: message
                })
            });

            const data = await response.json();

            // Remove loading
            loadingElement.remove();

            // Add assistant response
            this.addMessage('assistant', data.content, data.metadata);

        } catch (error) {
            console.error('Error sending message:', error);
            loadingElement.remove();
            this.addMessage('assistant', '❌ Failed to send message. Please try again.');
        } finally {
            this.setLoading(false);
        }
    }

    addMessage(role, content, metadata = null) {
        // Remove welcome message if present
        const welcome = this.chatContainer.querySelector('.welcome');
        if (welcome) {
            welcome.remove();
        }

        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${role}`;

        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = role === 'user' ? 'U' : '🤖';

        const contentDiv = document.createElement('div');
        contentDiv.className = 'message-content';

        // Parse markdown-style formatting
        const formattedContent = this.formatContent(content);
        contentDiv.innerHTML = formattedContent;

        // Add metadata if present
        if (metadata) {
            this.addMetadata(contentDiv, metadata);
        }

        messageDiv.appendChild(avatar);
        messageDiv.appendChild(contentDiv);

        this.chatContainer.appendChild(messageDiv);
        this.scrollToBottom();
    }

    formatContent(content) {
        // Convert basic markdown to HTML
        let formatted = content
            // Bold
            .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
            // Italic
            .replace(/\*(.+?)\*/g, '<em>$1</em>')
            // Code blocks
            .replace(/```(.+?)```/gs, '<pre><code>$1</code></pre>')
            // Inline code
            .replace(/`(.+?)`/g, '<code>$1</code>')
            // Line breaks
            .replace(/\n/g, '<br>');

        return formatted;
    }

    addMetadata(contentDiv, metadata) {
        if (metadata.tool) {
            const metaDiv = document.createElement('div');
            metaDiv.className = 'message-meta';

            // Map tool names to display names
            const toolDisplayNames = {
                'rag_ingest': '📄 Document Ingestion',
                'rag_query': '🔍 RAG Query',
                'echo': '📢 Echo',
                'add': '➕ Add',
                'get_current_time': '🕒 Current Time',
                'jsonplaceholder-user': '👤 User Info API'
            };

            const displayName = toolDisplayNames[metadata.tool] || `🔧 ${metadata.tool}`;

            let metaHTML = `<span>Tool: <strong>${displayName}</strong></span>`;

            // Add tool-specific metadata
            if (metadata.tool === 'rag_ingest' && metadata.chunks !== undefined) {
                metaHTML += `<span>📊 Chunks: ${metadata.chunks}</span>`;
            } else if (metadata.tool === 'rag_query' && metadata.sources !== undefined) {
                metaHTML += `<span>📚 Sources: ${metadata.sources}</span>`;
            }

            metaDiv.innerHTML = metaHTML;
            contentDiv.appendChild(metaDiv);
        } else if (metadata.command === 'help') {
            const metaDiv = document.createElement('div');
            metaDiv.className = 'message-meta';
            metaDiv.innerHTML = '<span>📖 Help Documentation</span>';
            contentDiv.appendChild(metaDiv);
        }
    }

    showLoading() {
        const loadingDiv = document.createElement('div');
        loadingDiv.className = 'message assistant';
        loadingDiv.innerHTML = `
            <div class="message-avatar">🤖</div>
            <div class="message-content">
                <div class="loading">
                    <div class="loading-dot"></div>
                    <div class="loading-dot"></div>
                    <div class="loading-dot"></div>
                </div>
            </div>
        `;
        this.chatContainer.appendChild(loadingDiv);
        this.scrollToBottom();
        return loadingDiv;
    }

    setLoading(loading) {
        this.isLoading = loading;
        this.sendButton.disabled = loading;
        this.messageInput.disabled = loading;
    }

    scrollToBottom() {
        this.chatContainer.scrollTop = this.chatContainer.scrollHeight;
    }
}

// Initialize app when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    new ChatApp();
});
