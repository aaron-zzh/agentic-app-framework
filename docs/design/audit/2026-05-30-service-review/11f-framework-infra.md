# 11f framework 基础设施：任务 · 消息 · 缓存 · 序列 · 日志（优先级 5）

> 覆盖：`task/`（DistributedLockAspect、queue/TaskConsumer+RedisStreamTaskQueue、retry/RetryableTaskConsumer）、`messaging/`（MessageTemplateEngine 等）、`engine/cache/`（TwoLevelCache、ConfigCacheManager、CacheInvalidation*）、`sequence/`、`logging/OperationLogAspect`。
> 2026-05-30 分区复审：基础设施正确性与安全。审查人 AI/architect。

## 问题清单（2026-08-01 复核与修复）

| 编号 | 级别 | 状态 | 位置 | 结论 |
|------|------|------|------|------|
| M49 | 🟠 | PARTIAL（残留已记录） | `task/queue/TaskConsumer` + `task/retry/RetryableTaskConsumer` | 核实：去重与并发保护**已存在**——`task_queue:completed:<id>` 标记 + `task_queue:processing:<id>` 租约，重投判 DUPLICATE。真实残留窗口只有两个：handler 成功但写 completed 标记前崩溃；completed 标记按 retention 过期后的超晚重投。故**未叠加第二套幂等存储**（会形成并行抽象），改为在 `RetryableTaskConsumer` 类注释中写明"至少一次"语义、残留窗口与彻底方案（把完成标记移入 handler 事务的事务性收件箱，并把重试改为队列重投驱动——属架构级变更）。有不可重复副作用的 handler 必须自行按 `task.id()` 建业务幂等键 |
| M50 | 🟠 | FIXED | `engine/cache/TwoLevelCache` + `CacheInvalidationListener` | 真实：`CacheInvalidationEvent` 是 JVM 内事件，其他实例本地缓存最长陈旧 5 分钟（LOCAL_TTL）。修复：新增 `CacheInvalidationBroadcaster`（Redis pub/sub，消息带实例 ID 防回环）+ `TwoLevelCache.invalidateLocal/invalidateAllLocal`；本机失效后广播，其他实例只清本地副本；Redis 故障时退化为 TTL 收敛，不阻断主流程 |
| M51 | 🟠 | FIXED | `logging/OperationLogAspect` | 真实：`Arrays.toString(args)` 与 `result.toString()` 原样落库。修复：新增 `SensitiveLogMasker`，按参数名整值掩码 + 对渲染文本做 `key=value` / `"key":"value"` 模式掩码，覆盖入参/出参/错误信息，并跳过 servlet/multipart 等容器对象 |
| m30 | 🟡 | FIXED | `TwoLevelCache#invalidateAll` | 真实：`KEYS name:*` 在 Redis 单线程下随 key 量线性阻塞。修复：改用 SCAN 游标分批删除（每批 500） |
| m32 | 🟡 | FIXED | `task/DistributedLockAspect#around` | 真实，且确认 `@DistributedLock` 至今**无使用点**。修复：按返回类型区分——void 方法保持"跳过"语义，有返回值的方法抛 `LockNotAcquiredException`，不再用 null 表达"跳过" |

## 待确认（已核销）

- **Flyway clean 生产隔离**：`application-prod.yaml` 确有 `spring.flyway.clean-disabled: true` 与 `aaf.flyway.clean-on-start: false`，生产 clean 风险不成立。

## 良好实践

- `SequenceService` 基于 Postgres `SEQUENCE` + `nextval` 并发安全，批量用 `generate_series`，按月分段；pg 序列名由内部 `seqId`（非用户输入）构造，无注入面。
- `TaskConsumer` 失败链路已接入指数退避重试与 DLQ，避免失败任务静默丢失。
- `TaskConsumer` 使用实例化 consumer 名并回收 pending，配合虚拟线程池、`@PreDestroy` 关闭和多 Stream 优先级轮询；`ensureGroups` 幂等建组（注册/注销对称）。
- `TwoLevelCache` 两级读写穿透 loader，Redis 读写异常降级不阻断主流程。
- `OperationLogAspect` 的 SpEL 模板来自**注解常量**（开发期，非用户输入），注入风险低。

## 对称性 / 一致性提示

- 业务副作用 vs ACK（清单#10）：崩溃窗口内重新投递仍可能重复执行业务副作用（M49）。
- 缓存写入 vs 失效（清单#11）：跨节点本地缓存不失效（M50）。

## 待确认

- **Flyway clean 生产隔离**：`framework/flyway/` 无自定义配置类，需查 `application-prod.yaml` 的 `spring.flyway.clean-disabled=true`（未确认即潜在生产 clean 风险）。
- `intelligent/{agent,team,cognition}` 编排（CognitiveCycleExecutor/TaskBoard/TeamOrchestrator/AgentScheduler/agentscope）仅抽样，编排正确性与占位/重复待后续轮次逐读。
