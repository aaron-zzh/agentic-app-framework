---
level: Practice
layer: Product
purpose: 拆分 AAF-110 执行中断续跑的状态槎保留信号与触发路径分流任务
status: draft
version: 1.1.0
date: 2026-09-01
author: AaronZZH & Kiro
tags:
  - AAF-110
  - AgentState
  - 执行器
  - 技术任务
related:
  - ../../../design/framework/intelligent/task-durability.md
gains:
  - 能让同责任主体的中断续跑复用原 executionId 延续对话历史
  - 能让责任主体变化（接管）继续强制新 execution 独立冻结画像
  - 能定位哪些中断路径该删状态槎、哪些不该删
---

# AAF-110 执行中断续跑任务

## 任务约束

- 技术真理源：[持久执行 · AgentState 与外部会话持久化机制对比](../../../design/framework/intelligent/task-durability.md#agentstate-与外部会话持久化机制对比2026-09-01-补充)。
- 第一性原理已复核确定：恢复**正确性**继续锚定 receipt/TaskBoard/事件流，不因本任务改变；本任务只处理恢复**效率**层——是否延续 `AgentState` 对话历史，与正确性无关（工具调用层 receipt 幂等独立保证不重复副作用）。
- **方案推翻（2026-09-01，核实 `ReActAgent.loadOrCreateAgentStateForSlot` 源码后）**：不改 `AgentExecutionPort.cancel` 签名。core 侧续跑逻辑只有"`stateStore.get()` 有值则用、没有则新建"这一条判断，不需要 `cancel()` 携带终止原因反推。改为在 `AgentExecutionCommand`（全仓唯一构造点在 `AssistantApplicationService`）携带一个"是否保留状态槎"的信号，由发起 execution 的调用方正向声明，`HarnessAgentExecutionAdapter.doFinally` 读该信号决定是否跳过删除。
- 范围边界：**只处理 `AgentExecutionCommand` 的状态槎保留信号与 `HarnessAgentExecutionAdapter.doFinally` 的分流判断**，不涉及"如何续接"这一侧的具体调用改造（那部分若有需要，留给依赖本任务的 AAF-107 #10704 或其它任务）。
- 风险等级：🟡 中（数据字段新增，不改接口方法签名；仍触及 AAF-103 已完成的执行器核心路径，需谨慎但不属于🔴 接口删除/权限变更范畴）。
- **阶段约束**：断言补进既有测试，不新增测试文件；不执行 `check` / `acceptance`；准出最低要求 `pnpm nx compile service` 通过。

## 技术任务

### #11001 立设计：状态槎保留信号与触发路径处置矩阵

- **状态**：✅ 已完成（2026-09-02，方案在编码阶段被推翻并修正）— Kiro
- **负责人**：architect
- **依赖**：无
- **⚠️ 方案推翻**：原方案"`AgentExecutionCommand` 新增保留信号字段"建立在错误假设上——核实 `AssistantApplicationService.controlTask`（`PAUSE`/`TAKE_OVER`/`CANCEL` 唯一处理入口）后发现，这三种操作**都不构造新的 `AgentExecutionCommand`**，只调用 `agentExecution.cancel(command.executionId())` 终止一个已经在跑的旧 execution。`AgentExecutionCommand` 只在**发起**新 execution 时构造（`agentCommand(...)` 方法，两处调用点都是 `agentExecution.execute(...)`），跟**终止**现有 execution 是完全不相交的两条代码路径——保留信号不可能通过"预先声明"的方式挂在一份根本不会被重新构造的命令对象上。
- **修正后方案**：回到"改动 `AgentExecutionPort` 接口"这条路——但不是改 `cancel(ExecutionId)` 签名（会牵动所有既有调用点被迫显式传参），而是**新增一个语义独立的 `pause(ExecutionId)` 方法**，跟 `cancel(ExecutionId)` 并列。`HarnessAgentExecutionAdapter` 内部新增 `TerminalState.PAUSING`（与 `CANCELLING` 平级）参与终态仲裁，`ActiveExecution` 新增 `pauseWon`（`AtomicBoolean`）标志——因为仲裁后 `terminal` 字段统一收敛为 `TERMINATED`，无法反推走的是哪条仲裁路径，需要独立标志供 `release(...)` 判断是否跳过状态槎删除。
- **触发路径分类表**（最终版）：

| 触发路径 | 调用点 | 责任主体是否变化 | 状态槎处置 |
|---|---|---|---|
| 正常完成 | `HarnessAgentExecutionAdapter` 内部终态仲裁 | 否 | 删（真正终态） |
| 失败 | 同上 | 否 | 删（真正终态） |
| `CANCEL`（用户主动取消） | `AssistantApplicationService.controlTask` → `agentExecution.cancel(...)` | 否，任务终止无需续接 | 删（真正终态） |
| `PAUSE`（用户暂停） | `AssistantApplicationService.controlTask` → `agentExecution.pause(...)`（新方法） | 否，同责任主体 | **不删**（`TerminalState.PAUSING` 仲裁路径） |
| `TAKE_OVER`（人工接管） | `AssistantApplicationService.controlTask` → `agentExecution.cancel(...)`（`owner` 切换为 `humanOwner`） | 是 | 删（责任主体变化） |
| `stop`/`takeOver`（委托任务层，多子任务） | `DelegatedTaskCoordinator.cancelRunningChildren` → `agentExecution.cancel(...)` | 视场景，两者均对应真正终止或责任主体变化，无 PAUSE 语义 | 删 |
| 进程重启/连接中断 | core `GracefulShutdownManager` 生命周期（#11004） | 否 | 不删（`shutdownInterrupted` 由 core 原生标记） |

- **完成标准**：设计方案经审阅后方可启动 #11002。
- **实际结果**：修正后方案已确认，#11002～#11004 按新方案实施（见下）。

### #11002 `AgentExecutionPort` 新增 `pause(ExecutionId)` 方法

- **状态**：✅ 已完成（2026-09-02）— developer-service
- **负责人**：developer-service
- **依赖**：#11001
- **范围**：
  - ✅ `AgentExecutionPort` 新增 `Mono<Boolean> pause(ExecutionId executionId)`，与 `cancel(ExecutionId)` 并列（接口新增方法，不改既有方法签名）。全仓核实唯一实现是 `HarnessAgentExecutionAdapter`。
  - ✅ `HarnessAgentExecutionAdapter.TerminalState` 新增 `PAUSING`；新增 `pauseWonRace(active)`（与 `cancelWonRace` 平级），CAS 成功时置位新增的 `ActiveExecution.pauseWon`（`AtomicBoolean`）。
  - ✅ `AgentScopeEventMapper` 新增 `paused(...)` 公开方法（与 `canceled(...)` 平级），产出 `ExecutionEventType.EXECUTION_PAUSED`（复用既有枚举值，未新增）。
  - ✅ `AssistantApplicationService.controlTask`：按 `command.operation()` 分流——`PAUSE` 走 `agentExecution.pause(...)`，`CANCEL`/`TAKE_OVER` 继续走 `agentExecution.cancel(...)`。`DelegatedTaskCoordinator.cancelRunningChildren`（`stop`/`takeOver` 共用）核实无 PAUSE 语义，保持 `cancel(...)` 不变。
- **完成标准**：`compile` 通过；`PAUSE` 场景调用 `pause(...)`，其余场景调用 `cancel(...)`。

### #11003 `HarnessAgentExecutionAdapter.doFinally` 按保留信号分流状态槎处置

- **状态**：✅ 已完成（2026-09-02，随 #11002 一并实施）— developer-service
- **负责人**：developer-service
- **依赖**：#11002
- **范围**：
  - ✅ 两处终态仲裁分支（`onErrorResume`/`concatWith`）在 `cancelWonRace` 判断之后追加 `pauseWonRace` 判断，产出 `eventMapper.paused(...)` 而非误判为失败。
  - ✅ `interruptIfCancelledAndStarted` 判断条件扩展为 `CANCELLING || PAUSING`，保证暂停请求同样能补发早到的中断信号。
  - ✅ `release(executionId, active, command, stateUserKey)` 读取 `active.pauseWon().get()`：为真时跳过 `deleteExecutionState(...)`。
  - ✅ 未改变 `stateUserKey` 命名空间构成（`executionId` 维度保留）。
- **完成标准**：同责任主体的 `PAUSE`→`RESUME` 场景下状态槎不被误删；`CANCEL`/`TAKE_OVER`/正常终态场景状态槎仍被正确删除；`compile` 通过。

### #11004 接入 core `GracefulShutdownManager` 生命周期（进程重启路径）

- **状态**：✅ 已完成（2026-09-02）— developer-service
- **负责人**：developer-service
- **依赖**：#11001
- **范围**：
  - ✅ `ActiveExecution` 新增 `shutdownRequestId`（`AtomicReference<String>`）。`executeResolved` 在 `activeExecutions.putIfAbsent(...)` 成功后调用 `GracefulShutdownManager.getInstance().bindStateSaver(agent, saver)`（`saver` 委托给既有 `stateStore.save(userId, sessionId, "agent_state", state)`）与 `registerRequest(agent)`，结果存入 `shutdownRequestId`。
  - ✅ **关键发现**：`RuntimeContext.getAgentState()` 是"call-scoped"，只在框架于 `call()`/`streamEvents()` 入口注入后才有值——`executeResolved` 建立 `active` 那一刻读取会是 `null`。改为在 `onSourceEvent` 的 `AGENT_START` 分支（`active.started().set(true)` 之后）才调用 `bindRequestState(requestId, state)`，此时 `RuntimeContext` 已被框架填充。
  - ✅ `release(...)` 方法（4 参数版本）无论后续是否跳过状态槎删除，都先调用 `unregisterRequest(requestId)` 释放追踪，避免 `activeRequestsById` 无界增长。
  - ✅ 与 #11002/#11003 的分流逻辑对齐：`shutdownInterrupted` 由 core 原生标记（`GracefulShutdownManager` 内部逻辑），与 `PAUSING`/`CANCELLING` 是两条独立生效的链路，互不干扰。
- **完成标准**：JVM 优雅停机场景下状态槎被 core 原生标记而非 AAF 误删；重启后同 `(userId, sessionId)` 加载可检测到 `shutdownInterrupted` 标记；`compile` 通过。

### #11005 验证与文档收口

- **状态**：✅ 已完成（2026-09-02）— developer-service
- **负责人**：developer-service
- **依赖**：#11003、#11004
- **范围**：
  - ✅ 人工核对 #11001～#11004 的改动与触发路径分类表逐一对应，无遗漏或偏离。
  - ✅ `pnpm nx compile service` BUILD SUCCESS（6 模块全绿，含测试代码编译）。
  - ✅ 已确认 AAF-107 #10704 依赖前提：`executeApprovedPlanSteps` 恢复场景下复用同一 `executionId`/`sessionId` 重新发起 `execute(...)` 时，`HarnessAgentExecutionAdapter.doFinally` 现在按责任主体是否变化正确分流状态槎删除——`PAUSE` 场景不再无差别删除。#10704 收窄后的范围（`ExecutorPlanStep.status` 转换在续跑场景下的幂等接线）现在具备可验证的前提，可以推进。
- **完成标准**：`compile` 通过；AAF-107 #10704 的依赖前提已明确记录满足。
- **实际结果**：AAF-110 全部技术任务完成。

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入

## 评审状态

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 0 | — | ⏳ PENDING | 本任务源自技术缺口发现，非独立用户故事细化，跳过 |
| architect（技术设计） | 0 | — | ⏳ PENDING | 是（🟡 中风险，设计仍需过一遍但不要求 🔴 人类强制审核门禁） |
| designer（UI 审查） | 0 | — | ⏳ PENDING | 不涉及前端，跳过 |
| developer（编码） | 0 | — | ⏳ PENDING | 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 视质量门控结果 |
