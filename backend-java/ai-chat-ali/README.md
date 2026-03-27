# AI Chat Ali

基于 Spring AI Alibaba 的多 Agent 聊天系统，集成 Studio 调试 UI、肺炎图片识别（CNN）和医疗诊断（vLLM）。

## 环境要求

- Java 21
- Node.js >= 18 + pnpm（或 npm）
- 环境变量：`AI_DASHSCOPE_API_KEY`

## 快速开始（开发）

```bash
cd backend-java/ai-chat-ali
./gradlew bootRun
```

启动后访问 `http://localhost:8080/chatui/index.html`。

首次运行会自动安装前端依赖并构建，后续如果前端代码没变会跳过。

## 打包部署

### 一键打包

```bash
cd backend-java/ai-chat-ali
./gradlew bootJar
```

自动执行流程：
1. 安装前端依赖（pnpm install）
2. 构建前端静态文件（STATIC_EXPORT=true）
3. 复制构建产物到 Spring Boot 静态资源目录
4. 编译 Java 代码
5. 打包为可执行 JAR

产物路径：`build/libs/ai-chat-ali-0.0.1-SNAPSHOT.jar`

### 服务器运行

```bash
export AI_DASHSCOPE_API_KEY=your-api-key
java -jar ai-chat-ali-0.0.1-SNAPSHOT.jar
```

默认端口 8080，可通过 `--server.port=9090` 修改。

### 仅构建前端

```bash
./gradlew frontendBuild
```

### 跳过前端构建（仅编译 Java）

```bash
./gradlew bootJar -x frontendInstall -x frontendBuild -x frontendCopy
```

## 项目结构

```
backend-java/
├── ai-chat-ali/                    # Spring Boot 主项目
│   ├── src/main/java/ff/pro/aichatali/
│   │   ├── config/
│   │   │   ├── AgentConfig.java        # Agent 注册与路由
│   │   │   └── MedicalToolConfig.java  # 医疗服务配置
│   │   ├── controller/
│   │   │   └── ChatStreamController.java  # REST + SSE 端点
│   │   ├── dto/
│   │   │   ├── MediaItem.java          # 媒体数据 DTO
│   │   │   └── MultimodalRequest.java  # 多模态请求 DTO
│   │   ├── service/
│   │   │   ├── ChatHistoryService.java    # 会话历史存储
│   │   │   └── CustomThreadService.java   # Studio ThreadService 覆盖
│   │   └── tool/
│   │       ├── PneumoniaRecognitionTool.java  # CNN 肺炎识别
│   │       └── MedicalDiagnosisTool.java      # vLLM 医疗诊断
│   └── src/main/resources/
│       └── application.yml
└── agent-chat-ui/                  # Studio 前端（修改版）
    └── src/
```

## Agent 路由

supervisor_agent 根据用户输入自动路由：

| 场景 | 路由目标 |
|------|----------|
| 上传胸部 X 光图片 / 肺炎检测 | pneumonia_recognition |
| 医疗健康咨询 | medicalDiagnosis |
| 天气 / 位置查询 | weather_agent |
| 其他对话 | chat_agent |

## 医疗服务配置

在 `application.yml` 中配置外部服务地址：

```yaml
medical:
  cnn:
    url: http://localhost:5000/api/pneumonia/predict
    enabled: true
  vllm:
    url: http://localhost:8000/v1/chat/completions
    model: qwen3-0.6b
    enabled: true
```

当外部服务不可用时，工具会返回模拟结果。

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/chat/stream?message=&threadId=` | SSE 流式对话 |
| POST | `/api/chat/send?message=&threadId=` | 同步对话 |
| POST | `/api/chat/multimodal` | 多模态对话（支持图片上传） |
| GET | `/chatui/index.html` | Studio 调试 UI |
