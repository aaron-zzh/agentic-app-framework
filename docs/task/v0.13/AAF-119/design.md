---
level: Practice
layer: Model
purpose: AAF-119 全局事件通道泛化与实时通道收敛的方案设计：通用事件模型、订阅范围语义、发布入口、通道迁移与前端订阅
status: active
version: 1.0.0
date: 2026-10-14
author: AaronZZH & Kiro
tags:
  - AAF-119
  - SSE
  - 全局事件通道
  - 设计
related:
  - requirement.md
  - tasks.md
  - ../../aaf-v0.13.md
  - ../../../design/apps/webui/tech-design/realtime-data-strategy.md
---

# AAF-119 方案设计

> 状态：设计已确认（2026-10-14，7 条待确认项均已由人类接受），可按任务开工。涉及跨模块改动、接口路径、数据模型和已冻结的 v0.13 文档，变更设计需重新审核。

## 现状核对

以下内容来自 2026-10-14 对代码的阅读，未运行验证。

| 事项 | 现状 | 位置 |
|------|------|------|
| 全局 Activity SSE | `GET /api/aigc/events/stream`，`SseEmitter` 永不超时，15 秒心跳，事件落库并按游标重放 | `AigcActivityEventController`、`AigcActivityEventService` |
| 事件表与信封 | 表 `aigc_activity_event`，含 `project_id`、`execution_run_id`、`task_id`、`media_version_id`、`object_version_id`、`review_id`、`work_id`、`publication_id`、`conversation_id` 九个 AIGC 关联列 | `v7__aigc_schema.sql`、`AigcActivityEnvelope` |
| 订阅范围 | `SubscriptionScope(ownerId, orgId, workspaceId)` 精确匹配，null 只匹配 null | `AigcActivityEventRepository.replay` |
| 顺序保证 | 发布时按 scope 取 `pg_advisory_xact_lock`，`afterCommit` 后广播并按游标补发 | `AigcActivityEventService.publish / broadcast / catchUp` |
| 事件发布方 | 8 个类：`AigcTaskExecutor`、`AigcTaskService`、`AigcProjectService`、`AigcExecutionTerminalService`、`AigcProjectDeliveryService`、`ImageProcessTaskSyncJob`、`AigcTaskApiAdapter`、`AigcWorkService`；已见事件类型 `task.created / task.completed / task.failed` | `module/ai/aigc/**` |
| 前端连接 | `ProjectActivityProvider` 用 `backendStreamFetch` 建一条 fetch SSE，游标存 localStorage，只挂在 `StudioLayout` | `features/studio/content/`、`layouts/studio/StudioLayout.tsx` |
| 异步任务 SSE | `GET /api/async-tasks/events` 经 `SseSessionManager`（内存，按 userId，无补发）；`AsyncQueueTaskService.publishStatusAfterCommit` 在提交后 `push("async-task.status", event)`；前端 `EntityActions` 每次操作新建 `EventSource` | `module/system/task`、`framework/task`、`framework/messaging/sse` |
| 通知传输 | `InternalMessageSenderImpl` 按策略走 WebSocket 与 `SseSessionManager`；`SSE_ONLY` 在代码中未发现调用方 | `module/system/notify/service`、`framework/messaging/internal/InternalMessage` |
| 导出进度 | 前端 `use-export-progress.ts` 请求 `/api/{entity}/export-progress/{taskId}`；后端 Java 代码中未发现该端点；`ExportButton` 仅在 `index.ts` 导出，未发现 JSX 使用 | `lib/hooks/use-export-progress.ts`、`features/entity-engine/components/list/components/` |
| 通知 Hook | `NotificationDrawer` 自己调用 `useWebSocket("/ws/notifications")`；`use-notification-ws.ts` 无调用方 | `features/notifications/index.tsx`、`lib/hooks/use-notification-ws.ts` |
| 浏览器 Cookie 注入 | `SseTokenFilter` 为原生 `EventSource` 把 `aaf-token` Cookie 注入 Bearer 头，`use-doc-events` 仍依赖 | `framework/security/SseTokenFilter` |

## 目标架构

```text
WebSocket   通知 · IM/客服 · 语音/Omni · 画布 · 在线状态        （不变）
全局 SSE    GET /api/events/stream   落库 + 游标补发 + 心跳      （泛化）
AG-UI SSE   POST /api/agui/run       单次 Run                   （不变）
```

业务模块统一调用 `UserEventPublisher` 发事件；前端只维护一条全局连接，按事件类型前缀分发给各模块的消费者。

## 与既有 messaging 基础设施的关系

`aaf-framework` 的 `framework.messaging` 已有一套消息基础设施，现状如下（2026-10-14 读代码，未运行）：

| 部分 | 位置 | 职责 |
|------|------|------|
| `MessageService` | `messaging/` | 统一消息发送：按 `MessageChannel`（`SMS / EMAIL / INTERNAL / DINGTALK / WECOM`）路由到 `ChannelSender`，含模板渲染与发送日志，异步 `send` 与同步 `sendSync` |
| 站内信渠道 | `messaging/internal`，实现类在 `aaf-api` 的 `InternalMessageSenderImpl` | `INTERNAL` 渠道桥接到 `InternalMessageSender`，写 `Notification` 表，再按 `PushStrategy` 推 WebSocket 或 SSE |
| WebSocket 推送 | `messaging/ws`：`WebSocketMessageSender`、`WebSocketSessionManager` | 按 `(userId, channel)` 一人一连接，强类型 `WsMessage` 信封 |
| SSE 推送 | `messaging/sse`：`SseSessionManager` | 按 `userId` 保存多个 `SseEmitter`，`push` 广播，15 秒心跳；无连接范围、无事件 id、无补发、无单连接发送锁 |

直接使用情况：

- `MessageService.send / sendSync`：认证、支付、计费、分润、`TaskNotifier` 等。
- `InternalMessageSender`：`NotificationEventListener`、`NotificationService`、`TodoService`。
- 直接调 `WebSocketMessageSender`（跳过策略层）：`EntityChangeListener`、`MessageCrudService`。
- 直接调 `SseSessionManager.push`（跳过策略层）：`AsyncQueueTaskService`。
- 通知的 SSE 分支几乎无效：前端 `/api/async-tasks/events` 连接只监听 `async-task.status`，不监听通知推送的 `notification` 事件；`SSE_ONLY` 与 `progress` 类型仅出现在类注释示例，未发现调用方。
- AIGC Activity SSE 完全没有复用这套基础设施，自己实现了订阅表、emitter 生命周期、单连接发送锁与心跳。

本故事的处理：

| 事项 | 决定 | 理由 |
|------|------|------|
| `UserEventPublisher` 位置 | `framework.messaging.event`，与 `MessageService` 同在 `messaging` 包下 | 同属"推送给用户"的基础设施，同模块、同层，依赖方向不变 |
| 是否并入 `MessageService` | 不并入，不新增 `MessageChannel` | `MessageService` 面向触达类消息（模板、日志、渠道厂商）；全局事件是变化信号，需要游标补发，量大且不展示给用户 |
| 是否并入 `InternalMessageSender` | 不并入 | `InternalMessage` 是写 `Notification` 表的站内信，有标题、正文与已读状态，语义不同 |
| 复用 WebSocket 部分 | 不复用 | `WebSocketSessionManager` 是 WebSocket 专用；通知继续走原有 `InternalMessageSender` 加 `WebSocketMessageSender` |
| 复用 SSE 部分 | 复用并改造 `SseSessionManager` 作为 SSE 传输层 | 避免新增第二套 emitter 生命周期、心跳和连接清理；`AigcActivityEventService` 里的这部分能力本来就和它重复 |

`SseSessionManager` 改造方向：

- 连接对象携带 `ownerId` 与订阅范围（组织、工作区），支持带 `id` 的事件发送，每条连接一把发送锁（`SseEmitter` 不允许并发写）。
- 心跳统一在这里：保留 15 秒周期，改为 SSE 注释帧，不再用命名事件，避免客户端误当成业务事件。
- 暴露"按用户取连接、按范围过滤"的能力，`UserEventStreamService` 在其上做重放、`catchUp` 与广播；落库、游标、重放仍在 `UserEventStreamService`，传输层不依赖数据库。
- 现有三个调用方（`AsyncTaskController`、`AsyncQueueTaskService`、`InternalMessageSenderImpl`）都在本故事内迁出，改造不留旧签名。

## 通用事件模型

### 信封

```json
{
  "id": 12,
  "type": "aigc.task.completed",
  "refs": { "projectId": 1001, "taskId": 3001, "executionRunId": 2001 },
  "payload": { "status": "SUCCESS" }
}
```

- `type`：`{域}.{资源}.{动作}`，域为模块名（`aigc`、`async-task`、后续按需增加），不用内部枚举名。
- `refs`：`Map<String, Long>`，由发布方自行定义键，值必须是正整数；前端校验沿用现有"正整数或缺省"规则。字符串类关联（如 `assistantRunId`、`toolCallId`）不放 `refs`，放 `payload`。
- `payload`：只放状态摘要，不放权威业务数据，不放正文。
- SSE 命名事件名由 `activity` 改为中性的 `event`；`id:` 字段使用事件 id，支持 `Last-Event-ID`。

### 存储

表 `sys_user_event`，定义在 `v1__system_schema.sql`，删除 `v7__aigc_schema.sql` 中的 `aigc_activity_event`：

| 列 | 说明 |
|----|------|
| `id` | 自增，即 SSE 游标 |
| `owner_id` | 接收事件的用户，非空 |
| `org_id`、`workspace_id` | 发布时的租户范围；二者都为空表示用户级事件 |
| `event_type` | 事件类型 |
| `refs`、`payload` | JSONB，非空，默认 `{}` |
| 公共审计列 | 沿用 `BaseEntity` |

索引：`(owner_id, id) WHERE deleted = FALSE`；`(owner_id, org_id, workspace_id, id) WHERE deleted = FALSE`。

取舍：原表的九个关联列只被用来组装信封，服务端没有按这些列查询。换成 `refs` JSONB 后，新增模块不需要改表，代价是不能在库里按关联过滤，当前没有这类需求。

### 订阅范围语义

现状是 `(owner, org, workspace)` 精确匹配。异步任务由工作线程执行，没有组织上下文，按现状会发布到 `(owner, null, null)`，用户在任何组织或工作区下的连接都收不到。

调整为：

- 用户级事件：`org_id` 与 `workspace_id` 都为空，投递给该用户的所有连接。
- 范围事件：其余情况仍按 `(org, workspace)` 精确匹配，与现状一致。

订阅方 `(owner, org, workspace)` 收到事件当且仅当 `owner` 相同，且事件为用户级或范围精确匹配。

影响：个人上下文下发布的 AIGC 事件现在也会到达该用户在组织上下文下的连接。事件只作失效通知，前端按当前请求头重新查询，不会越权读取数据，最多多一次查询。

### 顺序保证

用户级事件会出现在同一用户的多个范围连接里，因此发布时的 advisory lock 从 `(owner, org, workspace)` 提到 `owner` 一级。同一用户的发布串行化，换来每条连接看到的游标单调、不跳号。发布频率按用户计，竞争可忽略。

订阅表由 `SseSessionManager` 持有，按 `ownerId` 索引，每条连接带自己的订阅范围，广播时按范围过滤。

## 事件清单与后续补充

### 现有事件（2026-10-14 盘点）

以下为 8 个发布方当前实际发布的 17 种事件类型，迁移时只加 `aigc.` 前缀，不改语义。`refs` 键在 #11901 开工时对照发布点复核。

| 现有类型 | 迁移后 | 发布方 |
|----------|--------|--------|
| `task.created` | `aigc.task.created` | `AigcTaskService`、`AigcTaskApiAdapter` |
| `task.completed` / `task.failed` | `aigc.task.completed` / `aigc.task.failed` | `AigcTaskService`、`AigcTaskExecutor`、`ImageProcessTaskSyncJob` |
| `project.materialized` / `project.lifecycle.changed` / `project.changed` | `aigc.project.*` | `AigcProjectService` |
| `execution.run.terminal` | `aigc.execution.run.terminal` | `AigcExecutionTerminalService` |
| `candidate.registered` / `candidate.rejected` / `candidate.adopted` | `aigc.candidate.*` | `AigcProjectDeliveryService` |
| `review.submitted` / `review.stale` / `review.decided` | `aigc.review.*` | `AigcProjectDeliveryService` |
| `work.updated` / `work.collected` / `work.archived` | `aigc.work.*` | `AigcWorkService` |
| `publication.changed` | `aigc.publication.changed` | `AigcWorkService` |

加前缀的原因：`task`、`project` 等名字与 canonical Task、系统异步任务重名，不加域前缀无法区分来源。

现有前端 `ProjectActivityProvider.invalidate` 不按事件类型分支，只按 `projectId` 失效查询。因此迁移时类型改名对前端行为没有影响；新增事件类型也只需要发布，不需要前端同步修改。

### AAF-113 后续需要补充的事件

下列事件在 AAF-113 的设计中依赖 Activity 做失效通知，但盘点时在 `module/ai` 下没有发现对应的发布点。它们由 AAF-113 的 [#11332](../AAF-113/tasks.md) 负责补齐，**不阻塞 AAF-119**：AAF-119 只负责机制，新事件直接按 `aigc.*` 前缀与 `refs` 约定接入，无论 #11332 先于还是后于 AAF-119 落地，都只需调用发布入口。

| 建议事件 | 触发点 | 依据 |
|----------|--------|------|
| `aigc.project.schedule.changed` | ProjectSchedule 创建、编辑、暂停、恢复、取消、触发、延后、失败 | AAF-113 设计：Activity 只失效 schedule、Task、ProjectProgressSnapshot 查询；`ProjectScheduleService` 当前未发布 |
| `aigc.proposal.changed` | AssistantBlueprintProposal、ProjectGenerationProposal 的创建、确认、失效、过期 | Assistant 在后台生成提案时，页面需要通知；当前仅靠用户操作后刷新 |
| canonical Task 状态变化（事件名待定） | 后台 Task 进入等待外部、待确认、暂停、终态 | AAF-113 UI 约定失效 Task 查询；AAF-115 当前靠 1 秒、3 秒短轮询 |
| 关联补全 | `conversationId` 进 `refs`；`assistantRunId`、`toolCallId` 进 `payload` | 现有信封有 `conversationId` 字段但没有任何发布方填值 |

canonical Task 的 id 类型与事件名，在 #11332 对照 AAF-115 定型；AAF-119 的设计不依赖它。

## 后端结构

| 层 | 位置 | 内容 |
|----|------|------|
| 通用核心 | `aaf-framework`，包 `com.xuejiai.aaf.framework.messaging.event`，与 `MessageService` 所在的 `messaging` 包同级的子包 | `UserEvent`（实体）、`UserEventRepository`、`UserEventPublisher`、`UserEventStreamService`（落库、按游标重放、补发）、`EventEnvelope`、`SubscriptionScope`；SSE 连接管理与心跳复用 `messaging.sse.SseSessionManager`，见"与既有 messaging 基础设施的关系" |
| 端点 | `aaf-api`，`module.system.event.controller` | `EventStreamController`：`GET /api/events/stream`，`@PreAuthorize("isAuthenticated()")`，沿用 `after` 参数、`Last-Event-ID` 头、`orgId / workspaceId` 范围授权 |
| AIGC 适配 | `module.ai.aigc.event` | `AigcEventPublisher`：把 `AigcTaskVO`、`AigcTaskView` 等转为 `refs / payload`，类型加 `aigc.` 前缀，再调用 `UserEventPublisher`；框架层不依赖任何 AIGC 类型 |

发布入口：

```java
public interface UserEventPublisher {
    /** 使用调用线程的 OrgContext 作为范围；无上下文时为用户级事件。 */
    UserEvent publish(Long ownerId, String type, Map<String, Long> refs, Map<String, Object> payload);
}
```

- `publish` 在当前事务内落库，`afterCommit` 后广播；无事务时自己开事务。
- 现有 `AigcActivityEventService.publish` 的两个重载及其按 VO 类型取字段的逻辑，迁入 `AigcEventPublisher`。
- 删除：`AigcActivityEventController`、`AigcActivityEventService`、`AigcActivityEventRepository`、`AigcActivityEvent`、`AigcActivityEnvelope`、`SubscriptionScope`（原包）。已有的 `AigcActivityEventServiceTest` 改为针对 `UserEventStreamService` 的测试，范围匹配与补发用例保留。

## 通道迁移

### 异步任务

- `AsyncQueueTaskService` 去掉 `publishStatusAfterCommit` 的 `TransactionSynchronization` 包装，直接在状态变更所在事务内调用 `UserEventPublisher.publish(ownerId, "async-task.status", refs, payload)`；事件与任务状态同事务提交，比先提交再推送更不容易丢。
- `refs` 不放字符串 taskId，`payload` 保留 `taskId / taskType / status / lastError` 等现有字段。
- 删除 `AsyncTaskController` 与 `/api/async-tasks/events`；`SseSessionManager` 保留并改造为全局事件通道的传输层，不再有直接 `push` 的业务调用方。

### 通知

- `InternalMessageSenderImpl` 去掉 `SseSessionManager` 依赖与 SSE 推送分支。
- `InternalMessage.PushStrategy` 删除 `SSE_ONLY`，`ALL` 语义改为存库加 WebSocket；类注释同步。`PERSIST_ONLY`、`WS_ONLY` 保留。
- 修改 `framework/messaging/internal` 的枚举属于接口签名变更，调用方为 `NotificationEventListener`、`NotificationService`（只使用 `PERSIST_ONLY`），需协调者评估。

### 导出进度

后端未发现 `export-progress` 端点，`ExportButton` 未发现使用点。核实后直接删除 `use-export-progress.ts`、`ExportButton` 及其导出；若核实时发现仍有使用点，改为让导出走异步任务，由 `async-task.status` 事件驱动进度，不再保留独立端点。

## 前端结构

| 位置 | 内容 |
|------|------|
| `apps/webui/src/lib/realtime/event-stream-provider.tsx` | 单例 Provider，建立 `GET /events/stream` fetch SSE，沿用现有解析、去重、游标、重连逻辑；游标 key 前缀改为 `aaf.events.cursor.v1` |
| `apps/webui/src/lib/realtime/event-registry.ts` | `subscribe(typePrefix, handler)` 返回取消函数；`waitForEvent(predicate, signal)` 用于"等待某个任务终态"；内部保留最近 64 条事件，避免提交响应与订阅之间的竞态漏事件 |
| `apps/webui/src/features/studio/content/` | 现有的 AIGC 缓存失效逻辑改成注册到 `aigc.` 前缀的消费者，不再自己持有连接 |
| `EntityActions.tsx` | 用 `waitForEvent` 替换 `subscribeToAsyncTask`，去掉 `EventSource`、`ASYNC_TASK_EVENTS_URL` |

- Provider 挂在 workspace 与 Studio 布局的共同上层；找不到共同上层时两处各挂一次，任一时刻只存在一条连接。
- 服务端数据仍由 TanStack Query 管理，事件只触发失效；不把事件载荷复制到 Zustand。
- 删除 `lib/hooks/use-notification-ws.ts`。`NotificationDrawer` 保持现有 WebSocket 接入不变。
- 现有连接中断提示条文案是"服务端任务继续运行，正在重连…"，原本只在 Studio 出现。移到全局后，是否在 workspace 页面也显示、是否延迟几秒再显示，需要 UI 确认，见待确认项。

## 不变项

- `SseTokenFilter` 保留：`use-doc-events` 仍使用原生 `EventSource`。
- CORS、`OrgFilter`、`ScopeFilter`、`SecurityConfig` 不改。
- WebSocket 端点与握手拦截器不改。

## 迁移与文档同步

直接替换，不保留旧端点和旧表：

- 受影响的冻结文档：`docs/task/aaf-v0.13.md`（实时事件与双通道边界一节中的端点和 `useAigcTaskStream` 表述）、`docs/task/v0.13/AAF-112/tasks.md`（端点名）、`docs/task/v0.13/AAF-113/requirement.md`（端点名）、`docs/design/apps/webui/tech-design/realtime-data-strategy.md`（`/api/events/live` 改为 `/api/events/stream`，补充三类通道边界）。
- 建议新增一份 ADR 记录"三类实时通道边界"，编号取 `docs/design/adr/` 当前最大值加一。

## 风险

| 风险 | 缓解 |
|------|------|
| 用户级事件语义改变了现有精确匹配 | 只扩大投递面，事件仅作失效通知；用最小用例覆盖"用户级到所有连接、范围事件仅精确匹配" |
| owner 级 advisory lock 粒度变粗 | 竞争只在同一用户内发生，发布本身只写一行；保留现有补发逻辑兜底 |
| 8 个 AIGC 发布方一次迁移，涉及文件多 | 先落通用核心与 `AigcEventPublisher`，发布方只改调用，不改业务逻辑；超过 5 个文件由协调者评估 |
| 与 AAF-112、AAF-113 进行中的改动冲突 | AAF-112 的 Activity 事件已落地，清单已在本文盘点，不以 AAF-112 #11206 为开工前提；AAF-112 整改（B-02、B-04、B-05 涉及 `AigcProjectDeliveryService`）与 #11905 改动同一批发布方，按先后顺序合并即可。AAF-113 后续新增事件由其 #11332 按新约定接入，不构成前置条件 |
| 事件表会持续增长 | 不在本故事范围，另立故事处理清理策略；当前与旧表的增长特征一致 |
| 改造 `SseSessionManager` 影响现有调用方 | 三个调用方都在本故事内迁出，改造后无外部使用；心跳由命名事件改为注释帧，需确认前端不依赖 `heartbeat` 事件名（`EntityActions` 只监听 `async-task.status`） |

## 已确认事项

以下 7 项已于 2026-10-14 由人类逐条接受；第 4 项按建议处理（workspace 页面也显示，延迟约 3 秒），第 5 项先按"确认无使用点后直接删除"处理，第 6 项新增 ADR。

1. "用户级事件 = `org_id` 与 `workspace_id` 都为空"的语义是否接受。
2. `refs` 采用 JSONB 而不是保留 AIGC 关联列是否接受。
3. 端点名 `/api/events/stream`、表名 `sys_user_event`、前端目录 `lib/realtime/` 是否接受。
4. 全局连接中断提示在 workspace 页面是否显示，以及是否延迟显示。
5. 导出进度核实结果：确认无使用点后直接删除，还是需要补齐导出到异步任务的链路。
6. 是否同意新增 ADR。
7. `UserEventPublisher` 放在 `messaging.event`、复用并改造 `SseSessionManager` 作为 SSE 传输层，且不并入 `MessageService` 与 `InternalMessageSender`，是否接受。
