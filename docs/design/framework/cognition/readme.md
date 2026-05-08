---
level: Practice
layer: Model
purpose: AAF 认知层（Cognition）设计 - 记忆/知识/融合检索/学习优化四个独立组件
status: draft
version: 1.0.0
date: 2026-05-08
author: AaronZZH
---

# 认知层设计（Cognition Layer）

> AAF 五层智能架构中的 **Layer 1 Cognition**，跨 Agent 共享的持久认知底座。
> 参考：[架构设计思想 - 分层智能](../../../explanation/architecture-thought.md#分层智能渐进决策)

## 核心概念关系：一切皆文档 / 知识管理业务系统 / 知识库

AAF 是**规范驱动 + 一切皆文档**的 AI 原生框架，这三者不是三套系统，而是统一模型的三个层次：

```
┌────────────────────────────────────────────────────┐
│      一切皆文档（架构哲学 / 存储范式）               │ ← 顶层理念
│      所有制品（文档/工作流/组件/对话/规范/代码）       │
│      都以文档形式存入知识库                          │
└────────────────────────────────────────────────────┘
                      ↓ 指导落地
┌─────────────────┐          ┌─────────────────────┐
│ 知识管理业务系统  │          │  AI Agent           │
│ (Layer 4 业务层) │          │  (Layer 2/3)        │
│ 用户 CRUD 界面   │          │  检索/推理/生成      │
│ 版本/协作/分享   │          │                     │
└─────────────────┘          └─────────────────────┘
        ↓ 双视图访问同一份数据        ↓
┌────────────────────────────────────────────────────┐
│  知识库（Layer 1 Cognition 基础底座 ★）              │
│  文档内容 + 向量切片 + 图谱实体 + 元数据              │
│  ECL 管道自动维护双视图一致性                        │
└────────────────────────────────────────────────────┘
                      ↓ 基于
         PostgreSQL + PgVector + Neo4j + Redis + OSS
```

| 概念 | 层次 | 定位 |
|------|------|------|
| **一切皆文档** | 架构哲学 | 存储范式：所有制品都是文档 |
| **知识管理业务系统** | Layer 4 | 用户视图：CRUD / 版本 / 协作 |
| **知识库** | Layer 1 | **统一存储底座**：同时服务用户和 Agent |

**关键关系**：

1. **知识库是底座**：不是 Agent 专属的索引，而是所有结构化知识的统一存储层
2. **数据同源**：只有一份数据，不存在"文档系统数据"和"知识库数据"分离存储
3. **双视图暴露**：
   - 用户视图 = 知识管理业务系统（完整文档 + 版本 + 协作评论）
   - Agent 视图 = 检索 API（向量切片 + 图谱实体 + 混合检索）
4. **ECL 管道桥接**：用户编辑 → 触发事件 → 自动索引更新 → Agent 立即可检索
5. **递归性**：工作流 DSL、语义组件、Agent 配置、对话都是文档 → 都存入知识库 → Agent 可检索"系统自己的定义"

## 设计取向：自实现而非集成

AAF 已具备完整基础设施（PostgreSQL + PgVector + Neo4j + Redis），且 AAF 是 AI 原生框架，**认知能力是核心竞争力**，不能外挂于第三方。自实现的权衡：

| 维度 | 集成第三方（Mem0/Cognee/Graphiti 等） | AAF 自实现 |
|------|-----------------------------------|-----------|
| 技术栈一致性 | ❌ 多为 Python，跨语言通信 | ✅ 纯 Java |
| 基础设施复用 | ❌ 额外 Milvus/Qdrant/图库 | ✅ 复用现有 PgVector+Neo4j+Redis |
| 部署复杂度 | ❌ 多进程 + 额外运维 | ✅ 单 JVM |
| 与元引擎融合 | ❌ 外挂，难以深度定制 | ✅ 与 DSL + 语义组件深度耦合 |
| 长期可控性 | ❌ 受制于第三方演进 | ✅ 自主演化 |

**策略**：**借鉴精华不集成实现**。参考各框架的设计思想，基于 AAF 自身基础设施实现四个独立组件。

## 四个独立组件

```
┌──────────────────────────────────────────────────────┐
│                Layer 1 Cognition                     │
│                                                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
│  │   Memory    │  │  Knowledge  │  │  Retrieval  │  │
│  │   记忆系统   │  │   知识库     │  │  融合检索层  │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  │
│         ↑                ↑                ↑          │
│         └────────────────┼────────────────┘          │
│                          ↓                           │
│                  ┌─────────────┐                     │
│                  │  Learning   │                     │
│                  │ 学习优化层   │                     │
│                  └─────────────┘                     │
└──────────────────────────────────────────────────────┘
                          ↓
    基础设施：PostgreSQL + PgVector + Neo4j + Redis
```

| 组件 | 定位 | 属性 | 可见性 |
|------|------|------|--------|
| **Memory（记忆）** | 个体经验的持久化 | 动态、时序 + 语义双索引 | 用户/Agent 私有 |
| **Knowledge（知识）** | 领域知识的静态沉淀 | 静态、向量 + 图谱 | 全局共享 |
| **Retrieval（融合检索）** | 跨源路由与结果聚合 | 无状态、策略驱动 | 对上层透明 |
| **Learning（学习优化）** | 反哺闭环、自进化 | 异步、规则 + 评估驱动 | 系统级 |

---

## 组件一：Memory（记忆系统）

> 借鉴 **Mem0**（多级架构）+ **Graphiti**（双时态）+ **ReMe**（程序化记忆）

### 记忆分类

| 类型 | 存储 | 生命周期 | 用途 |
|------|------|---------|------|
| 短期记忆 | Redis（TTL） | 会话期 | 当前对话上下文 |
| 长期记忆 | PostgreSQL + PgVector | 永久 | 用户偏好、历史交互摘要 |
| 情景记忆 | Neo4j | 永久 | 事件序列、因果关系，支持时态回溯 |
| 情感记忆 | PostgreSQL（本地加密） | 永久 | 情绪偏好、高压场景交互偏好（隐私敏感，不外传） |
| 程序化记忆 | Markdown 文件 + PgVector 索引 | 永久 | "如何做"的经验（区别于事实记忆） |

### 关键设计

- **双时态模型**（借鉴 Graphiti）：事件时间（event_time）+ 写入时间（valid_from/valid_to），支持"当时知道什么"的回溯
- **多级架构**（借鉴 Mem0）：用户级 / 会话级 / Agent 级三层隔离
- **时序 + 语义双索引**：按时间窗口检索 + 语义相似度检索
- **记忆压缩与遗忘**：定期归档低相关性记忆为摘要，保留高频/高权重记忆

### 对外接口

```java
public interface MemoryService {
    void record(MemoryScope scope, Memory memory);             // 记录
    List<Memory> recall(MemoryScope scope, RecallQuery query); // 检索
    void forget(MemoryScope scope, ForgetStrategy strategy);   // 遗忘
    TimelineView timeline(MemoryScope scope, TimeRange range); // 时序回溯
}
```

---

## 组件二：Knowledge（知识库）

> **统一存储底座**：同时服务用户（知识管理业务系统）和 Agent（检索推理）。
> 借鉴 **Cognee**（ECL 管道）+ **LightRAG**（混合检索）+ **Graphiti**（知识图谱）

### 双视图服务

| 视图 | 面向 | 接口 | 数据形态 |
|------|------|------|---------|
| 用户视图 | 人类 | 知识管理业务系统（Layer 4） | 完整文档 + 版本 + 评论 + 协作 |
| Agent 视图 | AI | 检索 API | 向量切片 + 图谱实体 + 元数据 |

**两个视图访问同一份数据**，通过 ECL 管道保持一致性。

### 知识组织

| 层次 | 存储 | 内容 |
|------|------|------|
| 原始文档 | PostgreSQL + 对象存储 | 上传的原始文件 |
| 切片向量 | PgVector | 文档分块 + Embedding |
| 实体关系 | Neo4j | 从文档抽取的实体、关系、属性 |
| 知识分类 | PostgreSQL | 分类标签、知识等级、权限 |

### ECL 管道（Extract → Cognify → Load）

```
原始文档 → [解析+分块] → [Embedding+实体抽取] → [向量库+图谱]
          Tika            Spring AI + LLM        PgVector + Neo4j
```

- **Extract**：多格式解析（Tika/Markdown/PDF），分块策略可配
- **Cognify**：LLM 驱动的实体/关系抽取 + Embedding 生成
- **Load**：向量入 PgVector，图谱入 Neo4j，元数据入 PostgreSQL

### 检索模式

| 模式 | 实现 | 适用 |
|------|------|------|
| 语义检索 | PgVector 向量相似度 | "意思相近" |
| 关键词检索 | PostgreSQL FTS（中文分词） | 精确匹配 |
| 图谱检索 | Neo4j Cypher | 多跳关系查询 |
| 混合检索 | 向量 + 图谱 + 关键词联合 | 复杂查询 |

### 对外接口

```java
public interface KnowledgeService {
    KnowledgeDoc ingest(IngestRequest request);               // 导入
    List<KnowledgeChunk> search(SearchQuery query);          // 检索
    Graph explore(String entityId, int hops);                // 图谱探索
    void update(String docId, UpdateStrategy strategy);      // 增量更新
}
```

---

## 组件三：Retrieval（融合检索层）

> 借鉴 **M-FLOW** 图路由思想

### 职责

**不是又一个检索引擎，而是路由和聚合层**。根据 Agent 的请求语义，选择合适的数据源组合、并行检索、结果融合排序。

### 检索路由策略

| 场景 | 路由目标 | 聚合策略 |
|------|---------|---------|
| 事实性问答 | Knowledge（向量+图谱） | 按相似度 + 权威度 |
| 个人化回答 | Memory（长期+情景） + Knowledge | 优先用户记忆，知识库补充 |
| 时序相关 | Memory（情景，时态回溯） | 按时间窗口 |
| 多跳推理 | Knowledge（图谱） + Memory（情景） | 图路径 + Bundle Search |
| 如何做/经验 | Memory（程序化记忆） | 按任务类型 |

### 关键设计

- **策略驱动**：检索策略以 DSL 定义，元引擎可动态调整
- **并行检索**：用 `StructuredTaskScope` 并行查多源
- **重排序**：检索结果通过 Reranker 重新打分（借鉴 LightRAG）
- **缓存层**：相同查询结果缓存（Redis），降低重复检索成本

### 对外接口

```java
public interface RetrievalService {
    RetrievalResult retrieve(RetrievalRequest request);      // 统一入口
    RetrievalResult retrieve(String strategy, Object args);  // 指定策略
}
```

---

## 组件四：Learning（学习优化层）

> 借鉴 AAF 自进化原则 + Cognee 的知识生长闭环

### 职责

**认知系统的反哺与自进化**。执行结果不是一次性的输出，而是下一轮认知的输入。

### 反哺闭环

```
Agent 执行
    ↓
[执行结果 + 置信度 + 用户反馈]
    ↓
Learning 层：
    ├─ 效果评估（自动 + 人工）
    ├─ 模式识别（高频错误、重复任务）
    ├─ 语义漂移检测（工具行为 vs 知识描述）
    ├─ 规范更新建议（置信度达标自动，否则转人工）
    ↓
更新 Memory（经验写入程序化记忆）
更新 Knowledge（知识生长，实体关系补充）
更新 Tool（工具与知识绑定关系调整）
```

### 关键能力

| 能力 | 说明 |
|------|------|
| 执行结果归档 | Agent 调用工具/检索结果自动入程序化记忆 |
| 效果评估 | 基于用户反馈 + LLM 自评 + 指标比对 |
| 语义漂移检测 | 工具实际行为与知识库描述不一致时告警 |
| 知识生长 | 高置信度执行结果反哺 Knowledge（实体关系补充） |
| 规范自进化 | 识别改进模式 → 更新 DSL/规范 → 代码重生成（人工确认） |

### 与元引擎联动

Learning 层是**元引擎自进化机制的认知侧实现**。详见 [元引擎设计 - 自进化机制](../engine/meta-engine.md#自进化机制)。

---

## 与其他层的关系

| 层 | 关系 |
|----|------|
| Layer 0 Core | Cognition 为 Core 组装上下文（系统 Prompt + 检索结果 + 记忆） |
| Layer 2 Agent | Agent 执行前从 Cognition 拉取、执行后写回，Agent 自身无状态 |
| Layer 3 Assistant | Assistant 持有用户级 Memory 引用，会话上下文由 Cognition 组装 |
| Layer 4 Team | Team 共享 Knowledge，不共享 Memory（隐私隔离） |

## 实现路径

| 版本 | 内容 |
|------|------|
| v0.5 | Knowledge 基础（PgVector 语义检索 + Neo4j 图谱 + ECL 管道） |
| v0.6 | Memory 基础（短期/长期/情景三层 + 多级架构） |
| v0.7 | Retrieval 融合检索（路由策略 + 并行检索 + Reranker） |
| v0.8 | Learning 反哺闭环（执行结果归档 + 效果评估） |
| v0.9 | 整合联调（程序化记忆 + 语义漂移检测 + 规范自进化） |
| v1.0 | 时态回溯完善 + 知识生长闭环完善 + 情感记忆 |

## 参考框架借鉴表

| 框架 | 借鉴点 | 不借鉴点 |
|------|--------|---------|
| Mem0 | 多级记忆架构（用户/会话/Agent） | 独立部署、Python 依赖 |
| Graphiti | 双时态模型、动态知识图谱 | Zep 云服务依赖 |
| Cognee | ECL 管道、统一记忆层设计 | 独立进程、Python 实现 |
| LightRAG | 混合检索、Reranker | RAG 框架全栈 |
| M-FLOW | 图路由 Bundle Search 思想 | 具体实现 |
| ReMe | 程序化记忆（"如何做"）、Markdown 存储 | 完全本地化部署 |

## 所在模块

```
aaf-framework/
  └── intelligent/
      └── cognition/
          ├── memory/              # 记忆系统
          ├── knowledge/           # 知识库
          ├── retrieval/           # 融合检索
          └── learning/            # 学习优化
```
