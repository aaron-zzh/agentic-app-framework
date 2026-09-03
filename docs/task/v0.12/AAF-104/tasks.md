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

- **状态**：✅ 已完成（2026-09-01）— developer-service
- **负责人**：developer-service
- **依赖**：AAF-103 #10301
- **范围**：
  - ✅ `AgentScopeEventMapper` 对 `AgentEventType` 全部 31 项建立"映射 / 安全忽略 / 不应出现"策略，**删除 `default` 分支**——穷举 switch 使上游新增枚举常量直接编译失败。
  - ✅ `THINKING_BLOCK_*` 在本层拦截并注明原因（与 AAF-106 #10603 同一约束，只实现一次）；`DATA_BLOCK_*` 与 `TOOL_RESULT_DATA_DELTA` 安全忽略（二进制不进事件账本）；`SUBAGENT_EXPOSED` 记配置漂移 WARN。
  - ✅ 8 项"待映射"类型已在 #10403 第二增量补齐：`TEXT_BLOCK_END`、`TOOL_CALL_DELTA/END`、`TOOL_RESULT_START/TEXT_DELTA`、`USER_CONFIRM_RESULT`、`EXTERNAL_EXECUTION_RESULT`、`REQUIRE_EXTERNAL_EXECUTION`。
- **完成标准**：枚举新增项会使既有测试失败；公共事件流不含思考内容；`compile` 通过。
- **实际结果**：改为编译器强制穷举，比测试断言更硬；`compile` + `test` 全绿（framework 415 / auto-dev 2 / api 241）。8 项待映射已随 #10403 第二增量落地，31 项全部显式处置完成。

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

- **状态**：[x] ✅ 已完成（2026-09-01）— developer-service
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
- **实际结果**：链路端到端贯通（`SubTask` → `AssistantCommand.forSubTask` → `InvocationContext` → `ExecutionEvent` → `AafAiTaskEvent`）；`compile` 六模块全绿。暴露形式取"稳定/展示标签"一侧——透出 `subTaskId`/`kind`/`roleKey`/`skillKey`，`agentId` 仍只留内部。
- **DB 无需改动**：`ExecutionEventEntity` 把整个 `ExecutionEvent` 存为 JSONB `event_payload`（表 `ai_task_event`），仅提升 7 个字段为索引列。新增字段自动进 JSONB，存量行缺键即 null，原"存量数据兼容"待确认项自行消解。若将来需按 `roleKey` 做 SQL 聚合，加 JSONB 表达式索引即可，属优化非阻塞。
- **纠正**：`TaskBoard.Kind` 实为四值（COORDINATOR / EXECUTOR / EVALUATOR / AGGREGATOR），此前误判为两值。`NodeKind` 已补齐并按交付类/内部类分组；由此产生的「每板至多一个交付类节点」不变量待 #10403 确认。

### #10403 补齐必需事件族

- **状态**：⚠️ 部分完成（2026-09-02 核实同步）——多项子任务已完成但未同步记录，剩余真实缺口见下
- **负责人**：developer-service
- **依赖**：#10402、#10405（勘误已完成）
- **契约修正（2026-09-01，据官方文档与源码核实）**：
  - **节点区分采用官方 `source` 路径约定，由 AAF 自行合成**。core 的 `AgentEvent.getSource()` 在 AAF 平级编排下对所有节点恒为 `null`（那是嵌套委派模型的产物），但 `source` 只是约定，AAF 用内部已有的 `parentExecutionId` 自行合成同形状路径即可。参考 <https://java.agentscope.io/v2/zh/docs/harness/subagent.html#streamevents>
  - **线协议分两类，对齐官方 `SubagentEventConverter`**：根节点（`parentExecutionId == null`）发标准 `TEXT_MESSAGE_*`/`TOOL_CALL_*`；非根节点一律降级 `CUSTOM` 并在 payload 带 `source` + 原始事件类型。理由：`TEXT_MESSAGE` 语义是"最终助手回复"，执行者中间推理混入会被客户端当主回复渲染。
  - **连带简化**：只有根节点发 `TEXT_MESSAGE`，messageId 跨执行冲突消失，`replyId:blockId` 即够；不再需要三段派生。
  - ~~**前置阻塞项**：`AafAiTaskEvent` 必须补 `parentExecutionId` 与节点归属，否则投影层无法判断分支也无法合成 `source`。该字段涉及"内部标识可否对外暴露"，🔴 需人类确认暴露形式（原始 agentId vs 稳定节点标签）。~~ ✅ **已在 #10407 解决**：暴露形式取"稳定/展示标签"一侧——透出 `subTaskId`/`kind`/`roleKey`/`skillKey`，`agentId` 仍只留内部（见 #10407"实际结果"）。
  - **借鉴 `remoteStreamDetail`**：Activity/Step 引入 `STATUS`/`FULL`/`VERBOSE` 类详细度档位，多节点并行时不一刀切全发。——**仍未落地**，见下方剩余范围。
  - **待确认**：面向用户的应答者是协调者还是聚合者（存在 AGGREGATOR 概念），判定规则暂定 `parentExecutionId == null`，实施前与 `runtime.md` 交付责任定义核对。——**仍待确认**。
- **已确认完成的部分**（2026-09-02 核实，此前未记录在本文档）：
  - ✅ **非根节点 CUSTOM 投影 converter**：`InternalNodeEventConverter.java`（`apps/service/aaf-api/.../module/ai/agui/converter/`）已实现，按事件族分 `aaf.node.lifecycle`/`aaf.node.message`/`aaf.node.tool`/`aaf.node.event` 四类 CUSTOM 事件名，payload 带 `source`（`NodeIdentity.sourcePath()`）、`nodeKind`、`roleKey`、原始类型与状态，数据一律取自 `ExecutionEventPublicMapper` 已脱敏的公共事件。
  - ✅ **`RUN_FINISHED`**：`AafAguiStreamContext.runFinishedOnce()` + `RunLifecycleEventConverter` 已实现，保证同一 run 只发一次终态。
- **仍未落地的真实剩余范围**（2026-09-03 更新）：
  - `TOOL_CALL_ARGS`：按 schema 脱敏后的 JSON delta；无法逐片安全脱敏时缓冲成完整对象再发一个 delta。**仍未实现**，本轮判断无 schema 输入暂不做（见下方"本轮范围收尾"）。
  - ✅ **`STATE_SNAPSHOT/DELTA`已实现**（2026-09-03）：`AafAguiStreamContext.subTaskStateChanged` 在内存维护子任务状态表（不引入 `TaskBoardPort` 依赖，完全靠事件流自带的 `nodeIdentity`+`status` 增量重建，与既有 `startedMessages`/`startedToolCalls` 同一模式），复用官方 `AguiStateConverter`（`agentscope-extensions-agui` 包内纯函数 RFC 6902 diff 工具，不违反"只用 AguiEvent/AguiEventType/AguiEventEncoder"边界——不涉及 Agent/Workspace 耦合）算增量。首次发 `StateSnapshot` 全量，后续无变化不发噪声事件，有变化发 `StateDelta`。接入 `InternalNodeEventConverter`，子任务 `EXECUTION_STARTED/COMPLETED/FAILED/CANCELED` 时作为 CUSTOM 投影附加产出。State 形状对齐 assistant-ui `useAgUiState` 的任意自定义 JSON 约定：`{subTasks: [{subTaskId, kind, roleKey, status}]}`，不含目标文本/`forwardedProps`/凭据/完整 TaskBoard。
  - ✅ **`STEP_STARTED/FINISHED`已实现**（2026-09-03）：新建 `StepEventConverter` 处理 `EXECUTOR_PLAN_CREATED/SUBMITTED`（planning）与 `VALIDATION_STARTED/COMPLETED/FAILED`（verification）；`execution` 阶段边界移至 `InternalNodeEventConverter` 内部作为 CUSTOM 投影附加产出（子任务 `EXECUTION_STARTED/COMPLETED/FAILED/CANCELED`），不新增独立 converter 抢占该类型分派权（避免与 `RunLifecycleEventConverter` 的 per-run 语义冲突）。**不区分 aggregation 阶段**（已核实确认：`AGGREGATOR_REDUCE` 契约下 `AGGREGATOR` 节点自己就是唯一交付者，走根节点路径而非内部节点降级，不需要独立 Step 包装；`PASS_THROUGH`/`ORDERED_CONCAT` 无独立聚合工作）。为支持 Step 事件对根/非根节点一致投影，`AafAguiEventConverter` 新增默认方法 `bypassesInternalNodeDowngrade()`（默认 `false`）。**过程中发现并清理死代码**：`SUBTASK_CREATED/STARTED/COMPLETED/FAILED/CANCELED` 五个 `ExecutionEventType` 枚举值全仓核实确认从未被任何生产代码发出（`DelegatedTaskCoordinator` 落地前的预留占位，实际改用 `EXECUTION_STARTED/COMPLETED`+`nodeIdentity` 区分节点），已彻底删除（含 `AafAiTaskEventRegistry` 对应 5 个 switch 分支、`TurnOutcomeAggregator` 的 `SUBTASK_CREATED` 分支）。
  - ✅ **`MESSAGES_SNAPSHOT` 判定不做**（2026-09-03 核实确认）：官方语义是"重连/初始化聊天历史"，但 webui 侧已用 `UseAgUiThreadListAdapter.onSwitchToThread`（assistant-ui 官方历史加载专用适配点）+ 独立 REST `GET /sessions/thread/{threadId}/messages`（`ChatController`/`ChatService` 既有能力）完整解决同一问题，且 `AssistantAguiController./run` 当前不支持"重连到已有 run"（每次 SSE 连接对应一次新 execution）。若引入 `MessagesSnapshot` 会产生两套并行历史加载机制，违反"优先已有模式，禁止并行抽象"硬约束。
  - ✅ **`TEXT_MESSAGE_*` 的 messageId 已改用 `replyId:blockId` 派生**（2026-09-03）：`TextMessageEventConverter` 改造，新增支持 `MESSAGE_BLOCK_COMPLETED`（对应 `TEXT_BLOCK_END`，块级收尾）触发块级 `TextMessageEnd`；`MESSAGE_COMPLETED`（`AGENT_RESULT`，整次回复结束，无 `blockId`）不再触发块级 END，交给 `AafAguiStreamContext.close()` 流尾兜底闭合未闭合的块。
  - ✅ **`nodeIdentity` 传播缺口全面修复**（2026-09-03，本轮意外发现并修复的前置缺口）：核实确认 `AssistantApplicationService.event()`（任务级 `EXECUTION_*`/`VALIDATION_*` 事件唯一构造入口）此前一直用不带 `nodeIdentity` 的旧版 `ExecutionEvent` 构造器，导致子任务的任务级事件恒为 `null`，会被误判成根节点/DIRECT。已修复全部 10 处直接构造调用点（`AssistantApplicationService.event()`、`DelegatedTaskCoordinator` 三处、`SupportHandoffTool`、`PersistentHitlCoordinator`、`JpaTaskTransitionAdapter` 三处、`DefaultToolGateway`、`SynchronousExecutionEventWriter.withSequence`），确认无需修复（原本已带）：`AgentScopeEventMapper` 全部事件、`ReportExecutorStepTool`、`SubmitExecutorPlanTool`、`DelegatedTaskCoordinator.emitPlanEvent`。这是 `STEP_STARTED/FINISHED` 与 `STATE_SNAPSHOT/DELTA` 能在真实环境正确工作的必要前提。
  - ~~统一 converter 输入为公共事件，修掉 #10402 记录的脱敏缺口（`MESSAGE_DELTA` 正文当前绕过 `ExecutionEventPublicMapper`）~~ ✅ **已核实非缺口，是刻意设计（2026-09-02）**：`ExecutionEventPublicMapper` 对 `MESSAGE_DELTA` 只放 `contentLength`（长度），这是给内部审计事件流（`AafAiTaskEvent`）用的通用脱敏规则；AG-UI 的 `TextMessageEventConverter` 直接读取内部原始 `delta` 正文是正确行为——`MESSAGE_DELTA` 是"助手回复给用户的最终正文"，理应完整透出，不能套用内部审计流的"仅长度"规则（那会导致用户看不到 AI 回复内容）。两条路径的差异化处理符合设计意图，不是绕过脱敏的漏洞。
  - 评估用官方 `AguiStreamContext` 替换自建 `AafAguiStreamContext` 的 ReActAgent 侧部分——已拆到 #10403b 独立评估，见下。
  - ✅ **第二增量已完成**：落地 #10401 欠的 8 项事件映射（`TEXT_BLOCK_END`、`TOOL_CALL_DELTA/END`、`TOOL_RESULT_START/TEXT_DELTA`、`USER_CONFIRM_RESULT`、`EXTERNAL_EXECUTION_RESULT`、`REQUIRE_EXTERNAL_EXECUTION`），并把 `replyId`/`blockId`/`toolCallId` 写入 payload（`AgentScopeEventMapper` 内，纯 aaf-framework 侧，不碰对外协议）。详见下方"第二增量实现记录"。
  - ✅ **`ACTIVITY_SNAPSHOT/DELTA` 已实现**（2026-09-03）：`AafAguiStreamContext.subTaskActivityChanged` 在 `InternalNodeEventConverter` 内接入，子任务 `EXECUTION_STARTED/COMPLETED/FAILED/CANCELED` 时作为 CUSTOM 投影附加产出。`messageId` 用 `subTaskId` 本身（占用与文本消息平级但互不冲突的 id 空间），`activityType` 固定 `"SUBTASK"`；首次发 `ActivitySnapshot` 全量，后续用 `AguiStateConverter.createDelta` 对比该子任务上一次已发布 content 算增量发 `ActivityDelta`，无变化不发。**已核实并记录已知前端缺口**（`docs/prd/improvements.md` 2026-09-03 条目）：assistant-ui 的 `useAgUiRuntime` 目前只原生渲染 `activityType="a2ui-surface"`（配合官方 A2UI 生成式 UI 协议），其它 `activityType`（包括本实现的 `"SUBTASK"`）会被静默忽略不产生 UI，需要 webui 侧另行注册 `onActivitySnapshotEvent`/`onActivityDeltaEvent` 订阅与卡片组件才能真正对用户可见——后端投影已完整按协议标准落地，不阻塞该前端排期。**未做 `STATUS`/`FULL`/`VERBOSE` 详细度档位**：核实确认 `remoteStreamDetail` 是官方 subagent 声明时的静态配置字段（构建期由声明方决定，非运行时协商），且 `InternalNodeEventConverter` 现有"CUSTOM 兜底 + 已脱敏公共事件"已经解决了"要不要把内部细节透给客户端"的核心问题，本轮固定单一详细度、不引入可配置项（不为假设需求预留）。
- **完成标准**：官方 `@ag-ui/core` schema 形状断言通过；重连 snapshot 与增量一致；根/非根事件分类可验证；`compile` 通过。
- **已核实无需处理**：执行者失败不中断整板流——`DelegatedTaskCoordinator:513-517` 的 `onErrorResume` 已就地吞掉子流异常转 `Flux.empty()`，`onError` 不传播到 `executeBoard` 的 `flatMap`，与官方"子 agent 出错写 TOOL_RESULT、不传播 onError"等价。
- **顺带发现（不并入本任务）**：该 `onErrorResume` 把错误摊平为 `"子任务执行异常"` 字符串，丢弃了 `AgentScopeFailureClassifier` 已算出的 `failureCategory`/`retryable`，导致协调者无法区分"可重试的模型超时"与"不可重试的规格非法"。属编排层失败语义问题，非事件投影，建议独立登记。

#### 第二增量实现记录（2026-09-01）

- **新增 `ExecutionEventType` 7 项**：`MESSAGE_BLOCK_COMPLETED`（TEXT_BLOCK_END）、`TOOL_CALL_ARGS_DELTA`（TOOL_CALL_DELTA）、`TOOL_CALL_ARGS_COMPLETED`（TOOL_CALL_END）、`TOOL_RESULT_STARTED`（TOOL_RESULT_START）、`TOOL_RESULT_DELTA`（TOOL_RESULT_TEXT_DELTA）、`EXTERNAL_EXECUTION_REQUESTED`（REQUIRE_EXTERNAL_EXECUTION）、`EXTERNAL_EXECUTION_SUPPLIED`（EXTERNAL_EXECUTION_RESULT）。`USER_CONFIRM_RESULT` 未新增类型，复用既有 `APPROVAL_RESOLVED`（与 `REQUIRE_USER_CONFIRM → APPROVAL_REQUESTED` 配对，同一 replyId）。
- **`MESSAGE_BLOCK_COMPLETED` 与 `MESSAGE_COMPLETED` 分离**：严格按契约修正——`TEXT_BLOCK_END`（回复内某一文本块结束）与 `AGENT_RESULT`（整次回复结束）各自一次配对，不合并成同一类型，避免破坏 AG-UI start/end 配对。
- **`TOOL_CALL_ARGS_*` 与 `TOOL_CALL_COMPLETED/FAILED` 分离**：`TOOL_CALL_END`（入参流结束）与工具执行结果的成功/失败是不同阶段，前者只表示"入参已拼齐"，不能复用后者的类型。
- **敏感增量正文不透出**：`TOOL_CALL_DELTA`（工具入参增量）与 `TOOL_RESULT_TEXT_DELTA`（工具执行文本输出增量）都只落 `deltaLength`，不落原始内容——前者可能是模型从上下文摘取的业务字段值，后者是工具执行的业务产出，两者都需按 schema/证据规则脱敏，而脱敏规则由 AG-UI 投影层（#10403 剩余部分）决定，本层无 schema 可用。与既有 `mapToolStart`"入参不出边界"的处置一致。
- **`replyId`/`blockId` 补齐到既有映射**：`TEXT_BLOCK_START`（原 payload 为空）、`TEXT_BLOCK_DELTA`（原只有 `replyId`）现都携带完整的 `replyId`/`blockId`，供后续 messageId 改 `replyId:blockId` 派生。`AGENT_RESULT` 未加 `replyId`——核实 `AgentResultEvent` 源码后确认它不携带该字段（只有 `Msg result`），不能凭空捏造。
- **`USER_CONFIRM_RESULT`/`EXTERNAL_EXECUTION_RESULT` 只暴露标识不暴露正文**：确认结果只给工具名与确认计数（`ConfirmResult.toolCall` 的修改后入参不出边界）；外部执行结果只给 `toolCallId` 列表与结果数量（`ToolResultBlock` 正文不出边界），与 `mapToolResult` 对工具证据的既有处置一致。
- **`AafAiTaskEventRegistry.descriptor` 同步扩展**：该 switch 与 mapper 同样无 `default`，新增 7 项类型必须同批补齐才能编译；`ExecutionEventPublicMapper.safeData` 有 `default` 分支不强制扩展，新类型的公共数据字段留给 #10403 剩余的 AG-UI 投影契约决定，本次不越权设计。
- **验证**：未跑 `pnpm nx compile service` / `pnpm nx test service`（人类要求本次不执行测试及 lint，手动验证）。人工复核了两处穷举 switch（`AgentScopeEventMapper.map`、`AafAiTaskEventRegistry.descriptor`）覆盖全部新增常量，无 `default` 分支遗漏；`ExecutionEventPayload` key 校验规则确认新键名（`deltaLength`/`toolCallIds`/`resultCount`/`confirmedCount`/`totalCount`）不触发敏感字段拦截。

### #10403b 官方 AguiStreamContext 复用评估

- **状态**：✅ 已完成（2026-09-03 核实确认）— Kiro
- **负责人**：architect + developer-service
- **依赖**：#10402
- **背景**：官方 `AguiStreamContext`（373 行）已覆盖 ReActAgent 侧全部配对面——text / reasoning / toolCall args / toolResult text 与 data / suspended / interrupt / `finishPendingEvents`，正是 #10403 要新建的东西。自建的 `AafAguiStreamContext` 只 135 行且功能是其子集。
- **可行性已验证**：构造函数 `public AguiStreamContext(String threadId, String runId, AguiAdapterConfig config)` 公开；`config` 与 `runInput` 只存放不参与逻辑（全文除构造赋值与 getter 外零引用），不被官方入参模型绑住；发射模型 `emit()` + `drainEvents()` 可对接 AAF 现有 `List<AguiEvent>` 返回风格。
- **边界**：官方 context **完全不含** `Custom`/`Activity`/`Step`/`StateSnapshot`/`MessagesSnapshot` 出口（grep 零命中），即**只覆盖 ReActAgent 侧，不覆盖 AAF 编排层事件**。因此结论是分层复用而非整体替换：ReActAgent 侧复用官方，编排层侧保留 AAF 自建并收窄职责。
- **必须核对的默认值**（`AguiAdapterConfig`，属"builder 默认值必须验证"规则）：`emitStateEvents=true`（需关，用 AAF 的 state view）、`emitToolCallArgs=true`（**必须关**，否则工具参数原样外发绕过 schema 脱敏）、`enableReasoning=false`（与"CoT 默认不外发"一致，保持）、`emitTokenUsage=false`、`baseEventPropertiesEnricherEnabled=false`、`emitSubagentEventsAsNative=false`。
- **完成标准**：给出"复用/不复用"结论与依据；若复用，`AafAguiStreamContext` 收窄为只管编排层事件出口。
- **实际结论（2026-09-03）**：**不复用，维持自建 `AafAguiStreamContext`**——已完成的 Step/State/Activity 三项投影（本轮 #10403 落地）恰好全部是"编排层事件"（子任务级，`nodeIdentity != null`，不是 ReActAgent 单次调用级），与官方 `AguiStreamContext` 的覆盖范围（ReActAgent 侧配对面）完全不重叠，"边界"一节给出的"分层复用而非整体替换"结论在实践中得到验证——AAF 当前不存在需要复用官方 ReActAgent 侧配对逻辑的场景（`AgentScopeEventMapper` 已经把 core 事件映射为 AAF 内部 `ExecutionEvent`，`AafAguiStreamContext` 消费的是这一层，不是原始 core 事件），无需再评估默认值核对与迁移工作量。

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
