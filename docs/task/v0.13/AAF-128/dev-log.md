# 开发记录：轻量态遇授权工具自动升级并携带待执行调用（AAF-128）

执行者：AI/developer-service

> 按人类要求，本故事不执行编译、测试与 lint/check，以下记录均未经构建验证。

## #12805 等待已原子提交后节点不再写事件、不提交结果

✅ 11-12 — developer-service

- finalizeNodeExecution 见本节点 canonical 等待事件即记 trace 与 AWAITING_HUMAN，返回空流
- NEEDS_USER 分支改抛 IllegalStateException，旧 VALIDATION_*/EXECUTION_PAUSED 删除；TASK_ROOT 不动
- executeClaimed 置 awaitingHuman，跳过 commit，记 task_node_commit outcome=AWAITING_HUMAN
- JpaTaskMaterializationAdapter.rejection 未改

## #12801 PendingToolCall 与升级携带

✅ 11-12 — developer-service

- 新 `PendingToolCall`（注解往返、`digestOf` 与网关 action 摘要同算法、`requireDigest`）与 `DirectPromotionPort`
- `promoteDirect` 增参 pendingCall，写 checkpoint `promotionPendingCall`；PromoteDirectTaskTool 传 null
- TaskCommandService 实现 `promoteForPendingCall`，构造增 `AssistantDefinitionPort`
- 升级事件 `trigger`/`pendingToolName`：物化事件与 finalizeDirect 实时事件（promotionDetails）两处均写入，公共映射已放行

> 实时 SSE 收到的是 finalizeDirect 生成的 EXECUTION_PROMOTED（Assistant 侧不落库），只改物化事件前端拿不到 trigger，故两处同改。

## #12802 授权工具对轻量态可见但只能触发升级

✅ 11-12 — developer-service

- `ToolAuthorizationRule.promotionOnly`，保留 4 参构造默认 false；为 true 时必须需授权
- requireVisible：promotionOnly 仅允许 READ_ONLY；READ_ONLY 写工具非 promotionOnly 才拒
- 画像：轻量态含 promote_to_task 时 taskRequired 工具（过功能权限）合入，跳过 requireAllowed
- REQUIRES_TASK 文案、InvocationPolicy.COORDINATOR、builtin-task-triage、promote_to_task 描述去掉"授权先 promote"

## #12803 网关自动升级与控制交接收口

✅ 11-12 — developer-service

- `ToolGatewayPort` 增 `PromotionHandoffException`、`CONTROL_HANDOFF_KEY`；DIRECT 拒绝分支删除
- 网关：画像无 promote_to_task 即失败；否则构造 PendingToolCall → boundedElastic 升级 → 抛交接异常
- 参数策略 `DefaultToolParameterPolicy` 对写工具要求 grant，升级分支跳过，任务态重放时完整校验
- PortBackedAgentTool 记证据 controlHandoff/promotedTaskId 转 ToolSuspend；映射器出 TOOL_CALL_COMPLETED
- Harness 谓词改认证据标记，删 CONTROL_HANDOFF_TOOLS；trace 记"已交接"；自动配置 ObjectProvider 延迟获取

## #12804 协调者节点合成挂起调用

✅ 11-12 — developer-service

- 可行性核实通过（agentscope-core 2.0.4，tmp/agentscope-java revision=2.0.4），证据见下
- `AgentExecutionCommand.seededToolCall`，与 resumeStateRequired 互斥，旧构造默认 null
- agentCommand：协调者、非恢复、attempt 1、checkpoint 有携带调用时设置；工具不可见即抛
- Harness 校验摘要后经 `agent.getAgentState(slot)` 写入冻结消息 + assistant tool_use，save 后空输入启动

> 核实证据：`ReActAgent.activateSlotForContext`（约 669 行）每次调用从 stateStore 重载 agent_state；`doCallInner`（约 1833–1865 行）有挂起调用且输入为空时 `resumeAgent()` → `acting(0)`，后者以 `MessageUtils.extractPendingToolCalls` 取调用（约 2859 行）；`extractPendingToolCalls` 取最后一条 name=agentName 的 ASSISTANT 消息中无结果的 ToolUseBlock；AAF 编译器 `enablePendingToolRecovery(false)`，不会被自动补错误结果；`ToolExecutor`（约 233 行）按 `ToolUseBlock.getContent()` 做 schema 校验，故合成块的 content 必须是参数 JSON；`getAgentState(String,String)` 注释写明支持"get → mutate → save"。

## #12807 显式控制交接工具同样挂起循环

✅ 11-12 — developer-service

- promote_to_task、activate_role 成功结果 metadata 加 controlHandoff=true，经 #12803 路径挂起
- finalizeDirect 判 PROMOTED 读 Execution 状态、角色切换读 handoff 注册表，均不依赖工具结果状态
- 交接工具的 TOOL_CALL_COMPLETED.resultState 由 SUCCESS 变为挂起原值

## #12809 农业技能文案

✅ 11-12 — developer-service

- 对话中：确认设备 → 起草 → 直接调确认工具，删除 promote_to_task 步骤
- 任务中：据确认结果 `device` 回复，不再额外调读数；草稿过期重新起草并重新审批
- 内置技能 md 仅在 `ai_skill_definition` 表为空时导入，已有库需清空技能表后生效

## #12811 自动升级提示文案

✅ 11-12 — developer-webui

- `aaf.task.promoted` 读 `trigger`/`pendingToolName`，记入本轮运行记录
- 新增 `ToolGatePromotionChip`，与 `ControlToolChip` 共用行内样式
- 仅当前轮末条助手消息显示"该操作需要你审批，已转为任务 · 工具名"
- 核查：工具结果 role=tool 无 mcpResult，`isError` 恒 false，挂起不误判失败

## 注意事项

- 未编译：签名变更点（DefaultToolGateway 构造、ToolAuthorizationRule 5 参、AgentExecutionCommand 11 参、promoteDirect 7 参）已按搜索同步调用点，需协调者编译确认
- `ToolAuthorizationRule` 新增布尔字段随画像快照 JSONB 存储；旧快照缺该字段时依赖 Jackson 对缺失基本类型取默认 false
- 携带调用常驻 Task checkpoint：后续若产生新的协调者 attempt 1 执行（如整体重规划），会再次重放，需协调者评估是否要加"已重放"标记
- 既有单测若断言旧 DIRECT 拒绝文案、节点 NEEDS_USER 事件序列或交接工具 resultState=SUCCESS，运行期会失败，需补派测试更新

## #12806 审批卡片到达前端核对（联调修正）

✅ 11-13 — 协调者

- 恢复流先发 RUN_STARTED：`AgUiProjector.Session.start`，审批/澄清恢复订阅前调用
- 仅协调者计划由协调者交付：commit 视其为 finalizer，RUNNING→VERIFYING→COMPLETED，不再 PAUSED+finalizerMissing
- 审批卡只在任务弹窗渲染；面板只给"等待授权"入口；去掉依赖 interrupt 的去重

> 联调证据：恢复流首个 AG-UI 事件为 CUSTOM（aaf.authorization.granted），触发 "First event must be 'RUN_STARTED'"；ai_task.status=PAUSED 且 checkpoint 含 finalizerMissing，节点 result_text 已生成。

## #12806 联调修正（二）

✅ 11-13 — 协调者

- 恢复流工具结果不补 TOOL_CALL_END：调用的 START/END 属于上一 run，只回填 TOOL_CALL_RESULT
- `Task.RootResult` 允许 sourceNodeId=coordinator；提交条件加限 nodeId=coordinator，修复提交回滚致任务停在 RUNNING
- 任务面板等待授权改为标题行小图标，点击打开弹窗处理

> 联调证据：EXECUTION_COMPLETED 已落库，但 Task/节点/Execution 仍为 RUNNING、Dispatch 仍 CLAIMED——commit 内 RootResult 构造校验 sourceNodeId 必须为 aggregator 抛错，整个事务回滚。
