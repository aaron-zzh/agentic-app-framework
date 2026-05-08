---
level: Practice
layer: Product
purpose: NexusKB 连接式知识引擎设计——Cognition.Knowledge 的通用执行能力
status: draft
version: 2.0.0
date: 2026-05-08
author: AaronZZH
changelog:
  - 2026-05-08 v2.0.0 | 重新定位为引擎层实现，从 core/ 迁移到 engine/
  - 2026-05-06 v1.0.0 | 初稿占位（原 core/nexusKB.md）
---

# NexusKB 连接式知识引擎

> **引擎层（Layer 2）的通用执行能力**，支撑 Cognition.Knowledge 模块。
> 引擎只关心"怎么存/怎么索引/怎么检索"，不感知双视图/价值观/分区等业务语义。
> 认知层接入见：[Cognition 设计 - Knowledge 模块](../core/cognition/Readme.md#组件二knowledge知识库)

## 定位

| 层 | 角色 |
|----|------|
| **Cognition.Knowledge 模块** | 业务语义接口（双视图/分区/权限/价值观） |
| **NexusKBEngine（本文档）** | 通用执行能力（向量+图谱混合存储/检索） |

## 连接式设计

**"Nexus" 命名含义**：知识以**实体和关系**为一等公民，强调"连接"而非孤立的文档块。知识不是孤岛，每个实体通过关系与其他实体关联，形成可推理的网络。

### 知识三元组

```
KnowledgeNode {
    id              # 实体标识
    type            # 实体类型（概念/人物/事件/规则...）
    content         # 实体内容
    embedding       # 向量表示
    source          # 来源文档引用
    confidence      # 置信度
    metadata        # 扩展元数据
}

KnowledgeRelation {
    source          # 起点实体
    relation        # 关系类型（is-a/part-of/caused-by/...）
    target          # 终点实体
    weight          # 关系强度
    evidence        # 证据（来源文档片段）
}
```

## 核心能力

| 能力 | 说明 |
|------|------|
| ECL 管道 | Extract（解析分块）→ Cognify（向量化+实体抽取）→ Load（入库） |
| 向量检索 | PgVector 语义相似度搜索 |
| 图谱检索 | Neo4j Cypher 多跳关系查询 |
| 混合检索 | 向量 + 图谱 + 关键词联合，Reranker 重排 |
| 增量更新 | 新文档入库时自动补充实体/关系，避免重建 |
| 冲突处理 | 同实体多源冲突时按置信度/权威度仲裁 |
| 版本管理 | 知识快照，支持"当时知识状态"回溯 |

## 对外接口

```java
public interface NexusKBEngine {
    // ECL 管道
    IngestResult ingest(IngestRequest request);

    // 检索
    List<KnowledgeNode> searchByVector(float[] queryVec, int topK);
    List<KnowledgeNode> searchByKeyword(String keyword);
    Graph searchByGraph(String entityId, int hops);
    List<KnowledgeNode> searchHybrid(HybridQuery query);

    // 图谱操作
    void addNode(KnowledgeNode node);
    void addRelation(KnowledgeRelation relation);
    Graph explore(String entityId, ExploreStrategy strategy);

    // 维护
    void update(String nodeId, UpdateStrategy strategy);
    void merge(List<String> nodeIds);  // 同义合并
    void delete(List<String> nodeIds);
}
```

## 存储实现

| 数据 | 存储后端 | 选型原因 |
|------|---------|---------|
| 原始文档 | PostgreSQL + 对象存储 | 文档元数据 + 大文件分离 |
| 向量切片 | PgVector | 文档分块 + Embedding |
| 实体关系 | Neo4j | 图查询天然支持多跳推理 |
| 全文索引 | PostgreSQL FTS（中文分词） | 精确关键词匹配 |
| 热缓存 | Redis | 高频查询缓存 |

## ECL 管道详解

```
原始文档
    ↓ Extract（解析分块）
    │   - Tika 多格式解析（PDF/Word/Markdown/HTML）
    │   - 语义分块（按标题/段落/语义边界）
    │   - 元数据抽取（标题/作者/时间/标签）
    ↓
Chunks（切片）
    ↓ Cognify（向量化+实体抽取）← 调用 SemanticCalcEngine
    │   - Embedding 生成（文本 → 向量）
    │   - 实体抽取（NER）
    │   - 关系抽取（实体三元组）
    │   - 语义分类
    ↓
KnowledgeNodes + KnowledgeRelations
    ↓ Load（入库）
    │   - 向量入 PgVector
    │   - 图谱入 Neo4j
    │   - 元数据入 PostgreSQL
    │   - 冲突检测与合并（同义实体）
    ↓
可检索的知识网络
```

## 检索策略

| 策略 | 场景 | 实现 |
|------|------|------|
| 纯向量 | "意思相近"的内容 | PgVector cosine/dot product |
| 纯关键词 | 精确匹配术语 | PostgreSQL FTS |
| 纯图谱 | 多跳关系探索 | Neo4j Cypher |
| 混合 | 复杂查询 | 多源并行 + Reranker 重排 |
| Bundle Search | 多跳推理聚合证据 | 借鉴 M-FLOW 图路由思想 |

## 与其他引擎的协作

| 引擎 | 协作方式 |
|------|---------|
| SemanticCalcEngine | Cognify 阶段的向量化/NER/关系抽取；检索时查询理解 |
| AtomMemoryEngine | 用户高价值记忆经价值观校验后提升为知识（Learning 反哺触发） |
| ValueRuleEngine | 入库前/检索后的价值观过滤（由 Knowledge 模块触发） |

## 与业界框架的对照

| 框架 | 借鉴点 | 差异 |
|------|--------|------|
| Cognee | ECL 管道 | 独立进程/Python vs AAF 内嵌/Java |
| LightRAG | 混合检索、Reranker | RAG 框架全栈 vs 纯引擎 |
| Graphiti | 动态知识图谱、时态 | Zep 云服务 vs AAF 自管 |

## 非目标

- 不处理业务语义（双视图/分区/权限）——那是 Cognition.Knowledge 模块的职责
- 不处理 Embedding/NER 本身——那是 SemanticCalcEngine 的职责
- 不提供用户界面——用户视图由 Layer 4 知识管理业务系统实现

## 相关文档

- [Cognition 认知层设计](../core/cognition/Readme.md)
- [SemanticCalcEngine 语义计算引擎](semantic-calc-engine.md)（待创建）
- [AtomMemory 原子记忆引擎](atom-memory-engine.md)
- [文档引擎](document-engine.md)
