---
level: Practice
layer: Model
purpose: AAF-128 轻量态遇授权工具自动升级并携带待执行调用的技术任务拆分
status: draft
version: 1.0.0
date: 2026-11-12
author: AaronZZH & Kiro
tags:
  - AAF-128
  - 任务升级
  - HITL
  - 技术任务
related:
  - design.md
  - ../../../design/adr/ADR-008-task-execution-shape-and-streaming.md
  - ../../../design/adr/ADR-011-assistant-single-entry-and-on-demand-role.md
---

# 技术任务：轻量态遇授权工具自动升级并携带待执行调用（AAF-128）

> 需求：[backlog AAF-128](../../backlog.md)
> 设计：[design.md](design.md)
> 负责人：architect 拆分 | 创建：11-12

## 任务约束

- 🔴 高风险：设计经人类审核前不开发
- 当前开发阶段：不跑 check/test/lint，最低准出为"编译通过"，单测与验收由协调者后续补派；不新增非必要测试文件；因签名变化无法编译的既有测试同步修改
- 不新增 SQL、不改表；待执行调用存 `ai_task.checkpoint` 注解
- 禁兼容层：直接删除"DIRECT 拒绝并提示 promote_to_task"路径与 `CONTROL_HANDOFF_TOOLS` 名称集合，不保留开关
- 日志沿用中文标签加全角逗号 `k=v` 风格；关键节点 `log.info`，逐事件 `log.debug`；ID 用 `.value()`
- 内置技能 md 只在 `ai_skill_definition` 表为空时导入，已有库需清空技能表后生效（记入 dev-log）

## 后端路径缩写

- `fw` = `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent`
- `api` = `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai`
- `skills` = `apps/service/aaf-framework/src/main/resources/aaf/skills`

## 任务列表

### 升级携带调用

1. ✅ #12801 `PendingToolCall` 与升级携带（developer-service 11-12）
   - 新增 `fw/agent/model/PendingToolCall.java`：字段、`ANNOTATION_KEY="promotionPendingCall"`、`toAnnotation`、`fromAnnotation`（结构不符抛出）、`requireDigest`
   - 新增 `fw/assistant/port/DirectPromotionPort.java`（`promoteForPendingCall`、`PromotionHandoff`），由 `TaskCommandService` 实现
   - `TaskCommandService.promoteDirect` 增参 `PendingToolCall pendingCall`；非空时写 checkpoint 注解，`promotionReason` 为"工具 {toolName} 需要用户授权，系统自动转为任务"；`PromoteDirectTaskTool` 传 `null`
   - `promoteForPendingCall`：读助理定义（同 `PromoteDirectTaskTool`），以 `context.executionId()` 为 origin、`roleKey`/`shape` 为空调用 `promoteDirect`
   - `JpaTaskMaterializationAdapter.promotionEvent` 增 `trigger`（`MODEL`/`TOOL_GATE`）、`pendingToolName`；`ExecutionEventPublicMapper.safeData` 与 `AafAiTaskEvent.ReferenceSafeData.ALLOWED_KEYS` 放行
   - 日志：`[任务升级] 网关自动升级：originExecutionId={}，taskId={}，tool={}，toolCallId={}`
   - verify: 编译通过；需协调者后续补测（checkpoint 注解往返、摘要校验、epoch≠0 时不创建 Task）

### 轻量态可见性

2. ✅ #12802 授权工具对轻量态可见但只能触发升级（developer-service 11-12）
   - `ToolAuthorizationRule` 增 `promotionOnly`（为 true 时 `authorizationRequired` 必须为 true），保留现有 4 参构造器默认 `false`
   - `requireVisible`：`READ_ONLY && !readOnly && !promotionOnly` 才抛；`promotionOnly && mode != READ_ONLY` 抛出
   - `AssistantApplicationService` 画像：轻量态且有效工具含 `promote_to_task` 时，把 `taskRequiredToolNames` 合入 `effectiveTools`（在解析器之后），规则 `promotionOnly=true`，跳过 `toolPolicy().requireAllowed(controlMode)`
   - `skillToolLines` 先判 `taskRequired`，这些工具标 `REQUIRES_TASK`；`PromptAssembler` 的 `REQUIRES_TASK` 文案改为"需要用户审批：用户要执行时直接调用，系统会自动转为任务并弹出审批卡，调用后本轮结束；不要先调用 promote_to_task"
   - `InvocationPolicy.COORDINATOR` 与 `skills/builtin-task-triage.md`"对话态升级"：删去"授权动作须先 promote"，改为"需要授权的工具直接调用，系统自动升级"；多步骤、多角色、结构化人工交互仍走 `promote_to_task`
   - 不改 `executeDirect` 的 `READ_ONLY START`、不改对话入口 `DENY_AUTHORIZED_ACTIONS`
   - verify: 编译通过；需协调者后续补测（轻量态画像含 `agriConfirmDeviceCommand` 且规则 `promotionOnly`；任务态画像不出现 `promotionOnly`）

### 网关自动升级与交接

3. ✅ #12803 网关自动升级与控制交接收口（developer-service 11-12）
   - `ToolGatewayPort` 新增 `PromotionHandoffException(promotedTaskId, message)`
   - `DefaultToolGateway` 构造新增 `DirectPromotionPort`；替换 `taskId()==null && grantRequired` 分支：画像无 `promote_to_task` 时报"当前执行不能升级为任务，无法执行需授权工具"；否则构造 `PendingToolCall`（摘要复用 `canonical` + `sha256`）→ `Mono.fromCallable(promoteForPendingCall).subscribeOn(boundedElastic)` → 抛 `PromotionHandoffException`。分支位于 grant 查询与 `recordSideEffectIntent` 之前；升级前对参数做 `parameterPolicy.validate(…, null)`，若该策略依赖 grant 则对此类工具跳过（开发时核实）
   - `PortBackedAgentTool`：`PromotionHandoffException` → 证据 `{controlHandoff:true, promotedTaskId}`（`authorizationRequired=false`）→ `ToolSuspendException`；结果 metadata 含 `controlHandoff=true` 时同样处理（供 #12807）
   - `AgentScopeEventMapper.mapToolResult`：非成功且证据含 `controlHandoff` → `TOOL_CALL_COMPLETED`，`resultState` 保留原值
   - `HarnessAgentExecutionAdapter.controlHandoff`：改为 `TOOL_CALL_COMPLETED && taskId==null && payload.controlHandoff==true`，删除 `CONTROL_HANDOFF_TOOLS`
   - `ExecutionTraceRecorder`：`controlHandoff` 结果记"已交接"
   - 自动配置以 `ObjectProvider` 延迟注入 `DirectPromotionPort`，避免循环依赖
   - 日志：`[工具网关] 轻量态触及授权工具，已自动升级：executionId={}，tool={}，taskId={}`
   - verify: 编译通过；需协调者后续补测（轻量态写工具不进入 `executeAndAccount`；并行两个授权调用只携带第一个）

### 协调者首轮重放

4. ✅ #12804 协调者节点合成挂起调用并经网关发起 HITL（developer-service 11-12）
   - 先做可行性核实：对照 `tmp/agent/agentscope` 源码确认如何把"冻结消息 + assistant tool_use"写入调用作用域状态，使 `MessageUtils.extractPendingToolCalls` 能识别、空输入能执行挂起调用。不可行时停止并回到设计评审，不改用其他方案
   - `AgentExecutionCommand` 增可空 `seededToolCall`，现有构造器默认 `null`；`resumeStateRequired` 与 `seededToolCall` 互斥
   - `AssistantApplicationService.agentCommand`：协调者节点、非恢复、节点 Execution `attemptNo==1`、checkpoint 有 `promotionPendingCall` 时设置；`toolName` 不在 `effectiveTools` 内则抛出（节点按现有策略失败重试，attempt 2 走模型路径）
   - `HarnessAgentExecutionAdapter.executeResolved`：`requireNoHiddenPersistentHistory` 之后校验 `requireDigest`，写入合成状态（assistant 消息 `name=agent.getName()`、`ToolUseBlock` 沿用原 `toolCallId`），`agentInput=List.of()`
   - 日志：`[任务升级] 协调者首轮重放待执行调用：executionId={}，taskId={}，tool={}，toolCallId={}`
   - verify: 编译通过；需协调者后续补测（首轮无模型调用即产生 `AUTHORIZATION_REQUESTED`；批准后重执行命中 grant；重复 resume 返回 receipt REPLAY）

### HITL 等待收口

5. ✅ #12805 等待已原子提交后节点不再写事件、不提交结果（developer-service 11-12）
   - `AssistantApplicationService.finalizeNodeExecution`：`agentEvents` 含 canonical 等待事件时只记 trace 与 `stage=task_node_validation outcome=AWAITING_HUMAN`，返回空流；`NEEDS_USER` 分支改为抛 `IllegalStateException`，旧事件生成删除；TASK_ROOT 分支不动
   - `TaskCommandService.executeClaimed`：本节点 execution 的 canonical 等待事件置 `awaitingHuman`；`awaitingHuman && failure==null` 时跳过 `materializations.commit`，记 `stage=task_node_commit outcome=AWAITING_HUMAN`
   - `JpaTaskMaterializationAdapter.rejection` 不改
   - 本任务与升级无依赖，可先行
   - verify: 编译通过；需协调者后续补测（授权等待后无 `StaleExecutionException`、无 commit 调用；澄清等待同样成立）

6. ✅ #12806 审批卡片到达前端核对（协调者 11-13）
   - 依赖 #12805；端到端复现"打开2号大棚的风机"，按序核对：`AafAguiStreamContext.runFinished` 在 interrupt 前是否已被置位；`followTaskEvents` 的结束事件类型与原因；`AssistantAguiController` 流结束时补发 `runFinishedOnce` 的条件
   - `followTaskEvents` 结束日志补充收口事件类型：`[任务续流] 结束：taskId={}，原因={}，收口事件={}`
   - 定位到的缺陷在本任务内修正，修正点写入 dev-log；若需改动超出 `api/agui` 与 `AssistantExecutionService`，先回协调者评估
   - verify: 编译通过；需协调者后续补验收（前端收到 `outcome.interrupt` 的 `RUN_FINISHED`，`interruptId` 等于 `approvalId`）

### 可选与配套

7. ✅ #12807 显式控制交接工具同样挂起循环（人类 11-12 确认纳入）（developer-service 11-12）
   - `PromoteDirectTaskTool`、`ActivateRoleTool` 成功结果 metadata 加 `controlHandoff=true`，经 #12803 的挂起路径结束循环
   - 核实 `finalizeDirect` 的 PROMOTED 判定与 `DirectRoleHandoffRegistry` 续接不受挂起影响
   - verify: 编译通过；需协调者后续补测（`promote_to_task`、`activate_role` 后无下一次模型调用）

8. ❌ #12808 模型调用取消不中断信封冻结的 JDBC（人类 11-12 决定不做）
   - `PromptEnvelopeCaptureMiddleware` 第 66 行：冻结在不可被取消中断的执行方式下完成（例如提交到独立执行器并以 `CompletableFuture` 承接，取消只丢弃结果），保证连接不被标记损坏
   - verify: 编译通过；需协调者后续补测（取消模型调用后 Hikari 无损坏连接告警）

9. ✅ #12809 农业技能文案（developer-service 11-12）
   - `skills/system.skill.agri-iot-control.md`"控制设备流程"：对话中改为"确认设备 → `agriDraftDeviceCommand` 起草 → 直接调用 `agriConfirmDeviceCommand`，系统自动转任务并弹出审批卡"；删除"调用 `promote_to_task` 升级"
   - 任务中：收到确认工具结果后直接依据结果中的 `device` 状态回复，不再额外调用读数工具；草稿过期时重新起草
   - verify: 文案与 `PromptAssembler` 的 `REQUIRES_TASK` 文案、`builtin-task-triage` 无矛盾

### 文档

10. ✅ #12810 同步设计文档（协调者执行）- 协调者 11-12
    - ADR-008"DIRECT 与 TASK 的关系"：自动升级由建议改为已采纳，补携带调用与首轮重放
    - ADR-011：`EXECUTION_PROMOTED` 新字段、控制交接改为证据标记 + ToolSuspend
    - AAF-115 design："promotion 与副作用互斥"补 `PendingToolCall`；"短事务、锁顺序与 fencing"补等待后不追加事件、不提交结果
    - AAF-117 tasks #11712：标注由 AAF-128 实现，保留原验证结论
    - backlog AAF-128 状态
    - verify: 文档间无矛盾，链接可达

### 前端

11. ✅ #12811 自动升级提示文案（developer-webui 11-12）
    - 依赖 #12801（`aaf.task.promoted` 公共事件 data 含 `trigger`、`pendingToolName`）
    - 对话流收到 `trigger=TOOL_GATE` 的升级事件时，在消息流中显示行内提示"该操作需要你审批，已转为任务"（可带工具名），复用现有控制工具行内标记（`ControlToolChip`）的视觉样式，不新建并行组件
    - `trigger=MODEL` 时保持现有展示
    - verify: 类型检查通过（本阶段不跑）；需协调者后续补测

## 依赖关系

```text
#12801 → #12803 → #12804
#12802 → #12803
#12805 → #12806
#12803 → #12807
#12809 依赖 #12802 文案
#12801 → #12811
#12810 在 #12801–#12807 完成后
```

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 | ❌ 已取消 | 🚫 阻塞中 -->

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入

## 评审状态（🔴 高风险适用）

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（技术设计） | 1 | 11-12 | ✅ CLEAR（人类 11-12 审核通过） | 🔴 是 |
| designer（UI 审查） | 0 | — | — | 仅 #12811 行内文案，复用现有样式，不单独设计 |
| developer（编码） | 0 | — | ⏳ PENDING | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |

<!-- 状态：⏳ PENDING | 🔄 IN_PROGRESS | ✅ CLEAR | ❌ BLOCKED | ↩️ RETURNED -->
