---
level: Practice
layer: Model
purpose: 五层智能架构：整体设计、层间关系、编排模式
status: published
version: 4.0.0
date: 2026-05-28
author: AaronZZH
gains:
  - 架构可视化图、关键关系说明、技能/工具分层
  - 编排模式（三个正交维度）、渐进决策模型
  - 技术方案与抽象层、包结构设计
---

# 五层智能架构

> 从内核到协作，分层认知，渐进决策。

## 设计原则

- **分工协作，发挥各自专长**：大模型 + 传统计算 + 人类，各司其职
- **群体智能**：分层多智能体、智能块、智能核
- **智能模块化**：选择合适的模型，专用模型/通用模型专用化
- **复杂环境协同**：多智能体在复杂环境下的协同工作能力
- **自适应多场景**：从易用性、灵活性、扩展性、性能、数据安全、隐私
- **渐进决策**：多模型、分布式存储运算、隐私隔离、加密存储
- **业务与智能融合**：减少具体业务开发，同时保持高效高性能易用
- **可验证性优先**：规划阶段将模糊任务降维为可自动验证的子任务，评估阶段区分"可自动验证"和"需人工审查"
- **能力护栏**：根据任务类型动态限定 Agent 操作范围，限定范围换取信任空间
- **瓶颈迁移意识**：执行近乎免费，规划与审查是新瓶颈——Agent 的核心价值是帮用户规划和审查

### 架构核心约束

- **无状态层可水平扩展**：Core 和 Agent 设计为无状态，支持池化和并发
- **状态集中在 Cognition**：Team / Assistant 只持有轻量会话级状态，数据级状态统一由 Cognition 管理
- **认知循环分层原则**：每层有且只有一个认知循环，粒度从项目级到请求级逐层细化；禁止跨层直接触发
- **私有与共享分离**：Agent 内模块私有；记忆/知识/价值观下沉到 Cognition，多 Agent 共享
- **引擎与业务解耦**：引擎层提供通用能力，业务模块只依赖引擎接口
- **渐进决策模型**：Agent 层决策树展开；Assistant 层意图漏斗收敛；Team 层目标假设性分解
- **决策权跨层流动**：低置信度上报，高置信度本层执行，不固定归属
- **可撤销渐进提交**：执行步骤先暂存，确认后提交，未确认可回滚
- **智能降级策略**：AI 不可用→规则引擎；检索失败→默认知识库；Agent 超时→简化流程
- **知识能力一体**：知识库与工具系统绑定，禁止独立迭代导致语义漂移
- **三层上下文分离**：知识库（静态/全局）/ 记忆库（动态/个体）/ 上下文（临时/会话级）
- **执行结果反哺知识**：工具调用结果自动归档，经评估后更新知识库

## 五层总览

```text
Layer 4  协作层  Team                              【项目级】
         认知循环：目标对齐 → 任务分发 → 进度同步 → 结果聚合 → 冲突仲裁
         由 Leader Assistant（主助理）协调多个 Worker Assistant 协作完成复杂目标
         Team 本身无执行能力，具体执行由各 Assistant 调度 Agent 完成
         状态：轻量会话级状态（任务分配表、进度、仲裁结果），不持有数据级状态

Layer 3  助理层  Assistant                         【会话级】
         认知循环：前注意分流 → 情感感知 → 意图理解 → 上下文构建 → Agent 调度 → 反馈整合 → 记忆更新
         前注意分流：规则+小模型快速路由（<50ms），简单请求不走 Agent
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
         用户理解：被动接收各层事件→异步提炼用户画像/偏好/情感模式
         决策日志：每次 AI 自主推进的决策记录（决策点、选项、理由、置信度、可验证性），支持异步审查
         状态分区：用户私有区 / 全局共享区 / Agent 工作区 / 决策审计区

Layer 0  内核层  Core                              【请求级·无状态】
         认知循环：推理 / 生成 / 上下文窗口管理
         LLM + Context，上下文由调用方（Agent）组装后传入
         无状态，可水平扩展、池化复用
```

## 架构可视化

```text
┌─────────────────────────────────────────────────────────────────────────┐
│  缓存层（应用级，启动时加载 + 变更刷新）                                  │
│  ActorCache / RoleCache / SkillDefCache / AgentDefCache / ModelCache     │
│  策略：本地 Caffeine + Redis 二级缓存，DB 变更时发事件刷新               │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│  Layer 4  Team  协作层                                    【项目级】     │
│  由 Leader Assistant（主助理）协调多个 Worker Assistant 协作              │
│  目标对齐 → 任务分发 → 进度同步 → 结果聚合 → 冲突仲裁                    │
│  任务管理：GoalTracker（目标级，持久化到 DB）                             │
│  内部直接调用；跨系统时走 A2A 协议                                       │
└─────────────────────────────────────────────────────────────────────────┘
                              ↕ 调度/回调
┌─────────────────────────────────────────────────────────────────────────┐
│  Layer 3  Assistant  助理层                               【会话级】     │
│  前注意分流（<50ms）→ 情感感知 → 意图理解 → Skill 匹配 → Agent 调度      │
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
│  │  取消→立即中断 / 修改→重新规划 / 补充→注入上下文 / 无关→排队        │   │
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
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  UnifiedRetrievalService（混合检索，独立组件）                     │   │
│  │  多源并行检索 → 双层 RRF 融合 → LLM 重排 → Value 过滤             │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│         ↑ 被编排调用                    ↑ 可被直接调用                    │
│  ┌──────────────────────┐  ┌─────────────────────────────────────┐     │
│  │  MemoryPipeline      │  │  Personalization（用户理解）         │     │
│  │  读管道：编排检索+组装 │  │  事件接收→异步提炼→画像输出         │     │
│  │  写管道：提取→去重→   │  │  意图+情绪+画像+偏好+端适配         │     │
│  │  写入→遗忘（固定四步） │  │                                    │     │
│  └──────────────────────┘  └─────────────────────────────────────┘     │
│  状态分区：用户私有区 / 全局共享区 / Agent 工作区 / 决策审计区            │
└─────────────────────────────────────────────────────────────────────────┘
                              ↕ 上下文传入 / 结果返回
┌─────────────────────────────────────────────────────────────────────────┐
│  Layer 0  Core  内核层                                    【请求级】     │
│  LLM 推理 / 上下文窗口管理 / Token 预算控制 / 多模型路由                  │
│  FunctionDefinition + ToolProvider 接口契约（工具系统契约层）             │
│  完全无状态，上下文由 Agent 组装后传入，不需要 Checkpoint                 │
└─────────────────────────────────────────────────────────────────────────┘
```

## 关键关系说明

| 关系 | 说明 |
|------|------|
| Agent ↔ Cognition | 水平协作，不是上下级；Agent 执行前拉取 MemoryContext，执行后写回 |
| MemoryPipeline | Cognition 对外暴露的上下文组装接口（查询理解→路由→并行检索→RRF→重排→组装） |
| 混合检索（独立组件） | 记忆+知识库统一检索门面，被 MemoryPipeline 编排调用，也可被搜索引擎等直接调用 |
| MemoryStrategy | Assistant 层配置，决定 MemoryPipeline 使用哪些 Stage（Memory/Knowledge/混合/程序化优先） |
| Learning（横切） | 异步反哺通道，Agent 执行结果→评估→更新 Memory/Knowledge/Value/Skill，不属于 Cognition 内部循环 |
| User → Assistant（1:N） | 一个用户可拥有多个 Assistant，每个有独立人格/技能集/记忆策略 |
| Assistant → Agent（1:N） | 按 Skill 匹配路由调度，Agent 全局注册按能力共享，通过 AgentPool 池化 |
| Team → Assistant（1:N） | 由主助理（Leader）协调多个 Assistant 协作完成复杂目标，各 Assistant 独立调度 Agent 执行 |
| Agent 池化 | AgentScope ReActAgent 借出前重置/归还前清空，对上层透明为无状态 |
| InputBuffer | Assistant 层接收执行期追加输入（取消/修改/补充/无关），Agent 通过 Checkpoint 回调检查 |
| Checkpoint 分层 | Agent 步骤级 + Assistant 会话级 + Team 目标级，各层按需使用通用 Checkpoint 引擎 |
| 置信度门控 | 贯穿 Agent→Assistant→人类，决策权随置信度跨层流动 |

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

## 技能与工具的分层

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

## 层间调用规则

- 上层循环通过调度触发下层循环
- 下层结果通过回调返回上层
- **禁止跨层直接触发**（如 Team 不能直接调用 Core）

## 认知模型映射

> 参考人类认知心理学模型，AAF 五层智能架构的设计灵感来源。

| 认知心理学概念 | AAF 对应组件 | 说明 |
|---------------|-------------|------|
| 刺激输入 | 交互层（安全网关 + 协议适配） | 系统对外边界 |
| 短时感觉存储（前注意） | Assistant 前注意分流 | <50ms 分流决策 |
| 感知 | Assistant（意图理解 + 情感感知） | 理解输入含义 |
| 注意力资源 | Assistant 算力资源池 | Agent 池并发数 + Token 预算 |
| 识别 | 混合检索 `cognition/retrieval` | 从记忆/知识中匹配 |
| 工作记忆（3-7 项） | WorkingMemory + P0-P5 优先级 | Token 窗口有限 |
| 长期记忆 | 记忆引擎 + 知识库引擎 | 持久化认知基础 |
| 决策与响应选择 | Assistant（协调者） | 路径决策 + Agent 选择 |
| 响应执行 | Agent + 工具引擎 | 工具调用 + LLM 推理 |
| 响应 | AG-UI 协议 | SSE 流式输出 |
| 反馈 | 学习反哺 `cognition/learning` | 执行结果反哺 |
| 眼动（注意力调控） | Assistant（MemoryStrategy） | 决定关注哪些来源 |

## 编排模式

### 三个正交维度

| 维度 | 含义 | 选项 |
|------|------|------|
| **运行模式** | 流程由谁驱动 | 编排模式（用户定义）/ 自主模式（Assistant 自主） |
| **编排对象** | 各层编排什么 | Team→Assistant / Assistant→Agent |
| **执行模式** | Agent 内部推理 | ReAct / CoT / Function Calling |

### 编排 vs 自主

两种模式**不互斥**，可混合：工作流骨架（编排）+ 节点内自主执行（自主）。

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

| 层 | 编排模式 | 自主模式 |
|----|---------|---------|
| Team | Pipeline/Supervisor 编排多 Assistant | Leader 自主分发仲裁 |
| Assistant | 技能链/工作流节点 + fork 多实例 | 意图自主路由 Skill → Agent |
| Agent | 被编排（作为节点） | 自主规划步骤（ReAct） |

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

| 场景 | 运行模式 | 编排层 |
|------|---------|--------|
| 日常对话/问答 | 自主 | Assistant 直接回复 |
| 单一任务 | 自主 | Assistant → Agent |
| 多角色并行加速 | 自主 | Assistant fork 多实例 |
| 固定业务流程 | 编排 | 工作流引擎 → Agent 节点 |
| 对抗性验证 | 自主 | Team（MsgHub 辩论） |
| 跨系统协作 | 编排 | Team + A2A 协议 |

## 渐进决策模型

| 层级 | 决策粒度 | 策略 |
|------|----------|------|
| Agent | 粗粒度规划 | 决策树展开：走一步看一步 |
| Assistant | 意图漏斗 | 意图澄清优先于执行 |
| Team | 目标假设性分解 | 目标不清晰不阻塞执行 |

置信度门控：>0.9 自动执行 / 0.7-0.9 确认 / <0.7 转人工。详见 [置信度门控器](core/confidence-gate.md)。

## 技术方案与抽象层

```text
层          AAF 接口（稳定）              当前实现（可替换）
────────────────────────────────────────────────────────────
Team        TeamOrchestrator             DefaultTeamOrchestrator
Assistant   AssistantExecutor            DefaultAssistantExecutor
Agent       AgentExecutor                AgentScopeExecutor（包装 ReActAgent）
Cognition   MemoryPipeline               自实现（AtomMemoryEngine + PgVector + Neo4j）
Core        LlmClient                    SpringAiLlmClient / AgentScopeLlmClient
```

## 技术选型

| 能力 | 技术选型 |
|------|----------|
| 智能体编排 | Spring AI + AgentScope |
| 工具协议 | MCP（Model Context Protocol） |
| 多智能体通信 | 内部直接调用；跨系统走 A2A 协议 |
| 人机交互 | AG-UI 协议 |
| 缓存 | Caffeine + Redis |
| Checkpoint | PostgreSQL（JSON 序列化） |

## 包结构设计

```text
com.xuejiai.aaf.framework/
├── engine/                        ← 引擎层：通用执行能力
│   ├── memory/                    AtomMemoryEngine
│   ├── knowledge/                 NexusKBEngine
│   ├── tool/                      ToolRegistry / ToolCallDispatcher / MCP
│   ├── skill/                     SkillDefinition / SkillMatchEngine
│   └── runtime/checkpoint/        CheckpointStore / CheckpointEntry
│
├── intelligent/                   ← 智能层：接口契约 + 业务语义
│   ├── core/                      接口契约（零框架依赖）
│   ├── agent/                     AgentScopeExecutor / AgentPool / Sandbox
│   ├── assistant/                 DefaultAssistantExecutor / SessionManager / InputBuffer
│   ├── cognition/                 MemoryPipeline / UnifiedRetrieval
│   ├── learning/                  TrajectoryCollector / ProceduralDistiller
│   ├── team/                      TeamOrchestrator / GoalTracker / A2A
│   └── ai/                        SpringAiLlmClient / AgentScopeLlmClient / ModelRouter
```

编排时整合（v0.6 工作流节点只依赖 Core 接口）：

```text
AgentNode      →  AgentExecutor      →  engine/tool + intelligent/agent
AssistantNode  →  AssistantExecutor  →  intelligent/assistant
MemoryNode     →  MemoryPipeline     →  engine/memory + engine/knowledge
ToolNode       →  ToolProvider       →  engine/tool
LlmNode        →  LlmClient          →  intelligent/ai
```

## 待解决问题

- Agents need budget-awareness: How to enforce 5 mins/$10/2M tokens budgets?
- Tools should be self-evolving: How can models improve their own tool ergonomics?
- Multi-agents need new ways of communicating: How to expand from synchronous USER:ASSISTANT turns?

## 数据架构

> 本节定义五层智能架构的数据库表结构与关系。设计原则：**配置与运行态分离、私有与共享分离、持久与临时分离**。
> 实际表定义以 `v2__ai_schema.sql` / `v8__chat_schema.sql` 为准，本节为设计视角的精简描述。

### 总览

```text
┌─────────────────────────────────────────────────────────────────────────┐
│  Layer 4  Team 协作层（配置 + 运行态）                                    │
│  ai_team  ai_team_member  ai_team_task                                  │
└─────────────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────────────┐
│  Layer 3  Assistant 助理层（配置 + 运行态）                               │
│  配置：ai_persona  ai_role  ai_skill_definition  ai_assistant            │
│  运行：conversation  ai_chat_task  ai_task_execution  ai_task_checkpoint │
└─────────────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────────────┐
│  Layer 2  Agent 智能体层（配置）                                          │
│  ai_agent_definition                                                    │
│  ai_tool_catalog  ai_action_catalog  ai_mcp_server                     │
└─────────────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────────────┐
│  Layer 1  Cognition 认知基础层（跨会话持久）                               │
│  ai_memory_atom  ai_memory_relation                                    │
│  ai_knowledge_base  ai_knowledge_document  ai_knowledge_chunk           │
│  ai_knowledge_embedding  ai_value_rule  ai_decision_log                │
└─────────────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────────────┐
│  Layer 0  Core 内核层（模型配置 + 计量）                                  │
│  ai_model_provider  ai_model  ai_model_preference  ai_prompt_template  │
│  ai_token_usage                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 模型层（Layer 0）

#### ai_model_provider — 供应商配置

统一维护同一供应商的 baseUrl 与 API Key。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| provider_code | VARCHAR(64) UNIQUE | aliyun / volcengine / deepseek 等 |
| provider_name | VARCHAR(128) | 显示名称 |
| provider_type | VARCHAR(32) | OPENAI_COMPAT / ANTHROPIC / OLLAMA |
| base_url | VARCHAR(512) | API 端点地址 |
| api_key_encrypted | VARCHAR(1024) | 加密存储 |
| enabled | BOOLEAN | |

#### ai_model — 模型配置

LLM 模型接入配置，支持多协议类型。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| model_id | VARCHAR(128) UNIQUE | 逻辑标识，如 `openai:gpt-4o` |
| display_name | VARCHAR(128) | 显示名称 |
| provider_id | BIGINT FK→ai_model_provider | 供应商配置 |
| provider_type | VARCHAR(32) | OPENAI_COMPAT / ANTHROPIC / OLLAMA |
| model_name | VARCHAR(128) | 实际模型名 |
| base_url | VARCHAR(512) | 覆盖供应商地址（可为空） |
| api_key_encrypted | VARCHAR(1024) | 覆盖供应商 Key（可为空） |
| capabilities | VARCHAR(256) | CHAT,VISION,EMBEDDING,IMAGE_GEN 等 |
| context_window | INTEGER | 上下文窗口大小（tokens） |
| enabled | BOOLEAN | |

#### ai_model_preference — 模型偏好

按 scope（USER/SYSTEM）× capability 存储有序降级模型列表。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| scope | VARCHAR(16) | USER / SYSTEM |
| scope_id | BIGINT | 用户ID（scope=USER时），SYSTEM时为null |
| capability | VARCHAR(32) | CHAT / EMBEDDING / IMAGE_GEN 等 |
| model_ids | JSONB | 有序降级列表，如 `["n1n:gpt-4o","openai:gpt-4o"]` |

#### ai_prompt_template — Prompt 模板

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| name | VARCHAR(128) | 模板名称（+version 构成唯一约束） |
| template_version | INTEGER | 版本号 |
| content | TEXT | 模板内容（支持变量占位） |
| variables | TEXT | 变量说明 |
| category | VARCHAR(64) | 分类 |

### 五层智能配置（Layer 2-4）

#### ai_persona — 人格模板（Layer 3）

Actor 人格配置，可被多个 Assistant 复用。通过 `ai_assistant.persona_id` FK 引用，不复制字段。

用户基于公共模板定制时，复制一条记录并设置 `owner_id`，Assistant 改指向新记录——改模板不影响存量 Assistant，改私有人格也不影响其他用户。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| name | VARCHAR(128) | 人格名称 |
| persona | TEXT | 人格描述 |
| system_prompt | TEXT | 系统提示词 |
| avatar_url | VARCHAR(512) | 头像 |
| status | VARCHAR(16) | active / inactive |
| owner_id | BIGINT | **NULL=系统公共模板**（所有用户可选）；**有值=用户私有人格**（仅所属用户可用） |

#### ai_agent_definition — Agent 定义（Layer 2）

Agent 静态定义，运行时由 AgentPool 实例化。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| agent_id | VARCHAR(64) UNIQUE | Agent 业务标识 |
| name | VARCHAR(128) | |
| system_prompt | TEXT | |
| model_id | BIGINT FK→ai_model | 绑定模型（NULL则走路由） |
| tools | TEXT | 工具列表（逗号分隔） |
| allowed_tools | TEXT | 工具白名单 |
| mcp_servers | TEXT | MCP 服务器列表 |
| max_iterations | INTEGER | ReAct 最大循环次数 |
| memory_config | JSONB | 记忆配置 |
| status | VARCHAR(16) | active / inactive |

#### ai_skill_definition — 技能定义（Layer 3）

Skill = Assistant→Agent 的路由规则：意图匹配后绑定 Agent + 系统 Prompt。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| assistant_id | BIGINT FK→ai_assistant | 所属 Assistant（NULL=全局技能） |
| name | VARCHAR(128) | |
| trigger_intent | TEXT | 触发条件描述 |
| agent_id | BIGINT FK→ai_agent_definition | 路由到的 Agent |
| system_prompt | TEXT | 技能专属系统 Prompt |
| priority | INTEGER | 匹配优先级 |
| is_global | BOOLEAN | 是否全局技能 |
| status | VARCHAR(16) | |

#### ai_role — 能力配置（Layer 3）

Role = Assistant 的能力集合：Skill 引用 + Tool 白名单。**注意：与 ai_assistant 存在循环 FK，建表顺序处理**。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| assistant_id | BIGINT FK→ai_assistant | 所属 Assistant |
| name | VARCHAR(128) | |
| skill_ids | TEXT | 技能 ID 列表（逗号分隔，逻辑引用） |
| tool_whitelist | TEXT | 工具白名单 |
| status | VARCHAR(16) | |

#### ai_assistant — Assistant 配置（Layer 3）

Assistant = Persona + default Role + MemoryStrategy 的组合。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| user_id | BIGINT | 所属用户 |
| persona_id | BIGINT FK→ai_persona | 人格模板 |
| default_role_id | BIGINT FK→ai_role | 默认角色 |
| memory_strategy | VARCHAR(32) | HYBRID / MEMORY_ONLY / KNOWLEDGE_ONLY 等 |
| knowledge_base_id | BIGINT | 绑定知识库（逻辑引用） |
| status | VARCHAR(16) | |

#### ai_team — 多智能体团队（Layer 4）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| name | VARCHAR(128) | |
| collaboration_mode | VARCHAR(32) | LEADER_COORDINATED 等 |
| coordinator_assistant_id | BIGINT FK→ai_assistant | Leader Assistant |
| status | VARCHAR(16) | |

#### ai_team_member — 团队成员

| 字段 | 类型 | 说明 |
|------|------|------|
| team_id | BIGINT FK→ai_team | |
| assistant_id | BIGINT FK→ai_assistant | |
| role | VARCHAR(32) | leader / member |
| capabilities | TEXT | 能力描述 |

#### ai_team_task — 团队任务（Layer 4）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| team_id | BIGINT FK→ai_team | |
| task_id | VARCHAR(64) UNIQUE | 业务标识 |
| parent_task_id | VARCHAR(64) | 父任务（逻辑自引用，树形） |
| assignee_id | BIGINT FK→ai_assistant | 执行者 |
| description | TEXT | |
| status | VARCHAR(16) | PENDING / RUNNING / DONE / FAILED |
| dependencies | TEXT | 依赖任务 ID 列表 |
| result | TEXT | 执行结果 |

### 会话与执行（Layer 3，运行态）

#### conversation — 统一会话

统一 AI / 客服 / IM 三类会话，每个对话对应一个主 Assistant 实例。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| type | VARCHAR(16) | AI / LIVECHAT / IM |
| status | VARCHAR(16) | ACTIVE / ARCHIVED / CLOSED 等 |
| creator_id | BIGINT | |
| assistant_id | BIGINT FK→ai_assistant | AI 对话快捷字段 |
| thread_id | VARCHAR(64) | AG-UI threadId |
| knowledge_base_id | BIGINT | 直连知识库（逻辑引用） |
| channel_extension | JSONB | 客服渠道扩展信息 |

#### conversation_participant — 参与方

支持 HUMAN / ASSISTANT / AGENT / STAFF / BOT 动态进出。

| 字段 | 类型 | 说明 |
|------|------|------|
| conversation_id | BIGINT FK→conversation | |
| participant_id | BIGINT | 参与方业务 ID |
| participant_type | VARCHAR(16) | HUMAN / ASSISTANT / AGENT / STAFF / BOT |
| role | VARCHAR(16) | OWNER / MEMBER / OBSERVER |
| left_at | TIMESTAMP | NULL=仍在会话中 |

#### conversation_message — 消息

统一三类消息，sender 支持任意参与方。

| 字段 | 类型 | 说明 |
|------|------|------|
| conversation_id | BIGINT FK→conversation | |
| sender_id | BIGINT | 发送方业务 ID |
| sender_type | VARCHAR(16) | HUMAN / ASSISTANT / AGENT / STAFF / BOT |
| role | VARCHAR(20) | user / assistant / system / tool |
| content | TEXT | |
| content_type | VARCHAR(20) | TEXT / IMAGE / FILE / TOOL_CALL / TOOL_RESULT |
| payload | JSONB | 结构化内容（工具调用参数等） |

#### ai_chat_task — 对话任务

用户在会话中提交的任务队列，助理按优先级逐个执行。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| session_id | BIGINT | 关联会话 |
| title | VARCHAR(500) | |
| status | VARCHAR(20) | PENDING / RUNNING / DONE / FAILED / CANCELLED |
| scheduled_at | TIMESTAMP | 定时执行时间，NULL=立即 |
| result | TEXT | |

#### ai_task_execution — 执行实例

一个 ChatTask 可多次执行（重试），支持主/子关系。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| task_id | BIGINT | 关联 ai_chat_task（逻辑引用） |
| parent_execution_id | BIGINT | 父执行实例（NULL=主执行） |
| subtask_key | VARCHAR(100) | 子任务标识，如 backend/frontend |
| attempt_no | INTEGER | 重试序号 |
| status | VARCHAR(20) | PENDING / RUNNING / DONE / FAILED / WAITING_APPROVAL |
| role | VARCHAR(100) | 执行角色 |

#### ai_task_checkpoint — 执行检查点

支持从最近检查点恢复，对应 Agent 步骤级 + Assistant 会话级。

| 字段 | 类型 | 说明 |
|------|------|------|
| execution_id | BIGINT | 关联执行实例 |
| scope | VARCHAR(20) | coordinator / subtask / agent_step |
| step_index | INTEGER | 步骤序号 |
| state_json | JSONB | 状态快照（TaskBoard / 步骤进度 / 工作记忆） |

#### ai_task_event — 任务事件

append-only 审计流水，支持 SSE 实时推送。

| 字段 | 类型 | 说明 |
|------|------|------|
| task_id | BIGINT | |
| execution_id | BIGINT | |
| type | VARCHAR(50) | task_started / step_completed / tool_called 等 |
| payload_json | JSONB | 事件数据 |

#### ai_decision_log — AI 决策日志

AI 自主决策的审计记录，支持异步人工审查。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| scope_type | VARCHAR(16) | AGENT / ASSISTANT / TEAM |
| scope_id | VARCHAR(128) | 执行实例 ID（逻辑引用） |
| decision_point | TEXT | 决策节点描述 |
| options | JSONB | 备选方案：[{option, pros, cons}] |
| chosen_option | TEXT | 选中方案 |
| confidence | DOUBLE | 置信度（0.0~1.0） |
| review_status | VARCHAR(16) | PENDING / APPROVED / REJECTED |

### 认知层（Layer 1，Cognition）

#### ai_memory_atom — 记忆原子

用户私有区，短期/长期/情景/程序化记忆，双时态设计（事件时间 + 写入时间）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID PK | |
| user_id | BIGINT | 用户私有，严格隔离 |
| scope | VARCHAR(20) | short_term / long_term / episodic / procedural |
| content | TEXT | |
| embedding | vector(1536) | PgVector 向量索引 |
| event_time | TIMESTAMPTZ | 事件发生时间（双时态） |
| valid_from/valid_to | TIMESTAMPTZ | 有效期 |
| weight | DOUBLE | 重要性权重（遗忘策略） |
| tags | TEXT[] | |

#### ai_memory_relation — 记忆关系

记忆原子间关系图（用于 BundleSearch 图路由），与 Neo4j MemoryEntity 互补。

| 字段 | 类型 | 说明 |
|------|------|------|
| source_id | UUID FK→ai_memory_atom | |
| target_id | UUID FK→ai_memory_atom | |
| relation_type | VARCHAR(50) | |
| weight | DOUBLE | |
| edge_text | TEXT | 关系描述 |
| edge_embedding | vector(1536) | 边向量 |

#### ai_knowledge_base — 知识库

全局共享区，支持多知识库隔离。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| name | VARCHAR(200) | |
| embedding_model | VARCHAR(100) | 向量化模型 |
| chunk_strategy | VARCHAR(50) | 分块策略 |
| status | INTEGER | |

#### ai_knowledge_document — 知识文档

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| knowledge_base_id | BIGINT FK | |
| title | VARCHAR(500) | |
| file_type | VARCHAR(50) | |
| status | INTEGER | 0=待处理 / 已分块 / 已索引 / 失败 |
| chunk_count | INTEGER | |

#### ai_knowledge_chunk — 知识分块

检索最小单元，对应 Neo4j KnowledgeEntity 节点。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| document_id | BIGINT FK | |
| knowledge_base_id | BIGINT FK | |
| content | TEXT | |
| chunk_index | INTEGER | 在文档中的位置 |
| metadata | JSONB | {headings, page, tags} |

#### ai_knowledge_embedding — 知识向量

兼容 Spring AI VectorStore，独立存储向量与关联。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID PK | |
| embedding | vector(1536) | HNSW 索引 |
| chunk_id | BIGINT FK→ai_knowledge_chunk | |
| knowledge_base_id | BIGINT FK | |
| model_name | VARCHAR(50) | 向量化模型 |

#### ai_value_rule — 价值观规则

全局共享区，Agent 执行前 Value 过滤。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| name | VARCHAR(128) | |
| rule_type | VARCHAR(32) | FORBIDDEN / REQUIRED / PREFERRED |
| condition | TEXT | 规则条件（用于 LLM 判断） |
| priority | INTEGER | |
| scope | VARCHAR(16) | GLOBAL / TENANT |

### 工具与计量

#### ai_tool_catalog — 工具目录

ToolCallback / MCP 工具注册，ToolRegistry 的持久化存储。

| 字段 | 类型 | 说明 |
|------|------|------|
| tool_name | VARCHAR(120) UNIQUE | Function Calling 中的 name |
| source | VARCHAR(32) | LOCAL / MCP / CUSTOM |
| tool_type | VARCHAR(32) | FUNCTION / MCP / HTTP / WORKFLOW / AGENT |
| risk_level | VARCHAR(16) | NONE / LOW / MEDIUM / HIGH |
| require_confirm | BOOLEAN | 是否需要人工确认 |
| input_schema / output_schema | TEXT | JSON Schema |

#### ai_action_catalog — 业务动作目录

AI 可调用的业务能力边界，与 ai_tool_catalog 正交（工具=技术原子；动作=业务语义）。

| 字段 | 类型 | 说明 |
|------|------|------|
| action_key | VARCHAR(120) | query / create / update 等 |
| entity_slug | VARCHAR(120) | 实体标识，如 system-role |
| risk_level | VARCHAR(16) | low / medium / high |
| require_confirm | BOOLEAN | |
| permission_code_override | VARCHAR(120) | 覆写权限码 |

#### ai_mcp_server — MCP 服务器

| 字段 | 类型 | 说明 |
|------|------|------|
| name | VARCHAR(128) UNIQUE | |
| url | VARCHAR(512) | |
| transport | VARCHAR(16) | HTTP / SSE / STDIO |
| status | VARCHAR(16) | connected / disconnected / error |

#### ai_token_usage — Token 计量

| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | BIGINT | |
| conversation_id | BIGINT FK→conversation | |
| model_id | BIGINT FK→ai_model | |
| prompt_tokens / completion_tokens / total_tokens | BIGINT | |
| usage_id | VARCHAR(64) UNIQUE | 关联 credit_transaction |

### 实体关系总览

```text
【模型层】
ai_model_provider ──(1:N FK)──→ ai_model ←──(FK)── ai_agent_definition
                                ai_model ←──(FK)── ai_token_usage
ai_model_preference（独立，逻辑引用 ai_model）
ai_prompt_template（独立）

【五层智能配置】
ai_persona ←──(FK)──── ai_assistant.persona_id
ai_role    ←──(FK)──── ai_assistant.default_role_id
ai_role.assistant_id ──(FK)──→ ai_assistant        ← 循环FK，建表顺序处理
ai_role.skill_ids (TEXT) ──逻辑──→ ai_skill_definition
ai_skill_definition.agent_id ──(FK)──→ ai_agent_definition
ai_assistant ←──(FK)──── ai_team.coordinator_assistant_id
ai_team ──(1:N FK)──→ ai_team_member.assistant_id ──(FK)──→ ai_assistant
ai_team ──(1:N FK)──→ ai_team_task.assignee_id ──(FK)──→ ai_assistant
ai_team_task.parent_task_id ──逻辑自引用──→ ai_team_task

【会话与执行】
conversation ──(1:N FK)──→ conversation_participant
conversation ──(1:N FK)──→ conversation_message
conversation.assistant_id ──(FK)──→ ai_assistant
ai_chat_task ──(1:N 逻辑)──→ ai_task_execution
ai_task_execution.parent_execution_id ──逻辑自引用──→ ai_task_execution
ai_task_execution ──(1:N 逻辑)──→ ai_task_checkpoint
ai_task_execution ──(1:N 逻辑)──→ ai_task_event
ai_token_usage.conversation_id ──(FK)──→ conversation

【认知层 Cognition】
ai_memory_atom ──(自关联 N:M FK)──→ ai_memory_relation
ai_knowledge_base ──(1:N FK)──→ ai_knowledge_document
ai_knowledge_document ──(1:N FK)──→ ai_knowledge_chunk
ai_knowledge_chunk ──(1:N FK)──→ ai_knowledge_embedding
ai_value_rule（独立）
ai_decision_log.scope_id ──逻辑──→ ai_task_execution

【工具与计量】
ai_mcp_server ──逻辑──→ ai_tool_catalog（source=MCP时）
ai_tool_catalog / ai_action_catalog（独立目录）
```

### Neo4j 整合点

PostgreSQL 是 source of truth，Neo4j 承担关系遍历查询（写入成功后异步 Spring Event 同步，幂等 MERGE）。

| Neo4j 节点/关系 | 对应 PG 表 | 桥接字段 | 用途 |
|------|------|------|------|
| `MemoryEntity` + `RELATES_TO` | `ai_memory_atom` + `ai_memory_relation` | `GraphMemoryNode.userId` = `ai_memory_atom.user_id` | PG 做向量检索，Neo4j 做实体关系遍历 |
| `KnowledgeEntity` + `RELATES_TO` | `ai_knowledge_chunk` / `ai_knowledge_document` | `KnowledgeEntity.sourceDocumentId` = `ai_knowledge_document.id` | PG 做向量检索，Neo4j 做知识图谱多跳 |
| `AgentNode` + `INVOKED` | `ai_task_execution` | `AgentGraphNode.agentId` = `ai_agent_definition.agent_id` | Agent 协作调用拓扑 |
| `AutodevDoc` | `autodev_doc`（v4） | `AutodevDocNode.docId` = PG 主键 | 文档引用依赖图 |

待补充整合（未实现）：

- **Skill→Agent 路由图**（v0.4）：`ai_role.skill_ids` 当前为 TEXT，建议同步 `(:Assistant)-[:HAS_ROLE]->(:Role)-[:INCLUDES_SKILL]->(:Skill)-[:ROUTES_TO]->(:Agent)`
- **决策链路图**（v0.4）：`(:Task)-[:TRIGGERED]->(:Decision)-[:CHOSE]->(:Action)`，配合 `ai_decision_log`
- **Memory ↔ Knowledge 交叉引用**（v0.5）：`(:MemoryEntity)-[:REFERENCES]->(:KnowledgeEntity)`

### 与架构约束的映射

| 架构约束 | 表级实现 |
|---------|---------|
| 无状态层可水平扩展（Core/Agent） | Agent 无持久化主表，状态外化到 `ai_task_checkpoint` |
| 状态集中在 Cognition | 用户数据统一在 `ai_memory_atom` / `ai_knowledge_*` |
| 私有与共享分离 | `ai_memory_atom`（user_id 严格隔离）vs `ai_knowledge_base`（共享） |
| 三层上下文分离 | 知识库（`ai_knowledge_*`）/ 记忆（`ai_memory_atom`）/ 上下文（`conversation` + `conversation_message`） |
| 执行结果反哺知识 | `ai_decision_log` + `ai_task_event`（append-only 溯源） |
| 配置缓存刷新 | `ai_persona/ai_role/ai_skill_definition/ai_agent_definition/ai_model` 变更 → Spring Event → 缓存失效 |

---

## 各层详细设计

| 层 | 详细文档 |
|----|---------|
| Layer 0 Core | [Core 技术方案](core/core-tech.md) |
| Layer 1 Cognition | [Cognition 层设计](cognition/cognition.md) |
| Layer 2 Agent | [Agent 技术方案](agent/agent-tech.md) |
| Layer 3 Assistant | [Assistant 技术方案](assistant/assistant-tech.md) |
| Layer 4 Team | [Team 技术方案](team/team-tech.md) |

## 相关文档

- [元引擎设计](../engine/meta/meta-engine.md)
- [认知层设计](cognition/cognition.md)
- [AtomMemory 记忆引擎](../engine/data-knowledge/atom-memory.md)
- [NexusKB 知识引擎](../engine/data-knowledge/nexus-knowledge.md)
- [置信度门控器](core/confidence-gate.md)
- [用户感知与个性化](cognition/personalization.md)
