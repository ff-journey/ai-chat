# CLAUDE.md
Rules for this session:
1. NEVER re-read a file you have already read in this conversation unless I explicitly ask or the file changed (use git diff to check).
2. Maintain a mental list of read files. Before reading, think: "Do I already know this file's content?"
3. For project overview, ONLY reference CLAUDE.md and MEMORY.md first.
4. If context is uncertain, ask me instead of re-reading the whole repo.


This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Structure

```
ai-chat/
├── frontend/                        Vue3 CDN SPA (index.html + script.js + style.css)
├── backend-java/
│   ├── ai-chat-ali/                 Spring Boot 后端 (主模块, ACTIVE)
│   └── agent-chat-ui/               Next.js 调试 UI (LEGACY, 已废弃, 不参与构建)
└── backend-python/
    ├── model_interface/             FastAPI CNN 推理服务 (端口 9801)
    └── ai-chat/                     忽略 — 学习笔记
```

各模块详细说明优先参考各自的 CLAUDE.md:
- `backend-java/ai-chat-ali/CLAUDE.md` — 后端架构、API、工具配置
- `backend-java/agent-chat-ui/CLAUDE.md` — 废弃模块说明
- `backend-python/model_interface/CLAUDE.md` — Python CNN 服务

## Build & Run

### 后端 (ai-chat-ali)
```bash
cd backend-java/ai-chat-ali
./gradlew bootRun       # 启动，端口 8080，前端同步提供
./gradlew build         # 构建 JAR
./gradlew test
```

### 前端
**无需构建**。`frontend/` 下的 Vue3 CDN 文件由 Gradle `processResources` 自动复制到
`classpath:/static/`，随后端启动一同提供，访问 `http://localhost:8080/`。

### Python 推理服务 (model_interface)
```bash
cd backend-python/model_interface
pip install torch torchvision fastapi uvicorn pillow
python main.py          # 启动在 http://127.0.0.1:9801
```
如启用 Feiyan CNN 工具，需在 ai-chat-ali 之前启动。

## 环境要求

| 组件 | 要求 |
|---|---|
| Java | 21 (Alibaba Dragonwell 或标准 JDK) |
| Gradle | 9.3.1 (wrapper 已包含) |
| Python | torch, torchvision, fastapi, uvicorn, pillow |
| `AI_DASHSCOPE_API_KEY` | **必须** — Spring Boot 启动依赖 |
| `TAVILY_API_KEY` | 可选 — 联网搜索工具 |
| `JINA_API_KEY` | 可选 — Jina rerank 服务 |

## 服务端口

| 服务 | 端口 | 访问地址 |
|---|---|---|
| ai-chat-ali (Spring Boot) | 8080 | `http://localhost:8080` |
| model_interface (FastAPI CNN) | 9801 | `http://127.0.0.1:9801` |
| vLLM 医疗诊断 | 9901 | `http://127.0.0.1:9901` |
| Milvus 向量库 | 19530 | — |

## 主要设计架构

### 工具原子化 (核心设计原则)

每个能力封装为独立 Spring `@Component`，实现 `PluggableTool` 接口：

```java
public interface PluggableTool {
    String name();
    String description();
    ToolCallback toolCallback();
}
```

- 所有工具使用 `@ConditionalOnProperty(name = "tools.<name>.enabled", ...)` 开关控制
- `ToolRegistryService` 通过 Spring 自动收集所有 `PluggableTool` bean
- `AgentConfig.supervisorAgent()` 从 `ToolRegistryService` 读取工具列表，无硬编码
- 新增/移除工具只需实现接口或修改 `application.yml`，无需改编排代码

当前工具：

| 工具 | 配置键 | 依赖 |
|---|---|---|
| `RagPluggableTool` | `tools.rag.enabled` | Milvus + BM25 混合检索 |
| `WebSearchPluggableTool` | `tools.web-search.enabled` | Tavily API |
| `FeiyanPluggableTool` | `tools.feiyan.enabled` | Python CNN (9801) |
| `MedicalDiagnosisPluggableTool` | `tools.medical-diagnosis.enabled` | vLLM (9901) |

### 多 Agent 架构 (Supervisor Pattern)

定义于 `config/AgentConfig.java`：
- **`supervisor_agent`** — 顶层路由，持有 `ToolRegistryService` 提供的全部工具
- Feiyan / 医疗工具内部通过 `FunctionToolCallback` 委托子 agent 处理
- 所有 agent 使用 `MemorySaver` 做会话记忆（重启后不持久）

### ThreadId 规范
`userId + "_" + sessionId`；userId 为 "default" 时直接用 sessionId。
`SessionController.listSessions()` 返回给前端时去掉前缀，只返回裸 sessionId。

### 关键版本
- Spring Boot: `3.5.11`
- Spring AI Alibaba BOM: `1.1.2.2`
- Spring AI BOM: `1.1.2`
- 默认模型: `qwen-turbo` (Dashscope)

---

## 部署架构

```
家里 Windows (3060Ti)                阿里云 ECS (Linux)
┌─────────────────────────┐          ┌──────────────────────────┐
│  Python CNN   :9801     │──frpc──▶ │  frps   :7000            │
│  vLLM         :9901     │──frpc──▶ │  Java   :8080 (对外)     │
└─────────────────────────┘  tunnel  └──────────────────────────┘
```

- **ECS**：运行 Java 后端（对外提供服务）+ frps（内网穿透服务端）
- **家里**：运行 Python CNN（9801）+ vLLM（9901），通过 frpc 隧道暴露给 ECS
- Java 后端调用 CNN/vLLM 时走 `127.0.0.1:9801 / 9901`，frp 透明转发到家里机器

### 部署脚本

```
deploy/
├── ecs/
│   ├── setup.sh             # bash setup.sh  （首次）安装 SDKMAN + JDK
│   ├── java-app.sh          # bash java-app.sh start | stop
│   ├── frps.sh              # bash frps.sh start | stop
│   └── config/
│       ├── .env.example     # 复制为 .env 并填值（已在 .gitignore）
│       └── frps.toml        # frps 配置（填 auth.token）
└── home/
    ├── frpc.ps1             # .\frpc.ps1 start | stop
    ├── cnn.ps1              # .\cnn.ps1 start | stop
    └── config/
        └── frpc.toml        # 填 serverAddr + auth.token，代理 9801/9901
```

JDK 版本声明在项目根 `.sdkmanrc`，`java-app.sh` 启动时自动通过 SDKMAN 切换到指定版本。

### 首次部署操作流程

#### ECS 端
```bash
# 0. 初始化环境（安装 SDKMAN + 项目所需 JDK，仅首次）
bash deploy/ecs/setup.sh

# 1. 构建 JAR 并上传
cd backend-java/ai-chat-ali && ./gradlew build
scp build/libs/ai-chat-ali.jar user@ECS:~/deploy/ecs/../../backend-java/ai-chat-ali/build/libs/

# 2. 配置环境变量
cd deploy/ecs/config && cp .env.example .env
# 填写 SILICONFLOW_API_KEY / ZILLIZ_HOST / ZILLIZ_TOKEN / TAVILY_API_KEY 等

# 3. 配置 frps（填 auth.token）并启动
vim frps.toml
bash ../frps.sh start
bash ../java-app.sh start
```

#### 家里端
```powershell
# 1. 填写 deploy\home\config\frpc.toml（serverAddr + auth.token 与 frps 一致）

# 2. 启动 frpc 和 CNN 服务
.\deploy\home\frpc.ps1 start
.\deploy\home\cnn.ps1 start
```

### 日常操作速查

| 操作 | 命令 |
|---|---|
| ECS 重启 Java | `bash java-app.sh stop && bash java-app.sh start` |
| ECS 查看日志 | `tail -f deploy/ecs/java-app.log` |
| 家里重启 frpc | `.\frpc.ps1 stop; .\frpc.ps1 start` |
| 家里重启 CNN | `.\cnn.ps1 stop; .\cnn.ps1 start` |
