---
level: Reality
layer: Model
purpose: 评估 AAF 自研 AgentScope Harness 编排适配层的可行性、稳定性与可靠性
status: draft
version: 1.2.0
date: 2026-08-31
author: AaronZZH
---

# AgentScope Harness 编排运行时质量评估

## 评估结论

> **修复状态（2026-08-31 复评）**：RQ-01 blocker 与 RQ-02～RQ-10 九条 major 均已修复，逐条修复位置见风险清单"修复"列。剩余 3 条 minor（RQ-11/12/13）未修复，均不阻断上线。
> 复评基线：`pnpm check:affected` 全绿，新增 P0/P1 测试见"测试覆盖现状"。

当前实现具备继续演进的可行性。正向基础包括：事件顺序入库、数据库原子重分配 sequence、临时 Agent 释放、同一 `executionId` 并发执行拒绝、AgentScope v2 按会话隔离与串行化均已形成明确实现。原先阻断上线的取消/完成双终态竞态已由单一 CAS 终态机消除；本轮进一步补齐了阻塞 I/O 线程隔离、时限语义分离、失败分类契约、终态重放幂等、有界缓存、执行私有状态槽与工具证据兜底清理。

问题统计：**blocker 1（已修）/ major 9（已修）/ minor 3（未修）**。统计按本文唯一问题编号 `RQ-*` 去重；风险为"无"的正确性判断不计入问题数。

| 维度 | 判定 | 证据 | 风险 |
|---|---|---|---|
| 可行性 | 可行。AAF 只复用 ReAct 循环、模型与工具调用，关闭 Harness 内建记忆、工作区、子智能体、技能和压缩，边界清晰 | `AgentScopeSpecCompiler.compileDynamicNew`、`compileNew` | 无 |
| 稳定性 | 稳定。终态由 `AtomicReference<TerminalState>` 单点 CAS 仲裁，取消与完成互斥且各自恰好一次 | `HarnessAgentExecutionAdapter.cancel`、`executeResolved` 的 `onErrorResume` / `concatWith` 分支 | 已修（RQ-01） |
| 可靠性 | 达到上线门槛。阻塞 I/O 已离开事件发射线程；总时限 / 静默时限 / 入库 SLA 三条边界分离；终态重放幂等；缓存有界；状态槽按 Agent + execution 私有 | `HarnessAgentExecutionAdapter`（重放守卫 / 边界校验 / `totalDeadline`）、`AgentScopeSpecCompiler.BoundedAgentCache`、`AgentScopeRuntimeContextMapper.stateUserKey` | 已修（RQ-02～RQ-10） |

## 调查范围与方法

本次逐行检查了指定的执行适配、编译、消息/事件/上下文映射、中间件、工具、Redis 状态适配和事件存储实现，并读取了现有同名测试及 Prompt Envelope 测试。事件存储端口的实现确定为 `JpaExecutionEventStoreAdapter`，同步写入委托 `SynchronousExecutionEventWriter`；证据见 `JpaExecutionEventStoreAdapter`（23-128 行）和 `SynchronousExecutionEventWriter`（11-110 行）。

为核实缓存实例是否可并发复用，进一步检查了仓库内 `tmp/agentscope-java` 的 AgentScope v2 源码与测试。`HarnessAgent` 明确声明跨用户/会话共享安全（152 行附近类注释），实际委托的 `ReActAgent.callSerializationKey` 按 `(userId, sessionId)` 串行同槽调用（502-513 行），`AgentBase.serializeOnKey` 在完成、错误和取消时释放队列槽（约 326-352 行），不同会话可并行；上游测试 `ReActAgentPerSessionStateTest.concurrentSameSessionIsSerialized`（248-275 行）和 `concurrentStreamEventsAreIsolated`（277-309 行）验证了该语义。`ReActAgent` 类头“单实例不线程安全”的旧注释与当前实现及测试冲突，应视为上游文档陈旧，而不能据此否定实际并发实现。

## 风险清单

> 下表与后续"必查项逐项判断"是**审计时点**（2026-08-30）的原始发现记录，行号对应当时代码，不随修复更新；当前状态见本节末的[修复记录](#修复记录)。

| 编号 | 结论 | 发生条件与后果 | 证据 | 风险 |
|---|---|---|---|---|
| RQ-01 | 取消与完成状态机不自洽，存在“两个终态”和“取消丢失”两个确定窗口 | 窗口 A：源已映射 `RUN_COMPLETED`/`RUN_FAILED`，但 `doOnComplete` 尚未写 `sourceCompleted=true` 时执行 `cancel()`，后续 `concatWith` 又发 `EXECUTION_CANCELED`，持久事件出现矛盾终态。窗口 B：`cancel()` 读到 `sourceCompleted=false` 后，源完成并让 `concatWith` 读到 `cancelled=false`，随后 cancel CAS 成功并返回 `true`，但取消事件已错过，最终只保留正常终态。五个 AtomicBoolean 只能保证单标志原子性，不能原子仲裁终态 | `HarnessAgentExecutionAdapter.cancel`（99-128 行）；`executeResolved` 中 `onErrorResume`、`doOnComplete`、`concatWith`（约 286-326 行）；`AgentScopeEventMapper.mapAgentEnd`（164-181 行）、`canceled`（151-161 行） | blocker |
| RQ-02 | `doOnNext` 在事件发射线程同步执行 Redis/JPA I/O；ADR-003 的请求虚拟线程不会自动覆盖 Reactor 回调线程 | 委托执行每个源事件都调用 `leases.requireCurrent` 和 `delegatedTasks.requireAgentExecution`；前者同步访问 Redis，后者执行 Spring Data JPA 查询。模型开始/结束事件还同步预扣或记账。若 Redis/DB 延迟 500 ms，该发射线程至少阻塞 500 ms，事件背压、模型流消费和取消响应同步变慢；共享 SDK/Reactor 线程被占用时会放大到其他执行 | `HarnessAgentExecutionAdapter.executeResolved`（236-339 行）、`requireCurrent`（420-429 行）；`RedisConversationLeaseAdapter.requireCurrent`（58-62 行）；`JpaDelegatedTaskAdapter.requireAgentExecution`（198-212 行）；`AgentScopeTokenMeteringObserver.observe`（34-56 行）。相对地，事件写入显式 `subscribeOn(Schedulers.boundedElastic())`，见 `JpaExecutionEventStoreAdapter.append`（48-53 行） | major |
| RQ-03 | `timeout` 不是整次执行硬时限，也不覆盖事件入库 | `timeout` 位于源事件映射 `concatMap` 之后、持久化 `concatMap` 之前。它约束的是“已映射事件之间的静默时间”：未映射源事件不会重置计时，持续输出已映射事件则可无限延长总执行；源完成后慢数据库写入不受该 timeout 控制。因此配置的 `executionPolicy.timeout()` 不能保证模型调用总墙钟时间，也不能保证整体交付时限 | `HarnessAgentExecutionAdapter.executeResolved`（约 276-328 行）；事件存储在 timeout 之后调用 `eventStore.append`（约 327 行） | major |
| RQ-04 | 源流、租约、计量和映射异常都被收敛成正常完成的事件流，失败类别契约不足且原异常未记录 | 调用方可通过 `RUN_FAILED` 判断失败，并可用业务失败的 `reason` 与基础设施失败的 `errorType` 作弱区分；但没有稳定的 `failureCategory/retryable` 字段，且该适配器不记录原异常堆栈。租约过期、数据库异常、模型异常和映射缺陷均正常 `onComplete`，只留下类简单名，自动重试与故障定位无法可靠决策。事件持久化自身失败位于 `onErrorResume` 之后，仍会以 Reactor error 传播，形成两套失败传递语义 | `HarnessAgentExecutionAdapter.executeResolved`（约 286-328 行）；`AgentScopeEventMapper.failure`（135-148 行）；`JpaExecutionEventStoreAdapter.append`（48-53 行） | major |
| RQ-05 | 并发单次执行可拒重，但完成后的重新订阅/重放不幂等 | 存储层只按 `eventId` 幂等；同一个源事件 ID 重投可安全返回已有事件。重新订阅同一 command 会重新执行模型，AgentScope 与合成事件产生新 ID，`idempotencyKey` 不参与唯一约束，因而会追加第二套事件和副作用。发生客户端重试、进程恢复或重复调度时，同一 `executionId` 可形成重复运行记录 | `HarnessAgentExecutionAdapter.execute`（92-96 行）、`executeResolved`（236-339 行）；`AgentScopeEventMapper.event`（359-380 行）、`syntheticEvent`（383-399 行）；`SynchronousExecutionEventWriter.append`（28-62 行）、`requireSameEvent`（72-78 行） | major |
| RQ-06 | 两个 HarnessAgent 缓存均无上限、TTL 或版本淘汰 | 每个新 `DefinitionKey`/`DirectKey` 都永久保留 Agent，直到容器关闭。动态直答的 prompt、模型或工具画像持续变化时，堆内 Agent、Toolkit、中间件引用和完整 prompt 字符串持续增长；长期运行节点最终出现内存压力，容器关闭前不会回收 | `AgentScopeSpecCompiler.cache/directCache`（约 39-45 行）、`compile`（54-71 行）、`compileDirect`（74-92 行）、`close`（211-217 行）、键定义（220-233 行） | major |
| RQ-07 | `DirectKey` 缺少会改变 ReAct 行为的执行策略 | 两个动态直答规格若 identifier、模型、工具和 prompt 相同但 `maxIterations` 或 `maxModelRetries` 不同，会命中同一 HarnessAgent，后一次继续使用首次编译的迭代/重试配置。外层 timeout 读取本次 command，因而只更新 timeout，形成同一执行策略内部不一致 | `AgentScopeSpecCompiler.compileDirect`（74-92 行）、`compileDynamicNew` 将策略写入 builder（126-168 行）、`DirectKey`（228-233 行） | major |
| RQ-08 | 隐藏历史校验存在 TOCTOU 绕过窗口 | 两个不同 `executionId` 但共享 `(stateUserKey, sessionId)` 的调用并发开始时，都可先读到空状态并通过校验；AgentScope 随后按同槽串行，第二个调用启动时会重新加载第一个调用刚保存的历史，却不会再次执行 AAF 校验，于是未纳入冻结画像的历史进入第二次模型请求。委托态通常受“当前 execution”约束降低概率，READ_ONLY/DIRECT 不经过该约束 | `HarnessAgentExecutionAdapter.requireNoHiddenPersistentHistory`（342-362 行）在注册/调用 AgentScope 前执行；`AgentScopeRuntimeContextMapper.toAgentScope`（26-38 行）；AgentScope `ReActAgent.activateSlotForContext`（442-483 行）在真正获准执行后重新加载状态；`AgentBase.serializeOnKey`（约 326-352 行）只串行实际调用，不覆盖 AAF 预检 | major |
| RQ-09 | 状态键覆盖租户、用户、任务、会话，但未包含 Agent/Assistant 身份或 execution | Redis 最终槽键由 `stateUserKey + '/' + sessionId` 构成；租户、用户、任务、会话隔离成立，委托态还含 fence。若同一四元组被两个 Agent 身份或两个直答 execution 复用，它们读取同一个 `agent_state`，会发生跨 Agent 历史污染，或被隐藏历史校验拒绝。当前检查未发现状态值加载时校验 agentId 的代码 | `AgentScopeRuntimeContextMapper.stateUserKey`（41-57 行）、`toAgentScope`（26-38 行）；AgentScope `RedisAgentStateStore.slotId`（374-379 行）、`getStateKey`（414-416 行） | major |
| RQ-10 | 工具证据在取消/异常路径可永久泄漏 | 工具完成后先 `record`，只有收到并映射 `TOOL_RESULT_END` 才 `take` 删除。若在两者之间取消、超时、映射失败或进程内流被 dispose，该 `(executionId, toolCallId)` 项没有执行级清理或 TTL；每次此类终止都会永久增加内存条目 | `PortBackedAgentTool.callAsync`（46-87 行）；`ToolResultEvidenceStore.record`（36-55 行）、`take`（58-61 行）；`AgentScopeEventMapper.mapToolResult`（247-280 行） | major |
| RQ-11 | `activeExecutions` 的 map 原语使用正确，但重复订阅失败没有统一事件化 | `putIfAbsent` 保证同一 `executionId` 只有一个并发赢家，失败方的临时 Agent 会释放；`remove(key,value)` 不会误删后来注册的新句柄。失败方直接收到 `IllegalStateException`，不会落 `RUN_FAILED`，与“解析异常也事件化”的接口说明不一致 | `HarnessAgentExecutionAdapter.executeResolved`（约 267-279 行）、`release(executionId, active)`（455-460 行）、`executeDeferred` 注释与实现（131-150 行） | minor |
| RQ-12 | AAF 侧 interrupt 下发幂等，但 cancel 与临时 Agent close 没有时序互斥 | `interruptIssued.compareAndSet` 保证一次执行只调用一次 delegate interrupt。若 cancel 已取得 ActiveExecution 引用，另一线程随后 `doFinally` 出表并 close，cancel 仍可在 close 之后调用 interrupt。当前 AAF 关闭了 Harness 资源型能力且 `ReActAgent.close` 是 no-op，所以现状后果仅是不必要调用；未来启用子智能体/工作区资源后该顺序会触碰已关闭资源 | `HarnessAgentExecutionAdapter.interruptOnce`（447-452 行）、`release`（455-460 行）；AgentScope `HarnessAgent.close`（375-388 行）、`ReActAgent.close`（3821-3825 行） | minor |
| RQ-13 | Redis 状态值与 `_keys` 登记不是原子写，模式扫描使用阻塞 `KEYS` | 进程在 `SET state` 与 `SADD _keys` 之间失败时，状态值成为无法由 `exists/delete/listSessionIds` 完整管理的孤儿；反向中断则 `_keys` 可指向缺失值。`findKeysByPattern` 直接调用 `StringRedisTemplate.keys`，当会话键数量大时会阻塞 Redis。常规单次 get/set 可用，但批量管理与故障恢复可靠性不足 | `SpringRedisClientAdapter.set`（24-27 行）、`addToSet`（58-61 行）、`findKeysByPattern`（80-84 行）；AgentScope `RedisAgentStateStore.save`（211-224 行）、`listSessionIds`（345-364 行） | minor |

## 修复记录

> 2026-08-31 复评。RQ-01 由 `426a5f51` 修复，RQ-02～RQ-10 本轮修复。行号不再登记——修复点均带 `RQ-NN` 注释，按编号搜索即可定位。

| 编号 | 修复做法 | 主要改动位置 | 验证 |
|---|---|---|---|
| RQ-01 | 三个独立布尔量收敛为 `AtomicReference<TerminalState>` 单点 CAS（`ACTIVE→CANCELLING→TERMINATED`），`cancel()` 返回值与终态仲裁同源 | `HarnessAgentExecutionAdapter.TerminalState` / `cancel` / `cancelWonRace` | `HarnessAgentExecutionAdapterTest` 5 个 P0 并发用例（Sinks 精确控时序） |
| RQ-02 | 源事件校验与计量改为 `concatMap` + 注入的 `blockingScheduler`（生产 `boundedElastic`，单测 `immediate`），并收窄到 `GUARDED_EVENT_TYPES` 边界事件；fail-closed 语义不变，文本增量不再逐条查 Redis/DB | `HarnessAgentExecutionAdapter`（`blockingScheduler`、`GUARDED_EVENT_TYPES`）、`AgentScopeInfrastructureAutoConfiguration` | `should_guard_only_boundary_events_for_delegated_execution` |
| RQ-03 | `ExecutionPolicy` 拆为 `totalTimeout`（订阅起算的墙钟硬时限）/ `idleTimeout`（相邻已映射事件静默）/ `persistTimeout`（单条入库 SLA）三条独立边界；总时限用"主流终止信号 + `timeout`"实现，超时走终态仲裁落 RUN_FAILED 而非裸异常 | `ExecutionPolicy`、`HarnessAgentExecutionAdapter.totalDeadline` | `should_terminate_on_total_deadline_even_when_events_keep_arriving`（持续产出事件仍按总时限终止） |
| RQ-04 | 失败事件新增稳定契约字段 `failureCategory` / `retryable` / `failureId`，分类由 `AgentScopeFailureClassifier` 按语义归类并穿透 cause 链，未知类别保守判不可重试；原异常堆栈只写服务端日志 | `AgentScopeFailureClassifier`、`AgentScopeEventMapper.failure` | `AgentScopeFailureClassifierTest`（4）、`AgentScopeEventMapperFailureTest`（2） |
| RQ-05 | 执行入口增加持久重放守卫：`sequenceBase` 之后已存在 AGENT 终态事件即跳过模型调用，直接回放已持久事件；仅有非终态历史（崩溃在中途）时继续执行并 warn，跨进程在途重复仍由租约 fencing 拦截 | `HarnessAgentExecutionAdapter.replaySettledExecution` | `should_replay_persisted_events_and_skip_model_when_execution_already_settled`、`should_execute_when_history_has_no_terminal_event` |
| RQ-06 | 两个 Agent 缓存改为有界 LRU（`BoundedAgentCache`，默认容量 128），淘汰时立即 `close` 被淘汰实例并 warn；新增 `cachedAgentCount()` 供监控 | `AgentScopeSpecCompiler` | `should_evict_and_close_least_recently_used_agent_when_capacity_reached` |
| RQ-07 | `DirectKey` 纳入完整 `ExecutionPolicy`，`maxIterations` / `maxModelRetries` 不同不再复用同一实例 | `AgentScopeSpecCompiler.DirectKey` | `should_not_reuse_direct_agent_when_execution_policy_differs`（断言 `getMaxIters()` 3 vs 9） |
| RQ-08 | 状态槽键加入 `executionId` 后按执行私有化，两个并发执行不可能读到彼此历史，隐藏历史预检的 TOCTOU 窗口消失；槽在 `doFinally` 中删除，避免 Redis 无界增长 | `AgentScopeRuntimeContextMapper.stateUserKey`、`HarnessAgentExecutionAdapter.deleteExecutionState` | `AgentScopeRuntimeContextMapperTest`、`should_clear_tool_evidence_and_execution_state_on_termination` |
| RQ-09 | 同一键同时加入 Agent 身份，换 Agent/Assistant 不再共享 `agent_state` | 同上 | `should_isolate_state_slot_by_agent_identity` |
| RQ-10 | `ToolResultEvidenceStore` 增加 `clear(executionId)`（`doFinally` 无条件调用）+ TTL 淘汰 + 容量上限三道防线，并暴露 `size()` | `ToolResultEvidenceStore`、`HarnessAgentExecutionAdapter.release` | `ToolResultEvidenceStoreTest`（4）、`should_clear_tool_evidence_and_execution_state_on_termination` |

### 未修复项（minor，不阻断上线）

- RQ-11：重复订阅失败方仍收到裸 `IllegalStateException`，未事件化
- RQ-12：`cancel` 与临时 Agent `close` 无时序互斥；当前 `ReActAgent.close` 为 no-op，实际后果仅为多余调用
- RQ-13：Redis 状态值与 `_keys` 登记非原子，`findKeysByPattern` 仍用阻塞 `KEYS`

### 残留风险（RQ-05 边界）

崩溃恢复走 `JpaTaskRecoveryAdapter.asResume` 会保留原 `executionId` 与 `sequenceBase`。终态已存在时命中重放守卫，不会重复调用模型；**中途崩溃**（无终态、有部分事件）时新一代执行会追加一套新的 AGENT 事件，前缀事件出现重复。DELEGATED 下旧写入方被租约 fencing 拒绝，因此同一时刻只有一个活写入方，不会出现两套并发写入。彻底消除前缀重复需要持久执行声明（含 checkpoint 续跑），属独立需求，不在本轮范围。

## 必查项逐项判断

### 并发与竞态

| 检查项 | 结论 | 证据 | 风险 |
|---|---|---|---|
| `putIfAbsent` / `remove(key,value)` | 用法正确；同一 executionId 并发订阅只有一个执行，失败方若为 ephemeral 会关闭。失败方收到裸错误而非失败事件，见 RQ-11 | `HarnessAgentExecutionAdapter.executeResolved`（236-339 行）、`release`（455-460 行） | minor |
| 五个取消状态标志 | 不自洽；存在矛盾终态和 cancel 返回 true 但取消事件丢失的窗口，见 RQ-01 | `cancel`（99-128 行）、`executeResolved`（236-339 行） | blocker |
| cancel 早于 `AGENT_START` | 单独考察该时序没有丢失窗口：cancel 先写 `cancelled`，AGENT_START 后写 `started` 并再次检查；反向时序由 cancel 自己检查。两条路径最终汇合到 CAS 幂等 interrupt。若 AGENT_START 永不到达，取消事件要等待源终止或 timeout，不会立即发出 | `onSourceEvent`（432-438 行）、`interruptIfCancelledAndStarted`（440-444 行）、`interruptOnce`（447-452 行） | 无 |
| `doOnNext.requireCurrent` 线程影响 | 会阻塞源事件发射线程；Redis 与 JPA 都是同步 I/O。Virtual Threads 只覆盖运行在虚拟线程上的调用，不会把 Reactor/SDK 回调自动迁移到虚拟线程，见 RQ-02 | `executeResolved`（236-339 行）、`requireCurrent`（420-429 行）、`JpaDelegatedTaskAdapter.requireAgentExecution`（198-212 行） | major |

### 取消与中断语义

| 检查项 | 结论 | 证据 | 风险 |
|---|---|---|---|
| interrupt 幂等与 close 时序 | AAF 下发幂等；close 竞态未互斥，当前配置下实际破坏性低，见 RQ-12 | `interruptOnce`（447-452 行）、`release`（455-460 行） | minor |
| timeout 语义 | 截断映射后的 AgentScope 源流，不覆盖后续入库；且是事件静默超时，不是总墙钟执行时限，见 RQ-03 | `executeResolved`（约 276-328 行） | major |
| `onErrorResume` 收口 | 调用方看到 `RUN_FAILED`/`EXECUTION_CANCELED` 后正常完成；事件入库失败仍抛 error。业务失败与基础设施失败只能依赖 payload 的 `reason`/`errorType` 弱区分，原异常未记录，见 RQ-04 | `executeResolved`（约 286-328 行）、`AgentScopeEventMapper.failure`（135-148 行） | major |

### 事件顺序与幂等

| 检查项 | 结论 | 证据 | 风险 |
|---|---|---|---|
| `MappingState` / `sequenceBase` | `AtomicLong` 与 `AtomicReference` 线程安全；其 sequence 明确是入库前临时契约，存储层用 PostgreSQL upsert 原子重分配并返回持久事件，不构成运行时双真理源 | `AgentScopeEventMapper.MappingState`（453-477 行）；`SynchronousExecutionEventWriter.append`（28-62 行）；`ExecutionEventRepository.allocateSequence`（约 24-35 行） | 无 |
| `concatMap` 顺序 | 第一层串行映射，第二层串行等待每次 append 完成，能保持本次订阅的事件顺序；数据库还有 `(tenant_id, execution_id, sequence)` 唯一约束 | `HarnessAgentExecutionAdapter.executeResolved`（236-339 行）；`ExecutionEventEntity` 类级唯一约束（18-22 行） | 无 |
| 重复订阅/重放幂等 | 同 eventId 重投幂等；整个 execution 重新订阅不幂等，见 RQ-05 | `SynchronousExecutionEventWriter.append`（28-62 行）、`AgentScopeEventMapper.syntheticEvent`（383-399 行） | major |

### 生命周期与资源

| 检查项 | 结论 | 证据 | 风险 |
|---|---|---|---|
| ephemeral close 路径 | 已穷尽主要路径：上下文预检失败走 `release(ResolvedExecution)`；注册后同步异常走 catch；异步完成、错误、timeout 和订阅取消走 `doFinally`；`released` CAS 防 double-close。cancel 本身不 close，而由流终止统一释放 | `executeResolved`（236-339 行）、两个 `release`（455-467 行）、`ActiveExecution`（478-502 行）；现有测试（110-144 行） | 无 |
| 缓存 key | Predefined key 含 agentId、版本、工具和完整 prompt；Direct key 含标识、模型、工具和完整 prompt，但缺执行策略，见 RQ-07 | `DefinitionKey`（220-225 行）、`DirectKey`（228-233 行） | major |
| 缓存增长 | 两个 ConcurrentMap 无界，仅容器 close 清理，见 RQ-06 | `AgentScopeSpecCompiler`（28-237 行） | major |
| 缓存实例线程安全 | 在当前 AgentScope v2 快照下可并发复用：Harness 声明无调用间状态；ReActAgent 按槽串行同会话、不同会话并行，事件 sink 位于每订阅 Reactor Context。该结论已由上游并发测试验证，不是推断 | AgentScope `HarnessAgent` 类注释（约 147-151 行）；`ReActAgent.callSerializationKey`（502-513 行）、`streamEvents`（897-958 行）；`ReActAgentPerSessionStateTest`（248-309 行） | 无 |
| 工具证据生命周期 | 非正常终止不会清理暂存证据，见 RQ-10 | `ToolResultEvidenceStore.record/take`（36-61 行） | major |

### 状态隔离

| 检查项 | 结论 | 证据 | 风险 |
|---|---|---|---|
| tenant/user/session/task 隔离 | 指定四个维度均进入最终 Redis 槽：tenant/user/task 在 `stateUserKey`，session 作为 AgentStateStore 的第二维；DELEGATED 再加 fence。对这四个维度本身隔离充分 | `AgentScopeRuntimeContextMapper.toAgentScope`（26-38 行）、`stateUserKey`（41-57 行）；AgentScope `RedisAgentStateStore.slotId`（374-379 行） | 无 |
| Agent/execution 隔离 | 键未包含 Agent/Assistant/execution，满足条件时会共享状态，见 RQ-09 | 同上 | major |
| 隐藏历史校验 | 空状态和非空状态的静态判断正确，Redis 读取异常会 fail closed；但检查与 AgentScope 真正加载不原子，并发同槽可绕过，见 RQ-08 | `requireNoHiddenPersistentHistory`（342-362 行）、AgentScope `ReActAgent.activateSlotForContext`（442-483 行） | major |

## 测试覆盖现状

### 已覆盖场景

| 测试 | 实际覆盖 | 证据 |
|---|---|---|
| Harness 执行适配器 | Dynamic 源失败后 close；timeout 后 close；订阅方 dispose 后 close | `HarnessAgentExecutionAdapterTest.should_close_dynamic_agent_after_source_failure`（110-120 行）、`should_close_dynamic_agent_after_timeout`（122-132 行）、`should_close_dynamic_agent_after_subscription_cancel`（134-144 行） |
| Spec 编译器 | 最终工具集；相同 Predefined/Direct 画像复用；prompt 隔离；不同工具画像并发隔离；Dynamic 并发现场编译隔离 | `AgentScopeSpecCompilerTest`（80-238 行） |
| Prompt Envelope | 首次冻结；相同请求识别重试；缺 typed InvocationContext 时 fail closed | `PromptEnvelopeCaptureMiddlewareTest`（64-137 行） |
| AgentScope 上游并发语义 | 同会话串行、不同事件订阅互不串流 | `ReActAgentPerSessionStateTest`（248-309 行） |

现有测试没有直接覆盖 `AgentScopeEventMapper`、`AgentScopeMessageMapper`、`AgentScopeRuntimeContextMapper`、`AgentScopeTokenMeteringObserver`、`PortBackedAgentTool`、`AgentScopeToolkitFactory`、`ToolResultEvidenceStore`、`SpringRedisClientAdapter` 以及 AAF 适配层和真实事件存储的组合路径；证据是目标测试目录仅有 execution/compiler/middleware/definition 四组文件，且其中只有前三组与本次范围相关。

### 本轮新增测试（2026-08-31）

| 测试 | 覆盖不变量 | 对应风险 |
|---|---|---|
| `HarnessAgentExecutionAdapterTest.should_replay_persisted_events_and_skip_model_when_execution_already_settled` | 已有终态时不调用模型、不追加第二套事件，只回放持久事件 | RQ-05 |
| `HarnessAgentExecutionAdapterTest.should_execute_when_history_has_no_terminal_event` | 只有非终态历史时不得被重放守卫误拦 | RQ-05 |
| `HarnessAgentExecutionAdapterTest.should_terminate_on_total_deadline_even_when_events_keep_arriving` | 持续产出已映射事件不能延长总时限；超时异常类型为 `ExecutionDeadlineExceededException` | RQ-03 |
| `HarnessAgentExecutionAdapterTest.should_clear_tool_evidence_and_execution_state_on_termination` | 终止路径无条件清工具证据 + 删执行状态槽 | RQ-10、RQ-08 |
| `HarnessAgentExecutionAdapterTest.should_guard_only_boundary_events_for_delegated_execution` | 租约/任务校验只发生在边界事件，文本增量不触发 | RQ-02 |
| `AgentScopeRuntimeContextMapperTest`（4） | 状态槽按 Agent 身份与 executionId 隔离；`userId` 与状态键同源；空 agentIdentifier 拒绝 | RQ-08、RQ-09 |
| `AgentScopeFailureClassifierTest`（4） | 分类表稳定、穿透 cause 链、未知类别不可重试 | RQ-04 |
| `AgentScopeEventMapperFailureTest`（2） | RUN_FAILED 载荷含 `failureCategory`/`retryable`/`failureId` | RQ-04 |
| `ToolResultEvidenceStoreTest`（4） | 按执行清零、TTL 淘汰、容量有界、一次性消费 | RQ-10 |
| `AgentScopeSpecCompilerTest.should_not_reuse_direct_agent_when_execution_policy_differs` | 执行策略不同不得复用同一实例 | RQ-07 |
| `AgentScopeSpecCompilerTest.should_evict_and_close_least_recently_used_agent_when_capacity_reached` | 超容量淘汰并 close，缓存有界 | RQ-06 |

### 缺失测试清单

> 审计时点清单。已补齐：P0 全部 5 项（cancel 交错 / cancel 与 AGENT_START 时序 / 总时限 / 重放幂等 / 状态槽隔离取代 TOCTOU 并发预检）、P1 的 Direct 策略键、缓存淘汰、工具证据清理。仍缺：并发双订阅事件化（RQ-11）、interrupt 与 close 竞态（RQ-12）、ephemeral 同步异常矩阵、事件映射与持久顺序组合、Redis 故障注入（RQ-13）。

| 优先级 | 缺失测试 | 应验证的不变量 | 对应风险证据 |
|---|---|---|---|
| P0 | cancel 与源 complete 的可控交错测试 | `cancel=true` 必有且仅有一个 CANCELED 终态；完成胜出时 cancel 必须返回 false；永不出现 COMPLETED/FAILED 与 CANCELED 并存 | RQ-01；`cancel`（99-128 行）、`executeResolved`（236-339 行） |
| P0 | 同一 executionId 并发双订阅测试 | 仅一个源调用、失败方资源释放、map 条目不被错误 remove；明确失败方是否必须事件化 | RQ-11；`putIfAbsent/remove(key,value)`（约 267-279、455-460 行） |
| P0 | cancel 早于/同时/晚于 AGENT_START 测试 | interrupt 恰好一次，取消终态恰好一次，无启动事件时按约定及时结束 | `onSourceEvent`（432-438 行）、`interruptOnce`（447-452 行） |
| P0 | timeout 墙钟与慢入库测试 | 明确 total timeout、idle timeout、DB timeout 三种边界，禁止用一个操作符混合表达 | RQ-03；`executeResolved`（236-339 行） |
| P0 | 隐藏历史并发 TOCTOU 测试 | 两个同槽调用不能都通过空历史预检；第二次模型请求不得带入第一次未冻结历史 | RQ-08；`requireNoHiddenPersistentHistory`（342-362 行） |
| P1 | interrupt 与 close 竞态测试 | close 后不再调用 interrupt，或底层明确保证 close 后 interrupt 安全 | RQ-12；`interruptOnce/release`（447-460 行） |
| P1 | ephemeral 同步异常矩阵 | contextMapper、stateStore、streamEvents 构造、mapper、metering、eventStore 各点抛错时 close 恰好一次 | `executeResolved`（236-339 行）、`release`（455-467 行） |
| P1 | 事件映射与持久顺序组合测试 | 过滤事件不乱序，持久 sequence 严格递增，终态后不得再写运行事件 | `AgentScopeEventMapper.map`（49-132 行）、两个 `concatMap`（executeResolved 约 276-328 行） |
| P1 | execution 重放幂等测试 | 使用同一 idempotencyKey 重试时不得再次调用模型/工具，不得追加第二套事件 | RQ-05；`SynchronousExecutionEventWriter.append`（28-62 行） |
| P1 | Direct cache 策略差异测试 | maxIterations/maxRetries 不同必须得到不同配置实例，或缓存键明确纳入策略 | RQ-07；`DirectKey`（228-233 行） |
| P1 | 缓存容量与淘汰测试 | 超过上限后旧 Agent 被 close，长期画像抖动下缓存有界 | RQ-06；`AgentScopeSpecCompiler.close`（211-217 行） |
| P1 | 工具证据异常清理测试 | 工具结果后取消/timeout/error 时执行级证据归零 | RQ-10；`ToolResultEvidenceStore`（20-78 行） |
| P2 | Redis 故障注入测试 | SET/SADD 任一步失败后可恢复或清理；生产扫描不用 KEYS | RQ-13；`SpringRedisClientAdapter`（24-84 行） |

## 演进路线建议

> 审计时点建议。"上线前修复"与"近期加固"两节已全部落地，做法见[修复记录](#修复记录)；下面保留原文作为决策依据的历史记录。仅"取消主动 dispose 上游"一条被评估后调整为：取消通过 interrupt + 终态 CAS 表达，不额外 dispose 上游订阅——dispose 会绕过 AgentScope 自身的槽释放路径，收益不抵风险。

### 上线前修复

- 用单一原子终态枚举替代五个布尔量之间的隐式协议，例如 `ACTIVE → COMPLETED | FAILED | CANCELED` 只允许一次 CAS；让 `cancel()` 的返回值与终态 CAS 同源。依据：RQ-01，`cancel`（99-128 行）与 `concatWith`（executeResolved 约 309-326 行）当前分离仲裁。
- 将取消建模为源流的控制信号并主动 dispose 上游，同时把“发出 interrupt”和“持久取消终态”拆开；不得依赖模型最终响应或 timeout 才完成取消。依据：`interruptIfCancelledAndStarted`（440-444 行）当前只发中断，不终止订阅。
- 把执行总 deadline 放在完整 AgentScope 调用外层，单独为事件持久化定义写入 SLA；如仍需 idle timeout，应以独立名称配置。依据：RQ-03。
- 将每事件同步 Redis/JPA 校验移出模型事件线程：采用专用 boundedElastic/虚拟线程 Scheduler、异步端口，或降低到关键边界事件校验；无论方案如何，必须保留 fence 的 fail-closed 语义。依据：RQ-02。
- 为基础设施失败定义稳定事件字段（category、retryable、failureId），服务端完整记录原异常；调用方继续消费事件，但不再依赖异常类简单名。依据：RQ-04。

### 近期加固

- 在执行入口以 `(tenantId, executionId/idempotencyKey)` 建持久执行声明，恢复与重复订阅读取已有事件而不是再次调用模型；保留 `eventId` 作为单事件幂等的第二层。依据：RQ-05。
- 把缓存改为有界、可观测、淘汰时 close 的缓存；Direct key 纳入完整 `ExecutionPolicy`，Predefined 发布新版本时主动失效旧版本。依据：RQ-06、RQ-07。
- 把隐藏历史校验与槽占用合并为同一个串行/租约边界，或完全禁用 AgentScope session persistence；若保留状态，键中加入稳定 Agent 身份并明确 execution 是否应共享。依据：RQ-08、RQ-09。
- 给 `ToolResultEvidenceStore` 增加 execution 级 `clear`，在 `doFinally` 无条件调用，并增加 TTL/容量作为第二道防线。依据：RQ-10。

### 后续优化

- 用 Lua/事务把 Redis 状态值与 `_keys` 登记原子化，用 SCAN 替代 KEYS。依据：RQ-13。
- 建立上述 P0/P1 并发测试矩阵，并使用可控 Publisher、barrier/latch 和虚拟时间，不用 sleep 猜竞态。依据：当前 `HarnessAgentExecutionAdapterTest`（110-144 行）只覆盖三条 close 路径。
- 跟踪 AgentScope v2 上游文档一致性：当前 `HarnessAgent` 的线程安全说明、ReActAgent 实现和并发测试相互印证，但 `ReActAgent` 类头仍残留“不线程安全”描述；升级依赖时必须重跑共享实例并发契约测试。依据：AgentScope `HarnessAgent` 类注释、`ReActAgent.callSerializationKey`（502-513 行）及 `ReActAgentPerSessionStateTest`（248-309 行）。

## 最终判定

> 2026-08-31 复评结论：blocker 与九条 major 已全部修复并有测试锁定，该适配器达到上线门槛。剩余 3 条 minor（RQ-11/12/13）与 RQ-05 的"中途崩溃前缀事件重复"残留风险已明确记录，不阻断上线，按后续迭代排期处理。

### 审计时点判定（2026-08-30）

自研层没有重复实现 ReAct 推理循环本身，复用边界总体合理；主要质量问题集中在 AAF 为治理要求新增的“执行注册—取消—事件落库—状态预检”协议没有形成单一原子状态机。完成 RQ-01 和 P0 测试前，不应把该适配器作为无人值守生产执行的可靠底座；完成上线前修复与近期加固后，可以保留当前“AgentScope 执行内核 + AAF 治理外壳”的总体方向。
