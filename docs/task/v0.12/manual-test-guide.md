---
level: Practice
layer: Product
purpose: v0.12 智能层运行时前端手动测试指南——验证直接执行/协调者拆分/多子智能体、角色切换、技能启用、工具调用、HITL 审批续跑
status: draft
version: 1.0.0
date: 2026-09-03
author: AaronZZH & Kiro
tags:
  - v0.12
  - 手动测试
  - AG-UI
  - HITL
related:
  - AAF-104/tasks.md
  - AAF-107/tasks.md
gains:
  - 能在本地环境跑通三档任务复杂度、角色切换、技能启用、工具调用、HITL 审批续跑的手动验证
  - 能识别每个测试点当前系统的真实能力边界，不会把 UI 状态误判为后端生效
---

# v0.12 智能层运行时手动测试指南

## 前置说明

本指南所有提示词与预期效果均基于源码核实（`AssistantExecutionService`/`DelegatedTaskCoordinator`/`SubmitExecutorPlanTool`/`SubmitCoordinationPlanTool`/`ai_tool_catalog` seed 数据），不是假设的能力。测试前必须了解两个术语纠正：

- **任务是否拆分由模型语义判断，不是关键词确定性路由**。全局提示词 `builtin-task-decomposition` 引导模型在一次 execution 内选择"直接回答 / 调用 `submit_executor_plan` / 调用 `submit_coordination_plan`"。同一提示词最多重试 3 次，以工具调用证据判定，不要只看最终文字。
- 无论任务多简单，`/api/agui/run` 当前都会创建一个 `TaskBoard`，初始节点是 `COORDINATOR`——协调者节点本身不算"拆分"，拆分的判定标准是**是否新增了 `EXECUTOR` 节点**。

## 环境准备

### 前置条件

- Node.js ≥24，pnpm 11，JDK 25
- PostgreSQL：`localhost:5432/aaf-dev`（默认 `postgres/postgres`）
- Redis：`localhost:6379`
- Neo4j：`bolt://localhost:7687`
- 已配置真实可用且已启用的 LLM provider/model（默认占位 key 无法完成任何测试）
- `apps/webui/.env.local` 配置 `NEXT_PUBLIC_API_URL=http://localhost:8080`

> 🔴 **高风险提醒**：dev 配置 `aaf.flyway.clean-on-start: true`，每次后端启动会清库重建。只能连接可丢弃的本地开发库。

### 启动步骤

```powershell
# 终端 1：后端
pnpm nx serve service
# 默认 http://localhost:8080

# 终端 2：前端
pnpm nx dev webui
# 默认 http://localhost:3000，未登录会跳登录页，seed 管理员账号 admin/admin
```

### 测试页面

| 页面 | URL | 用途 |
|------|-----|------|
| 通用 AI 对话 | `http://localhost:3000/ai/chat` | 场景 1/2、角色切换、工具调用 |
| 文案工作台 | `http://localhost:3000/studio/create/copy` | 技能启用、HITL 审批续跑 |
| AG-UI 后端入口 | `POST http://localhost:8080/api/agui/run` | 打开 DevTools Network，勾选 Preserve log，观察请求体与 SSE |

## 场景一：简单任务直接执行

入口：`/ai/chat`

### 测试提示词

**提示词 1**：
> 请直接回答，不要拆解任务、不要制定计划：用三句话说明 AAF 中 Skill 和 Tool 的区别。

**提示词 2**：
> 请直接回答，不要拆解任务、不要调用计划工具：把"先写规范，再写代码"改写成一句不超过 20 个字的口号。

### 预期效果

- Network 首次请求 `forwardedProps.mode="CHAT"`。
- 会看到一个 `COORDINATOR` Activity 卡从 `RUNNING` 到 `COMPLETED`（这是统一入口固有的协调者节点，不代表任务被拆分）。
- **不应**看到任何 `EXECUTOR`/`EVALUATOR`/`AGGREGATOR` Activity 卡。
- **不应**看到 `submit_executor_plan` 或 `submit_coordination_plan` 工具调用卡。
- 最终直接输出答案，无中间步骤展示。

## 场景二：中等复杂度任务，协调者拆分步骤执行

入口：`/ai/chat`

### 测试提示词

**提示词 1**：
> 这是一个中等复杂度任务，只允许一个执行体，不要创建多个子智能体。请严格顺序完成：①提炼"规范驱动开发"的核心观点；②列出三条实施原则；③用前两步结果写一段 120 字摘要。必须调用 submit_executor_plan，并为每一步上报 STARTED 和 COMPLETED。

**提示词 2**：
> 请由同一个执行体顺序完成，不要并行、不要分派独立角色：先分析"小红书新品咖啡机文案"的目标读者，再列三条卖点，最后生成一段 150 字文案。使用步骤计划并逐步报告执行状态。

### 预期效果

- `submit_executor_plan` 工具调用卡出现，计划含 3 个有序步骤。
- 不会出现多个独立 `EXECUTOR` Activity 卡，始终是同一节点推进。
- 后端应依次产生 `EXECUTOR_PLAN_SUBMITTED`、各步骤 `STARTED`/`COMPLETED` 事件（页面未挂完整 Step 时间线组件，如页面未显示不代表失败，以 Network SSE 事件为准）。

### ⚠️ 已知问题（不影响测试执行，仅供参考）

`ReportExecutorStepTool` 的异常提示文案写着"只能在编排板上的 EXECUTOR 节点内调用"，但经核实这只是过时的错误消息文案，实际代码只检查节点是否在编排板上（`nodeIdentity != null`），不限定节点类型——协调者节点合法可以完成"提交计划 → 上报步骤"完整链路，这是 AAF-107 #10706 架构改造后的既定设计（"任何节点自主决定要不要调用 `submit_executor_plan`"）。已记录为文案纠正任务 [#10707](AAF-107/tasks.md)，**不影响本场景的实际测试结果**，预期效果应能正常达成。

## 场景三：复杂任务拆分给多个子智能体执行

入口：`/ai/chat`

### 测试提示词

**提示词 1**：
> 请并行分派两个独立子智能体并在最后聚合：A 使用平台向导角色 system.role.platform-guide 和技能 builtin-javascript-compute，计算 199 元按 30 天折算的日均价格；B 使用内容创作者角色 system.role.content-creator 和技能 biz-analysis，分析这个定价的用户价值。必须调用 submit_coordination_plan，maxParallelism=2。

**提示词 2**：
> 请并行分派两个独立子智能体，最后统一汇总：A 使用内容创作者角色 system.role.content-creator 和技能 redbook 生成小红书文案；B 使用同一角色但技能 title-topic 生成 5 个标题。必须调用 submit_coordination_plan，maxParallelism=2，不要由一个智能体代做全部工作。

### 预期效果（若链路完整）

- 先出现 `COORDINATOR` Activity。
- 出现 `submit_coordination_plan` 工具调用卡。
- 至少两个 `EXECUTOR` Activity 卡：提示词 1 应显示不同 `roleKey`；提示词 2 应在事件中显示不同 `skillKey`。
- 两个执行者的运行时间应有重叠，证明并行执行，不能只看最终文字判断。
- 之后可能出现 `AGGREGATOR` Activity，TaskBoard 最终整体完成。

### ⚠️ 已知限制

核实确认当前非 Team 模式的 `submit_coordination_plan` 工具**强制要求所有 Executor 的 Role/Skill 与协调者自身冻结的 Role/Skill 一致**，禁止动态指定不同角色/技能（`SubmitCoordinationPlanTool.java:135-162`，刻意的安全设计："授权衰减基准是委派方自身，executor 只能等于该基准，不得放大到基准之外"）。`/ai/chat` 走 AUTO 路由时，协调者的 baseline skill 可能为 null，而工具 schema 要求 Executor 的 `skillKey` 必须是非空字符串，计划提交会因此失败。已记录为架构评估任务 [#10708](AAF-107/tasks.md)。

**测试时请记录实际观察到的行为**：
1. 模型调用 `submit_coordination_plan` 报错（如"不能更改已冻结的 Role 或 Skill"）——记为"协调计划被拒绝"
2. 模型放弃拆分，由自己直接完成全部工作——记为"降级为单体直答"
3. 若两个执行者确实以不同 Role/Skill 成功并行执行——说明系统行为优于当前核实结论，请记录完整 Network 证据反馈复核。

**当前已知可行的多角色/技能组合方式**：需要先创建并发布一个包含多个冻结 Worker（不同 Role/Skill）的 Team，再通过 Team 专属入口调用（`forwardedProps.mode="TEAM"` + `teamId`）。此路径当前 webui 前端未提供页面入口，只能通过直接构造 API 请求验证，不在本次纯前端手动测试范围内。

## 角色（Role）切换测试

### 系统中真实存在的 Role

| Role Key | 名称 |
|----------|------|
| `system.role.platform-guide` | 平台向导 |
| `system.role.content-creator` | 内容创作者 |
| `system.role.customer-service` | 客服专员 |

### 测试步骤（`/ai/chat`）

1. 打开 DevTools Network。
2. 在角色下拉框选择"平台向导"，发送：
   > 你当前是什么角色？只说明职责与能力边界。
3. 切换角色下拉框为"内容创作者"，再次发送相同提示。
4. 对比两次 `/api/agui/run` 请求体与响应中的 `roleKey`。

### 预期效果与已知限制

正确实现应在请求体中携带所选 Role key，且响应 Activity 的 `roleKey` 随选择变化。核实结论分两部分（详见 [#10708](AAF-107/tasks.md)/[#10709](AAF-107/tasks.md)）：

1. **显式指定不会生效**：`/ai/chat`（CHAT 模式）协议本身没有设计"用户显式指定 Role"的输入通道——`AssistantAguiController` CHAT 分支硬编码 `RouteConstraint.AUTO` 且 `role` 参数固定传 `null`，角色下拉框选择不会随请求发送到后端。在 Network 面板核对请求体会发现，无论下拉框选择什么，请求体中都不包含角色标识字段。
2. **模型动态决策角色确实在运行，但界面看不到结果**：后端在 `RouteConstraint.AUTO` 时会走 `DefaultRoleSelector.selectByModel`——这是已经存在且真实运行的能力，依据候选 Role 的职责描述与任务输入语义动态选择角色。但选择结果只写入后端审计表（`ai_skill_decision_audit`），不投影到 AG-UI 事件流，界面上不会显示"当前使用的角色"，需要后端日志或直接查库才能确认模型选了哪个角色。

**可验证角色确实生效的替代路径**：`/studio/create/copy`（文案工作台）走 `EXECUTION` 模式，每次固定发送 `role.key="system.role.content-creator"` 且 `routeConstraint="FIXED"`，可作为"显式指定角色确实生效"的对照组（但同样看不到界面展示，只能通过 Network 请求体确认）。

## 技能（Skill）启用测试

### 系统中真实存在的 Skill（部分）

| 分类 | Skill Code | 说明 |
|------|------------|------|
| 内容生产 | `redbook` | 小红书爆款文案 |
| 内容生产 | `biz-analysis` | 商业分析 |
| 内容生产 | `title-topic` | 标题选题 |
| 框架内建 | `builtin-javascript-compute` | 受控 JavaScript 计算 |

内容创作者角色（`system.role.content-creator`）绑定了 `redbook`/`biz-analysis`/`title-topic` 等全部内容生产 Skill。

### 测试步骤（`/studio/create/copy`）

1. 打开文案工作台，选择 Skill = `redbook`。
2. 输入：
   > 为一款便携咖啡机写一篇面向 25-35 岁上班族的小红书种草笔记，突出 3 分钟出咖啡、易清洗和适合办公室，正文 300-500 字。
3. 点击生成，检查 Network 请求体。

### 预期效果

- 首次请求 `forwardedProps.mode="EXECUTION"`。
- `forwardedProps.request.role.key="system.role.content-creator"`。
- `forwardedProps.request.skill.code="redbook"`。
- 最终标题含情绪词/关键词/emoji，不超过 18 字；正文分段，前两句是钩子，结尾有互动引导；标签 5-8 个——这些是 `redbook` Skill 定义的明确产出规则，能验证 Skill 真实生效（而不只是被选中）。

## 工具调用（Tool Call）测试

### 测试提示词（`/ai/chat`）

> 请不要凭已有上下文回答。必须调用 context.load，参数使用 kind=SKILL、key=redbook，读取该技能正文后，告诉我它规定的标题最大长度和标签数量范围。

`context.load` 是真实存在的只读白名单工具（`read_only=true`，无需审批），可安全用于验证工具调用链路。

### 预期效果

- 界面显示"正在调用 1 个工具…"，随后显示 `context.load` 已完成或"1 个工具调用"。
- SSE 事件流中同一 `toolCallId` 有 `TOOL_CALL_START` 与对应结果事件。
- 最终回答必须准确复述"标题控制在 18 字以内，标签 5-8 个"（`redbook` Skill 定义的真实规则），能证明回答确实基于工具真实返回结果，不是模型编造。
- 不会出现审批卡（该工具无需确认）。

## HITL 权限审批续跑测试（AAF-104 #10404）

这是刚完成的核心能力：AG-UI 标准 `resume[]` 协议，用真实审批场景验证"暂停 → 批准 → 从原执行续接"的完整闭环。

### 推荐入口

`/studio/create/copy`（文案工作台）。**不要**用 `/ai/chat` 的旧版工具确认弹窗做本项验证——那条路径走的是独立于本次改造的旧 REST 决策链，不是标准协议。

### 测试步骤

1. 打开 DevTools Network，勾选 Preserve log。
2. 进入文案工作台，选择 Skill = `redbook`，长度选"中篇"。
3. 输入：
   > 为新品便携咖啡机写一篇面向 25-35 岁上班族的小红书种草笔记，标题不超过 18 字，正文 300-500 字，附 5-8 个标签；完成后保存为数据库草稿。
4. 点击"生成"。
5. 观察首次 `/api/agui/run` 请求体：`forwardedProps.mode="EXECUTION"`，role 为内容创作者，skill 为 `redbook`，`artifactPersistence="AUTO_SAVE_DRAFT"`。
6. 模型生成内容后会调用 `content.draft.upsert`（真实存在、需要审批的工具：`read_only=false`、`reversible=true`、`require_confirm=true`）。

### 审批前应看到

- 执行阶段变为"等待授权"。
- 执行过程列表出现"content.draft.upsert 等待用户授权"。
- 出现确认卡片：标题"需要确认受控工具操作"，标签"工具：content.draft.upsert"与"操作可撤销"，按钮"批准并继续"/"拒绝"。
- 该次 SSE 流以 `RUN_FINISHED{outcome:{type:"interrupt"}}` 结束（可在 Network 响应中核对），并记下其中的 `interrupt.id`。

### 点击"批准并继续"后应看到

7. 发出第二次 `/api/agui/run` 请求，核对请求体：
   - `messages: []`（不是重新发送原提示词）
   - `resume: [{interruptId: <第一次记录的 id>, status: "resolved", payload: {approved: true}}]`
   - `threadId` 与第一次相同（同一线程），`runId` 可以不同（这是协议正常行为，新 run 续接旧 execution，不代表新建了业务任务）。
8. 界面出现"授权已确认，正在恢复执行"过渡提示。
9. 工具列表中 `content.draft.upsert` 状态从"等待中"变为"已完成"。
10. 最终阶段变为"已完成"，输出区出现"数据库草稿 #<正整数 id>"。

### 判定为真恢复必须同时满足

- 审批前确实进入"等待授权"阶段（不是审批卡一闪而过）。
- 第二次请求使用了标准 `resume[]` 字段，且 `messages` 为空数组。
- 最终产出包含真实生成的草稿 ID，且文案内容符合 `redbook` 结构约束。

### 判定为失败（假恢复）的情况

- 审批卡直接消失，没有经过"等待授权"状态。
- 批准后前端重新发送了完整的原始提示词（等价于重新生成一次，不是续接）。
- 最终没有产出数据库草稿 ID。
- 工具状态一直停留在"等待中"或"运行中"不再变化。

## 测试记录建议

每个测试用例建议记录：所用模型 ID、完整提示词、首次/续跑请求体、`interruptId`、`threadId`、最终产出（草稿 ID / 生成文案）、观察到的界面截图。若同一提示词连续尝试 3 次都无法触发目标行为（如始终不调用计划工具），应记为"当前行为稳定但与预期不符"，无需继续重试。

## 已知问题清单（测试前必读）

本指南在设计过程中核实发现以下问题，均已记录到 [AAF-107/tasks.md](AAF-107/tasks.md) 跟踪：

| 编号 | 问题 | 影响范围 | 性质 |
|------|------|----------|------|
| [#10707](AAF-107/tasks.md) | `ReportExecutorStepTool` 异常提示文案与实际守卫逻辑不一致 | 场景二 | 文案纠正，不影响实际功能 |
| [#10708](AAF-107/tasks.md) | CHAT 模式无 Role 显式指定通道；非 Team 模式禁止 Executor 使用不同 Role/Skill | 场景三、角色切换测试 | 需架构评估，部分涉及安全边界调整 |
| [#10709](AAF-107/tasks.md) | 角色选定决策不投影到前端，用户看不到"选了哪个角色、为什么" | 角色切换测试 | 需评估是否新增 AG-UI 事件投影 |

**关于角色能力的核实结论（避免误解）**：
- **显式指定角色**：`EXECUTION` 模式（文案工作台走这条）已完整支持；`CHAT` 模式（`/ai/chat`）协议本身没有设计接收字段，用户选择不会真正生效。
- **不指定时由模型动态决策角色**：后端**已经支持且在真实运行**（`DefaultRoleSelector.selectByModel`），不是缺失能力。
- **前端能否看到"当前用的是哪个角色"**：完全不能，无论是显式指定还是模型动态选择的结果都不会投影到界面，这是独立于"能不能指定"的另一个缺口（#10709）。

## 参考

- 关键源码：`AssistantExecutionService.java`（请求组装与 `analyzedBoard()`）、`DelegatedTaskCoordinator.java`（拆分判断）、`SubmitExecutorPlanTool.java`/`ReportExecutorStepTool.java`/`SubmitCoordinationPlanTool.java`（计划与协调工具守卫）
- Seed 数据：`apps/service/aaf-api/src/main/resources/db/seed/v12__init_seed_data.sql`（Role/Skill/Tool 定义）
- 相关任务：[AAF-104 tasks.md](AAF-104/tasks.md)（AG-UI 事件完整性与 HITL 闭环）、[AAF-107 tasks.md](AAF-107/tasks.md)（EXECUTOR 持久计划）
