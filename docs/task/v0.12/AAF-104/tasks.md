---
level: Practice
layer: Product
purpose: 拆分 AAF-104 AG-UI 事件完整性的映射补齐、converter 结构与持久 resume 任务
status: draft
version: 1.0.0
date: 2026-09-01
author: AaronZZH & Kiro
tags:
  - AAF-104
  - AG-UI
  - 事件
  - 技术任务
related:
  - ../../../design/audit/2026-09-01-harness-landing-plan.md
  - ../../../design/framework/intelligent/runtime-event.md
gains:
  - 能让 AG-UI 出口被标准 @ag-ui/client 完整消费
  - 能让中断与恢复跨进程、跨副本可用
---

# AAF-104 AG-UI 事件完整性任务

## 任务约束

- 技术真理源：[Harness 落地计划 · 主线：AG-UI 事件完整性](../../../design/audit/2026-09-01-harness-landing-plan.md)。
- 事件契约真理源：[runtime-event.md](../../../design/framework/intelligent/runtime-event.md)。
- 风险等级：🔴 高（对外协议 + 安全披露 + 持久 HITL）。
- 只复用官方事件 DTO、`AguiEventEncoder` 与 wire contract；不引入 starter / `AguiMvcController` / 官方内存 `AguiResumeCoordinator`。
- 不为凑"28/28"发 `RAW`、deprecated THINKING alias、内部 CoT 或冗余 `*_CHUNK`。
- **阶段约束**：断言补进既有 `AgUiProjectorTest`，不新增测试文件；不执行 `check` / `acceptance`；准出最低要求 `pnpm nx compile service` 通过。

## 技术任务

### #10401 AgentEvent 31 项显式处置

- **状态**：⏳ 进行中（2026-09-01）— developer-service
- **负责人**：developer-service
- **依赖**：AAF-103 #10301
- **范围**：
  - ✅ `AgentScopeEventMapper` 对 `AgentEventType` 全部 31 项建立"映射 / 安全忽略 / 不应出现"策略，**删除 `default` 分支**——穷举 switch 使上游新增枚举常量直接编译失败。
  - ✅ `THINKING_BLOCK_*` 在本层拦截并注明原因（与 AAF-106 #10603 同一约束，只实现一次）；`DATA_BLOCK_*` 与 `TOOL_RESULT_DATA_DELTA` 安全忽略（二进制不进事件账本）；`SUBAGENT_EXPOSED` 记配置漂移 WARN。
  - [ ] 补齐 8 项"待映射"类型：`TEXT_BLOCK_END`、`TOOL_CALL_DELTA/END`、`TOOL_RESULT_START/TEXT_DELTA`、`USER_CONFIRM_RESULT`、`EXTERNAL_EXECUTION_RESULT`、`REQUIRE_EXTERNAL_EXECUTION`——**与 #10403 合并实施**，因其落点取决于该任务确定的配对契约（messageId 改 `replyId:blockId` 派生）。
- **完成标准**：枚举新增项会使既有测试失败；公共事件流不含思考内容；`compile` 通过。
- **实际结果**：改为编译器强制穷举，比测试断言更硬；`compile` + `test` 全绿（framework 415 / auto-dev 2 / api 241）。

### #10402 AAF converter registry 结构

- **状态**：[x] ✅ 已完成（2026-09-01）— developer-service
- **负责人**：developer-service
- **依赖**：#10401
- **范围**：
  - ✅ 新建 `AafAguiEventConverter`（接口）、`AafAguiConverterRegistry`（枚举分派 + 重复注册拒绝）、`AafAguiStreamContext`（per-run 配对状态与全部事件发射入口）。
  - ✅ 三个 converter：`RunLifecycleEventConverter`、`TextMessageEventConverter`、`PublicEventFallbackConverter`（工具三段式 + CUSTOM 兜底，按公共事件 `type` 字符串二次分派）。
  - ✅ `AgUiProjector` 瘦身为 facade（248 → 107 行）：只建 context、调 registry、流尾闭合、异常终结，不含任何事件构造逻辑。
  - ✅ registry 启动时拒绝重复 `ExecutionEventType` 注册（构造即失败，不采用后注册静默覆盖）。
  - ✅ `Session` 新增跨 run 复用检测：threadId/runId 不一致直接失败。
  - [ ] 每个 converter 的独立单测——**与 #10403 一并补**（届时 converter 输入与 messageId 派生规则会变，先写会返工）。
- **完成标准**：每个 converter 独立单测；同 run 的 start/end 配对且 finish 只一次。
- **实际结果**：本次为**纯结构重构、行为不变**；`AgUiProjectorTest` 8 个既有用例全绿（原地验证配对与 finish 不变量），三模块 415/2/241 全绿。

### #10403 补齐必需事件族

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10402
- **范围**：
  - `TOOL_CALL_ARGS`：按 schema 脱敏后的 JSON delta；无法逐片安全脱敏时缓冲成完整对象再发一个 delta。
  - `STATE_SNAPSHOT/DELTA`：新增安全 `AgUiStateView`，从 TaskBoard snapshot 与公共事件生成；禁止回吐 `forwardedProps`、凭据、Prompt 或完整 TaskBoard。
  - `STEP_STARTED/FINISHED`：为 planning / execution / verification / aggregation 稳定阶段成对产出。
  - `MESSAGES_SNAPSHOT`、`ACTIVITY_SNAPSHOT/DELTA`、`RUN_FINISHED` 成功与 interrupt outcome。
  - `TEXT_MESSAGE_*` 的 messageId 改用 `replyId:blockId` 派生，支持单 execution 多文本块。
- **完成标准**：官方 `@ag-ui/core` schema 形状断言通过；重连 snapshot 与增量一致；`compile` 通过。

### #10404 持久 interrupt / resume 闭环

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10403
- **范围**：
  - 请求 record 增加 `resume[]`，字段严格为 `interruptId/status/payload`；`status` 只允许 `RESOLVED`/`CANCELLED`，业务拒绝是 `RESOLVED + {approved:false}`。
  - Controller 在启动新 execution 前以 `(tenantId, threadId, interruptId)` 原子 resolve AAF 持久记录，校验调用人、过期时间、run 关联与精确全集。
  - 不实例化官方 `AguiResumeCoordinator` 作为状态仓库（其状态仅进程内 `ConcurrentHashMap`）。
- **完成标准**：跨重启/跨副本可恢复；缺失、重复、未知、过期 interrupt 全部 fail closed；`compile` 通过。

### #10405 ADR-005 勘误提案

- **状态**：[x] ✅ 已完成（2026-09-01）— Kiro
- **负责人**：协调者
- **依赖**：无
- **范围**：核实官方 starter 扩展点后为 ADR-005 准备勘误，保留"不采用 starter"结论并更换论据。
- **完成标准**：已写入 ADR-005 v1.2.0「勘误与决策补充（2026-09-01 核实）」。

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入

## 评审状态（🔴 高风险适用）

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（技术设计） | 0 | — | ⏳ PENDING | 🔴 是 |
| designer（UI 审查） | 0 | — | ⏳ PENDING | 涉及前端消费时 |
| developer（编码） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |
