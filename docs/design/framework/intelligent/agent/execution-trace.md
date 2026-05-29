# 智能体执行追踪（Execution Trace）

> 状态：草案 | 作者：AaronZZH & Kiro | 日期：2026-05-25

## 概述

为 AAF 多智能体框架提供完整的执行过程追踪能力，支撑三个核心场景：

- **审计**：谁在什么时候执行了什么，结果如何
- **错误恢复与重试**：从断点继续执行，避免重复计算
- **协作分析**：多 Agent 间的调用拓扑与因果链

## 设计原则

- **异步不阻塞**：trace 记录永远不阻塞 Agent 执行主路径
- **写入失败不影响业务**：trace 是旁路，降级为 warn 日志即可
- **分层存储**：热/温/冷三层，按访问频率和查询模式选择存储
- **复用已有机制**：基于 AgentCheckpointService + TrajectoryCollector 扩展

## 存储分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                     Redis（热层）                             │
│  用途：运行时 checkpoint + 快速重试恢复                       │
│  TTL：2h（执行中）/ 完成后清除                                │
│  Key：agent:trace:{executionId}                              │
│  Value：完整 ExecutionState（含所有已完成步骤的 input/output） │
└──────────────────────────┬──────────────────────────────────┘
                           │ 异步写入（执行完成/失败时）
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                  PostgreSQL（温层/审计层）                     │
│  execution_run  — 一次完整执行                                │
│  execution_step — 执行中的每个步骤（树形自关联）              │
└──────────────────────────┬──────────────────────────────────┘
                           │ 异步写入（仅多 Agent 协作场景）
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   Neo4j（冷层/分析层）                        │
│  (:AgentNode)-[:INVOKED]->(:AgentNode)  协作调用关系         │
│  (:ExecutionNode)-[:TRIGGERED]->(:ExecutionNode) 执行触发链  │
└─────────────────────────────────────────────────────────────┘
```

## 数据模型

### PostgreSQL

```sql
-- 一次完整的 Agent 执行
CREATE TABLE execution_run (
    id              BIGSERIAL PRIMARY KEY,
    execution_id    VARCHAR(128) NOT NULL UNIQUE,
    parent_run_id   BIGINT REFERENCES execution_run(id),
    agent_id        VARCHAR(64) NOT NULL,
    agent_name      VARCHAR(128),
    user_id         BIGINT,
    conversation_id VARCHAR(64),
    input           TEXT,
    output          TEXT,
    status          VARCHAR(16) NOT NULL,  -- RUNNING / SUCCESS / FAILED / RETRYING
    error_message   TEXT,
    token_input     INT DEFAULT 0,
    token_output    INT DEFAULT 0,
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ,
    duration_ms     BIGINT,
    retry_count     INT DEFAULT 0,
    metadata        JSONB
);

-- 执行步骤（树形自关联）
CREATE TABLE execution_step (
    id              BIGSERIAL PRIMARY KEY,
    run_id          BIGINT NOT NULL REFERENCES execution_run(id),
    parent_step_id  BIGINT REFERENCES execution_step(id),
    step_index      INT NOT NULL,
    step_type       VARCHAR(32) NOT NULL,  -- PERCEIVE / PLAN / TOOL_CALL / LLM_CALL / EVALUATE / LEARN
    agent_id        VARCHAR(64),
    tool_name       VARCHAR(128),
    input           TEXT,
    output          TEXT,
    status          VARCHAR(16) NOT NULL,
    error_message   TEXT,
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ,
    duration_ms     BIGINT
);

CREATE INDEX idx_run_user ON execution_run(user_id, started_at DESC);
CREATE INDEX idx_run_agent ON execution_run(agent_id, started_at DESC);
CREATE INDEX idx_run_status ON execution_run(status) WHERE status != 'SUCCESS';
CREATE INDEX idx_step_run ON execution_step(run_id, step_index);
```

#### 设计决策：步骤关系为什么不放 Neo4j

单次执行内的步骤是确定性的树/链结构，查询模式固定（"给我 runId=X 的所有步骤按顺序"）。PostgreSQL 的 `parent_step_id` 自关联 + 递归 CTE 完全满足需求，无需跨库查询增加复杂度。

Neo4j 只处理**跨执行维度**的图分析——"Agent A 历史上调用过哪些 Agent"、"某个错误影响了哪些下游执行"。

### Neo4j（仅跨执行协作拓扑）

```cypher
-- Agent 节点（长期存在）
CREATE (:AgentNode {agentId: $id, name: $name, type: $type})

-- 协作调用关系（聚合统计，非每次执行都写）
MERGE (a:AgentNode {agentId: $caller})-[r:INVOKED]->(b:AgentNode {agentId: $callee})
ON CREATE SET r.count = 1, r.lastAt = $now, r.avgDurationMs = $duration
ON MATCH SET r.count = r.count + 1, r.lastAt = $now,
             r.avgDurationMs = (r.avgDurationMs * (r.count - 1) + $duration) / r.count

-- 执行触发链（可选，仅 Team 协作场景）
CREATE (e1:ExecutionNode {executionId: $parentId})-[:TRIGGERED {reason: $reason}]->(e2:ExecutionNode {executionId: $childId})
```

**写入条件**：仅当 `parent_run_id IS NOT NULL`（存在 Agent 间调用）时异步写入 Neo4j。单 Agent 执行不写 Neo4j。

### Redis（运行时 checkpoint）

沿用现有 `AgentCheckpointService` 的 Key 结构，扩展 `ExecutionState`：

```java
// 扩展后的 ExecutionState
{
    executionId,
    agentId,
    userId,
    currentStep,
    completedSteps: [
        { stepIndex, stepType, input, output, startedAt, finishedAt }
    ],
    intermediateResults: { ... },
    lastError,
    retryCount
}
```

## 异步写入机制

```
CognitiveCycleExecutor.execute()
    │
    ├── 执行中：每步完成 → 同步写 Redis checkpoint（轻量，已有）
    │
    └── 执行结束（finally）
            │
            └── 发布 ExecutionCompletedEvent（Spring ApplicationEvent）
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
  @Async PG 写入   @Async Neo4j     清除 Redis
  (batch insert    (仅多Agent)      checkpoint
   run + steps)
```

### 事件定义

```java
public record ExecutionCompletedEvent(
    String executionId,
    String parentExecutionId,  // null 表示顶层执行
    String agentId,
    String agentName,
    Long userId,
    String conversationId,
    String input,
    String output,
    ExecutionStatus status,
    String errorMessage,
    int tokenInput,
    int tokenOutput,
    Instant startedAt,
    Instant finishedAt,
    int retryCount,
    List<StepRecord> steps
) {}

public record StepRecord(
    int stepIndex,
    String parentStepId,  // null 表示顶层步骤
    StepType stepType,
    String agentId,       // 子 Agent 调用时
    String toolName,      // 工具调用时
    String input,
    String output,
    ExecutionStatus status,
    String errorMessage,
    Instant startedAt,
    Instant finishedAt
) {}
```

### 监听器

```java
@Component
@RequiredArgsConstructor
public class ExecutionTraceListener {

    private final ExecutionRunRepository runRepository;
    private final ExecutionStepRepository stepRepository;
    private final Neo4jAgentGraphService graphService;

    @Async
    @TransactionalEventListener(phase = AFTER_COMPLETION)
    public void onExecutionCompleted(ExecutionCompletedEvent event) {
        try {
            persistToPostgres(event);
            if (event.parentExecutionId() != null) {
                persistToNeo4j(event);
            }
        } catch (Exception e) {
            log.warn("执行追踪写入失败 [{}]: {}", event.executionId(), e.getMessage());
        }
    }
}
```

## 重试恢复策略

```
重试触发
    │
    ├── 1. 查 Redis checkpoint（热恢复，< 2h 内的失败）
    │       → 有：从 checkpoint.currentStep 继续，跳过已完成步骤
    │       → 优势：快速，包含完整中间结果
    │
    ├── 2. Redis 无 → 查 PG execution_run + steps（冷恢复）
    │       → status=FAILED：重建 ExecutionState
    │       → 从最后一个 status=SUCCESS 的 step 之后继续
    │       → 优势：持久，不受 TTL 限制
    │
    └── 3. 都无 → 全新执行
```

### 恢复接口

```java
public interface ExecutionRecoveryService {

    /**
     * 尝试恢复执行状态。Redis 优先，PG 兜底。
     *
     * @return 可恢复的状态，empty 表示需全新执行
     */
    Optional<RecoverableState> recover(String executionId);

    record RecoverableState(
        String executionId,
        int resumeFromStep,           // 从哪一步继续
        List<StepRecord> completedSteps,
        Map<String, Object> intermediateResults
    ) {}
}
```

## 审计查询场景

| 查询 | 数据源 | 方式 |
|------|--------|------|
| 用户最近 N 次执行详情 | PG | `WHERE user_id=? ORDER BY started_at DESC` |
| 某次执行的完整步骤树 | PG | 递归 CTE on `parent_step_id` |
| 失败率统计（按 Agent/时间段） | PG | `GROUP BY agent_id, status` |
| Agent 协作调用拓扑 | Neo4j | `MATCH (a)-[:INVOKED]->(b) RETURN a, b` |
| 某个失败的影响链 | Neo4j | `MATCH path=(e)-[:TRIGGERED*]->() RETURN path` |
| 某用户的 token 消耗趋势 | PG | `SUM(token_input + token_output) GROUP BY date` |

## 与 AgentScope 的集成

复用 AgentScope 的 Hook 机制做步骤级采集：

```java
public class ExecutionTraceHook implements AgentHook {

    private final ThreadLocal<List<StepRecord>> stepBuffer = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public void beforeStep(StepContext ctx) {
        // 记录开始时间
    }

    @Override
    public void afterStep(StepContext ctx) {
        // 1. 构建 StepRecord 加入 buffer
        // 2. 同步更新 Redis checkpoint（轻量）
    }

    /** 获取并清空当前执行的所有步骤记录 */
    public List<StepRecord> drainSteps() {
        var steps = stepBuffer.get();
        stepBuffer.remove();
        return steps;
    }
}
```

在 `AgentScopeRuntime.create()` 中注册：

```java
builder.hook(tokenMeteringHook)
       .hook(executionTraceHook);
```

## 实现优先级

| 阶段 | 内容 | 依赖 |
|------|------|------|
| P1 | PG 表 + ExecutionCompletedEvent + 异步写入监听器 | Flyway 迁移 |
| P1 | 扩展 AgentCheckpointService 支持冷恢复（查 PG） | P1 表 |
| P2 | ExecutionTraceHook 集成到 AgentScope | AgentScope Hook API 稳定 |
| P2 | Neo4j 协作拓扑写入 | 多 Agent 协作场景落地 |
| P3 | 审计查询 API + 前端可视化 | 前端 trace viewer 组件 |

## 与现有模块的关系

```
AgentCheckpointService（已有）
    → 保留：运行时 checkpoint + 重试
    → 扩展：recover() 增加 PG 兜底

TrajectoryCollector（已有接口）
    → 实现：发布 ExecutionCompletedEvent

CognitiveCycleExecutor（已有）
    → 改造：finally 块发布事件

GraphMemoryService（已有）
    → 不复用：职责不同（记忆 vs 执行追踪）
    → 但 Neo4j 连接配置共享
```
