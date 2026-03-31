const { createApp } = Vue;

const SPAN_NAMES = {
    ragAgentTool:         { label: '知识库检索', icon: 'fa-robot' },
    ragSearch:            { label: '文档检索', icon: 'fa-search' },
    webSearch:            { label: '联网搜索', icon: 'fa-globe' },
    webSearchTool:        { label: '联网搜索', icon: 'fa-globe' },
    ragTool:              { label: '文档检索', icon: 'fa-book' },
    pneumoniaCnnTool:     { label: 'X光分析', icon: 'fa-lungs' },
    medicalDiagnosisTool: { label: '医疗诊断', icon: 'fa-stethoscope' },
};

createApp({
    data() {
        return {
            // ─── chat state ────────────────────────────────────────
            messages: [],
            userInput: '',
            isLoading: false,
            abortController: null,
            isComposing: false,

            // ─── auth ──────────────────────────────────────────────
            token: null,
            loggedIn: false,
            username: '',
            loginForm: { username: '', password: '' },
            registerMode: false,

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

            // ─── observability ────────────────────────────────────
            pendingBotIdx: -1,
            spanMap: {},          // spanId → step object (for tree building)
            rootSpanId: null,     // supervisor span id (for detecting final span_end)
            ignoredSpanIds: {},   // LLM span ids (skipped in UI but must not trigger collapse)
            userScrolledUp: false,

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
            return this.availableTools.length > 5 ? this.availableTools.slice(0, 5) : this.availableTools;
        },
        extraTools() {
            return this.availableTools.length > 5 ? this.availableTools.slice(5) : [];
        },
        chatImagePreviewUrl() {
            if (this.selectedSample) {
                return `/api/samples/${this.selectedSample.category}/${this.selectedSample.filename}`;
            }
            return this.selectedChatImagePreview;
        },
        hasAttachment() {
            return !!(this.selectedChatImage || this.selectedSample);
        },
        toolFlagValue() {
            return this.enabledTools.reduce((acc, name) => {
                const t = this.availableTools.find(t => t.name === name);
                return t ? acc | t.toolFlag : acc;
            }, 0);
        }
    },

    async mounted() {
        this.configureMarked();
        const storedToken    = localStorage.getItem('token');
        const storedUserId   = localStorage.getItem('userId');
        const storedUsername = localStorage.getItem('username');
        if (storedToken && storedUserId) {
            this.token    = storedToken;
            this.userId   = storedUserId;
            this.username = storedUsername || '';
            this.loggedIn = true;
            await this.loadTools();
        }
    },

    methods: {
        // ─── Auth ──────────────────────────────────────────────────
        async login() {
            if (!this.loginForm.username || !this.loginForm.password) return;
            try {
                const res = await fetch('/api/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username: this.loginForm.username, password: this.loginForm.password })
                });
                const data = await res.json();
                if (data.success) {
                    this.token    = data.token;
                    this.userId   = String(data.userId);
                    this.username = data.username;
                    localStorage.setItem('token',    data.token);
                    localStorage.setItem('userId',   String(data.userId));
                    localStorage.setItem('username', data.username);
                    this.loggedIn = true;
                    this.loginForm = { username: '', password: '' };
                    await this.loadTools();
                } else {
                    alert('登录失败: ' + data.message);
                }
            } catch (e) {
                console.error(e);
                alert('登录请求失败: ' + e.message);
            }
        },

        async register() {
            if (!this.loginForm.username || !this.loginForm.password) return;
            try {
                const res = await fetch('/api/auth/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ username: this.loginForm.username, password: this.loginForm.password })
                });
                const data = await res.json();
                if (data.success) {
                    this.token    = data.token;
                    this.userId   = String(data.userId);
                    this.username = data.username;
                    localStorage.setItem('token',    data.token);
                    localStorage.setItem('userId',   String(data.userId));
                    localStorage.setItem('username', data.username);
                    this.loggedIn    = true;
                    this.registerMode = false;
                    this.loginForm   = { username: '', password: '' };
                    await this.loadTools();
                } else {
                    alert('注册失败: ' + data.message);
                }
            } catch (e) {
                console.error(e);
                alert('注册请求失败: ' + e.message);
            }
        },

        logout() {
            this.messages.forEach(m => { this.stopAllTimers(m.progressSteps); if (m._phaseTimerId) clearInterval(m._phaseTimerId); });
            this.pendingBotIdx = -1;
            this.token     = null;
            this.loggedIn  = false;
            this.userId    = '';
            this.username  = '';
            this.messages  = [];
            this.sessions  = [];
            this.availableTools = [];
            this.enabledTools   = [];
            localStorage.removeItem('token');
            localStorage.removeItem('userId');
            localStorage.removeItem('username');
        },

        handleUnauthorized() {
            this.logout();
        },

        async authFetch(url, options = {}) {
            const { headers: extraHeaders, ...rest } = options;
            const res = await fetch(url, { headers: { 'X-Token': this.token, ...extraHeaders }, ...rest });
            if (res.status === 401) {
                this.handleUnauthorized();
                const err = new Error('Unauthorized');
                err.isUnauthorized = true;
                throw err;
            }
            return res;
        },

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
        scrollToBottom(force) {
            if (!this.$refs.chatContainer) return;
            if (!force && this.userScrolledUp) return;
            const el = this.$refs.chatContainer;
            if (this.isLoading) {
                el.scrollTop = el.scrollHeight;
            } else {
                el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
            }
        },
        handleChatScroll() {
            const el = this.$refs.chatContainer;
            if (!el) return;
            // User is "at bottom" if within 80px of the bottom
            const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
            this.userScrolledUp = !atBottom;
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
            this.userScrolledUp = false;
            this.$nextTick(() => { this.resetTextareaHeight(); this.scrollToBottom(true); });

            // Add thinking bubble
            const botMsg = { isUser: false, text: '', isThinking: true, toolEvents: [], ragTrace: null, ragSteps: [], progressSteps: [], progressCollapsed: true, progressDone: false, progressSummary: '', progressExtra: '', phase: 'thinking', phaseStartTime: Date.now(), phaseElapsed: '0.0', _phaseTimerId: null };
            this.messages.push(botMsg);
            const botIdx = this.messages.length - 1;
            this.pendingBotIdx = botIdx;
            this.spanMap = {};
            this.rootSpanId = null;
            this.ignoredSpanIds = {};

            // Phase elapsed timer
            botMsg._phaseTimerId = setInterval(() => {
                if (this.messages[botIdx] && this.messages[botIdx].phase !== 'done') {
                    this.messages[botIdx].phaseElapsed = ((Date.now() - this.messages[botIdx].phaseStartTime) / 1000).toFixed(1);
                }
            }, 100);

            this.isLoading = true;
            this.abortController = new AbortController();

            try {
                let response;
                const toolHeaders = { 'X-Tool-Flag': String(this.toolFlagValue) };
                if (sentSample) {
                    // Sample image via query string
                    const params = new URLSearchParams({
                        sampleId: `${sentSample.category}/${sentSample.filename}`,
                        message:   sentText,
                        userId:    this.userId,
                        sessionId: this.sessionId
                    });
                    response = await this.authFetch(`/chat/stream/sample?${params}`, {
                        method: 'POST',
                        headers: toolHeaders,
                        signal: this.abortController.signal
                    });
                } else if (sentImage) {
                    // Uploaded image via multipart
                    const fd = new FormData();
                    fd.append('image',     sentImage);
                    fd.append('message',   sentText);
                    fd.append('userId',    this.userId);
                    fd.append('sessionId', this.sessionId);
                    response = await this.authFetch('/chat/stream/image', {
                        method: 'POST',
                        headers: toolHeaders,
                        body: fd,
                        signal: this.abortController.signal
                    });
                } else {
                    // Text-only via JSON body
                    response = await this.authFetch('/chat/stream', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json', ...toolHeaders },
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
                } else if (err.isUnauthorized) {
                    this.messages.splice(botIdx, 1);
                } else {
                    console.error(err);
                    this.messages[botIdx].isThinking = false;
                    this.messages[botIdx].text = `出错了：${err.message}`;
                }
            } finally {
                this.isLoading = false;
                this.abortController = null;
                if (this.pendingBotIdx >= 0 && this.messages[this.pendingBotIdx]) {
                    const m = this.messages[this.pendingBotIdx];
                    this.stopAllTimers(m.progressSteps);
                    if (m._phaseTimerId) { clearInterval(m._phaseTimerId); m._phaseTimerId = null; }
                    if (m.phase !== 'done') m.phase = 'done';
                }
                this.pendingBotIdx = -1;
                this.userScrolledUp = false;
                this.$nextTick(() => this.scrollToBottom(true));
            }
        },

        async readSseStream(response, botIdx) {
            const reader  = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';

            // Token batching: accumulate tokens and flush every 50ms
            let tokenBuffer = '';
            let flushTimer = null;
            const flushTokens = () => {
                if (tokenBuffer && this.messages[botIdx]) {
                    this.messages[botIdx].text += tokenBuffer;
                    tokenBuffer = '';
                }
                flushTimer = null;
            };
            const scheduleFlush = () => {
                if (!flushTimer) {
                    flushTimer = setTimeout(flushTokens, 50);
                }
            };

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
                            if (ev.parentSpanId && this.spanMap[ev.parentSpanId]) {
                                this.spanMap[ev.parentSpanId].thinkingText += ev.text;
                            } else {
                                // First final-reply token → delayed collapse + phase transition
                                if (!this.messages[botIdx].text && !tokenBuffer) {
                                    this.messages[botIdx].phase = 'generating';
                                    // Delayed collapse: wait 1.5s before collapsing exec-card
                                    setTimeout(() => {
                                        if (this.messages[botIdx]) {
                                            this.messages[botIdx].progressCollapsed = true;
                                        }
                                    }, 1500);
                                }
                                this.messages[botIdx].isThinking = false;
                                tokenBuffer += ev.text;
                                scheduleFlush();
                            }
                        } else if (ev.type === 'span_start' || ev.type === 'span_end') {
                            this.handleObsEvent(ev, botIdx);
                        } else if (ev.type === 'error') {
                            this.messages[botIdx].isThinking = false;
                            this.messages[botIdx].text += `\n[Error: ${ev.message || ev.error}]`;
                        }
                    } catch (e) {
                        console.warn('SSE parse error:', e, raw);
                    }
                }
                // Scrolling handled by watcher with rAF debounce
            }
            // Flush remaining tokens
            if (flushTimer) clearTimeout(flushTimer);
            flushTokens();
            this.messages[botIdx].isThinking = false;
            this.messages[botIdx].phase = 'done';
            // Stop phase timer
            if (this.messages[botIdx]._phaseTimerId) {
                clearInterval(this.messages[botIdx]._phaseTimerId);
                this.messages[botIdx]._phaseTimerId = null;
            }
        },

        // ─── Session title ──────────────────────────────────────────
        async saveTitle(title) {
            try {
                await this.authFetch(`/sessions/${this.userId}/${this.sessionId}/title`, {
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
            this.messages.forEach(m => { this.stopAllTimers(m.progressSteps); if (m._phaseTimerId) clearInterval(m._phaseTimerId); });
            this.pendingBotIdx  = -1;
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
                const res = await this.authFetch(`/sessions/${this.userId}`);
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
                const res = await this.authFetch(`/sessions/${this.userId}/${sid}`);
                if (!res.ok) throw new Error('Failed to load session');
                const data = await res.json();
                this.messages = (data.messages || [])
                    .filter(m => m.messageType === 'user' || m.messageType === 'assistant')
                    .map(m => ({
                        isUser:        m.messageType === 'user',
                        text:          m.content || '',
                        imageUrl:      m.imageUrl || null,
                        toolEvents:    [],
                        ragTrace:      null,
                        ragSteps:      [],
                        progressSteps: []
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
                await this.authFetch(`/sessions/${this.userId}/${sid}`, { method: 'DELETE' });
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
                const res = await this.authFetch('/api/tools');
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
                // Find mutual exclusions declared by this tool
                const tool = this.availableTools.find(t => t.name === name);
                const excludes = tool?.mutuallyExclusiveWith ?? [];
                // Remove any currently-enabled tools that are mutually exclusive with the new one
                this.enabledTools = this.enabledTools.filter(n => !excludes.includes(n));
                this.enabledTools.push(name);
            }
        },

        toolDisplayName(name) {
            const t = this.availableTools.find(t => t.name === name);
            return t ? t.title : name;
        },

        toolIcon(name) {
            const t = this.availableTools.find(t => t.name === name);
            return 'fas ' + (t ? t.toolIcon : 'fa-cog');
        },

        // ─── Timer management for progress steps ─────────────────────
        startStepTimer(step) {
            step.startTime = Date.now();
            step.timerId = setInterval(() => {
                step.elapsed = ((Date.now() - step.startTime) / 1000).toFixed(1);
            }, 100);
        },
        stopStepTimer(step) {
            if (step.timerId) {
                clearInterval(step.timerId);
                step.timerId = null;
            }
            if (step.startTime) {
                step.elapsed = ((Date.now() - step.startTime) / 1000).toFixed(1);
            }
        },
        stopAllTimers(steps) {
            if (!steps) return;
            for (const step of steps) {
                this.stopStepTimer(step);
                if (step.subSteps) this.stopAllTimers(step.subSteps);
            }
        },

        handleObsEvent(ev, botIdx) {
            const msg = this.messages[botIdx];
            if (!msg) return;

            if (ev.type === 'span_start') {
                // Track supervisor span id — don't render in tree
                if (ev.spanType === 'supervisor') {
                    this.rootSpanId = ev.spanId;
                    return;
                }
                // Track LLM span ids — don't render in tree but must not trigger collapse
                if (ev.spanType === 'llm') {
                    this.ignoredSpanIds[ev.spanId] = true;
                    return;
                }

                const toolInfo = this.availableTools.find(t => t.name === ev.name);
                const fallback = SPAN_NAMES[ev.name];
                const step = {
                    spanId: ev.spanId,
                    type: ev.spanType,   // 'agent' or 'tool'
                    name: ev.name,
                    displayName: toolInfo ? toolInfo.title : (fallback ? fallback.label : (ev.name || '工具')),
                    icon: toolInfo ? toolInfo.toolIcon : (fallback ? fallback.icon : (ev.spanType === 'agent' ? 'fa-robot' : 'fa-wrench')),
                    outputPreview: '',
                    done: false, error: false, expanded: false,
                    thinkingText: '', thinkingExpanded: false,
                    subSteps: [], subStepsCollapsed: false,
                    startTime: null, elapsed: '0.0', timerId: null
                };
                this.spanMap[ev.spanId] = step;

                // Find parent: if parentSpanId points to an agent/tool step, nest under it
                const parentStep = ev.parentSpanId ? this.spanMap[ev.parentSpanId] : null;
                if (parentStep) {
                    parentStep.subSteps.push(step);
                } else {
                    // Top-level (parent is supervisor — not in spanMap)
                    msg.progressSteps.push(step);
                }
                // Auto-expand when first step arrives so user sees the trace live
                if (!msg.progressDone) {
                    msg.progressCollapsed = false;
                }
                // Phase: thinking → tools on first non-supervisor/llm span
                if (msg.phase === 'thinking') {
                    msg.phase = 'tools';
                }
                this.startStepTimer(step);

            } else if (ev.type === 'span_end') {
                // Supervisor span_end — final completion, collapse entire card
                if (ev.spanId === this.rootSpanId) {
                    this.stopAllTimers(msg.progressSteps);
                    msg.progressDone = true;
                    const totalSteps = this.countAllSteps(msg.progressSteps);
                    const totalTime = msg.progressSteps.reduce((sum, s) => sum + parseFloat(s.elapsed || 0), 0).toFixed(1);
                    msg.progressSummary = `${msg.progressSteps.length} 个步骤 · ${totalTime}s`;
                    msg.progressExtra = `used ${totalSteps} tools`;
                    msg.progressCollapsed = true;
                    return;
                }
                // LLM span_end — silently ignore, do NOT treat as supervisor end
                if (this.ignoredSpanIds[ev.spanId]) {
                    return;
                }
                // Agent/tool span_end — mark step done, auto-collapse its subtree
                const step = this.spanMap[ev.spanId];
                if (step) {
                    step.done = true;
                    step.outputPreview = ev.output || '';
                    if (ev.status === 'error') step.error = true;
                    this.stopStepTimer(step);
                    // Auto-collapse subtree of completed step
                    if (step.subSteps && step.subSteps.length) {
                        step.subStepsCollapsed = true;
                    }
                }
            }
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
                const res = await this.authFetch('/api/samples');
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
                const res = await this.authFetch('/documents');
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
                const res = await this.authFetch('/documents/upload', { method: 'POST', body: fd });
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
                const res = await this.authFetch(`/documents/${encodeURIComponent(filename)}`, { method: 'DELETE' });
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

        formatToolInput(input) {
            if (!input) return '';
            try { return JSON.stringify(JSON.parse(input), null, 2); }
            catch { return input; }
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
        },

        timerClass(step) {
            if (!step.done) return 'active';
            const t = parseFloat(step.elapsed);
            if (t > 30) return 'tl-timer-slow';
            if (t > 10) return 'tl-timer-warn';
            return '';
        },

        currentStepName(msg) {
            const running = this.findRunningStep(msg.progressSteps);
            return running ? (running.displayName || running.name) : null;
        },

        findRunningStep(steps) {
            if (!steps) return null;
            for (const s of steps) {
                if (!s.done) {
                    const sub = this.findRunningStep(s.subSteps);
                    return sub || s;
                }
            }
            return null;
        },

        countAllSteps(steps) {
            if (!steps) return 0;
            let count = 0;
            for (const s of steps) {
                count++;
                count += this.countAllSteps(s.subSteps);
            }
            return count;
        },

        execSummaryText(msg) {
            if (msg.progressDone && msg.progressSummary) {
                return msg.progressSummary;
            }
            // Dynamic summary for in-progress state
            const stepCount = msg.progressSteps.length;
            if (stepCount === 0) return '执行中...';
            const totalTime = msg.progressSteps.reduce((sum, s) => sum + parseFloat(s.elapsed || 0), 0).toFixed(1);
            return `${stepCount} 个步骤 · ${totalTime}s`;
        },

        execExtraText(msg) {
            if (msg.progressDone) {
                return msg.progressExtra || '';
            }
            // In-progress: show deepest running step name + elapsed
            const running = this.findRunningStep(msg.progressSteps);
            if (running) {
                return `${running.displayName || running.name}  ${running.elapsed}s`;
            }
            return '';
        },

        phaseIndex(phase) {
            const map = { thinking: 0, tools: 1, generating: 2, done: 3 };
            return map[phase] ?? 0;
        }
    },

    watch: {
        messages: {
            handler() {
                if (this._scrollRafId) return;
                this._scrollRafId = requestAnimationFrame(() => {
                    this._scrollRafId = null;
                    this.scrollToBottom();
                });
            },
            deep: true
        }
    }
}).mount('#app');
