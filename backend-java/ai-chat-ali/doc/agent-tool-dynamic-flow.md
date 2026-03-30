# Agent 工具动态控制流程

> 框架版本：`spring-ai-alibaba-agent-framework:1.1.2.0`
> 源码路径：`AgentLlmNode`, `DefaultBuilder`, `ModelRequest`, `ModelInterceptor`

---

## 一、生命周期概览

```
[启动时]  ReactAgent.build() → toolCallbacks 固定，CustomToolCallbackProvider 仅调用一次
[请求时]  Controller → RunnableConfig.metadata(toolFlag)
[执行时]  AgentLlmNode → ModelRequest → 拦截器链 → buildChatClientRequestSpec
```

---

## 二、构建时（Spring 启动）

`AgentConfig.supervisorAgent()` 调用 `ReactAgent.builder().build()`：

```
DefaultBuilder.build()
  └─ gatherLocalTools()
      └─ CustomToolCallbackProvider.getToolCallbacks()   ← 只调用这一次
          └─ ToolRegistryService.getTools()  → 返回所有 PluggableTool 的 ToolCallback
  └─ AgentLlmNode.builder().toolCallbacks(allTools).build()
      └─ buildChatOptions(toolCallbacks)
          └─ chatOptions.toolCallbacks = allTools  ← 固定，后续不再变化
  └─ AgentToolNode.builder().toolCallbacks(allTools).build()
```

**结论：`CustomToolCallbackProvider` 仅在 build 时被调用一次，之后每次请求共用同一个 supervisorAgent 实例，toolCallbacks 已固定。因此 `CustomToolCallbackProvider` 无法做到按请求动态控制工具，动态控制必须在 `ModelInterceptor` 中实现。**

---

## 三、请求时（HTTP 请求进入）

```
HTTP Request
  X-Token: <token>
  X-Tool-Flag: <bitmask>   ← 前端按用户选择的工具计算
  Body: {message, userId, sessionId}

AuthInterceptor
  └─ 验证 token → RequestContext.setUserInfo() + RequestContext.setToolFlag(bitmask)

ChatController.doStream()
  └─ RunnableConfig config = RunnableConfig.builder()
         .threadId(userId + "_" + sessionId)
         .addMetadata("toolFlag", RequestContext.getRequestContext().getToolFlag())  ← 关键
         .build()
  └─ supervisorAgent.stream(message, config)
```

---

## 四、Agent 执行时（AgentLlmNode.apply()）

```java
// 1. options 复制（含全部 toolCallbacks，build 时固定的）
.options(this.chatOptions.copy())

// 2. tools 字段：全部工具的名字列表（作为过滤白名单）
requestBuilder.tools(["rag", "webSearch", "feiyan", ...])

// 3. toolDescriptions：工具描述 map（供拦截器读取，无需再查 Registry）
requestBuilder.toolDescriptions({"rag": "...", "webSearch": "...", ...})

// 4. context：来自 RunnableConfig.metadata（含 toolFlag）
.context(config.metadata().orElse(new HashMap<>()))

ModelRequest modelRequest = requestBuilder.build();

// 5. 进入拦截器链
InterceptorChain.chainModelInterceptors(modelInterceptors, baseHandler).call(modelRequest)
```

---

## 五、拦截器（ModelInterceptor.interceptModel()）

这是动态控制工具的唯一正确入口：

```java
public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
    // 1. 从 context 读取 toolFlag（由 Controller 注入）
    int toolFlag = (int) request.getContext().getOrDefault("toolFlag", 0);

    // 2. 根据 toolFlag 过滤 options 中的 toolCallbacks
    List<ToolCallback> filtered = request.getOptions().getToolCallbacks()
        .stream()
        .filter(cb -> toolRegistryService.matchesFlag(cb.getToolDefinition().name(), toolFlag))
        .toList();

    // 3. 更新 options（拦截器只需修改 options.toolCallbacks，不需要改 tools 字段）
    ToolCallingChatOptions newOptions = request.getOptions().copy();
    newOptions.setToolCallbacks(filtered);

    // 4. 同步更新 system prompt（直接用 request.getToolDescriptions()，无需注入 Registry）
    Map<String, String> allDescs = request.getToolDescriptions();
    StringBuilder sb = new StringBuilder(request.getSystemMessage().getText());
    sb.append("\n\n【当前可用工具】\n");
    filtered.forEach(cb -> {
        String name = cb.getToolDefinition().name();
        String desc = allDescs.getOrDefault(name, "");
        sb.append(String.format("- %s：%s\n", name, desc));
    });
    sb.append("只调用以上工具，没有合适工具时直接回复用户。");

    // 5. 构建修改后的 request 并继续
    ModelRequest enhanced = ModelRequest.builder(request)
        .options(newOptions)
        .systemMessage(new SystemMessage(sb.toString()))
        .build();

    return handler.call(enhanced);
}
```

---

## 六、buildChatClientRequestSpec()（拦截器之后）

```
filterToolCallbacks(modelRequest)
  ├─ 从 options.getToolCallbacks() 取工具（拦截器已过滤）
  └─ 再用 request.getTools()（name 白名单）二次过滤
      注意：tools 字段是 build 时的全量名单，不影响结果
      因为 filter 是「options 有 AND tools 有」，options 已缩小，tools 多出的名字无害

if (!isEmpty(modelRequest.getDynamicToolCallbacks()))
  └─ 追加额外工具（此路径由拦截器主动设置才触发，正常流程不走）
  └─ 同时写入 config.context(DYNAMIC_TOOL_CALLBACKS_METADATA_KEY)（供 ToolNode 使用）

promptSpec.options(finalOptions)  → 发给模型
```

---

## 七、关键字段对照表

| 字段 | 在哪设置 | 作用 |
|---|---|---|
| `options.toolCallbacks` | build 时固定；拦截器可覆盖 | 实际传给模型的工具对象 |
| `tools`（name list） | `AgentLlmNode.apply()` 从 toolCallbacks 提取 | 过滤白名单，拦截器一般不需要改 |
| `toolDescriptions` | `AgentLlmNode.apply()` 从 toolCallbacks 提取 | 供拦截器读取描述，避免查 Registry |
| `dynamicToolCallbacks` | 拦截器主动设置才有值，AgentLlmNode 不设置 | 在 filterToolCallbacks 之后追加 |
| `context` | 来自 `RunnableConfig.metadata()` | 拦截器读取 toolFlag 的通道 |

---

## 八、当前项目实现状态

| 组件 | 文件 | 状态 |
|---|---|---|
| `CustomToolCallbackProvider` | `config/CustomToolCallbackProvider.java` | 仅 build 时调用一次，无法动态控制，动态过滤逻辑已注释移除 |
| `DynamicToolPromptInterceptor` | `config/DynamicToolPromptInterceptor.java` | ⚠️ 读 `getDynamicToolCallbacks()`（永远为空），system prompt 增强无效，待修复 |
| `ChatController` | `controller/ChatController.java` | ✅ 正确：toolFlag 通过 `addMetadata` 注入 RunnableConfig |

---

## 九、正确的动态工具控制方案（结论）

```
前端选工具 → X-Tool-Flag header
    → AuthInterceptor → RequestContext.toolFlag
    → Controller.addMetadata("toolFlag", ...)
    → RunnableConfig.metadata
    → AgentLlmNode: context = metadata（ModelRequest.context）
    → ModelInterceptor: 读 context.toolFlag → 过滤 options.toolCallbacks + 更新 systemPrompt
    → buildChatClientRequestSpec: 用过滤后的 options 发给模型
```
