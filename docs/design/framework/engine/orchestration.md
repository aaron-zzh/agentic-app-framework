---
level: Practice
layer: Model
purpose: 编排引擎设计——执行路径决策、引擎协同、置信度门控、响应式执行管道
status: draft
version: 0.2.0
date: 2026-05-20
author: AaronZZH
changelog:
  - 2026-05-20 v0.2.0 | 拆分：调度引擎独立为 scheduler.md，本文档只保留编排
  - 2026-05-20 v0.1.0 | 初稿
---

# 编排引擎设计

> 编排引擎负责单次请求的执行路径决策和引擎协同，不管任务队列和定时触发（那是调度引擎的职责）。

## 与相关引擎的分工

```text
编排引擎（本文档）              工作流引擎              调度引擎
────────────────────────────────────────────────────────────────
单次请求的执行路径              跨时间的业务流程         异步任务队列
动态路由，按 DSL 决定           有向图，节点顺序固定      定时触发、重试
引擎协同、置信度门控            状态持久化，可暂停/回退   优先级、分布式锁
```

工作流引擎和调度引擎都是被编排引擎驱动的专项引擎。

## 核心组件

### 执行调度器

DSL 路由 → 引擎选择 → 生命周期管理。

当前实现：分散在各层，尚无统一 `ExecutionScheduler` 入口。规划中将提供：
- 响应式执行管道（filter / transform / route / parallel / reduce）
- 引擎选择与编排
- 执行生命周期（启动 / 暂停 / 恢复 / 取消）

### 状态管理器

会话 / 工作区 / 系统 / 元数据四层持久化状态。

当前实现：
- 会话状态：`SessionManager`（Redis 持久化）
- Agent 工作状态：`AgentCheckpointService`（检查点 + 指数退避重试）

### 置信度门控器

自动执行 / 等待确认 / 转人工三档。

```text
> 0.9   → 自动执行，结果暂存，异步通知
0.7-0.9 → 展示执行计划，等待确认
< 0.7   → 暂停，说明原因，转人工处理
```

当前实现：`ResultAggregator`（置信度聚合）+ `ConflictArbitrator`（冲突仲裁），门控逻辑待统一封装。

### 元数据管理器

模块 / 插件 / 工具 / 组件元数据，语义漂移检测。

当前实现：`ToolRegistry` + `AgentRegistryService`，语义漂移检测由语义计算引擎支撑。

## 已支持的编排模式

| 编排模式 | 实现 | 说明 |
|---------|------|------|
| Agent 自主规划 | AgentScope ReActAgent | Agent 接收目标后自主拆分子步骤、选择工具、迭代执行 |
| 工作流编排 | Flowable | 固定流程骨架，节点顺序确定，支持审批/暂停/回退 |
| Team 多 Assistant 协作 | TeamOrchestrator + TaskDistributor | Leader 协调或平等协作，任务分发与进度同步 |
| A2A 跨服务通信 | Spring AI A2A | Assistant 间异步消息，支持跨框架/跨进程 |
| Agent 间消息路由 | AgentEventBus | 发布/订阅，Agent 间解耦通信 |

## 响应式执行管道

```text
输入请求
  → filter（权限/预算校验）
  → route（DSL 路由，决定走哪个引擎）
  → parallel（并行执行多个子任务）
  → transform（结果转换）
  → reduce（结果聚合）
  → 输出响应
```

## 相关文档

- [工作流引擎](workflow.md)
- [调度引擎](scheduler.md)
- [预算控制](budget-control.md)
