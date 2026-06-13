---
title: 任务调度引擎设计
category: design
layer: framework/engine
status: draft
date: 2026-06-13
author: AaronZZH & Kiro
---

# 任务调度引擎

AAF 统一任务调度框架，覆盖定时任务、异步队列、重试、监控四大能力。

## 设计定位

```
任务调度（本框架）          元引擎 runtime
─────────────────          ──────────────────
后台异步任务                编排执行引擎
无交互，fire-and-forget     有交互，可暂停等人确认
类似 Celery / Spring Batch  类似 Flowable Engine
```

两者是调用关系：元引擎执行编排时，可将长时间子任务委托给本框架异步执行。

## 核心表关系

```
sys_scheduled_task（任务配置）
  └─ 1:N → sys_task_execution（执行日志）
              每次触发写一条，保留 90 天后自动清理

sys_scheduled_task（触发入口）
  └─ 触发 → 各业务实例表
              aigc_task（图像/视频生成实例）
              pay_order（支付订单）
              sys_workflow_instance（Flowable 流程）
              ...
```

### 各表职责

| 表 | 职责 | 增长速度 | 清理策略 |
|---|---|---|---|
| `sys_scheduled_task` | 任务配置（做什么、何时做） | 极少，人工维护 | 不清理 |
| `sys_task_execution` | 每次触发的执行日志 | 高（每次触发一条） | 保留 N 天自动清理（默认90天） |
| `sys_holiday_calendar` | 排除日历配置 | 极少 | 不清理 |
| `aigc_task` 等业务表 | 业务实例（用户操作产生） | 中 | 用户删除/归档 |

## 触发类型

| trigger_type | 说明 | 适用场景 |
|---|---|---|
| `CRON` | Cron 表达式 | 低频定时业务任务（每周发积分、每天报告） |
| `FIXED_DELAY` | 上次结束后等待 N ms | 高频轮询（图片状态同步 10s） |
| `FIXED_RATE` | 固定周期，不等上次结束 | 心跳检测等对时间精度要求高的场景 |

## 动作类型（action_type）

| action_type | actionConfig 示例 | 说明 |
|---|---|---|
| `NOTIFY` | `{"userId":1,"title":"标题","content":"内容"}` | 发送系统通知 |
| `WEBHOOK` | `{"url":"https://...","method":"POST","body":"..."}` | 调用外部 HTTP |
| `WORKFLOW` | `{"processKey":"my-flow","variables":{"userId":1}}` | 触发 Flowable 工作流 |

> TODO：`AGENT` 类型——直接派发 Agent 执行，待 AAF-021 元引擎就绪后扩展。

## 调度模式

### 模式 A：工作流内部编排（一次调度，步骤有依赖）

```
调度器 09:00 触发一次
  → WorkflowActionExecutor 启动 Flowable 流程实例
      ├── ServiceTask：步骤1（收集数据）→ 完成后
      ├── ServiceTask：步骤2（分析数据）→ 完成后
      └── ServiceTask：步骤3（发送报告）→ 流程结束
```

适合步骤间有数据依赖、整体在分钟级内完成的场景。

### 模式 B：步骤独立调度

在 `sys_scheduled_task` 建多条记录，各自独立 Cron，步骤间无依赖。

### 模式 C：调度触发 + 工作流内定时等待（混合）

```
调度器 09:00 触发一次
  → Flowable 启动流程
      ├── ServiceTask：步骤1 立即执行
      ├── TimerBoundaryEvent：等待到 10:00
      ├── ServiceTask：步骤2
      ├── TimerBoundaryEvent：等待到 11:00
      └── ServiceTask：步骤3
```

Flowable 原生支持 Timer Boundary Event，`sys_scheduled_task` 只需一条配置。

步骤级执行历史查询：
```sql
SELECT act_name_, start_time_, end_time_, duration_
FROM act_hi_actinst
WHERE proc_inst_id_ = :instanceId
ORDER BY start_time_;
```

## 可靠性机制

### 分布式锁（防集群重复执行）

每次执行前通过 Redis `SET NX` 获取锁，执行完释放。同一时刻集群内只有一个节点执行。

```
节点A 获取锁 task_lock:image_sync → 执行
节点B 获取锁失败 → 跳过
```

### Misfire 补偿（重启后补跑）

`sys_scheduled_task.last_run` 记录最后成功执行时间（持久化到 DB，不依赖 Redis）。启动时检查：若 `last_run` 之后本应触发但未触发，按 `misfire_policy` 决定是否补跑。

| misfire_policy | 行为 | 适用 |
|---|---|---|
| `IGNORE` | 跳过，等下次正常触发 | 高频轮询（image_sync） |
| `RUN_ONCE` | 补跑一次 | 重要低频任务（积分发放、订阅结算） |

### 排除日历

`sys_scheduled_task.calendar_code` 关联 `sys_holiday_calendar`，执行前判断当天是否在排除日期内。

支持自定义任意日历编码（`CN_HOLIDAY`/`COMPANY_OFFDAY`/`PROJECT_FREEZE` 等），`WORKDAY` 类型可覆盖 `HOLIDAY`（调休补班场景）。

### 连续失败自动暂停

连续失败 3 次（可配置）自动将任务状态改为 `failed`，并发送系统通知给管理员。

## 架构分层

```
aaf-framework/task/
  ├── TaskDefinition          任务定义（名称/触发类型/间隔/misfire 策略）
  ├── TriggerType             触发类型枚举（CRON/FIXED_DELAY/FIXED_RATE）
  ├── MisfirePolicy           补偿策略枚举（IGNORE/RUN_ONCE）
  ├── TaskRegistry            内存注册表（动态暂停/恢复）
  ├── TaskPersistencePort     持久化接口（framework 定义，api 实现）
  ├── ScheduledTaskExecutor   调度执行器（加锁→执行→回写 last_run）
  ├── TaskMonitor             执行日志（recordStart/recordSuccess/recordFailure）
  ├── queue/                  Redis Stream 异步队列
  └── retry/                  重试机制（指数退避）

aaf-api/module/system/task/
  ├── domain/ScheduledTask    任务实体（持久化到 sys_scheduled_task）
  ├── service/DbTaskPersistencePort  TaskPersistencePort 实现
  ├── service/ScheduledTaskService   业务逻辑（CRUD + 执行动作）
  ├── service/HolidayCalendarService 排除日历服务
  ├── action/
  │   ├── ScheduledActionExecutor    动作执行器接口
  │   ├── NotifyActionExecutor       发送通知
  │   ├── WebhookActionExecutor      调用外部 HTTP
  │   └── WorkflowActionExecutor     触发 Flowable 工作流
  └── controller/
      ├── ScheduledTaskController    任务管理 API
      └── HolidayCalendarController  排除日历管理 API
```

## 异步任务队列

### 适用场景

把**同步方法**异步化，调用方立即返回，任务在后台消费：

```java
// 原来：同步调用（阻塞等待）
imageService.generateImage(prompt);  // 可能耗时30秒

// 改造后：入队立即返回
taskQueue.enqueue(new AsyncTaskMessage("IMAGE_GENERATE",
    """{"prompt":"a cat","userId":1}"""));
// 调用方立即返回，消费者在后台处理
```

### 使用方式

**1. 定义 Handler（消费者）**

```java
@Component
public class ImageGenerateHandler implements TaskHandler {

    @Override
    public String taskType() { return "IMAGE_GENERATE"; }

    @Override
    public void handle(AsyncTaskMessage task) {
        var dto = parse(task.payload()); // 解析 JSON
        imageService.generateImage(dto.prompt(), dto.userId());
    }
}
```

**2. 入队（生产者）**

```java
@RequiredArgsConstructor
public class ImageController {
    private final TaskQueue taskQueue;

    public void submit(String prompt, Long userId) {
        // 普通优先级（0-9，0最高）
        taskQueue.enqueue(new AsyncTaskMessage("IMAGE_GENERATE",
                toJson(Map.of("prompt", prompt, "userId", userId))));

        // 高优先级（priority=1）
        taskQueue.enqueue(new AsyncTaskMessage("IMAGE_GENERATE", payload, 1));

        // 延迟5分钟入队
        taskQueue.enqueueWithDelay(msg, Duration.ofMinutes(5));
    }
}
```

### 队列优先级

```
task_queue:high   (priority 0-2) ← 优先消费
task_queue:normal (priority 3-6)
task_queue:low    (priority 7-9)
task_queue:dead               ← 超过最大重试次数后转入死信
```

### 与定时任务的区别

| | 定时任务（ScheduledTask） | 异步队列（TaskQueue） |
|---|---|---|
| 触发方式 | 时间到了自动触发 | 业务代码主动入队 |
| 典型场景 | 每周发积分、每天报告 | 图片生成、发邮件、耗时计算 |
| 执行时机 | 固定时间点 | 尽快执行（FIFO + 优先级） |
| 重试 | misfire 补偿 | 指数退避重试 + 死信队列 |

## 线程模型

### 技术决策：统一使用虚拟线程

AAF 基于 Java 25 + Spring Boot 4，全局启用虚拟线程：

```yaml
# application.yaml（已配置）
spring:
  threads:
    virtual:
      enabled: true  # Tomcat 请求线程 + @Async 全部走虚拟线程
```

**`TaskConsumer` 消费者**同样使用虚拟线程池：

```java
executor = Executors.newFixedThreadPool(
    threads, r -> Thread.ofVirtual().name("task-consumer").unstarted(r));
```

### 并发模型分层

AAF 采用**分层并发模型**，不同层次用不同方案，互不干扰：

```
┌─────────────────────────────────────────────────┐
│ 控制器层（WebFlux）                               │
│   Reactor event loop（Mono/Flux）                │
│   非阻塞，处理 HTTP 请求/SSE/WebSocket           │
└──────────────┬──────────────────────────────────┘
               │ 切换到虚拟线程
┌──────────────▼──────────────────────────────────┐
│ 业务层 + 任务层（虚拟线程）                        │
│   同步写法，阻塞调 DB/Redis/第三方 API            │
│   虚拟线程自动挂起，不阻塞 event loop             │
│   TaskConsumer / AigcTaskHandler / Service 层    │
└─────────────────────────────────────────────────┘
```

### 选择理由

图像生成、AI 调用、第三方 API 均为 **IO 密集型**，虚拟线程相比平台线程：

| | 平台线程（@Async 默认） | 虚拟线程 |
|---|---|---|
| 内存占用 | ~1MB/线程 | ~几KB/线程 |
| 并发上限 | 受线程池大小限制（默认8） | 百万级 |
| IO 等待 | 阻塞整个线程 | 自动挂起，不占 CPU |
| 写法 | 同步 | 同步（无需改代码） |
| 迁移成本 | — | 一行配置 |

### 为什么不用 Reactor 全面响应式

Reactor（Mono/Flux）是 WebFlux 控制器层的运行时，任务层**不引入**：

- 业务逻辑用响应式写法可读性差，调试堆栈不直观
- `@Transactional` 基于 ThreadLocal，响应式切线程后事务上下文丢失
- 虚拟线程 + 同步写法已达到接近响应式的性能，无需额外复杂度
- Spring Boot 4 官方推荐方向：WebFlux event loop + 虚拟线程业务层

### 为什么不引入 Mutiny

Mutiny 是 Quarkus 生态的响应式库，Spring 生态已有 Reactor：

- 两套响应式 API 并存即双真理源
- 与 Spring 事务、AOP 整合需额外处理
- 虚拟线程已解决 IO 密集型场景，Mutiny 带来复杂度但无额外收益

> 如需响应式组合（并发调多个第三方 API），在局部使用 Reactor `Mono.zip()` 或虚拟线程 `Future.get()` 均可，不引入第三方响应式库。

### 当前混用情况（迁移前）

| 位置 | 线程类型 | 状态 |
|---|---|---|
| `TaskConsumer`（队列消费者） | 虚拟线程 ✅ | 已正确 |
| `TaskConsumer` 消费线程数配置 | `aaf.task.queue.consumer-threads=2` | 可按需调整 |
| `@Async`（AigcTaskExecutor 等） | 虚拟线程 ✅ | 已由全局配置覆盖 |
| `Thread.startVirtualThread()` 裸启动 | 虚拟线程 ✅ | 已正确，但缺监控 |

阶段二迁移完成后，`@Async` 和裸 `Thread.startVirtualThread()` 统一走 `TaskQueue`，线程模型完全统一。

## 扩展点

- **新增动作类型**：实现 `ScheduledActionExecutor` 接口，标注 `@Component` 即自动注册
- **新增系统任务**：在 `DbTaskPersistencePort.BUILTIN_BEAN_MAP` 注册 Bean 名，或直接向 DB 插入配置
- **Agent 动作**（TODO）：待 AAF-021 元引擎就绪后，加 `AgentActionExecutor`，`actionConfig` 包含 `agentId` 字段

## 与 Quartz 对比

| 能力 | Quartz | 本框架 |
|---|---|---|
| 分布式锁 | 数据库锁（11张表） | Redis 锁（零额外表） |
| 动态管理 | ✅ | ✅ |
| 执行历史 | ❌（需自建） | ✅ `sys_task_execution` |
| 异步队列 | ❌ | ✅ Redis Stream |
| Misfire 补偿 | ✅ 多策略 | ✅ IGNORE/RUN_ONCE |
| 日历排除 | ✅ | ✅ 自定义日历编码 |
| 集群故障转移 | ✅ 自动 | ❌（Redis 锁只防重复） |
| 运维复杂度 | 高 | 低（依赖已有 Redis） |


## 迁移计划

将现有散落的 `@Scheduled` 和 `@Async` 任务统一迁移到本框架，分三个阶段。

### 阶段一：定时任务迁移（低成本，优先）

去掉 `@Scheduled` 注解，改为向 `sys_scheduled_task` 注册配置，获得分布式锁 + 执行监控 + misfire 补偿。

| 任务类 | 当前方式 | 迁移类型 | misfire_policy | 备注 |
|---|---|---|---|---|
| `WeeklyCreditScheduler` | `@Scheduled cron` | CRON | RUN_ONCE | 积分发放不能漏 |
| `SubscriptionCreditScheduler` | `@Scheduled cron` | CRON | RUN_ONCE | 同上 |
| `CreditExpireScheduler` | `@Scheduled cron` | CRON | RUN_ONCE | 同上 |
| `PayOrderExpireTask` | `@Scheduled fixedDelay` | FIXED_DELAY | IGNORE | 高频，漏一次无妨 |
| `PayOrderSyncTask` | `@Scheduled fixedDelay` | FIXED_DELAY | IGNORE | 同上 |
| `ImageSyncJob` | `@Scheduled fixedDelay` | FIXED_DELAY | IGNORE | 同上 |
| `MemoryMaintenanceTask` | `@Scheduled` | CRON | IGNORE | |

迁移方式：在 `sys_scheduled_task` 插入配置记录 + 对应类实现 `Runnable` 接口（大多已实现）。

### 阶段二：异步任务迁移（获得持久化 + 统一监控）

将 `@Async` 调用改为 `TaskQueue.enqueue()`，消费者实现 `TaskHandler` 接口。

| 任务 | 当前方式 | 迁移后 | 收益 |
|---|---|---|---|
| `AigcTaskExecutor.submitSync()` | `@Async` 直接调第三方 | `TaskQueue` + `AigcTaskHandler` | 重启不丢任务、执行日志、优先级 |
| `AsyncTaskExecutor.execute()` | `@Async` 通用执行器 | 统一走 `TaskQueue` | 可观测 |

迁移后在 `TaskMonitor.recordStart()` 传入 `bizId`（如 `aigc_task.id`）和 `context` 快照，`sys_task_execution` 即可作为统一监控视图，不需要去各业务表关联查询。

### 阶段三：启动恢复（防止重启丢任务）

在服务启动时扫描业务表中状态卡住的任务，重新入队：

```java
@EventListener(ApplicationReadyEvent.class)
void recoverStuckTasks() {
    // 找出 RUNNING 超过 10 分钟的孤儿任务（服务重启导致）
    aigcTaskRepo.findByStatusAndUpdateTimeBefore("RUNNING", LocalDateTime.now().minusMinutes(10))
        .forEach(task -> taskQueue.enqueue(
            new AsyncTaskMessage("IMAGE_GENERATE", toJson(task), 1))); // 高优先级
}
```

此时持久化保障链完整：

```
aigc_task (DB，主状态) → Redis Stream (队列传输) → 消费者执行 → sys_task_execution (日志)
                ↑
         启动时恢复孤儿任务（Redis 挂了也能恢复）
```
