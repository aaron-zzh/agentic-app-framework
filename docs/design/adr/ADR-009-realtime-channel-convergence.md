---
level: Practice
layer: Principle
purpose: 明确 AAF 三类实时通道的边界，并把分散的 SSE 推送收敛为一条带游标补发的全局事件通道
status: accepted
version: 1.0.0
date: 2026-10-14
author: AaronZZH & Kiro
changelog:
  - 2026-10-14 | 1.0.0 | 初稿：AAF-119 实时通道收敛
---

# ADR-009: 实时通道收敛为 WebSocket、全局事件 SSE 与 AG-UI Run SSE

```yaml
status: accepted
date: 2026-10-14
deciders: [协调者]
consulted: []
informed: []
related-tasks: [AAF-119 / #11911]
```

## Context and Problem Statement

v0.13 冻结了"AG-UI Run SSE + 用户级 Activity SSE"双通道，落地后核对代码发现推送路径已经分散：

- Activity SSE 的端点、表、信封与前端 Provider 都带 AIGC 前缀，信封写死 AIGC 关联列，其他模块无法复用，且只挂在 Studio 布局。
- 异步任务状态经 `SseSessionManager` 推送：仅内存、无补发、多实例会丢，前端每次操作新建一条 `EventSource`。
- 通知同时走 WebSocket 与 `SseSessionManager` 两条传输，SSE 分支实际无人监听。
- 前端残留指向无后端端点的导出进度 Hook 与重复的通知 WebSocket Hook。

继续按模块各建通道会让连接数、补发语义与连接生命周期代码持续重复，需要现在划定边界。

## Decision Drivers

- AAF 未发布，不保留兼容层和并行抽象
- 事件只做变化通知，权威数据由 TanStack Query 重新查询
- 断线、重启后必须能补发，不能只靠内存
- 新模块接入成本应只是"发布事件 + 订阅前缀"

## Considered Options

- 保持现状，各模块自建 SSE
- 全部合并到 WebSocket
- 收敛为三类通道，SSE 变化通知统一为一条带游标补发的全局通道

## Decision Outcome

**Chosen option**: "三类通道，全局事件 SSE 统一变化通知"，理由：WebSocket 已稳定承载双向与二进制场景，AG-UI Run 是单次流式会话，其余服务端到用户的变化通知量大、单向、需要补发，适合一条落库的 SSE。

| 通道 | 职责 | 生命周期 |
|------|------|----------|
| WebSocket | 通知、IM/客服、语音与实时多模态、画布协同、在线状态 | 按功能连接 |
| 全局事件 SSE `GET /api/events/stream` | 服务端到当前用户的变化通知，事件落库、游标补发、15 秒注释帧心跳 | 登录后全应用一条 |
| AG-UI Run SSE `POST /api/agui/run` | 助理对话单次 Run 的正文与工具调用 | 单次 Run |

关键约定：

- 事件表 `sys_event`，信封 `{ id, type, refs, payload }`；类型 `{域}.{资源}.{动作}`，AIGC 加 `aigc.` 前缀，异步任务为 `async-task.status`。
- 业务统一经 `SysEventPublisher` 发布，在当前事务内落库，提交后广播；`SseSessionManager` 只做 SSE 传输层，不再被业务直接 `push`。
- 用户级事件（组织与工作区都为空）投递给该用户所有连接，范围事件精确匹配；发布时按 owner 取 advisory lock 保证游标单调。
- 前端 `lib/realtime` 持有唯一连接，按类型前缀分发；事件不进入 Zustand。
- 通知只走 WebSocket，`InternalMessage.PushStrategy` 删除 `SSE_ONLY`。

### Positive Consequences

- 新模块只需发布事件并订阅前缀，不再新建通道或 `EventSource`
- 异步任务事件随事务提交，回滚不产生事件，重启后按游标补发
- 通知与事件通道职责分离，无重复推送路径

### Negative Consequences

- 用户级事件语义扩大了投递面：个人上下文发布的事件也会到达组织上下文连接（仅作失效通知，多一次查询）
- 事件表持续增长，清理策略另立故事
- 多实例间无事件总线，跨实例事件最迟一个心跳周期经库补发送达

### Reversal Triggers（反向选择触发条件）

1. 事件量使单表游标查询成为瓶颈，需要引入消息中间件
2. 出现必须由客户端回传的双向实时场景，SSE 无法承载

## Pros and Cons of the Options

### 保持现状，各模块自建 SSE

- Good: 无迁移成本
- Bad: 连接数与补发语义重复实现，通知与异步任务已出现并行抽象

### 全部合并到 WebSocket

- Good: 单一协议
- Bad: AG-UI 与 AIGC 变化通知本质单向，改为 WebSocket 需重写协议且失去 SSE 的断线重放语义；与已定的 AG-UI 合同冲突

## More Information

- 需求、设计与任务：`docs/task/v0.13/AAF-119/`
- 前端方案：[realtime-data-strategy.md](../apps/webui/tech-design/realtime-data-strategy.md)
- 遗留：`use-execution-state.ts`（流程执行事件）与 `use-doc-events.ts`（autodev 文档事件）仍使用原生 `EventSource`，不在本决策范围
