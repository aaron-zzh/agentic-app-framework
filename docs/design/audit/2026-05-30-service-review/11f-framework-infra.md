# 11f framework 基础设施：任务 · 消息 · 缓存 · 序列 · 日志（优先级 5）

> 覆盖：`task/`（DistributedLockAspect、queue/TaskConsumer+RedisStreamTaskQueue、retry/RetryableTaskConsumer）、`messaging/`（MessageTemplateEngine 等）、`engine/cache/`（TwoLevelCache、ConfigCacheManager、CacheInvalidation*）、`sequence/`、`logging/OperationLogAspect`。
> 2026-05-30 分区复审：基础设施正确性与安全。审查人 AI/architect。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| M49 | 🟠 | `task/queue/TaskConsumer` | handler 业务副作用与 ACK 之间仍无原子或幂等保障→副作用完成后进程崩溃会触发重新投递并重复执行 | handler 以 task.id 建立持久化幂等键，并让幂等记录与业务副作用原子提交；或采用事务性收件箱 |
| M50 | 🟠 | `engine/cache/TwoLevelCache#invalidate` + `CacheInvalidationListener` + `ConfigCacheManager` | 失效仅清**本机** Caffeine + Redis；`CacheInvalidationEvent` 是 JVM 内 ApplicationEvent（非 Redis pub/sub）→其他实例本地缓存不失效，配置（model/agent/prompt）最长 `LOCAL_TTL=5min` 跨节点陈旧 | 失效经 Redis pub/sub 广播到所有实例本地缓存 |
| M51 | 🟠 | `logging/OperationLogAspect#publishEvent` | 审计记录 `params=Arrays.toString(args)` 与 `response=result.toString()`（截断 2000）**无脱敏**→密码/token/身份证等敏感数据进入审计日志 | 敏感字段/参数掩码（按注解或字段名白/黑名单） |
| m30 | 🟡 | `engine/cache/TwoLevelCache#invalidateAll` | 用 `redisTemplate.keys(name+":*")`，生产环境 KEYS 阻塞 Redis | 改用 SCAN 或维护 key 集合 |
| m32 | 🟡 | `task/DistributedLockAspect#around` | 获取锁失败仍返回 `null`；当前无 `@DistributedLock` 注解使用点，尚无实际调用方受影响，但未来用于有返回值方法时无法区分“跳过”与“返回 null” | 首次引入注解使用点前改为抛专用异常，或明确约束仅用于 void/@Scheduled |

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
