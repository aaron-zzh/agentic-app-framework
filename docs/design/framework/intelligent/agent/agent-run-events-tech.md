---
level: Practice
layer: Model
purpose: Agent 运行状态推送的逻辑与技术实现——统一事件模型、发布-传输解耦、跨线程上下文传播与 SSE 桥接
status: draft
version: 1.0.0
date: 2026-06-01
author: AaronZZH & Kiro
---

# Agent 运行状态推送

> 上下文压缩由 AgentScope 内置的 AutoContextMemory 处理，不再通过本机制推送事件。

## 解决的问题

一次 Agent 运行内部会发生很多对用户有意义、但不属于「对话内容」的事件：运行开始/结束/出错、工具调用、子 Agent 调度、协调决策、上下文压缩。这些过程信息原先散落在各处日志里，前端无从感知。本机制把它们收敛成一种统一的 `AgentRunEvent`，与原有的 AG-UI 内容流**并存于同一条 SSE 连接**，作为命名事件 `agent-run` 推给前端，实现运行过程的可视化。

## 第一性原理

- **发布与传输解耦**：领域代码（控制器、调度器、压缩器）只负责「发生了什么」，不关心「怎么送到前端」。两者通过 Spring 应用事件总线解耦——发布方调 `publish`，传输方 `@EventListener` 监听，互不依赖。
- **模型无关、与内容流正交**：`agent-run` 是过程事件，AG-UI `data` 是内容事件。两者用不同 SSE 事件名走同一连接，互不污染，前端可独立消费。
- **runId 是唯一关联键**：事件靠 `runId` 路由到对应连接。任何阶段、任何线程产生的事件，只要带对 `runId`，就能落到正确的 SSE 通道。
- **可丢不可错**：过程事件是增强信息，没有订阅者时直接丢弃（不报错、不阻塞主流程）；但一旦要送，就必须送到正确连接、不串写、不串连接。

## 统一事件模型

`AgentRunEvent`（record）：

```java
record AgentRunEvent(
    String runId, Long userId, String agentId,
    AgentRunEventType type, String title, String message,
    Map<String,Object> payload, Instant timestamp)
```

`AgentRunEventType` 覆盖运行全生命周期的 11 种类型：

```text
RUN_STARTED / RUN_FINISHED / RUN_ERROR
TOOL_CALL_STARTED / TOOL_CALL_COMPLETED / TOOL_CALL_FAILED
ROLE_SWITCHED
SUB_AGENT_STARTED / SUB_AGENT_COMPLETED
COORDINATION_STARTED / COORDINATION_DECISION
```

`payload` 经 `Map.copyOf` 做不可变快照，`timestamp` 在构造时落定。

## 运行上下文与两种发布方式

`AgentRunContext(runId, userId, agentId)` 描述「当前在哪次运行里」。`AgentRunContextHolder` 用 `ThreadLocal` 在当前线程内携带它，`open(...)` 返回 `AutoCloseable` 的 `Scope`，配合 try-with-resources 进出。

`AgentRunEventPublisher` 提供两种重载：

```java
// 隐式：从 ThreadLocal 取当前上下文（仅在 open 作用域、同线程内可靠）
publish(type, title, message, payload)

// 显式：直接带上下文（跨线程、reactive 回调、并行任务里使用）
publish(AgentRunContext, type, title, message, payload)
```

### 跨线程边界的上下文传播（关键）

`ThreadLocal` 不会跨线程。一旦发布点落在「开启 `open` 作用域的那个线程」之外，隐式 `current()` 就是空的，事件会被静默丢弃。两个典型边界必须用显式上下文：

- **reactive 回调**：`flux.subscribe()` 非阻塞返回后 try-with-resources 立即关闭，`doOnComplete` / `doOnError` 稍后在 reactor 线程触发，此时 ThreadLocal 已清空。
- **并行流**：`parallelStream()` 的任务跑在 ForkJoinPool 线程上，拿不到调用线程的 ThreadLocal。

对应处理：

- `ChatController.streamChat` 的 `doOnComplete`(RUN_FINISHED) / `doOnError`(RUN_ERROR) 改用显式 `new AgentRunContext(runId, userId, null)`；否则流式完成/出错事件永远到不了前端。
- `AgentDispatcher.dispatchMultiple` 在进入 `parallelStream` 前捕获 `current()`，在每个并行任务内用 `open(...)` 重建作用域（`dispatchWithContext`），使子 Agent 事件不丢。
- `AgUiStreamHandler` 全程在 reactive 链里，所有发布都显式带 `AgentRunContext`。

**经验法则**：同步、在 `open` 作用域内的发布用隐式；进入 reactive/并行/线程池的发布一律显式带 `runId`。

## 传输桥

```text
领域代码 ──publish──▶ ApplicationEventPublisher
                              │（Spring 同步多播）
                              ▼
                  AgentRunEventStreamService  @EventListener
                              │ 按 runId 找已注册的 SseEmitter
                              ▼
        event: agent-run\ndata: {AgentRunEvent JSON}\n\n  ──▶ 前端
```

`AgentRunEventStreamService` 维护 `runId → List<SseEmitter>`：

- `attach(runId, emitter)`：入口创建 SSE 时注册；并挂 `onCompletion/onTimeout/onError` 回调自动 `detach`，防止泄漏。
- `onAgentRunEvent(AgentRunEvent)`：监听事件，按 `runId` 找连接；无连接则直接返回（可丢不可错）。
- 发送：`SseEmitter.event().name("agent-run").data(json)`，命名事件与 AG-UI 默认 `data` 事件区分。

### SSE 写串行化

一个 `SseEmitter` 可能被多个线程写（内容流在 reactor 线程、过程事件可能在另一线程）。HTTP 响应流不允许并发写，因此所有写入点都在 `synchronized (emitter)` 下串行化——`AgentRunEventStreamService.send`、`AgUiStreamHandler.sendEvent`、`ChatController.sendEvent` 共用同一把锁（emitter 自身），保证帧不交错。

## 入口接线

统一事件桥已落到三条现有流入口，模式一致：创建 `runId` → `attach` → `open` 作用域 → 发 `RUN_STARTED`，异常发 `RUN_ERROR`：

| 入口 | 端点 | RUN_FINISHED 来源 |
|------|------|------|
| `ChatController.streamChat` | `/api/system/chat/sessions/{id}/stream` | 自身 reactive 回调（显式上下文） |
| `AiChatHandler.handle` | AI run handler | `AgUiStreamHandler`（显式上下文） |
| `AgUiChatController.run` | `/api/chat/agent/run` | `AgUiStreamHandler`（显式上下文） |

埋点分布：

| 发布点 | 事件类型 |
|------|------|
| 三入口 | RUN_STARTED / RUN_ERROR |
| `AgUiStreamHandler` | TOOL_CALL_STARTED/COMPLETED、RUN_FINISHED、RUN_ERROR |
| `AgentScopeToolGovernanceService` | TOOL_CALL_STARTED/COMPLETED/FAILED |
| `AgentDispatcher` | SUB_AGENT_STARTED/COMPLETED、COORDINATION_STARTED/DECISION |

## 前端消费契约

同一连接上前端会收到两类 SSE 事件：

```text
data: {AG-UI 内容事件 JSON}          ← 默认事件，原有 AG-UI 协议

event: agent-run
data: {AgentRunEvent JSON}           ← 命名事件，运行过程
```

消费要点：必须按事件名分流。用 `EventSource` 时 `addEventListener("agent-run", ...)` 单独挂；用 fetch 流式解析时必须解析 `event:` 行而非只读 `data:` 行。

> 现状提示：前端尚无 `agent-run` 消费方。现有共享解析器 `lib/utils/sse.ts` 与 livechat 的 `@ag-ui/client` 仅处理 AG-UI 内容流，不识别命名事件。要让运行过程可视化生效，需新增一个能按事件名分流的消费层——属于后续前端任务。

## 关键类清单

| 类 | 模块 | 职责 |
|------|------|------|
| `AgentRunEvent` / `AgentRunEventType` | aaf-framework | 统一事件模型 / 类型枚举 |
| `AgentRunContext` / `AgentRunContextHolder` | aaf-framework | 运行上下文 / ThreadLocal 持有器 |
| `AgentRunEventPublisher` | aaf-framework | 发布器（隐式/显式两重载） |
| `AgentRunEventStreamService` | aaf-api | 事件→SSE 命名事件 `agent-run` 桥接 + 连接管理 |
| `ChatController` / `AiChatHandler` / `AgUiChatController` | aaf-api | 三入口接线 |
| `AgUiStreamHandler` | aaf-api | reactive 流内埋点（显式上下文） |

## 已知局限与后续

- 前端 `agent-run` 消费方待补（见上）。
- AgentScope 运行时链路（`AgentScopeToolGovernanceService` 等）的工具事件仍用隐式 `current()`，其能否送达取决于 agent 执行线程是否持有上下文；若该链路也需稳定推送，应同样改为显式传播或在运行时入口统一注入上下文。
- 事件多播为同步模式，发布发生在调用线程；大量高频事件下可考虑异步多播 + 背压，避免拖慢主流程。
