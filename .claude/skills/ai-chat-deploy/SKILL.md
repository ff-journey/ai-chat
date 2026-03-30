---
name: ai-chat-deploy
description: Deploy and operate the ai-chat project on Alibaba Cloud ECS (Linux) and home Windows machine (3060Ti). Use this skill when the user asks to deploy, restart, check logs, update, or troubleshoot the ai-chat application on ECS or home machine. The user will provide SSH credentials (hostname/IP, username, password) for ECS access. The agent should execute all remote operations via SSH.
---

# ai-chat Deploy & Operations Skill

## Architecture Overview

```
Home Windows (3060Ti)               Alibaba Cloud ECS (Linux)
┌──────────────────────┐            ┌──────────────────────────┐
│  Python CNN  :9801   │──frpc──►   │  frps  :7000             │
│  vLLM        :9901   │──frpc──►   │  Java  :8080  (public)   │
└──────────────────────┘  tunnel    └──────────────────────────┘
```

- ECS runs: Java backend (port 8080, public-facing) + frps (port 7000, tunnel server)
- Home runs: Python CNN (9801) + vLLM (9901), tunneled to ECS via frpc
- Java backend calls CNN/vLLM at `127.0.0.1:9801/9901`; frp transparently forwards to home machine

## Deploy Scripts Location (on ECS)

All scripts live under the project's `deploy/ecs/` directory (relative to repo root).

```
deploy/ecs/
├── setup.sh           # bash setup.sh  (first-time) install SDKMAN + JDK
├── java-app.sh        # bash java-app.sh start | stop
├── frps.sh            # bash frps.sh start | stop
└── config/
    ├── .env           # actual env vars (not in git, copied from .env.example)
    └── frps.toml      # frps config (auth.token must match frpc.toml)
```

JDK version is declared in `.sdkmanrc` at the project root. `java-app.sh` sources SDKMAN and switches to that version automatically on each start.

Home machine scripts: `deploy/home/` (run manually by user via PowerShell).

## ECS Connection

- IP: `1.14.109.188`
- SSH: `ssh root@1.14.109.188` (public key auth, no password needed)
- OS: OpenCloudOS 9, x86_64
- Project root on ECS: `~/ai-chat`

```bash
# Run remote command
ssh root@1.14.109.188 "cd ~/ai-chat/deploy/ecs && bash java-app.sh start"

# Upload JAR
scp backend-java/ai-chat-ali/build/libs/ai-chat-ali.jar root@1.14.109.188:~/ai-chat/backend-java/ai-chat-ali/build/libs/
```

## Common Operations

### Check Status
```bash
# Java app running?
ssh ... "cat <project>/deploy/ecs/java-app.pid 2>/dev/null && kill -0 \$(cat <project>/deploy/ecs/java-app.pid) && echo running || echo stopped"

# frps running?
ssh ... "cat <project>/deploy/ecs/frps.pid 2>/dev/null && kill -0 \$(cat <project>/deploy/ecs/frps.pid) && echo running || echo stopped"

# Port listening?
ssh ... "ss -tlnp | grep -E '8080|7000'"
```

### View Logs
```bash
# Java app last 100 lines
ssh ... "tail -100 <project>/deploy/ecs/java-app.log"

# frps last 50 lines
ssh ... "tail -50 <project>/deploy/ecs/frps.log"

# Follow live
ssh ... "tail -f <project>/deploy/ecs/java-app.log"
```

### Start / Stop / Restart
```bash
# Restart Java
ssh ... "cd <project>/deploy/ecs && bash java-app.sh stop; bash java-app.sh start"

# Restart frps
ssh ... "cd <project>/deploy/ecs && bash frps.sh stop; bash frps.sh start"
```

### Deploy New JAR (full flow)
```bash
# 1. Build locally
cd backend-java/ai-chat-ali && ./gradlew build

# 2. Stop remote app
ssh ... "cd <project>/deploy/ecs && bash java-app.sh stop"

# 3. Upload JAR
scp build/libs/ai-chat-ali.jar <user>@<host>:<project>/backend-java/ai-chat-ali/build/libs/ai-chat-ali.jar

# 4. Start remote app
ssh ... "cd <project>/deploy/ecs && bash java-app.sh start"
```

### First-time Setup
- ECS environment (SDKMAN, JDK, git clone, .env): see `references/ecs-setup.md`
- ECS frps install & config: see `references/frp-setup-ecs.md`
- Home frpc install & config: see `references/frp-setup-home.md`

Quick ECS bootstrap order:
```bash
bash deploy/ecs/setup.sh          # install SDKMAN + JDK (idempotent)
# configure .env and frps.toml, upload JAR, then:
bash deploy/ecs/frps.sh start
bash deploy/ecs/java-app.sh start
```

## Environment Variables (.env on ECS)

Located at `deploy/ecs/config/.env` (not in git). Keys:
```
SILICONFLOW_API_KEY=
ZILLIZ_HOST=
ZILLIZ_TOKEN=
TAVILY_API_KEY=
JINA_API_KEY=
```

To edit remotely:
```bash
ssh ... "nano <project>/deploy/ecs/config/.env"
```

## Troubleshooting

| Symptom | Check |
|---|---|
| Java won't start | Check `.env` has required keys; JAR exists at expected path |
| Port 8080 unreachable | ECS security group allows 8080; java-app.sh started successfully |
| CNN/vLLM unreachable from ECS | frpc running on home; frps running on ECS; tokens match |
| Stale PID file | Delete `deploy/ecs/java-app.pid` or `frps.pid` manually, then restart |
| Out of memory | `ssh ... "free -h"` then consider reducing JVM heap or stopping other processes |

## Key Paths Reference

| Item | ECS Path |
|---|---|
| Project root | User provides at runtime (or check `~/` for clone location) |
| JAR | `<project>/backend-java/ai-chat-ali/build/libs/ai-chat-ali.jar` |
| java-app.sh | `<project>/deploy/ecs/java-app.sh` |
| frps.sh | `<project>/deploy/ecs/frps.sh` |
| .env | `<project>/deploy/ecs/config/.env` |
| frps.toml | `<project>/deploy/ecs/config/frps.toml` |
| Java log | `<project>/deploy/ecs/java-app.log` |
| frps log | `<project>/deploy/ecs/frps.log` |
