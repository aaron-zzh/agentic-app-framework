## 方案推翻：`cancel` 签名改动 → `AgentExecutionCommand` 保留信号（2026-09-01）

- ⚠️ **原方案（改 `AgentExecutionPort.cancel` 签名加终止原因参数）已推翻**，依据是通读 `tmp/agentscope-java` 的 `ReActAgent.loadOrCreateAgentStateForSlot` 源码（`agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:376-397`）：core 侧续跑判断逻辑只有"`stateStore.get(userId, sessionId, "agent_state", ...)` 有值则用、没有则 `freshState()`"这一条，**没有任何"这是续跑还是新对话"的显式标志**，也不检查 `shutdownInterrupted`（该标记只在 `GracefulShutdownMiddleware.onAgent` 单独消费，不影响这个加载判断）。这说明"要不要续跑"纯粹是"数据还在不在"的问题，`HarnessAgentExecutionAdapter.doFinally` 不需要理解"为什么终止"才能决定删不删状态槎，只需要知道"调用方是否期待下次还能查到状态"——这个决定权本就该在发起 execution 的调用方，不需要靠 `cancel()` 携带原因反推。
- **新方案**：`AgentExecutionCommand`（record，全仓唯一构造点在 `AssistantApplicationService` 内部私有方法，`grep "new AgentExecutionCommand("` 确认只有一处）新增一个保留信号字段，由该构造点按 `command.operation()` 正向赋值。`doFinally` 读这个字段决定是否跳过 `deleteExecutionState`。改动面从"改接口方法签名 + 两处调用方分别传参"变为"record 加字段 + 一处构造点赋值"，风险等级从 🔴 降为 🟡。
- ⚠️ **同一轮读 `agentscope-harness` 的 `PlanModeMiddleware`/`PlanModeTools`/`PlanModeManager`/`PlanModeContextState` 源码后确认**：官方 Plan Mode 的状态（`planActive`+`currentPlanFile`）就是 `AgentState` 里的两个普通字段，续跑完全靠同一套 `(userId,sessionId)` 持久化自然发生，不需要额外恢复机制——这与本次核实的 `loadOrCreateAgentStateForSlot` 结论一致，进一步印证"续跑不需要显式信号，只需要数据还在"这个判断。
- 用户已确认不调整 AAF-107 已拍板的"计划提交不设独立审批关卡"决策，Plan Mode `plan_exit` 走 `checkPermissions`→`ASK` 的发现仅记录在案（见上一条），不据此重开审批讨论。


## 附：核实发现（2026-09-01，非本任务范围内改动，仅记录）

- ⚠️ **ADR-006「决策推翻」章节的论证依据不完整**：读官方 [Plan Mode](https://java.agentscope.io/v2/zh/docs/harness/plan-mode.html) 文档原文确认，`plan_exit`（退出计划模式进入执行）**确实需要 HITL 确认**（"退出 Plan Mode 走 HITL 确认（复用权限系统的 ASK），避免模型一意孤行直接进入执行"）。ADR-006 此前推翻"计划需要独立审批"的依据只读了 `permission-system.html`，未读 `plan-mode.html` 本身，导致"官方没有'计划本身要不要审'这层概念"这一判断依据不完整——官方有这层概念，只是承载方式是运行时 `plan_exit` 触发的 HITL，不是独立状态机审批表。这不必然代表推翻结论本身错误（AAF 两次独立 execution 的架构与官方"同次 call 内切阶段"不同，审批必要性可能确实不同），但论证依据需要人类决定是否要求补正 ADR-006，本次未做修改。
