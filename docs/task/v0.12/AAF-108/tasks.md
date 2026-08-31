---
level: Practice
layer: Product
purpose: 拆分 AAF-108 门禁恢复与集成收口的全量验证、补测与真理源同步任务
status: draft
version: 1.0.0
date: 2026-09-01
author: AaronZZH & Kiro
tags:
  - AAF-108
  - 质量门禁
  - 技术任务
related:
  - ../../../design/audit/2026-09-01-harness-landing-plan.md
gains:
  - 能把阶段性偏离欠下的验证与测试一次性补齐
  - 能让设计与规范真理源与落地实现重新一致
---

# AAF-108 门禁恢复与集成收口任务

## 任务约束

- 技术真理源：[Harness 落地计划 · 分阶段路线](../../../design/audit/2026-09-01-harness-landing-plan.md) 末阶段。
- 本故事是 AAF-103～AAF-107 阶段性偏离（不跑 `check`/`acceptance`、不新增测试文件）的**偿还任务**，不是可选收尾。
- 风险等级：🔴 高（真理源一致性与发布结论）。
- 准入：AAF-103～AAF-107 全部合入且无未修 blocker。

## 技术任务

### #10801 全量门禁一次性跑通

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：AAF-103～AAF-107 全部完成
- **范围**：
  - 执行 `pnpm check` 与 `pnpm acceptance`，修复前五个故事累积的编译、lint、单测与验收失败。
  - 失败即视为 AAF-103～AAF-107 全部未完工，按失败归属回退对应故事修复，不在本故事内改他人范围的设计。
- **完成标准**：`pnpm check` + `pnpm acceptance` 全绿；质量门控 blocker=0 且 major≤2。

### #10802 补齐欠下的测试文件

- **状态**：[ ] 待开始
- **负责人**：developer-service（单测）+ tester（集成/验收）
- **依赖**：#10801
- **范围**：
  - 按 AAF-103～AAF-107 各工作包的验收判据，补齐阶段约束期间未新建的测试文件。
  - 测试分层不变：`*Test.java` → Surefire → developer；`*IT.java` / `*AcceptanceTest.java` → Failsafe → tester。
  - 重点补：工具面负向测试、AG-UI schema 与配对、跨副本 resume、计划状态机与恢复、Redis 故障注入。
- **完成标准**：每条工作包验收判据都有对应测试；无 `@Disabled` 绕过竞态。

### #10803 同步真理源与例外登记

- **状态**：[ ] 待开始
- **负责人**：协调者 + architect
- **依赖**：#10801
- **范围**：
  - `architecture.md`：把"AgentScope Harness 的 ReAct"改为"AAF Harness 包裹 AgentScope core ReAct"。
  - `runtime-event.md`：写入公共事件披露白名单（含思考内容不外发）。
  - `core/model-router.md`：写入模型侧思考参数与 L0 唯一模型解析。
  - `agentscope-usage-guide.md`：区分"官方推荐"与"AAF 选型"，删除 `HarnessAgent` 承载执行的表述。
  - `InvocationMode` 的 `AUTONOMOUS_HARNESS` 原子重命名为 `AUTONOMOUS_AGENT_LOOP`，不保留旧枚举别名。
  - 登记 `JsonSchemaUtils` shadow 为禁兼容层显式例外，退出路径（独立坐标 fork 或上游 PR）另立条目进改进意见池。
  - 阶段约束的三条偏离在恢复后从计划文档标记为已关闭。
- **完成标准**：无文档与实现冲突残留；例外与改进项均有登记位置。

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入

## 评审状态（🔴 高风险适用）

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | — | — | — | 无新需求 |
| architect（技术设计） | 0 | — | ⏳ PENDING | 🔴 是 |
| designer（UI 审查） | — | — | — | 不涉及前端 |
| developer（编码） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |
