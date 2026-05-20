---
level: Theory
layer: Paradigm
purpose: 元引擎代码结构设计——包划分、核心接口、模块边界
status: draft
version: 0.1.0
date: 2026-05-20
author: AaronZZH
---

# 元引擎代码结构

> 本文档描述元引擎在 `aaf-framework` 模块中的包结构、核心接口定义和模块边界约束。
> 架构设计见 [meta-engine.md](./meta-engine.md)，各子模块详细设计见 `core/` 目录。

## Maven 模块归属

元引擎核心实现位于 `aaf-framework`，按层分包：

```text
aaf-framework
└── src/main/java/com/xuejiai/aaf/framework/
    ├── core/                  ← 元引擎核心（本文档范围）
    │   ├── dispatcher/        ← 执行调度器
    │   ├── state/             ← 状态管理器
    │   ├── gate/              ← 置信度门控器
    │   ├── metadata/          ← 元数据管理器
    │   ├── pipeline/          ← 响应式执行管道
    │   ├── evolution/         ← 自进化机制
    │   ├── budget/            ← 预算感知
    │   └── sandbox/           ← 沙箱执行
    ├── intelligent/           ← Layer 3 智能层（agent/cognition/assistant/team）
    └── engine/                ← Layer 2 专项引擎（workflow/knowledge/memory 等）
```

`aaf-auto-dev` 依赖 `aaf-framework`，实现代码生成与热部署能力（自进化的执行侧）。

## 包结构详解

### core/dispatcher — 执行调度器

```text
dispatcher/
├── ExecutionDispatcher.java        ← 核心接口
├── ExecutionContext.java           ← 执行上下文（DSL + 状态快照）
├── ExecutionResult.java            ← 执行结果（含置信度、可验证性）
├── DomainRouter.java               ← 域路由（dev/runtime/doc）
├── LifecycleManager.java           ← 生命周期管理
└── impl/
    └── DefaultExecutionDispatcher.java
```

核心接口：

```java
public interface ExecutionDispatcher {
    Mono<ExecutionResult> dispatch(ExecutionContext ctx);
    Flux<ExecutionResult> dispatchStream(ExecutionContext ctx);
}
```

### core/state — 状态管理器

```text
state/
├── StateManager.java               ← 核心接口
├── SessionState.java               ← 会话状态（临时）
├── WorkspaceState.java             ← 工作区状态（持久，多用户共享）
├── SystemState.java                ← 系统状态（持久，全局）
├── MetadataState.java              ← 元数据状态（持久，规范驱动）
└── impl/
    └── RedisPostgresStateManager.java
```

核心接口：

```java
public interface StateManager {
    SessionState getSession(String sessionId);
    WorkspaceState getWorkspace(String workspaceId);
    void commitToWorkspace(String sessionId, String workspaceId);  // 渐进提交
}
```

### core/gate — 置信度门控器

```text
gate/
├── ConfidenceGate.java             ← 核心接口
├── ConfidenceScore.java            ← 置信度评分（意图×规范×历史）
├── VerifiabilityScore.java         ← 可验证性评分
├── GateDecision.java               ← 门控决策（AUTO/CONFIRM/HUMAN/BLOCK）
└── impl/
    └── DefaultConfidenceGate.java
```

核心接口：

```java
public interface ConfidenceGate {
    GateDecision evaluate(ExecutionContext ctx, ConfidenceScore score);
}
```

### core/metadata — 元数据管理器

```text
metadata/
├── MetadataManager.java            ← 核心接口
├── ModuleMetadata.java             ← 模块元数据
├── ToolMetadata.java               ← 工具元数据（含知识绑定）
├── PluginMetadata.java             ← 插件元数据
├── ComponentMetadata.java          ← UI 组件元数据
├── DriftDetector.java              ← 语义漂移检测
└── impl/
    └── DefaultMetadataManager.java
```

### core/pipeline — 执行处理步骤

调度器内部处理步骤的抽象，基于 Virtual Threads 同步调用链实现，不依赖 Reactor：

```text
pipeline/
├── ExecutionStep.java              ← 处理步骤接口（filter/transform/route/parallel/reduce）
├── StepChain.java                  ← 步骤链构建器
└── steps/
    ├── FilterStep.java
    ├── TransformStep.java
    ├── RouteStep.java
    ├── ParallelStep.java           ← Virtual Threads 并发执行
    └── ReduceStep.java
```

### core/evolution — 自进化

```text
evolution/
├── EvolutionEngine.java            ← 核心接口
├── BehaviorCollector.java          ← 行为采集
├── EffectEvaluator.java            ← 效果评估
├── EvolutionProposal.java          ← 进化方案（含影响范围分析）
├── AuditLevel.java                 ← 审核分级（AUTO/USER/HUMAN/BLOCKED）
└── impl/
    └── DefaultEvolutionEngine.java
```

### core/budget — 预算感知

```text
budget/
├── BudgetManager.java              ← 核心接口
├── BudgetEstimate.java             ← 执行前预估（Token/时间/费用/工具调用）
├── BudgetMonitor.java              ← 执行中实时监控
├── BudgetConfig.java               ← 预算配置（系统/用户/单次）
└── impl/
    └── DefaultBudgetManager.java
```

### core/sandbox — 沙箱执行

```text
sandbox/
├── SandboxExecutor.java            ← 核心接口
├── SandboxResult.java              ← 执行结果
├── SandboxConfig.java              ← 沙箱配置（超时/网络/IO 约束）
└── impl/
    ├── GraalVmSandboxExecutor.java ← 首选（GraalVM Polyglot）
    └── SubprocessSandboxExecutor.java ← 降级（子进程隔离）
```

## 模块依赖规则

```text
core/dispatcher  → core/gate, core/state, core/pipeline, core/metadata
core/gate        → 无内部依赖（纯计算）
core/state       → 无内部依赖（纯存储抽象）
core/pipeline    → 无内部依赖（纯管道抽象）
core/metadata    → core/gate（漂移检测触发门控）
core/evolution   → core/dispatcher, core/gate（进化方案走标准调度链）
core/budget      → core/pipeline（在管道中注入监控点）
core/sandbox     → 无内部依赖（独立执行环境）
```

禁止规则：
- `core/` 内任何包不得依赖 `intelligent/` 或 `engine/`（方向只能向下）
- `core/` 不得直接访问数据库，通过 `StateManager` 接口抽象存储

## 与其他模块的边界

| 调用方 | 调用 core/ 的方式 | 禁止 |
|---|---|---|
| `intelligent/agent` | 通过 `ExecutionDispatcher` 提交任务 | 直接 new 实现类 |
| `intelligent/team` | 通过 `StateManager` 读写工作区状态 | 跨 Session 直接读写 |
| `engine/*` | 被 `ExecutionDispatcher` 路由调用 | 反向调用 core/ |
| `aaf-auto-dev` | 通过 `EvolutionEngine` 接收进化任务 | 直接修改 core/ 状态 |

## 相关文档

- [执行调度器](core/execution-dispatcher.md)
- [状态管理器](core/state-manager.md)
- [置信度门控器](core/confidence-gate.md)
- [元数据管理器](core/metadata-manager.md)
- [响应式执行管道](core/reactive-pipeline.md)
- [自进化机制](core/evolution.md)
- [预算感知](core/budget-awareness.md)
- [人类计算支撑](core/human-computation.md)
- [复杂性封装策略](core/complexity-encapsulation.md)


## 技术映射

| 元引擎概念 | 技术实现 |
|---|---|
| 后端 DSL 解析 | 自研解析器（基于 ANTLR 或 Jackson） |
| 前端 DSL 解析 | 自研前端解析器（TypeScript） |
| 组件渲染 / 插件加载 | Web Components + 组件注册表 |
| 实时协同（OT/CRDT） | Yjs 或自研 CRDT 实现 |
| 工作流执行 | Flowable |
| 智能体编排 | Spring AI + AgentScope |
| 知识库 | PostgreSQL（向量）+ Neo4j（图谱） |
| 记忆系统 | Redis（短期）+ PostgreSQL（长期） |
| 文档引擎 | PostgreSQL（内容）+ Redis（协同缓存） |
| 元数据管理 | PostgreSQL（持久）+ Redis（热缓存） |
| 代码生成 | aaf-auto-dev（FreeMarker + JavaParser） |
| 沙箱执行 | JVM 沙箱隔离 |
| 状态管理 | Redis（会话）+ PostgreSQL（持久） |
| 自进化评估 | 异步任务 + 人工标注工作流 |
