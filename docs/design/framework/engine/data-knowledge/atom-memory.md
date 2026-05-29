---
level: Practice
layer: Product
purpose: AtomMemory 原子记忆引擎设计——Cognition.Memory 的通用执行能力
status: draft
version: 2.1.0
date: 2026-05-19
author: AaronZZH
changelog:
  - 2026-05-19 v2.1.0 | 更新参考框架对照表（整合 M-FLOW/Graphiti/ReMe）；新增 Agentic 边界说明
  - 2026-05-08 v2.0.0 | 重新定位为引擎层实现，从 core/ 迁移到 engine/
  - 2026-05-06 v1.0.0 | 初稿占位（原 core/atom-memory.md）
---

# AtomMemory 原子记忆引擎

> **引擎层（Layer 2）的通用执行能力**，支撑 Cognition.Memory 模块。
> 引擎只关心"怎么存/怎么索引/怎么检索"，不感知分区/权限/价值观等业务语义。
> 认知层接入见：[Cognition 设计 - Memory 模块](../intelligent/cognition.md#组件一memory记忆系统)

5层，潜意识、短期记忆、长期记忆、原则偏好兴趣性格、具体要求

## 定位

| 层 | 角色 |
|----|------|
| **Cognition.Memory 模块** | 业务语义接口（分区/权限/价值观/记忆类型） |
| **AtomMemoryEngine（本文档）** | 通用执行能力（原子记忆片段的存储/索引/检索） |

类比：`Memory` ≈ Spring Data Repository，`AtomMemoryEngine` ≈ Hibernate/JDBC。

## 原子化设计

**"Atom" 命名含义**：记忆以**不可再分的最小单元**（Memory Atom）存储，上层可按需组合成情景记忆、程序化记忆等复合结构。

### 原子记忆单元（Memory Atom）

```
MemoryAtom {
    id                 # 唯一标识
    content            # 原子内容（文本片段）
    embedding          # 向量表示
    event_time         # 事件发生时间
    valid_from         # 写入时间（生效）
    valid_to           # 失效时间（双时态）
    weight             # 价值权重（用于遗忘决策）
    tags               # 分类标签
    metadata           # 扩展元数据
}
```

**原子化的价值**：
- 细粒度存取：可独立更新/删除/归档任一原子
- 可组合：多原子组装成情景/程序化记忆
- 精确遗忘：按原子粒度评估价值，不丢失高价值细节
- 版本可控：原子级时态索引，支持"当时知道什么"回溯

## 核心能力

| 能力 | 说明 |
|------|------|
| 原子存储 | 单条原子的写入/读取/更新/删除 |
| 时序索引 | 按 event_time 和 valid_from 双维度索引 |
| 语义索引 | 基于向量的相似度索引（调用 SemanticCalcEngine） |
| 双时态检索 | "当时知道什么"（as-of）、"现在看当时"（as-at） |
| 混合检索 | 时序 + 语义 + 标签联合查询 |
| 权重衰减 | 基于访问频率和时间的自动权重衰减 |
| 压缩归档 | 低价值原子归档为摘要（保留原子链接） |
| 遗忘执行 | 按归档/删除策略执行（决策由上层做） |

## 对外接口

```java
public interface AtomMemoryEngine {
    // 存储
    void store(MemoryAtom atom);
    void storeBatch(List<MemoryAtom> atoms);

    // 检索
    List<MemoryAtom> searchByVector(float[] queryVec, int topK);
    List<MemoryAtom> searchByTime(TimeRange range, Filter filter);
    List<MemoryAtom> searchHybrid(HybridQuery query);

    // 双时态
    List<MemoryAtom> asOf(String key, Instant timestamp);

    // 生命周期
    void archive(List<String> atomIds, ArchiveStrategy strategy);
    void delete(List<String> atomIds);
    void updateWeight(String atomId, double weight);
}
```

## 存储实现

| 原子类型 | 存储后端 | 选型原因 |
|---------|---------|---------|
| 文本 + 向量 | PostgreSQL + PgVector | 关系 + 向量一体，事务一致 |
| 时序图 | Neo4j | 原子间关系图谱（因果、时序、关联） |
| 热缓存 | Redis | 高频访问的原子缓存 |

## 与其他引擎的协作

| 引擎 | 协作方式 |
|------|---------|
| SemanticCalcEngine | 提供 Embedding 生成、相似度计算、原子去重 |
| NexusKBEngine | 原子化记忆可进入 Knowledge（用户经验 → 领域知识） |
| ValueRuleEngine | 归档/删除前调用做合规校验（由 Memory 模块触发） |

## 与业界框架的对照

| 框架 | 借鉴点 | 差异 | AAF 吸收方式 |
|------|--------|------|-------------|
| **Mem0** | 记忆原子化、多级架构、LLM 价值判断 | 独立部署 vs AAF 内嵌 | MemoryAtom 原子化 + 三分区隔离 |
| **Graphiti** | 双时态模型（valid_at/invalid_at/expired_at）、Episodes 溯源、增量图构建 | Zep 云服务 vs AAF 自管 | MemoryAtom 双时态字段 + Neo4j 情景图 |
| **M-FLOW** | 倒锥形四层有向图、Bundle Search、程序化记忆、时间衰减 | Python 实现 vs Java | BundleSearchService + TimeDecayStrategy |
| **ReMe** | 程序化记忆蒸馏流水线（Trajectory→Extraction→Validation→Addition）、ReActAgent 摘要 | 完全本地 vs AAF 统一存储 | Learning 蒸馏通道 + ProceduralMemoryService |

### Agentic 边界（引擎层 vs 认知层）

本引擎（AtomMemoryEngine）是**纯算法层**，不调用 LLM。Agentic 能力由上层 Memory 模块在调用引擎前/后执行：

```
Agent 调用 MemoryService.record()
    ↓
Memory 模块（Agentic）：
    ├─ LLM 判断记忆价值（是否值得存）
    ├─ LLM 实体/关系抽取（结构化）
    ├─ LLM 去重判断（与已有记忆是否重复）
    └─ 构造 MemoryAtom
    ↓
AtomMemoryEngine.store()  ← 纯算法：写入 PgVector + Neo4j
```

```
Agent 调用 RetrievalService.retrieve()
    ↓
Retrieval 模块（Agentic）：
    ├─ LLM 查询意图理解 + 路由决策
    ↓
AtomMemoryEngine.searchHybrid()  ← 纯算法：向量+时序+标签检索
    ↓
Retrieval 模块（Agentic）：
    ├─ LLM 重排序（Reranker）
    └─ LLM 结果重写（组装连贯上下文）
```

## 非目标

- 不处理业务语义（分区/权限/价值观）——那是 Cognition.Memory 模块的职责
- 不处理意图识别、实体抽取——那是 SemanticCalcEngine 的职责
- 不处理跨 Agent 学习反哺——那是 Learning 横切通道的职责

## 相关文档

- [Cognition 认知层设计](../../intelligent/cognition/cognition.md)
- [SemanticCalcEngine 语义计算引擎](semantic-compute.md)（待创建）
- [NexusKB 连接式知识引擎](nexus-knowledge.md)

---

## 实现方案（参考 M-FLOW）

> 以下为 AAF-049 记忆系统实现的详细参考分析，供新会话开发使用。

### M-FLOW 核心架构参考

参考路径：`tmp/mem/m_flow/m_flow/`

#### 倒锥形四层有向图

```
Episode（对话片段）
  ↓ 句子级路由
Entity（实体节点）
  ↓ facet point 提取
Facet Point（细粒度事实）
  ↓ 关系建立
Edge（实体间关系边）
```

- **Episode**：一次对话/交互产生的原始记录，按句子分割后路由到对应实体
- **Entity**：从对话中抽取的实体（人/事/物/概念），带描述和 embedding
- **Facet Point**：实体的细粒度属性/事实（如"张三喜欢咖啡"），可独立更新
- **Edge**：实体间关系（如"张三→同事→李四"），带权重和时间戳

#### 关键源码文件映射

| M-FLOW 文件 | 功能 | AAF 对应实现 |
|-------------|------|-------------|
| `memory/episodic/write_episodic_memories.py` | 情景记忆写入主流程 | AtomMemoryEngine.store() |
| `memory/episodic/sentence_level_routing.py` | 句子级路由到实体 | 记忆路由服务 |
| `memory/episodic/facet_points_refiner.py` | facet point 提取与精炼 | LLM 驱动事实提取 |
| `memory/episodic/episode_router.py` | Episode 路由决策 | 记忆分类路由 |
| `memory/episodic/bundle_search.py` | Bundle Search 核心 | 记忆检索服务 |
| `memory/episodic/bundle_scorer.py` | Bundle 评分 | 检索结果排序 |
| `memory/episodic/adaptive_scoring.py` | 自适应评分 | 动态权重调整 |
| `memory/procedural/write_procedural_memories.py` | 程序化记忆蒸馏 | ProceduralMemoryService |
| `memory/procedural/procedure_router.py` | 程序化记忆路由 | 经验匹配 |
| `retrieval/memory_orchestrator.py` | 记忆检索编排 | MemoryRetrievalService |
| `retrieval/episodic_retriever.py` | 情景记忆检索 | 情景检索器 |
| `retrieval/procedural_retriever.py` | 程序化记忆检索 | 程序化检索器 |
| `retrieval/time/time_bonus.py` | 时间加权 | 时间衰减策略 |
| `storage/index_memory_nodes.py` | 记忆节点索引 | PgVector + Neo4j 索引 |
| `storage/add_memory_nodes.py` | 记忆节点写入 | 原子存储实现 |
| `core/models/MemoryNode.py` | 记忆节点模型 | MemoryAtom 实体 |
| `core/models/Edge.py` | 关系边模型 | MemoryRelation |

#### Bundle Search 算法（核心创新）

```
输入：查询文本 query
1. 向量检索 → 候选记忆原子集合
2. 对每个候选原子，沿图谱扩展 N 跳邻居 → 形成 Bundle
3. Bundle 评分 = Σ(原子相似度 × 关系权重 × 时间衰减)
4. 按 Bundle 整体分数排序，返回 Top-K Bundle
5. 每个 Bundle 组装为连贯的上下文片段
```

**与传统 Top-K 的区别**：不是找 K 个最相似的孤立片段，而是找 K 组"证据链"。

#### 时间衰减策略

```python
# M-FLOW: retrieval/time/time_bonus.py
time_score = base_score * decay_factor^(days_since_event)
# 精确时间匹配加分
if query_mentions_time and atom_event_time matches:
    time_score *= exact_match_bonus
```

#### 程序化记忆蒸馏

```
对话历史（episodic）
  ↓ LLM 提取"如何做"
程序化记忆（procedural）
  ├── 任务类型（task_type）
  ├── 步骤列表（steps）
  ├── 前置条件（preconditions）
  ├── 成功标准（success_criteria）
  └── 质量评分（quality_score，基于使用反馈）
```

### AAF 实现计划

#### 数据模型（Flyway 迁移）

```sql
-- 记忆原子表
CREATE TABLE memory_atom (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     BIGINT NOT NULL,
    scope       VARCHAR(20) NOT NULL, -- 'short_term'/'long_term'/'episodic'/'procedural'
    content     TEXT NOT NULL,
    embedding   vector(1536),
    event_time  TIMESTAMP NOT NULL,
    valid_from  TIMESTAMP NOT NULL DEFAULT NOW(),
    valid_to    TIMESTAMP, -- null = 当前有效
    weight      DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    access_count INT NOT NULL DEFAULT 0,
    last_accessed_at TIMESTAMP,
    tags        TEXT[], -- PostgreSQL 数组
    metadata    JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 记忆关系表（原子间关系）
CREATE TABLE memory_relation (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id   UUID NOT NULL REFERENCES memory_atom(id),
    target_id   UUID NOT NULL REFERENCES memory_atom(id),
    relation_type VARCHAR(50) NOT NULL,
    weight      DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 程序化记忆表
CREATE TABLE procedural_memory (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     BIGINT NOT NULL,
    task_type   VARCHAR(100) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    content     TEXT NOT NULL, -- Markdown 格式的 SOP
    embedding   vector(1536),
    use_count   INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    quality_score DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
```

#### Java 实现结构

```
aaf-framework/engine/memory/  ← AtomMemoryEngine 引擎层
  ├── MemoryAtom.java              -- @Entity 原子记忆
  ├── MemoryRelation.java          -- @Entity 原子间关系
  ├── MemoryAtomRepository.java    -- JPA Repository
  ├── AtomMemoryEngineImpl.java    -- 引擎核心实现
  ├── BundleSearchService.java     -- Bundle Search 算法
  ├── TimeDecayStrategy.java       -- 时间衰减策略
  └── MemoryCompressor.java        -- 低价值记忆压缩归档

aaf-framework/intelligent/cognition/memory/  ← 已有骨架，完善
  ├── ShortTermMemoryService.java  -- ✅ 已实现（Redis）
  ├── LongTermMemoryService.java   -- 改造：底层调用 AtomMemoryEngine
  ├── GraphMemoryService.java      -- 改造：底层调用 Neo4j 图谱
  ├── ProceduralMemoryService.java -- 改造：增加 LLM 蒸馏
  ├── MemoryRetrievalService.java  -- 改造：增加 Bundle Search
  ├── EpisodicMemoryService.java   -- 新增：情景记忆（句子级路由）
  └── MemoryDecayService.java      -- 新增：遗忘与衰减调度
```

#### 实现优先级（5 个任务）

| # | 任务 | 核心参考 | 产出 |
|---|------|---------|------|
| #4901 | 短期记忆增强 | M-FLOW session_cache | 已有 Redis 实现 + 滑动窗口优化 |
| #4902 | 长期记忆 + 原子引擎 | M-FLOW MemoryNode + index_memory_nodes | MemoryAtom 表 + AtomMemoryEngineImpl + 向量索引 |
| #4903 | 图谱记忆 + Bundle Search | M-FLOW bundle_search + bundle_scorer | Neo4j 情景图 + BundleSearchService |
| #4904 | 程序化记忆蒸馏 | M-FLOW write_procedural_memories + procedure_router | LLM 蒸馏 + 版本管理 + 质量反馈 |
| #4905 | 记忆检索编排 | M-FLOW memory_orchestrator + time_bonus | 多源融合 + 时间衰减 + 自适应评分 |

#### Bundle Search 实现要点

```java
public record MemoryBundle(
    List<MemoryAtom> atoms,      // 组成 bundle 的原子
    List<MemoryRelation> edges,  // 原子间关系
    double score,                // bundle 整体分数
    String summary               // 组装后的连贯文本
) {}

// 算法步骤：
// 1. 向量检索 Top-N 候选原子（N > K，如 N=50）
// 2. 对每个候选原子，通过 memory_relation 表扩展 2 跳邻居
// 3. 将候选原子 + 邻居组成 Bundle 候选集
// 4. Bundle 评分 = Σ(atom.similarity × edge.weight × timeDecay(atom.event_time))
// 5. 去重（Bundle 间重叠原子 > 50% 则合并）
// 6. 返回 Top-K Bundle
```

#### 时间衰减公式

```java
double timeDecay(Instant eventTime, Instant now) {
    long daysSince = Duration.between(eventTime, now).toDays();
    return Math.pow(0.95, daysSince); // 每天衰减 5%
}

// 精确时间匹配加分
double timeBonus(Instant eventTime, Instant queryTime) {
    if (queryTime != null && Math.abs(Duration.between(eventTime, queryTime).toHours()) < 24) {
        return 1.5; // 时间匹配加 50%
    }
    return 1.0;
}
```
