---
level: Practice
layer: Model
purpose: 定义助理的身份装配、会话身份、路由与技能激活、澄清策略与输入缓冲
status: draft
version: 1.1.1
date: 2026-08-25
author: Kiro
tags:
  - L3 Assistant
  - Persona
  - Role
  - 路由
dependencies:
  - ../architecture.md
  - ../runtime.md
related:
  - ./coordination.md
  - ../skill/skill-tool-resolution.md
scope:
  includes:
    - 助理的身份装配：Persona、Role、MemoryStrategy
    - 会话身份与多会话
    - Role 选择与 Skill 激活的路由决策
    - 澄清策略与澄清请求生命周期
    - 输入缓冲与执行期干预
    - 系统助理模板与主体隔离
  excludes:
    - 唯一入口、执行意图与完成门禁（见 runtime.md）
    - 多智能体编排与聚合（见 coordination.md）
    - Skill 版本与工具解析（见 skill/）
gains:
  - 能装配一个助理并解释其能力上限来源
  - 能预测一次请求的 Role 与 Skill 激活结果
  - 能说明执行期追加输入如何被分类处置
---

# L3 助理

> 助理是面向人的认知主体和最终交付责任方。它持有会话级焦点与交互上下文，不持有 L1 长期认知或 L2 执行工作态。

## 定位与边界

| 助理负责 | 助理不负责 |
|---|---|
| 身份装配、Role/Skill 路由、澄清、输入干预 | 唯一入口、执行意图与完成门禁，见 [runtime.md](../runtime.md) |
| 会话内目标理解与最终反馈 | 多执行体计划、聚合与产物时序，见 [coordination.md](coordination.md) |
| 系统模板实例化与主体隔离 | Skill 版本、工具解析与授权，见 [skill/](../skill/Readme.md) 与 [动作治理](../action-governance.md) |

会话身份固定为 `threadId = ConversationId = SessionId`，每轮运行使用独立 `runId`；等价关系、恢复和 fencing 契约只引用 [统一运行时](../runtime.md)，本篇不重复定义。

## 领域模型

### 身份装配

```text
Assistant identity = PersonaSnapshot + RoleAssignment + MemoryStrategy
Assistant capability ceiling = identity + SkillBinding + ToolPolicy + lifecycle
```

三项身份配置均无运行时可变状态；单次执行只引用冻结快照，不回写定义。

| 模型 | 字段合同 | 不变量 |
|---|---|---|
| `PersonaSnapshot` | `personaKey`、`personaRevision`、`name`、`description`、`personality`、`speakingStyle`、`instructions`、`avatarRef` | key 与文本字段非空；revision ≥ 0；只影响自然语言表达 |
| `Role` | `key`、`name`、`responsibilities[]`、`nonResponsibilities[]`、`skillBindings[]`、`toolKeys{}` | 职责非空；Skill 与工具 key 唯一；表示能力上限而非本次授权 |
| `SkillBinding` | `skillKey`、`activationMode` | 同一 Scope 内 key 唯一；模式仅为 `ALWAYS` / `ON_DEMAND` |
| `MemoryStrategy` | `mode`、`recallScopes{}`、`writeScopes{}`、`longTermEnabled` | 未启用长期记忆时写入范围必须为空；Role/Skill 只能收窄作用域 |

本文 `Role` 专指 `framework/intelligent/assistant/model/Role.java` 的运行时能力模型，不是 `module/system/role/domain/Role.java` 的系统权限角色；`PersonaSnapshot` 是执行冻结合同，`assistant/persona/Persona.java` 仅是其持久化装配来源，二者不得作为并行运行时真理源。

`MemoryStrategy.mode` 取值为 `MEMORY_ONLY`、`KNOWLEDGE_ONLY`、`HYBRID`、`PROCEDURAL_FIRST`、`FULL`。长期认知的唯一真理源与上下文组装见 [cognition/](../cognition/Readme.md)。

Persona 不得修改任务循环、输出 Schema、Role、Skill、工具或授权；严格 JSON 的执行身份默认不加载 Persona。

### AssistantDefinition 身份与所有权

| 字段组 | 字段 | 合同 |
|---|---|---|
| 稳定身份 | `assistantId`、`version`、`maintainer`、`lifecycle` | 执行按 `assistantId` 读当前行，不按 revision 取历史定义；`version` 是变更计数器，随执行画像冻结进快照，用于"定义在冻结后是否被调整过"的相等比对；仅 `PUBLISHED` 可执行 |
| 模板来源 | `ownership`、`systemKey`、`sourceSystemKey` | `SYSTEM_MANAGED` 仅有 systemKey；`USER_COPY` 仅有 sourceSystemKey；`USER_OWNED` 两者皆无 |
| 装配 | `persona`、`roles[]`、`defaultRoleKey`、`memoryStrategy`、`modelId` | Role 非空且 key 唯一；默认 Role 必须属于 roles |
| 能力上限 | `assistantSkillBindings[]`、`assistantToolKeys{}`、`toolPolicy` | Role 与 Assistant 的 Skill 不重复；所有工具并集必须与 ToolPolicy 完全一致 |
| 治理 | `supportedControlModes{}`、`defaultRiskPolicy` | 请求只能收窄，不能扩大定义上限 |

### 会话与多会话

同一 `AssistantDefinition` 可同时服务多个会话；每个会话的焦点、消息工作集、TaskBoard 引用和输入缓冲按 `threadId` 隔离。定义缓存可共享，以下状态禁止共享：Conversation、Memory subject、Task/Execution、AgentState、workspace、grant、receipt。

> 历史的长驻实例 fork、AgentPool 池化和子实例只读快照方案已废弃；不得复活。

## 契约

### Role 与 Skill 路由

| 阶段 | 输入 | 输出 | 失败行为 |
|---|---|---|---|
| `AUTO` Role 选择 | 已发布 Assistant 的 Role 摘要、任务输入、可选候选提议 | 唯一 `RoleAssignment` | 模型不可用或输出越界时使用定义内默认 Role，不扩大候选 |
| `FIXED` Route 校验 | `roleKey`、`skillKey`、`assistantRevision` | 已校验 `resolvedRoute` | Role/Skill 不属于定义时直接拒绝 |
| `ALWAYS` 激活 | SYSTEM、ASSISTANT、当前 Role 的绑定 | 确定性 Skill 集 | 配置无效时拒绝装配 |
| `ON_DEMAND` 选择 | 上述 Scope 的授权候选集 | 0..N 个 Skill | 候选外 key 直接拒绝 |
| 工具解析 | 最终 ActivatedSkill 与多层工具上限 | EffectiveTools | 未激活 Skill 的工具不可见 |

Role 变化以 `ROLE_RESOLVED` 留痕；Skill/工具的交集算法只引用 [skill-tool-resolution.md](../skill/skill-tool-resolution.md)。

### 澄清

`ClarificationPolicy` 与动作授权完全独立：

| 策略 | 行为 |
|---|---|
| `INTERACTIVE` | 可自然追问，下一轮继续理解 |
| `MINIMAL` | 仅硬阻塞追问；非阻塞缺口采用安全默认并记录假设 |
| `FAIL_ON_BLOCKER` | 硬阻塞直接失败，不创建追问 |

`ClarificationRequest` 字段合同：

| 字段 | 约束 |
|---|---|
| `requestId`、`taskId`、`executionId`、`subTaskId` | 定位唯一等待中的子执行 |
| `requiredFields[]`、`questions[]` | 非空、唯一、顺序逐项覆盖 |
| `deadline`、`createdAt` | deadline 晚于创建时间且不晚于任务 deadline |
| `status` | `PENDING` → `RESOLVED` / `CANCELED` / `EXPIRED` |
| `values{}`、`resolvedAt`、`resolutionReason` | 只能包含声明字段；RESOLVED 必须补齐全部字段；终止态必须带时间与原因 |

澄清请求与授权请求使用不同标识、状态机和恢复链，不得互相代替。

### 输入缓冲与执行期干预

输入以 `inputId` 幂等落库，并按 `receivedAt, inputId` 确定性消费；同一 ID 对应不同事实时拒绝。

| `kind` | 处置 | 对运行的影响 |
|---|---|---|
| `CANCEL` | 取消当前任务 | 立即进入取消链，不合并后续字段 |
| `MODIFY` | 替换已提供的澄清值 | 重新规划或恢复当前子执行 |
| `SUPPLEMENT` | 合并补充字段 | 字段齐全后恢复同一子执行 |
| `UNRELATED` | 保留待后续处理 | 不污染当前任务上下文 |

接收、分类、消费是三个独立事实；追加输入不得直接修改冻结画像。

### 系统 Assistant 模板

系统模板是数据库管理的 `SYSTEM_MANAGED` 定义，不是共享执行实例。系统模板注册表：

| templateId | 用途 | 实例化策略 | 执行约束 |
|---|---|---|---|
| `system.assistant.default-user` | 新用户默认助理的复制源 | 注册激活时幂等创建 `USER_COPY` | 登录用户不得把模板本身当个人默认执行体 |
| `system.assistant.customer-service` | 外部渠道访客客服 | 不复制；由租户渠道显式绑定 | 仅渠道绑定上下文可选择，访客使用独立 TTL 记忆主体 |

默认用户副本合同：

```text
code             = user.assistant.default.<userId>
userId/ownerId   = userId
isDefault        = true
sourceSystemKey  = system.assistant.default-user
复制             = Assistant 主配置 + ai_assistant_role 绑定
引用             = Persona / Role / Model / Skill / Tool 定义
```

主体隔离矩阵：

| 数据 | 模板与实例关系 |
|---|---|
| Persona、Role、Model、Skill、Tool 定义 | 可引用同一不可变版本 |
| Assistant 主记录、Role 绑定 | 用户副本独立；模板更新不覆盖副本 |
| Conversation、Task、Execution、InputBuffer | 按 tenant + user/visitor + thread/run 隔离 |
| Memory、AgentState、workspace | 按主体与会话隔离，模板不持有 |
| grant、receipt、审计 | 按任务与执行隔离，不因模板来源继承 |

未找到个人默认副本时必须报告 provisioning 异常，不得回退到系统模板。所有模板与用户实例共用 [统一运行时](../runtime.md)，不得注册专用运行时或第二正文入口。

## 实现态

| 契约 | 实现态 |
|---|---|
| Persona、Role、MemoryStrategy 字段与不变量 | ✅ 已实现 · `PersonaSnapshot.java:6-36`、`assistant/model/Role.java:8-62`、`MemoryStrategy.java:7-43` |
| 完整 AssistantDefinition 能力上限与所有权不变量 | ✅ 已实现 · `AssistantDefinition.java:13-240` |
| `PROCEDURAL_FIRST` / `FULL` 持久化语义保真 | ⚠️ 部分实现 · `JpaAssistantDefinitionAdapter.java:218-230`；两种模式当前被映射为 HYBRID 默认值 |
| `AUTO` 候选受限单选 Role 与安全默认 | ✅ 已实现 · `DefaultRoleSelector.java:14-171` |
| `FIXED` Route 越界拒绝 | ✅ 已实现 · `AssistantExecutionService.java:593-694` |
| Skill 分层绑定与候选外拒绝 | ✅ 已实现 · `AssistantDefinition.java:67-98`、`DefaultEffectiveSkillResolver.java:11-43` |
| 澄清请求字段、期限与终止状态机 | ✅ 已实现 · 字段及状态机见 `ClarificationRequest.java:13-160`；任务 deadline 上界见 `DelegatedTaskCoordinator.java:786-804` |
| 三种澄清策略的确定性门控 | 🎯 目标态 · 当前不得声称已执行 |
| `CHAT` 默认 `INTERACTIVE` | 🎯 目标态 · 当前不得声称已执行；入口仍固定为 `MINIMAL`，见 `AssistantAguiController.java:140-146` |
| 四类输入的持久缓冲与合并 | ⚠️ 部分实现 · 四种 `kind`、幂等持久化、确定性消费事件及 `CANCEL` 已实现（`ExecutionInput.java:13-58`、`JpaDelegatedTaskAdapter.java:50-82`、`JpaTaskTransitionAdapter.java:215-361`）；`kind` 仍由客户端直接提交（`DelegatedTaskService.java:76-85`），`MODIFY/SUPPLEMENT` 只允许待澄清参数，`UNRELATED` 消费后无后续任务，自然语言输入尚无四分类器 |
| 两个系统模板稳定标识 | ✅ 已实现 · `SystemAssistantTemplateIds.java:4-10` |
| 默认用户副本 provisioning | ⚠️ 部分实现 · 顺序重试会先查已有默认副本并返回（`JpaAssistantProvisioningAdapter.java:29-70`）；DB 层已有 `uk_ai_assistant_user_default`/`uk_ai_assistant_user_source` 唯一索引防止重复默认副本（`v2__ai_schema.sql:471-478`），数据完整性有保障；应用层未捕获并发冲突异常转幂等成功，极端竞态下第二个请求会收到约束冲突错误而非静默成功 |
| 系统模板主体隔离矩阵 | ⚠️ 部分实现 · 用户副本 owner 与默认查询已隔离（`JpaAssistantProvisioningAdapter.java:29-70`、`JpaAssistantDefinitionAdapter.java:65-76`）；通用校验仍放行任意 `SYSTEM_MANAGED` 定义（`AssistantExecutionService.java:867-879`），客服渠道与访客 TTL 主体未落地 |
| 个人默认查询无系统模板 fallback | ✅ 已实现 · `JpaAssistantDefinitionAdapter.java:65-76` |
| `default-user` 禁止被显式直接执行 | ⚠️ 部分实现 · `AssistantExecutionService.java:806-879`；默认查询已隔离，但显式选择 SYSTEM_MANAGED 定义仍可通过通用执行校验 |
| `customer-service` 仅渠道显式绑定 | 🎯 目标态 · 当前不得声称已执行 |
| 会话历史进入受控上下文 | 🎯 目标态 · 当前不得声称已执行，见 [memory.md](../cognition/memory.md) |

## 验收基线

- Persona 变化不改变 Schema、Role、Skill、工具或授权结果
- AUTO/FIXED 路由和 Skill 激活可从已发布定义与候选集合复算
- 澄清与授权拥有独立标识、状态机、事件和恢复入口
- 每条追加输入可定位其接收、分类、消费事实，且不会直接改写冻结画像
- 新用户只执行个人 `USER_COPY` 默认助理；模板不承载用户会话、记忆或执行状态
- 显式选择 `default-user` 模板和非渠道选择 `customer-service` 均被拒绝
