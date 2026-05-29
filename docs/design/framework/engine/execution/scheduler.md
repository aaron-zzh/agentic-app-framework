---
level: Practice
layer: Model
purpose: 调度引擎设计——异步任务队列、定时触发、重试策略、分布式锁
status: draft
version: 0.1.0
date: 2026-05-20
author: AaronZZH
changelog:
  - 2026-05-20 v0.1.0 | 从编排引擎拆分独立
---

# 调度引擎设计

> 调度引擎负责异步任务的队列管理、定时触发和可靠执行，不涉及执行路径决策（那是编排引擎的职责）。

## 与编排引擎的分工

```text
调度引擎（本文档）              编排引擎
────────────────────────────────────────────
异步任务入队/出队               单次请求执行路径决策
定时/周期触发                   引擎协同与路由
重试策略、死信队列               置信度门控
分布式锁防重复执行               响应式执行管道
```

## 核心组件

### 任务队列

基于 Redis Stream 实现优先级队列：

| 组件 | 职责 |
|------|------|
| `RedisStreamTaskQueue` | 任务入队/出队，支持优先级（高/中/低三档） |
| `TaskConsumer` | 消费者轮询，消息确认，消费组管理 |
| `TaskHandlerRegistry` | 任务类型 → 处理器映射 |
| `RetryableTaskConsumer` | 失败重试，死信队列处理 |

### 定时调度

| 组件 | 职责 |
|------|------|
| `ScheduledTaskExecutor` | 注册/取消/触发定时任务 |
| `TaskRegistry` | 任务定义注册表，支持暂停/恢复 |
| `TaskMonitor` | 执行记录、成功/失败统计、超时检测 |
| `DistributedLockAspect` | 分布式锁，防止多实例重复执行 |

### 重试策略

```text
RetryPolicy（指数退避）
  initialDelay × multiplier^attempt，上限 maxDelay
  超过 maxRetries → 发送死信队列 → 告警
```

## 任务类型

| 类型 | 触发方式 | 典型场景 |
|------|---------|---------|
| 即时异步任务 | 业务代码入队 | 知识库文档处理、邮件发送 |
| 定时任务 | Cron 表达式 | 记忆遗忘清理、积分结算 |
| 延迟任务 | 指定延迟时间 | 超时提醒、重试补偿 |
| 优先级任务 | 高/中/低三档队列 | 用户实时请求 > 后台批处理 |

## 相关文档

- [编排引擎](../meta/orchestration.md)
- [监控引擎](../governance/monitor.md)
