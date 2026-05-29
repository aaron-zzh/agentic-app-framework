---
level: Practice
layer: Model
purpose: 记忆管道功能设计——读管道（可编排检索）+ 写管道（固定四步持久化）+ 混合检索 + AgentScope 整合
status: draft
version: 1.0.0
date: 2026-05-28
author: AaronZZH
---

# 记忆管道功能设计

> 记忆管道是 Cognition 层对外暴露的核心接口，负责上下文组装（读）和交互持久化（写）。

## 定位

记忆管道编排记忆引擎和知识库引擎，为 Agent/Assistant 提供统一的记忆读写能力。上层不直接操作引擎，只通过管道接口交互。

```text
Agent/Assistant
    ↓ 调用
MemoryPipeline（读管道 + 写管道）
    ↓ 编排
AtomMemoryEngine + NexusKBEngine + UnifiedRetrievalService
```

## 读管道（RetrievalPipeline）— 可编排

按 MemoryStrategy 从多源检索并组装上下文，步骤可配置。

### 处理流程

```text
输入（query、userId、sessionId、knowledgeBaseId）
    │
    ▼ 查询理解（意图分析、关键词提取）
    │
    ▼ 路由决策（按 MemoryStrategy 选择数据源）
    │
    ▼ 并行检索（虚拟线程，多源同时查询）
    │   ├── AtomMemoryEngine → 短期/长期/情景/程序化记忆
    │   └── HybridSearchService → 知识库（向量+BM25+图谱三路 RRF）
    │
    ▼ 跨源 RRF 融合（UnifiedRetrievalService）
    │
    ▼ LLM 重排（可选，调用 AI 基础设施 rerank 服务）
    │
    ▼ Value 过滤（价值观校验，过滤不合规内容）
    │
    ▼ 组装 MemoryContext（P0-P5 优先级，Token 超限从 P5 丢弃）
```

### MemoryStrategy 策略

| 策略 | 数据源 | 适用场景 |
|------|--------|---------|
| `HYBRID`（默认） | 短期记忆 + 长期记忆 + 知识库 | 通用助理 |
| `MEMORY_ONLY` | 短期记忆 + 长期记忆 | 个人助理 |
| `KNOWLEDGE_ONLY` | 知识库 | 客服/问答 |
| `PROCEDURAL_FIRST` | 程序化记忆优先 + 知识库 | 代码助理 |
| `FULL` | 全源（记忆+知识库+程序化+图谱） | 复杂推理 |

策略由 Assistant 层配置（每个 Assistant 绑定一个 MemoryStrategy），决定 Pipeline 使用哪些 Stage。

### 上下文组装优先级

| 优先级 | 内容 | 说明 |
|--------|------|------|
| P0（必选） | 系统 Prompt + 当前消息 | 不可压缩 |
| P1 | 工作记忆（当前任务焦点） | Agent 执行期临时状态 |
| P2 | 短期记忆（近期会话摘要） | 最近 N 轮压缩摘要 |
| P3 | 知识库检索结果 | 按相关度截取 Top-K |
| P4 | 用户画像摘要 | 长期偏好的压缩表示 |
| P5 | 情景记忆片段 | 相关历史场景 |

超出 Token 预算时从 P5 开始丢弃。

## 写管道（MemoryWritePipeline）— 固定流程

对话结束后将交互内容持久化。步骤固定不可跳过，保障数据一致性。

### 处理流程

```text
提取（MemoryExtractionService）
    ↓ LLM 抽取值得记忆的片段（实体/偏好/决策/情感）
去重（MemoryDeduplicationService）
    ↓ 语义相似度比对，合并/更新已有记忆，避免冗余
写入（AtomMemoryEngine）
    ↓ 原子化存储，双时态索引（事件时间 + 写入时间）
遗忘（TimeDecayStrategy，异步）
    低权重旧记忆降权，高价值记忆永久保留
```

### 设计决策

- **写管道固定而非可编排**：写入逻辑是数据一致性保障，随意重排步骤容易导致记忆污染、重复或丢失
- **唯一可配置点**：提取策略（提取什么），通过 MemoryStrategy 控制提取粒度，步骤顺序固定不变
- **不同 Assistant 的差异只在"写什么"，不在"怎么写"**

## 混合检索系统

混合检索分两层：

```text
UnifiedRetrievalService（外层：跨记忆/知识库路由与聚合）
    │
    ├── AtomMemoryEngine（记忆检索）
    │     向量检索（PgVector）+ 时序索引（PostgreSQL）
    │     记忆束检索（BundleSearchService）
    │
    └── HybridSearchService（知识库检索，内层：三路融合）
          向量检索（SimilaritySearchService → PgVector）
          BM25 全文检索（PostgreSQL ts_rank）
          图谱检索（GraphSearchService → Neo4j 子图扩展）
          → 三路 RRF 融合排序
```

**双层 RRF 融合**：内层 HybridSearchService 先对知识库三路结果融合，外层 UnifiedRetrievalService 再对记忆和知识库结果跨源融合，最后 LLM 重排。

## 一次对话请求中的管道调用

```text
用户发送消息
    ↓
Assistant（意图理解 + 情感感知）
    ↓
AgentDispatcher → Agent
    │
    ├─ 执行前：【读管道】
    │   查询理解 → 路由决策 → 并行检索 → RRF 融合 → 重排 → 组装 MemoryContext
    │   → 注入 Prompt（P0-P5 优先级）
    │
    ├─ LLM 推理 + 工具调用
    │
    └─ 执行后：【写管道】
        提取 → 去重 → 写入 → 遗忘（异步）
        工具调用结果 → KnowledgePipelineService 增量更新知识库
```

## AgentScope 整合

AAF 记忆管道通过实现 AgentScope LongTermMemory 接口接入，运行时自动触发：

```java
public class AafLongTermMemory implements LongTermMemory {

    private final MemoryWritePipeline writePipeline;
    private final RetrievalPipeline retrievalPipeline;

    @Override
    public Mono<Void> record(List<Msg> msgs) {
        // 走 AAF 写管道：提取 → 去重 → AtomMemory 写入
        return Mono.fromRunnable(() -> writePipeline.execute(...));
    }

    @Override
    public Mono<String> retrieve(Msg msg) {
        // 走 AAF 读管道：记忆 + 知识库混合检索
        return Mono.fromCallable(() -> retrievalPipeline.execute(...).toPromptSection());
    }
}
```

**AgentScope STATIC_CONTROL 模式自动触发时机：**

```text
推理前 → 自动调用 retrieve() → AAF 读管道 → 记忆+知识库检索 → 注入上下文
回复后 → 自动调用 record()   → AAF 写管道 → 提取→去重→写入→遗忘
```

无需在 DefaultAssistantExecutor 里手动触发记忆管道，AgentScope Hook 机制自动处理。

**上下文 Token 预算截断**：使用 `agentscope-extensions-autocontext-memory`（AutoContextMemory），自动按 P0-P5 优先级压缩。

## 接口定义

```text
MemoryPipeline（接口，定义在 intelligent/core/）
  execute(PipelineInput) → MemoryContext

PipelineInput：
  - query（用户输入）
  - userId / sessionId / knowledgeBaseId
  - strategy（MemoryStrategy 枚举）

MemoryContext：
  - sections[]（按优先级排列的上下文片段）
  - totalTokens（总 Token 数）
  - toPromptSection()（序列化为 Prompt 文本）
```

## 相关文档

- [技术方案 — 记忆管道](memory-pipeline-tech.md)
- [技术方案 — 混合检索](retrieval-tech.md)
- [AtomMemory 记忆引擎](../../engine/data-knowledge/atom-memory.md)
- [NexusKB 知识引擎](../../engine/data-knowledge/nexus-knowledge.md)
- [Cognition 层设计](cognition.md)
- [用户感知与个性化](personalization.md)
