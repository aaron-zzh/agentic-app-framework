---
level: Practice
layer: Principle
purpose: 定案 AAF 对 AgentScope Java v2 的复用边界、AG-UI 协议对齐方式与双层编排模型的本质与去向
status: proposed
version: 1.2.0
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

### 与评估推荐路线的关系（本 ADR 改判 05）

本 ADR **改判** `05-evolution-options.md` 的单一推荐。评估推荐的路线三实质是"以 Harness 为执行面 + 不自研 harness 层 + 第三阶段分批启用 Harness 原生能力"；本 ADR 的议题二选 A（不用原生 Subagent/Plan Mode）与议题三选 B（编译目标改为 core 的 `ReActAgent`）合起来抽掉了这两个支柱，实际采纳的是：

> **路线二的执行面骨架（Agent 外层的 builder、生命周期、缓存、middleware 装配、中断、能力启停由 AAF 自持）+ 路线三第二阶段的扩展点迁移（横切治理迁入 core 的 Middleware/Toolkit/AgentStateStore/repository 扩展点）。**

未被改判、继续有效的部分：05 第一阶段的生产正确性封口（RQ-01 blocker + 9 项 major + `JsonSchemaUtils` shadow 退出）、第二阶段的扩展点迁移清单、路线定义与六维矩阵作为事实基线。已失效的部分：路线三第三阶段的原生能力启用计划（仅 `PermissionMode` 例外——它是 `ReActAgent.builder()` 基础参数，非 Harness 专属），以及切换判据中以 `HarnessAgent` 为执行面前提的两行（"Harness 默认表面持续漂移""上游提供稳定最小模式"），后者由本 ADR 的 Reversal Triggers 取代。

需要指出 05 的一处表述瑕疵：其路线三定义写作"继续以 `HarnessAgent`/`ReActAgent` 作为执行面，不新建 AAF ReAct 或自研 Harness"，把 `ReActAgent` 也纳入路线三，与同句"不自研 Harness"自相矛盾。区分路线二与路线三的真正判据是**谁拥有 Agent 外层装配与生命周期**，不是编译目标类名——按此判据，AAF 当前（`AgentScopeSpecCompiler` + `HarnessAgentExecutionAdapter`）已经在自持该层。

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
3. **入参模型不采用官方 `RunAgentInput`，但字段形状对齐协议**——官方 Java 类的注解是 Jackson 2 的，在 Spring Boot 4 的 Jackson 3 `HttpMessageConverter` 下不生效（该类无无参构造与 setter，`AguiMessage.content` 还依赖 Jackson 2 自定义 (de)serializer），且它落后于协议（`@ag-ui/core@0.0.57` 的 `RunAgentInputSchema` 有 `parentRunId`，Java 类没有）。技术上并非不可能——出口方向就是绕过 Spring converter 显式用 `AguiEventEncoder`，入口同理可用 Jackson 2 mapper 手工 `readValue`——但那要为一个端点引入第二套 JSON 栈、放弃 Bean Validation 与 Spring 的 400 语义，并把上游类的版本滞后变成 AAF 的契约缺口，收益不抵代价。因此 AAF 自定义入参 record，**但字段形状与协议一一对齐**：`threadId`、`runId`、`parentRunId`、`forwardedProps`、`state`、`messages`、`tools`、`context`。

   **2026-09-01 补充决策：调用参数从 `state` 迁到 `forwardedProps`。** 协议里 `state` 是线程级双向共享状态（可被 `STATE_SNAPSHOT`/`STATE_DELTA` 回吐、被客户端持久化回放），`forwardedProps` 是本次 run 的一次性单向透传参数；官方 starter 自身即用 `forwardedProps.agentId` 做路由（`AguiRequestProcessor`）。原实现把 `mode`/`request`/`assistantId`/`taskModelSelection`/`teamId`/`teamVersion` 放在 `state`，属语义错位：一旦服务端开始发 state 快照、或客户端持久化 thread state，上一轮的一次性参数会被当共享状态回放。现已全部迁入 `forwardedProps`，`state` 只留页面感知上下文。客户端侧 `@assistant-ui/react-ag-ui` 的 `useAgUiRuntime` 只接受 `agent`、拿不到 `RunAgentParameters` 注入点，因此以 `ForwardedPropsHttpAgent`（覆写 `HttpAgent.run`）注入。这是破坏性契约变更，前后端同批次提交。
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

## 勘误与决策补充（2026-09-01 核实）

对 AgentScope 2.0.2 源码逐项复核后，本 ADR 有三处需要更正或补齐。**三个议题的结论均不变**，更正的是论据与已过期表述。核实过程与证据见 [2026-09-01 Harness 落地计划](../audit/2026-09-01-harness-landing-plan.md)。

### 议题一论据更正：官方 starter 确有扩展点

上文「核心论据」第 1 条与「议题一 C」列出的理由中，"`AguiMvcController.Builder` 无 `agentResolver()` 注入点""`AguiAgentAdapter` 无法发出 `Custom`/`ActivitySnapshot`/`outcome.interrupt`"**失实**：

- `io.agentscope.core.agui.processor.AguiRequestProcessor.Builder` 明确提供 `agentResolver()` 与 `adapterFactory()` 两个注入点
- `Custom` 与 interrupt outcome 已内置（`CustomAgentEventConverter`、`AgentLifecycleEventConverter`），Activity 可由自定义 converter 发出
- starter 会收集 converter / enricher / adapterFactory bean

**"不采用 starter"的结论保留**，但正确理由是：`AguiMvcController.Builder` 本身仍不暴露 resolver，默认以 registry 的单 Agent 为执行货币，与 AAF"一个任务可含多节点"的执行粒度不匹配；官方 `AguiResumeCoordinator` 的 active run 与 pending interrupt 都只是进程内 `ConcurrentHashMap`，无法承载 AAF 持久 HITL；starter 的 cached thread pool 丢失 SecurityContext/租户上下文且违反 ADR-003；starter 无法替代 TaskBoard、租约、画像冻结与事件溯源入口。

### 已过期表述：RQ 工作量

「Negative Consequences」中"已确认的 13 条 RQ 仍需逐条修复，本 ADR 不改变这份工作量"已过期。2026-08-31 复评结论：RQ-01 blocker 与 RQ-02～RQ-10 九条 major 均已修复并有测试锁定，剩余 RQ-11/12/13 三条 minor 不阻断上线。本 ADR「后续动作」第 1、2 项已标记完成。`docs/design/audit/2026-08-30-agentscope-boundary/02-runtime-quality.md`

### 补充定案：依赖坐标降为 core

「后续动作」第 4 项已定案：`aaf-framework` 直接依赖由 `io.agentscope:agentscope-harness` **降为 `io.agentscope:agentscope-core`**，不保留 optional/fallback harness。依据是全仓 `io.agentscope.harness.*` 只引入一个类型 `HarnessAgent`（compiler、execution 及两个对应测试共 4 处），切换编译目标后无任何编译依据；保留 harness 会让隐式 workspace 能力重新进入可见 classpath。同批删除两个零使用坐标 `agentscope-extensions-skill-postgresql-repository` 与 `agentscope-extensions-oss`；victools 的两个 `<exclusion>` 原样迁到 core 坐标。`apps/service/aaf-framework/pom.xml`

### 补充定案：`JsonSchemaUtils` shadow 保留

「后续动作」隐含的"删除 shadow"方向不可行。上游 2.0.x 的 `io.agentscope.core.util.JsonSchemaUtils` 仍使用 Jackson 2 的 `com.fasterxml.jackson.databind.JsonNode` 与 victools 4 风格的 `JacksonModule`，AgentScope BOM 钉 `jackson 2.21.1` + `jsonschema-generator 4.38.0`；AAF 钉 victools `5.0.0`（Spring AI on Jackson 3 要求）。两边 API 不兼容，删除 shadow 必然 `NoSuchMethodError`。决定**保留 shadow**，修正其失真的版本注释，并登记为「禁兼容层」硬规则的显式例外；退出路径（独立坐标最小 fork 或上游 PR）另立任务。

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

- 输入材料：`docs/design/audit/2026-08-30-agentscope-boundary/`（01-reuse-map.md、02-runtime-quality.md、03-capability-gap.md、04-reuse-correctness.md、05-evolution-options.md——其中 05 的路线推荐已由本 ADR 改判，标记为 `superseded`）
- 本次对话验证的关键代码位置：
  - AG-UI：`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AgUiProjector.java`（本次改写）、`AssistantAguiController.java`
  - 双层循环：`apps/service/aaf-framework/.../assistant/application/DelegatedTaskCoordinator.java`（`decodeAndValidatePlan`、`TaskBoard.coordinated`）、`CoordinationPlan.java`、`TaskBoard.java`
  - 编译路径：`apps/service/aaf-framework/.../infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java`
- 后续动作：
  1. ~~修复 RQ-01（终态仲裁 blocker）~~ ✅ 已完成（`426a5f51`）——五个 `AtomicBoolean` 收敛为单一原子状态枚举
  2. ~~依次修复 9 条 major~~ ✅ 已完成（2026-08-31）——RQ-02～RQ-10 逐条修复做法见 02-runtime-quality.md「修复记录」
  3. 评估并执行议题三（`HarnessAgent` → `ReActAgent` 编译目标切换），独立于 RQ-01 修复，不与状态机改动混合提交
  4. ~~议题三落地后决定依赖坐标去向~~ ✅ 已定案（2026-09-01）——降为 `agentscope-core`，见「勘误与决策补充」
  5. `PermissionMode` 采纳评估（04 文档已列入"应改造为官方扩展点"）
  6. 落地执行按 [2026-09-01 Harness 落地计划](../audit/2026-09-01-harness-landing-plan.md) 的七阶段路线拆任务
- 待关闭的编号错位：本 ADR 编号为 005，修正前 ADR 索引因 `ADR-003-virtual-threads-over-webflux.md` 正文误标 `ADR-004` 而产生的编号不一致（05-evolution-options.md 初版曾提示此项），本次已同步修正，该遗留提示已在 05 与 audit README 中标注失效。
