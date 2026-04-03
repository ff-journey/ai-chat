# 项目技术亮点清单（医疗问答 Multi-Agent 系统）

> 从源码提炼，供简历编写时按需选取。每个亮点附带"简历话术方向"和"可量化角度"。

---

## 亮点 1：Multi-Agent 编排架构（Supervisor Pattern）

**技术要点**：
- 基于 Spring AI Alibaba 构建 Supervisor Agent，持有全部工具回调，LLM 判断意图并路由至专家 Agent
- 路由层级：P0 急诊协议 → P1 工具选择（RAG/CNN/vLLM/WebSearch）→ P2 非医疗通用对话
- ReactAgent + MemorySaver 实现多轮会话记忆（threadId = userId_sessionId）
- DynamicToolPromptInterceptor 运行时注入可用工具描述到 system prompt
- 80K token 阈值自动对话摘要，防止上下文溢出

**简历话术方向**：
- "设计 Supervisor Pattern 多专家 Agent 编排系统，LLM 实时判断用户意图并路由至医疗问答/影像分析/知识检索/联网搜索等专家 Agent"
- "实现基于 MemorySaver 的多轮会话记忆管理，支持 80K token 阈值自动摘要压缩"

**可量化角度**：专家 Agent 数量、路由准确率、会话轮次支持数

---

## 亮点 2：工具原子化 + 动态组合（PluggableTool + Bitmask）

**技术要点**：
- 定义 `PluggableTool` 接口，每个能力封装为独立 Spring `@Component`
- `@ConditionalOnProperty` 配置开关，零代码启停工具
- `ToolRegistryService` 通过 Spring 自动收集所有工具 bean，运行时分配 bitmask（`1 << index`）
- 前端 `X-Tool-Flag` header 传递用户选择的工具位掩码
- `CustomToolCallbackProvider` 按位掩码过滤，实现"用户选什么 Agent 用什么"
- 支持工具互斥声明（`mutuallyExclusiveWith`），前端自动处理冲突

**简历话术方向**：
- "设计工具原子化架构，基于 Spring 组件扫描 + 位掩码动态组合，实现运行时工具热插拔与用户级工具选择"
- "通过 PluggableTool 接口 + 配置驱动开关，新增工具仅需实现接口，无需修改编排代码"

**可量化角度**：工具数量（5 个）、零代码新增工具、用户可选组合数（2^5=32 种）

---

## 亮点 3：Modular RAG — 三级层次化分块 + 混合检索 + 自动合并

**技术要点**：
- `HierarchicalDocSplitter` 三级语义分块：L1(2048 chars) → L2(512) → L3(128)，针对中文医疗文档优化
- L3 切分采用中文句末标点（。！？；）+ 逗号/顿号回退策略，保留语义完整性
- Milvus 单次 API 调用同时执行 Dense（BGE 1024-dim, IVF_FLAT）+ Sparse（BM25 倒排）双路检索
- RRF(k=60) 融合排序 + Jina Reranker 精排（relevance_score 阈值过滤）
- **自动合并（Auto-Merge）**：2 轮迭代，当 ≥2 个 L3 子块命中同一父块时，自动升级为 L2/L1 父块，提供更完整上下文
- 父块通过 file-backed JSON store 持久化（`parent_chunks.json`）
- 检索结果不足 3 条时触发 Step-back Prompting 查询改写

**简历话术方向**：
- "设计三级层次化分块策略（L1/L2/L3），配合 Auto-Merge 机制，在检索细粒度与上下文完整性间取得平衡"
- "实现 Milvus 混合检索（Dense BGE + Sparse BM25 + RRF 融合）+ Jina Rerank 精排，构建多阶段检索管线"
- "引入 Step-back Prompting 查询改写，低召回时自动扩展查询语义，提升长尾 query 覆盖率"

**可量化角度**：层级数（3 级）、索引维度（1024-dim）、RRF k 值、Rerank 阈值、检索延迟

---

## 亮点 4：LoRA 微调 + vLLM 推理部署

**技术要点**：
- 基于 HuggingFace/飞桨开源医疗问答数据集（25,000+ 条，含思维链 CoT）LoRA 微调 Qwen3-0.6B
- vLLM 部署微调模型于家用 GPU（3060Ti），端口 9901
- 系统提示词引导分步诊断推理（step-by-step），输出后自动剥离 `<think>...</think>` 推理标签
- 结构化输入：`{patientInfo, clinicalMaterials}` 临床信息模板
- 服务不可用时 Graceful Fallback（Mock 响应）

**简历话术方向**：
- "基于 25,000+ 条医疗问答数据集 LoRA 微调 Qwen3-0.6B，vLLM 部署于 GPU 服务器实现低延迟推理"
- "设计结构化临床输入模板与分步诊断推理 prompt，支持 CoT 思维链输出"

**可量化角度**：训练数据量（25K+）、训练轮次（3 epoch）、模型参数量（0.6B）、推理延迟

---

## 亮点 5：LLM-as-Judge 评估体系

**技术要点**：
- 随机抽取 50 条测试集，使用 LLM 从准确性/完整性/幻觉率三维度评估微调效果
- 发现微调后指标未达预期，分析定位为 0.6B 模型容量不足以支撑长 CoT 任务
- 基于评估结论调整策略：规划筛选短问答数据集重新微调 + RAG 知识库补充
- 体现"评估驱动迭代"的工程闭环思维

**简历话术方向**：
- "建立 LLM-as-Judge 自动化评估体系，从准确性/完整性/幻觉率三维度量化微调效果"
- "基于评估数据分析定位模型容量瓶颈，制定 RAG 补充方案替代纯微调路径"

**可量化角度**：测试集规模（50 条）、评估维度（3 个）、策略迭代次数

---

## 亮点 6：CNN 影像分析 — PyTorch 模型 + FastAPI 服务化

**技术要点**：
- 4 层 CNN 分类网络：Conv2d → BN → ReLU → MaxPool，输入灰度 512×512，输出 3 类（正常/COVID/病毒性肺炎）
- FastAPI 双接口：本地路径模式 + multipart 文件上传模式（适配 frp 远程调用）
- X-ray 质量校验：亮度均值 < 180 且标准差 > 40
- Softmax 置信度输出，旋转文件日志（10MB/5 备份）

**简历话术方向**：
- "构建肺炎 X-ray 分类 CNN 模型（3 类），封装为 FastAPI REST 服务，支持本地路径与远程上传双模式"
- "设计 X-ray 图像质量校验前置检查，过滤无效输入提升推理可靠性"

**可量化角度**：分类类别数（3）、输入分辨率（512×512）、模型层数（4）

---

## 亮点 7：混合部署架构（ECS + 家用 GPU + FRP 内网穿透）

**技术要点**：
- Java 后端部署于阿里云 ECS（对外 8080），Python CNN/vLLM 运行于家用 3060Ti GPU
- FRP 隧道透明转发：ECS frps:7000 ↔ 家里 frpc，将 9801/9901 端口映射到 ECS localhost
- Java 调用 CNN/vLLM 时直接访问 127.0.0.1，FRP 透明代理，应用代码无感知
- SDKMAN 管理 JDK 版本（21.0.6-tem），`.sdkmanrc` 声明式版本锁定
- 自动化部署脚本：setup.sh（首次）、java-app.sh（启停）、frps.sh/frpc.ps1（隧道）

**简历话术方向**：
- "设计云端 + 本地 GPU 混合部署方案，通过 FRP 内网穿透将家用 3060Ti 算力无缝接入云端后端"
- "实现应用层零感知的 GPU 资源调度，Java 后端通过 localhost 透明调用远端 CNN/vLLM 推理服务"

**可量化角度**：部署节点数（2）、隧道端口数（2）、GPU 显存（12GB）

---

## 亮点 8：实时可观测性 — SSE 流式 + Span 追踪

**技术要点**：
- SpanContext 四级追踪：SUPERVISOR → AGENT → TOOL → LLM
- ToolCallCapture 拦截器自动发射 span_start/span_end SSE 事件
- Reactor Flux/Sinks 实现 per-threadId 单播流，15 秒 keepalive 防超时
- 前端实时展示 Agent 推理过程、工具调用链路、耗时分布
- 流式 Token 补偿：检测遗漏 streaming token，用 FINISHED 文本补偿

**简历话术方向**：
- "构建四级 Span 追踪体系（Supervisor/Agent/Tool/LLM），通过 SSE 实时推送推理过程与工具调用链路"
- "实现流式输出容错机制，检测并补偿遗漏 token，保障用户体验完整性"

**可量化角度**：追踪层级数（4）、SSE keepalive 间隔（15s）

---

## 亮点 9：认证与多租户

**技术要点**：
- UUID Token + 2 小时过期 + 单会话（新登录踢出旧 token）
- AuthInterceptor 拦截请求，白名单路由放行
- RequestContext 线程绑定用户信息 + toolFlag
- 每用户速率限制：滑动窗口（默认 3600s/50 轮）
- CleanupService 定时清理过期 token + 7 天历史消息

**简历话术方向**：
- "实现 Token 认证 + 单会话强制 + 滑动窗口限流，保障多租户场景下的安全性与公平性"

**可量化角度**：Token 过期时间（2h）、限流窗口（50 轮/小时）

---

## 亮点 10：文档摄取管线 — PDF 解析 + 并行 Embedding + 批量索引

**技术要点**：
- PDFBox 逐页解析 → 参考文献页检测与过滤 → HierarchicalDocSplitter 三级分块
- CompletableFuture 线程池并行 Embedding（batch=10）
- UUID 前缀文件存储，支持上传管理
- L1/L2 父块持久化至 JSON store，支持 Auto-Merge 回查
- 文档增删通过 sourceId 协调 Milvus 向量 + 父块存储一致性

**简历话术方向**：
- "实现 PDF 文档全自动摄取管线：解析 → 三级分块 → 并行 Embedding → 批量索引，支持增量更新"

**可量化角度**：batch 大小（10）、并行度、摄取耗时
