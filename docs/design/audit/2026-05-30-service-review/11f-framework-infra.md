# 11f framework 基础设施：任务 · 消息 · 缓存 · 序列 · 日志（优先级 5）

> 覆盖：`task/`（DistributedLockAspect、queue/TaskConsumer+RedisStreamTaskQueue、retry/RetryableTaskConsumer）、`messaging/`（MessageTemplateEngine 等）、`engine/cache/`（TwoLevelCache、ConfigCacheManager、CacheInvalidation*）、`sequence/`、`logging/OperationLogAspect`。
> 承接 [11 执行计划](11-followup-review-plan.md) 优先级 5（正确性 > 安全）。审查人 AI/architect · 2026-05-30。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B19 | 🔴 | `messaging/MessageTemplateEngine#render` | FreeMarker 直接 `Template(...templateContent...)` 处理用户模板，**未设 `TemplateClassResolver.SAFER_RESOLVER`/未禁 `?new`**→SSTI→RCE（模板经 SmsController CRUD 维护且无鉴权 [M22](09-file-sms-aigc.md)，可达） | 设 `setNewBuiltinClassResolver(SAFER_RESOLVER)` + 禁用 `?api`；或改用无逻辑模板引擎 |
| M47 | 🟠 | `task/DistributedLockAspect#around` | `finally { redisTemplate.delete(key) }` **无条件释放** + 锁值固定 `"1"`→任务超 ttl 后锁过期、他节点已持锁，本节点 finally 误删→互斥失效/重复执行 | 锁值用唯一 token，释放用 Lua 比对 token 后删（CAS 释放） |
| M48 | 🟠 | `task/queue/TaskConsumer#processMessage` + `retry/RetryableTaskConsumer` | 失败分支直接 `acknowledge` 且**不重试/不进 DLQ**（注释称交 RetryableTaskConsumer，但消费路径从未调用它）→失败异步任务**静默丢失**；RetryableTaskConsumer 为孤儿代码 | 消费失败走 `executeWithRetry`（退避重入队）或显式 `sendToDeadLetter`，勿先 ACK 再丢 |
| M49 | 🟠 | `task/queue/TaskConsumer` | 无消费幂等（按 task.id 去重）+ 无 pending 认领（XAUTOCLAIM）+ 固定 `CONSUMER_NAME="consumer-1"`→崩溃重投致重复执行、pending 永久滞留、多实例消费者名冲突 | handler 幂等键去重 + 定期 XAUTOCLAIM 回收 pending + 实例化 consumer 名 |
| M50 | 🟠 | `engine/cache/TwoLevelCache#invalidate` + `CacheInvalidationListener` + `ConfigCacheManager` | 失效仅清**本机** Caffeine + Redis；`CacheInvalidationEvent` 是 JVM 内 ApplicationEvent（非 Redis pub/sub）→其他实例本地缓存不失效，配置（model/agent/prompt）最长 `LOCAL_TTL=5min` 跨节点陈旧 | 失效经 Redis pub/sub 广播到所有实例本地缓存 |
| M51 | 🟠 | `logging/OperationLogAspect#publishEvent` | 审计记录 `params=Arrays.toString(args)` 与 `response=result.toString()`（截断 2000）**无脱敏**→密码/token/身份证等敏感数据入审计日志（同 M11 思路） | 敏感字段/参数掩码（按注解或字段名白/黑名单） |
| m30 | 🟡 | `engine/cache/TwoLevelCache#invalidateAll` | 用 `redisTemplate.keys(name+":*")`，生产环境 KEYS 阻塞 Redis | 改用 SCAN 或维护 key 集合 |
| m31 | 🟡 | `engine/cache/ConfigCacheManager#getSkillDef` | loader 恒 `k -> null`（无 Repository，占位）→skill 缓存永不命中 | 接入 SkillDefinition 持久化或移除该缓存 |
| m32 | 🟡 | `task/DistributedLockAspect#around` | 获取锁失败返回 `null`，对有返回值的方法语义不清（调用方无法区分"跳过"与"返回 null"） | 抛专用异常或文档约束仅用于 void/@Scheduled |

## 良好实践

- `SequenceService` 基于 Postgres `SEQUENCE` + `nextval` 并发安全，批量用 `generate_series`，按月分段；pg 序列名由内部 `seqId`（非用户输入）构造，无注入面。
- `RetryableTaskConsumer` 本身实现指数退避 + DLQ（设计正确，缺接线 M48）。
- `TaskConsumer` 虚拟线程池 + `@PreDestroy` 关闭 + 多 Stream 优先级轮询；`ensureGroups` 幂等建组（注册/注销对称）。
- `TwoLevelCache` 两级读写穿透 loader，Redis 读写异常降级不阻断主流程。
- `OperationLogAspect` 的 SpEL 模板来自**注解常量**（开发期，非用户输入），注入风险低。

## 对称性 / 一致性提示

- 资源申请 vs 释放（清单#6）：分布式锁释放非原子误删（M47）。
- 入队 vs 出队（清单#10）：失败消息不进 DLQ/重试（M48）、消费无幂等（M49）。
- 缓存写入 vs 失效（清单#11）：跨节点本地缓存不失效（M50）。
- 占位/重复（清单#13）：`getSkillDef` 恒 null（m31）。

## 待确认

- **Flyway clean 生产隔离**：`framework/flyway/` 无自定义配置类，需查 `application-prod.yaml` 的 `spring.flyway.clean-disabled=true`（未确认即潜在生产 clean 风险）。
- `intelligent/{agent,team,cognition}` 编排（CognitiveCycleExecutor/TaskBoard/TeamOrchestrator/AgentScheduler/agentscope）仅抽样，编排正确性与占位/重复待后续轮次逐读。
