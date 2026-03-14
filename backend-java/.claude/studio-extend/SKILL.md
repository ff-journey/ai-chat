---
name: studio-extend
description: Use when extending spring-ai-alibaba-studio module — adding agents, graphs, custom loaders, thread persistence, or modifying the Studio UI. Provides architecture knowledge, API endpoints, SPI interfaces, SSE protocol format, and extension patterns.
user-invocable: true
allowed-tools: Read, Grep, Glob, Edit, Write, Bash, Agent
---

# Spring AI Alibaba Studio Extension Skill

You are extending the `spring-ai-alibaba-studio` module. Follow the architecture and patterns below.

## Module Location

`D:\ai-code\spring-ai-alibaba\spring-ai-alibaba-studio\`

## Core SPI Interfaces

### AgentLoader — Agent Discovery

- Interface: `com.alibaba.cloud.ai.agent.studio.loader.AgentLoader`
- Default: `ContextScanningAgentLoader` (auto-scans Spring context for `Agent` beans)
- Custom: extend `AbstractAgentLoader`, override `loadAgentMap()` → `Map<String, Agent>`
- Registering a custom `AgentLoader` bean disables the default (`@ConditionalOnMissingBean`)

### GraphLoader — Graph Discovery

- Interface: `com.alibaba.cloud.ai.agent.studio.loader.GraphLoader`
- Default: `ContextScanningGraphLoader` (auto-scans `CompiledGraph` beans)
- Custom: extend `AbstractGraphLoader`, override `loadGraphMap()` → `Map<String, CompiledGraph>`

### ThreadService — Session Persistence

- Interface: `com.alibaba.cloud.ai.agent.studio.service.ThreadService`
- Default: `ThreadServiceImpl` (in-memory `ConcurrentHashMap`)
- For production: replace with JPA/MongoDB/Redis implementation
- Methods: `getThread()`, `listThreads()`, `createThread()`, `deleteThread()` — all return `Mono<>`

## REST API Endpoints

### Agent Path
- `GET /list-apps` → list agents
- `POST /run_sse` → execute agent (SSE streaming), body: `AgentRunRequest`
- `POST /resume_sse` → human-in-loop resume, body: `AgentResumeRequest`
- `GET/POST/DELETE /apps/{appName}/users/{userId}/threads[/{threadId}]` → session CRUD

### Graph Path
- `GET /list-graphs` → list graphs
- `GET /graphs/{graphName}/representation` → Mermaid visualization
- `POST /graph_run_sse` → execute graph (SSE streaming), body: `GraphRunRequest`
- `GET/POST/DELETE /graphs/{graphName}/users/{userId}/threads[/{threadId}]` → session CRUD

## SSE Protocol

### AgentRunRequest
```json
{
  "appName": "agent-name",
  "userId": "user-1",
  "threadId": "thread-1",
  "newMessage": { "messageType": "user", "content": "..." },
  "streaming": true,
  "stateDelta": {}
}
```

### AgentRunResponse (SSE event)
```json
{
  "node": "node-name",
  "agent": "agent-name",
  "message": { "messageType": "assistant|tool-request|tool-confirm|tool", "content": "..." },
  "chunk": "streaming text fragment",
  "tokenUsage": { "promptTokens": 0, "generationTokens": 0, "totalTokens": 0 }
}
```

### AgentResumeRequest (Human-in-Loop)
```json
{
  "appName": "agent-name",
  "threadId": "thread-1",
  "toolFeedbacks": [{ "id": "call-id", "name": "toolName", "result": "APPROVED|REJECTED|EDITED" }]
}
```

## Message Types
- `assistant` — AI response
- `user` — user input
- `tool-request` — tool call pending approval
- `tool-confirm` — approval result (APPROVED/REJECTED/EDITED)
- `tool` — tool execution result

## Integration Patterns (by priority)

1. **Register Agent/Graph as Spring Bean** → auto-discovered, zero config
2. **Custom AgentLoader/GraphLoader** → dynamic loading from DB/config center
3. **Implement SSE endpoints directly** → no dependency on agent-framework

## Configuration
- CORS: `spring.ai.alibaba.agent.studio.web.cors.enabled=true` (allows localhost:3000/3001)
- Auto-config entry: `SaaStudioWebModuleAutoConfiguration`
- Loader auto-config: `StudioLoaderAutoConfiguration`

## Frontend
- Stack: Next.js 15 + React 19 + TypeScript + Tailwind CSS 4 + Mermaid 11
- API client: `agent-chat-ui/src/lib/spring-ai-api.ts`
- Dev: `cd spring-ai-alibaba-studio/agent-chat-ui && npm install && npm run dev`

## Key Source Files
- Controllers: `src/main/java/.../studio/controller/`
- Loaders: `src/main/java/.../studio/loader/`
- DTOs: `src/main/java/.../studio/dto/`
- Message DTOs: `src/main/java/.../studio/dto/messages/`
- Thread service: `src/main/java/.../studio/service/`

## Rules When Extending
- Follow Apache 2.0 license header convention (see CLAUDE.md)
- Use Java 17 features, SLF4J logging, Lombok annotations
- Reactive style: return `Mono<>` / `Flux<>` for async operations
- SSE responses use `Flux<ServerSentEvent<T>>`
- Graph threads use `"graph:" + graphName` as appName prefix for namespace isolation
- Full integration guide: `docs/studio-integration-guide.md`


## Plainning生成后立即保存成文件