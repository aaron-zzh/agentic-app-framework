---
level: Practice
layer: Product
purpose: 定义 AAF-116 对话式受控实体 CRUD 与失败任务资源处置需求
status: draft
version: 1.0.0
date: 2026-09-11
author: AaronZZH & Kiro
tags:
  - AAF-116
  - Assistant
  - CRUD
  - ResourceReference
related:
  - ../../task/v0.13/AAF-115/requirement.md
  - ../../task/backlog.md
---

# AAF-116 对话式受控实体 CRUD 与失败任务资源处置

## Epic

**目标**：让用户看见 Task 执行期间创建或更新的业务资源，并能通过现有界面或 Assistant 在权限与确认边界内查询、更新、归档或删除，而不是由 Task 失败自动处置业务数据。

**范围**：定义结构化资源引用、失败任务资源入口、对话式实体动作和破坏性操作门禁；复用现有 CRUD resource 与 AI business action 基础设施，不建立第二套 AI CRUD 系统。本故事待排期，不在 AAF-115 中实现。

## 用户故事

作为使用 Assistant 完成长任务的用户，我希望任务失败或暂停后仍能访问已经生成的项目、文档、草稿和图片，并由我决定继续使用、修改、归档或删除，以便有价值的中间成果不会被系统误删，破坏性操作也始终可控、可审计。

## 需求评估

| 维度 | 结论 |
|---|---|
| 产品定位 | 属于 AI 原生的人机协作与可控工具执行能力 |
| 框架与业务边界 | 框架提供资源引用和动作门禁；实体规则仍由各业务服务负责 |
| 核心依赖 | 依赖 AAF-115 的 Task/Execution/receipt 真理链及既有 CRUD/action 注册体系 |
| 部署影响 | 不改变部署模式；链接与操作均在当前 tenant 内完成 |
| 数据安全 | 必须校验 tenant、owner、权限、版本、引用关系和审计身份 |
| 用户接受度 | 默认保留数据、优先归档、删除显式确认，降低误操作风险 |

## 业务规则

### 自动清理边界

- 自动清理仅适用于用户不可见、没有独立业务价值且未形成公开资源引用的运行时临时数据，例如一次性 Agent 实例、未完成工具调用的暂存证据、终态 Execution 私有状态槽和可重建缓存。
- 暂停、等待授权、等待澄清或其他可恢复状态所需的 checkpoint、AgentState 和恢复身份不是可立即清理的临时数据；必须保留到恢复完成、进入真正终态或命中独立保留策略。
- 项目、文档、草稿、生成图片、上传文件及其他已持久化或已向用户展示的资源一旦形成业务身份，就不得因 Task 失败、暂停、取消或补偿流程被重新归类为临时数据。
- 自动清理不得调用业务实体的 UPDATE、ARCHIVE、DELETE 或领域删除动作，不得级联删除对话消息、公开历史、Task、Execution、event、receipt 或审计事实。
- 临时数据清理失败可通过隔离键、TTL 或受控重试收敛，但不得改变 Task 业务结论，也不得扩大为对用户资源的补偿删除。

### 失败任务资源可见

- Task、Execution 或 Tool receipt 以结构化资源引用记录本次创建或更新的用户可见资源，至少能表达资源类型、资源 ID、显示名称、状态和可访问路由。
- 任务详情和失败结果展示当前用户有权访问的资源链接；越权、已失效或已删除资源不得泄露详情。
- Task 失败、暂停或取消不自动删除、归档或回滚项目、文档、草稿、生成图片及其他用户可见数据。
- 用户可从资源链接进入现有业务页面手动处置，也可在新消息中要求 Assistant 执行受控动作。

### 复用现有实体动作体系

- 能力真理源继续使用 `CrudResourceDefinition`、`CrudResourceRegistry` 与 `CrudResourceExposure.AI_ACTION`。
- Assistant 通过既有 `AiBusinessActionTool`、`AiBusinessActionExecutor`、`EntityActionRegistry`、`EntityActionAdapter` 和 `AiActionCatalogEntry` 查询并执行动作。
- 标准 QUERY、DETAIL、CREATE、UPDATE、ARCHIVE、RESTORE、DELETE 等动作通过 `AI_ACTION` 暴露；发布、采用、审批等领域动作必须使用显式 domain action，不得伪装成通用 UPDATE/DELETE。
- Action catalog 控制动作启用、风险、确认、权限和输入 schema；Role/Skill Tool allowlist 控制 Assistant 可见性；业务服务最终强制 tenant、owner、expected version、引用约束和领域不变量。
- 首批只开放经评审的项目、文档、草稿和 AIGC 资源；当前仅 `system.system-role` 已具备的 `AI_ACTION` 覆盖不能被误认为全实体已开放。

### 写操作与破坏性操作

- 查询可按普通 DIRECT Execution 完成；创建、更新、归档、恢复和删除属于副作用，必须按 AAF-115 创建或使用 canonical Task，并生成幂等 receipt 与审计事件。
- 优先归档而不是删除。DELETE 默认禁用，只有资源明确支持、调用者有权限、用户显式确认且引用检查通过时才可执行。
- UPDATE、ARCHIVE、RESTORE、DELETE 必须携带 expected version；版本冲突、资源状态变化或引用关系变化时 fail-closed，不得覆盖最新数据。
- 高风险或不可逆动作必须展示目标资源、影响范围和不可逆性；确认只能绑定该 Task、Execution、动作和参数，不能复用到其他资源。
- 批量动作必须逐项返回结果并保持幂等；部分失败不得伪造整体成功。
- 不再假设 CREATE、UPDATE、ARCHIVE、RESTORE 普遍可逆；可逆性必须由具体资源和动作定义声明。

## 验收标准

```gherkin
Scenario: 失败 Task 展示仍然存在的业务资源
Given Task 已创建用户有权访问的项目、文档、草稿或生成图片
When Task 失败、暂停或取消
Then Task 详情展示结构化资源链接和当前状态
And 业务资源保持可访问
And 系统不自动归档或删除资源
And 只有用户从 UI 手动操作或在新对话中明确指定后才进入受控处置流程
```

```gherkin
Scenario: 自动清理仅处理用户不可见的运行时临时数据
Given Execution 已进入真正终态
And 本次执行持有一次性 Agent、暂存工具证据或私有状态槽
When 系统执行自动清理
Then 系统只释放没有独立业务价值且未形成公开引用的临时数据
And 不调用任何业务资源的 UPDATE、ARCHIVE、DELETE 或领域删除动作
And Task、Execution、event、receipt、对话历史和用户可见资源保持不变
```

```gherkin
Scenario: 用户通过 Assistant 归档失败任务产生的资源
Given 用户从失败 Task 选择一个支持 ARCHIVE 的资源
When 用户在新消息中明确要求归档并完成必要确认
Then 系统创建 canonical Task 执行受控 ARCHIVE 动作
And 校验 tenant、owner、权限、expected version 和幂等键
And receipt 与审计记录可追溯到原资源和新 Task
```

```gherkin
Scenario: 删除默认关闭且必须显式确认
Given 一个资源未显式启用 DELETE 或仍被有效对象引用
When 用户要求 Assistant 删除该资源
Then 系统拒绝删除并说明安全原因
And 不以 UPDATE、归档或其他动作绕过限制
And 资源数据保持不变
```

```gherkin
Scenario: 版本冲突时写操作失败关闭
Given 用户确认操作时资源版本为 V1
And 执行前资源已被其他操作更新为 V2
When Assistant 提交 UPDATE、ARCHIVE、RESTORE 或 DELETE
Then 业务服务拒绝旧 expected version
And 不覆盖 V2
And Task 报告冲突并提供重新查看资源的入口
```

```gherkin
Scenario: Assistant 只能看到已注册和授权的实体动作
Given 某资源未声明 AI_ACTION 或当前 Role/Skill 未允许对应 Tool
When 用户要求 Assistant 操作该资源
Then 动作不会出现在 Assistant 可用能力中
And 直接伪造 action 名称也会被服务端拒绝
And 不存在第二套旁路 CRUD 执行器
```

## 非目标

- 不在 AAF-115 中实现本故事。
- 不因 Task 失败自动删除、归档或修改用户业务数据。
- 不建立独立于 CRUD resource 和 AI business action 的第二套实体操作系统。
- 不一次性开放所有实体；每类资源必须单独评审权限、风险、版本和引用规则。
- 不用通用 CRUD 模拟 publish、adopt、approve 等有明确领域语义的动作。

## 依赖与排期

- 依赖 AAF-115 提供唯一 Task/Execution/receipt/fencing 真理链和副作用边界。
- 复用现有 `AiBusinessActionTool`、`AiBusinessActionExecutor`、`EntityActionRegistry`、`BaseCrudEntityActionAdapter`、`CrudResourceDefinition` 与 `CrudResourceRegistry`。
- 当前状态为待排期，不加入 v0.13 实现范围；排期后再产出技术设计、任务拆分和 UI 设计。

## 相关设计

- [AAF-115 统一任务模型与 DAG 编排](../../task/v0.13/AAF-115/requirement.md)

## 变更记录

| 日期 | 变更内容 | 原因 | 影响评估 |
|---|---|---|---|
| 2026-09-11 | 明确自动清理仅限用户不可见临时数据；用户资源仅允许 UI 或对话显式处置 | 防止将运行时资源释放误解为业务数据补偿删除 | 增加边界 AC；不扩大 AAF-115 范围 |
| 2026-09-11 | 创建 AAF-116，拆出失败任务资源链接与用户驱动实体处置 | AAF-115 仅保留 Harness Task/Execution/DAG/恢复基础能力 | 待排期；不影响当前 AAF-115 实现范围 |
