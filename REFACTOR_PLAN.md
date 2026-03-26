# 重构方案：医疗诊断 Agent Demo

## Context

当前项目存在以下问题：
- 工具与 Agent 硬编码在 AgentConfig，无法灵活编排
- 依赖 spring-ai-alibaba-studio 代理接口，入口分散、侵入重
- 前端采用 Next.js 嵌入式构建，改动慢、问题难排查
- 包结构混乱，不符合 Java 三层架构习惯

**重构核心目标**：工具模块原子化，每个工具开箱即用，编排层可以随时更换尝试不同方式。

---

## 新包结构

```
ff.pro.aichatali/
├── controller/
│   ├── ChatController          # POST /chat/stream (SSE)
│   ├── SessionController       # GET/DELETE /sessions/{userId}/{sessionId}
│   ├── DocumentController      # POST/GET/DELETE /documents/*
│   └── ToolController          # GET /api/tools (查询已注册工具列表)
├── service/
│   ├── ChatService             # 构建 Agent、执行流式输出
│   ├── SessionService          # 对话历史管理（保持内存 MemorySaver）
│   ├── DocumentService         # 文档上传 + RAG 索引全流程
│   └── ToolRegistryService     # 收集所有 PluggableTool bean，动态提供工具列表
├── repository/
│   └── SessionRepository       # 当前先用 Map 内存实现，接口隔离便于后续换 DB
├── tool/                       # 每个工具 = 独立 Spring @Component
│   ├── PluggableTool.java      # 接口：name() / description() / toolCallback()
│   ├── RagTool.java            # 混合检索（现 memoryRagTool 逻辑）
│   ├── WebSearchTool.java      # Tavily 联网搜索
│   ├── PneumoniaTool.java      # CNN 肺炎图片分类（调用 Python 9801）
│   └── MedicalDiagnosisTool.java # 医疗 vLLM 问答（调用 9901）
├── rag/                        # RAG 基础设施（工具内部依赖，不暴露到编排层）
│   ├── EmbeddingService.java
│   ├── BM25Service.java
│   ├── RerankService.java
│   ├── QueryRewriter.java
│   ├── GradingService.java
│   └── RRFMerger.java
└── config/
    ├── AgentConfig.java        # 精简：从 ToolRegistryService 读工具列表，构建 Agent
    ├── VectorStoreConfig.java
    └── MedicalToolConfig.java  # medical.cnn.url / vllm.url 配置绑定
```

---

## 核心设计：工具原子化

### PluggableTool 接口

```java
public interface PluggableTool {
    String name();
    String description();
    ToolCallback toolCallback();
}
```

### 工具注册示例（RagTool）

```java
@Component
@ConditionalOnProperty(name = "tools.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagTool implements PluggableTool {
    // 封装现有 MemoryHybridRetrieverService 逻辑
    @Override public ToolCallback toolCallback() { ... }
}
```

### ToolRegistryService

```java
@Service
public class ToolRegistryService {
    @Autowired private List<PluggableTool> tools; // Spring 自动收集所有实现 bean

    public List<ToolCallback> getToolCallbacks() {
        return tools.stream().map(PluggableTool::toolCallback).toList();
    }
}
```

### AgentConfig（精简后）

```java
// 直接从 ToolRegistryService 获取工具，不再硬编码
ReactAgent agent = ReactAgent.builder()
    .tools(toolRegistryService.getToolCallbacks())
    .memoryStore(new MemorySaver())
    .build();
```

**效果**：想试新的编排方式，只需修改 AgentConfig；想加/换工具，只需实现 PluggableTool 接口，Spring 自动注册。

---

## 前端集成（简化）

丢弃 Next.js 嵌入方案，直接用 `frontend/` 下的 Vue3 CDN 页面。

**Gradle 构建改动**（build.gradle）：
- 删除 `frontendInstall`、`frontendBuild`、`frontendClean`、`frontendCopy` 任务
- 删除 `spring-ai-alibaba-studio` 依赖
- 添加 `processResources` 中的简单 copy：
  ```groovy
  processResources {
      from("${rootDir}/frontend") { into("static") }
  }
  ```
- 前端页面直接在 `http://localhost:8080/` 访问

---

## API 对齐（新 Controller 实现前端所需接口）

| 前端调用路径 | 新 Controller | 说明 |
|---|---|---|
| `POST /chat/stream` | ChatController | SSE 流式输出，替代 SSA `/run_sse` |
| `GET /sessions/{userId}` | SessionController | 会话列表 |
| `GET /sessions/{userId}/{sessionId}` | SessionController | 会话消息 |
| `DELETE /sessions/{userId}/{sessionId}` | SessionController | 删除会话 |
| `POST /documents/upload` | DocumentController | 上传文档，触发 RAG 索引 |
| `GET /documents` | DocumentController | 文档列表 |
| `DELETE /documents/{filename}` | DocumentController | 删除文档 |
| `GET /api/tools` | ToolController | 返回已注册工具名称列表 |

现有 ChatStreamController 中的 `/api/chat/multimodal` 等接口在新结构中合并到 ChatController，图片上传通过 multipart 支持。

---

## 保留不动（功能代码迁移，不重写）

- RAG 全流程逻辑：`MemoryHybridRetrieverService` 拆分迁移到 `rag/` 包各类
- BM25Service、RerankService、QueryRewriter、RRFMerger、GradingService
- PneumoniaRecognitionTool 核心 HTTP 调用逻辑
- MedicalDiagnosisTool 核心逻辑
- EmbeddingService
- UserAuthController（暂时保留，不动）

---

## 关键文件（待修改）

- `backend-java/ai-chat-ali/build.gradle` — 移除前端构建任务，加 copy 任务
- `backend-java/ai-chat-ali/src/main/java/ff/pro/aichatali/config/AgentConfig.java` — 精简重写
- `backend-java/ai-chat-ali/src/main/resources/application.yml` — 加 `tools.*` 开关配置
- 新建 `tool/PluggableTool.java` 接口
- 新建 `service/ToolRegistryService.java`
- 新建 `controller/ChatController.java`、`SessionController.java`、`DocumentController.java`

---

## 实施顺序

1. **build.gradle 清理**：移除 Next.js 任务，移除 SSA Studio 依赖，加前端 copy
2. **包结构搭建**：按新结构创建空包，把现有类移到对应位置（重命名包）
3. **工具原子化**：提取 PluggableTool 接口，逐个包装现有工具实现
4. **ToolRegistryService + AgentConfig 精简**
5. **Controller 对齐**：实现前端所需的 7 个 API 端点
6. **验证**：启动服务，访问 `http://localhost:8080/`，测试各功能

---

## 验证方式

1. `./gradlew bootRun` 启动，不再需要 pnpm/Node.js 环境
2. 访问 `http://localhost:8080/` 看到 Vue3 前端页面
3. 发送对话，验证 SSE 流式响应正常
4. 上传文档，验证 RAG 检索生效
5. 发送图片，验证肺炎分类工具触发（需 Python 服务在 9801）
6. 修改 `tools.rag.enabled=false` 重启，验证该工具从 Agent 中移除
