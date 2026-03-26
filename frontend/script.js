const { createApp } = Vue;

createApp({
    data() {
        return {
            // ─── chat state ────────────────────────────────────────
            messages: [],
            userInput: '',
            isLoading: false,
            abortController: null,
            isComposing: false,

            // ─── user / session ────────────────────────────────────
            userId: '',
            sessionId: 'session_' + Date.now(),
            isFirstMessage: true,

            // ─── navigation ────────────────────────────────────────
            activeNav: 'newChat',

            // ─── history ───────────────────────────────────────────
            sessions: [],
            showHistorySidebar: false,

            // ─── chat image attachment ─────────────────────────────
            selectedChatImage: null,        // File object
            selectedChatImagePreview: null, // data URL
            selectedSample: null,           // {category, filename}
            showSamplePicker: false,
            sampleCategories: [],

            // ─── tools ─────────────────────────────────────────────
            availableTools: [],   // [{name, description}]
            enabledTools: [],     // names of enabled tools
            showToolPicker: false,

            // ─── documents ─────────────────────────────────────────
            documents: [],
            documentsLoading: false,
            selectedFile: null,
            isUploading: false,
            uploadProgress: ''
        };
    },

    computed: {
        displayedTools() {
            return this.availableTools.slice(0, 4);
        },
        extraTools() {
            return this.availableTools.slice(4);
        },
        chatImagePreviewUrl() {
            if (this.selectedSample) {
                return `/api/samples/${this.selectedSample.category}/${this.selectedSample.filename}`;
            }
            return this.selectedChatImagePreview;
        },
        hasAttachment() {
            return !!(this.selectedChatImage || this.selectedSample);
        }
    },

    async mounted() {
        this.configureMarked();
        this.userId = localStorage.getItem('userId') || ('user_' + Math.random().toString(36).slice(2, 11));
        localStorage.setItem('userId', this.userId);
        await this.loadTools();
    },

    methods: {
        // ─── Markdown ──────────────────────────────────────────────
        configureMarked() {
            marked.setOptions({
                highlight: (code, lang) => {
                    const language = hljs.getLanguage(lang) ? lang : 'plaintext';
                    return hljs.highlight(code, { language }).value;
                },
                langPrefix: 'hljs language-',
                breaks: true,
                gfm: true
            });
        },
        parseMarkdown(text) { return marked.parse(text || ''); },
        escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text || '';
            return div.innerHTML;
        },

        // ─── IME / keyboard ────────────────────────────────────────
        handleCompositionStart() { this.isComposing = true; },
        handleCompositionEnd()   { this.isComposing = false; },
        handleKeyDown(e) {
            if (e.key === 'Enter' && !e.shiftKey && !this.isComposing) {
                e.preventDefault();
                this.handleSend();
            }
        },
        autoResize(e) {
            const ta = e.target;
            ta.style.height = 'auto';
            ta.style.height = ta.scrollHeight + 'px';
            ta.style.overflowY = ta.scrollHeight > 100 ? 'auto' : 'hidden';
        },
        resetTextareaHeight() {
            if (this.$refs.textarea) this.$refs.textarea.style.height = 'auto';
        },
        scrollToBottom() {
            if (this.$refs.chatContainer)
                this.$refs.chatContainer.scrollTop = this.$refs.chatContainer.scrollHeight;
        },

        // ─── Send / Stop ────────────────────────────────────────────
        handleStop() {
            if (this.abortController) this.abortController.abort();
        },

        async handleSend() {
            const text = this.userInput.trim();
            if ((!text && !this.hasAttachment) || this.isLoading || this.isComposing) return;

            // Snapshot attachment before clearing
            const sentText   = text;
            const sentImage  = this.selectedChatImage;
            const sentSample = this.selectedSample;
            const previewUrl = this.chatImagePreviewUrl;

            // Add user message bubble
            this.messages.push({
                isUser: true,
                text: sentText,
                imageUrl: previewUrl || null,
                toolEvents: [],
                ragTrace: null
            });

            // Clear inputs
            this.userInput = '';
            this.clearChatImage();
            this.$nextTick(() => { this.resetTextareaHeight(); this.scrollToBottom(); });

            // Add thinking bubble
            const botMsg = { isUser: false, text: '', isThinking: true, toolEvents: [], ragTrace: null, ragSteps: [] };
            this.messages.push(botMsg);
            const botIdx = this.messages.length - 1;

            this.isLoading = true;
            this.abortController = new AbortController();

            try {
                let response;
                if (sentSample) {
                    // Sample image via query string
                    const params = new URLSearchParams({
                        sampleId: `${sentSample.category}/${sentSample.filename}`,
                        message:   sentText,
                        userId:    this.userId,
                        sessionId: this.sessionId
                    });
                    response = await fetch(`/chat/stream/sample?${params}`, {
                        method: 'POST',
                        signal: this.abortController.signal
                    });
                } else if (sentImage) {
                    // Uploaded image via multipart
                    const fd = new FormData();
                    fd.append('image',     sentImage);
                    fd.append('message',   sentText);
                    fd.append('userId',    this.userId);
                    fd.append('sessionId', this.sessionId);
                    response = await fetch('/chat/stream/image', {
                        method: 'POST',
                        body: fd,
                        signal: this.abortController.signal
                    });
                } else {
                    // Text-only via JSON body
                    response = await fetch('/chat/stream', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            message:   sentText,
                            userId:    this.userId,
                            sessionId: this.sessionId
                        }),
                        signal: this.abortController.signal
                    });
                }

                if (!response.ok) throw new Error(`HTTP ${response.status}`);
                await this.readSseStream(response, botIdx);

                // After first exchange, generate LLM title and persist it
                if (this.isFirstMessage) {
                    this.isFirstMessage = false;
                    const title = sentText
                        ? sentText.slice(0, 50)
                        : (sentSample ? `样本分析: ${sentSample.category}` : '图片分析');
                    this.saveTitle(title);
                }

            } catch (err) {
                if (err.name === 'AbortError') {
                    this.messages[botIdx].isThinking = false;
                    if (!this.messages[botIdx].text) this.messages[botIdx].text = '(已终止)';
                    else this.messages[botIdx].text += '\n\n_(已终止)_';
                } else {
                    console.error(err);
                    this.messages[botIdx].isThinking = false;
                    this.messages[botIdx].text = `出错了：${err.message}`;
                }
            } finally {
                this.isLoading = false;
                this.abortController = null;
                this.$nextTick(() => this.scrollToBottom());
            }
        },

        async readSseStream(response, botIdx) {
            const reader  = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                buffer += decoder.decode(value, { stream: true });

                let idx;
                while ((idx = buffer.indexOf('\n\n')) !== -1) {
                    const chunk = buffer.slice(0, idx);
                    buffer = buffer.slice(idx + 2);
                    if (!chunk.startsWith('data:')) continue;
                    const raw = chunk.slice(5).trim();
                    if (raw === '[DONE]') continue;
                    try {
                        const ev = JSON.parse(raw);
                        if (ev.type === 'token') {
                            this.messages[botIdx].isThinking = false;
                            this.messages[botIdx].text += ev.text;
                        } else if (ev.type === 'tool_done') {
                            this.messages[botIdx].toolEvents.push({ done: true });
                        } else if (ev.type === 'rag_step') {
                            this.messages[botIdx].ragSteps.push(ev.step);
                        } else if (ev.type === 'trace') {
                            this.messages[botIdx].ragTrace = ev.rag_trace;
                        } else if (ev.type === 'error') {
                            this.messages[botIdx].isThinking = false;
                            this.messages[botIdx].text += `\n[Error: ${ev.message || ev.content}]`;
                        }
                    } catch (e) {
                        console.warn('SSE parse error:', e, raw);
                    }
                }
                this.$nextTick(() => this.scrollToBottom());
            }
            this.messages[botIdx].isThinking = false;
        },

        // ─── Session title ──────────────────────────────────────────
        async saveTitle(title) {
            try {
                await fetch(`/sessions/${this.userId}/${this.sessionId}/title`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ title })
                });
            } catch (e) {
                console.warn('Title save failed:', e);
            }
        },

        // ─── Navigation ─────────────────────────────────────────────
        handleNewChat() {
            this.messages       = [];
            this.sessionId      = 'session_' + Date.now();
            this.isFirstMessage = true;
            this.activeNav      = 'newChat';
            this.showHistorySidebar = false;
        },
        handleClearChat() {
            if (confirm('确定清空当前对话吗？')) this.messages = [];
        },

        // ─── History ────────────────────────────────────────────────
        async handleHistory() {
            this.activeNav = 'history';
            this.showHistorySidebar = true;
            await this.loadSessions();
        },

        async loadSessions() {
            try {
                const res = await fetch(`/sessions/${this.userId}`);
                if (!res.ok) throw new Error('Failed to load sessions');
                const data = await res.json();
                this.sessions = data.sessions || [];
            } catch (e) {
                console.error(e);
                alert('加载历史记录失败：' + e.message);
            }
        },

        async loadSession(sid) {
            this.sessionId      = sid;
            this.isFirstMessage = false;
            this.showHistorySidebar = false;
            this.activeNav = 'newChat';

            try {
                const res = await fetch(`/sessions/${this.userId}/${sid}`);
                if (!res.ok) throw new Error('Failed to load session');
                const data = await res.json();
                this.messages = (data.messages || [])
                    .filter(m => m.messageType === 'user' || m.messageType === 'assistant')
                    .map(m => ({
                        isUser:     m.messageType === 'user',
                        text:       m.content || '',
                        imageUrl:   m.imageUrl || null,
                        toolEvents: [],
                        ragTrace:   null,
                        ragSteps:   []
                    }));
            } catch (e) {
                console.error(e);
                alert('加载会话失败：' + e.message);
                this.messages = [];
            }
            this.$nextTick(() => this.scrollToBottom());
        },

        async deleteSession(sid) {
            if (!confirm('确定删除这个会话吗？')) return;
            try {
                await fetch(`/sessions/${this.userId}/${sid}`, { method: 'DELETE' });
                this.sessions = this.sessions.filter(s => s.sessionId !== sid);
                if (this.sessionId === sid) {
                    this.messages       = [];
                    this.sessionId      = 'session_' + Date.now();
                    this.isFirstMessage = true;
                    this.activeNav      = 'newChat';
                }
            } catch (e) {
                console.error(e);
                alert('删除失败：' + e.message);
            }
        },

        // ─── Settings ────────────────────────────────────────────────
        handleSettings() {
            this.activeNav = 'settings';
            this.showHistorySidebar = false;
            this.loadDocuments();
        },

        // ─── Tools ──────────────────────────────────────────────────
        async loadTools() {
            try {
                const res = await fetch('/api/tools');
                if (!res.ok) return;
                this.availableTools = await res.json();
                this.enabledTools   = [];
            } catch (e) {
                console.warn('Tool list unavailable:', e);
            }
        },

        toggleTool(name) {
            if (this.enabledTools.includes(name)) {
                this.enabledTools = this.enabledTools.filter(n => n !== name);
            } else {
                this.enabledTools.push(name);
            }
        },

        toolDisplayName(name) {
            const map = {
                ragTool:               '知识库',
                web_search:            '联网搜索',
                feiyanAgentMedicalTool:'肺炎分析',
                medical_diagnosis:     '医疗问诊'
            };
            return map[name] || name;
        },

        toolIcon(name) {
            const icons = {
                ragTool:               'fa-book',
                web_search:            'fa-globe',
                feiyanAgentMedicalTool:'fa-lungs',
                medical_diagnosis:     'fa-stethoscope'
            };
            return 'fas ' + (icons[name] || 'fa-cog');
        },

        // ─── Chat image attachment ────────────────────────────────────
        openChatImagePicker() {
            this.$refs.chatImageInput.click();
        },

        handleChatImageSelect(e) {
            const file = e.target.files && e.target.files[0];
            if (!file) return;
            this.selectedChatImage = file;
            this.selectedSample    = null;
            const reader = new FileReader();
            reader.onload = ev => { this.selectedChatImagePreview = ev.target.result; };
            reader.readAsDataURL(file);
            // Reset so the same file can be picked again
            e.target.value = '';
        },

        clearChatImage() {
            this.selectedChatImage        = null;
            this.selectedChatImagePreview = null;
            this.selectedSample           = null;
        },

        // ─── Sample picker ────────────────────────────────────────────
        async openSamplePicker() {
            this.showSamplePicker = true;
            if (this.sampleCategories.length === 0) await this.loadSamples();
        },

        async loadSamples() {
            try {
                const res = await fetch('/api/samples');
                if (!res.ok) return;
                this.sampleCategories = await res.json();
            } catch (e) {
                console.warn('Failed to load samples:', e);
            }
        },

        selectSample(category, filename) {
            this.selectedSample           = { category, filename };
            this.selectedChatImage        = null;
            this.selectedChatImagePreview = null;
            this.showSamplePicker         = false;
        },

        // ─── Documents ────────────────────────────────────────────────
        async loadDocuments() {
            this.documentsLoading = true;
            try {
                const res = await fetch('/documents');
                if (!res.ok) throw new Error('Failed to load documents');
                const data = await res.json();
                this.documents = data.documents || [];
            } catch (e) {
                console.error(e);
                alert('加载文档失败：' + e.message);
            } finally {
                this.documentsLoading = false;
            }
        },

        handleFileSelect(e) {
            const file = e.target.files && e.target.files[0];
            this.selectedFile = file || null;
            this.uploadProgress = '';
        },

        isDuplicate(filename) {
            return this.documents.some(d => d.originalFilename === filename);
        },

        async uploadDocument() {
            if (!this.selectedFile) return;
            if (this.isDuplicate(this.selectedFile.name)) {
                alert(`文档 "${this.selectedFile.name}" 已上传，请勿重复添加`);
                return;
            }
            this.isUploading   = true;
            this.uploadProgress = '正在上传并索引，请稍候...';
            try {
                const fd = new FormData();
                fd.append('file', this.selectedFile);
                const res = await fetch('/documents/upload', { method: 'POST', body: fd });
                if (!res.ok) {
                    const err = await res.json().catch(() => ({}));
                    throw new Error(err.error || 'Upload failed');
                }
                const data = await res.json();
                this.uploadProgress = `上传成功 — ${data.chunks} 个文本片段已索引`;
                this.selectedFile = null;
                if (this.$refs.docFileInput) this.$refs.docFileInput.value = '';
                await this.loadDocuments();
                setTimeout(() => { this.uploadProgress = ''; }, 5000);
            } catch (e) {
                console.error(e);
                this.uploadProgress = `上传失败：${e.message}`;
            } finally {
                this.isUploading = false;
            }
        },

        async deleteDocument(filename) {
            if (!confirm('确定删除该文档及其所有向量数据吗？')) return;
            try {
                const res = await fetch(`/documents/${encodeURIComponent(filename)}`, { method: 'DELETE' });
                if (!res.ok && res.status !== 404) throw new Error('Delete failed');
                await this.loadDocuments();
            } catch (e) {
                console.error(e);
                alert('删除失败：' + e.message);
            }
        },

        formatSize(bytes) {
            if (!bytes) return '';
            if (bytes < 1024) return bytes + ' B';
            if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
            return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
        },

        getFileIcon(type) {
            const icons = { PDF: 'fas fa-file-pdf', DOC: 'fas fa-file-word', DOCX: 'fas fa-file-word' };
            return icons[(type || '').toUpperCase()] || 'fas fa-file';
        },

        formatSessionTime(ts) {
            if (!ts) return '';
            try {
                const d    = new Date(ts);
                const now  = new Date();
                const diff = Math.floor((now - d) / 86400000);
                if (diff === 0) return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
                if (diff === 1) return '昨天';
                if (diff < 7)  return diff + '天前';
                return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' });
            } catch { return ''; }
        }
    },

    watch: {
        messages: {
            handler() { this.$nextTick(() => this.scrollToBottom()); },
            deep: true
        }
    }
}).mount('#app');
