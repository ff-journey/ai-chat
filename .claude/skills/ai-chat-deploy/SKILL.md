---
name: ai-chat-deploy
description: >-
  Deploy and operate the ai-chat project on Alibaba Cloud ECS (Linux) and home
  Windows machine (3060Ti). Triggers on: deploy, restart, check logs, update,
  troubleshoot, upload build, view status, start/stop Java app or services.
  Covers: build & upload JAR, start/stop Java backend, frp tunnel management,
  ECS first-time setup, .env configuration, log viewing, health checks.
---

# ai-chat Deploy & Operations

## Architecture

```
Home Windows (3060Ti)               Alibaba Cloud ECS (Linux)
┌──────────────────────┐            ┌──────────────────────────┐
│  Python CNN    :9801 │──frpc──►   │  frps  :7000             │
│  Medical Svc   :9901 │──frpc──►   │  Java  :8080  (public)   │
└──────────────────────┘  tunnel    └──────────────────────────┘
```

- **ECS**: Java backend (8080, public) + frps (7000, tunnel server)
- **Home**: Python CNN (9801) + Medical Service (9901), tunneled via frpc
- Java calls CNN/Medical at `127.0.0.1:9801/9901`; frp forwards transparently

## ECS Connection

```bash
ssh -i ~/.ssh/id_ed25519 root@1.14.109.188   # GitHub SSH key auth
```

Project root on ECS: `~/ai-chat`

## frp Delegation

**Before any frp operation** (install, start, stop, add proxy), check if `dotfiles` skill is available.

- **dotfiles available** → delegate all frp lifecycle to it:
  - Install: dotfiles handles binary install
  - Start/stop frpc: `frpc start` / `frpc stop` via dotfiles
  - Start/stop frps: `frps start` / `frps stop` via dotfiles
  - Add proxy: `frpc add` via dotfiles (edits `$DOTFILES/frp/frpc.toml`)
- **dotfiles NOT available** → use project-local scripts. See [frp-setup-ecs.md](references/frp-setup-ecs.md) and [frp-setup-home.md](references/frp-setup-home.md)

## Command Routing

| Operation | Reference |
|-----------|-----------|
| Deploy new build (full flow) | "Deploy" section below |
| Start / stop / restart Java | "Java App" section below |
| Check status / view logs | "Status & Logs" section below |
| First-time ECS setup | [ecs-setup.md](references/ecs-setup.md) |
| frp setup (no dotfiles skill) | [frp-setup-ecs.md](references/frp-setup-ecs.md), [frp-setup-home.md](references/frp-setup-home.md) |

## Deploy

```bash
# 1. Build dist package locally
cd backend-java/ai-chat-ali && ./gradlew dist
# Produces: <rootDir>/ai-chat-boot/ (bin/, lib/, config/, samples/)

# 2. Stop remote app
ssh root@1.14.109.188 "cd /opt/ai-chat-boot && bash bin/ai-chat.sh stop"

# 3. Upload to ECS
cd <rootDir> && tar cf /tmp/ai-chat-boot.tar ai-chat-boot/
scp /tmp/ai-chat-boot.tar root@1.14.109.188:/tmp/
ssh root@1.14.109.188 "rm -rf /opt/ai-chat-boot && tar xf /tmp/ai-chat-boot.tar -C /opt/ && rm /tmp/ai-chat-boot.tar"

# 4. First deploy only: configure .env
ssh root@1.14.109.188 "cp /opt/ai-chat-boot/config/.env.example /opt/ai-chat-boot/config/.env"
# Then edit .env with required keys (see "Environment Variables" below)

# 5. Start
ssh root@1.14.109.188 "cd /opt/ai-chat-boot && bash bin/ai-chat.sh start"
```

## Java App

```bash
# Start / Stop / Restart
ssh root@1.14.109.188 "cd /opt/ai-chat-boot && bash bin/ai-chat.sh start"
ssh root@1.14.109.188 "cd /opt/ai-chat-boot && bash bin/ai-chat.sh stop"
ssh root@1.14.109.188 "cd /opt/ai-chat-boot && bash bin/ai-chat.sh stop; bash bin/ai-chat.sh start"
```

## Status & Logs

```bash
# Java running?
ssh root@1.14.109.188 "cat /opt/ai-chat-boot/app.pid 2>/dev/null && kill -0 \$(cat /opt/ai-chat-boot/app.pid) && echo running || echo stopped"

# frps running?
ssh root@1.14.109.188 "cat ~/ai-chat/deploy/ecs/frps.pid 2>/dev/null && kill -0 \$(cat ~/ai-chat/deploy/ecs/frps.pid) && echo running || echo stopped"

# Ports listening?
ssh root@1.14.109.188 "ss -tlnp | grep -E '8080|7000|9801|9901'"

# Java logs
ssh root@1.14.109.188 "tail -100 /opt/ai-chat-boot/console.log"
ssh root@1.14.109.188 "tail -f /opt/ai-chat-boot/console.log"   # live follow

# frps logs
ssh root@1.14.109.188 "tail -50 ~/ai-chat/deploy/ecs/frps.log"
```

## Home Machine Services

```powershell
# Ensure Mihomo/Clash has 1.14.109.188 in DIRECT rules before frpc
.\deploy\home\cnn.ps1 start        # CNN on :9801
.\deploy\home\cnn.ps1 stop
.\deploy\home\medical.ps1 start    # Medical on :9901
.\deploy\home\medical.ps1 stop
```

## Environment Variables

Located at `/opt/ai-chat-boot/config/.env` on ECS (not in git):
```
SILICONFLOW_API_KEY=
ZILLIZ_HOST=
ZILLIZ_TOKEN=
TAVILY_API_KEY=
JINA_API_KEY=
```

## Key Paths

| Item | Path |
|------|------|
| App home | `/opt/ai-chat-boot/` |
| Startup script | `/opt/ai-chat-boot/bin/ai-chat.sh` |
| JAR | `/opt/ai-chat-boot/lib/ai-chat-boot.jar` |
| App config | `/opt/ai-chat-boot/config/` |
| App log | `/opt/ai-chat-boot/console.log` |
| App PID | `/opt/ai-chat-boot/app.pid` |
| frps script | `~/ai-chat/deploy/ecs/frps.sh` |
| frps config | `~/ai-chat/deploy/ecs/config/frps.toml` |
| frps log | `~/ai-chat/deploy/ecs/frps.log` |
| frpc binary (home) | `G:\tool\frp\frp_0.68.0_windows_amd64\frpc.exe` |

## Troubleshooting

| Symptom | Check |
|---------|-------|
| Java won't start | `.env` has required keys; JAR at `/opt/ai-chat-boot/lib/` |
| Port 8080 unreachable | ECS security group allows 8080; app started |
| CNN/Medical unreachable | frpc on home + frps on ECS running; tokens match |
| Stale PID file | Delete `.pid` manually, then restart |
| Out of memory | `ssh ... "free -h"` — reduce JVM heap or stop other processes |
