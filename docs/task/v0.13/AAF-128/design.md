---
level: Practice
layer: Model
purpose: AAF-128 轻量态遇授权工具自动升级并携带待执行调用、协调者首轮重放与 HITL 等待收口的技术设计
status: draft
version: 1.0.0
date: 2026-11-12
author: AaronZZH & Kiro
tags:
  - AAF-128
  - 任务升级
  - ToolGateway
  - HITL
  - 技术设计
related:
  - tasks.md
  - ../../../design/adr/ADR-008-task-execution-shape-and-streaming.md
  - ../../../design/adr/ADR-011-assistant-single-entry-and-on-demand-role.md
  - ../AAF-115/design.md
  - ../AAF-117/tasks.md
  - ../AAF-127/tasks.md
---

# 设计：轻量态遇授权工具自动升级并携带待执行调用（AAF-128）

> 风险等级 🔴（≥5 文件、跨 framework/api、改接口签名、触及授权边界）。本文经人类审核前不开发。

## 参考文档

- 需求：[backlog AAF-128](../../backlog.md)
- 架构决策：[ADR-008 DIRECT 与 TASK 的关系](../../../design/adr/ADR-008-task-execution-shape-and-streaming.md)、[ADR-011](../../../design/adr/ADR-011-assistant-single-entry-and-on-demand-role.md)
- 运行时合同：[AAF-115 design](../AAF-115/design.md)（promotion 与副作用互斥、HITL 短事务与 fencing）
- 前序任务：[AAF-117 #11712](../AAF-117/tasks.md)（当时以"拒绝并提示 promote"落地）、[AAF-127 #12704–#12706](../AAF-127/tasks.md)（控制交接 takeUntil、升级后同一 SSE 续流）

## 现状与证据

以"打开2号大棚的风机"为例，现状链路共 5 次模型调用、约 75 秒，升级后重复的 3 次约 52 秒：

- 轻量态模型调 `agriDeviceList` + `agriSensorRealtime`，再按技能调 `promote_to_task`。原因是 `agriConfirmDeviceCommand`（目录 `read_only=false`、`require_confirm=true`、HIGH）在 `READ_ONLY` 下被 `allowedToolNames` 归入 `controlModeFiltered`，经 `skillToolLines` 标为 `REQUIRES_TASK`，`PromptAssembler` 提示"先 promote"
- 即使模型直接调用，`ToolAuthorizationContext.requireVisible` 在 `READ_ONLY && !rule.readOnly()` 时先抛"READ_ONLY 模式禁止写工具"，`DefaultToolGateway` 第 101–110 行 `taskId()==null && grantRequired` 分支只对 DIRECT 可见的需授权只读工具生效
- 升级后协调者节点从头运行：再查一遍列表与读数 → `agriDraftDeviceCommand` → `agriConfirmDeviceCommand` → `JpaHitlTransitionAdapter.requestAuthorization` 原子提交 Task/Execution 等待态、关闭 Dispatch（`closeDispatch` 置 `CANCELED`）、落库 `AUTHORIZATION_REQUESTED`
- 等待后 `AssistantApplicationService.finalizeNodeExecution` 的 `NEEDS_USER` 分支追加 `VALIDATION_STARTED`、`VALIDATION_FAILED`、`EXECUTION_PAUSED(AUTHORIZATION_NEEDED)`，均为 `OwnerType.ASSISTANT`，经 `shouldPersistEvent` → `eventStore.append` → `requireLease` → `JpaTaskUnitOfWorkAdapter.requireExecution` 找不到 active Dispatch 抛 `StaleExecutionException`。该 append 位于管线 `onErrorResume` 之后，异常直接让节点事件流 `onError`
- `TaskCommandService.executeClaimed` 吞掉异常后以"did not reach terminal state"调用 `materializations.commit`，被 `JpaTaskMaterializationAdapter.rejection` 以 `task current plan/status changed`（Task 为 `AWAITING_AUTHORIZATION`）拒绝
- `promote_to_task` 成功返回后 AgentScope 仍发起下一次模型调用，`takeUntil(controlHandoff)` 取消时中断 `PromptEnvelopeCaptureMiddleware` 第 66 行 `Mono.fromCallable(freeze).subscribeOn(boundedElastic)` 上的 JDBC 调用，Hikari 连接被标记损坏

已核实的约束事实：

- `Execution.beginPromotion` 要求 `DIRECT && ELIGIBLE && sideEffectEpoch==0 && !terminal`；网关授权判断位于 `recordSideEffectIntent` 之前，DIRECT 里写工具永远到不了 `recordSideEffectIntent`，因此触发点上 epoch 必为 0
- 对话入口固定的 `ActionAuthorizationPolicy.DENY_AUTHORIZED_ACTIONS` 其 `actions` 为空，`behavior()` 对未列出动作返回 `DEFAULT`，对对话与升级后的任务都不产生效果（`ExecutionIntent.ActionAuthorizationPolicy` 注释已写明）
- HITL 恢复依赖 AgentState 中"有 tool_use、无 tool_result"的挂起调用：`resumeInput` 用 `MessageUtils.extractPendingToolCalls` 识别后传空输入，core 经 Toolkit → `PortBackedAgentTool` → 网关重新执行
- 农业草稿以 `operatorContext.currentOwnerId()`（用户）归属，有效期 10 分钟；网关审批申请的 grant `expiresAt` 为 15 分钟

## 目标与边界

- 轻量态模型调用"需授权且非只读或需确认"的工具时，系统自动升级为 Task 并携带该调用；协调者节点首轮不调模型，直接经 `ToolGateway` 走标准 HITL；批准后执行，再允许一次模型调用生成回复
- 模型在轻量态下仍无法真正执行写工具，只能触发升级
- 同一故事内修复 HITL 等待后的 `StaleExecutionException` 与 `task_node_commit` 误拒，保证审批卡片到达前端
- 不做：轻量态原生 HITL；不改 `executeDirect` 的 `READ_ONLY START` 约束；不新增表或 SQL；不保留"拒绝并提示 promote"旧路径
- 纯只读、无需授权的工具行为不变

## 方案概述

```text
轻量态模型 → agriConfirmDeviceCommand(draftId)
  → DefaultToolGateway：taskId==null && grantRequired && 当前画像含 promote_to_task
    → DirectPromotionPort.promoteForPendingCall（锁 origin Execution，beginPromotion，epoch==0）
      → Task.checkpoint["promotionPendingCall"] = PendingToolCall
    → 抛 PromotionHandoffException(taskId)
  → PortBackedAgentTool：记证据 controlHandoff=true，转 ToolSuspendException（循环挂起，无下一次模型调用）
  → 事件映射为 TOOL_CALL_COMPLETED(resultState=挂起, controlHandoff=true) → Harness takeUntil 收口
  → finalizeDirect 读到 PROMOTED → EXECUTION_PROMOTED(trigger=TOOL_GATE)
  → AssistantExecutionService.followPromotion 在同一 SSE 跟随任务事件
协调者节点 attempt 1：
  → AgentExecutionCommand.seededToolCall = PendingToolCall
  → Harness 把冻结消息 + 一条合成 assistant tool_use 写入调用作用域状态，以空输入启动（与 same-attempt 恢复同一路径）
  → core 执行挂起调用 → 网关（DELEGATED）→ requestAuthorization → AUTHORIZATION_REQUESTED → 状态挂起保存
  → 节点不再追加事件，executeClaimed 跳过 commit
用户批准 → 现有 resume → 空输入重执行挂起调用 → 命中 grant → recordSideEffectIntent → receipt → 执行
  → 模型基于工具结果生成回复（1 次）→ 节点完成
```

## 接口定义

```java
// agent.model（新增）：系统携带的待执行工具调用，仅含结构化调用，不含推理过程
public record PendingToolCall(
        String toolCallId,          // 沿用轻量态模型生成的原 id，便于审计关联
        String toolName,
        String toolId,
        Map<String, Object> arguments,
        String resource,            // 网关已解析的 resource
        String argumentsDigest,     // sha256(toolId + '|' + canonical(arguments))
        ExecutionId originExecutionId,
        Instant capturedAt) {

    public static final String ANNOTATION_KEY = "promotionPendingCall";

    public Map<String, Object> toAnnotation();

    /** 读取 checkpoint 注解；结构缺失或类型不符时抛出，不静默忽略。 */
    public static Optional<PendingToolCall> fromAnnotation(Map<String, Object> annotations);

    /** 重放前以同一算法重算摘要，防止 JSONB 往返或人为篡改改变参数。 */
    public void requireDigest(String recomputed);
}

// agent.port.ToolGatewayPort（新增嵌套异常，与 ApprovalRequiredException 并列）
final class PromotionHandoffException extends IllegalStateException {
    public PromotionHandoffException(String promotedTaskId, String message);
    public String promotedTaskId();
}

// assistant.port（新增）：网关触发升级的依赖倒置端口，唯一实现为 TaskCommandService
public interface DirectPromotionPort {
    PromotionHandoff promoteForPendingCall(InvocationContext context, PendingToolCall call);

    record PromotionHandoff(TaskId taskId, Task.Status taskStatus) {}
}

// TaskCommandService（签名变更，PromoteDirectTaskTool 传 null）
public Task promoteDirect(
        TenantId tenantId,
        ExecutionId originExecutionId,
        String reason,
        String roleKey,
        String shape,
        AssistantDefinition definition,
        PendingToolCall pendingCall);

// ToolAuthorizationContext.ToolAuthorizationRule（新增字段）
public record ToolAuthorizationRule(
        boolean readOnly,
        boolean reversible,
        boolean authorizationRequired,
        MissingGrantBehavior missingGrantBehavior,
        boolean promotionOnly) {   // 仅轻量态出现：可见但只能触发升级；为 true 时 authorizationRequired 必须为 true
}

// AgentExecutionCommand（新增可空字段，现有构造器默认 null）
PendingToolCall seededToolCall
```

载荷字段：

| 位置 | 字段 | 说明 |
|---|---|---|
| `Task.checkpoint.annotations` | `promotionPendingCall` | `PendingToolCall.toAnnotation()`，JSONB 嵌套对象（已有 `pauseAcks` 嵌套先例） |
| `Task.checkpoint.annotations` | `promotionReason` | 已有；自动升级写"工具 {toolName} 需要用户授权，系统自动转为任务" |
| `EXECUTION_PROMOTED` payload | `trigger` | `MODEL`（模型调 `promote_to_task`）或 `TOOL_GATE`（网关自动升级） |
| `EXECUTION_PROMOTED` payload | `pendingToolName` | 仅 `TOOL_GATE` 时有值 |
| 工具证据 / `TOOL_CALL_COMPLETED` payload | `controlHandoff`、`promotedTaskId` | 控制交接标记，Harness 据此收口 |

`trigger`、`pendingToolName` 同步加入 `ExecutionEventPublicMapper.safeData` 与 `AafAiTaskEvent.ReferenceSafeData.ALLOWED_KEYS`（不进 `DISPLAY_TEXT_KEYS`）。参数原文只存 Task checkpoint，不进入任何公开事件。

## 类结构

| 类 | 包 | 职责 / 变更 |
|---|---|---|
| `PendingToolCall`（新增） | `framework.intelligent.agent.model` | 待执行调用值对象与注解序列化、摘要校验 |
| `DirectPromotionPort`（新增） | `framework.intelligent.assistant.port` | 网关到升级服务的端口 |
| `ToolGatewayPort` | `agent.port` | 新增 `PromotionHandoffException` |
| `DefaultToolGateway` | `agent.application` | 替换第 101–110 行拒绝分支为自动升级；新增 `DirectPromotionPort` 构造参数 |
| `ToolAuthorizationContext` | `agent.model` | `ToolAuthorizationRule.promotionOnly`；`requireVisible` 对 `READ_ONLY` 放行 `promotionOnly` 写工具 |
| `TaskCommandService` | `assistant.application` | 实现 `DirectPromotionPort`；`promoteDirect` 写 `promotionPendingCall`；`executeClaimed` 等待态跳过 commit |
| `PromoteDirectTaskTool` | `assistant.application` | 适配新签名；结果 metadata 加 `controlHandoff=true`（#12807） |
| `ActivateRoleTool` | `assistant.application` | 结果 metadata 加 `controlHandoff=true`（#12807） |
| `AssistantApplicationService` | `assistant.application` | 轻量态画像暴露 `promotionOnly` 工具；协调者首轮注入 `seededToolCall`；节点等待态不再追加事件 |
| `PromptAssembler` | `assistant.application` | `REQUIRES_TASK` 文案改为"直接调用，系统自动转任务并请求审批" |
| `InvocationPolicy` | `assistant.model` | `COORDINATOR` 指令去掉"授权动作前必须 promote" |
| `AgentExecutionCommand` | `agent.model` | `seededToolCall` |
| `PortBackedAgentTool` | `infrastructure.agentscope.tool` | 映射 `PromotionHandoffException` 与 `controlHandoff` metadata 为证据 + `ToolSuspendException` |
| `AgentScopeEventMapper` | `infrastructure.agentscope.mapping` | 挂起且证据含 `controlHandoff` → `TOOL_CALL_COMPLETED` |
| `HarnessAgentExecutionAdapter` | `infrastructure.agentscope.execution` | `controlHandoff` 谓词改认证据标记；`seededToolCall` 合成挂起调用 |
| `JpaTaskMaterializationAdapter` | `infrastructure.assistant.persistence` | `promotionEvent` 增 `trigger`、`pendingToolName` |
| `ExecutionEventPublicMapper`、`AafAiTaskEvent.ReferenceSafeData` | `shared.event.publication` 等 | 放行新字段 |
| 自动配置（构造 `DefaultToolGateway` 处） | `infrastructure.*.spring` | 以 `ObjectProvider` 延迟注入 `DirectPromotionPort`，避免 `TaskCommandService → Assistant → Agent → Gateway` 循环 |

## 模块交互

### 触发条件与位置

网关在 `requireVisible`、`resource` 解析与 `grantRequired` 计算之后、任何 grant 查询与 `recordSideEffectIntent` 之前判定：

```text
context.taskId() == null && grantRequired
  ├─ 当前画像 effectiveTools 不含 promote_to_task → 失败："当前执行不能升级为任务，无法执行需授权工具"
  └─ 否则 → 构造 PendingToolCall → Mono.fromCallable(promotions::promoteForPendingCall).subscribeOn(boundedElastic)
           → flatMap 抛 PromotionHandoffException(taskId)
```

- `grantRequired = !readOnly || connectorAction || rule.authorizationRequired || requireConfirm`，与现有谓词一致，纯只读工具不进入此分支
- `promoteForPendingCall` 在 `TaskCommandService` 内复用 `promoteDirect`：读取定义（同 `PromoteDirectTaskTool`）、校验 `context.executionId()` 为 origin、`roleKey`/`shape` 为空；`materializations.promoteDirect` 锁 origin Execution 调 `beginPromotion`，epoch≠0 或已 PROMOTING/PROMOTED 时抛出，不创建 Task
- 同一轮并行发出两个需授权调用时只有第一个携带；第二个因 `beginPromotion` 失败得到错误结果，随循环收口丢弃。技能本就要求一次只控制一台设备
- 升级前先跑 `parameterPolicy.validate(definition, arguments, context, null)`，让参数错误在轻量态就回给模型修正；若该策略依赖 grant（连接器条件），对 `grantRequired` 工具跳过，开发时核实

### 轻量态工具可见性与约束调整

- 不改 `executeDirect` 的 `READ_ONLY START`、不改 DIRECT `controlMode`、不改对话入口的 `DENY_AUTHORIZED_ACTIONS`（其 `actions` 为空，无效果）
- 轻量态且画像含 `promote_to_task` 时，把现有 `taskRequiredToolNames`（Skill 必需、仅因 `controlMode` 被过滤的工具）合入 `effectiveTools`，规则为 `promotionOnly=true`、`authorizationRequired=true`、`readOnly`/`reversible` 取 ToolPolicy 规则（须与目录一致），`missingGrantBehavior=REQUEST_ON_DEMAND`；这些工具跳过 `toolPolicy().requireAllowed(controlMode)`。合入在 `effectiveToolResolver` 之后，不改变解析器的 fail-closed
- 未被 Skill 声明必需的角色写工具保持不可见，暴露面与现在 `REQUIRES_TASK` 提示的集合相同
- `requireVisible`：`READ_ONLY && !rule.readOnly() && !rule.promotionOnly()` 才抛；另加 `promotionOnly && 非 READ_ONLY` 抛出，保证该标记不出现在任务态
- 写工具在轻量态的执行屏障有三层：网关分支在执行前返回；origin Execution 转为 PROMOTED 后 `requireCurrent` 拒绝同一循环的后续调用；ToolSuspend 让循环不再调用模型
- `skillToolLines` 先判 `taskRequired`，这些工具仍标 `REQUIRES_TASK`；`PromptAssembler` 文案改为"需要用户审批：用户要执行时直接调用，系统会自动转为任务并弹出审批卡，调用后本轮结束；不要先调用 promote_to_task"

### 待执行调用存放

存 `Task.checkpoint.annotations["promotionPendingCall"]`（`ai_task.checkpoint` JSONB），不改表：

- checkpoint 是 Task 级事实标注，`requestAuthorization` 用 `withAnnotation("approvalId")` 合并写入，不会覆盖
- 未选 `command_payload`：升级命令由 `sourceCommand.promoteToTask` 派生，节点命令再经 `forNode` 替换 `input`，加字段要改 `AssistantCommand` 多处构造与复制方法
- 未选 TaskPlan 节点输入：要给 `TaskNode` 增结构化字段并进入 graphHash，改动面大，且计划修订会复制它
- checkpoint 当前不在公开投影中出现，参数原文不外泄；开发时复核 `TaskDetails` 投影

### 协调者首轮重放

| 方案 | AgentState 与恢复 | 审计 | 幂等 receipt | 结论 |
|---|---|---|---|---|
| A：Harness 合成挂起 tool_use，以空输入启动 | 与 same-attempt 恢复同一路径；挂起时 `preserveHitlState` 保存的状态含该调用，批准后 `resumeInput` 原样识别 | 经 core → `PortBackedAgentTool` → 网关，产生标准 `TOOL_RESULT_*`、`AUTHORIZATION_REQUESTED`；模型上下文里有一致的 tool_use/tool_result 对 | 节点 context 下 `requireActionKey` = 节点 `idempotencyKey` + 参数摘要；恢复与重复 resume 同键，`REPLAY` 防重放；轻量态从未 claim receipt，无冲突 | 采用 |
| B：强制首个工具调用（tool_choice 或指令注入） | 正常 | 正常 | 模型可能改写参数导致摘要不同 | 否决：多 1 次模型调用（10–15 秒），且参数不确定 |
| C：Harness 在循环外直接调网关 | AgentState 无挂起调用，批准后要另写恢复分支；模型看不到工具结果，收尾时仍需合成 | 绕过 core 事件，需手工补事件 | 可用 | 否决：并行抽象，违背"优先已有模式" |

方案 A 的落点：

- `AssistantApplicationService.agentCommand` 仅在以下条件同时满足时设置 `seededToolCall`：`nodeIdentity.kind()==COORDINATOR`、`!resumeStateRequired`、节点 Execution `attemptNo==1`、Task checkpoint 有 `promotionPendingCall`、`toolName` 在本次 `effectiveTools` 内（否则 fail-closed 抛出，节点按现有失败策略重试，attempt 2 走普通模型路径）
- `HarnessAgentExecutionAdapter.executeResolved` 在 `requireNoHiddenPersistentHistory` 确认无持久状态之后：校验 `requireDigest`；把冻结消息与一条 `name=agent.getName()`、内容为 `ToolUseBlock(toolCallId, toolName, arguments)` 的 assistant 消息写入调用作用域状态；`agentInput=List.of()`。写入方式以 AgentScope 当前版本为准（state 或 memory），判定标准是 `MessageUtils.extractPendingToolCalls` 能识别。开发时对照 `tmp/agent/agentscope` 源码核实
- 首轮不调用模型，因此 `PromptEnvelopeCaptureMiddleware` 首轮不冻结信封，批准后的收尾调用照常冻结
- 农业技能补充：协调者收到工具结果后直接据结果中的 `device` 状态回复，不再额外调读数工具

### 草稿前置工具衔接

- `agriDraftDeviceCommand` 目录为只读、无需确认，轻量态已可调用，行为不变；技能改为轻量态内"起草 → 直接调用确认"
- 草稿按用户 owner 归属，轻量态与任务节点是同一用户；开发时核实 `OperatorContext.currentOwnerId()` 在派发线程与请求线程解析出相同值（现状任务内起草再确认已能走到 HITL，可作旁证）
- 草稿 10 分钟过期而审批可更久：过期后批准，确认工具返回"草稿不存在或已过期"，作为工具失败结果交给模型，按技能重新起草并重新审批。不延长草稿有效期（演示数据，非本故事范围），列为已知限制

### 控制交接统一收口

- `PromotionHandoffException` 与 `ApprovalRequiredException` 同模式：`PortBackedAgentTool` 记证据 `{controlHandoff:true, promotedTaskId}`（`authorizationRequired=false`）后抛 `ToolSuspendException`，循环挂起，不再有下一次模型调用
- `AgentScopeEventMapper.mapToolResult`：非成功且证据含 `controlHandoff` → `TOOL_CALL_COMPLETED`，`resultState` 保留挂起态原值
- `HarnessAgentExecutionAdapter.controlHandoff` 改为 `TOOL_CALL_COMPLETED && taskId==null && payload.controlHandoff==true`，删除 `CONTROL_HANDOFF_TOOLS` 名称集合
- #12807 让 `promote_to_task`、`activate_role` 的成功结果也带 `controlHandoff=true`，走同一挂起路径。这样第 3 次模型调用与取消中断 JDBC 的路径在三种交接上都消失；只改 metadata，不改两工具语义
- `ExecutionTraceRecorder` 对 `controlHandoff` 结果记为"已交接"而不是"成功"

### HITL 等待后的收口

当前节点内授权与澄清等待均由 HITL 事务原子提交（Task、Execution、Plan 等待态 + Dispatch 关闭 + canonical 事件与 outbox），Harness 直接回送该事件并 `takeUntil` 结束。之后的处理：

- `finalizeNodeExecution`：`agentEvents` 中存在 canonical 等待事件（`AUTHORIZATION_REQUESTED/AWAITING_AUTHORIZATION` 或 `CLARIFICATION_REQUESTED/AWAITING_CLARIFICATION`）时，只记 trace 与 `stage=task_node_validation outcome=AWAITING_HUMAN` 日志，返回空流，不再生成 `VALIDATION_*`、`EXECUTION_PAUSED`。`switch` 的 `NEEDS_USER` 分支改为抛 `IllegalStateException("NEEDS_USER 必须伴随已提交的等待事件")`，旧分支删除
- `TaskCommandService.executeClaimed`：观察到本节点 execution 的 canonical 等待事件即置 `awaitingHuman`；`concatWith` 中 `awaitingHuman && failure==null` 时跳过 `materializations.commit`，记 `stage=task_node_commit outcome=AWAITING_HUMAN`
- `JpaTaskMaterializationAdapter.rejection` 不改：commit 是结果提交，Dispatch 已关闭时拒绝是正确的 fencing
- TASK_ROOT 的 `NEEDS_USER` 分支不动：`requirePlannedHitlBoundary` 只允许 TASK_NODE 进入 HITL

被否决的收口方式：并入 HITL 事务（要把 Assistant 层校验事件塞进基础设施事务，跨层且无新增信息）；只发实时不落库（`RunDeliveryTracker` 与 AG-UI 已在 `AUTHORIZATION_REQUESTED` 收口，后续事件没有消费方）；让 commit 接受等待态（削弱"关闭后的 Dispatch 不得提交"的 fencing）。

### AG-UI 终态

- canonical 事件由 HITL 事务落库，`followTaskEvents` 的回补或实时任一路径都能拿到；`AafAguiConverterRegistry` 对节点等待事件直接交给 `RunLifecycleEventConverter`，产生带 `outcome.interrupt` 的 `RunFinished`
- 事件流异常消除后，节点管线正常完成，不再产生异常日志
- 现场"本轮已结束，没有生成文字回复"说明前端收到的是不带 interrupt 的 `RunFinished`，与卡片缺失相符。代码静态阅读未能确认唯一原因，#12806 按以下顺序核对并修正：`AafAguiStreamContext.runFinished` 在 interrupt 前是否已被置位；`followTaskEvents` 的结束事件类型与原因；控制器在流结束时补发 `runFinishedOnce` 的条件。`followTaskEvents` 结束日志补充触发收口的事件类型

### 拒绝审批路径

用户拒绝 → 现有 resume → 空输入重执行挂起调用 → 网关 `priorRejection` 返回 `AuthorizationDeniedException` → 工具失败结果交模型 → 模型说明未执行并询问是否调整（1 次模型调用）→ 节点完成。同节点累计拒绝 2 次后网关停止重复申请，规则不变。

### ConnectorAction 工具

- 连接器动作 `grantRequired` 恒为真，轻量态同样自动升级并携带调用
- 凭证授权流程不变：任务态下 `grants.findActive` 与审批产生的 grant 携带 `credentialHandle`；缺凭证仍抛 `ConnectorCredentialUnavailableException`；写动作仍要求目录幂等声明，`ConnectorAction` 的幂等键沿用节点 receipt key
- 携带的 `resource` 与 `grantConditions` 在任务态由网关按同一算法重算，携带值只用于审计比对，不作为授权依据

## 设计决策（ADR）

| 决策 | 选择 | 原因 | 否决方案 |
|---|---|---|---|
| 轻量态是否支持 HITL | 不支持，遇授权自动升级 | 人类已决策；授权与恢复依赖持久中断点 | DIRECT 原生 HITL |
| 升级触发者 | 网关经 `DirectPromotionPort` 触发 | 授权判断已在网关，与 `requestAuthorization` 同模式；触发点天然在副作用前 | 模型自行 promote（多 3 次调用，依赖遵循度）；Harness 事件层检测（工具错误不带原因，需新证据通道，#11712 已论证） |
| 授权写工具对轻量态可见 | 可见，规则 `promotionOnly` | 模型可一步调用；执行屏障在网关 | 保持不可见（必须先 promote，仍重复查询）；改 DIRECT 为 COLLABORATIVE（扩大可执行面） |
| 待执行调用存放 | Task checkpoint 注解 | 已有 JSONB、Task 级、HITL 合并写入安全 | command_payload；TaskPlan 节点输入；新表 |
| 首轮重放方式 | 合成挂起 tool_use + 空输入（方案 A） | 复用恢复路径、审计完整、receipt 稳定 | 强制首个工具调用；循环外直调网关 |
| 重放适用范围 | 协调者节点 attempt 1 | 重试时已可能有副作用证据，让模型基于上下文重新判断更稳妥；receipt 仍兜底 | 每个 attempt 都重放 |
| 交接后循环终止 | ToolSuspend + 证据标记 | 不再发起多余模型调用，消除取消中断 JDBC | 仅靠 `takeUntil` 取消 |
| 等待后收口 | 不追加事件、跳过 commit | 等待已原子提交，后续写入必然失权 | 并入 HITL 事务；只发实时；commit 接受等待态 |
| 旧路径 | 直接替换 | 禁兼容层 | 保留"拒绝并提示 promote"作为开关 |

## 影响文件清单

| 范围 | 文件 | 任务 |
|---|---|---|
| framework | `agent/model/PendingToolCall.java`（新增） | #12801 |
| framework | `assistant/port/DirectPromotionPort.java`（新增） | #12801 |
| framework | `assistant/application/TaskCommandService.java` | #12801、#12805 |
| framework | `assistant/application/PromoteDirectTaskTool.java` | #12801、#12807 |
| framework | `infrastructure/assistant/persistence/JpaTaskMaterializationAdapter.java` | #12801 |
| framework | `shared/event/publication/ExecutionEventPublicMapper.java`、`AafAiTaskEvent` 所在文件 | #12801 |
| framework | `agent/model/ToolAuthorizationContext.java` | #12802 |
| framework | `assistant/application/AssistantApplicationService.java` | #12802、#12804、#12805 |
| framework | `assistant/application/PromptAssembler.java` | #12802 |
| framework | `assistant/model/InvocationPolicy.java` | #12802 |
| framework | `resources/aaf/skills/builtin-task-triage.md` | #12802 |
| framework | `agent/port/ToolGatewayPort.java` | #12803 |
| framework | `agent/application/DefaultToolGateway.java` | #12803 |
| framework | `infrastructure/agentscope/tool/PortBackedAgentTool.java` | #12803 |
| framework | `infrastructure/agentscope/mapping/AgentScopeEventMapper.java` | #12803 |
| framework | `infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java` | #12803、#12804 |
| framework | 构造 `DefaultToolGateway` 的自动配置类 | #12803 |
| framework | `assistant/application/ExecutionTraceRecorder.java` | #12803 |
| framework | `agent/model/AgentExecutionCommand.java` | #12804 |
| framework | `assistant/application/ActivateRoleTool.java` | #12807 |
| framework | `infrastructure/agentscope/middleware/PromptEnvelopeCaptureMiddleware.java` | #12808（可延期） |
| framework | `resources/aaf/skills/system.skill.agri-iot-control.md` | #12809 |
| api | `module/ai/assistant/service/AssistantExecutionService.java`、`module/ai/agui/*`（按 #12806 核对结果） | #12806 |
| 测试 | 因签名变化无法编译的既有测试（`promoteDirect`、`ToolAuthorizationRule`、`DefaultToolGateway` 构造） | 随对应任务 |
| 文档 | ADR-008、ADR-011、AAF-115 design、AAF-117 tasks、backlog | #12810 |

预估生产代码 22–24 个文件（新增 2 个），文档 5 个，另有若干因签名变化需同步的测试文件。不改表、不加 SQL。

## 对既有设计文档的影响

| 文档 | 影响 | 需同步内容 |
|---|---|---|
| ADR-008"DIRECT 与 TASK 的关系" | 第 151 行"建议增加 harness 约束"由建议转为已采纳 | 写明网关自动升级、携带待执行调用、协调者首轮重放；"升级是新开执行"仍成立，仅待执行调用被延续；"轻量态无授权类动作"改为"轻量态不执行授权类动作，可见的授权工具只触发升级" |
| ADR-011 | 升级续流与控制交接 | `EXECUTION_PROMOTED` 新增 `trigger`、`pendingToolName`；控制交接改为证据标记 + ToolSuspend |
| AAF-115 design"promotion 与副作用互斥" | 合同不变，新增携带物 | promotion 可携带结构化 `PendingToolCall`（存 checkpoint，非 CoT、非 AgentState）；仍要求 ELIGIBLE 且 epoch=0 |
| AAF-115 design"短事务、锁顺序与 fencing" | 补充 | HITL 等待原子提交后，节点不再追加事件、不提交结果 |
| AAF-117 #11712 | 结论被替代 | 状态改为"由 AAF-128 实现"，保留原验证结论作为历史 |
| backlog | 条目状态 | 设计审核后更新 |

## 风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 授权写工具对轻量态模型可见 | 屏障失效即越权 | 网关分支在 grant 查询与副作用之前；`requireVisible` 禁止 `promotionOnly` 出现在任务态；单测覆盖"轻量态调用写工具绝不进入 `executeAndAccount`" |
| AgentScope 合成挂起调用的 API 与预期不符 | 方案 A 落地受阻 | #12804 先做可行性核实；不可行时回到设计评审，不私自改用 B/C |
| `ToolSuspendException` 的结果状态与映射 | 交接事件丢失，循环不收口 | 映射与 `controlHandoff` 谓词同任务改，日志打出 `resultState` |
| checkpoint 中参数原文 | 敏感参数持久化 | 只存 Task 内部列，不进事件与公开投影；任务结束后随 Task 保留，与 HumanApproval 存参策略一致 |
| 自动升级让入口无法展示审批卡（如设备语音入口 `DeviceVoiceGatewayService`） | 任务停在等待，用户无感知 | 现状模型也会 promote，风险不新增；见未决问题 |
| 草稿过期早于审批 | 批准后执行失败 | 失败结果交模型重新起草，列为已知限制 |
| 循环依赖 | 启动失败 | `ObjectProvider` 延迟获取端口 |

## 验收标准

```gherkin
Feature: 轻量态遇授权工具自动升级并携带待执行调用

  Scenario: 自动升级且不重复查询
    Given 用户在对话轻量态说"打开2号大棚的风机"
    And 模型已调用 agriDraftDeviceCommand 得到 draftId
    When 模型调用 agriConfirmDeviceCommand(draftId)
    Then 系统创建 Task，origin Execution 状态为 PROMOTED
    And Task checkpoint 含 promotionPendingCall，toolName 为 agriConfirmDeviceCommand，参数摘要与原调用一致
    And 同一 SSE 收到 aaf.task.promoted，其 trigger 为 TOOL_GATE
    And 协调者节点在 AUTHORIZATION_REQUESTED 之前不调用模型，也不调用 agriDeviceList、agriSensorRealtime、agriDraftDeviceCommand

  Scenario: 模型调用次数
    Given 上一场景已进入审批等待
    When 用户批准
    Then 从发出 agriConfirmDeviceCommand 的那次模型调用起算到最终回复，模型调用不超过 2 次
    And 轻量态循环在交接后不再发起模型调用

  Scenario: 轻量态不能执行写工具
    Given 轻量态画像中 agriConfirmDeviceCommand 的规则为 promotionOnly
    When 模型调用该工具
    Then 网关不查询 grant、不记录 side-effect intent、不 claim receipt、不调用工具实现

  Scenario: 副作用已发生时不升级
    Given 轻量态 Execution 的 sideEffectEpoch 大于 0 或 promotionState 不是 ELIGIBLE
    When 网关判定某调用需授权
    Then 不创建 Task，工具调用以"当前执行已不可升级"失败
    And 不写 promotionPendingCall

  Scenario: 拒绝审批
    Given 协调者节点重放的调用已进入审批等待
    When 用户拒绝并填写反馈
    Then 工具不执行，设备状态不变
    And 模型收到包含反馈的拒绝结果并回复用户，模型调用 1 次后节点完成

  Scenario: 审批卡片到达前端且无 StaleExecutionException
    Given 协调者节点触发 AUTHORIZATION_REQUESTED
    Then 前端收到 outcome 为 interrupt 的 RUN_FINISHED，interruptId 等于 approvalId
    And 日志中没有 StaleExecutionException
    And 节点事件流正常完成，不追加 VALIDATION_* 与 EXECUTION_PAUSED
    And task_node_commit 记录 outcome=AWAITING_HUMAN，未调用 commit

  Scenario: 纯只读工具不受影响
    When 轻量态模型调用 agriSensorRealtime
    Then 工具直接执行，不触发升级

  Scenario: 重复恢复不重复执行
    Given 用户已批准且工具已执行
    When 同一审批的 resume 被重复提交
    Then 网关 receipt 返回 REPLAY，设备指令只执行一次
```

## 人类决策（2026-11-12）

- 模型调用次数口径：整轮 4 次（查设备、起草、确认、收尾）可接受；验收按"从发出授权调用起 ≤2、任务节点审批前 0 次"
- 设备语音等无法展示审批卡的入口：本期不处理
- #12807 纳入本故事
- #12808 不做（❌ 已取消）
- 前端按 `trigger=TOOL_GATE` 显示提示文案：新增 #12811（developer-webui）

## 未决问题（已于 2026-11-12 全部拍板，见上节）

- "模型调用次数≤2"的口径：确认工具依赖草稿号，轻量态至少要先起草一次，所以整轮最少为"查设备 1 + 起草 1 + 确认 1 + 收尾 1"。本设计按"从发出授权调用起 ≤2、任务节点审批前 0 次"验收。若要整轮更少，可选：技能允许用户已指明大棚与设备时跳过 `agriDeviceList` 直接起草（起草工具会校验设备，猜错返回错误），或让确认工具接受草稿参数直接生成草稿（改农业工具契约）
- 设备语音等无法展示审批卡的入口是否禁用自动升级（例如按入口在 `ExecutionIntent` 标注"不支持人工交互"，网关据此直接失败）
- #12807（显式 `promote_to_task`、`activate_role` 也改为挂起交接）是否纳入本故事；不纳入则这两条路径仍有"多一次模型调用 + 取消中断 JDBC"
- #12808（`PromptEnvelopeCaptureMiddleware` 取消时不中断 JDBC）是否本期做；自动升级与 #12807 落地后，该问题只剩用户主动取消等场景
- 前端 `aaf.task.promoted` 是否按 `trigger=TOOL_GATE` 显示"需要你审批，已转为任务"之类文案（本设计不改前端）
