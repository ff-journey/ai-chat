# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
cd backend-java/ai-chat-ali

# Run the app (serves on port 8080)
./gradlew bootRun

# Build JAR
./gradlew build

# Run tests
./gradlew test
```

After startup, the Studio debug UI is available at `http://localhost:8080/chatui/index.html`.

## Environment

- Java 21 (Alibaba Dragonwell or standard JDK)
- Gradle 9.3.1 (wrapper included)
- Required env var: `AI_DASHSCOPE_API_KEY` — used by both Spring config and standalone demos

## Architecture

This is a Spring Boot 3.5.11 app using **Spring AI Alibaba** (`spring-ai-alibaba-agent-framework` + `spring-ai-alibaba-starter-dashscope`) to build a multi-agent chat system backed by Alibaba Dashscope (default model: `qwen-turbo`).

### Multi-Agent Supervisor Pattern

Defined in `config/AgentConfig.java`, three `ReactAgent` beans form a delegation hierarchy:

- **`supervisor_agent`** — top-level router. Receives all user requests and delegates via tool calls:
  - `weather_agent` tool → for weather/location queries
  - `chat_agent` tool → for general conversation
- **`weather_agent`** — has a `get_weather` tool, uses `MemorySaver` for conversation state
- **`chat_agent`** — general-purpose assistant, no tools, uses `MemorySaver`

Sub-agents are wrapped as `FunctionToolCallback` so the supervisor invokes them as tool calls. Each agent gets its own fixed `threadId` for memory isolation (`supervisor-weather`, `supervisor-chat`).

### API Endpoints

`controller/ChatStreamController.java` exposes:
- `GET /api/chat/stream?message=...&threadId=...` — SSE streaming via `supervisorAgent.stream()`
- `POST /api/chat/send?message=...&threadId=...` — synchronous call via `supervisorAgent.call()`

### Studio Integration

The `spring-ai-alibaba-studio` dependency provides an embedded debug UI at `/chatui`. It auto-discovers all `ReactAgent` beans via `ContextScanningAgentLoader`. See `docs/studio-integration-guide.md` for the full SSE protocol, REST API, and extension points (custom `AgentLoader`, `GraphLoader`, `ThreadService`).

### Standalone Demo

`single_demo/SingleAgent.java` is a self-contained example (has its own `main()`) that demonstrates `ReactAgent` with streaming, tool interceptors, and `ToolContext` metadata — runs independently of Spring context.

## Key Patterns

- All agents use `MemorySaver` for in-memory conversation persistence (not durable across restarts)
- Streaming uses Reactor `Flux<NodeOutput>` — filter for `StreamingOutput` and check `OutputType` (AGENT_MODEL_STREAMING, AGENT_MODEL_FINISHED, AGENT_TOOL_FINISHED)
- UTF-8 encoding is explicitly set in `build.gradle` JVM args for international support
- BOM versions are pinned: `spring-ai-alibaba-bom:1.1.2.2`, `spring-ai-bom:1.1.2`

## Dependencies

Core dependencies (managed via BOMs):
- `spring-ai-alibaba-agent-framework` — ReactAgent, graph runtime, checkpoint savers
- `spring-ai-alibaba-starter-dashscope` — auto-configures `ChatModel` for Dashscope
- `spring-ai-alibaba-studio` — embedded debug UI
- `spring-boot-starter-web` — REST endpoints
- Lombok — compile-only
