# 前端构建与部署说明

## 前提条件

- Node.js >= 18
- pnpm（推荐）或 npm

## 构建步骤

### 1. 进入前端目录

```bash
cd D:\ai-code\ai-chat\backend-java\agent-chat-ui
```

### 2. 安装依赖

```bash
pnpm install
# 或
npm install
```

### 3. 构建（静态导出模式）

需要设置 `STATIC_EXPORT=true` 环境变量，才能生成可部署到 Spring Boot 的静态文件：

```bash
# Linux / macOS / Git Bash
STATIC_EXPORT=true pnpm run build

# Windows CMD
set STATIC_EXPORT=true && pnpm run build

# Windows PowerShell
$env:STATIC_EXPORT="true"; pnpm run build
```

构建产物会生成在 `agent-chat-ui/out/` 目录下，basePath 自动设为 `/chatui`。

### 4. 复制构建产物到 Spring Boot 静态资源目录

```bash
# 创建目标目录（如果不存在）
mkdir -p D:\ai-code\ai-chat\backend-java\ai-chat-ali\src\main\resources\static\chatui

# 复制构建产物（覆盖 JAR 内的默认前端）
cp -r out/* D:\ai-code\ai-chat\backend-java\ai-chat-ali\src\main\resources\static\chatui/
```

Spring Boot 会优先加载 `src/main/resources/static/chatui/` 下的文件，自动覆盖 `spring-ai-alibaba-studio` JAR 内的默认前端。

### 5. 启动后端验证

```bash
cd D:\ai-code\ai-chat\backend-java\ai-chat-ali
./gradlew bootRun
```

访问 `http://localhost:8080/chatui/index.html` 验证前端改动生效。

## 前端改动说明

共修改 3 个文件（约 15 行改动）：

1. **`src/components/thread/index.tsx`**
   - 启用了文件上传按钮（原来是 disabled + "Coming Soon"）
   - `handleSubmit` 传递 `contentBlocks` 给 `sendMessage`
   - 支持仅上传图片（无文本）时自动填充默认消息

2. **`src/providers/Stream.tsx`**
   - `sendMessage` 新增 `contentBlocks` 参数
   - 将 image 类型的 contentBlock 转换为 `MediaDTO[]`（`{mimeType, data}`）
   - media 数据通过 Studio 的 `/run_sse` 端点发送到后端

## 注意事项

- 构建产物只需复制一次，除非前端代码有新的修改
- 如果 `out/` 目录不存在，检查 `next.config.mjs` 中是否配置了 `output: 'export'`
- 前端仍然调用 Studio 原有的 `/run_sse` 端点，无需修改 API 路径
