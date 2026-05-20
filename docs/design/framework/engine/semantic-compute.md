---
level: Practice
layer: Product
purpose: SemanticCalc 语义计算引擎设计——横切支撑多个认知与业务组件的通用语义能力
status: draft
version: 1.0.0
date: 2026-05-08
author: AaronZZH
changelog:
  - 2026-05-08 v1.0.0 | 初稿占位
---

# SemanticCalc 语义计算引擎

> **引擎层（Layer 2）的通用执行能力**，提供跨组件复用的语义计算能力。
> 本文档为占位，详细设计待补充。
> 认知层接入见：[Cognition 设计 - 与引擎层的关系](../intelligent/cognition.md#八与引擎层的关系cognition-模块-vs-引擎实现)

## 定位

**"语义计算"**：一切涉及"从文本/数据中提取语义信息或基于语义做计算"的能力集合。这是 AAF 最底层、最横切的语义基础设施。

| 层 | 角色 |
|----|------|
| **SemanticCalcEngine（本文档）** | 通用语义计算能力 |
| **业务使用方** | Cognition（Memory/Knowledge/Retrieval）、Learning、Agent、Assistant、文档服务、元引擎 |

引擎只关心"怎么算"，不感知"为什么算"或"算完给谁用"。

## 为什么独立为引擎

语义计算是**跨组件复用的横切能力**，不属于某个具体认知模块：

- Memory 需要 Embedding、相似度、去重
- Knowledge 需要 NER、关系抽取、Embedding
- Retrieval 需要查询向量化、Reranker
- Learning 需要语义漂移检测、模式识别
- Agent 感知需要意图识别、实体抽取
- 文档服务需要自动分类、摘要

如果各自实现，会造成：Embedding 重复调用 LLM API、NER 逻辑散落、模型选择不统一、成本无法集中管控。独立成引擎后，上述问题由一个组件集中处理。

## 核心能力（占位清单）

| 能力 | 说明 | 主要使用方 |
|------|------|-----------|
| Embedding 生成 | 文本 → 向量（多模型路由：OpenAI/BGE/本地） | Memory 索引、Knowledge ECL、Retrieval 查询 |
| 语义相似度 | 向量间相似度计算（cosine/点积/欧氏） | Retrieval 排序、Memory 去重 |
| 实体抽取（NER） | 文本 → 实体列表（人物/时间/地点/概念/...） | Knowledge Cognify、Agent 感知 |
| 关系抽取 | 文本 → 实体关系三元组 | Knowledge 图谱构建 |
| 语义分类 | 文本 → 类别标签（零样本或小样本） | Memory 归档、Knowledge 分类、文档自动归类 |
| 语义聚类 | 向量集合 → 聚类结果 | 知识主题聚类、记忆归档 |
| 语义去重 | 相似片段合并/去重 | Memory 压缩、Knowledge 去重 |
| **语义漂移检测** | 工具实际行为与知识描述不一致时告警 | Learning 反哺 |
| 语义对齐 | 同义词/同义实体合并 | Knowledge 实体统一 |
| 意图识别 | 用户输入 → 意图类别 + 置信度 | Agent 感知、Assistant 路由 |
| 摘要生成 | 长文本 → 摘要（抽取式/生成式） | 文档服务、对话历史压缩 |
| Reranker 重排 | 候选集 → 按相关度重新排序 | Retrieval 结果重排 |

## 接口草案

```java
public interface SemanticCalcEngine {
    // Embedding
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);

    // 相似度
    double similarity(float[] a, float[] b, SimilarityMetric metric);

    // 实体/关系抽取
    List<Entity> extractEntities(String text);
    List<Triple> extractRelations(String text);

    // 分类与聚类
    List<Label> classify(String text, List<String> candidateLabels);
    List<Cluster> cluster(List<float[]> vectors, ClusterConfig config);

    // 语义运维
    DriftReport detectDrift(ToolBehavior observed, KnowledgeDescription expected);
    List<EntityMerge> alignEntities(List<Entity> entities);

    // 意图与摘要
    Intent recognizeIntent(String input, List<String> candidateIntents);
    String summarize(String text, SummarizeConfig config);

    // 重排
    List<Candidate> rerank(String query, List<Candidate> candidates);
}
```

## 实现策略（占位）

### 模型路由

统一通过 AgentScope Routing + Spring AI 抽象：

| 场景 | 优先选择 |
|------|---------|
| 高频低成本（Embedding） | 本地 BGE / 云服务最低档 |
| 高精度 NER / 关系抽取 | 强模型（GPT-4 类） |
| 低延迟意图识别 | 轻量模型或规则引擎兜底 |
| 批量任务 | 本地开源模型 |

### 缓存策略

- Embedding 缓存（同文本命中）
- 分类结果缓存（常见文本+候选标签）
- Reranker 结果缓存（短期）

### 成本控制

- Token 预算感知（失败快速降级）
- 批量调用优先（降低 API 调用次数）
- 本地模型优先（降低外部 API 依赖）

## 与其他引擎的协作

| 引擎 | 协作方式 |
|------|---------|
| AtomMemoryEngine | 为其提供 Embedding、相似度、去重 |
| NexusKBEngine | 为其提供 ECL Cognify 阶段的 NER/关系/向量化 |
| ValueRuleEngine | 提供意图识别，辅助 Value 校验场景判断 |

## 与业界框架的对照

| 框架 | 借鉴点 |
|------|--------|
| Spring AI | ChatClient / EmbeddingClient 统一抽象 |
| LangChain | Embedding 抽象、Reranker 设计 |
| Haystack | Pipeline 风格的语义计算链 |

## 非目标

- 不存储任何数据——存储由 AtomMemoryEngine / NexusKBEngine / PgVector / Redis 承担
- 不做业务路由/分区——那是 Cognition 各模块的职责
- 不直接对接 LLM API——通过 Spring AI / AgentScope 统一抽象

## 后续补全

- [ ] 各能力的详细接口契约
- [ ] 模型路由策略的具体规则
- [ ] 缓存一致性与失效策略
- [ ] 批量化调度与背压策略
- [ ] 性能基准与 SLA

## 相关文档

- [Cognition 认知层设计](../intelligent/cognition.md)
- [AtomMemory 原子记忆引擎](atom-memory.md)
- [NexusKB 连接式知识引擎](nexus-knowledge.md)
