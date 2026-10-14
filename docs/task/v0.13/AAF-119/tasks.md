---
level: Practice
layer: Model
purpose: AAF-119 实时通道收敛与全局事件通道的技术任务
status: draft
version: 1.0.0
date: 2026-10-14
author: AaronZZH & Kiro
tags:
  - AAF-119
  - SSE
  - 技术任务
related:
  - requirement.md
  - design.md
  - ../AAF-118/tasks.md
---

# 技术任务：实时通道收敛与全局事件通道（AAF-119）

> 需求：[requirement.md](requirement.md)　设计：[design.md](design.md)
> 负责人：architect 拆分 | 创建：10-14 | 更新：10-14

## 任务约束

- 涉及跨模块、接口路径、数据模型和已冻结文档，[设计](design.md)经人工审核通过后才能编码；设计与任务冲突时先改设计。
- 排期：不以 AAF-112 #11206、AAF-113 为前提，设计已确认即可开工；AAF-112 整改与 #11905 改动同一批发布方时按先后合并。AAF-113 后续补充的事件由其 [#11332](../AAF-113/tasks.md) 按本故事的 `aigc.*` 与 `refs` 约定接入。
- 项目未发布：直接替换，不保留旧端点、旧表、旧类，不加 fallback 或兼容层。
- SQL 直接修改现有 `v1__system_schema.sql`、`v7__aigc_schema.sql`，不新增迁移文件。
- 批量修改文件超过 5 个或改接口签名（#11903、#11905、#11906 涉及），由协调者评估后再做。
- 不新增非必要单元测试；范围匹配、补发、授权属于核心语义，必要时仅补最小集。
- 编码前需加载 `developer-service`（后端）与 `developer-webui`（前端）的 `resources`。
- 完工前跑 `pnpm check:affected`，全绿才能汇报。

## 任务列表

### 盘点与数据模型

1. [x] #11901 复核现有事件类型与消费方
   - [design.md](design.md#事件清单与后续补充) 已于 2026-10-14 盘点 17 种现有事件；开工时对照代码复核，补全各事件的 `refs` 键清单，并确认前端消费方（现有 `invalidate` 只按 `projectId` 失效，不按类型分支）
   - verify: 复核清单覆盖 `module/ai/aigc` 下全部 `publish(` 调用；前端使用到的关联字段都在 `refs` 键清单中；AAF-113 #11332 若已落地，将其新增事件并入清单
2. [x] #11902 通用事件表与实体
   - `v1__system_schema.sql` 新增 `sys_event`（含 `refs`、`payload` JSONB 与两个索引），删除 `v7__aigc_schema.sql` 中的 `aigc_activity_event`；`framework.messaging.event` 下新增 `SysEvent`、`SysEventRepository`；实体标注 `@OrgIgnore`；重放与上界查询实现"用户级事件投递所有连接、范围事件精确匹配"；advisory lock 改为 owner 级
   - 依赖：#11901
   - verify: 空库启动成功；表和索引存在；仓库层最小用例覆盖用户级与范围事件的重放（用户级到所有范围、范围事件仅精确匹配）

### 发布与订阅

3. [x] #11903 `SysEventPublisher` 与 `SysEventStreamService`
   - 把 `AigcActivityEventService` 的发布、重放、catchUp、广播泛化迁入 framework；SSE 连接管理、单连接发送锁与心跳复用并改造 `SseSessionManager`（连接带 `ownerId` 与订阅范围、支持带 id 事件、心跳改注释帧），落库与游标留在 `SysEventStreamService`；`publish` 在当前事务内落库，`afterCommit` 后广播
   - 依赖：#11902
   - verify: 补发不丢不重；同一用户多范围连接下游标单调；提交回滚的事件不广播；原 `AigcActivityEventServiceTest` 中的订阅与补发用例迁到新服务后通过；`AigcActivityEventService` 中自建的 emitter 生命周期与心跳代码不再存在
4. [x] #11904 `EventStreamController`
   - `aaf-api` 新增 `GET /api/events/stream`，沿用 `after`、`Last-Event-ID`、`orgId / workspaceId` 授权；命名事件名为 `event`；删除 `AigcActivityEventController`
   - 依赖：#11903
   - verify: 未登录返回 401；带不属于当前用户的 `orgId / workspaceId` 返回 403；`curl` 带 token 能收到 `connected` 注释与心跳；旧路径 `/api/aigc/events/stream` 返回 404

### 模块迁移

5. [x] #11905 AIGC 发布方迁移
   - 新增 `AigcEventPublisher`（VO / View 转 `refs / payload`，类型加 `aigc.` 前缀）；8 个发布方改调用；删除 `AigcActivityEvent*`、`AigcActivityEnvelope`、原 `SubscriptionScope`
   - 依赖：#11903、#11901
   - verify: `grep AigcActivityEvent` 在 `apps/` 下无结果；任务创建、完成、失败的事件类型与 `refs` 与清单一致；框架层不依赖 AIGC 类型
6. [x] #11906 异步任务事件并入
   - `AsyncQueueTaskService` 改为在状态变更事务内调用 `SysEventPublisher.publish`，去掉 `TransactionSynchronization` 包装；删除 `AsyncTaskController` 与 `/api/async-tasks/events`；`SseSessionManager` 不再被业务直接 `push`
   - 依赖：#11903
   - verify: 工作线程完成任务后用户所有连接都收到 `async-task.status`；事务回滚不产生事件；`grep sseSessionManager.push` 无结果，`SseSessionManager` 仅被 `SysEventStreamService` 使用
7. [x] #11907 通知清理 SSE 分支
   - `InternalMessageSenderImpl` 去掉 SSE 依赖与推送分支；`InternalMessage.PushStrategy` 删除 `SSE_ONLY`，`ALL` 改为存库加 WebSocket，同步类注释与调用方
   - 依赖：#11906
   - verify: 审批、@提及、任务类通知仍写库并经 `/ws/notifications` 到达；`PERSIST_ONLY` 调用方行为不变

### 前端

8. [x] #11908 全局事件 Provider 与订阅注册表
   - 新增 `lib/realtime/event-stream-provider.tsx` 与 `event-registry.ts`（`subscribe`、`waitForEvent`、最近 64 条事件缓冲）；连接 `GET /events/stream`，游标 key 前缀改为 `aaf.events.cursor.v1`；挂载到 workspace 与 Studio 布局的共同上层，找不到则两处各挂并保证任一时刻只有一条连接
   - 依赖：#11904
   - verify: 切换 workspace 与 Studio 页面时 Network 中同一时刻只有一条 `events/stream`；重连带最新游标；重复事件不触发二次失效；连接中断提示在不同布局的显示按待确认项结论处理
9. [x] #11909 AIGC 消费者迁移
   - 把 `ProjectActivityProvider` 中的缓存失效逻辑拆为注册到 `aigc.` 前缀的消费者，Provider 不再持有连接；`StudioLayout` 同步；`ProjectActivityProvider.test.ts` 迁移并更新断言（"只建立一个带授权上下文的 fetch SSE 连接"改到全局 Provider）
   - 依赖：#11908、#11905
   - verify: 生成任务完成后项目页、作品、媒体、任务查询被失效并重新拉取；`pnpm nx test webui` 通过
10. [x] #11910 `EntityActions` 与死代码清理
    - `EntityActions` 用 `waitForEvent` 替换 `subscribeToAsyncTask`，去掉 `EventSource` 与 `ASYNC_TASK_EVENTS_URL`；核实后删除 `use-export-progress.ts`、`ExportButton` 及导出、`use-notification-ws.ts`
    - 依赖：#11908、#11906
    - verify: 批量导入等异步操作的进度与终态展示不变；`grep "new EventSource"` 仅剩 `use-doc-events`；`grep export-progress apps/` 无结果；若核实发现 `ExportButton` 仍有使用点，停止删除并回到设计待确认项 5

### 文档与验证

11. [x] #11911 文档同步与 ADR
    - 更新 `docs/task/aaf-v0.13.md`、`docs/task/v0.13/AAF-112/tasks.md`、`docs/task/v0.13/AAF-113/requirement.md`、`docs/design/apps/webui/tech-design/realtime-data-strategy.md` 中的端点与表述；新增 ADR 记录三类实时通道边界；`docs/task/aaf-v0.13.md` 为迭代文件，由协调者更新
    - 依赖：#11905、#11906、#11908
    - verify: `grep -r "aigc/events/stream\|async-tasks/events\|events/live" docs/` 只剩 AAF-119 文档中对旧名的说明
12. [ ] #11912 整体验证
    - `pnpm check:affected` 全绿；手动验证：重启后端后前端按游标补发；关闭 WebSocket 通知后通知链路不依赖 SSE；助理对话和通知、IM 不受影响
    - 依赖：全部
    - verify: 验收标准每条场景有对应结果，写入 `dev-log.md`
