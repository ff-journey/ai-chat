# Spring AI Alibaba Studio 对接指南

## 架构概览

Studio 是一个嵌入式调试 UI，通过 SSE (Server-Sent Events) 与后端 Agent 通信。核心有两条路径：

- **Agent 路径**: `/list-apps` → `/run_sse` → `/resume_sse`
- **Graph 路径**: `/list-graphs` → `/graphs/{name}/representation` → `/graph_run_sse`

## 模块结构

```
spring-ai-alibaba-studio/
├── agent-chat-ui/                    # Next.js 前端 (React 19 + TypeScript)
│   ├── src/lib/spring-ai-api.ts      # API 客户端（核心）
│   ├── src/providers/                # React Context (Thread, Stream, GraphThread, GraphStream)
│   └── src/components/               # UI 组件 (Shadcn UI + Mermaid 图可视化)
└── src/main/java/.../studio/
    ├── controller/                   # REST API 端点
    │   ├── AgentController           # GET /list-apps
    │   ├── ExecutionController       # POST /run_sse, /resume_sse
    │   ├── GraphController           # GET /list-graphs, /graphs/{name}/representation
    │   ├── GraphExecutionController  # POST /graph_run_sse
    │   ├── ThreadController          # Agent 会话 CRUD
    │   └── GraphThreadController     # Graph 会话 CRUD
    ├── loader/                       # SPI 扩展点
    │   ├── AgentLoader               # Agent 发现接口
    │   ├── GraphLoader               # Graph 发现接口
    │   └── ContextScanningXxxLoader  # 默认实现（自动扫描 Spring Bean）
    ├── service/
    │   └── ThreadService             # 会话管理接口（默认内存实现）
    └── dto/                          # 数据传输对象
        └── messages/                 # 多态消息体系 (assistant/user/tool-request/tool-confirm/tool)
```

## 对接方式

### 方式一：注册 Spring Bean（推荐，最简单）

Studio 默认使用 `ContextScanningAgentLoader`，自动扫描容器中所有 `Agent` Bean。

```java
@Configuration
public class MyAgentConfig {

    @Bean
    public ReactAgent myCustomAgent(ChatClient.Builder chatClientBuilder) {
        return ReactAgent.builder()
            .name("my-assistant")  // 出现在 UI 列表中的名称
            .chatClient(chatClientBuilder.build())
            .description("我的自定义智能体")
            .tools(/* your tools */)
            .build();
    }
}
```

引入依赖后启动，访问 `/chatui` 即可。

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-studio</artifactId>
</dependency>
```

### 方式二：自定义 AgentLoader

适用于 Agent 非标准 Bean、需要动态加载的场景。

```java
@Component
public class MyAgentLoader extends AbstractAgentLoader {

    @Override
    protected Map<String, Agent> loadAgentMap() {
        Map<String, Agent> agents = new HashMap<>();
        agents.put("agent-a", buildAgentA());
        agents.put("agent-b", buildAgentB());
        return agents;
    }
}
```

> 注意：注册自定义 `AgentLoader` 后，默认的 `ContextScanningAgentLoader` 不会生效（`@ConditionalOnMissingBean`）。

### 方式三：Graph 工作流

基于 Graph 的智能体同样会被自动发现：

```java
@Bean
public CompiledGraph myWorkflow() {
    StateGraph graph = new StateGraph("my-workflow", MyState::new)
        .addNode("classify", classifyNode)
        .addNode("respond", respondNode)
        .addEdge(START, "classify")
        .addConditionalEdges("classify", routingFunction, Map.of(
            "simple", "respond",
            "complex", "deepThink"
        ))
        .addEdge("respond", END);
    return graph.compile();
}
```

Studio 会自动展示 Graph 的 Mermaid 可视化图。

### 方式四：直接对接 SSE 接口协议

不依赖 agent-framework，自己实现 Studio 期望的 SSE 端点。

**POST `/run_sse`** 请求体：
```json
{
  "appName": "your-agent-name",
  "userId": "user-1",
  "threadId": "thread-1",
  "newMessage": {
    "messageType": "user",
    "content": "你好"
  },
  "streaming": true
}
```

**SSE 响应事件格式：**
```json
{
  "node": "节点名",
  "agent": "agent名",
  "message": {
    "messageType": "assistant",
    "content": "回复内容"
  },
  "chunk": "流式文本片段",
  "tokenUsage": {
    "promptTokens": 100,
    "generationTokens": 50,
    "totalTokens": 150
  }
}
```

### 方式五：Human-in-the-Loop（工具审批）

1. Agent 执行遇到需审批的工具调用 → SSE 返回 `tool-request` 类型消息
2. 前端展示审批 UI，用户选择 APPROVED / REJECTED / EDITED
3. 前端调用 **POST `/resume_sse`**：

```json
{
  "appName": "my-agent",
  "threadId": "thread-1",
  "toolFeedbacks": [
    {
      "id": "tool-call-id",
      "name": "sendEmail",
      "result": "APPROVED"
    }
  ]
}
```

## REST API 完整列表

### Agent 管理
| 方法 | 端点 | 用途 |
|------|------|------|
| GET | `/list-apps` | 列出所有 Agent |
| POST | `/run_sse` | 执行 Agent（SSE 流式） |
| POST | `/resume_sse` | 恢复执行（Human-in-Loop） |

### Agent 会话
| 方法 | 端点 | 用途 |
|------|------|------|
| GET | `/apps/{appName}/users/{userId}/threads` | 列出会话 |
| GET | `/apps/{appName}/users/{userId}/threads/{threadId}` | 获取会话 |
| POST | `/apps/{appName}/users/{userId}/threads` | 创建会话 |
| DELETE | `/apps/{appName}/users/{userId}/threads/{threadId}` | 删除会话 |

### Graph 管理
| 方法 | 端点 | 用途 |
|------|------|------|
| GET | `/list-graphs` | 列出所有 Graph |
| GET | `/graphs/{graphName}/representation` | 获取 Graph Mermaid 图 |
| POST | `/graph_run_sse` | 执行 Graph（SSE 流式） |

### Graph 会话
| 方法 | 端点 | 用途 |
|------|------|------|
| GET | `/graphs/{graphName}/users/{userId}/threads` | 列出会话 |
| GET | `/graphs/{graphName}/users/{userId}/threads/{threadId}` | 获取会话 |
| POST | `/graphs/{graphName}/users/{userId}/threads` | 创建会话 |
| DELETE | `/graphs/{graphName}/users/{userId}/threads/{threadId}` | 删除会话 |

## 关键扩展点

| 扩展点 | 接口 | 默认实现 | 用途 |
|--------|------|----------|------|
| Agent 发现 | `AgentLoader` | `ContextScanningAgentLoader` | 控制哪些 Agent 出现在 UI |
| Graph 发现 | `GraphLoader` | `ContextScanningGraphLoader` | 控制哪些 Graph 出现在 UI |
| 会话持久化 | `ThreadService` | `ThreadServiceImpl`（内存） | 可替换为数据库实现 |
| CORS 配置 | 属性 `spring.ai.alibaba.agent.studio.web.cors.enabled` | `true` | 控制跨域 |

## 前端开发

```bash
cd spring-ai-alibaba-studio/agent-chat-ui
npm install
npm run dev   # localhost:3000
```

后端 CORS 默认放行 `localhost:3000` 和 `localhost:3001`。

## 消息类型体系

```
MessageDTO (interface)
├── AssistantMessageDTO   (messageType: "assistant")
├── UserMessageDTO        (messageType: "user")
├── ToolRequestMessageDTO (messageType: "tool-request")
├── ToolRequestConfirmMessageDTO (messageType: "tool-confirm")
└── ToolResponseMessageDTO (messageType: "tool")
```
