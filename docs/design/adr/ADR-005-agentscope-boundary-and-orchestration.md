---
level: Practice
layer: Principle
purpose: 定案 AAF 对 AgentScope Java v2 的复用边界、AG-UI 协议对齐方式与双层编排模型的本质与去向
status: draft
version: 1.0.0
date: 2026-08-31
author: AaronZZH
---

---
status: proposed
date: 2026-08-31
deciders: [AaronZZH]
consulted: []
informed: []
related-tasks: [docs/design/audit/2026-08-30-agentscope-boundary/]
---

# ADR-005: AgentScope 复用边界与双层编排模型定案

## Context and Problem Statement

2026-08-30 的专项评估（`docs/design/audit/2026-08-30-agentscope-boundary/`）测绘了 AAF 对 AgentScope Java v2 的复用边界，发现自研编排层存在 1 个 blocker + 9 个 major 问题，并推荐路线三（保留 harness、收窄自研边界）。本次对话在评估文档基础上，针对三个此前未决的具体问题做了代码级验证与决策：AG-UI 协议对齐、双层编排的本质与是否可替换、AgentScope 依赖版本与编译目标。三者共同回答一个问题——**AAF 与 AgentScope 的边界该划在哪，为什么划在这，以及现在该做什么**。

## Decision Drivers

- 协议合规性：自研 AG-UI 事件模型与标准 `@ag-ui/client` 的 zod schema 不兼容，livechat 路径完全无法运行
- 运行时正确性：自研执行适配器存在原子终态仲裁缺陷（RQ-01），阻断生产可靠性
- 架构可维护性：需要判断"双层循环"是否可被 AgentScope 原生能力（Subagent/Plan Mode）替代，避免重复造轮子
- 性能与正确性：HarnessAgent 在全部能力关闭情况下仍存在非预期的对象构造与工具泄漏

## Considered Options

### 议题一：AG-UI 事件模型来源
- A. 继续自研 `AgUiEvent`
- B. 采用官方 `agentscope-extensions-agui` 库的事件模型（不采用 starter/adapter）
- C. 采用官方 `agentscope-agui-spring-boot-starter` 全家桶

### 议题二：双层编排模型去向
- A. 保持 AAF 自建的 TaskBoard/DelegatedTaskCoordinator 外部编排
- B. 替换为 AgentScope 原生 Subagent（`agent_spawn`）内部嵌套委派
- C. 替换为 Plan Mode + Subagent 组合的原生方案

### 议题三：编译目标 Agent 类
- A. 继续基于 `HarnessAgent.builder()`（全部包装能力禁用）
- B. 切换为 `ReActAgent.builder()`

## Decision Outcome

**议题一选 B**：declare `io.agentscope:agentscope-extensions-agui`（库），删除自研 `AgUiEvent`，`AgUiProjector` 改为有状态会话（`AgUiProjector.Session`）输出官方 `AguiEvent`，SSE 序列化统一走 `AguiEventEncoder`。**不采用** starter/`AguiAgentAdapter`。

**议题二选 A**：保持 AAF 自建的 TaskBoard 外部编排，不替换为原生 Subagent。

**议题三选 B**：编译目标由 `HarnessAgent.builder()` 切换为 `ReActAgent.builder()`。AAF 对 Harness 包装的 10 项工程能力使用率为零（15 个 `disableXxx()` 全部禁用），且 `PermissionMode`（`permissionContext`）已确认是 `ReActAgent.builder()` 的基础参数，非 Harness 专属，不需要为它保留 HarnessAgent。

### 核心论据

1. **starter 不可用，根因是抽象层级错配，不是配置不足**——starter 的货币单位是"一个 Agent"（`AguiRequestProcessor.process` 直接 `new AguiAgentAdapter(agent, config)`），而 AAF 的执行单元是"一个任务"（TaskBoard 可含多节点，每节点独立编译 Agent）。`AguiMvcController.Builder` 无 `agentResolver()` 注入点，`AguiAgentAdapter` 无法发出 `Custom`/`ActivitySnapshot`/`outcome.interrupt`，TaskBoard 治理链路在这条路径上完全绕过。证据：`agentscope-agui-spring-boot-starter` 2.0.2 源码 `AguiMvcController`/`AguiRequestProcessor`。
2. **官方事件模型是纯数据层，无 Agent 依赖，可安全复用**——`AguiEvent`/`AguiEventType`/`AguiEventEncoder` 不绑定"Agent=应用"假设，替换自研模型可一次性解决 threadId 缺失、`toolCallName`/`content` 字段名错误、`RUN_FINISHED.outcome` 缺失三类 schema 硬失败（已用 `@ag-ui/core@0.0.57` 的 zod schema 逐字段核实，并有 8 个通过的单测覆盖序列化形状）。
3. **入参模型不采用官方 `RunAgentInput`**——其字段的 Jackson 2 注解在 Spring Boot 4 的 Jackson 3 `HttpMessageConverter` 下静默失效，AAF 自行定义入参 record。
4. **双层循环的本质是外部编排，非内部嵌套委派，两者结构不同不可互换**——AgentScope 原生 Subagent 是"一个 Agent 的推理循环把子 Agent 当工具调用嵌套在自己内部"（容器关系，事件靠 `source` 路径区分父子）；AAF 的 `DelegatedTaskCoordinator` 是"站在所有 Agent 之外的纯代码编排层，独立发起 N 次互不相关的 `AgentExecutionPort.execute()`"（平级编排关系，靠 `TaskBoard`/`ExecutionEventStorePort` 记账）。已用代码验证：`TaskBoard.coordinated()` 只建 1 个 `COORDINATOR` 节点，协调者与执行者节点走同一编译路径（`AgentScopeSpecCompiler.compileDynamicNew`/`compileNew`，均含 `disableSubagents()`），协调者的"计划"来自其**最终输出文本**经 `decodeAndValidatePlan` 严格 JSON 校验后追加新 `EXECUTOR` 节点，不经过任何 AgentScope 原生工具调用。
5. **切换到原生 Subagent 不能免除 AAF 自建治理代码，只是更换代价更高的实现**——原生方案要求：（a）开启工作区（`PlanModeManager`/`SubagentsMiddleware` 构造函数均硬依赖 `WorkspaceManager`，已用源码签名验证），带来全新的租户隔离/沙箱授权集成面；（b）用 Permission ASK 拦截补回"执行前仲裁"这一约束（`agent_spawn` 默认无此拦截）；（c）自建预算计数器替代 `DecompositionBudget`（官方递归硬编码 3 层，无租户级可配置项）；（d）持久化模型从 TaskBoard DB 行迁移到 `AgentState`/workspace 文件。四项均为全新开发，无已知问题清单，风险高于修复已定位的 13 条 RQ（1 blocker + 9 major，均为实现 bug，非概念缺陷）。
6. **HarnessAgent 在全部能力禁用时仍有非预期开销**——已确认 `HarnessAgent.builder().build()` 即便调用全部 15 个 `disableXxx()`，仍默认构造 `LocalFilesystem`/`WorkspaceManager`/`WorkspaceMessageBus`/`WorkspaceAsyncToolRegistry`，装配 `InboxMiddleware`，并**注册 `WaitAsyncResultsTool` 到工具列表**——后者是真实的工具泄漏，不只是性能损耗。此成本在 AAF 当前架构下每个 TaskBoard 节点、每次执行都重新支付（所有节点走 `SubagentSpec.Dynamic`→`compileDynamic`，零缓存）。

### Positive Consequences

- AG-UI 出口与标准 `@ag-ui/client` 兼容，livechat/chatter 路径可用标准客户端消费
- 事件模型维护责任转移给上游，AAF 只维护"如何从 `ExecutionEvent` 映射到官方事件"这一层
- 双层循环的架构边界有了代码级证据支撑的清晰定义，不再依赖文档措辞
- 切换编译目标到 `ReActAgent` 后消除 `WaitAsyncResultsTool` 工具泄漏，减少 `AgentScopeSpecCompiler` 中 15 个 `disableXxx()` 维护面

### Negative Consequences

- AAF 自建的 TaskBoard 治理代码（`DecompositionBudget`/`decodeAndValidatePlan`/`ConversationLeasePort` 等）需要继续自行维护，不能通过采用官方机制减负
- 已确认的 13 条 RQ（02-runtime-quality.md）仍需逐条修复，本 ADR 不改变这份工作量
- `PermissionMode` 采纳后仍需 AAF 自行承担"决策落事件+持久审批+租户约束"这层（04 文档已确认）

### Reversal Triggers（反向选择触发条件）

仅当出现以下之一时考虑回切：

1. AgentScope 官方为 Subagent 机制提供租户级可配置的递归深度/预算控制，且提供官方持久化桥接方案（无需 AAF 自建 workspace-DB 同步层）
2. AAF 业务场景出现明确需求："节点内部自主再拆一层"（`maxDecompositionDepth` 从目标态转正），且用 AAF 自建 TaskBoard 扩展支持递归的成本明显高于接入原生 Subagent
3. `agentscope-extensions-agui` 库的事件模型演进出与 AAF 场景冲突的破坏性变更，且官方 starter/adapter 在届时已支持自定义 `AgentResolver`+跨层治理接管点
4. `ReActAgent.builder()` 与 `HarnessAgent.builder()` 的 API 差异在实测中超出"builder 接口大体一致"的官方承诺，导致迁移成本远超预期

## Pros and Cons of the Options

### 议题一 A（继续自研 AgUiEvent）
- Bad: 与标准 `@ag-ui/client` 的 zod schema 不兼容，四类字段问题导致 livechat 路径完全不可用（已验证）
- Bad: 需要自行维护完整 AG-UI 协议的事件模型定义

### 议题一 C（采用官方 starter）
- Bad: `AguiAgentAdapter` 无法表达 TaskBoard/HITL 语义，绕过 AAF 全部治理链路
- Bad: 线程模型使用 `Executors.newCachedThreadPool()`，丢失 SecurityContext/租户上下文，且违反 ADR-003 全量虚拟线程约束

### 议题二 B/C（原生 Subagent/Plan Mode）
- Good: 复用官方维护的父子事件流转发（`source` 路径 + 2.0.2 新增 `SubagentEventConverter`）、后台任务反向通知、递归深度硬保护
- Bad: 无法在不新增 Permission ASK 拦截层的前提下保留"执行前仲裁"约束
- Bad: 引入工作区这一全新集成面，需要接入租户隔离与沙箱授权链
- Bad: 持久化模型与现有 `DelegatedTaskEntity`/outbox 表不兼容，需迁移或桥接

### 议题三 A（保持 HarnessAgent）
- Good: 保留未来选择性启用 Harness 原生能力（Plan Mode/Subagent）的低成本路径
- Bad: 已确认的对象构造开销与 `WaitAsyncResultsTool` 工具泄漏持续存在
- Bad: 15 个 `disableXxx()` 构成持续维护面，且依赖上游未来版本不默认新增能力（已有 `agentscope-usage-guide.md` 规则要求验证 builder 默认值）

## More Information

- 输入材料：`docs/design/audit/2026-08-30-agentscope-boundary/`（01-reuse-map.md、02-runtime-quality.md、03-capability-gap.md、04-reuse-correctness.md、05-evolution-options.md）
- 本次对话验证的关键代码位置：
  - AG-UI：`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AgUiProjector.java`（本次改写）、`AssistantAguiController.java`
  - 双层循环：`apps/service/aaf-framework/.../assistant/application/DelegatedTaskCoordinator.java`（`decodeAndValidatePlan`、`TaskBoard.coordinated`）、`CoordinationPlan.java`、`TaskBoard.java`
  - 编译路径：`apps/service/aaf-framework/.../infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java`
- 后续动作：
  1. 修复 RQ-01（终态仲裁 blocker）——`HarnessAgentExecutionAdapter` 五个 `AtomicBoolean` 收敛为单一原子状态枚举
  2. 依次修复 9 条 major（02-runtime-quality.md「上线前修复」「近期加固」清单）
  3. 评估并执行议题三（`HarnessAgent` → `ReActAgent` 编译目标切换），独立于 RQ-01 修复，不与状态机改动混合提交
  4. `PermissionMode` 采纳评估（04 文档已列入"应改造为官方扩展点"）
- 待关闭的编号错位：本 ADR 编号为 005，修正前 ADR 索引因 `ADR-003-virtual-threads-over-webflux.md` 正文误标 `ADR-004` 而产生的编号不一致（见 `docs/design/audit/2026-08-30-agentscope-boundary/05-evolution-options.md` 遗留提示），本次已同步修正。
