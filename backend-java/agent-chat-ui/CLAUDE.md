# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Install dependencies
pnpm install

# Development server (proxies to Spring Boot at localhost:8080 via .env.development)
pnpm dev

# Standard Next.js build (for standalone deployment)
pnpm build

# Static export for embedding in Spring Boot (outputs to out/, served at /chatui)
pnpm build:static

# Lint / format
pnpm lint
pnpm lint:fix
pnpm format
pnpm format:check
```

Package manager is **pnpm** (v10.5.1). There are no test scripts.

## Environment

- `NEXT_PUBLIC_API_URL` — base URL for all API calls. In dev: `http://localhost:8080`. In production static export: leave unset (uses relative paths, since the app is served from within the Spring Boot server).
- `NEXT_PUBLIC_APP_NAME` — default agent name (e.g. `research_agent`).
- `NEXT_PUBLIC_USER_ID` — default user ID (`user-001`). At runtime, the auth flow overrides this via `localStorage.auth_userId`.

Copy `.env.example` to `.env.development.local` or `.env.production.local` to override.

## Architecture

This is a **Next.js 15 / React 19** frontend for the Spring AI Alibaba Studio debug UI. It replaces the original LangGraph-based `agent-chat-ui`. Despite `@langchain/langgraph-sdk` being listed as a dependency, it is **not used** — all API communication goes through the custom `SpringAIApiClient` in `src/lib/spring-ai-api.ts`.

### Static Export & Deployment

When built with `STATIC_EXPORT=true` (`pnpm build:static`), Next.js exports a static site to `out/` with `basePath: /chatui`. The `build-and-deploy.sh` script copies this output into the Spring Boot resources directory so it is served at `http://localhost:8080/chatui/index.html`.

### Provider Hierarchy

Each page wraps children in this provider chain (outer → inner):

```
AuthProvider → ThreadProvider → StreamProvider → ArtifactProvider → UI
```

- **`AuthProvider`** (`src/providers/Auth.tsx`) — login/register against `/api/auth/login` and `/api/auth/register`. Stores `userId` in `localStorage.auth_userId`. Guards all pages behind `<AuthGate>`.
- **`ThreadProvider`** (`src/providers/Thread.tsx`) — manages the list of conversation threads (sessions) for the selected agent or graph. Fetches `/list-apps` and `/list-graphs` on mount. Supports an `initialLock` prop (used by deep-link pages) that pins the mode/agent/graph and hides selectors.
- **`StreamProvider`** (`src/providers/Stream.tsx`) — owns the `UIMessage[]` state and handles all SSE streaming. Routes to:
  - `/run_sse` (agent mode, standard text)
  - `/graph_run_sse` (graph mode)
  - `/api/chat/multimodal` (image file upload)
  - `/api/chat/multimodal/sample` (pre-loaded sample image)
  - `/resume_sse` (human-in-the-loop tool feedback)

### Routing

The home page (`src/app/page.tsx`) is the entry point. It reads `?agent=` or `?graph=` search params:
- If `?agent=<name>` → renders `<AgentPageClient>` inline (no navigation).
- If `?graph=<name>` → renders `<GraphPageClient>` inline.
- Otherwise → shows the agent/graph selection cards.

Dedicated Next.js routes at `/agent/[agentName]` and `/graph/[graphName]` exist but are used only in full Next.js server mode. In static export mode, all navigation goes through the home page with query params and `href="/index.html?agent=..."` links.

### API Client (`src/lib/spring-ai-api.ts`)

`SpringAIApiClient` wraps all backend endpoints. Key methods:

| Method | Endpoint | Purpose |
|---|---|---|
| `listApps()` | `GET /list-apps` | Discover registered agents |
| `listGraphs()` | `GET /list-graphs` | Discover registered graphs (returns `[]` on 404) |
| `runAgentStream()` | `POST /run_sse` | SSE stream for agent chat |
| `runGraphStream()` | `POST /graph_run_sse` | SSE stream for graph execution |
| `resumeAgentStream()` | `POST /resume_sse` | Resume after human-in-the-loop |
| `runMultimodalStream()` | `POST /api/chat/multimodal` | Image file + text |
| `runSampleImageStream()` | `POST /api/chat/multimodal/sample` | Sample image + text |
| `createSession()` / `createGraphSession()` | `POST /apps/:app/users/:user/threads` | Create thread |

SSE events carry `AgentRunResponse` / `GraphRunResponse` JSON: `{ node, agent, chunk, message, tokenUsage }`. The `StreamProvider` accumulates `chunk` strings into the last assistant message and appends complete `message` objects as new `UIMessage` entries.

### Message Types (`src/types/messages.ts`)

Custom message type union mirroring backend DTOs:
- `user` — human input, optional `media[]` for multimodal
- `assistant` — AI response, optional `toolCalls[]`
- `tool-request` — agent wants to call a tool (shown in UI before execution)
- `tool-confirm` — human-in-the-loop: user must APPROVE / REJECT / EDIT before execution resumes
- `tool` — tool response after execution

### Graph Workspace

The `GraphWorkspace` / `GraphStream` / `GraphThread` providers in `src/providers/` and `src/components/graph/` form an independent workspace for observing graph execution, including a Mermaid diagram (`GraphDiagram`), node timeline (`NodeTimeline`), and state inspector (`StateInspector`). These use `src/lib/spring-ai-api.ts` `getGraphRepresentation()` to fetch Mermaid source from `/graphs/:name/representation`.
