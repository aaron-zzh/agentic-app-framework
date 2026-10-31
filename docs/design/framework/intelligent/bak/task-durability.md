---
level: Practice
layer: Model
purpose: AI 长任务持久执行：可恢复、可观测、状态一致
status: published
version: 1.0.0
date: 2026-05-29
author: AaronZZH
---

# AI 长任务持久执行

> 让 AI 助理的长任务像分布式系统一样可靠：可恢复、可观测、状态一致。

## 问题背景

AI 助理执行长任务（分钟到小时级）时面临的核心挑战：

| 问题 | 场景 | 后果 |
|------|------|------|
| 重复执行 | 多实例/多线程同时扫描到同一任务 | 副作用重复（重复发邮件等） |
| 孤儿状态 | 进程崩溃，任务停在 running | 永远不会被重新拾起 |
| 不可恢复 | 无检查点，崩溃后丢失中间进度 | 长任务从头重来，浪费 Token |
| 不可观测 | 无事件日志 | 无法审计"做了什么、为什么做" |
| 多实例协调丢失 | 主实例崩溃，子任务状态散落 | 不知道哪些子任务完成了 |

## 设计原则

- **Durable Execution**：任务进度持久化，进程死亡不丢失
- **Checkpoint + Resume**：从最近检查点恢复，不重放已完成步骤
- **Event Sourcing**：append-only 事件日志，完整审计轨迹
- **CAS 抢占**：数据库行级锁防止重复执行，多实例安全
- **分层检查点**：coordinator / subtask / agent_step 三层粒度

## 架构总览

```text
┌─────────────────────────────────────────────────────────────────┐
│  ChatTask（用户可见的任务）                                       │
│  状态机：pending → running → done / failed / cancelled           │
└──────────────────────────────┬──────────────────────────────────┘
                               ↓ 触发执行
┌─────────────────────────────────────────────────────────────────┐
│  TaskExecution（执行实例，支持重试）                               │
│  一个 Task 可能多次执行（attempt_no 递增）                        │
│  支持 parent_execution_id 表达主/子关系                           │
└──────────────────────────────┬──────────────────────────────────┘
                               ↓ 执行过程中
┌─────────────────────────────────────────────────────────────────┐
│  TaskCheckpoint（检查点快照）                                     │
│  scope: coordinator / subtask / agent_step                       │
│  保存：completedSteps, workingMemory, nextAction, taskBoard      │
└──────────────────────────────┬──────────────────────────────────┘
                               ↓ 每个动作
┌─────────────────────────────────────────────────────────────────┐
│  TaskEvent（事件日志，append-only）                               │
│  类型：task_started / step_completed / tool_called /             │
│        checkpoint_saved / subtask_forked / error / completed      │
└─────────────────────────────────────────────────────────────────┘
```

## 多实例协调

对齐五层架构中 Assistant fork 多实例 + 多子 Agent 协调的场景：

```text
主 TaskExecution（协调者）
  checkpoint.scope = 'coordinator'
  checkpoint.state = { taskBoard, forkPlan, aggregatedResults }
  │
  ├── 子 TaskExecution #1 (role: 后端)
  │   checkpoint.scope = 'subtask'
  │   checkpoint.state = { step: 3/5, workingMemory, completedSteps }
  │
  ├── 子 TaskExecution #2 (role: 前端)
  │   checkpoint.scope = 'subtask'
  │   checkpoint.state = { step: 1/3, workingMemory, completedSteps }
  │
  └── join → 聚合验证 → 继续或返回
```

### TaskBoard 结构

```json
{
  "subtasks": [
    {"key": "backend", "role": "后端", "status": "DONE", "executionId": 101},
    {"key": "frontend", "role": "前端", "status": "RUNNING", "executionId": 102},
    {"key": "test", "role": "测试", "status": "PENDING", "dependsOn": ["backend", "frontend"]}
  ],
  "phase": "EXECUTING",
  "completedCount": 1,
  "totalCount": 3
}
```

### 恢复流程

```text
服务重启 / 崩溃恢复：
  1. 扫描 status=running 且超时的 TaskExecution
  2. 加载主实例 checkpoint → 恢复 TaskBoard
  3. 遍历子任务：
     ├── DONE → 跳过
     ├── RUNNING → 查找子 TaskExecution checkpoint
     │   ├── 有 → 从 checkpoint 恢复
     │   └── 无 → 重置为 PENDING，重新 fork
     └── PENDING → 检查依赖 → 满足则 fork
  4. 通知用户："任务已恢复，继续执行中..."
```

## 状态一致性保障

### CAS 抢占

```sql
UPDATE ai_task_execution SET status='running', update_time=now()
WHERE id=? AND status='pending'
-- affected=1 才执行，否则说明被其他实例抢走
```

### 孤儿回收

```sql
UPDATE ai_task_execution SET status='pending', update_time=now()
WHERE status='running' AND update_time < now() - interval '10 minutes'
```

### 并发控制

- 主实例：串行决策（fork/join/仲裁），单线程
- 子实例：多虚拟线程并发，各自独立 checkpoint
- 子任务完成：CAS 更新 TaskBoard 中对应状态

## 数据模型

### ai_task_execution

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键 |
| task_id | BIGINT | 关联 ai_chat_task |
| parent_execution_id | BIGINT | 子任务指向主执行（NULL=主执行） |
| subtask_key | VARCHAR(100) | 子任务标识（如 backend/frontend） |
| attempt_no | INTEGER | 第几次尝试 |
| status | VARCHAR(20) | pending/running/done/failed/cancelled |
| role | VARCHAR(100) | 执行角色 |
| checkpoint_id | BIGINT | 最新检查点 ID |
| started_at | TIMESTAMP | 开始时间 |
| ended_at | TIMESTAMP | 结束时间 |

### ai_task_checkpoint

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键 |
| execution_id | BIGINT | 关联执行实例 |
| scope | VARCHAR(20) | coordinator/subtask/agent_step |
| step_index | INTEGER | 步骤序号 |
| state_json | JSONB | 状态快照 |
| created_at | TIMESTAMP | 创建时间 |

### ai_task_event

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键 |
| task_id | BIGINT | 关联任务 |
| execution_id | BIGINT | 关联执行实例 |
| subtask_key | VARCHAR(100) | 子任务标识（可选） |
| type | VARCHAR(50) | 事件类型 |
| payload_json | JSONB | 事件载荷 |
| created_at | TIMESTAMP | 创建时间 |

## 事件类型

| type | 说明 | payload 示例 |
|------|------|-------------|
| task_started | 任务开始执行 | {attempt_no, model} |
| subtask_forked | fork 子任务 | {subtaskKey, role, executionId} |
| step_started | Agent 步骤开始 | {stepIndex, action} |
| step_completed | Agent 步骤完成 | {stepIndex, result} |
| tool_called | 工具调用 | {tool, input_hash} |
| tool_completed | 工具完成 | {tool, duration_ms} |
| checkpoint_saved | 检查点保存 | {checkpointId, scope} |
| subtask_completed | 子任务完成 | {subtaskKey, result} |
| join_completed | 聚合完成 | {completedCount} |
| error | 错误 | {message, recoverable} |
| task_completed | 任务完成 | {result_summary} |

## 与五层架构对齐

### 各层任务管理完整链路

```text
Layer 4  Team（v0.6+）
  GoalTracker：目标级，持久化到 DB
  目标拆分为子目标 → 分发给多个 Assistant

Layer 3  Assistant 主实例
  TaskBoard：子任务级，内存 + Checkpoint 持久化
  子目标拆分为子任务 → fork 多子实例并行 → join 聚合
  InputBuffer：执行期接收追加输入
  Checkpoint：TaskBoard + 会话上下文 + InputBuffer

Layer 3  Assistant 子实例
  SubTaskContext：当前任务，fork→完成→销毁
  调度 Agent 执行具体任务

Layer 2  Agent
  WorkingMemory（PlanNotebook）：步骤级，执行期存在
  CognitiveCycleExecutor：感知→规划→执行→评估→学习
  AgentCheckpointService：步骤级检查点 + 指数退避重试

Layer 1  Cognition（不变）
  被动底座：记忆/知识/价值观/检查点存储
  Agent 执行前拉取（MemoryPipeline），执行后写回
```

### 组件委托关系

```text
DurableTaskExecutor (aaf-api，入口 + 事件日志 + DB 持久化)
  │
  ├── TaskBoard (framework，子任务管理 + 依赖 + 快照/恢复)
  │     └── fork 子实例 → 各自独立执行
  │
  ├── CognitiveCycleExecutor (framework，Agent 认知循环)
  │     ├── AgentCheckpointService (步骤级检查点 + 重试)
  │     ├── WorkingMemory (注意焦点，执行期)
  │     └── AgentSandbox (虚拟线程隔离 + 超时)
  │
  ├── CheckpointStore (framework/engine，通用检查点持久化)
  │     └── 实现：PostgreSQL JSONB
  │
  ├── TaskEvent → ai_task_event (事件日志，append-only)
  │     └── SSE 推送给前端 TaskBoardPanel
  │
  └── SessionRecoveryService (服务重启恢复)
        └── 扫描活跃会话 → 加载 Checkpoint → 恢复 TaskBoard
```

### 可视化（前端已有）

| 组件 | 前端展示 | 数据来源 |
|------|---------|---------|
| TaskBoardPanel | 子任务列表 + 进度条 + 依赖关系 + 结果摘要 | SSE 订阅 TaskBoard 状态 |
| RecoveryNotification | 恢复通知 | SessionRecoveredEvent |
| TaskEvent 日志 | 事件时间线（待实现） | GET /api/chat/tasks/{id}/events |

### 持久化层对应

| 架构概念 | 实现组件 |
|---------|---------|
| Agent Checkpoint（步骤级） | AgentCheckpointService → CheckpointStore |
| Assistant TaskBoard（会话级） | TaskBoard.toSnapshot() → CheckpointStore |
| DurableTaskExecutor 事件日志 | ai_task_event（PostgreSQL） |
| DurableTaskExecutor 执行实例 | ai_task_execution（PostgreSQL） |
| DurableTaskExecutor 检查点 | ai_task_checkpoint（PostgreSQL） |
| 置信度门控 → 转人工 | execution status=waiting_approval |
| InputBuffer | coordinator checkpoint 中的 pendingInputs |

## 相关文档

- [五层智能架构](architecture.md)
- [Assistant 技术方案](assistant/assistant-tech.md)
- [Agent 技术方案](agent/agent-tech.md)
