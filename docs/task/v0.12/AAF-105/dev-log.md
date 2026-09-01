## #10506 协调者计划提交改用工具调用（2026-09-01）

- ✅ developer-service：新建 `SubmitCoordinationPlanTool`，删除 `decodeAndValidatePlan`，`compile` 通过

### 起因：#10503 结构化输出可行性调查引出的架构发现

调查 L0 gateway 是否需要支持结构化输出（#10503）时，人类提出"协调者/执行者拆分任务是否可以用结构化输出"。核实 `tmp/agentscope-java` 官方 `ReActAgent` 源码：

- `call(msgs, structuredOutputClass, ctx)` 支持原生 `response_format` 与合成 `generate_response` 降级两条路径，但两者的关键实现（`buildAgentStream` 收敛逻辑、`doFallbackStructuredCall`）**均为 `private`**，无法从 `streamEvents` 场景复用
- `streamEvents` 与 `call` 共享同一个 `buildAgentStream` 内核，但 `call` 内部把中间事件 `.filter(e -> e instanceof AgentResultEvent)` 过滤掉了，只留最终结果——这是 API 设计选择，不是能力缺失，但 AAF 无法绕过
- 协调者当前依赖 `streamEvents` 产出的完整事件流：`MODEL_CALL_END`（Token 计量挂在这里）、`TEXT_BLOCK_*`（AG-UI 流式呈现）、`REQUIRE_USER_CONFIRM`（HITL）等，改用 `call()` 会让协调者这次 execution 失去这些能力，是不可接受的退化

### 采用方案：不用结构化输出，改用工具调用（复用 AAF-107 SubmitExecutorPlanTool 模式）

工具调用本身产生 `TOOL_CALL_START`/`TOOL_RESULT_END` 事件，天然在 `streamEvents` 事件流内，不需要在"事件流"和"结构化约束"之间二选一——这是解开最初"是否要放弃事件流"这个假两难的关键认识。

### 实现文件

| 文件 | 说明 |
|------|------|
| `.../assistant/application/SubmitCoordinationPlanTool.java` | 新增：协调者 execution 内唯一允许调用的写工具，业务规则从 `decodeAndValidatePlan` 原样迁移 |
| `.../assistant/model/InvocationPolicy.java` | `COORDINATOR.instruction()` 改为要求调用工具，不再要求输出严格 JSON |
| `.../assistant/application/DelegatedTaskCoordinator.java` | 删除 `decodeAndValidatePlan` 及 6 个专属辅助方法（`iterationGroup`/`aggregationContract`/`inputBindings`/`stringSet`/`optionalPositive`/`optionalBoolean`）与未使用常量 `MAX_COORDINATION_PLAN_CHARS`；协调者分支改为重新查询最新 `TaskBoard` 判断协调者节点是否已转 `COMPLETED` |
| `.../infrastructure/assistant/spring/AssistantInfrastructureAutoConfiguration.java` | 新增 `planRequirementPolicy`（恒 false，与既有保守默认一致）、`submitCoordinationPlanTool` 两个 Bean |
| `SubmitCoordinationPlanToolTest.java` | 新增（偏离阶段约束，见下） | 迁移授权衰减边界断言 |
| `DelegatedTaskCoordinatorAuthorizationTest.java` | 删除（所测方法已不存在） | — |

### 关键设计决策

> **"只说不做"判定依据**：`TaskBoard.applyCoordinationPlan` 内部前置条件要求 `coordinator.status() == Status.RUNNING` 才允许执行，成功后把协调者节点标记为 `coordinator.completed(plan.goal())`。这给了协调者分支一个明确判据——execution 结束后重新查询最新 board（不能信任内存里的旧 `board` 快照，那是 `executeBoard` 递归开始前的一次性查询，工具调用发生在 execution 期间不会同步更新它），协调者节点若仍是 `RUNNING` 说明模型从未调用工具，转失败重试；若已是 `COMPLETED` 说明提交成功。

> **业务规则依赖运行时状态的反查方式**：`decodeAndValidatePlan` 原本靠 `AssistantCommand`/`DecompositionBudget` 参数直接持有 `board`/`taskModelSelection`；工具执行时只有 `ToolInvocation.context()`（`InvocationContext`，不携带这些字段）。核实确认 `TaskBoard.SubTask` 已持久化 `roleKey`/`skillKey`/`modelSelection`，工具内部通过 `TaskBoardPort.find(tenantId, taskId)` 反查协调者节点自身即可拿到全部授权衰减基准，不需要额外设计传参路径。

> **不借机启用协调者建议规划能力**：`SubmitCoordinationPlanTool` 需要 `PlanRequirementPolicy` 依赖，之前该类型没有 Bean 定义（`DelegatedTaskCoordinator` 旧构造器固定传恒 false lambda，未走 Bean）。新增 Bean 时特意保持恒 false 默认值，不用 `respectCoordinatorSuggestion()`——是否启用协调者建议规划是 AAF-107 记录过的独立决定，不因这次改动顺带打开。

### 已核实但未采用的方向：ToolBase 直接继承

人类提出"为什么不直接用官方 `ToolBase`"。核实确认 AAF 已经用了 `ToolBase`——`PortBackedAgentTool extends ToolBase` 是唯一真实工具边界，业务工具（`SubmitCoordinationPlanTool`/`SubmitExecutorPlanTool` 等）实现的是 AAF 自己的 `ContextAwareToolHandler`，通过 `RegistryToolPortAdapter`/`PortBackedAgentTool` 统一桥接进 `Toolkit`。这是刻意的两层设计：`PortBackedAgentTool.callAsync` 统一转发到 `DefaultToolGateway.invoke`，保证没有工具能绕过 AAF 自己的授权链路（`AuthorizationGrantPort`/HITL）；`checkPermissions` 未覆盖走 core 默认 `passthrough`，AAF 把等价判定放在 `callAsync` 内部（`ApprovalRequiredException`→`ToolSuspendException`），功能等价，不是安全缺口。本次沿用既有模式，不改。

### 验证

- 按人类要求本次不执行 `pnpm nx test service`；已执行 `pnpm nx compile service`，BUILD SUCCESS（6 模块全绿，含新测试文件编译）。
- 人工核对：`SubmitCoordinationPlanToolTest` 三个测试场景（基准内通过、越权 Role 拒绝、越权 Skill 拒绝）与已删除的 `DelegatedTaskCoordinatorAuthorizationTest` 原有断言逐一对应，行为语义不变（同样的输入组合产生同样的拒绝/通过结果）。

---


## #10501 立 ADR：L0 模型选择与 fallback 语义

## #10502 装配 L0 专用零工具 ReActAgent 单例与 Function Contract 注入

- ✅ 2026-09-01 — developer-service：新增 4 个类，重写 `PromptInvocationGateway`，`compile` 通过

### 实现文件

| 文件 | 说明 |
|------|------|
| `.../core/prompt/FunctionContractSystemPrompt.java` | 新增：per-call system prompt 载体 record |
| `.../infrastructure/agentscope/middleware/FunctionContractPromptMiddleware.java` | 新增：`onSystemPrompt` 钩子，未携带契约直接失败 |
| `.../infrastructure/agentscope/compiler/BoundedAgentCache.java` | 新增：从 `AgentScopeSpecCompiler` 私有嵌套类提取为独立公共类 |
| `.../infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java` | 删除内联 `BoundedAgentCache` 定义，改用提取后的独立类；清理未使用 import（`LinkedHashMap`/`Map`/`Function`） |
| `.../infrastructure/agentscope/model/L0ReActAgentFactory.java` | 新增：按 `modelId` 分桶缓存零工具 `ReActAgent` 实例 |
| `.../infrastructure/agentscope/model/AgentScopeModelResolver.java` | 新增 `resolve(AiModel)` 公共重载 |
| `.../core/prompt/PromptInvocationGateway.java` | 重写：依赖 `Function<AiModel,ReActAgent>` + `CapabilityRouter`，不再依赖 `LlmClient`；`call(...)` 返回 `ModelInvocationResult` |
| `.../infrastructure/agentscope/spring/AgentScopeInfrastructureAutoConfiguration.java` | 新增 `l0ReActAgentFactory`/`promptInvocationGateway` 两个 Bean；`@AutoConfigureAfter` 追加 `AiAutoConfiguration` |
| `.../infrastructure/assistant/spring/AssistantInfrastructureAutoConfiguration.java` | 删除 3 处基于 `LlmClient` 的 Bean 定义，改为 `@ConditionalOnBean(PromptInvocationGateway.class)` |
| `.../assistant/application/DefaultRoleSelector.java`、`DefaultInputClassifier.java`、`ModelDrivenTaskComplexityAnalyzer.java`、`ModelSkillSelectionPort.java` | 同步改为读 `response.text()` |

### 关键发现与设计推翻

> **单例假设被推翻**：最初任务描述设想"L0 专用 `ReActAgent` 单例"，编码时发现 `ReActAgent.builder().model(Model)` 是构造期固定参数，而 `CapabilityRouter.resolve(...)` 是 per-call 路由——同一 `routeScene` 在不同租户/用户偏好下可能解析出不同 `AiModel`，全局单例会让路由结果失效（永远打到装配时选定的模型）。改为按 `AiModel.getModelId()` 分桶缓存多个 `ReActAgent` 实例，模型种类有限（个位数到几十个），构建成本可接受。

> **复用 `AgentScopeSpecCompiler` 已有的 `BoundedAgentCache`，不重新发明缓存**：该私有嵌套类已解决"有界 LRU + 淘汰即 close"这一确定性语义（RQ-06），提取为独立公共类后 L0 场景直接复用，避免写一个更差的 `ConcurrentHashMap`（无淘汰机制，模型配置变化后旧实例永远不会释放）。

> **新旧 `PromptInvocationGateway` Bean 构造器冲突无法与调用方迁移分割处理**：`PromptInvocationGateway` 构造器签名变更后，`AssistantInfrastructureAutoConfiguration` 里基于 `LlmClient` 的旧 Bean 定义立即编译失败（找不到匹配构造器），不存在"只改 #10502、留旧 Bean 到 #10504 再删"的中间状态。因此本任务实际完成了原属于 #10504 的部分范围（4 个已接入 gateway 的调用点适配 + 3 处 Bean 定义清理），#10504 已同步收窄，仅保留 5 个直接依赖 `LlmClient` 的类（未接入 gateway，可独立处理，不受本次签名变更直接影响）。


- ✅ 2026-09-01 — Kiro：产出 [ADR-007](../../../design/adr/ADR-007-l0-model-invoker-unification.md)，人类审核通过复用零工具 `ReActAgent` 方案

### 方案推翻记录

ADR-007 v1.0.0 最初方向是新建 `NonAutonomousModelInvoker` 端口直调 core `Model.stream(...)`，自行实现消息映射、流式收敛、结构化输出路径选择。人类提出"直接复用 ReActAgent，保留基础能力影响不大"的方向后，读官方 [Agent](https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html) 文档与 `ReActAgent.java` 源码核实：

- `ReActAgent` 支持零工具（空 `Toolkit`）+ 无状态（不设 `stateStore`）配置，行为退化为一次系统提示词+消息→模型→返回文本的调用，与自建端口想做的事高度重合
- `ReActAgent.builder().fallbackModel(...)` 是可选参数，不设置即可满足"不静默切模型"要求，零额外代码
- **发现真实限制**：核实 `ReActAgent.doStructuredCall` 源码（`ReActAgent.java:1040-1074`）确认，结构化输出的降级路径（原生失败或不支持时自动切到合成 `generate_response` 工具）**硬编码无法通过 builder 参数关闭**——`useNative=false` 时直接走降级分支，原生路径异常时 `onErrorResume` 自动降级。这与"模型不支持时直接失败，不允许静默降级为工具循环"的决策冲突，因此 AAF 侧必须在调用 `ReActAgent.call(msgs, schemaClass, ctx)` 之前用 `model.supportsNativeStructuredOutput()` 做前置检查，不满足直接 fail-closed
- `onSystemPrompt(Agent, RuntimeContext, String)` middleware 钩子可以对基础 prompt 做 per-call 改写（参照官方 `PlanModeMiddleware.onSystemPrompt` 用法），因此不需要按 Function Contract 建多个 `ReActAgent` 实例，一个 L0 专用单例 + middleware 即可

**结论**：选项 C（复用 `ReActAgent`）比选项 B（自建端口）省掉一整套官方已验证的结构化输出路径选择、流式收敛、消息映射逻辑；代价是需要在结构化输出前做一次前置能力检查（唯一无法通过配置规避的官方硬编码行为），以及未来需要持续审查 `ReActAgent` 新增默认能力是否与 L0"确定性失败、无状态"语义冲突。这是一次性设计成本换取长期维护成本降低，人类拍板采纳。

ADR-007 已改写为 v2.0.0，`tasks.md` #10502～#10505 同步更新为复用 `ReActAgent` 的落地范围。

