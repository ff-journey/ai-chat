---
name: resume-writer
description: "基于医疗问答 Multi-Agent 系统项目生成定制化简历项目经历。结合项目技术亮点（Multi-Agent 编排、Modular RAG、LoRA 微调、CNN 影像分析、混合部署等）与用户目标岗位，按简历编写原则输出高质量项目描述（中英文）。Use when user says '写简历', 'resume', '简历', 'write resume', '项目经历', 'project experience', '简历项目', or asks to generate resume content based on this project."
---

# Resume Writer — 医疗问答 Multi-Agent 系统

基于"写作原则 + 项目亮点 + 用户需求 = 定制化简历"生成简历项目经历。

## 工作流程

### Phase 1: 加载知识

1. 读取 [references/resume_principles.md](references/resume_principles.md) — 四段式结构、技术标签、亮点策略
2. 读取 [references/project_highlights.md](references/project_highlights.md) — 10 大技术亮点（含话术和量化角度）
3. 按需深读源码补充细节

### Phase 2: 需求确认

如用户已提供简历初稿和明确需求，跳过问答直接进入 Phase 3。

否则用 `ask_questions` 收集（最多 3 个问题）：

**问题 1 — 目标岗位**（单选）：
- Java Backend / LLM Application Engineer / Agent Engineer / MLE / 全栈 AI
- 决定：技术关键词策略、亮点筛选优先级

**问题 2 — 技术侧重**（多选）：
- Multi-Agent 编排 / RAG 混合检索 / LoRA 微调 / CNN 推理 / 可插拔架构 / 混合部署 / 可观测性 / 评估体系
- 决定：哪 3-5 个亮点写入 bullet points

**问题 3 — 特殊要求**（自由文本）：
- 示例："往 Agent 方向靠""强调系统设计""中英双版本""5 条 bullet 以内"

### Phase 3: 亮点匹配与内容生成

#### 3.1 岗位匹配策略

| 岗位方向 | 优先亮点 |
|---------|---------|
| Java Backend / 架构 | 亮点2(工具原子化) → 3(RAG) → 7(混合部署) → 8(可观测性) |
| Agent Engineer | 亮点1(Multi-Agent) → 2(工具原子化) → 3(RAG) → 5(评估) |
| LLM App / MLE | 亮点4(LoRA) → 3(RAG) → 1(Multi-Agent) → 5(评估) → 6(CNN) |
| 全栈 AI | 亮点1 → 3 → 4 → 6 → 7（全覆盖，各取精华） |

用户"技术侧重"多选优先覆盖默认排序。

#### 3.2 生成规则

**格式**：项目标题行 + 技术栈标签 + 4-6 条 bullet points

**每条 bullet**：
1. 动词开头（设计/实现/构建/优化/集成）
2. 三段式：做了什么 → 用什么技术 → 达成什么效果
3. 夹带岗位匹配关键词
4. 量化收尾（数据量/性能/规模）

**技术栈标签**：从项目实际使用中选取，按岗位权重排列：
- 核心：Spring AI Alibaba · Multi-Agent · Supervisor Pattern · Milvus · LoRA · vLLM
- 检索：Hybrid Search · BM25 · RRF · Jina Rerank · BGE Embedding · Auto-Merge
- 推理：Qwen3-0.6B · PyTorch · CNN · FastAPI · LLM-as-Judge
- 工程：FRP 内网穿透 · SSE · Reactor Flux · Spring Boot 3.5

### Phase 4: 输出

```
**[项目名称]** | 个人项目 | [时间段]
[技术栈标签行]

• [Bullet 1] — 核心架构
• [Bullet 2] — 关键技术实现
• [Bullet 3] — 工程亮点
• [Bullet 4] — 效果/评估
• [Bullet 5-6] — 可选扩展

迭代方向：[1-2 句已规划但未完成的改进]
```

约束：
- 每条 bullet 不超过 2 行（约 80 字）
- 不声称未实现的功能（CLIP、FAISS、K8s 等项目未用技术）
- "迭代方向"可写已规划的改进（如 RAG 作为微调补充），体现技术判断力
- 量化须基于项目实际能力，标注"建议值"需用户确认

### Phase 5: 迭代

1. 展示初稿，询问反馈
2. 根据反馈调整侧重、措辞、篇幅
3. 主动提供面试追问预测（3-5 条）
4. 如需英文版，提供翻译

## 放大策略

| 维度 | 允许 | 禁止 |
|------|------|------|
| 架构叙事 | 强调设计决策与技术判断力 | 编造未实现的功能 |
| 量化指标 | 添加合理效果数据 | 脱离项目能力的夸大 |
| 角色 | "设计并实现" | 虚构团队规模 |
| 规模 | 声称支撑 XX 文档/请求 | 不合理的数量级 |

## 反模式检查

输出前检查不包含：泛化描述、工具堆砌无解释、缺失量化、被动语态（参与/协助）、面试无法自圆其说的声称。
