---
level: Practice
layer: Model
purpose: AAF 轻量任务队列设计（PostgreSQL + Redis，零额外中间件）
status: draft
version: 1.0.0
date: 2026-05-08
author: AaronZZH
---

# 轻量任务队列设计

> 用 PostgreSQL 做持久化队列 + Redis 做实时通知，覆盖持久化/重试/死信/削峰/延迟执行，不引入 RabbitMQ/Kafka。

## 设计动机

AI 应用常见场景：

- LLM 调用失败需要重试（网络抖动、限流）
- 批量 Embedding 需要削峰（用户上传 100 篇文档，不能同时打满 API）
- Agent 长任务需要持久化（服务重启后恢复执行）
- 邮件/通知异步发送（不阻塞主流程）

这些场景的共同需求：**持久化 + 失败重试 + 并发控制**。传统方案用 RabbitMQ，但 v0.1.0 单体架构下引入 MQ 增加运维成本且收益有限。

## 架构概览

```
生产者（业务代码）
    │
    ├─① INSERT task_queue（PostgreSQL，持久化保证不丢）
    └─② PUBLISH task:notify:{type}（Redis Pub/Sub，实时唤醒消费者）

消费者（虚拟线程）
    │
    ├─ 正常路径：Redis SUBSCRIBE 收到通知 → 立即消费
    └─ 兜底路径：@Scheduled 每 5s 轮询（防 Redis 通知丢失 + 处理延迟任务）

    消费流程：
    SELECT ... FOR UPDATE SKIP LOCKED（原子抢占，并发安全）
        → 执行任务
            → 成功：status = SUCCESS
            → 失败：retry_count++, next_run_at = 指数退避
            → 超限：status = DEAD（死信）
```

## 为什么 PostgreSQL + Redis 而不是单用其一

| 职责 | PostgreSQL | Redis | 说明 |
|------|-----------|-------|------|
| 任务持久化 | ✅ | ❌ | 数据不丢，事务保证 |
| 失败重试/死信 | ✅ | ❌ | 状态机 + 重试计数 |
| 任务查询/统计 | ✅ | ❌ | SQL 灵活查询 |
| 实时通知消费者 | ❌（轮询有延迟） | ✅ | Pub/Sub 毫秒级唤醒 |
| 并发限流 | ❌ | ✅ | 令牌桶 / Semaphore |
| 幂等去重 | ❌（需额外查询） | ✅ | SET NX 快速判重 |

## 数据模型

```sql
CREATE TABLE task_queue (
    id            BIGSERIAL PRIMARY KEY,
    task_type     VARCHAR(50)  NOT NULL,          -- embedding / email / agent_call / notification
    task_key      VARCHAR(128),                   -- 幂等键（可选，防重复提交）
    payload       JSONB        NOT NULL,          -- 任务参数（JSON 格式）
    status        VARCHAR(20)  DEFAULT 'PENDING', -- PENDING → PROCESSING → SUCCESS / FAILED / DEAD
    priority      INT          DEFAULT 0,         -- 优先级（越大越先消费）
    retry_count   INT          DEFAULT 0,
    max_retries   INT          DEFAULT 3,
    next_run_at   TIMESTAMP    DEFAULT NOW(),     -- 延迟执行 / 退避重试时间
    timeout_sec   INT          DEFAULT 300,       -- 任务超时秒数
    error_message TEXT,                           -- 最后一次失败原因
    created_at    TIMESTAMP    DEFAULT NOW(),
    updated_at    TIMESTAMP    DEFAULT NOW()
);

-- 消费查询索引（覆盖 claimNext 查询条件）
CREATE INDEX idx_task_poll ON task_queue (status, task_type, next_run_at, priority DESC);

-- 幂等键唯一索引（可选）
CREATE UNIQUE INDEX idx_task_key ON task_queue (task_key) WHERE task_key IS NOT NULL;
```

### 状态机

```
PENDING → PROCESSING → SUCCESS
                    ↘ FAILED → (retry) → PENDING
                              ↘ (exhausted) → DEAD
```

## 核心实现

### 抢占查询（并发安全）

```sql
UPDATE task_queue SET status = 'PROCESSING', updated_at = NOW()
WHERE id = (
    SELECT id FROM task_queue
    WHERE status = 'PENDING'
      AND (task_type = :type OR :type IS NULL)
      AND next_run_at <= NOW()
    ORDER BY priority DESC, created_at ASC
    FOR UPDATE SKIP LOCKED
    LIMIT 1
)
RETURNING *;
```

关键点：`FOR UPDATE SKIP LOCKED` 是 PostgreSQL 原生支持的并发安全消费机制，多个消费者不会重复抢占同一条任务。

### 消费者

```java
@Component
public class TaskQueueConsumer {

    private final TaskQueueRepository repo;
    private final Map<String, TaskHandler> handlers;
    private final Semaphore concurrency = new Semaphore(20);  // 最大并发消费数

    // 实时路径：Redis 通知触发
    @RedisListener(channel = "task:notify:*")
    public void onNotify(String taskType) {
        consumeNext(taskType);
    }

    // 兜底路径：定时轮询（处理延迟任务 + 超时恢复 + 防通知丢失）
    @Scheduled(fixedDelay = 5000)
    public void poll() {
        recoverTimeoutTasks();
        consumeNext(null);
    }

    private void consumeNext(String taskType) {
        concurrency.acquire();  // 虚拟线程挂起等待，不占 OS 线程
        try {
            var task = repo.claimNext(taskType);
            if (task == null) return;

            try {
                handlers.get(task.getTaskType()).execute(task.getPayload());
                task.markSuccess();
            } catch (Exception e) {
                handleFailure(task, e);
            }
        } finally {
            concurrency.release();
        }
    }

    private void handleFailure(Task task, Exception e) {
        task.setErrorMessage(e.getMessage());
        if (task.getRetryCount() >= task.getMaxRetries()) {
            task.markDead();
            // TODO: 告警通知
        } else {
            // 指数退避：2s, 4s, 8s, 16s...
            var backoff = Duration.ofSeconds((long) Math.pow(2, task.getRetryCount() + 1));
            task.scheduleRetry(backoff);
        }
    }

    // 超时恢复：PROCESSING 超过 timeout_sec 的任务重置为 PENDING
    private void recoverTimeoutTasks() {
        repo.resetTimedOut();
    }
}
```

### 生产者 API

```java
@Service
public class TaskQueueService {

    private final TaskQueueRepository repo;
    private final StringRedisTemplate redis;

    /** 简单提交 */
    public Long submit(String type, Object payload) {
        return submit(type, payload, TaskOptions.defaults());
    }

    /** 带选项提交 */
    public Long submit(String type, Object payload, TaskOptions options) {
        // 幂等检查
        if (options.idempotentKey() != null) {
            var key = "task:dedup:" + options.idempotentKey();
            if (Boolean.FALSE.equals(redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(5)))) {
                return null;  // 重复提交，跳过
            }
        }

        // 持久化
        var task = Task.builder()
            .taskType(type)
            .taskKey(options.idempotentKey())
            .payload(toJson(payload))
            .priority(options.priority())
            .maxRetries(options.maxRetries())
            .nextRunAt(Instant.now().plus(options.delay()))
            .timeoutSec(options.timeoutSec())
            .build();
        repo.save(task);

        // 实时通知消费者
        redis.convertAndSend("task:notify:" + type, task.getId().toString());
        return task.getId();
    }
}
```

### 任务选项

```java
public record TaskOptions(
    int priority,          // 默认 0，越大越先消费
    int maxRetries,        // 默认 3
    Duration delay,        // 默认 Duration.ZERO（立即执行）
    int timeoutSec,        // 默认 300
    String idempotentKey   // 默认 null（不去重）
) {
    public static TaskOptions defaults() {
        return new TaskOptions(0, 3, Duration.ZERO, 300, null);
    }

    public static Builder builder() { return new Builder(); }
    // Builder 省略...
}
```

### 任务处理器接口

```java
@FunctionalInterface
public interface TaskHandler {
    void execute(JsonNode payload) throws Exception;
}

// 注册示例
@Component("embedding")
public class EmbeddingTaskHandler implements TaskHandler {
    @Override
    public void execute(JsonNode payload) {
        var docId = payload.get("docId").asLong();
        var content = payload.get("content").asString();
        embeddingService.embed(docId, content);
    }
}
```

## 业务使用示例

```java
// 批量 Embedding（削峰：消费者 Semaphore=20，不会打满 OpenAI API）
taskQueue.submit("embedding", new EmbeddingPayload(docId, content));

// 邮件发送（失败重试 3 次，指数退避）
taskQueue.submit("email", new EmailPayload(to, subject, body),
    TaskOptions.builder().maxRetries(3).build());

// Agent 调用（高优先级 + 幂等去重）
taskQueue.submit("agent_call", agentPayload,
    TaskOptions.builder().priority(10).idempotentKey("agent:" + sessionId).build());

// 定时提醒（延迟 30 分钟执行）
taskQueue.submit("reminder", reminderPayload,
    TaskOptions.builder().delay(Duration.ofMinutes(30)).build());

// 批量导入（低优先级，超时 10 分钟）
taskQueue.submit("import", importPayload,
    TaskOptions.builder().priority(-1).timeoutSec(600).build());
```

## 监控接口

```java
// 框架内置管理 API
GET  /api/admin/tasks?status=DEAD&type=embedding   // 查询死信任务
GET  /api/admin/tasks/stats                         // 各状态/类型计数
POST /api/admin/tasks/{id}/retry                    // 手动重试死信
POST /api/admin/tasks/{id}/cancel                   // 取消待执行任务
```

## 并发控制策略

| 控制点 | 机制 | 配置 |
|--------|------|------|
| 全局消费并发 | Java Semaphore | `task.queue.max-concurrency=20` |
| 按类型限流 | 每个 type 独立 Semaphore | `task.queue.types.embedding.concurrency=5` |
| 外部 API 限流 | Resilience4j RateLimiter | 在 TaskHandler 内使用 |
| DB 连接保护 | HikariCP 连接池 | `maximumPoolSize=50` |

## 引入 RabbitMQ 的触发条件

当前方案满足 v0.1.0 需求。出现以下信号时升级为 MQ：

1. **拆微服务**：需要跨进程/跨服务投递任务
2. **复杂路由**：需要 topic/fanout/header 等路由模式
3. **吞吐量瓶颈**：PostgreSQL 任务表 TPS > 5000 成为瓶颈
4. **流式处理**：需要百万级事件流（此时选 Kafka 而非 RabbitMQ）

升级路径：`TaskQueueService` 接口不变，底层实现从 PostgreSQL 切换为 RabbitMQ，业务代码零改动。

## 所在模块

```
aaf-framework/
  └── engine/
      └── task/
          ├── TaskQueueService.java       // 生产者 API
          ├── TaskQueueConsumer.java       // 消费者
          ├── TaskHandler.java            // 处理器接口
          ├── Task.java                   // 实体
          ├── TaskOptions.java            // 提交选项
          ├── TaskQueueRepository.java    // 数据访问
          └── TaskQueueProperties.java    // 配置属性
```
