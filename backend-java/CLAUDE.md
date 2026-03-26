# CLAUDE.md — backend-java

This directory contains the Java backend and its frontend SPA.

## Modules

| Module | Status | Description |
|---|---|---|
| `ai-chat-ali/` | **Active** | Spring Boot backend + Vue3 CDN frontend |
| `agent-chat-ui/` | **Legacy** | Next.js debug UI (superseded, not used in production) |

For detailed guidance on each module, see:
- `backend-java/ai-chat-ali/CLAUDE.md` — primary reference for all backend work
- `backend-java/agent-chat-ui/CLAUDE.md` — legacy module notes

## Quick Start

```bash
# Start the full application (backend + frontend)
cd backend-java/ai-chat-ali
./gradlew bootRun
# → http://localhost:8080
```

Required env var: `AI_DASHSCOPE_API_KEY`

Optional: start Python inference service first if pneumonia classification is needed:
```bash
cd backend-python/model_interface
python main.py   # → http://127.0.0.1:9801
```

## Frontend

The frontend is a **Vue3 CDN SPA** located in `frontend/` (project root level).
Gradle copies it into `classpath:/static/` at build time — no Node.js or pnpm required.

The `agent-chat-ui/` Next.js module is no longer part of the active build.
