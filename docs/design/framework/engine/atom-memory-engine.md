---
level: Practice
layer: Product
purpose: AtomMemory 原子记忆引擎设计——Cognition.Memory 的通用执行能力
status: draft
version: 2.0.0
date: 2026-05-08
author: AaronZZH
changelog:
  - 2026-05-08 v2.0.0 | 重新定位为引擎层实现，从 core/ 迁移到 engine/
  - 2026-05-06 v1.0.0 | 初稿占位（原 core/atom-memory.md）
---

# AtomMemory 原子记忆引擎

> **引擎层（Layer 2）的通用执行能力**，支撑 Cognition.Memory 模块。
> 引擎只关心"怎么存/怎么索引/怎么检索"，不感知分区/权限/价值观等业务语义。
> 认知层接入见：[Cognition 设计 - Memory 模块](../core/cognition/Readme.md#组件一memory记忆系统)

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

| 框架 | 借鉴点 | 差异 |
|------|--------|------|
| Mem0 | 记忆原子化思路 | 独立部署 vs AAF 内嵌 |
| Graphiti | 双时态模型 | Zep 云服务 vs AAF 自管 |
| ReMe | Markdown 存储、可读可编辑 | 完全本地 vs AAF 统一存储 |

## 非目标

- 不处理业务语义（分区/权限/价值观）——那是 Cognition.Memory 模块的职责
- 不处理意图识别、实体抽取——那是 SemanticCalcEngine 的职责
- 不处理跨 Agent 学习反哺——那是 Learning 横切通道的职责

## 相关文档

- [Cognition 认知层设计](../core/cognition/Readme.md)
- [SemanticCalcEngine 语义计算引擎](semantic-calc-engine.md)（待创建）
- [NexusKB 连接式知识引擎](nexus-kb-engine.md)
