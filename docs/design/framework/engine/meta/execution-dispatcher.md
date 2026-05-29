---
level: Theory
layer: Paradigm
purpose: 执行调度器——元引擎的中枢，将 DSL 路由到对应专项引擎
status: draft
version: 0.1.0
date: 2026-05-20
author: AaronZZH
---

# 执行调度器

> 调度器不包含业务逻辑，只做路由和生命周期管理。业务逻辑在 DSL 中，执行逻辑在各专项引擎中。

## 职责

执行调度器是元引擎的中枢，负责：

- 解析 DSL，识别域（dev / runtime / doc）和任务类型
- 将任务路由到对应的专项引擎
- 管理执行生命周期（启动、暂停、恢复、取消）
- 在路由前做 Agent 启用判断

## 路由逻辑

```text
DSL 解析
  ↓
域识别（dev / runtime / doc）
  ↓
任务类型识别
  ├── dev 域    → 规范驱动开发流程（auto-dev 引擎）
  ├── runtime 域 → 工作流 / 智能体 / 权限引擎
  └── doc 域    → 文档引擎 / 前端渲染管道
```

## Agent 启用判断

在路由到智能体引擎前，先做四维评估，四维均满足才启用 Agent 自主决策，否则降级到工作流或直接执行：

| 维度 | 判断 | 结论 |
|---|---|---|
| 任务复杂度 | 低 | 走工作流，不启用 Agent |
| 任务价值 | 低（< 阈值） | 走工作流，不启用 Agent |
| 所有步骤可执行 | 否 | 缩小范围或加人工节点 |
| 错误成本 | 高 | 启用 Agent 但加人工审核节点 |

## 生命周期管理

```text
PENDING   → 任务已接收，等待调度
RUNNING   → 正在执行
PAUSED    → 置信度门控触发，等待用户确认
CANCELLED → 用户取消或超出预算
COMPLETED → 执行完成，结果写入会话状态
FAILED    → 执行失败，触发降级策略
```

状态转换由调度器统一管理，各专项引擎只上报结果，不直接修改状态。

## 降级策略

| 故障场景 | 降级行为 |
|---|---|
| AI 服务不可用 | 降级到规则引擎（预定义工作流） |
| Agent 超时 | 切换简化流程（直接 LLM 调用） |
| 沙箱执行失败 | 暂停并转人工处理 |
| 所有 AI 不可用 | 纯工作流模式，保留核心业务功能 |

降级不静默发生，对话区明确告知用户当前处于降级模式及原因。

## 内部处理步骤

调度器内部对每个任务按顺序执行以下步骤（Virtual Threads 同步调用链）：

```text
输入（DSL / 意图 / API 调用 / 事件触发）
  ↓ filter    权限校验、预算检查，不满足则拒绝
  ↓ transform DSL 解析、意图结构化、L1→L2→L3 转化
  ↓ route     域识别（dev/runtime/doc）+ 任务类型识别，分发到对应引擎
  ↓ parallel  多子任务并发执行（Virtual Threads + StructuredTaskScope）
  ↓ reduce    汇总子任务结果，写入会话状态
输出（执行结果 → 对话区 / 工作区）
```

并发控制：Agent 并发数超限时新任务进入有界等待队列，不丢弃。预算监控在 route 步骤后更新消耗统计，超限注入暂停事件。

## 包结构与核心接口

```text
core/dispatcher/
├── ExecutionDispatcher.java        核心接口
├── ExecutionContext.java           执行上下文（DSL + 状态快照）
├── ExecutionResult.java            执行结果（含置信度、可验证性）
├── DomainRouter.java               域路由（dev/runtime/doc）
├── LifecycleManager.java           生命周期管理
└── impl/
    └── DefaultExecutionDispatcher.java
```

```java
public interface ExecutionDispatcher {
    Mono<ExecutionResult> dispatch(ExecutionContext ctx);
    Flux<ExecutionResult> dispatchStream(ExecutionContext ctx);
}
```

### 执行管道步骤

```text
core/pipeline/
├── ExecutionStep.java              处理步骤接口（filter/transform/route/parallel/reduce）
├── StepChain.java                  步骤链构建器
└── steps/
    ├── FilterStep.java             权限校验、预算检查
    ├── TransformStep.java          DSL 解析、意图结构化
    ├── RouteStep.java              域识别 + 任务类型分发
    ├── ParallelStep.java           Virtual Threads 并发执行
    └── ReduceStep.java             汇总子任务结果
```

## 相关文档

- [元引擎设计](meta-engine.md)
- [置信度门控器](../../intelligent/core/confidence-gate.md)
- [状态管理器](state-manager.md)
