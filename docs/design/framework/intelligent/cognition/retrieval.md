---
level: Practice
layer: Model
purpose: 混合检索——记忆+知识库统一检索门面
status: draft
version: 1.0.0
date: 2026-05-28
author: AaronZZH
---

# 混合检索功能设计

> 记忆+知识库统一检索门面，屏蔽多源检索差异，对外提供统一入口。

## 定位

混合检索（UnifiedRetrievalService）是 Cognition 层的独立组件，不是 MemoryPipeline 的内部步骤。它被 MemoryPipeline 编排调用，也可被搜索引擎、知识库管理 API 等场景直接调用。

```text
调用方：
  ├── MemoryPipeline（Agent 执行前上下文组装）
  ├── 搜索引擎（跨资源统一搜索）
  ├── 知识库管理 API（用户手动检索）
  └── RAG 生成服务（直接检索+生成）
         ↓
UnifiedRetrievalService（统一检索门面）
         ↓
  ├── AtomMemoryEngine（记忆检索：向量+时序）
  └── HybridSearchService（知识库检索：向量+BM25+图谱）
```

## 核心能力

- **多源并行检索**：记忆和知识库同时查询（虚拟线程并行）
- **双层 RRF 融合**：内层知识库三路融合 + 外层跨源融合
- **LLM 重排**：可选，按语义相关性精排 Top-K
- **Value 过滤**：出库前经过价值观校验，拦截敏感/违规内容
- **Agentic 增强**：查询理解时用 LLM 分析意图、改写查询、选择路由策略

## 双层架构

```text
UnifiedRetrievalService（外层：跨记忆/知识库路由与聚合）
    │
    ├── AtomMemoryEngine（记忆检索）
    │     向量检索（PgVector）+ 时序索引（PostgreSQL）
    │     记忆束检索（BundleSearchService，关联记忆聚合）
    │
    └── HybridSearchService（知识库检索，内层：三路融合）
          向量检索（SimilaritySearchService → PgVector cosine）
          BM25 全文检索（PostgreSQL ts_rank）
          图谱检索（GraphSearchService → Neo4j 多跳 Cypher）
          → 三路 RRF 融合排序
```

**双层 RRF**：内层 HybridSearchService 先对知识库三路结果融合，外层 UnifiedRetrievalService 再对记忆和知识库结果跨源融合，消除来源偏差。

## 三层递进策略

```text
精确匹配（关键词/实体）
    ↓ 未命中
全文检索（PostgreSQL FTS）
    ↓ 未命中或相关性低
语义检索（PgVector 向量相似度）
    ↓ 需要多跳关系
图谱推理（Neo4j 多跳 Cypher）

最终：RRF 融合多路结果，按综合相关性排序
```

## Agentic 检索（认知层增强）

引擎层（AtomMemoryEngine / HybridSearchService）是纯算法，不调用 LLM。Agentic 能力在 Cognition 层的 UnifiedRetrievalService 中：

| 步骤 | Agentic 能力 | 说明 |
|------|-------------|------|
| 查询理解 | LLM 分析意图、提取实体、时间范围 | 将模糊查询转为结构化检索条件 |
| 路由决策 | LLM 判断该查哪些源 | 超越静态 MemoryStrategy 配置 |
| 查询改写 | LLM 扩展/精化查询 | 提升召回率 |
| 重排序 | LLM 按语义相关性精排 | 提升精确率 |

## 输出

```text
RetrievalResult：
  - items[]（按相关性排序的检索结果）
  - source（来源标记：memory/knowledge/mixed）
  - citation（溯源信息：文档ID+段落）
  - score（融合后相关性分数）
```

被 MemoryPipeline 消费时，转换为 MemoryContext 注入 Prompt。被其他场景消费时，直接返回 RetrievalResult。

## 相关文档

- [技术方案 — 混合检索](retrieval-tech.md)
- [记忆管道](memory-pipeline.md)
- [AtomMemory 记忆引擎](../../engine/data-knowledge/atom-memory.md)
- [NexusKB 知识引擎](../../engine/data-knowledge/nexus-knowledge.md)
