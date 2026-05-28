---
level: Practice
layer: Model
purpose: 五层智能架构：智能体系统的架构设计与协作机制
status: published
version: 3.0.0
date: 2026-05-28
author: AaronZZH
gains:
  - 架构可视化图、关键关系说明、技能/工具分层、技术方案、包结构设计
  - Assistant 多实例并行（fork 模式）、输入缓冲区、各层任务管理
  - 多会话支持、Checkpoint 分层设计、缓存层、编排模式确认
---

# 五层智能架构多智能体系统设计

> 从内核到协作，分层认知，渐进决策。

## 设计原则

- **分工协作，发挥各自专长**：大模型 + 传统计算 + 人类，各司其职
- **群体智能**：分层多智能体、智能块、智能核
- **智能模块化**：选择合适的模型，专用模型/通用模型专用化
- **复杂环境协同**：多智能体在复杂环境下的协同工作能力
- **自适应多场景**：从易用性、灵活性、扩展性、性能、数据安全、隐私
- **渐进决策**：多模型、分布式存储运算、隐私隔离、加密存储
- **业务与智能融合**：减少具体业务开发，同时保持高效高性能易用
- **可验证性优先**：规划阶段将模糊任务降维为可自动验证的子任务，评估阶段区分"可自动验证"和"需人工审查"，可验证部分自主推进，不可验证部分留决策日志异步审查
- **能力护栏**：根据任务类型动态限定 Agent 操作范围，限定范围换取信任空间，减少人工审查成本
- **瓶颈迁移意识**：执行近乎免费，规划与审查是新瓶颈——Agent 的核心价值是帮用户规划和审查，而非仅仅执行

### 架构核心约束

- **无状态层可水平扩展**：Core 和 Agent 设计为无状态，支持池化和并发；同一 Agent 定义可并发处理多个任务实例，失败直接重新调度无需恢复状态
- **状态集中在 Cognition**：Team / Assistant 只持有轻量会话级状态，数据级状态统一由 Cognition 管理，避免状态分散导致一致性问题
- **认知循环分层原则**：每层有且只有一个认知循环，粒度从项目级到请求级逐层细化；上层循环通过调度触发下层循环，下层结果通过回调返回上层，不允许跨层直接触发
- **私有与共享分离**：Agent 内感知 / 规划 / 执行模块私有，不跨 Agent 共享；记忆 / 知识 / 价值观下沉到 Layer 1，多 Agent 共享，避免知识孤岛
- **引擎与业务解耦**：引擎层提供通用能力（Agent 编排、工作流执行、知识检索、记忆管理），业务模块只依赖引擎接口，不直接操作底层
- **渐进决策模型**：决策分三个粒度展开——Agent 层粗粒度规划后执行中细化（决策树展开：走一步看一步，每步结果作为下一步规划输入）；Assistant 层意图漏斗收敛用户需求后再调度（意图澄清优先于执行，通过最少问题快速收敛）；Team 层目标假设性分解后动态调整（目标不清晰不阻塞执行）
- **决策权跨层流动**：低置信度决策上报上层处理，高置信度决策本层直接执行，决策权随置信度在层间动态流动，不固定归属某层
- **可撤销渐进提交**：执行步骤先进入暂存态，用户或上层确认后提交，未确认前可回滚，避免低置信度操作直接生效
- **智能降级策略**：AI 服务不可用 → 降级规则引擎；知识检索失败 → 使用默认知识库；Agent 超时 → 切换简化流程
- **知识能力一体**：知识库与工具系统绑定，禁止独立迭代导致语义漂移
- **三层上下文分离**：知识库（静态、领域、全局共享）/ 记忆库（动态、个体、时序+语义双索引）/ 上下文（临时、会话级、KV 存储）职责严格隔离，不允许跨层直接读写
- **执行结果反哺知识**：工具调用结果、Agent 执行日志自动归档，经评估后更新知识库，形成知识生长闭环
- 智能体池化、知识分片、记忆压缩：定期归档和摘要

## 五层智能架构

```text
Layer 4  协作层  Team                              【项目级】
         认知循环：目标对齐 → 任务分发 → 进度同步 → 结果聚合 → 冲突仲裁
         多个 Assistant 组成团队，支持 Leader 协调或平等协作
         状态：轻量会话级状态（任务分配表、进度、仲裁结果），不持有数据级状态

Layer 3  助理层  Assistant                         【会话级】
         认知循环：情感感知 → 意图理解 → 上下文构建 → Agent 调度 → 反馈整合 → 记忆更新
         面向人，有人格 / 角色扮演，持有多个 Agent，向上可加入 Team
         多实例并行：同 Actor + 多 Role fork 子实例，主实例协调聚合
         能力护栏：根据任务类型动态限定 Agent 操作范围（限定范围换取信任空间）
         状态：用户画像（含情感偏好）、长期记忆引用（实体存于 Cognition），会话上下文

Layer 2  智能体层  Agent                           【任务级·无状态】
         认知循环：感知 → 规划 → 执行 → 评估 → 学习 ↔ 记忆
         规划模块：目标分解、任务排序、可验证性降维（将模糊任务拆为可自动验证的子任务）
         评估模块：结果验证（可验证→自动检查；不可验证→标记待人工审查）、置信度评估
         无状态任务执行单元，执行前从 Cognition 拉取记忆/知识，执行后写回
         多实例并发：AgentPool 池化复用，同一定义可并发处理多个任务
         状态：无状态，自身不持久化

Layer 1  认知基础层  Cognition                     【持久级·跨 Agent 共享·横向共享底座】
         认知循环：存储 / 检索 / 更新 / 遗忘（被动响应，不主动触发）
         记忆 + 知识 + 价值观 + 决策日志，为上层提供认知基础
         决策日志：每次 AI 自主推进的决策记录（决策点、选项、理由、置信度、可验证性），支持异步审查
         状态分区：用户私有区 / 全局共享区 / Agent 工作区 / 决策审计区

Layer 0  内核层  Core                              【请求级·无状态】
         认知循环：推理 / 生成 / 上下文窗口管理
         LLM + Context，上下文由调用方（Agent）组装后传入
         无状态，可水平扩展、池化复用
```


### 架构可视化

```text
┌─────────────────────────────────────────────────────────────────────────┐
│  缓存层（应用级，启动时加载 + 变更刷新）                                  │
│  ActorCache / RoleCache / SkillDefCache / AgentDefCache / ModelCache     │
│  策略：本地 Caffeine + Redis 二级缓存，DB 变更时发事件刷新               │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│  Layer 4  Team  协作层                                    【项目级】     │
│  高层抽象：多个 Assistant 基于 A2A 协议协作完成复杂目标                   │
│  目标对齐 → 任务分发 → 进度同步 → 结果聚合 → 冲突仲裁                    │
│  任务管理：GoalTracker（目标级，持久化到 DB）                             │
│  A2A 协议：Assistant 间异步消息通信，不要求同框架/同进程                  │
└─────────────────────────────────────────────────────────────────────────┘
                              ↕ 调度/回调
┌─────────────────────────────────────────────────────────────────────────┐
│  Layer 3  Assistant  助理层                               【会话级】     │
│  情感感知 → 意图理解 → Skill 匹配 → Agent 调度 → 反馈整合                │
│                                                                          │
│  Assistant = Actor + Role + MemoryStrategy                               │
│                                                                          │
│  ┌───────────────────────┐  ┌──────────────────────────────┐            │
│  │  Actor（人格载体）     │  │  Role（能力配置）             │            │
│  │  name / persona       │  │  1..N Skill（任务级路由）     │            │
│  │  systemPrompt / avatar│  │  1..N Tool（工具白名单）      │            │
│  │  可复用·跨 Role        │  │  可复用·跨 Actor              │            │
│  └───────────────────────┘  └──────────────────────────────┘            │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  多实例并行（fork 模式）                                          │   │
│  │  主实例（协调者）→ fork 子实例（同 Actor + 不同 Role）→ join 聚合  │   │
│  │  子实例：临时创建，执行完销毁，不池化                              │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  InputBuffer（输入缓冲区）                                        │   │
│  │  Agent 执行期间接收用户追加输入，分类后决定处理时机                 │   │
│  │  取消→立即中断 / 修改→重新规划 / 补充→注入上下文 / 无关→排队      │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  TaskBoard（任务看板）                                            │   │
│  │  子任务状态追踪：PENDING / RUNNING / DONE / FAILED + 依赖关系     │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  Checkpoint（助理级检查点）                                       │   │
│  │  保存 TaskBoard + 会话上下文 + InputBuffer，支持长任务恢复         │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
                              ↕ 调度/回调
┌─────────────────────────────────────────────────────────────────────────┐
│  Layer 2  Agent  智能体层                                 【任务级】     │
│  感知 → 规划 → 执行（调用工具+技能）→ 评估 → 学习                        │
│  AAF 语义无状态：执行前注入 MemoryContext，执行后写回 Cognition           │
│  AgentPool 池化复用：借出前重置框架内部状态，归还前清空                   │
│  多实例并发：同一 Agent 定义可同时服务多个 Assistant 实例                  │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────┐            │
│  │  Tool（工具系统）engine/tool/                            │            │
│  │  ToolRegistry：Spring Bean 自动发现 + MCP 协议发现       │            │
│  │  ToolCallDispatcher：参数校验 → 执行 → 结果回传          │            │
│  │  契约（FunctionDefinition + ToolProvider）在 Core 层定义 │            │
│  └─────────────────────────────────────────────────────────┘            │
│  ┌─────────────────────────────────────────────────────────┐            │
│  │  Checkpoint（智能体级检查点）runtime/                     │            │
│  │  每步执行后保存状态快照（步骤 + 中间结果 + 工作记忆）     │            │
│  │  失败时从最近检查点恢复，指数退避重试                     │            │
│  └─────────────────────────────────────────────────────────┘            │
│  ┌─────────────────────────────────────────────────────────┐            │
│  │  WorkingMemory（工作记忆 / PlanNotebook）                │            │
│  │  步骤级任务管理，执行期存在，任务结束清理                 │            │
│  └─────────────────────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────────────────┘
        ↑ 执行前拉取（MemoryPipeline）  ↓ 执行后写回（store）
┌─────────────────────────────────────────────────────────────────────────┐
│  Layer 1  Cognition  认知基础层                【持久级·跨 Agent 共享】   │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                  │
│  │    Memory    │  │  Knowledge   │  │    Value     │                  │
│  │   记忆系统    │  │   知识库      │  │   价值观      │                  │
│  │ 短期/长期/    │  │ 向量+图谱+    │  │ 伦理约束+     │                  │
│  │ 情景/程序化   │  │ 关键词混合    │  │ 优先级规则    │                  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                  │
│         └─────────────────┼─────────────────┘                           │
│                           ↓                                              │
│              ┌────────────────────────┐                                 │
│              │    MemoryPipeline      │  ← 上下文组装流水线               │
│              │  查询理解 → 路由决策    │                                 │
│              │  → 并行检索（混合检索） │                                 │
│              │  → RRF 融合 → 重排     │                                 │
│              │  → MemoryContext       │  → 注入 Prompt                   │
│              └────────────────────────┘                                 │
│  状态分区：用户私有区 / 全局共享区 / Agent 工作区                         │
└─────────────────────────────────────────────────────────────────────────┘
                              ↕ 上下文传入 / 结果返回
┌─────────────────────────────────────────────────────────────────────────┐
│  Layer 0  Core  内核层                                    【请求级】     │
│  LLM 推理 / 上下文窗口管理 / Token 预算控制 / 多模型路由                  │
│  FunctionDefinition + ToolProvider 接口契约（工具系统契约层）             │
│  完全无状态，上下文由 Agent 组装后传入，不需要 Checkpoint                 │
└─────────────────────────────────────────────────────────────────────────┘
```


### 关键关系说明

| 关系 | 说明 |
|------|------|
| Agent ↔ Cognition **水平协作** | Agent 无状态，Cognition 是横向共享底座；不是上下级，是执行者与记忆库的协作关系 |
| MemoryPipeline | Cognition 对外暴露的上下文组装接口；混合检索（向量+图谱+关键词+RRF）是其内部的并行检索步骤 |
| 混合检索 ⊂ MemoryPipeline | 混合检索 = 管道第 3-4 步（并行检索 + RRF 融合），不等于管道本身 |
| MemoryStrategy | Assistant 层配置，决定 MemoryPipeline 使用哪些 Stage（Memory/Knowledge/混合/程序化优先） |
| Learning（横切） | 异步反哺通道，Agent 执行结果 → 评估 → 更新 Memory/Knowledge/Value，不属于 Cognition 内部循环 |
| **User → Assistant（1:N）** | 一个用户可拥有多个 Assistant，每个 Assistant 有独立人格/技能集/记忆策略 |
| **Assistant → Agent（1:N）** | 一个 Assistant 协调多个 Agent，按 Skill 匹配路由，Agent 全局注册按能力共享 |
| **Team → Assistant（1:N）** | Team 是高层抽象，多个 Assistant 基于 A2A 协议协作；Assistant 可独立运行，也可加入 Team |
| **A2A 协议** | Assistant 间异步消息通信，不要求同框架/同进程，支持跨服务协作 |
| **Agent 池化** | AgentScope ReActAgent 内部有对话历史（非真正无状态），借出前重置/归还前清空，对上层透明为无状态 |

### 技能与工具的分层

```text
Assistant 层  ←  Skill（技能）+ Tool（工具白名单）定义/配置在此
  │  Skill = 粗粒度·任务级路由（触发条件 + 绑定 Agent + 系统 Prompt）
  │  Tool  = 细粒度·原子工具白名单（Assistant 配置，Agent 执行时继承）
  ↓ 调度
Agent 层      ←  Tool 注册与调用在此（engine/tool/）
  │  ToolRegistry：Spring Bean + MCP 发现，按 assistantId 白名单过滤
  │  ToolCallDispatcher：参数校验 → 执行 → 结果回传
  ↓ 接口契约
Core 层       ←  FunctionDefinition + ToolProvider 接口定义在此
```

| 概念 | 定义/配置层 | 执行层 | 说明 |
|------|--------|--------|------|
| Skill（技能） | Assistant | Agent | 粗粒度·任务级，触发条件 + Agent 绑定，是 Assistant→Agent 的路由规则 |
| Tool（工具） | Assistant（白名单配置）/ Core（契约） | Agent（执行） | 细粒度·原子级，Assistant 配置可用工具集，Agent 执行时按白名单调用 |
| MCP 工具 | 外部服务注册 | Agent | 通过 MCP 协议发现，统一注册到 ToolRegistry，受 Assistant 白名单控制 |

### 层间调用规则

- 上层循环通过调度触发下层循环
- 下层结果通过回调返回上层
- **禁止跨层直接触发**（如 Team 不能直接调用 Core）

## 运行时架构

### 缓存层

配置数据（Actor、Role、SkillDef、AgentDef、AiModel、PromptTemplate）启动时加载，运行时缓存，DB 变更时事件刷新。

```text
┌─────────────────────────────────────────────────────────────────┐
│  缓存层（应用级）                                                │
│                                                                  │
│  ActorCache          RoleCache          SkillDefCache            │
│  (id→Actor)          (id→Role)          (id→SkillDef)           │
│  AgentDefCache       ModelCache         PromptCache              │
│  (id→AgentDef)       (id→AiModel)       (id→Template)           │
│                                                                  │
│  策略：本地 Caffeine + Redis 二级缓存                            │
│  刷新：DB 变更 → Spring Event → 本地失效 → 懒加载重填            │
│  Actor/Role 是纯配置，不需要池化，直接从缓存引用                 │
└─────────────────────────────────────────────────────────────────┘
```

### 多会话支持

一个对话对应一个主 Assistant 实例，用户只与主实例（协调者）交互。

```text
用户
  ├── 对话 A（"帮我开发用户模块"）
  │     └── 主 Instance-A（协调者，用户唯一交互入口）
  │           ├── InputBuffer（接收追加输入）
  │           ├── TaskBoard（子任务状态）
  │           ├── MessageStream（统一输出流）
  │           ├── fork → 子 Instance-A1 (Role: 后端) → AgentPool
  │           └── fork → 子 Instance-A2 (Role: 前端) → AgentPool
  │
  ├── 对话 B（"帮我写文档"）
  │     └── 主 Instance-B（协调者）
  │           └── 子 Instance-B1 (Role: 文档) → AgentPool
  │
  └── 对话 C（"闲聊"）
        └── 主 Instance-C（直接回复，无子实例）

数量关系：
  用户 : 对话         = 1 : N（可同时开多个对话）
  对话 : 主实例       = 1 : 1（每个对话一个协调者）
  主实例 : 子实例     = 1 : 0..N（按需 fork，可并行）
  子实例 : Agent      = 1 : 1..N（子实例调度 Agent 执行）
  AgentPool           = 全局 1 个（所有实例共享）
```

### 各层任务管理

| 层 | 任务管理组件 | 粒度 | 生命周期 | 持久化 |
|----|-------------|------|----------|--------|
| Team | GoalTracker | 目标级 | 项目级 | DB |
| 主 Assistant | TaskBoard | 子任务级 | 会话级 | 内存 + 可选持久化（长任务） |
| 子 Assistant 实例 | SubTaskContext | 当前任务 | fork→完成→销毁 | 无 |
| Agent | WorkingMemory（PlanNotebook） | 步骤级 | 执行期 | Checkpoint（可恢复） |

任务管理归属：
- Team 和 Assistant 是有状态的，各自维护自己层级的任务列表和进度
- Agent 是无状态的，步骤规划存在工作记忆中，执行完即销毁
- 跨层不共享任务状态——上层只知道"派发了什么、结果是什么"，不感知下层内部步骤

```text
Team：目标 A 拆为 [子目标1, 子目标2, 子目标3]
  └── Assistant（主实例）：子目标1 拆为 [任务a, 任务b]
        ├── fork 子实例(Role-A)：任务a → Agent 规划为 [步骤1→步骤2→步骤3]
        └── fork 子实例(Role-B)：任务b → Agent 规划为 [步骤1→步骤2]
      → 主实例聚合验证 → 返回 Team
```

### Checkpoint 分层设计

Checkpoint 引擎放在 engine 层作为通用基础设施，各层按需使用。Core 层不需要（无状态单次调用）。

```text
engine/runtime/checkpoint/          ← 通用 Checkpoint 引擎
  ├── CheckpointStore（接口）       持久化存储
  ├── CheckpointEntry              快照数据结构
  └── CheckpointPolicy             策略（每步/每N步/关键节点）
```

| 层 | 是否需要 | 保存内容 | 恢复场景 |
|----|---------|---------|----------|
| Core | ❌ | 无状态，单次请求 | 失败直接重试 |
| Agent | ✅ | 步骤进度 + 中间结果 + 工作记忆 | 步骤失败从最近检查点重试 |
| Assistant 子实例 | ✅ | 任务上下文 + 关联 Agent 检查点 ID | 子实例崩溃后主实例重新 fork |
| Assistant 主实例 | ✅ | TaskBoard + 会话上下文 + InputBuffer | 服务重启后恢复长任务 |
| Team | ✅（v0.6+） | GoalTracker + 分配表 | 项目级任务恢复 |

恢复流程：
```text
服务重启 / 主实例崩溃：
  1. SessionManager 扫描未完成的 AssistantCheckpoint
  2. 恢复主实例：加载 TaskBoard + 上下文
  3. 检查子任务状态：
     ├── DONE → 跳过
     ├── RUNNING → 查找子实例 Checkpoint → 有则恢复，无则重新 fork
     └── PENDING → 等待依赖满足后正常调度
  4. 通知用户："之前的任务已恢复，继续执行中..."
```


## Layer 0 内核层 Core

> 无状态·请求级，LLM 推理的最小执行单元

### 职责

- LLM 接入与调用
- 上下文窗口管理
- Token 预算控制
- 多模型路由（按任务类型选择模型）

### 认知循环

```text
接收上下文（由 Agent 组装）
  ↓
推理 / 生成
  ↓
返回结果 + Token 消耗统计
```

### 状态策略

- **完全无状态**：不持有任何上下文，每次调用独立
- **可水平扩展**：支持池化复用，多实例并发
- **不需要 Checkpoint**：原子操作，失败直接重试

### 设计要点

- 上下文由调用方（Agent）组装后传入，Core 不负责上下文管理
- 支持 function calling，工具调用决策在此层完成
- 模型选择策略：简单任务用轻量模型，复杂任务用强模型

## Layer 1 认知基础层 Cognition

> 持久级·跨 Agent 共享，为上层提供记忆、知识、价值观

### 职责

- 记忆管理：短期 / 长期 / 情景 / 情感记忆
- 知识检索：领域知识、向量检索、知识图谱
- 价值观约束：团队级伦理约束，全局一致

### 认知循环

```text
存储 / 检索 / 更新 / 遗忘
（被动响应上层请求，不主动触发）
```

### 状态分区

| 分区 | 说明 | 访问权限 |
|------|------|----------|
| 用户私有区 | 用户记忆、情感偏好、历史模式 | 仅该用户的 Assistant/Agent |
| 全局共享区 | 领域知识、规范文档、价值观 | 所有 Agent |
| Agent 工作区 | 任务执行中的临时数据 | 当前 Agent |

### 核心引擎

- **记忆引擎**：详见 [AtomMemory 原子记忆引擎](../engine/atom-memory.md)
- **知识库引擎**：详见 [NexusKB 连接式知识引擎](../engine/nexus-knowledge.md)

### 设计要点

- 时序 + 语义双索引，支持按时间和语义检索
- 情感记忆本地存储，不用于训练或外传
- 遗忘机制：低价值记忆定期归档或清理

## Layer 2 智能体层 Agent

> 任务级·无状态，感知-规划-执行-评估的认知循环

### 职责

- 任务执行：接收任务，完成后返回结果
- 工具调用：通过 MCP 协议调用工具
- 规划决策：目标分解、任务排序

### 认知循环

```text
感知 → 规划 → 执行 → 评估 → 学习 ↔ 记忆
```

| 模块 | 职责 | 类比 |
|------|------|------|
| 感知模块 | 输入解析、意图识别 | 感觉皮层 |
| 规划模块 | 目标分解、任务排序、可验证性降维 | 前额叶 |
| 执行模块 | 工具调用、代码生成 | 小脑 |
| 评估模块 | 结果验证、置信度评估 | 评估阶段 |
| 价值模块 | 优先级判断、伦理约束 | 杏仁核 |

### 状态策略

- **完全无状态**：执行前从 Cognition 拉取记忆/知识，执行后写回
- **多实例并发**：同一 Agent 定义可并发处理多个任务实例
- **失败可重试**：无状态设计使得失败后可直接重新调度
- **Checkpoint**：步骤级检查点，失败从最近检查点恢复

### AgentPool 池化

```text
AgentPool（全局共享）
  ├── idle: [Agent-1, Agent-2, Agent-3, ...]    预创建实例
  ├── busy: [Agent-4(task-x), Agent-5(task-y)]  执行中实例
  └── 策略：预热 N 个，动态扩缩，最大 M 个，超时回收

生命周期：
  borrow() → 重置框架内部状态 → 注入任务上下文 → 执行 → release() → 清空
```

Agent 由所属的 Assistant 实例调度，不跨实例共享正在执行的 Agent。

### 何时启用 Agent

| 维度 | 判断 | 结论 |
|------|------|------|
| 任务复杂度 | 低 | 用 Workflow |
| 任务价值 | < $0.1 | 用 Workflow |
| 所有步骤可执行 | 否 | 缩小范围或加人工 |
| 错误成本 | 高 | 加人工审核节点 |

**最适合场景**：编码 Agent（从需求文档到完整 PR），复杂度高、价值高、错误可控。

核心心法：Workflow 适合可预测任务，Agent 适合动态场景。成功关键在于精准定位、保持简单、对 Agent 有限视角的理解。

### 单 Agent 最小闭环

Agents are models using tools in a loop，保持极致简单最小闭环只需要：环境+工具+系统提示。

```python
env = Environment()
tools = Tools(env)
system_prompt = "Goals, constraints, and how to act"

while True:
    action = llm.run(system_prompt + env.state)
    env.state = tools.run(action)
```

核心心法：**站在 Agent 视角思考，它只能看到你给的上下文**。每次提示前都要模拟它的"视野"。

### 引擎

- **工具引擎**：详见 [../engine/tools.md](../engine/tools.md)（待创建）


## Layer 3 助理层 Assistant

> 会话级，面向人的交互入口，核心编排单元

### 职责

- 意图理解：解析用户输入，识别目标和约束
- 情感感知：识别用户情绪状态，动态调整回应风格
- Agent 调度：根据任务类型选择合适的 Agent
- 多实例并行：fork 子实例并行处理，聚合结果
- 输入缓冲：执行期间接收用户追加输入
- 记忆更新：会话结束后更新用户画像和长期记忆

### 认知循环

```text
情感感知 → 意图理解 → 上下文构建 → Agent 调度 → 反馈整合 → 记忆更新
```

### 组成结构

```text
Assistant = Actor + Role + MemoryStrategy

Actor（人格载体）：name / persona / systemPrompt / avatar
  - 纯配置，无运行时状态，从缓存引用
  - 可复用·跨 Role（同一 Actor 可搭配不同 Role）

Role（能力配置）：Skill 集 + Tool 白名单
  - 纯配置，无运行时状态，从缓存引用
  - 可复用·跨 Actor（同一 Role 可被不同 Actor 使用）

MemoryStrategy：决定从哪些源拉取上下文
  - MEMORY_ONLY / KNOWLEDGE_ONLY / HYBRID / PROCEDURAL_FIRST / FULL
```

### 多实例并行（fork 模式）

Assistant 多实例通过 fork 模式实现，不需要池化（实例轻量，按需创建/销毁）。

```text
主 AssistantInstance（协调者，长驻，绑定用户会话）
  │
  ├── 判断任务可并行（自主模式）或被编排为并行（编排模式）
  │
  ├── fork(Role-A, contextSnapshot) → 子 Instance-1（临时）
  │     └── AgentPool.borrow() → Agent 执行 → AgentPool.release()
  │
  ├── fork(Role-B, contextSnapshot) → 子 Instance-2（临时）
  │     └── AgentPool.borrow() → Agent 执行 → AgentPool.release()
  │
  ├── await all → 聚合 → 冲突检测
  └── 子实例销毁（GC 回收）
```

**为什么不池化 Assistant 实例：**
- 实例 = Actor 引用 + Role 引用 + 会话上下文（内存中的消息列表），创建成本极低
- 每个子实例的会话上下文是独立的、任务相关的，不可互换
- 池化的前提是"实例可互换"——子实例各自带着不同 Role 和不同任务上下文

**子实例上下文策略：**
- fork 时拷贝主实例上下文的只读快照
- 各子实例独立写入自己的上下文
- 主实例 merge 子实例结果

**与 Team 的边界：**

| 维度 | 单 Assistant 多实例 | Team 多 Assistant |
|------|---------------------|-------------------|
| 用户感知 | 一个助理在高效工作 | 多个助理在协作 |
| 上下文共享 | 共享同一 Cognition 用户私有区 | 各自独立（需 A2A 同步） |
| 冲突处理 | 主实例直接裁决 | 需要仲裁协议 |
| 适用场景 | 同一用户、同一目标的并行加速 | 跨系统、跨服务、真正独立的多方 |

### 输入缓冲区（InputBuffer）

Agent 执行期间，用户可以继续输入。InputBuffer 在 Assistant 层接收并分类处理。

```text
┌──────────────────────────────────────────────────────────────┐
│  InputBuffer                                                  │
│                                                               │
│  接收 → 分类 → 决定处理时机                                   │
│                                                               │
│  ┌──────────────┬──────────────────────────────────────────┐ │
│  │ 输入类型      │ 处理方式                                  │ │
│  ├──────────────┼──────────────────────────────────────────┤ │
│  │ 取消/中断     │ 立即中断当前执行                          │ │
│  │ 修改指令      │ 标记当前结果待废弃，重新规划              │ │
│  │ 补充信息      │ 注入当前执行上下文（下一个 Checkpoint 可见）│ │
│  │ 无关/闲聊     │ 排队，当前任务完成后处理                  │ │
│  └──────────────┴──────────────────────────────────────────┘ │
│                                                               │
│  实现要点：                                                   │
│  - 基于 WebSocket/SSE 双向通道                                │
│  - 缓冲区是 Assistant 级别，不是 Agent 级别                   │
│  - Agent 通过 Checkpoint 回调检查是否有新输入                 │
└──────────────────────────────────────────────────────────────┘
```

交互示例：
```text
用户："帮我实现用户注册功能"
Assistant："好的，正在规划..."（Agent 开始执行）

用户："对了，密码要求至少8位"  ← 补充信息
  → InputBuffer 分类 → 注入 Agent 上下文（下一个 Checkpoint 可见）

用户："算了，先做登录功能"  ← 修改指令
  → InputBuffer 分类 → 中断当前 Agent，暂存已有结果，重新规划
```

### 状态策略

| 状态类型 | 生命周期 | 存储位置 |
|----------|----------|----------|
| 会话上下文 | 会话级 | 内存 |
| TaskBoard | 会话级 | 内存 + Checkpoint（长任务） |
| InputBuffer | 会话级 | 内存 |
| 用户画像 | 持久 | Cognition 用户私有区 |
| 长期记忆引用 | 持久 | Cognition（实体存储） |

### 情感感知

- **感知**：通过语言语气、操作节奏推断用户情绪状态
- **响应**：AI 回应风格、信息密度随情绪状态自适应
- **记忆**：情感偏好纳入用户画像
- **伦理边界**：不利用情绪弱点，不模拟情感依赖


## Layer 4 协作层 Team

> 项目级，多 Assistant 编排与协作（保留，v0.6+ 简化实现）

### 职责

- 目标对齐：确保所有 Assistant 理解共同目标
- 任务分发：将大任务拆解分配给各 Assistant
- 进度同步：跟踪各 Assistant 执行进度
- 冲突仲裁：处理 Assistant 间的意见分歧

### 认知循环

```text
目标对齐 → 任务分发 → 进度同步 → 结果聚合 → 冲突仲裁
```

### 协作模式

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| Leader 协调 | 一个 Assistant 作为 Leader 统筹 | 明确分工的项目 |
| 平等协作 | 多个 Assistant 平等讨论决策 | 需要多视角的探索性任务 |

### 状态策略

- **轻量会话级状态**：任务分配表、进度、仲裁结果
- **不持有数据级状态**：数据统一由 Cognition 管理
- **GoalTracker**：目标级任务管理，持久化到 DB

### 通信协议

- **A2A 协议**：Agent-to-Agent，多智能体间通信
- 支持同步和异步通信模式
- 不要求同框架/同进程，支持跨服务协作

### 与 Assistant 多实例的定位区分

| 场景 | 用 Assistant 多实例 | 用 Team |
|------|---------------------|---------|
| 同一用户、同一目标的并行加速 | ✅ | ❌ |
| 跨用户/跨系统协作 | ❌ | ✅ |
| 对抗性验证（写+审查） | ❌ | ✅ |
| 需要 A2A 协议的外部 Agent | ❌ | ✅ |

大多数用户场景用单 Assistant 多实例即可。Team 是"加速器"和"跨边界协作器"。

## 编排模式

### 确认的设计：Assistant 自主编排为主

```text
Team（可选·加速器，v0.6+）
  └── 多 Assistant 协作（A2A 协议，跨边界场景）

Assistant（核心编排单元）
  ├── 单实例模式：Actor + 单 Role，串行处理
  └── 多实例模式：同 Actor + 多 Role，并行 fork/join
        ├── Instance-1 (Role-A) → 调度 Agent 池
        ├── Instance-2 (Role-B) → 调度 Agent 池
        └── 主实例聚合结果 + 冲突裁决

Agent（无状态执行单元，池化）
  ├── 被 Assistant 实例按需调度
  ├── 同一 Agent 定义可多实例并发
  └── 执行完归还池，不持有状态
```

### 三个正交维度

| 维度 | 含义 | 选项 |
|------|------|------|
| **运行模式** | 流程由谁驱动 | 编排模式（用户定义流程）/ 自主模式（Assistant 自主协调） |
| **编排对象** | 各层编排什么 | Team 编排 Assistant / Assistant 编排 Agent |
| **执行模式** | 单 Agent 内部如何推理 | ReAct / CoT / Function Calling（内部实现细节） |

### 运行模式：编排 vs 自主

两种模式**不互斥**，可以混合：工作流骨架（编排）+ 节点内自主执行（自主）。

```text
编排模式（确定性，用户/流程设计者驱动）：
  步骤顺序预定义，由工作流引擎（Flowable/DSL/Pipeline）驱动
  可审计、可回退、可监控
  例：需求 → 设计 → 编码 → 审查 → 测试 → 部署

自主模式（动态，Assistant 自主决策驱动）：
  Assistant 根据意图自动协调 Agent，动态规划执行路径
  灵活、适应性强，但需要置信度门控约束
  例：用户说"帮我完成这个需求" → Assistant 自主拆解、调度、聚合
```

### 编排对象：各层编排什么

| 层 | 编排模式（确定性流程） | 自主模式（动态协调） |
|----|---------------------|---------------------|
| **Team** | 编排多个 Assistant（Pipeline/Supervisor） | Leader Assistant 自主分发、仲裁 |
| **Assistant** | 编排 Agent（技能链/工作流节点）+ fork 多实例 | 根据意图自主路由 Skill → 调度 Agent |
| **Agent** | 被编排（作为节点被上层调度） | 自主规划步骤（ReAct 循环） |

### 执行模式：Agent 内部推理

执行模式是 Agent 内部的推理策略，对上层透明：

| 执行模式 | 说明 | 适用场景 |
|---------|------|---------|
| ReAct | Think → Act → Observe → 循环 | 需要工具调用的复杂任务（默认） |
| CoT | 纯推理链，不调用工具 | 分析、规划、评估 |
| Function Calling | 单次工具调用，不循环 | 简单确定性操作 |

AAF 当前统一使用 **ReAct**（AgentScope ReActAgent），上层不感知内部执行模式。

### 混合模式：编排 + 自主

```text
工作流层（编排模式，确定性）：
  固定流程骨架，由 Flowable/DSL 驱动
  例：product → architect → developer → tester → qa

执行层（自主模式，动态性）：
  每个节点内 Agent 自主规划子步骤、选择工具、迭代执行
  例：developer Agent 收到"实现用户模块" → 自主拆分为
      分析需求 → 设计接口 → 写代码 → 跑测试 → 修复
```

设计约束：
- 工作流节点的**进入/退出条件**是确定性的
- Agent 在节点内的**执行过程**是自主的，但受预算和时间约束
- Agent 可以**向上请求**：发现任务超出能力范围时，上报工作流层决策

### 场景选型

| 场景 | 运行模式 | 编排层 | 说明 |
|------|---------|--------|------|
| 日常对话/问答 | 自主 | Assistant 直接回复 | 无需调度 Agent |
| 单一任务（写代码/分析） | 自主 | Assistant → Agent | 意图路由，单 Agent 执行 |
| 多步骤串行（上下文连贯） | 自主 | Assistant + Role 切换 | 同一会话，角色串行 |
| 多角色并行加速 | 自主 | Assistant fork 多实例 | 同 Actor + 多 Role 并行 |
| 固定业务流程（审批/发布/CI） | 编排 | 工作流引擎 → Agent 节点 | Flowable 驱动 |
| 对抗性验证（写+审查） | 自主 | Team（MsgHub 辩论） | 多视角挑战 |
| 跨系统协作 | 编排 | Team + A2A 协议 | 外部 Agent 系统 |

## 渐进决策模型

### 决策粒度

| 层级 | 决策粒度 | 策略 |
|------|----------|------|
| Agent | 粗粒度规划 | 决策树展开：走一步看一步，每步结果作为下一步输入 |
| Assistant | 意图漏斗 | 意图澄清优先于执行，通过最少问题快速收敛 |
| Team | 目标假设性分解 | 目标不清晰不阻塞执行，动态调整 |

### 置信度门控

```text
> 0.9   → 自动执行，结果暂存，异步通知
0.7-0.9 → 展示执行计划，等待确认
< 0.7   → 暂停，说明原因，转人工处理
```

### 可撤销渐进提交

- 执行步骤先进入暂存态
- 用户或上层确认后提交
- 未确认前可回滚

## 智能降级策略

| 场景 | 降级方案 |
|------|----------|
| AI 服务不可用 | 降级到规则引擎（预定义工作流） |
| 知识检索失败 | 使用默认知识库（内置规范文档） |
| Agent 超时 | 切换简化流程（直接 LLM 调用） |
| 沙箱执行失败 | 暂停并转人工处理 |

降级不静默发生，对话区会明确告知用户当前处于降级模式及原因。

## 技术方案与抽象层

每层定义 AAF 自己的接口，实现层依赖具体框架，上层只依赖接口——框架可替换，业务逻辑不变。

```text
层          AAF 接口（稳定）              当前实现（可替换）
────────────────────────────────────────────────────────────
Team        TeamOrchestrator（接口）      DefaultTeamOrchestrator
Assistant   AssistantExecutor            DefaultAssistantExecutor
Agent       AgentExecutor                AgentScopeExecutor（包装 ReActAgent）
Cognition   MemoryPipeline 等已有接口    自实现（AtomMemoryEngine + PgVector + Neo4j）
Core        LlmClient（两种实现）         SpringAiLlmClient / AgentScopeLlmClient
```

**Core 层两种 LLM 封装**：

```text
LlmClient（接口）
  ├── SpringAiLlmClient
  │     包装 Spring AI ChatClient
  │     用于：直接 LLM 调用（对话引擎、记忆提取、重排等）
  └── AgentScopeLlmClient
        包装 AgentScope OpenAIChatModel
        用于：AgentScope ReActAgent 内部的 LLM 调用
        统一接入 AiModel 表的模型配置和 Token 计量
```

## 技术选型

| 能力 | 技术选型 |
|------|----------|
| 智能体编排 | Spring AI + AgentScope |
| 工具协议 | MCP（Model Context Protocol） |
| 多智能体通信 | A2A 协议 |
| 人机交互 | AG-UI 协议 |
| 缓存 | Caffeine（本地）+ Redis（分布式） |
| Checkpoint 存储 | PostgreSQL（JSON 序列化） |


## 包结构设计

三层分工：`engine/` 通用执行能力，`intelligent/core/` 接口契约，`intelligent/*/` 业务语义。编排层（v0.6）只依赖 Core 接口。

```text
com.xuejiai.aaf.framework/
│
├── engine/                        ← 引擎层：通用执行能力（无业务语义）
│   ├── memory/                    原子记忆引擎（AtomMemoryEngine）
│   ├── knowledge/                 知识库引擎（NexusKBEngine/HybridSearch/ECL）
│   ├── tool/                      工具引擎
│   │   ├── ToolRegistry           工具注册表（Spring Bean + MCP 发现）
│   │   ├── ToolCallDispatcher     调用分发（参数校验→执行→结果封装）
│   │   ├── McpToolService         MCP 协议工具发现
│   │   ├── SpringAiToolAdapter    Spring AI ToolCallback 适配
│   │   └── ScriptSandbox          脚本安全执行（扩展 AgentScope，加资源限制）
│   ├── skill/                     技能引擎
│   │   ├── SkillDefinition        @Entity：触发条件 + 绑定 Agent + 指令 + builtIn/version
│   │   ├── SkillDefinitionRepository
│   │   ├── SkillMatchEngine       技能匹配（意图 + 关键词，用户自定义优先于内置）
│   │   ├── BuiltinSkills          枚举：4 个内置技能
│   │   └── BuiltinSkillInitializer ApplicationRunner：启动时 upsert
│   └── runtime/                   运行时基础设施
│       └── checkpoint/            通用 Checkpoint 引擎
│           ├── CheckpointStore    接口：持久化存储
│           ├── CheckpointEntry    快照数据结构（scopeType/scopeId/snapshot）
│           └── CheckpointPolicy   策略（每步/每N步/关键节点）
│
├── intelligent/                   ← 智能层：接口契约 + 业务语义
│   ├── core/                      接口契约层（稳定，零框架依赖）
│   │   ├── agent/AgentExecutor    接口：execute / interrupt / reset / getName
│   │   ├── assistant/AssistantExecutor 接口：chat / forkParallel
│   │   ├── skill/SkillProvider    接口：match / getDefinitions
│   │   ├── skill/SkillDef         Record：纯数据契约
│   │   ├── tool/ToolProvider      接口：getDefinitions / call
│   │   ├── tool/FunctionDefinition Record：name / description / parameters
│   │   ├── memory/MemoryPipeline  接口：execute(PipelineInput) → MemoryContext
│   │   ├── memory/MemoryStrategy  枚举：MEMORY_ONLY / KNOWLEDGE_ONLY / HYBRID / PROCEDURAL_FIRST / FULL
│   │   ├── llm/LlmClient          接口：call / stream
│   │   ├── model/                 AiModel @Entity + 模型管理
│   │   ├── prompt/                PromptTemplate @Entity + 模板引擎
│   │   └── token/                 Token 计量与配额
│   │
│   ├── agent/                     Agent 实现（依赖 engine/tool + engine/skill）
│   │   ├── AgentScopeExecutor     实现 AgentExecutor，包装 ReActAgent
│   │   ├── AgentFactory           返回 AgentExecutor
│   │   ├── AgentDefinition        @Entity + Repository
│   │   ├── AgentRegistryService
│   │   ├── CognitiveCycleExecutor
│   │   └── runtime/               运行时基础设施
│   │       ├── AgentPool          池化复用（借出重置/归还清空）
│   │       ├── AgentSandbox       虚拟线程隔离（依赖 AgentExecutor）
│   │       ├── AgentEventBus      消息路由
│   │       └── AgentCheckpointService 调用 engine/runtime/checkpoint
│   │
│   ├── assistant/                 Assistant 实现（核心编排单元）
│   │   ├── DefaultAssistantExecutor 实现 AssistantExecutor
│   │   ├── AssistantDefinition    @Entity：Actor + Role + MemoryStrategy
│   │   ├── AssistantInstance      运行时实例（Actor引用 + Role引用 + 上下文）
│   │   ├── actor/Actor            @Entity：人格载体（name/persona/systemPrompt/avatar）
│   │   ├── role/Role              @Entity：能力配置（Skill 集 + Tool 白名单）
│   │   ├── SessionManager         会话管理（conversationId → 主实例）
│   │   ├── InputBuffer            输入缓冲区（分类 + 路由）
│   │   ├── TaskBoard              任务看板（子任务状态 + 依赖关系）
│   │   ├── AgentDispatcher        Agent 调度器
│   │   ├── IntentUnderstandingService
│   │   ├── EmotionPerceptionService
│   │   ├── ResultAggregator
│   │   └── AssistantCheckpointService 调用 engine/runtime/checkpoint
│   │
│   ├── cognition/                 认知层（依赖 engine/memory + engine/knowledge）
│   │   ├── memory/                记忆业务语义（ShortTermMemoryService 等）
│   │   ├── pipeline/              MemoryPipeline 各实现 + Factory
│   │   └── retrieval/             UnifiedRetrievalService
│   │
│   ├── learning/                  学习反哺通道（产出按 assistantId/全局分流）
│   │   ├── LearningSkill          内置学习技能触发入口
│   │   ├── LearningPipeline       学习流程
│   │   ├── TrajectoryCollector / EffectEvaluator / ProceduralDistiller
│   │   └── SelfImprovementService / KnowledgeGrowthService / ValueUpdateProposer
│   │
│   ├── team/                      Team 实现（v0.6+）
│   │   ├── DefaultTeamOrchestrator
│   │   ├── GoalTracker            目标级任务管理
│   │   ├── A2AProtocolService / TaskDistributor / ProgressSyncService / ConflictArbitrator
│   │
│   └── ai/                        LLM 封装（两种实现）
│       ├── SpringAiLlmClient      实现 LlmClient（Spring AI ChatClient）
│       ├── AgentScopeLlmClient    实现 LlmClient（AgentScope Model）
│       └── ModelRouter / ChatContextBuilder / AiProperties
│
└── ...（storage/messaging/security/crud 等基础设施）
```

**编排时整合**（v0.6 工作流节点只依赖 Core 接口）：

```text
工作流节点          依赖的 Core 接口        实际执行
AgentNode      →   AgentExecutor      →   engine/tool + engine/skill + intelligent/agent
AssistantNode  →   AssistantExecutor  →   engine/skill（查询）+ intelligent/assistant（调度）
MemoryNode     →   MemoryPipeline     →   engine/memory + engine/knowledge
ToolNode       →   ToolProvider       →   engine/tool
SkillNode      →   SkillProvider      →   engine/skill
LlmNode        →   LlmClient          →   intelligent/ai
```

## 待解决问题

### 预算感知

> Agents need budget-awareness: How do we explain and enforce 5 mins/$10/2M tokens budgets?

### 工具自进化

> Tools should be self-evolving: How can models improve their own tool ergonomics?

### 多智能体通信

> Multi-agents need new ways of communicating: How to expand from synchronous USER:ASSISTANT turns?

## 相关文档

- [元引擎设计](../meta-engine.md) - 基础设施
- [认知层设计](./cognition.md) - Layer 1 Cognition 总览
- [AtomMemory 记忆引擎](../engine/atom-memory.md) - Memory 模块的引擎实现
- [NexusKB 知识引擎](../engine/nexus-knowledge.md) - Knowledge 模块的引擎实现
