# 开发记录：AAF-119 实时通道收敛与全局事件通道

执行者：AI/developer-service + developer-webui

> 约束：本轮实现不编译、不运行任何测试与 lint、不新增 SQL 文件（直接改现有 `v1__system_schema.sql`、`v7__aigc_schema.sql`），下列条目均为"已实施、未运行验证"，由人类或后续 check 阶段验证。

## 完成记录

- ✅ #11901 事件清单复核 — 8 个发布方按原签名迁移，`refs` 键为 projectId / executionRunId / taskId / mediaVersionId / objectVersionId / reviewId / workId / publicationId（2026-10-14）
- ✅ #11902 通用事件表与实体 — v1 新增 `sys_event`（`refs`、`payload` JSONB，两个索引），v7 删除 `aigc_activity_event`；`SysEvent` 标注 `@OrgIgnore`，范围匹配由仓库 JPQL 显式完成（2026-10-14）
- ✅ #11903 发布与订阅 — `SysEventPublisher`（事务内落库、afterCommit 广播、owner 级 advisory lock）、`SysEventStreamService`（重放、补发、15 秒兜底）；`SseSessionManager` 改为带 `ownerId` 与订阅范围的传输层，新增 `SseConnection`，心跳改注释帧（2026-10-14）
- ✅ #11904 `EventStreamController` — `GET /api/events/stream`，沿用 `after`、`Last-Event-ID`、`orgId / workspaceId` 授权，命名事件 `event`；删除 `AigcActivityEventController`（2026-10-14）
- ✅ #11905 AIGC 发布方迁移 — `AigcEventPublisher` 保留原两个 `publish` 重载并加 `aigc.` 前缀，8 个发布方与 4 个测试 mock 仅改类型；删除 `AigcActivityEvent*`、`AigcActivityEnvelope`、旧 `SubscriptionScope` 及其两个旧测试（2026-10-14）
- ✅ #11906 异步任务事件并入 — `AsyncQueueTaskService` 状态变更同事务调用 `SysEventPublisher`，删除 `AsyncTaskController` 与 `AsyncTaskStatusEvent`（2026-10-14）
- ✅ #11907 通知清理 SSE 分支 — `InternalMessageSenderImpl` 去 SSE 依赖，`PushStrategy` 删除 `SSE_ONLY`，`ALL` 为存库加 WebSocket（2026-10-14）
- ✅ #11908 前端全局 Provider 与注册表 — `lib/realtime` 下新增 `event-stream.ts`（协议）、`event-registry.ts`（`subscribe` / `waitForEvent` / 最近 64 条缓冲）、`event-stream-provider.tsx`、`use-event-subscription.ts`；Provider 挂在根 layout 的 `AuthProvider` 内（2026-10-14）
- ✅ #11909 AIGC 消费者迁移 — `AigcEventConsumer` 订阅 `aigc.` 前缀并失效查询；删除 `ProjectActivityProvider` 及其测试，断言迁入 `lib/realtime/event-stream.test.ts`（2026-10-14）
- ✅ #11910 `EntityActions` 与死代码清理 — `trackAsyncTask` 基于 `subscribe` + `waitForEvent`；删除 `use-export-progress.ts`、`ExportButton` 及导出、`use-notification-ws.ts`（2026-10-14）
- ✅ #11911 文档同步与 ADR — 更新 `aaf-v0.13.md`、AAF-112 tasks、AAF-113 requirement / tasks / ui-design、`realtime-data-strategy.md`；新增 ADR-009（2026-10-14）
- ⏳ #11912 整体验证 — 按用户要求本轮不执行 `check:affected` 与手动验证，待后续补做

## 实现决策

- `SysEventPublisher` 为具体类而非接口：只有一个实现，遵循编码规范"单实现不加接口"；方法签名与设计一致
- `AigcEventPublisher` 放在 `module.ai.aigc.event.service`，沿用原包位置，发布方只需替换类型与 import
- 连接先登记再重放：重放期间到达的广播等待连接发送锁，之后从游标继续，不再需要旧实现的 scope 级锁
- 全局 Provider 挂根 layout 而非 workspace / Studio 共同上层：二者的共同上层就是根 layout，登录后全应用只有一条连接；连接中断提示延迟 3 秒显示，workspace 页面同样显示
- `refs` 非正整数值与 null 在发布时丢弃并记 warn，payload 的 null 值丢弃
- 异步任务事件 payload 保留 `result`、`lastError` 等原字段，`EntityActions` 依赖 `result.deletedCount`

## 注意事项

- 本轮没有补 `SysEventStreamServiceTest`：用户要求不新增非必要测试。设计列出的最小用例（用户级事件到所有范围、范围事件仅精确匹配、补发不丢不重）仍建议后续补充
- `use-execution-state.ts`（流程执行事件）与 `use-doc-events.ts` 仍使用原生 `EventSource`，不在本故事范围
- 历史记录 `AAF-112/test-report.md`、`review.md` 仍引用已删除的 `AigcActivityEventServiceTest` 等，属当时事实记录，未改
- `sys_event` 表无清理策略，另立故事处理
