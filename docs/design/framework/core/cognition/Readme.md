---
level: Practice
layer: Product
purpose: AAF 认知层（Cognition）设计 - Layer 1 持久级·跨 Agent 共享的认知基础
status: draft
version: 2.2.0
date: 2026-05-08
author: AaronZZH
changelog:
  - 2026-05-08 v2.2.0 | 补充与引擎层的关系章节；引入 SemanticCalcEngine 语义计算引擎；Cognition 模块 vs 引擎实现职责分工
  - 2026-05-08 v2.1.0 | 对齐认知心理学信息加工模型：加入工作记忆、注意力资源、双反馈通道
  - 2026-05-08 v2.0.0 | 加入价值观、明确三分区与被动循环、Learning 重新定位为横切反哺通道
  - 2026-05-08 v1.0.0 | 初稿（Memory/Knowledge/Retrieval/Learning 四组件）
---

# 认知层设计（Cognition Layer）

> AAF 五层智能架构中的 **Layer 1 Cognition**，跨 Agent 共享的持久认知底座。
> 参考：[智能体系统设计](../agent.md#layer-1-认知基础层-cognition) · [架构设计思想](../../../../explanation/architecture-thought.md)

## 一、架构定位

### 在五层智能架构中的位置

```
Layer 4  Team        协作层（项目级）
Layer 3  Assistant   助理层（会话级）
Layer 2  Agent       智能体层（任务级·无状态）
         ↓ 执行前拉取 / 执行后写回
Layer 1  Cognition   认知基础层（持久级·跨 Agent 共享）  ← 本文档
         ↑ 组装上下文
Layer 0  Core        内核层（请求级·无状态）
```

### 核心属性（架构约束）

| 属性 | 说明 |
|------|------|
| **持久级** | 数据持久存储，跨会话/跨 Agent 存在 |
| **共享底座** | 全体 Agent/Assistant/Team 的认知基础，不归属某个具体 Agent |
| **被动响应** | 认知循环是被动触发（上层调用 → 响应），**不主动执行任何循环** |
| **状态集中** | Agent/Team/Assistant 的数据级状态统一下沉到 Cognition |

## 二、认知循环（被动响应）

```
存储 / 检索 / 更新 / 遗忘（被动响应，不主动触发）
```

**关键约束：Cognition 不主动执行任何循环**。它只响应上层请求：
- Agent 执行前调用 `retrieve()` → Cognition 返回相关知识/记忆
- Agent 执行后调用 `store()` → Cognition 持久化结果
- 系统级反哺通道（Learning，见第五节）调用 `update()` → Cognition 更新内容

主动触发的学习、规划、推理都在上层（Agent/Assistant/Learning），Cognition 只是**认知内容的存储与检索层**。

### 与认知心理学信息加工模型对齐

AAF 认知系统概念上遵循经典信息加工模型（感知 → 工作记忆 → 决策 → 执行 → 反馈），在五层智能架构中的映射：

| 信息加工模型 | AAF 架构对应 | 所在层 |
|-------------|-------------|-------|
| 刺激输入 | 用户输入 / 任务触发 | Layer 3 Assistant / Layer 2 Agent |
| 短时感觉存储（前注意 <200ms） | Agent 感知预处理（快速过滤/规则匹配） | Layer 2 Agent |
| 感知 / 识别 | Agent 感知阶段（实体/意图识别） | Layer 2 Agent |
| **注意力资源** | Core 上下文窗口 + 上下文管理器 + Token 预算 | Layer 0 Core + Layer 2 引擎 |
| **工作记忆**（3-7 项，<30 秒） | **Agent 工作区**（任务级上下文焦点） | Layer 1 Cognition · Agent 工作区 |
| 长期记忆（无限容量） | Memory 长期记忆 + 情景 + 情感 + 程序化 | Layer 1 Cognition · 用户私有区 |
| 现有知识 | Knowledge（领域知识 + 规范文档） | Layer 1 Cognition · 全局共享区 |
| 决策与响应选择 | Agent 规划 + Value 价值观校验 + Core LLM 推理 | Layer 2 Agent + Layer 1 + Layer 0 |
| 响应执行 | Agent 执行阶段（工具调用 / 沙箱执行） | Layer 2 Agent |
| 反馈（即时） | Agent 循环内部 评估 ↔ 学习 ↔ 记忆 | Layer 2 Agent（自循环）|
| 反馈（异步） | Learning 横切反哺通道 | 跨层横切 |

**关键澄清**：
- **工作记忆 ≠ 短期记忆**。工作记忆是 Agent 当前任务的"注意焦点"（任务级、Agent 工作区），短期记忆是用户会话上下文（会话级、用户私有区）
- **注意力资源不由 Cognition 管理**。注意力的本质是"算力/Token 预算的有限分配"，这是 Core 和引擎层的职责，Cognition 只响应检索请求
- **双反馈通道**：Agent 内部即时反馈（任务循环自带）+ Learning 异步反哺（跨任务/跨 Agent 沉淀）

## 三、状态分区（三区隔离，架构约束）

Cognition 的数据按可见性分为三个严格隔离的分区：

| 分区 | 内容 | 访问权限 | 隐私级别 |
|------|------|---------|---------|
| **用户私有区** | 用户记忆、情感偏好、历史交互模式 | 仅该用户的 Assistant/Agent 可读写 | 🔒 最高（情感记忆本地加密） |
| **全局共享区** | 领域知识、规范文档、价值观 | 所有 Agent 可读（写入需授权） | 🌐 公开 |
| **Agent 工作区** | 任务执行中的临时数据、中间结果 | 仅当前 Agent 任务实例 | 🔐 任务级隔离 |

### 隔离策略（架构约束）

- **用户私有区**不可跨用户共享；Team 共享 Knowledge 但不共享 Memory
- **情感记忆永远本地存储**，不用于训练、不外传、不跨租户
- **全局共享区**写入需要价值观校验（防止污染）
- **Agent 工作区**任务结束后归档或清理，不污染长期存储

## 四、三个核心组件 + 一个服务组件

### 核心三件套：记忆 + 知识 + 价值观

按架构定义 **Cognition = Memory + Knowledge + Value**，这三个是平等的核心组件，不是谁包含谁：

```
┌──────────────────────────────────────────────────────────┐
│                   Layer 1 Cognition                      │
│                                                          │
│  ┌────────────┐  ┌────────────┐  ┌────────────────┐     │
│  │   Memory   │  │ Knowledge  │  │     Value      │     │
│  │   记忆系统  │  │  知识库     │  │   价值观系统    │     │
│  │  (个体经验) │  │ (领域知识)  │  │  (伦理约束)     │     │
│  └────────────┘  └────────────┘  └────────────────┘     │
│         ↑              ↑                ↑                │
│         └──────────────┼────────────────┘                │
│                        │                                 │
│                  ┌────────────┐                          │
│                  │ Retrieval  │  ← 服务层（跨三者的路由）  │
│                  │ 融合检索层  │                          │
│                  └────────────┘                          │
└──────────────────────────────────────────────────────────┘
              基础设施：PgVector + Neo4j + Redis + PostgreSQL

          ↑ 被动响应
          │
      上层请求（Agent/Assistant/Team）

      --- 以下为横切机制，不属于 Cognition 内部 ---
          │
          │ 反哺更新（异步触发）
          ↓
┌──────────────────────────────────────────────────────────┐
│  Learning（系统级反哺通道·横切机制）                       │
│  执行结果 → 评估 → 更新 Memory/Knowledge/Value            │
└──────────────────────────────────────────────────────────┘
```

| 组件 | 定位 | 属性 |
|------|------|------|
| **Memory** | 个体经验的持久化（动态） | 时序 + 语义双索引 |
| **Knowledge** | 领域知识的静态沉淀 | 向量 + 图谱混合 |
| **Value** | 伦理与价值约束（团队级，全局一致） | 规则 + 优先级 + 边界 |
| **Retrieval** | 跨三者的路由与聚合（服务层） | 无状态、策略驱动 |
| **Learning** | 跨 Agent 系统级反哺（横切机制） | 异步、不属 Cognition 内部循环 |

### 核心概念：一切皆文档 / 知识管理业务系统 / 知识库

三者不是三套系统，而是统一模型的三个层次：

```
┌────────────────────────────────────────────────┐
│   一切皆文档（架构哲学 / 存储范式）             │ ← 顶层理念
│   所有制品都以文档形式存入 Knowledge           │
└────────────────────────────────────────────────┘
                  ↓ 指导落地
┌─────────────────┐      ┌─────────────────┐
│ 知识管理业务系统  │      │  AI Agent       │
│ (Layer 4 业务层) │      │  (Layer 2/3)    │
│ 用户 CRUD 界面   │      │  检索/推理/生成  │
└─────────────────┘      └─────────────────┘
        ↓ 双视图访问同一份数据       ↓
┌────────────────────────────────────────────────┐
│  Knowledge（Layer 1 Cognition 组件）             │
│  文档内容 + 向量切片 + 图谱实体 + 元数据         │
│  ECL 管道自动维护双视图一致性                    │
└────────────────────────────────────────────────┘
```

**关键关系**：
1. **Knowledge 是统一存储底座**，同时服务用户（文档管理系统）和 Agent（检索）
2. **数据同源**，不存在文档系统和知识库分离存储
3. **双视图**：用户看到完整文档+版本+协作，Agent 看到向量切片+图谱实体
4. **ECL 管道桥接**：用户编辑 → 事件触发 → 索引更新 → Agent 可检索
5. **递归性**：工作流 DSL、语义组件、Agent 配置都是文档，Agent 可检索"系统自己的定义"

## 五、设计取向：自实现而非集成

AAF 已具备完整基础设施（PostgreSQL + PgVector + Neo4j + Redis），且认知能力是 **AI 原生框架的核心竞争力**，不能外挂于第三方：

| 维度 | 集成第三方 | AAF 自实现 |
|------|-----------|-----------|
| 技术栈一致性 | ❌ 多为 Python，跨语言通信 | ✅ 纯 Java |
| 基础设施复用 | ❌ 额外 Milvus/Qdrant | ✅ 复用 PgVector+Neo4j+Redis |
| 部署复杂度 | ❌ 多进程 + 额外运维 | ✅ 单 JVM |
| 与元引擎融合 | ❌ 外挂，难以定制 | ✅ 与 DSL + 语义组件深度耦合 |
| 长期可控性 | ❌ 受制于第三方演进 | ✅ 自主演化 |

**策略**：**借鉴精华不集成实现**。参考 Mem0/Graphiti/Cognee/LightRAG/ReMe/M-FLOW 的设计思想，基于 AAF 自身基础设施实现。

---

## 组件一：Memory（记忆系统）

> 借鉴 **Mem0**（多级架构）+ **Graphiti**（双时态）+ **ReMe**（程序化记忆）
> 详见 [atom-memory-engine.md](../../engine/atom-memory-engine.md)

### 记忆分类

| 类型 | 存储 | 分区 | 生命周期 | 容量 | 用途 |
|------|------|------|---------|------|------|
| **工作记忆** | 内存 / Agent 本地缓存 | **Agent 工作区** | 任务级（<任务时长） | 有限（类比 3-7 项） | 任务当前焦点、中间结果、规划上下文 |
| 短期记忆 | Redis（TTL） | 用户私有区 | 会话级 | 滑动窗口 | 当前对话上下文 |
| 长期记忆 | PostgreSQL + PgVector | 用户私有区 | 永久 | 无限 | 用户偏好、交互摘要 |
| 情景记忆 | Neo4j | 用户私有区 | 永久 | 无限 | 事件序列、因果关系 |
| 情感记忆 | PostgreSQL（**本地加密·不外传**） | 用户私有区 🔒 | 永久 | 无限 | 情绪偏好、交互节奏 |
| 程序化记忆 | Markdown + PgVector 索引 | 用户私有区 / Agent 工作区 | 永久 | 无限 | "如何做"的经验 |

**工作记忆 vs 短期记忆的区别**：

| 维度 | 工作记忆 | 短期记忆 |
|------|---------|---------|
| 所属分区 | Agent 工作区 | 用户私有区 |
| 生命周期 | 任务级（任务结束释放） | 会话级（TTL 滑动） |
| 容量 | 有限（类比人类 3-7 项） | 滑动窗口（按 Token/轮次） |
| 用途 | 任务当下正在用的信息焦点 | 对话历史的上下文 |
| 持久化 | 不持久化 | Redis TTL 持久化 |

### 关键设计

- **双时态模型**（借鉴 Graphiti）：事件时间 + 写入时间分离，支持"当时知道什么"的时序回溯
- **多级隔离**（借鉴 Mem0）：用户级 / 会话级 / Agent 级三层，严格隔离
- **时序 + 语义双索引**：按时间窗口检索 + 语义相似度检索
- **基于价值的遗忘**：低**价值**记忆（低频 + 低置信 + 低情感权重）归档为摘要，保留高价值记忆
- **情感记忆隐私边界（架构约束）**：本地加密存储，不训练模型，不跨用户，不外传

### 对外接口

```java
public interface MemoryService {
    void record(MemoryScope scope, Memory memory);             // 记录（被动响应）
    List<Memory> recall(MemoryScope scope, RecallQuery query); // 检索（被动响应）
    void forget(MemoryScope scope, ForgetStrategy strategy);   // 遗忘（被动响应）
    TimelineView timeline(MemoryScope scope, TimeRange range); // 时序回溯
}
```

---

## 组件二：Knowledge（知识库）

> **统一存储底座**：同时服务用户（知识管理业务系统）和 Agent（检索推理）。
> 借鉴 **Cognee**（ECL 管道）+ **LightRAG**（混合检索）+ **Graphiti**（知识图谱）
> 详见 [nexus-kb-engine.md](../../engine/nexus-kb-engine.md)

### 双视图服务

| 视图 | 面向 | 接口 | 数据形态 |
|------|------|------|---------|
| 用户视图 | 人类 | 知识管理业务系统（Layer 4） | 完整文档 + 版本 + 评论 + 协作 |
| Agent 视图 | AI | 检索 API | 向量切片 + 图谱实体 + 元数据 |

两个视图访问同一份数据，通过 ECL 管道保持一致性。

### 知识组织（均在全局共享区）

| 层次 | 存储 | 内容 |
|------|------|------|
| 原始文档 | PostgreSQL + 对象存储 | 上传的原始文件 |
| 切片向量 | PgVector | 文档分块 + Embedding |
| 实体关系 | Neo4j | 抽取的实体、关系、属性 |
| 知识分类 | PostgreSQL | 分类标签、等级、权限 |

### ECL 管道

```
原始文档 → [Extract 解析+分块] → [Cognify Embedding+实体抽取] → [Load 入库]
            Tika                    Spring AI + LLM              PgVector + Neo4j
```

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
    KnowledgeDoc ingest(IngestRequest request);
    List<KnowledgeChunk> search(SearchQuery query);
    Graph explore(String entityId, int hops);
    void update(String docId, UpdateStrategy strategy);
}
```

---

## 组件三：Value（价值观系统）

> 团队级伦理约束，全局一致，影响所有 Agent 的决策。

### 为什么独立成组件

- **架构定义**：按 agent.md，Cognition = 记忆 + 知识 + **价值观**
- **非知识**：价值观是**如何取舍**的规则，不是"是什么"的事实
- **非记忆**：价值观是全局共享的，不属于任何个体
- **强约束**：影响 Agent 决策优先级、工具调用边界、输出过滤

### 价值观内容

| 类别 | 内容 | 示例 |
|------|------|------|
| 伦理边界 | 不允许的行为 | 不利用情绪弱点、不模拟情感依赖、不伪造用户身份 |
| 优先级规则 | 冲突时如何取舍 | 用户隐私 > 便利性、安全 > 效率、事实 > 取悦 |
| 交互规范 | 人机边界 | 明确告知 AI 身份、重大决策必须人工确认 |
| 降级边界 | 服务不可用时的底线 | 不静默降级、不伪造检索结果、不跳过置信度门控 |
| 合规约束 | 法规与行业要求 | 数据本地化、敏感信息脱敏、审计日志完整 |

### 关键设计

- **全局一致**：价值观不分用户、不分租户（合规约束除外）
- **覆盖全栈**：Agent 的规划/执行、Assistant 的回应风格、Team 的仲裁都受其约束
- **置信度门控集成**：价值观校验失败 → 自动转人工
- **可演化但严控**：价值观变更需要人工审核，不走 Learning 自动通道

### 对外接口

```java
public interface ValueService {
    ValidationResult validate(Action action, Context context);   // 行为校验
    List<ValueRule> rulesFor(Scenario scenario);                 // 场景适用规则
    PriorityDecision resolve(Conflict conflict);                 // 冲突仲裁
}
```

---

## 组件四：Retrieval（融合检索层·服务组件）

> 借鉴 **M-FLOW** 图路由思想。**服务于核心三件套的统一访问入口**。

### 职责

**不是又一个检索引擎，是跨 Memory/Knowledge/Value 的路由和聚合层**。

### 检索路由策略

| 场景 | 路由目标 | 聚合策略 |
|------|---------|---------|
| 事实性问答 | Knowledge（向量+图谱） | 按相似度 + 权威度 |
| 个人化回答 | Memory（长期+情景） + Knowledge | 优先用户记忆，知识库补充 |
| 时序相关 | Memory（情景，时态回溯） | 按时间窗口 |
| 多跳推理 | Knowledge（图谱） + Memory（情景） | 图路径 + Bundle Search |
| 如何做/经验 | Memory（程序化记忆） | 按任务类型 |
| 决策辅助 | Memory + Knowledge + **Value**（伦理校验） | 结果 + 约束提示 |

### 关键设计

- **策略驱动**：检索策略以 DSL 定义，元引擎可动态调整
- **并行检索**：用 `StructuredTaskScope` 并行查多源
- **重排序**：检索结果通过 Reranker 重新打分（借鉴 LightRAG）
- **价值观校验**：所有检索结果出库前经过 Value 过滤（敏感/违规过滤）
- **缓存层**：相同查询结果缓存（Redis），降低重复检索成本

### 对外接口

```java
public interface RetrievalService {
    RetrievalResult retrieve(RetrievalRequest request);      // 统一入口
    RetrievalResult retrieve(String strategy, Object args);  // 指定策略
}
```

---

## 五、Learning（横切反哺通道·不属 Cognition 内部）

> **架构定位澄清**：Learning 不是 Cognition 的内部组件——Cognition 是**被动响应**的，不主动循环。Learning 是连接 Agent 执行结果与 Cognition 更新的**横切机制**，异步触发。

### 为什么独立于 Cognition

- **Cognition 的循环是被动的**（存储/检索/更新/遗忘，响应上层请求）
- **Learning 是主动的**（采集执行结果 → 评估 → 触发 Cognition 更新）
- **Agent 层自带"学习"阶段**（单任务内的学习 ↔ 记忆）
- **Learning 组件填补的是**：跨 Agent 的系统级反哺、规范演化、知识生长

### 反哺闭环

```
Agent 执行完成
    ↓（主动推送到 Learning）
[执行结果 + 置信度 + 用户反馈 + 工具调用链]
    ↓
Learning 处理（异步）：
    ├─ 效果评估（自动 + 人工）
    ├─ 模式识别（高频错误、重复任务）
    ├─ 语义漂移检测（工具行为 vs 知识描述）
    ├─ 价值观校验（更新建议是否违反价值观）
    └─ 规范更新建议
    ↓（调用 Cognition 的 update 接口）
Memory.update   → 经验写入程序化记忆
Knowledge.update → 知识生长（实体关系补充）
Value.propose   → 价值观更新建议（必须人工审核）
```

### 与元引擎自进化的关系

Learning 是**元引擎自进化机制的认知侧实现**。它把 Agent 的执行结果转化为 Cognition 的更新输入，但不直接修改代码/DSL——那是元引擎的职责。

详见 [meta-engine.md](../../meta-engine.md)

### 关键约束

- **异步触发**：不阻塞 Agent 执行链路
- **价值观校验不可绕过**：所有更新提议必须通过 Value 校验
- **高风险更新强制人工**：价值观变更、知识权威度变更必须人工确认
- **可追溯**：每次更新记录来源（哪个 Agent、哪次执行、什么反馈）

---

## 六、注意力资源与双反馈通道

### 注意力资源（不由 Cognition 管理）

信息加工模型中的**注意力资源**在 AAF 中本质是"有限算力/Token 预算的分配"，由以下组件共同承担，**Cognition 只被调用，不主动调度**：

| 组件 | 职责 | 所在层 |
|------|------|-------|
| Core 上下文窗口 | LLM 的 Token 容量上限 | Layer 0 Core |
| 上下文管理器 | 会话级 KV、跨会话引用、上下文压缩归档 | Layer 2 引擎 |
| Token 预算感知 | 成本估算 + 预算硬限制 | Layer 2 引擎·监控 |
| 置信度门控器 | 注意力切换（自动/等待/转人工） | Layer 2 引擎 |

### 双反馈通道

Agent 循环的反馈分为**即时**和**异步**两个通道，服务不同粒度：

```
┌─ 即时反馈（Agent 内部自循环，任务级） ─┐
│                                       │
│  感知 → 规划 → 执行 → 评估             │
│    ↑                      │            │
│    └── 学习 ↔ 工作记忆 ←──┘            │
│         （Agent 工作区）                │
│                                       │
└───────────────────────────────────────┘
             │ 任务完成
             ↓
┌─ 异步反哺（Learning 横切，跨任务/跨 Agent） ─┐
│                                             │
│  执行结果 → 评估 → 更新                     │
│     → Memory（用户私有区，经验沉淀）         │
│     → Knowledge（全局共享区，知识生长）      │
│     → Value（人工审核后更新）                │
│                                             │
└─────────────────────────────────────────────┘
```

| 通道 | 粒度 | 触发 | 更新目标 | 实现层 |
|------|------|------|---------|-------|
| 即时反馈 | 任务级 | Agent 循环自触发 | 工作记忆（Agent 工作区） | Layer 2 Agent |
| 异步反哺 | 跨任务/跨 Agent | 任务完成后推送 | Memory/Knowledge/Value | Learning 横切通道 |

## 七、与其他层的关系

| 层 | 关系 | 调用方向 |
|----|------|---------|
| Layer 0 Core | Cognition 为 Core 组装上下文（系统 Prompt + 检索结果 + 记忆）| Agent → Cognition → 组装 → Core |
| Layer 2 Agent | Agent 执行前调用 `retrieve()`，执行后调用 `store()` | Agent → Cognition（被动响应）|
| Layer 3 Assistant | Assistant 持有用户级 Memory 引用，会话上下文由 Cognition 组装 | Assistant → Cognition |
| Layer 4 Team | Team 共享 Knowledge + Value，**不共享 Memory**（隐私隔离） | Team → Cognition（Knowledge/Value）|
| 横切 Learning | 异步反哺通道，Agent 执行结果 → 评估 → 更新 Cognition | Agent → Learning → Cognition |

## 八、与引擎层的关系（Cognition 模块 vs 引擎实现）

Cognition 层的组件（Memory/Knowledge/Value/Retrieval）是**业务语义接口**，底层依赖**引擎层**（Layer 2 引擎）的通用执行能力。两层严格分工。

### 职责分工

| 层 | 角色 | 感知的信息 | 不感知的信息 |
|----|------|----------|-------------|
| Cognition 模块 | 业务语义接口 | 分区/权限/价值观/记忆类型/生命周期 | 存储技术/索引算法 |
| 引擎层 | 通用执行能力 | 存储技术/索引算法/性能优化 | 业务语义/分区语义/价值观 |

类比 Spring 生态：`Memory` 模块 ≈ Spring Data Repository，`AtomMemoryEngine` ≈ Hibernate/JDBC。

### 引擎清单（支撑 Cognition 的四个引擎）

| 引擎 | 支撑组件 | 通用能力 |
|------|---------|---------|
| **AtomMemoryEngine**（原子记忆引擎） | Memory | 原子记忆片段的存储/索引/检索，支持时序+语义双索引、双时态、压缩归档 |
| **NexusKBEngine**（连接式知识引擎） | Knowledge | 向量+图谱混合存储，ECL 管道、多跳推理、混合检索、Reranker |
| **SemanticCalcEngine**（语义计算引擎） | Memory/Knowledge/Retrieval/Learning | Embedding 生成、语义相似度、实体抽取、关系抽取、语义分类、语义漂移检测、语义对齐 |
| **ValueRuleEngine**（价值观规则引擎） | Value | 规则解析、优先级仲裁、行为校验、冲突判定 |

### SemanticCalcEngine（语义计算引擎）

**为什么独立为引擎**：语义计算是跨组件复用的横切能力，不属于某个具体认知组件。

| 能力 | 用途 | 谁使用 |
|------|------|-------|
| Embedding 生成 | 文本 → 向量 | Memory 索引、Knowledge ECL、Retrieval 查询 |
| 语义相似度 | 向量间相似度计算 | Retrieval 排序、Memory 去重 |
| 实体抽取（NER） | 文本 → 实体列表 | Knowledge Cognify 阶段、Agent 感知 |
| 关系抽取 | 文本 → 实体关系三元组 | Knowledge 图谱构建 |
| 语义分类/聚类 | 文本归类、主题聚类 | Memory 归档、Knowledge 分类 |
| **语义漂移检测** | 工具行为 vs 知识描述不一致告警 | Learning 反哺通道 |
| 语义对齐 | 去重、同义词合并 | Memory 压缩、Knowledge 去重 |
| 意图识别 | 用户输入 → 意图类别 | Agent 感知、Assistant 路由 |

### 调用链示例

**记忆写入**：

```
Agent.execute()
    ↓
MemoryService.record(scope, memory)      ← Cognition 业务语义
    ├─ 分区路由（用户私有/工作区）
    ├─ ValueService.validate(...)         ← 价值观校验
    └─ SemanticCalcEngine.embed(...)      ← 引擎：生成向量
         ↓
AtomMemoryEngine.store(...)               ← 引擎：写入存储
    ↓
PgVector / Neo4j / Redis                 ← 基础设施
```

**知识检索**：

```
Agent.execute()
    ↓
RetrievalService.retrieve(query)          ← Cognition 融合路由
    ├─ SemanticCalcEngine.embed(query)    ← 引擎：查询向量化
    ├─ NexusKBEngine.hybridSearch(...)    ← 引擎：向量+图谱
    ├─ AtomMemoryEngine.recall(...)       ← 引擎：记忆检索
    └─ SemanticCalcEngine.rerank(...)     ← 引擎：重排序
         ↓
ValueService.filter(...)                  ← 价值观过滤
    ↓
返回聚合结果给 Agent
```

### 依赖方向（架构约束）

```
Cognition 模块（Memory/Knowledge/Value/Retrieval）
    ↓ 允许调用
引擎层（AtomMemoryEngine / NexusKBEngine / SemanticCalcEngine / ValueRuleEngine / ...）
    ↓ 允许调用
基础设施层（PgVector / Neo4j / Redis / LLM API）

❌ 引擎层不能依赖 Cognition 模块
❌ 下层不能依赖上层
```

### 引擎的多场景复用

引擎是通用能力，**不绑定单个认知组件**，可被多处使用：

```
SemanticCalcEngine
    ├── 被 Cognition.Memory 使用 → 记忆向量化、去重
    ├── 被 Cognition.Knowledge 使用 → ECL Cognify
    ├── 被 Cognition.Retrieval 使用 → 查询理解、重排
    ├── 被 Learning 使用 → 语义漂移检测
    ├── 被 Agent 感知使用 → 意图识别、实体抽取
    └── 被文档服务使用 → 文档自动分类
```

这正是引擎层用 AtomMemory/Nexus 这种**特色命名**的原因——是通用能力，不绑定特定认知组件；而 Cognition 的 Memory/Knowledge 使用**通用名**，更直观。

## 九、实现路径

| 版本 | 内容 |
|------|------|
| v0.5 | Knowledge 基础（PgVector + Neo4j + ECL 管道 + 双视图暴露） |
| v0.6 | Memory 基础（短期/长期/情景 + 三分区隔离）+ Value 最小集（伦理边界 + 降级规则）|
| v0.7 | Retrieval 融合检索（路由策略 + 并行检索 + Reranker + Value 校验）|
| v0.8 | Learning 反哺通道（执行结果归档 + 效果评估 + 价值观校验）|
| v0.9 | 整合联调（程序化记忆 + 语义漂移检测 + 规范自进化提议）|
| v1.0 | 时态回溯完善 + 知识生长闭环 + 情感记忆 + 价值观演化治理 |

## 十、参考框架借鉴表

| 框架 | 借鉴点 | 不借鉴点 |
|------|--------|---------|
| Mem0 | 多级记忆架构（用户/会话/Agent） | 独立部署、Python 依赖 |
| Graphiti | 双时态模型、动态知识图谱 | Zep 云服务依赖 |
| Cognee | ECL 管道、统一记忆层设计 | 独立进程、Python 实现 |
| LightRAG | 混合检索、Reranker | RAG 框架全栈 |
| M-FLOW | 图路由 Bundle Search 思想 | 具体实现 |
| ReMe | 程序化记忆（"如何做"）、Markdown 存储 | 完全本地化部署 |

## 十一、所在模块

### Cognition 业务语义层（认知模块）

```
aaf-framework/intelligent/cognition/
  ├── memory/          # 记忆业务接口与分区策略
  ├── knowledge/       # 知识库业务接口与双视图
  ├── value/           # 价值观规则与校验接口
  └── retrieval/       # 融合检索路由（服务组件）

aaf-framework/intelligent/learning/
  └── (横切反哺通道，不属 cognition 包)
```

### 引擎层实现（通用能力）

```
aaf-framework/engine/
  ├── atom-memory/     # 原子记忆引擎（支撑 Memory）
  ├── nexus-kb/        # 连接式知识引擎（支撑 Knowledge）
  ├── semantic-calc/   # 语义计算引擎（Embedding / NER / 相似度 / 漂移检测）
  └── value-rule/      # 价值观规则引擎（支撑 Value）
```

## 十二、相关文档

- [agent.md](../agent.md) - 五层智能架构总览
- [记忆系统详细设计](../../engine/atom-memory-engine.md) - 待创建
- [知识库详细设计](../../engine/nexus-kb-engine.md) - 待创建
- [融合检索详细设计](retrieval.md) - 待创建
- [元引擎自进化机制](../../meta-engine.md#自进化机制)
