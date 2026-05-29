---
level: Theory
layer: Paradigm
purpose: 用户感知与个性化——记忆系统、知识库、上下文管理、状态管理协同设计
status: draft
version: 0.2.0
date: 2026-05-20
author: AaronZZH
gains:
  - 理解记忆系统、知识库、上下文、状态管理如何协同实现用户感知与个性化
  - 掌握五层智能架构中记忆/知识的调用关系流程
  - 了解 AgentScope 整合方案
---

# 用户感知与个性化

> 本文档是串联视角，聚焦"如何让系统感知用户、记住用户、服务用户"。
> 各组件详细设计见：[Cognition](cognition.md) · [AtomMemory](../../engine/data-knowledge/atom-memory.md) · [NexusKB](../../engine/data-knowledge/nexus-knowledge.md) · [状态管理器](../../engine/meta/state-manager.md)

## 四层数据的职责分工

```text
┌─────────────────────────────────────────────────────────┐
│  上下文（Context）                                        │
│  请求级·临时·LLM 窗口内                                   │
│  当前对话消息 + 从记忆/知识检索到的相关片段                 │
│  由上下文管理器组装，Token 预算控制，超限压缩归档            │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  状态（State）                                            │
│  会话级/工作区级·持久·跨请求                               │
│  会话状态：当前任务列表、暂存结果、在线用户                  │
│  工作区状态：文档/代码/工作流（OT/CRDT 合并）               │
│  由状态管理器维护，渐进提交，Redis + PostgreSQL 双层存储     │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  记忆（Memory）                                           │
│  持久级·跨会话·用户私有                                    │
│  短期：会话摘要（近期对话压缩）                             │
│  长期：用户偏好、习惯、重要事件                             │
│  情景：具体交互场景的完整记录                               │
│  情感：情绪偏好、高压场景交互模式                           │
│  程序化：可复用的操作经验/技能                              │
│  决策日志：AI 自主决策记录，支持异步审查                    │
│  由记忆管道处理，AtomMemory 引擎存储（PG + Redis + Neo4j） │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  知识库（Knowledge）                                      │
│  持久级·全局共享·领域知识                                  │
│  结构化文档：Markdown/PDF/Word 等，向量化后存入 PgVector    │
│  知识图谱：实体关系，存入 Neo4j                             │
│  RAG 检索：混合检索（向量 + 图谱 + 关键词）+ LLM 重排       │
│  由 NexusKB 引擎管理，KnowledgePipelineService 入库        │
└─────────────────────────────────────────────────────────┘
```

**记忆 vs 知识库的核心区别：**

| | 记忆 | 知识库 |
|---|---|---|
| 归属 | 用户私有（隔离） | 全局共享（或按空间隔离） |
| 内容 | 交互产生的个人经历 | 人工整理的领域知识 |
| 更新 | 每次对话后自动写入 | 人工上传/管理员维护 |
| 检索 | 时序+语义双索引 | 向量+图谱混合检索 |

## 五层智能架构中的调用关系

```text
Team（项目级）
  └── 读取工作区状态（多 Assistant 共享进度）
  └── 不直接访问记忆/知识库

Assistant（会话级）
  ├── 读取用户画像 ← Cognition 长期记忆 + 情感记忆
  │     → 调整回应风格、信息密度
  ├── 读取会话状态 ← 状态管理器
  │     → 当前任务列表、暂存结果
  └── 派发任务给 Agent

Agent（任务级·无状态）
  ├── 执行前：从 Cognition 拉取上下文
  │     ├── 短期记忆（近期会话摘要）
  │     ├── 长期记忆（用户偏好/历史）
  │     ├── 程序化记忆（可复用经验）
  │     └── 知识库检索（相关领域知识）
  ├── 执行中：工作记忆（任务焦点，执行期临时）
  ├── LLM 推理 + 工具调用
  └── 执行后：写回 Cognition
        ├── 记忆写管道（提取→去重→写入→遗忘）
        └── 工具调用结果 → 知识库增量更新

Cognition（横向共享底座）
  ├── 统一检索入口（UnifiedRetrievalService）
  │     ├── 记忆检索 → AtomMemoryEngine（PG + Redis）
  │     └── 知识检索 → HybridSearchService（PgVector + Neo4j）
  ├── 记忆管道（读：RetrievalPipeline / 写：MemoryWritePipeline）
  └── 不主动触发，被动响应上层调用

Core（LLM 基础设施）
  └── 不直接访问记忆/知识库，通过 Cognition 获取上下文
```

## 一次对话请求的完整数据流

```text
用户发送消息
    │
    ▼
Assistant（意图理解 + 情感感知）
    ├─ 读取会话状态 ← 状态管理器（Redis）
    └─ 读取用户画像 ← 用户画像服务（长期记忆 + 情感记忆聚合）
    │
    ▼
AgentDispatcher → Agent（ReActAgent）
    │
    ├─ 执行前：【读管道 RetrievalPipeline】按 MemoryStrategy 检索并组装上下文
    │   │
    │   ├── 查询理解 → 路由决策（按策略选数据源）
    │   ├── 并行检索（虚拟线程）：
    │   │     AtomMemoryEngine → 短期/长期/情景/程序化记忆（P2/P4/P5）
    │   │     HybridSearchService → 知识库（P3，向量+BM25+图谱三路 RRF）
    │   ├── 跨源 RRF 融合 + LLM 重排（UnifiedRetrievalService）
    │   └── 组装 MemoryContext（P0-P5 优先级，Token 超限从 P5 丢弃）
    │
    ├─ LLM 推理（Core，ReActAgent 认知循环）
    │
    ├─ 工具调用（编排服务路由）
    │   ├── 技能引擎（SkillMatchEngine）→ 匹配内置/自定义技能
    │   ├── 工具引擎（ToolCallDispatcher）→ MCP 工具 / 脚本沙箱
    │   └── 知识库工具（KnowledgeRetrievalTools）→ 按需追加检索
    │
    └─ 执行后：【写管道 MemoryWritePipeline】固定四步，不可跳过
          提取（MemoryExtractionService，LLM 抽取值得记忆的片段）
              ↓
          去重（MemoryDeduplicationService，语义相似度比对）
              ↓
          写入（AtomMemoryEngine，PG + Redis 双写，双时态索引）
              ↓
          遗忘（TimeDecayStrategy，异步，低权重降权）

          工具调用结果 → KnowledgePipelineService 增量更新知识库
          用户行为信号 → 用户画像服务（异步更新画像）
```

## 混合检索系统

混合检索分两层，已实现：

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

两层 RRF 融合：内层 HybridSearchService 先对知识库三路结果融合，外层 UnifiedRetrievalService 再对记忆和知识库结果跨源融合，最后 LLM 重排。

## 用户画像系统

当前用户画像散落在长期记忆中，缺少独立的画像服务和定期更新机制。规划设计如下：

```text
用户画像服务（UserProfileService）
    │
    ├── 画像存储（PostgreSQL，结构化）
    │     偏好维度：回应风格、信息密度、领域专业度
    │     行为维度：常用功能、活跃时段、交互模式
    │     情感维度：情绪偏好、高压场景模式（来自情感记忆）
    │
    ├── 画像更新（两种触发方式）
    │     实时更新：每次对话结束后，写管道触发异步更新
    │     定期更新：内置"用户理解技能"（UserUnderstandingSkill）
    │                 定期扫描近期记忆 → LLM 提炼画像变化 → 更新画像
    │
    └── 画像消费
          Assistant 会话开始时读取 → 注入 System Prompt
          上下文组装时作为 P4（用户画像摘要）
```

**内置"用户理解技能"（待实现）**：

```java
// 注册为 AgentScope AgentSkill，定期触发
@AgentSkill(name = "user_understanding", description = "定期分析用户行为，更新用户画像")
public class UserUnderstandingSkill {

    // 定时任务触发（每日/每周）
    // 1. 从 AtomMemoryEngine 拉取近期记忆
    // 2. LLM 提炼：偏好变化、新兴趣、情绪模式
    // 3. 更新 UserProfileService
}
```

> 待实现：`UserProfileService`、`UserUnderstandingSkill`，当前用户画像通过 `UnifiedRetrievalService` 检索长期记忆临时替代。

## 记忆管道（Memory Pipeline）

记忆管道分为**读管道**和**写管道**两种，职责不同，设计原则不同。

### 读管道（RetrievalPipeline）— 可编排

按 `MemoryStrategy` 从多源检索并组装上下文：

| 策略 | 数据源 | 适用场景 |
|------|--------|---------|
| `HYBRID`（默认） | 短期记忆 + 长期记忆 + 知识库 | 通用助理 |
| `MEMORY_ONLY` | 短期记忆 + 长期记忆 | 个人助理 |
| `KNOWLEDGE_ONLY` | 知识库 | 客服/问答 |
| `PROCEDURAL_FIRST` | 程序化记忆优先 + 知识库 | 代码助理 |
| `FULL` | 全源（记忆+知识库+程序化+图谱） | 复杂推理 |

### 写管道（MemoryWritePipeline）— 固定流程

步骤固定不可跳过，保障数据一致性：

```text
提取（MemoryExtractionService）→ 去重（MemoryDeduplicationService）
    → 写入（AtomMemoryEngine）→ 遗忘（TimeDecayStrategy，异步）
```

> 设计决策：写管道固定而非可编排。写入逻辑是数据一致性保障，随意重排步骤容易导致记忆污染。唯一可配置点是提取策略（提取什么），不影响步骤顺序。

## AgentScope 整合方案

AAF 记忆系统和知识库通过实现 AgentScope 接口接入，AgentScope 运行时自动管理触发时机：

```java
// 实现 AgentScope LongTermMemory 接口，对接 AAF 记忆+知识库
public class AafLongTermMemory implements LongTermMemory {

    private final MemoryWritePipeline writePipeline;    // AAF 写管道
    private final RetrievalPipeline retrievalPipeline;  // AAF 读管道（含知识库）

    @Override
    public Mono<Void> record(List<Msg> msgs) {
        // 走 AAF 写管道：提取 → 去重 → AtomMemory 写入
        return Mono.fromRunnable(() -> writePipeline.execute(...));
    }

    @Override
    public Mono<String> retrieve(Msg msg) {
        // 走 AAF 读管道：记忆 + 知识库混合检索（UnifiedRetrievalService）
        return Mono.fromCallable(() -> retrievalPipeline.execute(...).toPromptSection());
    }
}
```

**AgentScope `STATIC_CONTROL` 模式的自动触发时机：**

```text
推理前 → 自动调用 retrieve() → AAF 读管道 → 记忆+知识库检索 → 注入上下文
回复后 → 自动调用 record()   → AAF 写管道 → 提取→去重→写入→遗忘
```

无需在 `DefaultAssistantExecutor` 里手动触发记忆管道，AgentScope Hook 机制自动处理。

**上下文 Token 预算截断**：使用 `agentscope-extensions-autocontext-memory`（`AutoContextMemory`），自动按 P0-P5 优先级压缩，替换 AAF 自研的上下文管理器截断逻辑。

## 上下文组装优先级

| 优先级 | 内容 | 说明 |
|--------|------|------|
| P0（必选） | 系统 Prompt + 当前消息 | 不可压缩 |
| P1 | 工作记忆（当前任务焦点） | Agent 执行期临时状态 |
| P2 | 短期记忆（近期会话摘要） | 最近 N 轮压缩摘要 |
| P3 | 知识库检索结果 | 按相关度截取 Top-K |
| P4 | 用户画像摘要 | 长期偏好的压缩表示 |
| P5 | 情景记忆片段 | 相关历史场景 |

超出 Token 预算时从 P5 开始丢弃。

## 个性化实现路径

| 个性化维度 | 数据来源 | 实现机制 |
|-----------|---------|---------|
| 回应风格（正式/轻松/简洁） | 情感记忆 + 用户画像 | Assistant 读取后注入 System Prompt |
| 信息密度（详细/摘要） | 长期记忆（用户偏好） | 上下文组装时调整知识片段数量 |
| 主动提醒（待办/截止日期） | 情景记忆 + 状态管理 | Assistant 会话开始时检查 |
| 领域专业度（新手/专家） | 用户画像 | Prompt 模板选择不同难度版本 |
| 历史延续（记住上次说的） | 短期/长期记忆 | 上下文组装时注入相关历史 |

## 相关文档

| 文档 | 内容 |
|------|------|
| [cognition.md](cognition.md) | Cognition 层详细设计（记忆分区/检索管道/价值观） |
| [atom-memory.md](../../engine/data-knowledge/atom-memory.md) | AtomMemory 引擎（原子化存储/双时态索引/遗忘策略） |
| [nexus-knowledge.md](../../engine/data-knowledge/nexus-knowledge.md) | NexusKB 知识引擎（向量检索/知识图谱/RAG） |
| [state-manager.md](../../engine/meta/state-manager.md) | 状态管理器（四层状态/渐进提交/存储映射） |
| [agent.md](../architecture.md) | Agent 工作记忆与注意力预算 |
| [AgentScope 整合](../agentscope-integration.md) | AgentScope 整合策略与适配层 |

## 三层数据的职责分工

```text
┌─────────────────────────────────────────────────────────┐
│  上下文（Context）                                        │
│  请求级·临时·LLM 窗口内                                   │
│  当前对话消息 + 从记忆/知识检索到的相关片段                 │
│  由上下文管理器组装，Token 预算控制，超限压缩归档            │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  状态（State）                                            │
│  会话级/工作区级·持久·跨请求                               │
│  会话状态：当前任务列表、暂存结果、在线用户                  │
│  工作区状态：文档/代码/工作流（OT/CRDT 合并）               │
│  由状态管理器维护，渐进提交，Redis + PostgreSQL 双层存储     │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│  记忆（Memory）                                           │
│  持久级·跨会话·用户私有                                    │
│  短期：会话摘要（近期对话压缩）                             │
│  长期：用户偏好、习惯、重要事件                             │
│  情景：具体交互场景的完整记录                               │
│  情感：情绪偏好、高压场景交互模式                           │
│  决策日志：AI 自主决策记录，支持异步审查                    │
│  由记忆管道处理，AtomMemory 引擎存储                        │
└─────────────────────────────────────────────────────────┘
```

## 一次对话请求的完整数据流

```text
用户发送消息
    │
    ▼
Assistant（意图理解 + 情感感知）
    │
    ├─ 读取会话状态（SessionState）← 状态管理器
    │   当前任务列表、暂存结果
    │
    ├─ 读取用户画像 ← Cognition（长期记忆 + 情感记忆）
    │   偏好、习惯、情绪模式 → 调整回应风格/信息密度
    │
    ▼
AgentDispatcher → CognitiveCycleExecutor
    │
    ├─ 执行前：上下文管理器组装 LLM 输入
    │   ├── 系统 Prompt（角色定义 + 价值观约束）
    │   ├── 用户画像摘要（从长期记忆提取）
    │   ├── 短期记忆（近期会话摘要）
    │   ├── 知识检索结果（相关领域知识）
    │   ├── 工作记忆（当前任务焦点）
    │   └── 当前消息
    │   Token 预算控制：超限时按优先级压缩（工作记忆 > 短期记忆 > 知识 > 长期记忆摘要）
    │
    ├─ LLM 推理 → 工具调用 → 结果
    │
    └─ 执行后：记忆管道写回
        ├── 提取阶段：从对话中抽取值得记忆的片段（实体/偏好/决策）
        ├── 去重阶段：与已有记忆对比，避免冗余
        ├── 写入阶段：AtomMemory 引擎持久化
        └── 遗忘阶段：低价值旧记忆按时间衰减降权
```

## 记忆管道（Memory Pipeline）

记忆管道分为**读管道**和**写管道**两种，职责不同，设计原则不同。

### 读管道（RetrievalPipeline）— 可编排

按 `MemoryStrategy` 从多源检索并组装上下文，不同场景配置不同策略：

```text
输入（query、userId、sessionId、knowledgeBaseId）
    │
    ▼ 查询理解 → 路由决策 → 并行检索 → RRF 融合 → LLM 重排
    │
    ▼ MemoryContext（可注入 Prompt 的上下文块）
```

| 策略 | 数据源 | 适用场景 |
|------|--------|---------|
| `HYBRID`（默认） | 短期记忆 + 长期记忆 + 知识库 | 通用助理 |
| `MEMORY_ONLY` | 短期记忆 + 长期记忆 | 个人助理 |
| `KNOWLEDGE_ONLY` | 知识库 | 客服/问答 |
| `PROCEDURAL_FIRST` | 程序化记忆优先 + 知识库 | 代码助理 |
| `FULL` | 全源（记忆+知识库+程序化+图谱） | 复杂推理 |

### 写管道（MemoryWritePipeline）— 固定流程

对话结束后将交互内容持久化，步骤固定不可跳过，保障数据一致性：

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

唯一可配置点：**提取策略**（提取什么），通过 `MemoryStrategy` 控制提取粒度，步骤顺序固定不变。

> 设计决策：写管道固定而非可编排，原因是写入逻辑是数据一致性保障，随意重排步骤容易导致记忆污染、重复或丢失。不同 Assistant 的差异只在"写什么"，不在"怎么写"。

## 上下文组装优先级

上下文窗口有限，组装时按以下优先级截断：

| 优先级 | 内容 | 说明 |
|--------|------|------|
| P0（必选） | 系统 Prompt + 当前消息 | 不可压缩 |
| P1 | 工作记忆（当前任务焦点） | Agent 执行期临时状态 |
| P2 | 短期记忆（近期会话摘要） | 最近 N 轮压缩摘要 |
| P3 | 知识检索结果 | 按相关度截取 Top-K |
| P4 | 用户画像摘要 | 长期偏好的压缩表示 |
| P5 | 情景记忆片段 | 相关历史场景 |

超出 Token 预算时从 P5 开始丢弃。

## 个性化实现路径

| 个性化维度 | 数据来源 | 实现机制 |
|-----------|---------|---------|
| 回应风格（正式/轻松/简洁） | 情感记忆 + 用户画像 | Assistant 读取后注入 System Prompt |
| 信息密度（详细/摘要） | 长期记忆（用户偏好） | 上下文组装时调整知识片段数量 |
| 主动提醒（待办/截止日期） | 情景记忆 + 状态管理 | Assistant 会话开始时检查 |
| 领域专业度（新手/专家） | 用户画像 | Prompt 模板选择不同难度版本 |
| 历史延续（记住上次说的） | 短期/长期记忆 | 上下文组装时注入相关历史 |

## 数据采集入口

用户感知是横切面，数据来源跨多层，通过事件驱动统一汇入 Cognition 层：

```text
┌─────────────────────────────────────────────────────────────────┐
│  交互层                                                          │
│  UI 操作行为（点击/停留/滚动）→ BehaviorEvent                    │
│  设备/端信息（屏幕尺寸/OS/网络）→ DeviceContextEvent             │
│  用户界面输入（表单/搜索/偏好设置）→ UserInputEvent              │
└──────────────────────────┬──────────────────────────────────────┘
                           │ 事件总线（异步）
┌──────────────────────────┼──────────────────────────────────────┐
│  引擎层                   │                                      │
│  监控引擎行为日志 → UsageStatEvent（功能使用频次/时段/时长）      │
│  Token 消耗统计 → TokenUsageEvent                                │
└──────────────────────────┼──────────────────────────────────────┘
                           │ 事件总线（异步）
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│  Cognition 层 — UserProfileService（被动接收事件，异步提炼）      │
│                                                                  │
│  EventListener 接收各层事件 → 缓冲聚合 → 定期 LLM 提炼          │
│    ├── 行为偏好：常用功能、活跃时段、交互模式                     │
│    ├── 设备适配：首选端、屏幕偏好、网络环境                       │
│    └── 使用统计：功能热度、Token 消耗模式                         │
│                                                                  │
│  更新频率：实时事件缓冲 → 批量写入（每 N 条或每 M 分钟）         │
└─────────────────────────────────────────────────────────────────┘
```

### 事件类型

| 来源层 | 事件 | 携带数据 | 写入目标 |
|--------|------|---------|---------|
| 交互层 | `BehaviorEvent` | 操作类型、目标元素、停留时长 | 用户画像·行为维度 |
| 交互层 | `DeviceContextEvent` | 设备类型、屏幕尺寸、OS、网络 | 用户画像·端适配参数 |
| 引擎层 | `UsageStatEvent` | 功能 ID、调用次数、时段分布 | 用户画像·行为维度 |
| 引擎层 | `TokenUsageEvent` | 模型、Token 数、任务类型 | 预算偏好 |
| Assistant 层 | `IntentHistoryEvent` | 意图类型、频次、上下文 | 用户画像·偏好维度 |
| Assistant 层 | `EmotionStateEvent` | 情绪极性、强度、触发场景 | 情感记忆 |

### 设计原则

- **被动接收**：UserProfileService 不主动拉取各层数据，只监听事件
- **异步非阻塞**：事件通过 Spring Event / Redis Stream 异步投递，不影响主链路性能
- **隐私边界**：行为数据仅用于个性化，不外传；用户可查看/删除自己的画像数据

## 前端消费（语义组件引擎适配）

语义组件引擎读取用户画像，实现界面个性化适配：

```text
语义组件引擎（SenseUI）
    │
    ├── 读取 UserProfile.deviceContext
    │     → 多端适配：移动端简化布局 / 桌面端完整功能
    │     → 网络适配：弱网环境降低媒体质量
    │
    ├── 读取 UserProfile.preferences
    │     → 主题偏好：深色/浅色/跟随系统
    │     → 信息密度：紧凑/宽松布局
    │     → 语言/地区：本地化适配
    │
    └── 读取 UserProfile.behaviorPattern
          → 常用功能前置：高频操作放入快捷入口
          → 引导策略：新手显示引导，专家隐藏
```

### 接口契约

```text
GET /api/user/profile/ui-context
Response:
{
  "theme": "dark",
  "density": "compact",
  "locale": "zh-CN",
  "expertLevel": "advanced",
  "shortcuts": ["knowledge-search", "agent-chat"],
  "deviceAdaptation": { "type": "desktop", "screenWidth": 1920 }
}
```

语义组件引擎在页面渲染时请求一次，缓存在客户端（TanStack Query），画像变更时通过 SSE 推送刷新。

## 架构定位

用户感知与个性化是横切面，放在 Cognition 层作为独立组件。

理由：

1. 核心是"理解用户是谁"——认知能力，不是编排能力
2. 数据存储和计算主体在 Cognition（画像存储、记忆检索、偏好提炼）
3. 被动响应——被 Assistant/Agent 调用时提供用户上下文，不主动触发
4. 与 Cognition 的记忆服务、学习反哺天然协作（学习反哺产出→更新画像）

Assistant 层只"消费"个性化结果（读取画像→注入 Prompt），不负责"生产"。交互层的 UI 操作行为通过事件驱动写入 Cognition，个性化组件不直接感知交互层。

## 相关文档

| 文档 | 内容 |
|------|------|
| [cognition.md](cognition.md) | Cognition 层详细设计（记忆分区/检索管道/价值观） |
| [atom-memory.md](../../engine/data-knowledge/atom-memory.md) | AtomMemory 引擎（原子化存储/双时态索引/遗忘策略） |
| [state-manager.md](../../engine/meta/state-manager.md) | 状态管理器（四层状态/渐进提交/存储映射） |
| [agent.md](../architecture.md) | Agent 工作记忆与注意力预算 |
