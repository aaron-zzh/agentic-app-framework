---
level: Practice
layer: Model
purpose: Layer 4 协作层 Team 技术方案——Leader 协调 + AgentScope 编排模式
status: draft
version: 1.1.0
date: 2026-05-28
author: AaronZZH
---

# Layer 4 协作层 Team 技术方案

> 由 Leader Assistant 协调多个 Worker Assistant 协作，委托 AgentScope 编排能力。

## 认知循环

```text
目标对齐 → 任务分发 → 进度同步 → 结果聚合 → 冲突仲裁
```

## 协作模式与 AgentScope 映射

| 模式 | AgentScope 实现 | 说明 |
|------|----------------|------|
| Leader 协调 | Supervisor | Leader 分发任务，Worker 汇报，Leader 仲裁 |
| 流水线 | Pipeline | 按顺序串行，上一个输出是下一个输入 |
| 平等协作 | MsgHub | 多方平等讨论，轮流发言 |

## 状态策略

- **GoalTracker**：目标级任务管理，持久化到 DB（v0.6+）
- **轻量会话级状态**：任务分配表、进度、仲裁结果
- **不持有数据级状态**：数据统一由 Cognition 管理

## 通信方式

- **内部**：Leader Assistant 直接调用 Worker Assistant（AssistantExecutor.chat）
- **跨系统**：A2A 协议（HTTP/SSE 双通道，Task 状态机）

内部协作不走 A2A，直接方法调用，性能最优。

## 与 Assistant 多实例的定位区分

| 场景 | 用 Assistant 多实例 | 用 Team |
|------|---------------------|---------|
| 同一用户、同一目标的并行加速 | ✅ | ❌ |
| 对抗性验证（写+审查） | ❌ | ✅ |
| 跨领域协作 | ❌ | ✅ |
| 跨系统外部 Agent | ❌ | ✅ |

## 包结构

```text
intelligent/team/
  ├── TeamOrchestrator           协作规范容器（注册/查询团队定义）
  │   ├── TeamDefinition         团队定义（内部类）
  │   ├── TeamMember             成员定义（内部类）
  │   └── CollaborationMode      协作模式枚举（内部类）
  ├── TaskDistributor            任务分解 + 分发
  ├── ProgressSyncService        进度同步
  └── ConflictArbitrator         冲突仲裁
```

**设计定位**：Team 是协作规范的容器（谁参与、什么模式、什么规则），实际执行由 coordinator Assistant 通过 A2A 协议驱动。Team 不直接调用 Agent/Pipeline，而是通过 coordinator Assistant 分发任务。

```text
用户请求 → AssistantService（coordinator）→ TeamOrchestrator（查规则）
  → coordinator 通过 A2A 分发给成员 Assistant → 汇总结果
```

## 相关文档

- [功能设计 — Team](team.md)
- [五层智能架构总览](../architecture.md)
- [A2A 协议](../../api/a2a.md)

---

## AgentScope 接口映射

### 核心类映射

| AAF 组件 | AgentScope 类 | 说明 |
|----------|--------------|------|
| Leader 协调 | `io.agentscope.core.pipeline.SequentialPipeline` | 顺序编排多 Agent |
| 并行协作 | `io.agentscope.core.pipeline.FanoutPipeline` | 并行分发同一输入给多 Agent |
| 平等讨论 | `io.agentscope.core.pipeline.MsgHub` | 消息广播，多 Agent 轮流发言 |
| 工具函数 | `io.agentscope.core.pipeline.Pipelines` | 静态工具方法（sequential/fanout） |
| Pipeline 接口 | `io.agentscope.core.pipeline.Pipeline<T>` | 统一编排接口（`execute(Msg) → Mono<T>`） |

### Pipeline 模式对照

| AgentScope Pipeline | 执行模式 | AAF Team 场景 |
|---------------------|---------|---------------|
| `SequentialPipeline` | A→B→C 串行，上游输出是下游输入 | 流水线协作（需求→设计→编码） |
| `FanoutPipeline(concurrent=true)` | 同一输入并行分发，收集所有结果 | 并行加速（多 Agent 同时分析） |
| `FanoutPipeline(concurrent=false)` | 同一输入顺序分发，收集所有结果 | 对抗性验证（写→审查） |
| `MsgHub` | 消息广播，参与者轮流发言 | 多方辩论/协商 |
| `Pipelines.compose(p1, p2)` | 组合两个 Pipeline | 复杂编排 |

### MsgHub 关键特性

```text
MsgHub 特性：
  - 自动广播：任何参与者的输出自动广播给其他所有参与者
  - 动态参与：运行时 add/remove Agent
  - 公告消息：enter() 时广播初始消息
  - 生命周期：try-with-resources 自动清理
  - observe 模式：Agent 接收消息但不回复（旁听）
```

## 适配器实现

### TeamOrchestrator（协作规范容器）

```java
package com.xuejiai.aaf.framework.intelligent.team;

/**
 * 团队协作规范层——定义协作规则，不直接执行。
 *
 * 设计定位：
 * - Team 是协作规范的容器（谁参与、什么模式、什么规则）
 * - 实际执行由 coordinator（协调者 Assistant）通过 A2A 协议驱动
 * - Team 不直接调用 Agent/Pipeline，而是通过 coordinator Assistant 分发任务
 */
@Service
@RequiredArgsConstructor
public class TeamOrchestrator {

    private final Map<String, TeamDefinition> teams = new ConcurrentHashMap<>();

    public void registerTeam(TeamDefinition team) { ... }
    public TeamDefinition getTeam(String teamId) { ... }
    public String getCoordinator(String teamId) { ... }
    public List<TeamMember> getMembers(String teamId) { ... }

    public enum CollaborationMode {
        COORDINATOR_DRIVEN,    // 协调者统筹
        PEER_COLLABORATION     // 平等协作
    }
}
```

**与文档设计的差异说明**：

文档原设计中 `DefaultTeamOrchestrator` 直接使用 AgentScope `Pipeline`/`MsgHub` 编排多 Agent。实际实现采用更解耦的设计：Team 层只管规则定义，执行委托给 coordinator Assistant 通过 A2A 分发。

理由：
- 符合 AAF 五层架构分层——Team 层不应直接操作 Agent 层
- coordinator Assistant 本身就是 AssistantExecutor，天然具备 Agent 调度能力
- A2A 协议统一了内部/外部通信，未来可无缝扩展到分布式

AgentScope `Pipeline`/`MsgHub` 能力保留在 AgentScope 整合文档中作为未来优化选项（当 coordinator 需要高性能并行编排时可引入）。

## 关键 Hook 注入点

Team 层不直接使用 Hook（Hook 是 Agent 级别的机制）。Team 通过以下方式与 AgentScope 交互：

| 交互方式 | 说明 |
|---------|------|
| Pipeline 编排 | 直接使用 `Pipelines.sequential()` / `Pipelines.fanout()` |
| MsgHub 广播 | 创建 MsgHub，管理参与者和消息流 |
| Agent observe | 通过 `agent.observe(msg)` 让 Agent 旁听不回复 |
| 结构化输出 | `pipeline.execute(input, ResultClass.class)` 最后一个 Agent 输出结构化结果 |

## 配置与初始化

```java
// 创建 Team 编排
// 1. Sequential（流水线）
var pipeline = Pipelines.createSequential(List.of(
        agentFactory.create(productDef),
        agentFactory.create(architectDef),
        agentFactory.create(developerDef)
));
pipeline.execute(requirementMsg).subscribe();

// 2. Fanout（并行）
var fanout = Pipelines.createFanout(List.of(
        agentFactory.create(reviewerA),
        agentFactory.create(reviewerB)
));
fanout.execute(codeMsg).subscribe();  // Mono<List<Msg>>

// 3. MsgHub（讨论）
try (var hub = MsgHub.builder()
        .participants(leader, memberA, memberB)
        .announcement(Msg.builder()
                .role(MsgRole.SYSTEM)
                .content(TextBlock.builder().text("讨论方案优劣").build())
                .build())
        .build()) {
    hub.enter().block();
    leader.call().block();   // leader 发言，自动广播
    memberA.call().block();  // memberA 发言，自动广播
    memberB.call().block();  // memberB 发言，自动广播
}
```

## 相关文档（补充）

- [AgentScope 整合策略](../agentscope-integration.md)
