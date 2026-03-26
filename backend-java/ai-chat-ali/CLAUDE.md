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
│   ├── ChatHistoryService      In-memory message + title store (ConcurrentHashMap)
│   ├── DocumentService         Upload → UUID rename → Milvus index
│   ├── ToolRegistryService     Collects List<PluggableTool> beans, exposes getToolCallbacks()
│   ├── MemoryHybridRetrieverService  BM25 + vector hybrid retrieval + rerank + grading
│   ├── EmbeddingService        Dashscope embedding calls
│   ├── RerankService           HTTP rerank (Jina or custom)
│   ├── GradingService          LLM relevance grading
│   ├── QueryRewriter           LLM query rewriting
│   └── UserService
├── service/rag/
│   ├── BM25Service
│   └── RRFMerger
├── service/websearch/
│   ├── WebSearchPort           Interface
│   ├── TavilyWebSearchService
│   └── WebSearchTool
├── tool/                       Each tool = Spring @Component implementing PluggableTool
│   ├── PluggableTool.java      Interface: name() / description() / toolCallback()
│   ├── rag_tool/RagPluggableTool
│   ├── fetch_tool/WebSearchPluggableTool
│   ├── feiyan_tool/
│   │   ├── FeiyanPluggableTool   Wraps FeiyanAgentMedicalTool as PluggableTool
│   │   ├── FeiyanAgentMedicalTool  Sub-agent tool (supervisor delegates to it)
│   │   └── FeiyanCnnTool         HTTP call to Python CNN at 9801
│   └── medical_tool/
│       ├── MedicalDiagnosisPluggableTool
│       └── MedicalDiagnosisTool  HTTP call to vLLM at 9901
├── repo/
│   ├── RagChunkPo
│   ├── RagChunkMapper
│   └── RagChunkMapperImpl
├── config/
│   ├── AgentConfig             Builds supervisor_agent from ToolRegistryService
│   ├── MedicalToolConfig       Binds medical.cnn.url / medical.vllm.url
│   ├── MilvusConfig
│   ├── VectorStoreConfig
│   ├── WebMvcConfig            Static resource mappings, CORS
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
}
```

All tools use `@ConditionalOnProperty(name = "tools.<name>.enabled", havingValue = "true", matchIfMissing = true)`. Toggle in `application.yml` under `tools:` without code changes.

`ToolRegistryService` collects all `List<PluggableTool>` beans via Spring autowiring and exposes `getToolCallbacks()`. `AgentConfig.supervisorAgent()` reads from it — no hardcoded tool list.

### Multi-Agent Supervisor Pattern

`config/AgentConfig.java` builds:
- **`supervisor_agent`** — top-level, delegates to feiyan/medical sub-agents + uses RAG/web-search tools directly
- Sub-agents wrapped as `FunctionToolCallback` with isolated `threadId` for memory

All agents use `MemorySaver` (in-memory, lost on restart).

### ThreadId Convention

`userId + "_" + sessionId` (or bare `sessionId` when userId is "default").
`SessionController.listSessions()` strips the prefix before returning to frontend.

### Frontend

Vue3 CDN SPA in `frontend/` (index.html + script.js + style.css).
Gradle `processResources` copies `frontend/` to `classpath:/static/`.
Served at `http://localhost:8080/` — no Node.js/pnpm required.

## API Endpoints

| Method | Path | Notes |
|---|---|---|
| POST | `/chat/stream` | JSON body `{message,userId,sessionId}`; SSE JSON events |
| POST | `/chat/stream/image` | multipart `{image,message,userId,sessionId}` |
| POST | `/chat/stream/sample` | form params `{sampleId,message,userId,sessionId}` |
| GET | `/sessions/{userId}` | `{sessions:[{sessionId,title,messageCount,updatedAt}]}` |
| GET | `/sessions/{userId}/{sessionId}` | `{messages:[{messageType,content,imageUrl?}]}` |
| DELETE | `/sessions/{userId}/{sessionId}` | 204 |
| PUT | `/sessions/{userId}/{sessionId}/title` | body `{title:"..."}` |
| POST | `/documents/upload` | multipart → `{filename,chunks,url,message}` |
| GET | `/documents` | `{documents:[{filename,originalFilename,fileType,sizeBytes,url}]}` |
| DELETE | `/documents/{filename}` | deletes file + Milvus chunks |
| GET | `/api/tools` | `[{name,description}]` |
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
  web-search.enabled: true
  feiyan.enabled: true
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
