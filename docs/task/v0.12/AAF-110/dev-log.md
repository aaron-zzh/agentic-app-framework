## 方案二次推翻：`AgentExecutionCommand` 保留信号 → `AgentExecutionPort.pause(ExecutionId)`（2026-09-02）

- ⚠️ **原方案（`AgentExecutionCommand` 新增保留信号字段）在编码阶段被推翻**：动手实施 #11002 前核实 `AssistantApplicationService.controlTask`（`PAUSE`/`TAKE_OVER`/`CANCEL` 唯一处理入口）完整实现，发现这三种操作都不构造新的 `AgentExecutionCommand`，只调用 `agentExecution.cancel(command.executionId())` 终止一个已经在跑的旧 execution。`AgentExecutionCommand` 唯一构造点（`agentCommand(...)`）只在两处 `agentExecution.execute(...)` 调用点使用，两者都是**发起新 execution**，跟**终止现有 execution** 是完全不相交的两条路径——保留信号不可能预先埋在一份根本不会被重新构造的命令对象里。
- 之前（2026-09-01）"推翻改 `cancel()` 签名"的第一性原理判断本身没错（"数据还在不在才是关键"），但落地方式选错了——第一轮推翻时只验证了"`AgentExecutionCommand` 全仓唯一构造点"，没有验证"这一处构造点是否覆盖 `PAUSE`/`TAKE_OVER` 场景"，两者被误当成了同一件事。
- **修正后方案**：`AgentExecutionPort` 新增 `pause(ExecutionId)` 方法（不是改 `cancel` 签名加参数——那会牵动所有既有调用点被迫显式传参；而是新增一个语义独立方法，跟 `cancel` 并列，风险更低）。`HarnessAgentExecutionAdapter` 内部新增 `TerminalState.PAUSING` 参与终态仲裁，因为仲裁后 `terminal` 字段统一收敛为 `TERMINATED`（`compareAndSet` 目标状态相同），无法反推走的是哪条路径，额外引入 `ActiveExecution.pauseWon`（`AtomicBoolean`）独立记录。

### 实现文件

| 文件 | 说明 |
|------|------|
| `.../agent/port/AgentExecutionPort.java` | 新增 `Mono<Boolean> pause(ExecutionId executionId)` |
| `.../infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java` | 实现 `pause(...)`；`TerminalState` 新增 `PAUSING`；新增 `pauseWonRace(...)`；`ActiveExecution` 新增 `pauseWon`/`shutdownRequestId` 两个字段；两处终态仲裁分支新增 `pauseWonRace` 判断分支；`interruptIfCancelledAndStarted` 扩展判断 `PAUSING`；`release(...)` 读 `pauseWon` 决定是否跳过删除；接入 `GracefulShutdownManager.bindStateSaver`/`registerRequest`/`bindRequestState`/`unregisterRequest` |
| `.../infrastructure/agentscope/mapping/AgentScopeEventMapper.java` | 新增 `paused(...)` 公开方法（与 `canceled(...)` 平级），产出既有 `ExecutionEventType.EXECUTION_PAUSED` |
| `.../assistant/application/AssistantApplicationService.java` | `controlTask` 按 `command.operation()` 分流：`PAUSE`→`pause(...)`，`CANCEL`/`TAKE_OVER`→`cancel(...)` |

### 关键发现：`RuntimeContext.getAgentState()` 是 call-scoped，建立 `active` 时读不到

`GracefulShutdownManager.bindRequestState(requestId, state)` 需要的 `AgentState` 只在框架于 `call()`/`streamEvents()` **入口**才注入到 `RuntimeContext`（core 官方文档："Called by the agent at call entry; not part of the public tool/middleware contract"）。`executeResolved` 建立 `active` 变量的那一刻（`streamEvents(...)` 尚未开始订阅）读取 `runtimeContext.getAgentState()` 会是 `null`。改为在 `onSourceEvent` 的 `AGENT_START` 分支（已确认的"Agent 才可被中断"时机点）才调用 `bindRequestState`，此时 `RuntimeContext` 已被框架填充。这个时机点与"补发早到的取消/暂停请求"共用同一个信号（`AGENT_START` 事件），是天然对齐的复用。

### 验证

- 按人类要求本次不执行 `pnpm nx test service`；`pnpm nx compile service` 待统一执行（见 #11005）。
- 人工核对：`DelegatedTaskCoordinator.cancelRunningChildren`（`stop`/`takeOver` 共用）确认无 PAUSE 语义，保留 `cancel(...)` 不变；`AgentExecutionPort` 全仓核实唯一实现 `HarnessAgentExecutionAdapter` 已补齐 `pause(...)`。

---


## 方案推翻：`cancel` 签名改动 → `AgentExecutionCommand` 保留信号（2026-09-01）

- ⚠️ **原方案（改 `AgentExecutionPort.cancel` 签名加终止原因参数）已推翻**，依据是通读 `tmp/agentscope-java` 的 `ReActAgent.loadOrCreateAgentStateForSlot` 源码（`agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:376-397`）：core 侧续跑判断逻辑只有"`stateStore.get(userId, sessionId, "agent_state", ...)` 有值则用、没有则 `freshState()`"这一条，**没有任何"这是续跑还是新对话"的显式标志**，也不检查 `shutdownInterrupted`（该标记只在 `GracefulShutdownMiddleware.onAgent` 单独消费，不影响这个加载判断）。这说明"要不要续跑"纯粹是"数据还在不在"的问题，`HarnessAgentExecutionAdapter.doFinally` 不需要理解"为什么终止"才能决定删不删状态槎，只需要知道"调用方是否期待下次还能查到状态"——这个决定权本就该在发起 execution 的调用方，不需要靠 `cancel()` 携带原因反推。
- **新方案**：`AgentExecutionCommand`（record，全仓唯一构造点在 `AssistantApplicationService` 内部私有方法，`grep "new AgentExecutionCommand("` 确认只有一处）新增一个保留信号字段，由该构造点按 `command.operation()` 正向赋值。`doFinally` 读这个字段决定是否跳过 `deleteExecutionState`。改动面从"改接口方法签名 + 两处调用方分别传参"变为"record 加字段 + 一处构造点赋值"，风险等级从 🔴 降为 🟡。
- ⚠️ **同一轮读 `agentscope-harness` 的 `PlanModeMiddleware`/`PlanModeTools`/`PlanModeManager`/`PlanModeContextState` 源码后确认**：官方 Plan Mode 的状态（`planActive`+`currentPlanFile`）就是 `AgentState` 里的两个普通字段，续跑完全靠同一套 `(userId,sessionId)` 持久化自然发生，不需要额外恢复机制——这与本次核实的 `loadOrCreateAgentStateForSlot` 结论一致，进一步印证"续跑不需要显式信号，只需要数据还在"这个判断。
- 用户已确认不调整 AAF-107 已拍板的"计划提交不设独立审批关卡"决策，Plan Mode `plan_exit` 走 `checkPermissions`→`ASK` 的发现仅记录在案（见上一条），不据此重开审批讨论。


## 附：核实发现（2026-09-01，非本任务范围内改动，仅记录）

- ⚠️ **ADR-006「决策推翻」章节的论证依据不完整**：读官方 [Plan Mode](https://java.agentscope.io/v2/zh/docs/harness/plan-mode.html) 文档原文确认，`plan_exit`（退出计划模式进入执行）**确实需要 HITL 确认**（"退出 Plan Mode 走 HITL 确认（复用权限系统的 ASK），避免模型一意孤行直接进入执行"）。ADR-006 此前推翻"计划需要独立审批"的依据只读了 `permission-system.html`，未读 `plan-mode.html` 本身，导致"官方没有'计划本身要不要审'这层概念"这一判断依据不完整——官方有这层概念，只是承载方式是运行时 `plan_exit` 触发的 HITL，不是独立状态机审批表。这不必然代表推翻结论本身错误（AAF 两次独立 execution 的架构与官方"同次 call 内切阶段"不同，审批必要性可能确实不同），但论证依据需要人类决定是否要求补正 ADR-006，本次未做修改。
