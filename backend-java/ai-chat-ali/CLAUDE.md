# CLAUDE.md — ai-chat-ali

Spring Boot 3.5.11 + Spring AI Alibaba. Multi-agent medical diagnosis demo.

## Build & Run

```bash
cd backend-java/ai-chat-ali

# Run (serves on port 8080, includes frontend)
./gradlew bootRun

# Build JAR
./gradlew build

# Run tests
./gradlew test
```

## Environment

| Variable | Required | Notes |
|---|---|---|
| `AI_DASHSCOPE_API_KEY` | Yes | Dashscope API key (qwen-turbo default) |
| `TAVILY_API_KEY` | No | Web search tool |
| `JINA_API_KEY` | No | Jina rerank service |

Java 21, Gradle 9.3.1 (wrapper included).

## Package Structure

```
ff.pro.aichatali/
├── controller/
│   ├── ChatController          POST /chat/stream[/image|/sample] — SSE streaming
│   ├── SessionController       GET/DELETE/PUT /sessions/{userId}[/{sessionId}[/title]]
│   ├── DocumentController      POST/GET/DELETE /documents/*
│   ├── ToolController          GET /api/tools
│   ├── SampleImageController   GET /api/samples[/{category}/{filename}]
│   ├── ChatStreamController    (legacy) /api/chat/multimodal* — preserved, not removed
│   └── UserAuthController      /api/auth/*
├── service/
│   ├── ChatHistoryService      In-memory message + title store (ConcurrentHashMap); deleteOlderThan(Duration)
│   ├── DocumentService         Upload → UUID rename → Milvus index
│   ├── ToolRegistryService     Collects List<PluggableTool> beans, exposes getToolCallbacks()
│   ├── MemoryHybridRetrieverService  BM25 + vector hybrid retrieval + rerank + grading
│   ├── EmbeddingService        Dashscope embedding calls
│   ├── RerankService           HTTP rerank (Jina or custom)
│   ├── GradingService          LLM relevance grading
│   ├── QueryRewriter           LLM query rewriting
│   ├── UserService             In-memory users: admin(id=0) preset; register/login return UserSimpleDto
│   ├── AuthService             Token store: issueToken(userId)/validateAndGetUserId(token)/expireAll()
│   └── CleanupService          @Scheduled: expire tokens hourly, delete history >7d
├── service/rag/
│   ├── BM25Service
│   └── RRFMerger
├── service/websearch/
│   ├── WebSearchPort           Interface
│   └── TavilyWebSearchService
├── tool/                       Each tool = Spring @Component implementing PluggableTool
│   ├── PluggableTool.java      Interface: name() / description() / toolCallback() / mutuallyExclusiveWith()
│   ├── rag_tool/
│   │   ├── RagPluggableTool      Hybrid retrieval (Milvus BM25 + vector)
│   │   ├── RagTool               BiFunction delegate
│   │   ├── RagAgentPluggableTool Sub-agent: RAG + web-search fallback
│   │   └── RagAgentTool          ReactAgent wrapping ragSearch + webSearch
│   ├── fetch_tool/
│   │   ├── WebSearchPluggableTool
│   │   └── WebSearchTool         BiFunction delegate
│   ├── feiyan_tool/
│   │   ├── FeiyanPluggableTool   Chest X-ray CNN classification
│   │   └── FeiyanCnnTool         HTTP call to Python CNN at 9801
│   └── medical_tool/
│       ├── MedicalDiagnosisPluggableTool
│       └── MedicalDiagnosisTool  HTTP call to vLLM at 9901
├── repo/
│   ├── RagChunkPo
│   ├── RagChunkMapper
│   └── RagChunkMapperImpl
├── config/
│   ├── AgentConfig             Builds supervisorAgent from ToolRegistryService
│   ├── AuthInterceptor         Token auth; whitelist: /api/auth/**, /, /*.js, /*.css, /media/**, /uploads/**, /api/samples/**
│   ├── MedicalToolConfig       Binds medical.cnn.url / medical.vllm.url
│   ├── MilvusConfig
│   ├── VectorStoreConfig
│   ├── WebMvcConfig            Static resource mappings, CORS, AuthInterceptor registration
│   └── HttpClientConfig
├── common/
│   ├── HierarchicalDocSplitter
│   ├── ThreadPoolHelper
│   └── RequestContext
└── single_demo/SingleAgent.java  Standalone demo (has own main())
```

## Architecture

### Tool Atomization Pattern

Every capability is a `PluggableTool` `@Component`:

```java
public interface PluggableTool {
    String name();
    String description();
    ToolCallback toolCallback();
    default List<String> mutuallyExclusiveWith() { return List.of(); }
}
```

All tools use `@ConditionalOnProperty(name = "tools.<name>.enabled", havingValue = "true", matchIfMissing = true)`. Toggle in `application.yml` under `tools:` without code changes.

`ToolRegistryService` collects all `List<PluggableTool>` beans via Spring autowiring and exposes `getToolCallbacks()`.

#### Mutual Exclusion Rules

Declared by each tool via `mutuallyExclusiveWith()`. Returned in `/api/tools` response so the frontend can auto-deselect conflicting tools:

| Tool | Mutually exclusive with |
|---|---|
| `ragTool` | `ragAgentTool` |
| `ragAgentTool` | `ragTool`, `webSearchTool` |
| `webSearchTool` | `ragAgentTool` |

### Multi-Agent Supervisor Pattern

`config/AgentConfig.java` builds:
- **`supervisorMemorySaver`** — singleton `MemorySaver` bean shared across all agent instances
- **`supervisorAgent`** — default singleton agent with all tools; used when `enabledTools` is not specified
- Per-request agent — built in `ChatController.resolveAgent()` when `enabledTools` is provided; reuses the same `MemorySaver` so conversation history persists by `threadId`

All agents use `MemorySaver` (in-memory, lost on restart).

### Auth Flow

```
POST /api/auth/login|register → UserService → AuthService.issueToken() → {token, userId, username}
    Frontend stores token in localStorage, sends as X-Token header on every request
    AuthInterceptor validates token → sets RequestContext.userInfo + request
    Tokens expire after 2h; CleanupService purges hourly
    Same account login again → old token revoked immediately (single session)
```

### ThreadId Convention

`userId + "_" + sessionId` (or bare `sessionId` when userId is "default").
`userId` comes from the login response (numeric id, e.g. "1"), stored in localStorage.
`SessionController.listSessions()` strips the prefix before returning to frontend.

### Frontend

Vue3 CDN SPA in `frontend/` (index.html + script.js + style.css).
Gradle `processResources` copies `frontend/` to `classpath:/static/`.
Served at `http://localhost:8080/` — no Node.js/pnpm required.

## API Endpoints

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/register` | `{username,password}` → `{success,token,userId,username}` |
| POST | `/api/auth/login` | `{username,password}` → `{success,token,userId,username}` |
| POST | `/chat/stream` | `X-Token`+`X-Tool-Flag` headers; JSON body `{message,userId,sessionId}`; SSE |
| POST | `/chat/stream/image` | `X-Token`+`X-Tool-Flag` headers; multipart `{image,message,userId,sessionId}` |
| POST | `/chat/stream/sample` | `X-Token`+`X-Tool-Flag` headers; form params `{sampleId,message,userId,sessionId}` |
| GET | `/sessions/{userId}` | `{sessions:[{sessionId,title,messageCount,updatedAt}]}` |
| GET | `/sessions/{userId}/{sessionId}` | `{messages:[{messageType,content,imageUrl?}]}` |
| DELETE | `/sessions/{userId}/{sessionId}` | 204 |
| PUT | `/sessions/{userId}/{sessionId}/title` | body `{title:"..."}` |
| POST | `/documents/upload` | multipart → `{filename,chunks,url,message}` |
| GET | `/documents` | `{documents:[{filename,originalFilename,fileType,sizeBytes,url}]}` |
| DELETE | `/documents/{filename}` | deletes file + Milvus chunks |
| GET | `/api/tools` | `[{name,description,mutuallyExclusiveWith:[...]}]` |
| GET | `/api/samples` | `[{category,label,images:[{filename,label}]}]` |
| GET | `/api/samples/{category}/{filename}` | image binary |
| POST | `/api/chat/summary` | `{messages:[...]}` → `{summary:"..."}` LLM title generation |

### SSE Event Protocol

```json
{"type":"token","text":"..."}     // streaming text token
{"type":"tool_done"}              // a tool call completed
```

## Key Configuration (application.yml)

```yaml
tools:
  rag.enabled: true
  rag-agent.enabled: true
  websearch.enabled: true
  pneumonia.enabled: true
  medical-diagnosis.enabled: true

medical:
  cnn:
    url: http://127.0.0.1:9801
  vllm:
    url: http://127.0.0.1:9901

app:
  upload:
    dir: uploads
  samples:
    dir: samples
```

## Service Dependencies

| Service | Port | Purpose |
|---|---|---|
| ai-chat-ali (this) | 8080 | Main application |
| model_interface (Python) | 9801 | CNN pneumonia classification |
| vLLM | 9901 | Medical diagnosis LLM |
| Milvus | 19530 | Vector store for RAG |

Python CNN service must start before ai-chat-ali if feiyan tool is enabled.

## Document Filename Convention

Uploaded files are stored as `{UUID36chars}_{originalFilename}`.
`DocumentService.listDocuments()` strips the first 37 characters to recover the original name.
Frontend uses `originalFilename` for duplicate detection before upload.
