---
level: Practice
layer: Model
purpose: AAF-127 助理自动切换角色、任务升级续流与看板状态推送的技术任务
status: draft
version: 1.0.0
date: 2026-11-11
author: AaronZZH & Kiro
tags:
  - AAF-127
  - 协调者
  - 角色切换
  - 任务升级
  - 技术任务
related:
  - ../../../design/adr/ADR-011-assistant-single-entry-and-on-demand-role.md
  - ../../../design/adr/ADR-008-task-execution-shape-and-streaming.md
---

# 技术任务：助理自动切换角色、任务升级续流与看板状态（AAF-127）

> 设计依据：[ADR-011](../../../design/adr/ADR-011-assistant-single-entry-and-on-demand-role.md)，人类已于 11-11 确认按该设计先实现一版
> 负责人：协调者拆分 | 创建：11-11

## 任务约束

- 🔴 含权限变更（委派授权基线）与接口删除（L0 角色选择），已获人类确认
- 用户指定：不执行编译、不执行测试、不跑 lint/check；不新增非必要单元测试；非指定不新增 SQL
- 已有测试因类删除或签名变化无法通过编译的，同步删除或修改；不新增测试
- 禁兼容层：删除 L0 角色选择不留开关，不保留旧路径
- 日志：关键节点用 `log.info`，逐事件追踪用 `log.debug`；中文标签加全角逗号的 `k=v` 风格，与 `[角色选择]` 一致；ID 用 `.value()`
- 内置技能 md 只在 `ai_skill_definition` 表为空时由 `SkillMarkdownSourceSynchronizer` 导入，已有库要清空技能表后才生效

## 后端路径缩写

- `fw` = `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent`
- `api` = `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai`
- `skills` = `apps/service/aaf-framework/src/main/resources/aaf/skills`

## 新增事件与前后端契约

| 内部类型 | 公共名（AG-UI CUSTOM name） | 公开 data 字段 | 产生时机 |
|---|---|---|---|
| `ROLE_SWITCHED`（新增） | `aaf.role.switched` | `fromRoleKey`、`toRoleKey`、`toRoleName`、`reason` | 轻量态协调者调用 `activate_role` 后，前一执行收口时发出 |
| `EXECUTION_PROMOTED`（已有） | `aaf.task.promoted` | 现有 `promotedTaskId`、`taskStatus`、`executionStatus`，新增 `roleKey`、`shape` | 轻量态升级为任务；不再映射为 `RUN_FINISHED` |
| `ROLE_RESOLVED`（已有） | `aaf.role.resolved` | 现有字段，新增 `selectedBy` | 每个执行确定角色时 |

- CUSTOM value 仍是 `AafAiTaskEvent` 信封，业务字段在 `value.data` 下
- 新增 data 字段必须同时加入 `ExecutionEventPublicMapper.safeData` 与 `AafAiTaskEvent.ReferenceSafeData.ALLOWED_KEYS`；`toRoleName`、`reason` 加入 `DISPLAY_TEXT_KEYS`（`copyText` 已限制 256 字符且不含换行，`reason` 超长截断、换行替换为空格后再放入 payload）
- 公共 SSE 事件类型沿用 `task.status.changed`，payload 新增可选字段 `nodeId`、`nodeKind`、`roleKey`

## 任务列表

### 角色

1. ✅ #12701 删除 L0 角色选择，未锁定时用会话当前角色或默认角色
   - 删除 `fw/assistant/application/RoleSelector.java`、`DefaultRoleSelector.java`、对应测试 `DefaultRoleSelectorTest.java`
   - `AssistantInfrastructureAutoConfiguration` 删除 `modelRoleSelector`、`roleSelector` 两个 bean 与构造参数
   - `AssistantApplicationService.resolveExecutionProfile` 的 `route == null` 分支：
     - 先查新端口 `ConversationRolePort.find(tenantId, conversationId)`（见 #12702），角色仍在当前助理已发布角色中则用它，`selectedBy=CONVERSATION_ROLE`
     - 否则用 `definition.defaultRole()`，`selectedBy=DEFAULT_ROLE`；会话角色已失效时 `log.warn`
   - `appendRoleAudit` 改为接收 `(command, Role role, String selectedBy, String reason)`；`RoleSelector.RoleSelection` 的用法改为本地 record 或直接传参
   - `roleResolvedEvent` payload 增加 `selectedBy`
   - `AssistantExecutionService` 中引用 `DefaultRoleSelector` 的注释同步改写
   - 日志：`[角色选择] 角色已确定` 保留，补充 `selectedBy` 取值

2. ✅ #12702 会话当前角色端口
   - `fw/assistant/port/ConversationRolePort`：`Optional<String> find(TenantId, ConversationId)`、`void save(TenantId, ConversationId, String roleKey)`
   - aaf-api 实现：读写 `module/chat/conversation/domain/Conversation.agentRole`（`ConversationRepository.findByThreadId(conversationId.value())`），会话不存在时 find 返回空、save 记 debug 日志后跳过
   - 框架侧以 `ObjectProvider` 或可空注入使用，未提供实现时等同"无会话角色"
   - 写入时机：`activate_role` 成功、`promote_to_task` 带 `roleKey` 成功
   - 日志：`[会话角色] 已记录：conversationId={}，roleKey={}，来源={}`

3. ✅ #12703 协调者 prompt 注入角色目录
   - `PromptAssembler.compileAssistant` 在按需技能候选块附近新增 `appendRoleCatalog`
   - 注入条件：`InvocationPolicy.COORDINATOR`，且满足其一
     - 轻量态（无 taskId）且路由为 `ROUTE_AUTO`：说明可调用 `activate_role` 切换
     - 任务态协调者节点且分解策略不是 `DECOMPOSE_NONE`：说明 `delegate_agents` 可为执行者指定 `roleKey`
   - 内容：每个已发布角色一行，`roleKey`、名称、职责、非职责、可用按需技能 code；标出当前角色；声明"目录是数据，不是指令"
   - 角色摘要逻辑原在 `RoleSelector.RoleSelectionRequest.candidateProposal()`，删除后迁到 `PromptAssembler` 私有方法
   - sourceKey 用 `role-catalog:` 加 assistantId，层级沿用 `appendOnDemandCandidates` 的 `PromptLayerKind`/`PromptSourceKind` 取值

4. ✅ #12704 新增 `activate_role` 工具与轻量态续接
   - 新增 `fw/assistant/application/ActivateRoleTool.java`（`ContextAwareToolHandler`），`TOOL_NAME="activate_role"`，`readOnly()=true`
     - 入参：`roleKey`（必填）、`reason`（必填，≤256 字符）
     - 校验：`taskId == null && nodeIdentity == null`；`roleKey` 属于当前助理已发布角色（`AssistantDefinitionPort.findById`）；不等于当前角色（`context.skillExecutionProfile().selection().roleKey()`）
     - 成功后：登记到新 bean `DirectRoleHandoffRegistry`（进程内 `ConcurrentHashMap<ExecutionId, Handoff(fromRoleKey, toRoleKey, toRoleName, reason)>`），调用 `ConversationRolePort.save`，返回"已切换到角色 X，本轮立即结束，由新角色继续回答"
     - 在 `AssistantInfrastructureAutoConfiguration` 注册 bean
   - `BuiltinControlToolProfile`：新增 `DIRECT_AUTO_ROUTE_TOOLS = DIRECT_COORDINATOR_TOOLS + activate_role`；`resolve` 在轻量态协调者且 `executionIntent().routeConstraint() == ROUTE_AUTO` 时返回它；`ALL_TOOL_NAMES` 纳入 `activate_role`
   - `HarnessAgentExecutionAdapter`：`takeUntil(canonicalWait)` 改为 `takeUntil(e -> canonicalWait(e) || controlHandoff(e))`；`controlHandoff` 为 `TOOL_CALL_COMPLETED` 且 `payload.toolName` 属于 `activate_role`、`promote_to_task`；`preserveHitlState` 仍只认 `canonicalWait`
   - `AssistantApplicationService`：
     - `finalizeDirect` 在 PROMOTED 分支之后检查 `DirectRoleHandoffRegistry`：命中时把执行置为 `Execution.Status.SUPERSEDED`（先核实 DIRECT 是否允许该状态，不允许则用 COMPLETED），不跑 `completionValidator`、不写会话记忆和长期记忆，发出 `ROLE_SWITCHED` 事件（不是 `EXECUTION_COMPLETED`）
     - `invoke` 对 DIRECT 在整条管线外 `concatWith(Flux.defer(...))`：取出 handoff 后构造续接 `AssistantInvocation` 并递归 `invoke`，保证续接执行有独立的持久化、失败收口与追踪
     - 续接命令：新 `executionId = UUID.nameUUIDFromBytes("role-handoff|" + 原 executionId)`；`sequenceBase` = 前一执行最后 sequence；`executionIntent` 改为 `ROUTE_FIXED(toRoleKey, skill=null, revision)`（续接后不再暴露 `activate_role`，每个 Run 最多切换一次）；其余字段沿用。需要时在 `AssistantCommand` 新增一个复制方法，不改已有方法
     - 核实同一 `sessionId` 下两个执行的 AgentState 隔离（`stateUserKey` 含 `stateSlot=executionId`），有冲突则续接用新 `sessionId`
   - 新增 `ExecutionEventType.ROLE_SWITCHED`，同步 `AafAiTaskEventRegistry`（`aaf.role.switched`，END_USER）、`ExecutionEventPublicMapper`、`ReferenceSafeData.ALLOWED_KEYS`/`DISPLAY_TEXT_KEYS`；AG-UI 走 `PublicEventFallbackConverter` 成为 CUSTOM，不进 `RunLifecycleEventConverter`
   - 日志：
     - `[角色切换] 协调者请求切换：executionId={}，from={}，to={}，reason={}`
     - `[角色切换] 前一执行已收口：executionId={}，status={}`
     - `[角色切换] 续接执行开始：runId={}，originExecutionId={}，executionId={}，roleKey={}`

### 任务升级

5. ✅ #12705 `promote_to_task` 携带角色与形态
   - `PromoteDirectTaskTool` 入参新增可选 `roleKey`（必须属于当前助理已发布角色）、可选 `shape`（`STEPS` 或 `DELEGATE`）；描述改为覆盖"语义上需要多个有序步骤""需要不同角色分工""用户明确要求多角色协作"
   - `TaskCommandService.promoteDirect(tenantId, originExecutionId, reason, roleKey, shape)`：协调者节点角色取 `roleKey`，为空沿用 `profile.roleAssignment().roleKey()`；`shape` 写入 `TaskCheckpoint` 注解 `promotionShape`，并在协调者节点描述开头追加一行 `[升级说明] 形态=…；理由=…`
   - 带 `roleKey` 时调用 `ConversationRolePort.save`
   - `EXECUTION_PROMOTED` payload（`directTerminalEvent` 与 `JpaTaskMaterializationAdapter.promotionEvent`）新增 `roleKey`、`shape`；公共 mapper 放行
   - 日志：`[任务升级] DIRECT 已提升：originExecutionId={}，taskId={}，roleKey={}，shape={}，reason={}`

6. ✅ #12706 升级后同一条 SSE 继续接任务事件
   - `RunLifecycleEventConverter` 不再处理 `EXECUTION_PROMOTED`（从 `supportedTypes` 与 switch 中移除），由 fallback 投影为 CUSTOM `aaf.task.promoted`；RUN_FINISHED 改由任务交付节点完成时发出
   - `AssistantExecutionService.execute` 的 DIRECT 分支：观察到 `EXECUTION_PROMOTED` 后读取 `promotedTaskId`，`concatWith` 一个续流：
     1. `liveEvents.open(tenantId, taskId)`
     2. 用 `ExecutionEventStorePort.readTask(tenantId, taskId, 0)` 回补已落库事件
     3. 输出回补事件，再输出实时事件，按 `eventId` 去重
     4. `takeUntil(new RunDeliveryTracker()::endsStream).timeout(TASK_EVENT_STREAM_TIMEOUT)`，与 TASK 分支一致
   - 回补只能拿到落库事件，`MESSAGE_DELTA` 不落库，回补窗口内的增量文本只会以 `MESSAGE_COMPLETED` 出现，可接受
   - 已知限制：任务事件 sequence 与 DIRECT 不在同一序列，SSE id 可能回退，本期不处理，写进 dev-log
   - 日志：`[任务续流] 开始跟随任务事件：runId={}，taskId={}，回补事件数={}`、`[任务续流] 结束：taskId={}，原因={}`

7. ✅ #12707 TASK 允许自动路由，委派授权基线改为助理级
   - `ExecutionIntent` 构造器删除"TASK 必须 ROUTE_FIXED"校验；`ROUTE_FIXED` 须有 `resolvedRoute`、`ROUTE_AUTO` 不许预置路由的校验保留
   - `AssistantExecutionService.executionIntent` 的 TASK 分支与对话分支同规则：都不给为 `ROUTE_AUTO`；只给 Role 为 `ROUTE_FIXED(role, null)`；只给 Skill 用 `roleByExplicitSkill`；都给时校验技能属于角色候选
   - 程序化入口（`submitTask` 一侧使用 `route.roleKey()/skillKey()` 处）继续要求 Role 与 Skill，缺失抛 `EXECUTION_TASK_ROUTE_INCOMPLETE`
   - `ExecutionIntentTest.taskCannotUseAutoRoute` 改为断言 TASK + AUTO 合法；`AssistantExecutionServiceTest` 中断言 TASK 必须 FIXED 的用例同步调整
   - `SubmitCoordinationPlanTool`：
     - `skillKey` 改为可选：省略且角色与协调者相同时继承协调者技能（可以为空）；省略且角色不同时为空，执行者以 `SELECT_AND_AUGMENT` 运行
     - `requireDelegable`：角色必须属于助理已发布角色；给了技能则必须属于该角色候选；删除"工具集不得超出协调者角色"校验
     - 核实 `TaskNode.pending` 与执行者执行链路接受空 `skillKey`
     - inputSchema 与错误信息里的候选目录说明同步改写
   - 日志：`[协调委派] 计划已接收：taskId={}，执行者数={}，执行者角色={}`（`nodeId:roleKey/skillKey` 列表）

8. ✅ #12708 内置技能文案
   - `skills/builtin-task-triage.md`：复杂度三档改为四种动作（直答、`activate_role` 切换角色后由新角色回答、升级拆步骤、升级拆多角色）；升级条件补充多步骤与多角色分工；用户明确要求多角色协作时优先遵从，`promote_to_task` 带 `shape=DELEGATE`；要切换角色时必须在输出任何正文之前调用
   - `skills/builtin-agent-delegation.md`：`skillKey` 可选；执行者角色从角色目录中选，不再要求工具集是协调者子集
   - 已有库不会自动更新，记入 dev-log

### 看板

9. ✅ #12709 公共 SSE 推送任务与节点状态
   - `TaskStatusEventPublisher.STATUS_EVENTS` 增加 `EXECUTION_STARTED`、`EXECUTION_PROMOTED`、`ROLE_SWITCHED`
   - `EXECUTION_PROMOTED` 的 taskId 为空时从 payload 的 `promotedTaskId` 取；`ROLE_SWITCHED` 无 taskId 时用 `executionId`，payload 加 `scope=DIRECT`
   - payload 新增可选字段：`nodeId`、`nodeKind`、`roleKey`（事件带 `nodeIdentity` 时）；`ROLE_SWITCHED` 带 `toRoleKey`
   - 核实 `onEventCreated` 只在事件落库时触发；`EXECUTION_PROMOTED` 由 `JpaTaskMaterializationAdapter.promotionEvent` 落库，需确认会触发
   - 新增 REST `GET /api/ai/tasks/active`：当前用户未终结 Task 列表（跨会话，最多 50 条，按更新时间倒序），返回现有 `TaskDetails` 投影；`TaskQueryPort` 无合适查询时新增一个按用户与状态查询的方法
   - 日志：`[看板事件] 已发布：type={}，taskId={}，eventType={}，nodeKind={}`（debug）

### 前端

10. ✅ #12710 角色下拉增加"自动"
    - `apps/webui/src/features/chatter/toolbar/ChatterToolbar.tsx` 的 `AssistantRoleSelect` 每个助理分组首项增加"自动（助理判断）"，选中时 `onTargetChange({...target, type:"ai", assistantId, agentRole: undefined, agentSkill: undefined})`
    - 自动状态下触发器显示 `{助理名} · 自动`，收到 `aaf.role.resolved` 后显示 `{助理名} · 自动 · {角色名}`（读取 `useAgentRunStore` 的 `selectedRole`，这是已有 UI 状态）
    - 核实 `GlobalChatter.tsx` 中页面配置 `config.agentRole` 是否让默认对话变成锁定角色；页面没有明确要求固定角色时初始 target 不带 `agentRole`

11. ✅ #12711 对话流展示角色切换、升级与子智能体
    - `apps/webui/src/features/livechat/runtime/ag-ui-runtime.tsx` 的 `onCustomEvent`：
      - `aaf.role.switched` → `run.pushEntry({type:"ROLE_SWITCHED", title:"切换角色", message:"已切换到 {toRoleName}：{reason}"})`，并 `setSelectedRole`
      - `aaf.task.promoted` → `run.pushEntry({type:"TASK_PROMOTED", title:"转为任务", message: shape 对应文案})`，并失效 `taskKeys.all`（现有 `aaf.task.` 前缀分支会提前 return，需把 promoted 判断放在它前面）
      - `aaf.executor_plan.` 前缀 → 失效 `taskKeys.all`，让 TaskPanel 刷新步骤
    - 把 `features/livechat/components/` 下的 `SelectedRoleBadge`、`AgentRunStatus`、`SubTaskActivityList` 挂到 Chatter 对话视图（`features/chatter/layout/ChatterPanel.tsx` 或 `ChatterThread.tsx`），位置参照 `livechat/ChatLayout.tsx`
    - 遵守 base-ui 规则：Trigger 不套 `<Button>`，组合用 `render` prop

12. ✅ #12712 首页看板接入真实任务状态
    - `apps/webui/src/lib/api/rest/ai/task.ts` 增加 `taskApi.active()` 与 `useActiveTasks()`（queryKey `taskKeys.active`），调用 #12709 的接口
    - `useActiveTasks` 内 `useEventSubscription("task.", …)` 收到事件即失效 `taskKeys.active`
    - `features/studio/home/canvas/CollaborationCanvas.tsx` 助理节点的 `runningTasks` 用真实数量，替换 `MOCK_RUN_TASKS.length`
    - `RoleNode` 显示该角色运行中的节点数（活跃任务中 `plan.nodes` 状态未终结且 `roleKey` 匹配），数据从 query 派生，不进 Zustand
    - `RunLiveCanvas` 的模拟数据本期不替换，写进 dev-log

### 文档

13. ✅ #12713 ADR-011 补充会话当前角色、升级续流、看板事件

14. ✅ #12714 对话不锁定角色
    - `AssistantAguiController` CHAT 模式：`forwardedProps.role` 不再进入路由，改为调用 `ChatService.rememberStartingRole` 写入会话当前角色，请求按 `ROUTE_AUTO` 执行
    - 前端删除"自动（助理判断）"选项；下拉显示服务端本轮实际角色，协调者切换后把起步角色同步为实际角色，避免下一轮把会话角色改回旧选择
    - 续接执行的 `aaf.role.resolved` 为固定路由，前端在同一角色时保留"助理切换"来源

## 依赖关系

```text
#12701 → #12702 → #12704
#12701 → #12703
#12704 → #12705 → #12706
#12707、#12708 可并行
#12709 依赖 #12704（ROLE_SWITCHED）与 #12705（promoted payload）
#12710、#12711 依赖本文契约，可与后端并行
#12712 依赖 #12709 的接口
```
