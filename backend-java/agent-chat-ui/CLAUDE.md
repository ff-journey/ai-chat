# CLAUDE.md — agent-chat-ui

> **Status: LEGACY — no longer active.**
> This module was the Spring AI Alibaba Studio debug UI (Next.js 15 / React 19).
> It has been superseded by the Vue3 CDN frontend in `frontend/` at the project root.
> The `spring-ai-alibaba-studio` dependency has been removed from `ai-chat-ali`.
> Do not use this module for new development.

---

## What this was

A Next.js 15 / React 19 frontend for the Spring AI Alibaba Studio debug UI. It replaced
the original LangGraph-based `agent-chat-ui` and was built with `pnpm build:static` to
produce a static export under `out/`, which was then copied into Spring Boot's static
resources at `/chatui`.

## Commands (preserved for reference)

```bash
pnpm install
pnpm dev              # dev server → proxies to localhost:8080
pnpm build:static     # static export → out/, basePath=/chatui
```

## Why replaced

- Build dependency on pnpm/Node.js made the dev loop slower
- Tightly coupled to Spring AI Alibaba Studio's `/run_sse`, `/list-apps` protocol
- The new Vue3 CDN approach in `frontend/` has zero build tooling requirements and
  calls the application's own REST/SSE endpoints directly

## Key files (for historical reference)

- `src/lib/spring-ai-api.ts` — `SpringAIApiClient`, all backend API calls
- `src/providers/Stream.tsx` — SSE streaming state machine
- `src/providers/Auth.tsx` — login/register against `/api/auth/*`
- `src/providers/Thread.tsx` — session/thread management
- `build-and-deploy.sh` — copied `out/` into Spring Boot resources
