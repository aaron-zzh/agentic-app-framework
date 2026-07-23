---
level: Practice
layer: Product
purpose: 将五层智能架构 v2 和 AgentScope v2 适配方案拆解为可执行、可验证的开发阶段
status: draft
version: 1.1.0
date: 2026-07-23
author: AaronZZH
dependencies:
  - ./architecture-v2.md
related:
  - ../../../explanation/general-agent/general-agent-migration-design.md
  - ../../../explanation/general-agent/general-agent-delivery-roadmap.md
scope:
  includes:
    - P0-P6 开发阶段
    - 任务依赖与退出标准
    - 测试、迁移和删除门禁
  excludes:
    - 正式用户故事编号
    - 代码实现
    - 生产部署排期
gains:
  - 能按单一路径完成智能层 v2 迁移
  - 能验证状态、权限、计量、记忆和恢复不变量
---

# 五层智能架构 v2 开发计划

> 本文是 [五层智能架构 v2](architecture-v2.md) 的实施计划。它定义开发顺序和退出标准，不替代正式迭代中的 Story、技术任务和人类评审。

## 目标与不可变约束

### 交付目标

在 `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent` 内形成以 Assistant、Team、Agent、Cognition、Core 为核心的领域结构，并通过唯一的 AgentScope v2 基础设施适配器完成推理、工具、状态、沙箱和 middleware 接线。首批产品能力是系统内置内容创作助理与系统内置客服助理。

最终运行链路只有一条：

```text
交互入口
→ Assistant 应用用例
→ Assistant/Team 编排
→ AgentExecutionPort
→ infrastructure.agentscope
→ AgentScope HarnessAgent
→ AAF 事件、记忆、权限、计量和持久化端口
```

### 架构不变量

- 五层领域模型不依赖 AgentScope；只有 `intelligent.infrastructure.agentscope` 可直接 import `io.agentscope.*`。
- 不创建 legacy adapter、fallback、双路径、双写或“临时保留”的第二套 runtime。
- Assistant 是唯一面向用户的智能入口；内容创作和客服是两个系统内置 `AssistantDefinition`，不是两个硬编码 Agent 工厂。
- 问答、协作、委托、自动化是显式用户控制模式，与内部编排/自主正交；模型不得静默提高自主权。
- Task 生命周期和 CompletionValidator 从首个可写场景起生效；模型回合结束不等于业务任务完成。
- 所有调用显式携带 `tenantId`；AgentState、记忆、资料、授权、事件和凭证均在 tenant namespace 内隔离。
- Assistant 默认启用长期记忆；长期记忆唯一真理源是 Cognition。Agent 与 Core 不持有长期记忆，`MEMORY.md` 默认关闭或仅作可重建缓存。
- 用户可见消息归 `conversation_message`；Agent 执行快照归 Redis `AgentStateStore`；TaskBoard/Goal 归 PostgreSQL；三者不互相充当备份真理源。
- `ai_task_event` 是唯一执行轨迹事实源；`ai_execution_run` / `ai_execution_step` 若保留，只能是异步可重建投影。
- 同一 conversation 全局串行、不同 conversation 并行；多副本通过 distributed lease + fencing token 保证，sticky session 仅作优化。
- 所有外部副作用工具支持稳定幂等键；重试、恢复和副本接管不得重复扣费或重复执行动作。
- JPA、Spring Data、`JdbcTemplate` 等持久化实现不得进入纯领域包；应用层只依赖端口。

### 目标包与关键端口

```text
com.xuejiai.aaf.framework.intelligent
├── assistant/{model,application,port}
├── team/{model,application,port}
├── agent/{model,application,port}
├── cognition/{model,application,port}
├── core/{inference,capability,port}
├── shared/{id,event}
└── infrastructure/agentscope
    ├── execution
    ├── compiler
    ├── mapping
    ├── middleware
    ├── tool
    ├── state
    ├── sandbox
    └── spring
```

第一批稳定端口至少包括：

| 端口 | 责任 | 不得泄漏的类型 |
|---|---|---|
| `AssistantDefinitionPort` | 读取、版本化内置和用户助理定义 | JPA Entity、Repository |
| `AgentDefinitionPort` | 读取可执行 Agent 规格 | AgentScope Builder、Agent |
| `AgentExecutionPort` | 流式执行、取消、恢复 Agent | `HarnessAgent`、`Msg`、`RuntimeContext` |
| `MemoryRecallPort` / `MemoryWritePort` | Cognition 回忆与治理后写入 | AgentScope memory 文件类型 |
| `ToolCatalogPort` / `ToolInvocationPort` | 工具发现、授权、幂等调用 | `Toolkit`、MCP SDK 类型 |
| `PermissionDecisionPort` | 风险分级、审批、恢复 | Web/Spring Security 类型 |
| `ExecutionEventStorePort` | append-only 写入 `ai_task_event` | AgentScope Event |
| `ConversationLeasePort` | lease、续约、fencing 校验与释放 | Redisson/Redis 客户端类型 |
| `ConversationMessagePort` | 持久化用户可见消息 | AgentState context |
| `MeteringPort` | 预检、用量、结算和幂等扣减 | Model SDK 类型 |
| `TaskControlPort` | 生命周期、责任主体、检查点、暂停与人工接管 | AgentState、Web DTO |
| `EffectiveContextPort` | 生成生效规则/资料/知识/记忆的引用清单 | 检索实现、知识正文 |
| `AuthorizationGrantPort` | 动作/对象/范围/时效/条件授权与撤销 | Spring Security、连接器 SDK |
| `ConnectorActionPort` | 以业务动作和凭证句柄调用连接器 | OAuth token、MCP SDK 类型 |
| `DelegatedTaskPort` / `NotificationPort` | 持久后台调度、通知和人工接管 | 调度器、渠道 SDK 类型 |
| `AutomationDefinitionPort` | 版本化模板、试运行、触发和启停 | Flowable/Quartz 实现类型 |

持久化实现可放在 `com.xuejiai.aaf.framework.persistence.intelligent`，或由上层业务模块实现后注入；实现位置必须服从 Maven 模块依赖方向。

## 阶段与依赖

### 阶段总览

| 阶段 | 主题 | 依赖 | 核心产物 | 阶段退出后可用能力 |
|---|---|---|---|---|
| P0 | 版本与架构护栏 | 架构文档已评审 | 版本 ADR、契约测试、ArchUnit、事件契约 | 可以安全编码，不再猜 AgentScope API |
| P1 | Core + Agent 最小闭环 | P0 | 纯 AgentSpec、AgentExecutionPort、v2 适配器 | 单 Agent 可流式执行、取消和恢复 |
| P2 | 可信对话 + 内置助理 | P1 | Assistant 用例、用户控制模式、任务生命周期、两个内置定义 | 问答和协作任务可见、可停、不会误判完成 |
| P3 | 引导任务 + Cognition/治理 | P2 | 有效上下文、细粒度授权、HITL、连接器、计量和轨迹 | 单助理任务可恢复、可解释、可授权和撤销 |
| P4 | 受控委托 + 多副本恢复 | P3 | 后台契约、预算/期限/通知/接管、DAG、lease/fencing | 条件完备的复杂任务可离线执行且跨副本一致 |
| P5 | 规模化自动化 + Team/A2A | P4 | 模板/触发/发布治理、Team 分派、聚合和仲裁 | 经试运行的自动化与多 Assistant 协作 |
| P6 | 原子切换与清理 | P1-P5 | 旧路径删除、数据切换、全量门禁 | 生产只剩 v2 单一路径 |

阶段必须顺序通过退出标准。允许在 P1 后并行预研 P2/P3，但不得绕过 P0 版本门禁，也不得在 P6 前把新旧路径同时作为生产实现。

### 交付策略

- 每阶段先补契约和失败测试，再写实现；新增端口必须同时给出唯一生产实现和测试替身。
- 被新实现替代的旧责任在同阶段停止接线，最晚 P6 删除源码。不得以“灰度”为由长期双注册 runtime Bean 或双写事实表。
- 数据库变更优先采用“先加新约束/新字段 → 数据校验 → 原子切换 writer → 删除旧 writer/字段”的迁移；中间状态不开放两套事实写入者。
- 回滚按完整版本回滚，不靠运行时 fallback：破坏性删除前保留数据库备份和可逆迁移窗口；一旦执行 P6 删除，不再支持回到旧 API。
- P0、P4、P6 是人工确认门：分别确认 AgentScope 版本、分布式一致性方案、原子切换和数据删除。

## P0-P6 实施工作

### 基础阶段：P0-P1

**P0：版本与架构护栏**

目标：锁定可验证的 AgentScope v2 契约，建立任何业务代码之前都会失败的边界检查。

主要工作：

- 在 `2.0.0-RC4` 与经评审的升级版本之间做一次明确选择，并把 BOM、参考源码、Javadoc/源码标签和测试依赖对齐到同一版本。
- 编写 AgentScope characterization tests，覆盖：同实例并发不同状态槽位、同槽位串行、RuntimeContext 不持久化、AgentStateStore 自动加载/保存、middleware 顺序、取消、异常和流结束语义。
- 定义 AAF 稳定 ID：assistantId、agentId、conversationId/sessionId、taskId、executionId、runId、eventId、correlationId、causationId 与 idempotencyKey。
- 定义 `ExecutionEvent` 枚举、payload 脱敏规则、`UNIQUE(event_id)` 和 `UNIQUE(execution_id, sequence)`。
- 添加 ArchUnit 规则：五层领域包禁止 AgentScope、Spring、JPA、JdbcTemplate；AgentScope import 仅允许出现在唯一适配器目录。
- 建立旧类、旧 Bean、旧 endpoint、旧表 writer 和 POM exclude 清单，冻结新增旧路径代码。

产物：版本 ADR、契约测试套件、架构边界测试、事件 schema、ID 规范、迁移基线清单。

退出标准：

- 人工确认唯一 AgentScope 版本，BOM 与参考源码一致。
- characterization tests 在该版本全绿；关键 API 不再来自 Snapshot 猜测。
- ArchUnit 能对故意添加的非法 import 失败，并在当前目标骨架上通过。
- `ExecutionEvent`、状态所有权和删除清单完成评审。

**P1：Core + Agent 最小闭环**

目标：用纯 AAF 契约完成“定义 → 编译 → 执行 → 流式事件 → 状态恢复”的单 Agent 闭环。

主要工作：

- 建立纯领域 `AgentSpec`、`ModelSpec`、`ToolRef`、`ExecutionPolicy`、`AgentExecutionCommand`、`InvocationContext` 和 `ExecutionEvent`。
- 实现 `AgentExecutionPort`，业务返回 AAF 事件流，不返回 `ReActAgent`、`HarnessAgent` 或 AgentScope `Msg`。
- 实现 `AgentScopeSpecCompiler`：DB/领域规格编译为按 `definitionId + version` 缓存的无状态 HarnessAgent。
- 实现 AAF ↔ AgentScope 的消息、RuntimeContext、结果、异常、取消和事件映射。
- 接入 Redis `AgentStateStore`；`InvocationContext` 必须携带 tenantId，适配器通过 tenant store prefix 或规范化 state user key 映射 AgentScope 的 `(userId, sessionId)` 槽位，不使用 ThreadLocal fallback。
- 完成最小工具调用、流式响应、取消和进程重启恢复。
- 停止旧 `AgentRuntime` / Factory 作为新功能入口；仍被历史调用方使用的责任记录到 P2/P6，不建立 adapter。

测试与退出标准：

- 领域/应用单测不加载 Spring，不出现 AgentScope 类型。
- 编译缓存按定义版本命中，定义更新后新调用使用新版本，运行中任务不被突变。
- 同 Agent 实例并发两个 session 无串话；同 session 的并发轮次有确定顺序。
- Redis 故障、模型异常、取消和重启场景均产生完整终态事件；无悬挂执行。
- 静态扫描确认新增代码中没有 `AafContextHolder`、`AgentCapabilityContext` 或直接 `JdbcTemplate` middleware。

### 产品闭环：P2-P3

**P2：可信对话与两个内置助理**

目标：建立唯一 Assistant 入口，让普通用户在问答和协作模式中看懂状态、资料与动作，并能停止或接管第一个可写任务。

主要工作：

- 建立 AssistantDefinition、Actor、Role、MemoryStrategy、SkillRoute、ToolPolicy、AssistantVersion 和面向用户的 AssistantCapabilityManifest。
- 实现 Assistant 应用用例：加载定义、前注意分流、技能路由、创建/恢复任务、调用 AgentExecutionPort、整合结果。
- 建立 READ_ONLY / COLLABORATIVE 两种首期用户控制模式；模式切换显式记录，READ_ONLY 禁止外部写入。
- 实现 DRAFT、PLANNING、AWAITING_AUTHORIZATION、RUNNING、VERIFYING、COMPLETED、AWAITING_INPUT、PAUSED、CANCELED、FAILED 的任务生命周期及用户状态投影。
- 从第一个可写场景接入最小 CompletionValidator，区分模型回合结束和业务目标完成，并支持继续修复、需要用户、失败和转人工。
- 生成有效上下文清单，展示本次实际使用的规则、Skill、任务资料、知识和记忆引用及选用原因。
- 实现系统模板安装与升级：稳定 system key、版本号、幂等 seed；用户复制后解除模板升级覆盖。
- 预置内容创作助理：首期开放问答、策划和创建可撤销草稿，不开放自动发布。
- 预置客服助理：首期只读咨询、故障排查和转人工，不直接修改工单或用户数据。
- AG-UI/REST/GraphQL 只调用 Assistant 用例；移除固定助理类型 endpoint 和固定 `@AguiAgentId` Bean 接线。

退出标准：

- 内置、自建和复制助理均通过 `tenantId + assistantId + conversationId` 通用入口运行。
- 用户能看到助理职责、版本、支持模式、有效资料清单和统一任务状态，并可确定性取消或暂停。
- READ_ONLY 场景没有外部写入；内容创作唯一写场景为有预览和撤销能力的草稿。
- CompletionValidator 不会把模型停止输出误判为任务完成；验证失败可继续修复或明确请求用户。
- 两个内置定义可重复安装且不产生重复记录；模板升级不覆盖用户副本。
- 删除或断开 ContentCreationAgentFactory、专用客服 Factory 和固定 `@AguiAgentId` 生产接线；不存在第二条业务 runtime。
**P3：引导任务、Cognition 与横切治理**

目标：让可恢复任务具备透明上下文、细粒度授权、连接器信任、长期记忆、可恢复 HITL、计量和单一轨迹。

主要工作：

- 实现 MemoryContextMiddleware：调用前经 MemoryRecallPort 注入按预算裁剪的混合检索结果。
- 实现记忆写入流水线：候选提取 → 重要性/可信度/隐私评估 → 去重/冲突检测 → MemoryWritePort；共享知识另走审核。
- 明确 tenant/user/visitor scope、匿名 TTL、登录后合并确认、纠正、删除和遗忘能力。
- 完善 EffectiveContextPort：用户可移除或禁止允许管理的来源；上下文清单不复制知识和记忆正文。
- 建立 AuthorizationGrant：action、resource、scope、expiresAt、conditions、reversible、grantedBy、taskId，并支持查看和撤销。
- 实现 ToolGateway 三层检查：动作可见性、任务授权、参数与系统/租户策略；确认请求包含动作、原因、影响、数据和补救方式。
- 实现 ConnectorActionPort；OAuth scope、凭证托管、刷新、过期和撤销由服务/基础设施实现，智能层只接收凭证句柄。
- 将轮询式审批替换为可持久化 HITL：创建 approval → 任务暂停 → 外部决定 → 幂等恢复；不占线程等待。
- 实现 TokenMeteringMiddleware：从实际模型调用解析 modelId/capability，用量与扣减幂等；映射缺失时失败并告警，不用固定兜底单价。
- 实现 Trace middleware 和 ExecutionEventStorePort，只 append `ai_task_event`；SSE 从持久事件续订，conversation_message 仍是用户消息真理源。

退出标准：

- 刷新、断线、重启、暂停后任务从明确状态恢复；每个等待用户的任务都说明所需输入和不处理的后果。
- Assistant 默认能跨会话回忆允许沉淀的信息；匿名 TTL、tenant/user 隔离、纠正和遗忘测试全绿。
- 授权严格限制动作、对象、范围和时效；撤销后新动作被拒绝，用户授权不能越过系统或租户策略。
- OAuth 凭证正文不进入模型上下文、AgentState、日志或事件 payload；过期和撤销产生可恢复状态。
- 审批跨进程重启可恢复，重复回调只生效一次；拒绝后工具不执行。
- 每次状态、模型、工具、授权、审批和验证动作在 `ai_task_event` 有有序、脱敏、可续订事件。
- `ai_execution_run/step` 没有同步事实 writer；若保留投影，清空后可从事件重建。
- 计量、事件和工具 middleware 不读取 ThreadLocal，不直接操作 JdbcTemplate。
### 受控委托与可靠性：P4

目标：让条件完备的任务在用户离线时继续执行，同时具备预算、期限、通知、人工接管、复杂任务恢复和多副本一致性。

主要工作：

- 开放 DELEGATED 模式；建立 ExecutionContract：预算、截止时间、最大调用次数、允许动作、停止条件、重试、通知、负责人和接管策略。
- 实现 DelegatedTaskPort 的持久任务调度；AgentStateStore 只恢复 Agent 工作态，不作为后台队列或任务真理源。
- 实现任务中心与 NotificationPort：状态变化、授权缺口、连续失败、预算临界和完成结果按策略通知，通知失败不改变任务事实。
- 实现人工接管：暂停 Agent、释放执行权、变更 owner、人工处理；交回时创建新 execution，人与 Agent 不并发写同一任务。
- 扩展 TaskBoard、Goal、SubTask、依赖 DAG、并行度/预算限制和 CompletionValidator。
- 支持取消、修改、补充、无关四类执行期输入；每类都有确定状态机和事件。
- 子 Agent 使用独立 executionId/sessionId；父任务只通过 AAF 任务契约和事件聚合，不把 AgentScope subagent 当作 Team。
- 实现 ConversationLeasePort 的 Redis/Redisson lease、续约、fencing token 和 owner 诊断信息。
- 所有状态、事件、消息、计量和副作用提交前校验 fencing token；旧 owner 的迟到写入必须被拒绝。
- 为发布、工单、通知、扣费和外部工作流工具引入统一幂等键；接入 tenant/user/task 隔离的沙箱、MCP 和文件系统。
- 建立故障注入：模型流中断、工具超时、Redis 短暂不可用、Pod kill、lease 过期、重复消息、事件发布失败、凭证过期和用户长期未响应。

退出标准：

- 所有后台任务都有负责人、停止条件、预算、截止时间和全局停止入口；越界时自动暂停，不继续重试或扣费。
- 用户能查看中间产物、撤销未提交变更、停止任务和人工接管；接管期间 Agent 不产生副作用。
- 两个应用副本同时接收同一 conversation 的消息时，最多一个 owner 执行工具和写入新代状态。
- lease owner 被 kill 后，新 owner 从最近持久点接管；旧 owner 恢复后无法覆盖新状态。
- 不同 conversation 保持并行；同 conversation 的 sequence 连续且唯一。
- 复杂 DAG 部分失败后只重跑未完成/可重试节点，已成功副作用不重复。
- 取消和修改能传播至运行中的子任务，最终任务状态、TaskBoard、事件和用户消息一致。
### 规模化自动化与协作：P5

目标：把稳定的委托任务提升为可治理的重复自动化，并在统一责任链下引入多 Assistant 和 A2A 协作。

主要工作：

- 建立 AutomationDefinition/TaskTemplate：来源任务、输入参数、触发条件、频率、权限、预算、失败策略、通知、版本和启停状态。
- 自动化启用前必须试运行和影响预览；只允许从已通过 P4 验收的委托任务保存模板，模式升级产生审计事件。
- 工作流/调度引擎承载确定性触发和执行；Assistant 负责目标解释与结果，AgentScope 不承担长期调度。
- 建立 Assistant、Skill、Automation 和 Connector 的 DRAFT/PUBLISHED/DEPRECATED/DISABLED 生命周期、审核、兼容影响和回退规则。
- 建立 Team、Goal、Member、Assignment、CollaborationPolicy、ResultProposal 与 ArbitrationDecision。
- 实现 Leader/Worker、Pipeline、Supervisor 等策略，但策略只操作 AAF Assistant 端口；主 Assistant 对用户保持单一责任。
- 多 Assistant 分派、结果聚合、冲突仲裁、预算分配和项目级 checkpoint 归 Team 层。
- 为外部 Agent 定义 A2A gateway 端口、身份映射、能力描述、超时、信任和审计契约；协议 SDK 仅进入基础设施适配器。
- 明确 AgentScope subagent 只解决单 Assistant 内任务委派，不替代 Team，也不直接访问 Team Repository。
- 增加组织级策略、tenant 数据隔离、审计检索、异常检测和自动化全局停用能力。

退出标准：

- 自动化变更可预览、试运行、版本化、停用和回退；极高风险动作不能配置为无人值守自动执行。
- 同一触发重复投递只产生一个有效任务；模板升级不突变正在执行的旧版本。
- Assistant、Skill 和 Connector 升级有影响分析；被停用能力不会被新任务选择。
- Team 单测可用 fake Assistant 运行，不依赖 AgentScope；专业助手失败不会使主 Assistant 责任链消失。
- 成员失败、超时、冲突和低置信结果能触发可审计的重派、仲裁或转人。
- Team checkpoint 只保存项目编排事实，不复制成员 AgentState 或个人长期记忆。
- A2A 未启用时不影响本地 Team；启用后外部失败不会破坏本地任务一致性。
- 多 Agent 或自动化的收益通过完成率、时延或专业质量证明，而不是调用数量。
### 切换阶段：P6

目标：原子切到 v2 单一路径并删除所有旧责任、兼容接线和无主数据 writer。

主要工作：

- 停止服务，完成最后一次数据校验和必要投影重建，再切换唯一 endpoint/Bean/port 实现；不运行新旧双写窗口。
- 删除并列 `com.xuejiai.aaf.framework.agentscope` PoC 包。
- 删除 `com.xuejiai.aaf.framework.intelligent.agentscope` 下的 `.legacy` 内容与对应 POM excludes。
- 删除旧 AssistantRuntime/AgentRuntime、专用内容创作/客服 Factory、固定 `@AguiAgentId`、SessionAgentManager、HarnessGateway 和旧 endpoint。
- 删除 AafContextHolder、AgentCapabilityContext、Hook 接线、轮询 RequestApprovalTool、直接 JdbcTemplate middleware。
- 删除旧 Session/SQLite 示例存储、复制的官方 sample runtime、旧 sandbox/session 管理器和未使用配置项。
- 停止 `ai_execution_run/step` 事实写入；无查询依赖则删表，有查询依赖则保留为唯一事件投影并验证可重建。
- 删除旧配置键、Bean 条件、feature flag 和文档入口；更新迁移说明、运维手册和故障手册。

退出标准：

- 静态扫描旧包、旧类、`.legacy`、旧 import、POM exclude 和禁止配置均为零。
- 每个关键端口恰有一个生产实现；Spring context 无重复/条件式旧 Bean。
- 全量单测、集成测试、验收测试、ArchUnit、数据库迁移测试和双副本故障测试全绿。
- 从空库安装、现有数据升级、重启恢复、投影重建和回滚演练均有记录。
- 人工确认旧表数据保留策略及不可逆删除窗口后，才执行破坏性清理。

## 测试与退出门禁

### 测试矩阵

| 维度 | 单元/契约 | 集成 | 验收/故障 | 核心不变量 |
|---|---|---|---|---|
| 五层边界 | 领域模型、端口、ArchUnit | Spring 唯一实现 | 禁止 import 扫描 | AgentScope 不渗透领域 |
| Agent 执行 | compiler、mapping、event | 模型 stub + Redis StateStore | 流中断、取消、恢复 | 会话不串态、终态完整 |
| Assistant/任务控制 | 路由、模式、生命周期、validator | 内置定义 + AG-UI | 问答/协作、暂停/接管 | 模式不静默升级，回合结束不等于完成 |
| 有效上下文 | 来源选择、引用与脱敏 | 规则/Skill/知识/记忆 | 移除、纠正、tenant 隔离 | 清单透明且不复制真理源 |
| Cognition | 召回、去重、冲突、隐私 | PgVector/Neo4j/PG | 匿名 TTL、遗忘、隔离 | Cognition 唯一长期记忆源 |
| 授权、工具与 HITL | 动作/对象/范围/时效、幂等、状态机 | approval + ToolGateway | 撤销、重复审批、拒绝、超时 | 未授权不产生副作用 |
| 连接器与凭证 | scope、句柄、过期和撤销 | OAuth vault + connector | token 过期、连接器停用 | 凭证正文不进模型和事件 |
| 轨迹与消息 | event schema、sequence | PG + SSE resume | 断线续传、投影重建 | `ai_task_event` 唯一轨迹源 |
| 计量 | capability、价格、幂等 | model + ledger | 重试、取消、副本接管 | 不漏记、不重复扣费 |
| 多副本 | lease/fencing 算法 | 两实例 + Redis | kill owner、网络抖动 | 同会话全局单执行者 |
| 受控委托/复杂任务 | ExecutionContract、DAG、validator、干预 | 持久调度 + 子任务并行 | 预算越界、接管、部分失败 | 有负责人和停止条件，只重跑必要节点 |
| 自动化/Team/A2A | 模板版本、触发幂等、策略与仲裁 | workflow + fake/外部 gateway | 重复触发、升级、成员失败 | 可停用回退，Team 不复制 AgentState |
| 迁移 | 数据校验与脚本 | 空库/升级库 | 回滚与投影重建 | 无双 writer、无旧 Bean |

### 每阶段通用门禁

- 新增和修改行为有对应单元测试；跨存储或跨副本行为有 Testcontainers/集成测试。
- `pnpm check:affected` 全绿；P6 还需 `pnpm check` 与 `pnpm acceptance` 全绿。
- ArchUnit、禁止字符串/import 扫描和 Spring Bean 唯一性检查全绿。
- 日志、事件和错误不泄漏系统提示词、工具凭据、个人记忆和原始敏感 payload。
- 数据库 migration 可在空库和升级库执行；写入幂等，约束与代码契约一致。
- 本阶段替代的旧责任已断开或删除，不新增“稍后删除”的兼容实现。

### 关键可观测指标

上线前至少具备：活动 conversation lease 数、lease 等待/超时/失效次数、fencing 拒绝次数、AgentState load/save 延迟和失败率、事件 sequence 冲突、SSE lag、任务状态停留时长、CompletionValidator 继续修复率、有效上下文来源数量、授权撤销/拒绝、OAuth 过期、后台预算使用率、人工接管成功率、自动化重复触发抑制、工具幂等命中、审批等待时长、记忆候选接受/拒绝率、模型用量与结算差异、TaskBoard 悬挂任务数。

## 迁移、删除与人工确认

### 旧责任迁移清单

| 旧路径/责任 | 目标责任 | 处理阶段 | 最终动作 |
|---|---|---|---|
| `framework.agentscope` sibling PoC | `intelligent.infrastructure.agentscope` | P1-P4 | P6 整包删除 |
| `intelligent.agentscope/**/*.legacy` | v2 唯一适配器 | P0-P4 | P6 删除并移除 POM exclude |
| `action`、`ai` 顶层并列责任 | 五层应用端口或基础设施 | P1-P3 | 迁移后删除空包 |
| `AssistantRuntime` / `AgentRuntime` 暴露 AgentScope 类型 | Assistant 用例 + AgentExecutionPort | P1-P2 | 替换调用方后删除 |
| `ContentCreationAgentFactory` / 客服专用 Factory | 两个内置 AssistantDefinition | P2 | 删除专用工厂和 Bean |
| 固定 `@AguiAgentId` | 通用 `assistantId` 路由 | P2 | 删除固定注册 |
| `AafContextHolder` / `AgentCapabilityContext` | 显式 InvocationContext | P1-P3 | 删除 ThreadLocal |
| Hook / CallLogMiddleware 旧事件 | v2 Middleware + AAF ExecutionEvent | P3 | 删除旧 Hook 链 |
| `RequestApprovalTool` 轮询 | 持久 approval + 暂停/恢复 | P3 | 删除轮询实现 |
| `SessionAgentManager` / `HarnessGateway` | 无状态 Harness 缓存 +应用端口 | P1-P2 | 不复制，旧实现删除 |
| Session/SQLite sample store | Redis AgentStateStore | P1 | 删除样例存储生产接线 |
| `ai_execution_run/step` 直接 writer | `ai_task_event` + 可选投影 | P3/P6 | 停写；删表或转投影 |
| `MEMORY.md` 独立长期记忆 | Cognition | P3 | 默认关闭；仅允许可重建缓存 |
| JdbcTemplate middleware | 持久化端口实现 | P3 | 删除直接 SQL 接线 |
| 固定模型兜底单价 | 实际 modelId + 价格表 | P3 | 删除 fallback 计价 |

### 人工确认门

| 时点 | 必须确认 | 未确认时动作 |
|---|---|---|
| P0 开始编码前 | 继续 RC4 或升级到经评审版本 | 停止 AgentScope 适配编码，只做纯领域契约 |
| P2 内置助理发布前 | 提示词、工具权限、客服隐私和转人工规则 | 定义保持 draft，不对用户启用 |
| P4 实现前 | 委托预算/期限/停止/通知/接管契约，以及 Redis/Redisson lease 与 fencing 原子性 | 不开放后台委托和多副本生产部署 |
| P5 自动化启用前 | 试运行结果、触发幂等、权限范围、全局停用和极高风险禁用策略 | 模板保持 draft，不启用触发 |
| P6 切换前 | 停机窗口、数据库备份、旧表保留/删除策略 | 不执行破坏性迁移 |
| P6 删除后 | 全量验收、故障演练和回滚演练结果 | 不发布生产版本 |

### 完成定义

当 P6 全部退出标准满足时，才可宣称智能架构 v2 开发完成：领域代码仅表达五层模型；AgentScope 仅存在于唯一基础设施适配器；内容创作与客服由数据化内置 Assistant 提供；问答、协作、委托和自动化模式具有不可越权的控制边界；任务状态、有效上下文、授权和人工接管可解释、可恢复；长期记忆、消息、执行状态、编排和轨迹各有唯一真理源；tenant 数据和凭证隔离；同一 conversation 在多副本下保持单执行者；仓库中不存在可被重新启用的旧路径或双写开关。
