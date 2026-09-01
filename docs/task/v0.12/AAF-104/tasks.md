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

### #10407 事件身份维度补全（#10403 硬前置）

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#10402
- **为什么独立成任务**：它同时是「协调者/执行者事件区分」与「按 agent 类型监控」两件事的共同前置，且影响面超出事件投影——`ExecutionEvent` 本体、全部构造点、事件表 schema 与公共事件都要动。核实结果：`ExecutionEvent` 现有 21 个字段中身份相关的只有 `executionId`/`parentExecutionId`/`ownerType`/`assistantId`/`agentId`/`taskId`，**没有 `subTaskId`、`kind`、`roleKey`、`skillKey`**（`shared/` 包下这三个词分别只出现 2/1/2 次，均不在事件结构内）；`AafAiTaskEvent` 连 `agentId` 都没透出。
- **范围**：
  - `ExecutionEvent` 新增一个可空的聚合字段 `NodeIdentity(subTaskId, kind, roleKey, skillKey)`，而不是平铺 4 个字段——DIRECT 模式无 TaskBoard 时整体为 `null`，语义是"本事件属于某个 TaskBoard 节点"。
  - 在子任务派发处填充：`SubTask` 已有全部四项（`TaskBoard.java` 的 `SubTask` record），随 child command 传到执行适配器与事件映射器。
  - 事件表 schema 与 JPA 实体同步（按 v0.12 阶段约束直接改 `v16__intelligent_runtime_schema.sql`）。
  - `AafAiTaskEvent` 透出 `parentExecutionId` 与 `NodeIdentity`，供 `#10403` 合成 `source` 路径与判定标准/CUSTOM 分支。
- **安全约束（🔴 必须遵守）**：
  - `subTaskId` 在动态分解模式下是**模型生成的自由字符串**（`CoordinationPlan.ExecutorAssignment.subTaskId` 来自 plan JSON，经 `decodeAndValidatePlan` 仅校验格式）。它只能作为展示用标签与 `source` 路径段，**禁止用作授权、隔离或幂等键**——那些仍用 `executionId`/`tenantId`。且需评估模型自选名称是否泄漏内部意图。
  - `roleKey`/`skillKey` 是配置定义的稳定值，是监控聚合应使用的维度；`agentId` 是 Agent 定义 ID，不等于"角色"。
  - 静态 Team 模式下 `subTaskId`/`roleKey`/`skillKey` 必须匹配预定义 worker（`TaskBoard.java:124`），协调者无命名权——两种模式的命名权差异要在实现中区分。
- **完成标准**：事件可按 `roleKey` 聚合；`source` 路径可从 `parentExecutionId` + `subTaskId` 合成；DIRECT 模式 `NodeIdentity` 为 null 且行为不变；`compile` + `test` 全绿。
- **待人类确认**：事件表已有存量数据时的兼容处理（阶段约束是直接改 v16 不新增迁移，但存量事件行的新列为 null 是否可接受）。

### #10403 补齐必需事件族

- **状态**：[ ] 待开始（🔴 契约需 architect 先审，见下方"契约修正"）
- **负责人**：developer-service
- **依赖**：#10402、#10405（勘误已完成）
- **契约修正（2026-09-01，据官方文档与源码核实）**：
  - **节点区分采用官方 `source` 路径约定，由 AAF 自行合成**。core 的 `AgentEvent.getSource()` 在 AAF 平级编排下对所有节点恒为 `null`（那是嵌套委派模型的产物），但 `source` 只是约定，AAF 用内部已有的 `parentExecutionId` 自行合成同形状路径即可。参考 <https://java.agentscope.io/v2/zh/docs/harness/subagent.html#streamevents>
  - **线协议分两类，对齐官方 `SubagentEventConverter`**：根节点（`parentExecutionId == null`）发标准 `TEXT_MESSAGE_*`/`TOOL_CALL_*`；非根节点一律降级 `CUSTOM` 并在 payload 带 `source` + 原始事件类型。理由：`TEXT_MESSAGE` 语义是"最终助手回复"，执行者中间推理混入会被客户端当主回复渲染。
  - **连带简化**：只有根节点发 `TEXT_MESSAGE`，messageId 跨执行冲突消失，`replyId:blockId` 即够；不再需要三段派生。
  - **前置阻塞项**：`AafAiTaskEvent` 必须补 `parentExecutionId` 与节点归属，否则投影层无法判断分支也无法合成 `source`。该字段涉及"内部标识可否对外暴露"，🔴 需人类确认暴露形式（原始 agentId vs 稳定节点标签）。
  - **借鉴 `remoteStreamDetail`**：Activity/Step 引入 `STATUS`/`FULL`/`VERBOSE` 类详细度档位，多节点并行时不一刀切全发。
  - **待确认**：面向用户的应答者是协调者还是聚合者（存在 AGGREGATOR 概念），判定规则暂定 `parentExecutionId == null`，实施前与 `runtime.md` 交付责任定义核对。
- **范围**：
  - `AafAiTaskEvent` 补 `parentExecutionId` + 节点归属（前置）。
  - `TOOL_CALL_ARGS`：按 schema 脱敏后的 JSON delta；无法逐片安全脱敏时缓冲成完整对象再发一个 delta。
  - `STATE_SNAPSHOT/DELTA`：新增安全 `AgUiStateView`，从 TaskBoard snapshot 与公共事件生成；禁止回吐 `forwardedProps`、凭据、Prompt 或完整 TaskBoard。
  - `STEP_STARTED/FINISHED`：为 planning / execution / verification / aggregation 稳定阶段成对产出。
  - `MESSAGES_SNAPSHOT`、`ACTIVITY_SNAPSHOT/DELTA`、`RUN_FINISHED` 成功与 interrupt outcome。
  - 非根节点 CUSTOM 投影 converter（对齐官方形状）。
  - `TEXT_MESSAGE_*` 的 messageId 改用 `replyId:blockId` 派生。
  - 落地 #10401 欠的 8 项事件映射（`TEXT_BLOCK_END`、`TOOL_CALL_DELTA/END`、`TOOL_RESULT_START/TEXT_DELTA`、`USER_CONFIRM_RESULT`、`EXTERNAL_EXECUTION_RESULT`、`REQUIRE_EXTERNAL_EXECUTION`），并把 `replyId`/`blockId` 写入 payload。
  - 统一 converter 输入为公共事件，修掉 #10402 记录的脱敏缺口（`MESSAGE_DELTA` 正文当前绕过 `ExecutionEventPublicMapper`）。
  - 评估用官方 `AguiStreamContext` 替换自建 `AafAguiStreamContext` 的 ReActAgent 侧部分（见下）。
- **完成标准**：官方 `@ag-ui/core` schema 形状断言通过；重连 snapshot 与增量一致；根/非根事件分类可验证；`compile` 通过。
- **已核实无需处理**：执行者失败不中断整板流——`DelegatedTaskCoordinator:513-517` 的 `onErrorResume` 已就地吞掉子流异常转 `Flux.empty()`，`onError` 不传播到 `executeBoard` 的 `flatMap`，与官方"子 agent 出错写 TOOL_RESULT、不传播 onError"等价。
- **顺带发现（不并入本任务）**：该 `onErrorResume` 把错误摊平为 `"子任务执行异常"` 字符串，丢弃了 `AgentScopeFailureClassifier` 已算出的 `failureCategory`/`retryable`，导致协调者无法区分"可重试的模型超时"与"不可重试的规格非法"。属编排层失败语义问题，非事件投影，建议独立登记。

### #10403b 官方 AguiStreamContext 复用评估

- **状态**：[ ] 待开始
- **负责人**：architect + developer-service
- **依赖**：#10402
- **背景**：官方 `AguiStreamContext`（373 行）已覆盖 ReActAgent 侧全部配对面——text / reasoning / toolCall args / toolResult text 与 data / suspended / interrupt / `finishPendingEvents`，正是 #10403 要新建的东西。自建的 `AafAguiStreamContext` 只 135 行且功能是其子集。
- **可行性已验证**：构造函数 `public AguiStreamContext(String threadId, String runId, AguiAdapterConfig config)` 公开；`config` 与 `runInput` 只存放不参与逻辑（全文除构造赋值与 getter 外零引用），不被官方入参模型绑住；发射模型 `emit()` + `drainEvents()` 可对接 AAF 现有 `List<AguiEvent>` 返回风格。
- **边界**：官方 context **完全不含** `Custom`/`Activity`/`Step`/`StateSnapshot`/`MessagesSnapshot` 出口（grep 零命中），即**只覆盖 ReActAgent 侧，不覆盖 AAF 编排层事件**。因此结论是分层复用而非整体替换：ReActAgent 侧复用官方，编排层侧保留 AAF 自建并收窄职责。
- **必须核对的默认值**（`AguiAdapterConfig`，属"builder 默认值必须验证"规则）：`emitStateEvents=true`（需关，用 AAF 的 state view）、`emitToolCallArgs=true`（**必须关**，否则工具参数原样外发绕过 schema 脱敏）、`enableReasoning=false`（与"CoT 默认不外发"一致，保持）、`emitTokenUsage=false`、`baseEventPropertiesEnricherEnabled=false`、`emitSubagentEventsAsNative=false`。
- **完成标准**：给出"复用/不复用"结论与依据；若复用，`AafAguiStreamContext` 收窄为只管编排层事件出口。

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
