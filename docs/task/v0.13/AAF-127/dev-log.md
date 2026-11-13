# 开发记录：助理自动切换角色、任务升级续流与看板状态（AAF-127）

执行者：AI/developer-service、AI/developer-webui

> 按用户要求，本故事不执行编译、测试与 lint/check，以下记录均未经构建验证。

## #12701 删除 L0 角色选择

✅ 11-11 — developer-service

- 删 RoleSelector/DefaultRoleSelector/测试与两个 bean，无开关
- 未锁定：会话角色有效则用，否则默认角色，失效记 warn
- appendRoleAudit 改传 Role+selectedBy+reason
- ROLE_RESOLVED 增 selectedBy：SERVER_FIXED_ROUTE/CONVERSATION_ROLE/DEFAULT_ROLE/EXECUTION_PROFILE_REUSED

## #12702 会话当前角色端口

✅ 11-11 — developer-service

- 框架端口 `ConversationRolePort`，ObjectProvider 可空注入
- 实现 `chat/conversation/service/ConversationRoleService`，按 threadId 读写 agent_role，校验 orgId
- 写入点：activate_role 成功、promote_to_task 带 roleKey 成功
- roleKey 超 64 字符（列长）记 warn 跳过

## #12703 协调者 prompt 注入角色目录

✅ 11-11 — developer-service

- `PromptAssembler.appendRoleCatalog`，位于按需候选后、领域上下文前
- 轻量态 ROUTE_AUTO 或任务态非 DECOMPOSE_NONE 的协调者注入
- `AssistantPromptRequest` 增 `taskState` 判定容器状态

## #12704 activate_role 与轻量态续接

✅ 11-11 — developer-service

- 新 `ActivateRoleTool`、`DirectRoleHandoffRegistry`（handoff 带 assistantRevision）
- harness `takeUntil` 增控制交接：activate_role/promote_to_task 成功即止
- finalizeDirect 命中 handoff 置 SUPERSEDED，发 ROLE_SWITCHED，不验不记忆
- invoke 管线外 concatWith 递归续接，新 executionId 由原 ID 派生，ROUTE_FIXED(toRole, null)
- 核实：DIRECT 可 SUPERSEDED；stateUserKey 含 executionId，续接沿用 sessionId 无冲突

## #12705 promote_to_task 携带角色与形态

✅ 11-11 — developer-service

- 工具增 roleKey（校验属助理角色）、shape（STEPS/DELEGATE）
- `promoteDirect` 增两参；换角色则不继承原 Skill；注解 promotionShape
- 协调者节点描述前加 `[升级说明] 形态=…；理由=…`
- PROMOTED payload 增 roleKey/shape；promotionEvent 补 userId 供看板推送

## #12706 升级后同一 SSE 续接任务事件

✅ 11-11 — developer-service

- RunLifecycleEventConverter 移除 EXECUTION_PROMOTED，走 fallback CUSTOM
- `AssistantExecutionService.followPromotion`：先 open 再 readTask 回补，eventId 去重
- 收口 RunDeliveryTracker + 10 分钟超时，与 TASK 分支一致

## #12707 TASK 自动路由与助理级委派基线

✅ 11-11 — developer-service

- 删 ExecutionIntent "TASK 必须 FIXED"；TASK 与对话同路由规则
- 程序化入口仍缺 Role/Skill 即抛 EXECUTION_TASK_ROUTE_INCOMPLETE
- 委派：角色属助理即可，技能可省；删工具子集校验
- `ExecutorAssignment.skillKey` 改可空；核实 TaskNode/NodeIdentity 均接受空 skillKey
- 测试同步：`ExecutionIntentTest.taskCanUseAutoRoute`，`AssistantExecutionServiceTest` 补 eventStore mock

## #12708 内置技能文案

✅ 11-11 — developer-service

- triage：四种动作，升级条件补多步骤/多角色，多角色要求走 shape=DELEGATE
- delegation：skillKey 可选，角色取自角色目录，删工具子集要求

## #12709 公共 SSE 推送任务与节点状态

✅ 11-11 — developer-service

- STATUS_EVENTS 增 EXECUTION_STARTED/EXECUTION_PROMOTED/ROLE_SWITCHED
- PROMOTED 取 promotedTaskId；ROLE_SWITCHED 无 task 用 executionId 且 scope=DIRECT
- payload 增 nodeId/nodeKind/roleKey/toRoleKey
- 核实：promotionEvent 经 SynchronousExecutionEventWriter 落库触发回调
- 新 `GET /api/ai/tasks/active`（跨会话未终结，≤50，updatedAt 倒序）；NodeDetails 增 roleKey

> **问题**：TASK 自动路由且产物策略为 AUTO_SAVE_DRAFT 时协调者无技能声明保存工具 → 画像冻结抛"产物保存工具不在 Skill 固定执行画像内" → 自动路由的 TASK 调用方需传 RETURN_ONLY，本期未改

## 已知限制（后端）

- 续流中任务事件 sequence 与 DIRECT 不同序，SSE id 可能回退
- 回补窗口内 MESSAGE_DELTA 不落库，只以 MESSAGE_COMPLETED 完整出现
- 内置技能 md 仅 `ai_skill_definition` 为空时导入，已有库需清空技能表才生效
- activate_role 未在工具侧强制"正文前调用"，只靠技能文案与工具描述约束
- 续接执行未写 Execution.predecessorExecutionId，也未注入切换理由作受控上下文；前驱关系只能从派生 ID 与 ROLE_SWITCHED 事件推出
- 续接执行的 ROLE_RESOLVED selectedBy 为 SERVER_FIXED_ROUTE，切换来源以 ROLE_SWITCHED 为准
- DirectRoleHandoffRegistry 为进程内登记，只在同一进程的同一条 SSE 内生效

## #12710 角色下拉增加"自动"

✅ 11-11 — developer-webui

- 每个助理分组首项加"自动（助理判断）"，选中保留 assistantId、清空角色与技能
- 自动态触发器显示 `助理名 · 自动 · 角色名`，角色名只取非 ROUTE_FIXED 的 selectedRole
- GlobalChatter 已核实：无页面声明 agentRole，默认 target 不带角色，未改
- 文件：`features/chatter/toolbar/ChatterToolbar.tsx`

> **已知限制**：未锁定助理（初始 target 无 assistantId）时接口无默认助理标识，触发器显示"AI 助理 · 自动"

## #12711 对话流展示角色切换、升级与子智能体

✅ 11-11 — developer-webui

- runtime：`aaf.task.promoted` 置于 `aaf.task.` 前缀分支之前，按 shape 写 entry 并失效 `taskKeys.all`
- runtime：`aaf.role.switched` 写 entry 并 setSelectedRole；`aaf.executor_plan.` 失效任务查询
- `SelectedRole` 加 `selectedBy`，徽标按其显示来源，替换已失效的"AI 自动选择"文案
- `AgentRunStatus` 末条 entry 有 message 时追加显示，便于看到切换理由
- ChatterPanel 非 guest 挂载 SelectedRoleBadge、SubTaskActivityList、AgentRunStatus

> **已知限制**：若后端续接执行在同一 SSE 内再次发 RUN_STARTED，`startRun` 会清空 entries，"切换角色"条目随之消失，角色徽标仍由新的 `aaf.role.resolved` 更新

## #12712 首页看板接入真实任务状态

✅ 11-11 — developer-webui

- 新增 `restEndpoints.ai.activeTasks`、`taskApi.active()`、`useActiveTasks()`（queryKey `taskKeys.active`）
- `task.` 事件只失效 `taskKeys.active`；有活跃任务时 10s 轮询兜底
- 助理节点 runningTasks 取活跃任务数，替换 `MOCK_RUN_TASKS.length`
- 角色节点按 roleKey 统计未终结节点数，`CollaborationRole`/`RoleNodeData` 加 roleKey
- RunLiveCanvas 仍为模拟数据，本期不替换

> **问题**：后端 `TaskDetails.NodeDetails` 无 roleKey → 前端无法按角色统计节点 → 前端 `TaskNodeDetails.roleKey` 先声明为可选，后端补齐投影前角色角标恒为 0；活跃任务无 assistantId，助理节点统计的是当前用户全部活跃任务

## #12714 对话不锁定角色

✅ 11-11 — 协调者

- CHAT 模式 role 不入路由，写会话起步角色
- 新增 `ChatService.rememberStartingRole`
- 前端删"自动"选项，显示实际角色
- 自主切换后同步起步角色为实际角色
- 续接固定路由时保留"助理切换"来源
