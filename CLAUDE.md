# CLAUDE.md
Rules for this session:
1. NEVER re-read a file you have already read in this conversation unless I explicitly ask or the file changed (use git diff to check).
2. Maintain a mental list of read files. Before reading, think: "Do I already know this file's content?"
3. For project overview, ONLY reference CLAUDE.md and MEMORY.md first.
4. If context is uncertain, ask me instead of re-reading the whole repo.


This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Structure

This repository contains two main backends for an AI chat application:



### Backend-Java (`backend-java/`)
1. **重要模块: Agent-chat-ui** (`backend-java/agent-chat-ui`)
   - **前端ui** 提供项目的页面ui逻辑, 优先参考 (`backend-java/agent-chat-ui/CLAUDE.md`)
2. **重要模块: Ai-chat-ali** (`backend-java/ai-chat-ali`)
   - **后端java** 项目页面ui请求的直接提供者, 优先参考 (`backend-java/ai-chat-ali/CLAUDE.md`)


### Backend-Python (`backend-python/model_interface`)
1. **重要模块: Model-interface** (`backend-python/model_interface`)
    - **提供基础能力接口** 作为基础设施, 供Ai-chat-ali使用, 优先参考 (`backend-python/model_interface/CLAUDE.md`)
2. **Ai-chat** (`backend-python/ai-chat`): **忽略, 该目录下为学习笔记, 忽略**:

### Java Backend
```bash
# Build and run
cd backend-java/ai-chat-ali
./gradlew bootRun

# Run tests
./gradlew test

# Build JAR
./gradlew build
```

### Frontend (agent-chat-ui)
```bash
cd backend-java/agent-chat-ui
pnpm install
pnpm dev                # dev server, proxies to localhost:8080

pnpm build:static       # static export → out/, for embedding in Spring Boot
# After build:static, run build-and-deploy.sh to copy out/ into Spring Boot resources
```

### Python Inference Service (model_interface)
```bash
cd backend-python/model_interface
pip install torch torchvision fastapi uvicorn pillow
python main.py          # starts on http://127.0.0.1:9801
```

## **环境要求**

| 组件 | 要求 |
|---|---|
| Java | 21 (Alibaba Dragonwell 或标准 JDK) |
| Gradle | 9.3.1 (wrapper 已包含) |
| Node.js / pnpm | pnpm v10.5.1 |
| Python | 含 torch, torchvision, fastapi, uvicorn, pillow |
| 必须环境变量 | `AI_DASHSCOPE_API_KEY` — Dashscope API 密钥, Spring Boot 启动必须设置 |

## **服务端口**

| 服务 | 端口 | 访问地址 |
|---|---|---|
| ai-chat-ali (Spring Boot) | 8080 | `http://localhost:8080` |
| Studio 调试 UI | 8080 | `http://localhost:8080/chatui/index.html` |
| model_interface (FastAPI) | 9801 | `http://127.0.0.1:9801` |

## **主要设计架构**
- Agent-chat-ui模块是集成spring-ai-alibaba-studio的二次开发部分, 通过编译后替换Ai-chat-ali模块jar包中的静态资源实现页面ui逻辑的覆盖, 在必要时可参考(`backend-java/agent-chat-ali/build.gradle`)

### 多 Agent 架构 (Supervisor Pattern)
定义于 `config/AgentConfig.java`, 三个 `ReactAgent` bean 构成委托层级:
- **`supervisor_agent`** — 顶层路由, 将请求委托给子 agent (tool call 方式)
  - `weather_agent` — 天气/位置查询, 含 `get_weather` tool
  - `chat_agent` — 通用对话
- 子 agent 通过 `FunctionToolCallback` 包装, 各自持有独立 `threadId` 做内存隔离
- 所有 agent 使用 `MemorySaver` 做会话记忆 (重启后不持久)

### 服务间依赖
- **ai-chat-ali → model_interface**: 肺炎 X 光识别功能 (`PneumoniaRecognitionTool`) 通过 HTTP 调用 `POST http://127.0.0.1:9801/api/pneumonia/predict`
- model_interface 为基础设施层, 需在 ai-chat-ali 之前启动

### 关键版本
- Spring Boot: 3.5.11
- Spring AI Alibaba BOM: `1.1.2.2`
- Spring AI BOM: `1.1.2`
- 默认模型: `qwen-turbo` (Dashscope)
