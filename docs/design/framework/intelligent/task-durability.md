---
level: Practice
layer: Model
purpose: 定义长任务的持久执行机制——租约与 fencing、幂等、重试、取消、恢复与多副本一致性
status: draft
version: 1.0.1
date: 2026-08-25
author: Kiro
tags:
  - 持久执行
  - 租约
  - 幂等
  - 恢复
dependencies:
  - ./architecture.md
  - ./runtime.md
related:
  - ./runtime-event.md
scope:
  includes:
    - 持久任务模型与状态真理源划分
    - 多副本协调：租约、续租、抢占与 fencing
    - 幂等 receipt、重试、取消与接管
    - 断点恢复与状态一致性保障
  excludes:
    - 事件契约与对外投影（见 runtime-event.md）
    - 完成门禁判定（见 runtime.md）
    - 动作授权与预算门禁（见 action-governance.md）
gains:
  - 能说明长任务在进程重启或多副本切换后如何续跑而不重复副作用
  - 能定位某项状态的真理源是 TaskBoard、事件流还是 Agent 工作态
  - 能判断一次重试是否安全
---

# 持久执行

> 长任务必须可恢复、可观测、状态一致。**恢复与 fencing 只在持久任务模型内进行**，不依赖内存态或连接生命周期。

## 状态真理源划分

| 状态 | 真理源 | 说明 |
|---|---|---|
| 当前编排状态 | TaskBoard（PostgreSQL） | 节点拓扑、依赖、进度的业务真理源 |
| 执行事实与变化 | `ai_task_event` | 追加记录，不覆盖；支持重放 |
| Agent 可恢复工作态 | AgentState | 仅保存单个 Agent 的执行工作态 |
| 会话与任务身份 | `threadId` / `runId` | 见 [runtime.md](runtime.md) |

三者不互相替代：**TaskBoard 是状态，事件是事实，AgentState 是工作态**。

## 多副本协调

| 机制 | 契约 | 实现态 |
|---|---|---|
| 会话级租约 | 按 conversation 获取租约后才调度，防止同一会话并发执行 | ✅ 已实现 · `RedisConversationLeaseAdapter.java:22-62` |
| 续租 | 执行期周期续租，过期后才允许新 owner 抢占 | ✅ 已实现 · 适配器支持续租（`RedisConversationLeaseAdapter.java:28-38`），协调器按 TTL/3 周期续租（`DelegatedTaskCoordinator.java:1173-1190`） |
| 抢占与 fencing | 抢占后旧持有者的状态、事件与 TaskBoard 写入被 token 拒绝 | ✅ 已实现 · `JpaTaskTransitionAdapter.java:1050-1135` |
| 调度前校验 | 租约不可得时不进入执行 | ✅ 已实现 · `DelegatedTaskCoordinator.java:204-262` |

> ⚠️ 部分实现 · 租约不可得、任务已等待或暂停时当前可能返回空流（`DelegatedTaskCoordinator.java:204-262`），客户端拿不到持久终态帧；终态投影契约见 [runtime-event.md](runtime-event.md)。

## 幂等与重试

| 机制 | 契约 | 实现态 |
|---|---|---|
| 输入幂等 | 以 `inputId` 落库，相同 ID 不同事实拒绝 | ✅ 已实现 · `JpaDelegatedTaskAdapter.java:50-77` |
| 事件幂等 | 以 `eventId` 为主键，同 ID 内容一致时幂等返回 | ✅ 已实现 · `SynchronousExecutionEventWriter.java:28-78` |
| 动作幂等 | 写工具以稳定 action key 领取 receipt；成功 receipt 可重放跳过，但外部副作用与 receipt 完成非原子 | ⚠️ 部分实现 · 稳定 key、摘要冲突与成功重放已实现（`DefaultToolGateway.java:123-171,255-293`）；崩溃发生在外部动作成功后、receipt 完成前时，更高 fence 可重新领取 `PENDING` 并重复动作（`JpaInvocationReceiptAdapter.java:33-78`） |
| 工具侧 fencing | 当前 lease/fence 必须在调用前后有效，外部资源写入还须携带版本前置条件或下游幂等键 | ⚠️ 部分实现 · 网关调用前后会校验当前 lease（`DefaultToolGateway.java:67-69,218-230`），但本地工具/外部连接器未统一证明在资源提交点拒绝旧 fence |
| 重试边界 | 重试节点不复用父节点执行画像，各 execution 独立冻结 | ✅ 已实现 · `JpaExecutionProfileSnapshotAdapter.java:22-45` |

⚠️ 无源 ID 的投影合成事件仍使用随机 `eventId`，跨重试不稳定（`ExecutionEventPublicMapper.java:31-75`）。

## 取消、暂停与接管

任务状态独立于聊天回合，支持暂停、取消、继续、接管与验证失败后继续修复。

> **分类拍板（方案 C，2026-08-30 复核收窄）**：当前信任客户端传入的 `kind`（`CANCEL`/`MODIFY`/`SUPPLEMENT`/`UNRELATED`），须改为服务端分类且不信任客户端标注。不引入版本化的 `BoardRevision`/历史快照——审计留痕交给既有事件流，Board 是被正常更新的状态，不需要额外版本标记。目标态处置：

| 类别 | 识别方式 | 处置 |
|---|---|---|
| 取消 | 走既有确定性 `/stop` 端点，不经通用输入 `kind` 判定 | 中断当前执行 |
| 修改 | 非自主分类判定为修改意图 | 语义边界**包含目标/约束修改，也包含执行计划/DAG 修改**（新增/删除步骤、改变依赖、角色或工具）；先终止冲突的运行中/未执行子任务（复用既有 `TaskBoard.interruptRunning`，终止后的节点状态天然拒绝旧执行流程的滞后写回），再按新目标重新走一次协调规划生成新 `subTasks`，原地更新 `ai_task_board` 这一行；已完成节点结果不自动复用到新计划 |
| 补充 | 非自主分类判定为补充意图 | 注入上下文；仅在 `AWAITING_CLARIFICATION` 状态合并进 `requiredFields` |
| 无关 | 非自主分类兜底 | 排队待处理，不消费为已处理事实 |

修改生效事实追加一条任务事件（复用既有 `ai_task_event` 追加式事实流），不在 Board 实体上重复记录版本信息；`(tenant_id, task_id)` 唯一约束不变，不新增表或字段。

实现态：⚠️ 部分实现 · 结构化补参在 `AWAITING_CLARIFICATION` 状态可用（`TaskIngress.java:13-40`、`JpaTaskTransitionAdapter.java:215-361`）；运行中的自然语言追加输入当前被拒绝；服务端分类与终止冲突节点+原地重规划均为目标态，当前直接信任客户端 `kind`。

## 恢复

> **范围拍板（方案 C，2026-08-29）**：恢复边界是"从最近安全提交点重启"，不是"从 ReAct 内部任意步骤续跑"。不承诺任意 token/内部思考断点续跑；L2 只在 receipt 或节点完成边界重启，找不到安全边界即整节点重建。三层实现顺序按分层方向推进（各自建设 + 共享恢复门禁），不引入统一 Recovery Aggregate（避免其持有业务状态形成第二真理源）。

### 可恢复粒度

| 粒度 | 恢复对象 | 最近安全恢复点 | 恢复规则 |
|---|---|---|---|
| 步骤级 | 单个 L2 execution / TaskBoard 子节点 | 已提交工具 receipt、步骤结果或 iteration 判定（**不含** AgentState 内部工作态） | 跳过已完成副作用；找不到安全边界时**以新 execution 重建该节点**，不迁移旧 fence 的 AgentState，不回写旧 execution |
| 会话级 | L3 Assistant 的稳定 task、TaskBoard、InputBuffer 与会话焦点 | 已原子提交的 TaskBoard + 任务状态 + 事件 offset + 节点画像引用 | 取得新 lease/fence 后重放事件校验状态；完成节点跳过，运行节点恢复或重置为待调度，依赖满足后继续 |
| 目标级 | L4 Team/长期业务目标及其 Assistant 子目标 | 已发布目标版本、成员/责任分工、子任务完成证据与聚合状态 | 保持 goal/task 身份，已验证子目标跳过；未完成子目标创建新 execution；**成员或目标版本变化一律转人工重规划，不自动迁移** |

三层是递进边界，不是三份可独立修改的状态：步骤级工作态归 AgentState，会话级编排归 TaskBoard，目标级责任与分工归 Team/Goal；事件只记录变化。

### 恢复前置校验（RecoveryPreflight）

任一粒度的恢复动作生效前，必须先完成统一 preflight，不得延后到下一次工具调用才补校验：

| 校验项 | 规则 |
|---|---|
| 主体与租户 | 重新校验当前认证主体、任务所有权与资源归属 |
| grant | 重新检查授权范围、有效期、撤销状态；失效转同步 HITL |
| 凭证 | 重新解析有效 credential handle；过期或 scope 变化即暂停 |
| 平台策略 | 当前 deny/安全策略可收窄旧画像，不能扩大；冲突暂停并记录原因 |
| 预算 | 以持久账本扣除已消费量后继续，不重置任何预算 |

任一项不确定或校验失败即 fail-closed，不得放行。此表是对下方"恢复流程"与"恢复约束"两节中授权相关行的统一入口化，不新增校验规则本身。

### 恢复点写入时机

恢复点必须与对应状态、事件和 outbox 在同一事务或可证明一致的提交边界内写入：

| 时机 | 必须冻结/记录 |
|---|---|
| 意图、Route、画像与计划首次生效后 | execution 画像引用、TaskBoard 版本、预算与聚合合同 |
| 进入授权、澄清、暂停或人工接管前 | 等待原因、待处理 ID、当前 owner、下一恢复动作 |
| 每次有副作用工具完成后 | receipt、结果安全引用、资源版本与下一步骤；禁止先推进状态后补 receipt |
| 子任务、iteration group 或聚合阶段完成后 | 完成证据、依赖释放、剩余预算与下一可运行节点 |
| 取消、失败、完成或 ownership 转移前 | 规范终态/恢复原因、最后 eventOffset、责任主体 |

产物类长任务生成期间**不逐 token 更新数据库**。正文只经流传输；恢复缓冲按段落、稳定块或时间窗口写入受控产物 checkpoint，事件仅保存引用与摘要。

### 检查点策略

- **先持久事实后确认推进**：只有状态、事件、receipt/outbox 已提交才算恢复点成立
- **全量基线 + 增量事实**：TaskBoard/AgentState 保存最近完整快照，之后用追加事件校验；事件不反向成为第二状态表
- **稳定身份与版本**：检查点含 task/execution/session、快照版本、摘要哈希和 fencing token；版本不兼容时 fail-closed。**DelegatedTask/TaskBoard 的通用 checkpoint 须类型化**（版本号、内容哈希、eventOffset），不是无结构 Map，以支持恢复时机械校验一致性而非人工判读
- **有界频率**：步骤边界必写；长计算按时间/内容窗口写；纯 token delta 不写
- **单写者**：只有当前 lease/fence 持有者可写；迟到 owner 的状态、事件、AgentState 与产物提交全部拒绝
- **保留与清理**：终态确认、审计保留期和产物提交完成后才清理中间 checkpoint；清理不删除执行事实

### 恢复流程

```text
领取恢复任务并取得新 lease / fencing token
→ 加载 TaskBoard、任务状态、最近 checkpoint 与已持久事件
→ 校验 checkpoint 版本、哈希、身份和 eventOffset 一致性
→ 加载该 execution 的 ExecutionProfileSnapshot，不按当前配置重新解析
→ 执行 RecoveryPreflight：重新校验动态授权、凭证、预算与平台硬策略（任一不确定 fail-closed）
→ 跳过已有 receipt 和已完成节点；找不到安全边界的节点以新 execution 重建，不迁移旧 fence 的 AgentState
→ 原子写入 RECOVERING/RUNNING 事实后继续调度
```

画像与授权采用不同规则：

| 项 | 恢复约束 |
|---|---|
| 冻结画像 | 必须复用原 `ExecutionProfileSnapshot` 的 Assistant revision、Role、Skill、模型、Prompt、上下文压缩和工具授权规则；不同快照拒绝。新重试/接管节点用新 execution 独立冻结 |
| 主体与租户 | 重新校验当前认证主体、租户、任务所有权和资源归属；不得因旧快照跳过 |
| grant | 重新检查 action/resource/scope、有效期、撤销状态、条件、reversible 与当前主体；失效则进入同步 HITL |
| 凭证 | 重新解析有效 credential handle；过期、撤销或 scope 变化时暂停，不得使用快照中的明文或旧句柄绕过 |
| 平台硬策略 | 当前 deny/安全策略可以进一步收窄旧画像，但不能扩大工具与权限；策略冲突时暂停并记录原因 |
| 预算 | 以持久账本扣除已消费量后继续；恢复不得重置调用、Token、时间或迭代预算 |

实现态：⚠️ 部分实现 · `TaskRecoveryPort` 仅持久化 Assistant 命令和 approvalId 恢复作业（`TaskRecoveryPort.java:13-48`、`JpaTaskRecoveryAdapter.java:31-141`）；TaskBoard、任务状态、事件与 outbox 的原子迁移已实现（`JpaTaskTransitionAdapter.java:99-125,524-563`），过期运行任务可回到待调度（`JpaDelegatedTaskAdapter.java:536-565`）。通用 Agent 步骤 checkpoint、会话级完整重建、目标级恢复及恢复后统一动态授权重校验尚未闭合。

## 实现态

| 契约 | 实现态 |
|---|---|
| 会话租约、续租、抢占与 fencing | ✅ 已实现 · `RedisConversationLeaseAdapter.java:22-62`、`JpaTaskTransitionAdapter.java:1050-1135` |
| 输入与事件幂等 | ✅ 已实现 · `JpaDelegatedTaskAdapter.java:50-77`、`SynchronousExecutionEventWriter.java:28-78` |
| TaskBoard 作为编排状态真理源 | ✅ 已实现 · `JpaTaskTransitionAdapter.java:99-125,524-563` |
| 空流与异常路径的持久终态 | ⚠️ 部分实现 · `AssistantAguiController.java:185-197` 仍发送临时错误帧 |
| 运行中自然语言追加输入的合并 | 🎯 目标态 · 当前不得声称已执行 |
| `eventId` 跨重试稳定 | 🎯 目标态 · 当前不得声称已执行；投影合成事件仍随机 |
| 三层恢复粒度完整落地 | 🎯 目标态（方案 C，2026-08-29 已拍板范围） · 审批恢复与过期任务回收已落地；通用步骤级 checkpoint 类型化、会话完整重建、目标级恢复与统一 `RecoveryPreflight` 未闭合，见 `TaskRecoveryPort.java:13-48`、`JpaDelegatedTaskAdapter.java:536-565` |

## 验收基线

- 进程重启或副本切换后任务能从最近安全恢复点续跑
- 失败、重试、恢复与多副本切换不产生重复文档或重复外部副作用
- 被抢占的旧持有者无法写入任何状态、事件、AgentState 或产物
- 任一时刻能定位某项状态的唯一真理源
- 取消与暂停在下一个可中断点生效，且写入恢复点
- 恢复复用冻结画像但重新校验动态授权；任何过期 grant、凭证或更严格平台策略都 fail-closed
