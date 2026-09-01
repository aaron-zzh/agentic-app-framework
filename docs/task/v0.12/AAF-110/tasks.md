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

- **状态**：[ ] 待开始
- **负责人**：architect
- **依赖**：无
- **范围**：
  - 确认触发路径分类表（已在真理源文档给出：正常完成/失败/`stop`/`PAUSE`/`takeOver`/进程重启共 6 类）是否覆盖全部实际触发点，补齐真理源文档遗漏的路径。
  - 确定 `AgentExecutionCommand` 新增字段的命名与类型（例如 `retainStateOnTermination: boolean`），及其在 record 规范构造器/现有唯一构造点（`AssistantApplicationService`）的赋值来源——由 `command.operation()`（`CANCEL`/`PAUSE`/`TAKE_OVER`）或 `DelegatedTaskCoordinator` 对应的 `stop`/`takeOver` 场景决定取值。
  - **"进程重启/连接中断"一行应直接接入 core 现成机制，不新增字段**：core `GracefulShutdownManager.bindStateSaver`/`registerRequest`/`bindRequestState` 提供完整的"优雅停机标记状态+下次加载识别"链路（`AgentState.shutdownInterrupted`），AAF 当前 `HarnessAgentExecutionAdapter` 从未调用这组 API，只用了 `close()`/`interrupt(ctx)`。本任务需设计如何接入，交由 #11004 落地。
  - 明确 `PAUSE` 场景下"不删状态槎"与既有 `RecoveryPoint` 机制的关系——`AssistantApplicationService` 中 `PAUSE` 分支已生成 `RecoveryPoint`，需确认 `RESUME` 时是否已经复用同一 `executionId`，还是需要额外改动才能触发续接。
- **完成标准**：设计方案经审阅后方可启动 #11002。

### #11002 `AgentExecutionCommand` 新增状态槎保留信号

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#11001
- **范围**：
  - 按 #11001 拍板的字段命名与类型，在 `AgentExecutionCommand` record 新增字段。
  - 唯一构造点 `AssistantApplicationService`（`new AgentExecutionCommand(...)`）按 `command.operation()` 正确赋值：`CANCEL`/正常终态 → 不保留；`PAUSE` → 保留。
  - 全仓核实 `DelegatedTaskCoordinator.cancelRunningChildren`（`stop`/`takeOver` 共用）是否也需要经由同一构造路径传递这个信号，或该路径本就不涉及重新构造 `AgentExecutionCommand`（`cancel` 是终止已有 execution，不是新建命令，需确认信号实际生效点）。
- **完成标准**：`compile` 通过；`PAUSE` 场景生成的命令携带保留信号为真，其余场景为假。

### #11003 `HarnessAgentExecutionAdapter.doFinally` 按保留信号分流状态槎处置

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#11002
- **范围**：
  - `deleteExecutionState` 调用前读取 `command` 携带的保留信号，为真时跳过删除。
  - 不改变 `stateUserKey` 命名空间构成（`executionId` 维度保留，继续防并发 TOCTOU），只改变"是否执行删除"这一步的判断条件。
  - 核实"责任主体变化"场景（`takeOver`）下即使旧 execution 未被此信号标记保留，仍按现有逻辑正确删除，不与本次改动冲突。
- **完成标准**：同责任主体的 `PAUSE`→`RESUME` 场景下状态槎不被误删；`stop`/`takeOver`/正常终态场景状态槎仍被正确删除；`compile` 通过。

### #11004 接入 core `GracefulShutdownManager` 生命周期（进程重启路径）

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#11001
- **范围**：
  - `HarnessAgentExecutionAdapter` 在 execution 建立时调用 `GracefulShutdownManager.getInstance().registerRequest(agent)` 取得 `requestId`，并在 per-call `AgentState` 槎位解析后调用 `bindRequestState(requestId, state)`——目前完全没有接入这两步。
  - 建立 `ShutdownStateSaver` 并通过 `bindStateSaver(agent, saver)` 注册，使优雅停机时状态被正确标记 `shutdownInterrupted=true` 并持久化，而不是被 AAF 自己的 `doFinally` 无差别删除。
  - execution 正常结束（无论真正终态还是 #11003 判定"应保留"的场景）时调用 `unregisterRequest(requestId)` 释放追踪，避免 `activeRequestsById` 无界增长。
  - 与 #11003 的分流逻辑对齐：`shutdownInterrupted=true` 的场景专属"进程级中断"，不与 `stop`/`PAUSE`/`takeOver` 的业务终止信号混用同一套判断分支，两条链路独立生效。
- **完成标准**：JVM 优雅停机场景下状态槎被 core 原生标记而非 AAF 误删；重启后同 `(userId, sessionId)` 加载可检测到 `shutdownInterrupted` 标记；`compile` 通过。

### #11005 验证与文档收口

- **状态**：[ ] 待开始
- **负责人**：developer-service
- **依赖**：#11003、#11004
- **范围**：
  - 人工核对 #11001～#11004 的改动与 `task-durability.md` 真理源文档描述的触发路径分类表逐一对应，无遗漏或偏离。
  - 更新 `docs/task/v0.12/AAF-107/tasks.md` #10704，确认其"计划步骤幂等接线"范围现在是否已具备可验证的前提（AAF-110 落地后，`executeApprovedPlanSteps` 恢复场景下复用同一 `executionId` 是否已天然生效）。
- **完成标准**：`compile` 通过；AAF-107 #10704 的依赖前提已明确记录满足或不满足。

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
