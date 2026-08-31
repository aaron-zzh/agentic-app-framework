---
level: Reality
layer: Model
purpose: 审计 AAF 对 AgentScope Java v2 扩展点的复用正确性、重复建设边界与收敛路线
status: draft
version: 1.1.0
date: 2026-08-31
author: AaronZZH
changelog:
  - 2026-08-30 初版，重复能力清单 18 项
  - 2026-08-31 追加「后续修正」：新增 PermissionMode 重复项，清单增至 19 项
---

# AgentScope 复用正确性与重复建设审计

## 结论摘要

AAF 对 AgentScope 的总体使用方式不是“重新实现 Agent”，而是以 Harness ReAct loop 为执行骨架，把工具、模型、状态、调用上下文和中间件接入官方扩展点，再由 AAF 保留多租户、权限审批、计量、冻结画像、事件持久化和委托任务等治理权威。这个方向成立，但边界尚未完全收敛：七个必查扩展面中，Toolkit、Model、RuntimeContext 接法正确；Middleware 和 AgentStateStore 接线正确但上层语义不完整或职责错位；Msg/ContentBlock 与 AgentEvent 明确损失官方结构语义。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java`（`AgentScopeSpecCompiler`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java`（`HarnessAgentExecutionAdapter`）。

核心判断如下：

- **官方扩展点没有被整体“用错”**。`ToolBase`/`registerAgentTool`、官方 `Model`、`RedisAgentStateStore.clientAdapter(...)`、`MiddlewareBase.onModelCall(...)`、per-call `RuntimeContext` 都是正式扩展路径。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/PortBackedAgentTool.java`（`PortBackedAgentTool`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/ToolBase.java`（`ToolBase`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/Toolkit.java`（`Toolkit`）。
- **消息与事件边界是当前最大能力差距**。AAF 消息入向只保留文本和 URL 图片；事件出向仅显式处理官方 31 个事件类型中的 13 个，默认丢弃 18 个，导致工具参数/结果增量、多模态数据流、外部执行恢复、子智能体暴露、Hint 与自定义事件无法到达 AAF/AG-UI 前端。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeMessageMapper.java`（`AgentScopeMessageMapper`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeEventMapper.java`（`AgentScopeEventMapper`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/AgentEventType.java`（`AgentEventType`）。
- **Harness 是有意降维而非无意识漏用**。AAF 关闭 Harness 的 memory、workspace、subagent、dynamic skill、filesystem/shell、compaction、tool-result eviction 和 tracing log，只借 ReAct 推理循环与工具调用，避免出现第二套业务真理源；代价是官方能力升级不会自然进入 AAF。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java`（`AgentScopeSpecCompiler`）；`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java`（`HarnessAgent`）。
- **`JsonSchemaUtils` 同名覆盖必须退出**。它是 binary-name classpath shadowing 兼容 shim，不是稳定扩展点，直接违反 AAF“禁兼容层”约束。证据：`apps/service/aaf-framework/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（`JsonSchemaUtils`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（`JsonSchemaUtils`）；`.kiro/skills/coding-standards/SKILL.md`（编码硬约束）。
- **重复能力清单共 18 项**：应删除复用官方 3 项、应保留自研 7 项、应改造为官方扩展点 8 项。这里的“删除”是删除重复的通用 provider/protocol 代码，不是删除 AAF 的租户治理、审批、计量、领域状态与审计模型。证据见“重复能力清单”逐行源码。

## 审计范围与判定口径

本审计以源码为准，不根据 Maven 模块名推断能力。官方侧检查了 `agentscope-core` 的接口及内置实现，并打开 extensions、harness、Spring Boot starter 的代表实现；AAF 侧检查了 AgentScope 基础设施适配、执行编译、AG-UI 接口及 memory/knowledge/tool/skill/task/model 等包的代表类。`tmp/agentscope-java/` 是本仓库内参考源码，不参与 AAF 构建。证据：`AGENTS.md`（AAF 指针文档）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/agent/RuntimeContext.java`（`RuntimeContext`）。

判定术语：

| 判定 | 含义 |
| --- | --- |
| 用对 | 通过官方公开接口、builder 或可替换抽象接入，生命周期和语义基本符合契约 |
| 用错 | 调用了官方能力，但违反其状态、事件、并发或生命周期语义 |
| 绕过 | 没有使用官方提供的自动路径，改走另一个正式接口或 AAF 自有路径；绕过不必然是错误 |
| 完全重叠 | 输入输出、生命周期及主要职责可由官方实现直接替代 |
| 部分重叠 | 通用机制重叠，但 AAF 仍有不可删除的领域治理职责 |
| 表面相似实质不同 | 名称或存储介质相似，但服务的聚合、协议或一致性目标不同 |

类数量为按相关包与代表类做的约数，只用于判断维护面，不是精确资产盘点。无法从已打开源码确认的事项明确标为“未验证推断”。

## 官方扩展点复用正确性

### Toolkit 与 `@Tool`

**判定：用对正式 programmatic 扩展点；绕过 `@Tool` 反射注册和官方注解式 Schema 自动生成；不是用错。**

`AgentScopeToolkitFactory` 先按 `ToolRef` 精确版本解析 AAF `ToolDefinition`，校验数量与顺序，然后调用 `Toolkit.registerAgentTool(...)`。`PortBackedAgentTool extends ToolBase`，通过 builder 显式设置 name、description、inputSchema、readOnly 和 concurrencySafe，并把执行强制导向 AAF `ToolGatewayPort`。官方 `Toolkit` 同时支持 `registerTool(Object)` 扫描 `@Tool` 和 `registerAgentTool(AgentTool)`；官方 `ToolBase` 本身就是 programmatic 工具基类，因此 AAF 没使用 `@Tool` 不构成错误。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/AgentScopeToolkitFactory.java`（`AgentScopeToolkitFactory`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/PortBackedAgentTool.java`（`PortBackedAgentTool`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/Toolkit.java`（`Toolkit`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/ToolBase.java`（`ToolBase`）。

Schema 没有走 `@ToolParam` → `ToolSchemaGenerator` → `JsonSchemaUtils.generateSchemaFromType()` 的自动路径，而是使用 AAF 目录中已版本化的 `ToolDefinition.inputSchema()`，再交给 `ToolBase.inputSchema(...)`。这是“绕过自动生成、使用官方显式 Schema 接口”，符合 AAF 工具定义是唯一真理源、所有调用必须经过权限与审计网关的约束。风险在于 AAF 必须自己保证 JSON Schema Draft、`$defs`、required 和 provider 方言兼容性。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/PortBackedAgentTool.java`（`PortBackedAgentTool`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/ToolSchemaGenerator.java`（`ToolSchemaGenerator`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（`JsonSchemaUtils`）。

工具挂起语义也使用了官方异常：AAF 审批或人工移交转为 `ToolSuspendException`，使 Agent 挂起而非把审批当普通失败；但 `concurrencySafe(true)` 是无条件声明，只有当所有 `ToolGatewayPort` 实现及目标工具确实支持并发时才成立，当前未逐个验证底层工具，属于**未验证推断**。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/PortBackedAgentTool.java`（`PortBackedAgentTool`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/ToolSuspendException.java`（`ToolSuspendException`）。

### Msg 与 ContentBlock

**判定：部分用对，但结构映射明显不完整；对非图片附件存在误映射风险。**

官方 `ContentBlock` 是包含 `TextBlock`、`ImageBlock`、`AudioBlock`、`VideoBlock`、`ThinkingBlock`、`ToolUseBlock`、`ToolResultBlock`、`HintBlock`、`DataBlock` 的 sealed hierarchy；`Msg` 还携带 role subtype、metadata、timestamp 与 usage，并按 USER/SYSTEM 角色限制合法块类型。AAF 始终生成一个 `TextBlock`，把每个 attachment 无条件构造成 `ImageBlock(URLSource)`，拒绝 `REASONING`，也不保留 ToolUse、ToolResult、Thinking、Hint、Data、Audio、Video、metadata、timestamp、usage。若 `AgentMessage.Attachment` 未来承载音频或视频，当前 mapper 仍会把其 MIME 与 URL 包成图片，这是语义错误而不仅是能力缺失。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeMessageMapper.java`（`AgentScopeMessageMapper`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/message/ContentBlock.java`（`ContentBlock`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/message/Msg.java`（`Msg`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/message/ThinkingBlock.java`（`ThinkingBlock`）。

这会破坏“历史消息原样回放”：AAF 的工具历史只能退化为文本，推理历史被直接拒绝，多模态历史无法按官方块类型重建。应把 AAF `AgentMessage` 演进为块级联合类型，或在基础设施边界增加显式 MIME 分派和 Tool/Reasoning block 映射；不能继续以 `text + attachments-as-image` 声称完整映射。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/agent/model/AgentMessage.java`（`AgentMessage`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeMessageMapper.java`（`AgentScopeMessageMapper`）。

### Model

**判定：用对。AAF 保留模型治理，但执行时直接返回官方 `Model`，没有再包一层 Spring AI 模型。**

`AgentScopeModelResolver.resolve(...)` 从 AAF `ModelManagementService` 获取数据库模型、校验 CHAT capability、解析密钥，然后构建官方 extensions 的 `AnthropicChatModel`、`DashScopeChatModel` 或 `OpenAIChatModel`，返回类型就是 `io.agentscope.core.model.Model`。这符合官方 `Model.stream(List<Msg>, List<ToolSchema>, GenerateOptions)` 契约，也让 Harness 能直接消费 provider 的 structured-output/context-window 能力。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/model/AgentScopeModelResolver.java`（`AgentScopeModelResolver`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/model/Model.java`（`Model`）；`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-model/agentscope-extensions-model-openai/src/main/java/io/agentscope/extensions/model/openai/OpenAIChatModel.java`（`OpenAIChatModel`）。

AAF 合理保留的是数据库真理源、动态密钥、capability 和租户路由，不应重写 provider 协议客户端。当前“其他 provider 统一走 OpenAI compatible”是明确策略，但 provider 的方言兼容程度未在本次审计逐家验证，属于**未验证推断**。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/model/ModelManagementService.java`（`ModelManagementService`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/model/AgentScopeModelResolver.java`（`AgentScopeModelResolver`）。

### AgentState 与 AgentStateStore

**判定：底层接线用对；上层把持久状态当防御探针，职责错位。**

官方 `AgentStateStore` 以 `(userId, sessionId, key)` 读写 `State` 或状态列表。官方 `RedisAgentStateStore` 的 builder 公开 `clientAdapter(...)`；AAF `SpringRedisClientAdapter` 完整实现 Redis adapter，用 `StringRedisTemplate` 复用 Spring 连接池，把空返回归一为空集合，`close()` 不关闭 Spring 管理资源，生命周期选择正确。`AgentScopeInfrastructureAutoConfiguration` 也确实构建官方 `RedisAgentStateStore`，没有自写状态序列化协议。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/state/SpringRedisClientAdapter.java`（`SpringRedisClientAdapter`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/spring/AgentScopeInfrastructureAutoConfiguration.java`（`AgentScopeInfrastructureAutoConfiguration`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/state/AgentStateStore.java`（`AgentStateStore`）；`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis/state/RedisAgentStateStore.java`（`RedisAgentStateStore`）；`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis/state/RedisClientAdapter.java`（`RedisClientAdapter`）。

问题在 `HarnessAgentExecutionAdapter.requireNoHiddenPersistentHistory(...)`：每次执行前读取官方 `agent_state`，一旦 context 或 summary 非空就拒绝调用。与此同时编译器仍把同一个 store 注入 Harness，使 Harness 具备恢复和追加状态的能力。于是 AgentStateStore 既是运行时依赖，又不允许真正承载历史；它从恢复机制退化为“检测到官方恢复行为就报错”的防御探针。这说明 AAF 的冻结画像与 Harness 隐式历史尚未形成单一职责。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java`（`HarnessAgentExecutionAdapter`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java`（`AgentScopeSpecCompiler`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/state/AgentState.java`（`AgentState`）。

推荐二选一并写成契约：要么 AAF 负责完整消息画像，Harness 以真正无历史模式运行并不保存 `agent_state`；要么正式纳入官方恢复状态，在冻结 `PromptEnvelope` 前把恢复后的 context/summary 计入预算与哈希。当前“允许写、下一次发现非空再拒绝”不是稳定终态。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/middleware/PromptEnvelopeCaptureMiddleware.java`（`PromptEnvelopeCaptureMiddleware`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java`（`HarnessAgentExecutionAdapter`）。

### Middleware

**判定：扩展点用对，冻结快照语义不完整。**

官方 `MiddlewareBase` 提供 `onAgent`、`onReasoning`、`onActing`、`onModelCall`、`onSystemPrompt` 五个 around 扩展点，内置 middleware 通过 `next.apply(input)` 维持 onion chain，并用 defer/context 保持惰性。AAF 在 `onModelCall` 内先异步持久化冻结信封，再通过 `Flux.defer(() -> next.apply(input))` 发起真实模型调用；缺 typed `InvocationContext` 时 fail closed。该切点和调用顺序符合官方契约。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/middleware/PromptEnvelopeCaptureMiddleware.java`（`PromptEnvelopeCaptureMiddleware`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java`（`MiddlewareBase`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/TaskReminderMiddleware.java`（`TaskReminderMiddleware`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tracing/OtelTracingMiddleware.java`（`OtelTracingMiddleware`）。

缺口是 `messageSnapshots(...)` 只哈希 `Msg.getTextContent()`。同一文本但不同图片、音频、DataBlock、ToolUse/ToolResult 或 ThinkingBlock 会得到相同消息快照，因而 `promptSha256` 不能证明真实 provider 请求完全相同；`String.valueOf(tool.getParameters())` 与 `additionalBodyParams` 也未证明是稳定 canonical JSON。应按 `ContentBlock` 类型逐块规范化，并使用确定性 JSON 序列化。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/middleware/PromptEnvelopeCaptureMiddleware.java`（`PromptEnvelopeCaptureMiddleware`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/message/ContentBlock.java`（`ContentBlock`）。

### AgentEvent 与 AgentEventType

**判定：使用官方事件流，但没有穷尽；默认丢弃造成可观察能力缺失。**

官方 `AgentEventType` 当前有 **31** 个 canonical 枚举项。AAF switch 显式处理 **13** 个：`AGENT_START`、`AGENT_RESULT`、`AGENT_END`、`MODEL_CALL_START`、`MODEL_CALL_END`、`TEXT_BLOCK_START`、`TEXT_BLOCK_DELTA`、`TOOL_CALL_START`、`TOOL_RESULT_END`、`REQUIRE_USER_CONFIRM`、`REQUEST_STOP`、`EXCEED_MAX_ITERS`、`ALL_TOOLS_DENIED`。其余进入 `default -> Optional.empty()`。证据：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/AgentEventType.java`（`AgentEventType`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeEventMapper.java`（`AgentScopeEventMapper`）。

默认丢弃的 **18** 类为：

- 文本终止：`TEXT_BLOCK_END`；
- 思考流：`THINKING_BLOCK_START`、`THINKING_BLOCK_DELTA`、`THINKING_BLOCK_END`；
- 数据/多模态流：`DATA_BLOCK_START`、`DATA_BLOCK_DELTA`、`DATA_BLOCK_END`；
- 工具调用流：`TOOL_CALL_DELTA`、`TOOL_CALL_END`；
- 工具结果流：`TOOL_RESULT_START`、`TOOL_RESULT_TEXT_DELTA`、`TOOL_RESULT_DATA_DELTA`；
- 中断恢复：`REQUIRE_EXTERNAL_EXECUTION`、`USER_CONFIRM_RESULT`、`EXTERNAL_EXECUTION_RESULT`；
- 扩展协作：`SUBAGENT_EXPOSED`、`HINT_BLOCK`、`CUSTOM`。

证据：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/AgentEventType.java`（`AgentEventType`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeEventMapper.java`（`AgentScopeEventMapper`）。

即使在已处理集合中仍有条件丢弃：permission asking/tool suspended 的 `REQUEST_STOP` 只改状态不发布事件；非 RUNNING 状态的 `AGENT_END` 不发布；工具输入与思考内容被主动隐藏。隐藏 chain-of-thought 和敏感工具参数是合理安全策略，但“隐藏原文”不等于“删除生命周期事件”：可以保留 redacted start/delta/end、大小、哈希和状态。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeEventMapper.java`（`AgentScopeEventMapper`）。

前端实际影响已经可见：AAF `AgUiEvent` 定义了 `TOOL_CALL_ARGS`，但上游 mapper 丢弃 `TOOL_CALL_DELTA`，`AgUiProjector` 也没有投影 args；因此前端无法获得流式工具参数。类似地，reasoning、多模态 data、外部执行恢复、subagent、hint/custom 都没有完整协议输出。应新增“显式处置矩阵”：每个官方枚举必须映射、脱敏映射或带理由拒绝，禁止 `default` 静默吞掉未来新增事件。证据：`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/chat/agui/AgUiEvent.java`（`AgUiEvent`）；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AgUiProjector.java`（`AgUiProjector`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeEventMapper.java`（`AgentScopeEventMapper`）。

### RuntimeContext

**判定：per-call 构造和 typed attribute 使用正确；状态寻址策略需要业务确认。**

官方 `RuntimeContext` 是每次调用的 metadata 容器，attributes 不持久化，可携带 call-scoped `AgentState`，并可投影为工具执行上下文。AAF 每次 execute 新建 context，以 typed key 注入完整 `InvocationContext`，工具和 middleware 再按同一 typed key取回，符合官方语义。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeRuntimeContextMapper.java`（`AgentScopeRuntimeContextMapper`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/PortBackedAgentTool.java`（`PortBackedAgentTool`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/agent/RuntimeContext.java`（`RuntimeContext`）。

`stateUserKey` 使用 tenant/user/task，DELEGATED 再加 fencing token，能避免多租户串状态和旧租约脑裂；DIRECT 没有 execution/run 维度，因此同 tenant/user/task/session 可复用状态。若 DIRECT 设计为每次运行完全独立，这个寻址会过宽；若设计为任务会话连续，则合理。现有 `requireNoHiddenPersistentHistory` 又拒绝非空历史，表明这里尚未形成一致产品语义，属于需要决策的边界而非官方 API 使用错误。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeRuntimeContextMapper.java`（`AgentScopeRuntimeContextMapper`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java`（`HarnessAgentExecutionAdapter`）。

## Harness 复用边界

`AgentScopeSpecCompiler` 明确关闭 Harness 的 memory tools/hooks、workspace context、`@path` expansion、subagents/dynamic subagents、dynamic/default workspace skills、tools config、filesystem/shell、compaction、tool-result eviction、skills 与 tracing log。这使 Harness 退化为“可缓存的 ReAct loop + Model + Toolkit + AgentStateStore + Middleware”，而 AAF 自己掌握记忆、技能、任务委托、沙箱、trace 和治理。该策略与 AAF 的五层权威模型一致，不应简单把所有 AAF 能力替换为 Harness 内建能力。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java`（`AgentScopeSpecCompiler`）；`docs/design/framework/component-overview.md`（五层智能架构）。

质量评价：编译缓存键包含 agent/version、最终工具集、prompt hash/content，动态子智能体区分缓存与一次性实例；关闭时释放缓存 Agent；执行适配器用 `Flux.defer` 保证每次订阅独立、按 executionId 防重复执行、串行映射和入库、CAS 保证 interrupt 幂等，并在租约失效时 fail closed。这些是较好的生产化处理。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java`（`AgentScopeSpecCompiler`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java`（`HarnessAgentExecutionAdapter`）。

主要债务不是 ReAct loop 本身，而是关闭官方能力后又在边界层复制通用协议和状态逻辑：事件穷尽性、完整 ContentBlock、AG-UI 转换、AgentState 单一职责及 Schema 兼容都需要收敛。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeEventMapper.java`（`AgentScopeEventMapper`）；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AgUiProjector.java`（`AgUiProjector`）；`apps/service/aaf-framework/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（`JsonSchemaUtils`）。

## 重复能力清单

| 重叠面 | AAF 实现及大致类数量 | 官方具体模块/类与源码事实 | 重叠程度 | AAF 特有约束 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 长期记忆 provider | cognition/memory 与 adapter 约 10+ 类；代表 `JpaCognitionMemoryAdapter`、`RedisSessionMemoryAdapter`。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/cognition/persistence/JpaCognitionMemoryAdapter.java`（`JpaCognitionMemoryAdapter`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/cognition/memory/RedisSessionMemoryAdapter.java`（`RedisSessionMemoryAdapter`） | mem extension 的 `Mem0LongTermMemory`、`ReMeLongTermMemory`、`BailianLongTermMemory` 都实现官方 `LongTermMemory.record/retrieve`，含远端 client 与隔离 metadata。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-mem/agentscope-extensions-mem0/src/main/java/io/agentscope/core/memory/mem0/Mem0LongTermMemory.java`（`Mem0LongTermMemory`）；同模块 `ReMeLongTermMemory`、`BailianLongTermMemory` 源码类 | 部分重叠 | 用户私有记忆、租户隔离、写管道、遗忘、情感数据保护 | **应改造为官方扩展点**：保留 AAF memory port/治理管道，把 Mem0/ReMe/Bailian 作为官方 `LongTermMemory` provider 插件，停止自写同类远端 client |
| 原子记忆与治理记忆 | engine/memory + cognition pipeline 约 35+ 类；代表 `AtomMemoryEngineImpl`、`BundleSearchService`。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/engine/memory/AtomMemoryEngineImpl.java`（`AtomMemoryEngineImpl`）；同包 `BundleSearchService.java`（`BundleSearchService`） | 官方 mem provider 聚焦消息 record/retrieve；已读 `Mem0LongTermMemory` 未提供 AAF 双时态 atom、bundle、价值过滤和固定写管道。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-mem/agentscope-extensions-mem0/src/main/java/io/agentscope/core/memory/mem0/Mem0LongTermMemory.java`（`Mem0LongTermMemory`） | 表面相似实质不同 | 双时态、bundle、时间衰减、价值/情感/程序性分类、治理与可审计遗忘 | **应保留自研**：这是 AAF 认知域，不应退化成 provider SDK |
| RAG、chunker、embedding、vector/search/graph | engine/knowledge 约 70+ 类；代表 `KnowledgeVectorService`、trusted/graph/pipeline。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/engine/knowledge/KnowledgeVectorService.java`（`KnowledgeVectorService`）；同模块 `EntityExtractionService.java`（`EntityExtractionService`） | rag extension 的 `SimpleKnowledge` 完成 embed→store→retrieve；还有 `InMemoryStore`、`PgVectorStore`、`MilvusStore`、`QdrantStore`、`ElasticsearchStore` 及 readers/chunker/embedding。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-rag/agentscope-extensions-rag-simple/src/main/java/io/agentscope/core/rag/knowledge/SimpleKnowledge.java`（`SimpleKnowledge`）；同树 `PgVectorStore.java`（`PgVectorStore`） | 部分重叠 | 知识发布、可信来源、时态版本、图谱投影、权限过滤、RRF/LLM rerank | **应改造为官方扩展点**：保留 AAF knowledge 聚合与治理，底层 reader/chunker/embedding/vector-store provider 优先适配官方接口 |
| Redis AgentState | AAF 约 2 类：Spring adapter + auto-config。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/state/SpringRedisClientAdapter.java`（`SpringRedisClientAdapter`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/spring/AgentScopeInfrastructureAutoConfiguration.java`（`AgentScopeInfrastructureAutoConfiguration`） | 官方 `RedisAgentStateStore` 已实现状态协议并公开 `RedisClientAdapter` 注入。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis/state/RedisAgentStateStore.java`（`RedisAgentStateStore`）；同包 `RedisClientAdapter.java`（`RedisClientAdapter`） | 完全重叠（状态存储）；Spring client bridge 不重叠 | 复用 Spring 连接池、AAF key prefix、tenant/task state addressing | **应保留自研**：仅保留很薄的 Spring client adapter；状态 store 已正确复用官方，禁止再自写 Redis AgentState 协议 |
| Redis cache、lease、checkpoint、task queue | protection/task/assistant lease 等约 20+ 类；代表会话租约、checkpoint 与 Redis queue adapter。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/port/ConversationLeasePort.java`（`ConversationLeasePort`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/shared/event/ExecutionEventStorePort.java`（`ExecutionEventStorePort`） | 官方 `RedisStore` 是 Harness remote filesystem `BaseStore`，提供 namespace、版本与 CAS；`RedisSandboxExecutionGuard` 服务沙箱并发，不是 AAF 业务租约/事件序列。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-redis/src/main/java/io/agentscope/extensions/redis/store/RedisStore.java`（`RedisStore`）；同模块 `RedisSandboxExecutionGuard.java`（`RedisSandboxExecutionGuard`） | 表面相似实质不同 | fencing token、会话所有权、durable task、事件顺序、幂等与审批连续性 | **应保留自研**：不要把通用 `BaseStore` 当业务 lease/checkpoint 模型 |
| PostgreSQL state/base store 与 AAF JPA | AAF JPA repository/adapter 约 30+ 类，承载 Agent 定义、记忆、技能、事件、任务。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/definition/JpaAgentDefinitionAdapter.java`（`JpaAgentDefinitionAdapter`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/agent/AgentDefinitionRepository.java`（`AgentDefinitionRepository`） | 官方 `PostgresAgentStateStore` 实现 AgentState；`PostgresBaseStore` 实现 Harness namespace + CAS store。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-postgresql/src/main/java/io/agentscope/extensions/postgresql/state/PostgresAgentStateStore.java`（`PostgresAgentStateStore`）；同模块 `tmp/agentscope-java/agentscope-extensions/agentscope-extensions-postgresql/src/main/java/io/agentscope/extensions/postgresql/store/PostgresBaseStore.java`（`PostgresBaseStore`） | 表面相似实质不同 | 领域 schema、JPA 聚合、租户权限、Flyway、审计与查询模型 | **应保留自研**：官方 store 只可作为 Harness 状态/文件后端选项，不能替代 AAF 领域持久化 |
| Sandbox | engine/tool sandbox 约 5+ 类；代表 `GraalVmScriptExecutor`、`GovernedSandboxAdapter`、`SandboxPort`。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/engine/tool/GraalVmScriptExecutor.java`（`GraalVmScriptExecutor`）；同域 `GovernedSandboxAdapter.java`（`GovernedSandboxAdapter`） | 官方 `E2bSandbox`、`DaytonaSandbox`、`AgentRunSandbox`、Kubernetes runtime 实现 Harness `Sandbox`，覆盖远程执行、workspace 持久化/恢复。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-sandbox/agentscope-extensions-sandbox-e2b/src/main/java/io/agentscope/extensions/sandbox/e2b/E2bSandbox.java`（`E2bSandbox`）；同树 `DaytonaSandbox.java`、`KubernetesSandboxClient.java` | 部分重叠 | AAF 权限、审批、资源预算、GraalVM 进程内脚本、证据与可逆性 | **应改造为官方扩展点**：保留治理 port，以 adapter 接官方 Sandbox provider；GraalVM 可作为 AAF 自有 provider |
| Skill repository | engine/skill 与 JPA catalog 约 12+ 类；代表 `JpaSkillCatalogAdapter`。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agent/persistence/JpaSkillCatalogAdapter.java`（`JpaSkillCatalogAdapter`） | 官方 `PostgresSkillRepository`/`MysqlSkillRepository`/`GitSkillRepository` 实现 `AgentSkillRepository`，含 CRUD、资源和 metadata。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-postgresql-repository/src/main/java/io/agentscope/core/skill/repository/postgresql/PostgresSkillRepository.java`（`PostgresSkillRepository`） | 部分重叠 | AAF 版本化、发布审批、租户/角色绑定、工具白名单、技能生效画像 | **应改造为官方扩展点**：保留 AAF catalog 聚合与版本治理，后端 repository/provider 对接 `AgentSkillRepository` |
| Skill selection/runtime | assistant skill selection/resolution 约 10+ 类；代表 effective tool/skill profile resolver。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/DefaultEffectiveToolResolver.java`（`DefaultEffectiveToolResolver`） | Harness skills 是 `SKILL.md` 资源加载与动态/default workspace skills；AAF 编译器主动 `skillsEnabled(false)`。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java`（`AgentScopeSpecCompiler`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/skill/AgentSkill.java`（`AgentSkill`） | 表面相似实质不同 | AAF 的 Skill 是 Assistant 意图路由与生效工具画像，不等同 AgentScope Skill 资源包 | **应保留自研**：先统一概念映射，不应因同名删除 Assistant 路由 |
| Scheduler | engine/task/agent + automation 约 20+ 类；代表 `AutomationScheduler`、`DelegatedTaskScheduler`。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/automation/spring/AutomationScheduler.java`（`AutomationScheduler`）；同域 `DelegatedTaskScheduler.java`（`DelegatedTaskScheduler`） | 官方 `QuartzAgentScheduler`/`XxlJobAgentScheduler` 支持 cron/fixed rate/fixed delay 与 pause/resume/cancel。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-scheduler/agentscope-extensions-scheduler-quartz/src/main/java/io/agentscope/extensions/scheduler/quartz/QuartzAgentScheduler.java`（`QuartzAgentScheduler`） | 部分重叠 | durable task board、租约/fencing、审批暂停恢复、执行所有权与业务审计 | **应改造为官方扩展点**：触发器可复用官方 scheduler，触发后仍进入 AAF durable task/lease 协调器 |
| Studio、trace 与可视化 | shared event、monitor、public projection 约 15+ 类；代表 `ExecutionEventStorePort`、`ExecutionEventPublicMapper`。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/shared/event/ExecutionEventStorePort.java`（`ExecutionEventStorePort`）；同域 `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/shared/event/publication/ExecutionEventPublicMapper.java`（`ExecutionEventPublicMapper`） | 官方 `StudioManager` 管理 Studio HTTP/WebSocket client，并把 `TelemetryTracer` 注册到 tracing；不是只有 UI 名称。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-studio/src/main/java/io/agentscope/core/studio/StudioManager.java`（`StudioManager`）；同模块 `StudioClient.java`（`StudioClient`）、`StudioWebSocketClient.java`（`StudioWebSocketClient`） | 部分重叠 | AAF 事件账本、租户审计、公开脱敏、预算/计量、前端业务状态 | **应改造为官方扩展点**：AAF 保留权威事件账本，同时输出 OTEL/Studio；不要复制 Studio transport/client |
| Model provider | AgentScope resolver 约 1 类，另有 AAF direct-call 模型链。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/model/AgentScopeModelResolver.java`（`AgentScopeModelResolver`） | 官方 model extensions 提供 `OpenAIChatModel`、`DashScopeChatModel`、`AnthropicChatModel`、`GeminiChatModel`、`OllamaChatModel` 及 formatter/parser。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-model/agentscope-extensions-model-openai/src/main/java/io/agentscope/extensions/model/openai/OpenAIChatModel.java`（`OpenAIChatModel`） | 完全重叠（provider protocol） | DB 动态配置、密钥解析、租户 capability 与 route | **应删除复用官方**：保留 resolver/治理，所有 Harness provider client 只使用官方 model extensions；当前 resolver 已基本达成 |
| Model management/routing | core/model 约 15+ 类；代表 `AiModel`、`ModelManagementService`、`DefaultCapabilityRouter`、`AiModelSelector`。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/model/ModelManagementService.java`（`ModelManagementService`）；同包 `DefaultCapabilityRouter.java`（`DefaultCapabilityRouter`） | 官方 `Model` 与 provider builder 解决调用，不提供 AAF 数据库管理、租户路由和治理聚合。证据：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/model/Model.java`（`Model`）；`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-model/agentscope-extensions-model-openai/src/main/java/io/agentscope/extensions/model/openai/OpenAIChatModel.java`（`OpenAIChatModel`） | 表面相似实质不同 | 动态 DB 真理源、capability、成本/预算、租户密钥、冻结画像 | **应保留自研**：路由结果落到官方 `Model` 即可 |
| Protocol：AG-UI/A2A/chat-completions | AAF AG-UI 至少 5 类：`AgUiEvent`、`AgUiProjector`、`AssistantAguiController`、flow controller/service。证据：`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AssistantAguiController.java`（`AssistantAguiController`）；同包 `AgUiProjector.java`（`AgUiProjector`）；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/chat/agui/AgUiEvent.java`（`AgUiEvent`） | 官方 `AguiAgentAdapter` 已做输入消息、前端工具、RuntimeContext 和完整流事件转换；`A2aAgent`/`AgentScopeA2aServer` 提供 A2A；`ChatCompletionsStreamingAdapter` 提供 OpenAI chunks。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/adapter/AguiAgentAdapter.java`（`AguiAgentAdapter`）；同树 `A2aAgent.java`（`A2aAgent`）、`ChatCompletionsStreamingAdapter.java`（`ChatCompletionsStreamingAdapter`） | 完全重叠（协议转换），AAF 业务鉴权/命令模型部分不重叠 | Spring Security、thread ownership、AAF mode/request、公开脱敏和持久 cursor | **应删除复用官方**：使用官方 adapter/starter 负责标准 wire conversion，AAF 只保留鉴权、命令组装和自定义 `aaf.*` 投影。搜索未发现 AAF 正式 A2A Java 实现，不能据此断言不存在，属**未验证推断** |
| Channel | AAF messaging/企业 sender 约 15+ 类，含 WeCom/DingTalk sender 与模板。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/messaging/MessageService.java`（`MessageService`）；同域企业消息 sender 类 | 官方 `WeComChannel`、`FeishuChannel`、`DingTalkChannel`、`GitHubChannel`、`GitLabChannel` 同时含 inbound mapper、outbound client、签名、幂等和 bot-loop guard。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-channel/agentscope-extensions-channel-wecom/src/main/java/io/agentscope/extensions/channel/wecom/WeComChannel.java`（`WeComChannel`）；同树 `tmp/agentscope-java/agentscope-extensions/agentscope-extensions-channel/agentscope-extensions-channel-github/src/main/java/io/agentscope/extensions/channel/github/GitHubChannel.java`（`GitHubChannel`） | 部分重叠 | AAF 模板通知、组织同步、审批流、非 Agent 业务消息、租户配置 | **应改造为官方扩展点**：Agent 双向会话 channel 复用官方；AAF 保留企业业务集成和通知域，在边界做 route/identity adapter |
| Nacos 与 Higress | 当前 AAF 未发现 Nacos/Higress 生产实现，近似 0 类；技术选型还明确 v0.1 不引 Nacos。证据：`docs/design/apps/service/tech-stack.md`（后端技术选型）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/spring/AgentScopeInfrastructureAutoConfiguration.java`（`AgentScopeInfrastructureAutoConfiguration`） | 官方 `NacosSkillRepository` 真正下载 skill ZIP，另有 agent registry/prompt/A2A；`HigressMcpClientBuilder` 支持 SSE/Streamable HTTP、认证及 tool search。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-nacos/agentscope-extensions-nacos-skill/src/main/java/io/agentscope/core/nacos/skill/NacosSkillRepository.java`（`NacosSkillRepository`）；`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-higress/src/main/java/io/agentscope/extensions/higress/HigressMcpClientBuilder.java`（`HigressMcpClientBuilder`） | 当前无重复；未来会完全重叠 provider | AAF 租户配置、发布审批、MCP 工具治理 | **应删除复用官方**：准确含义是“禁止未来另造 provider”；需要 Nacos/Higress 时直接接官方模块并包 AAF 治理 |
| COS/OSS 与对象存储 | AAF storage/file 约 10+ 类，统一业务 `FileStorage`/引用管理。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/storage/StorageClient.java`（`StorageClient`）；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/system/file/service/FileStorageReferenceService.java`（`FileStorageReferenceService`） | 官方 `CosAgentStateStore`、`OssAgentStateStore` 保存 `(userId/sessionId/stateKey)` JSON/list/hash，是 AgentState 后端；另有 base store。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-cos/src/main/java/io/agentscope/extensions/cos/CosAgentStateStore.java`（`CosAgentStateStore`）；`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-oss/src/main/java/io/agentscope/extensions/oss/OssAgentStateStore.java`（`OssAgentStateStore`） | 表面相似实质不同 | 业务文件引用、权限、生命周期、内容类型、上传下载与对象治理 | **应保留自研**：官方 store 可作为 AgentState 备选后端，不能替代 AAF FileStorage |
| Spring Boot starters 与 AAF AutoConfiguration | AAF AgentScope 接线约 2 个主 auto-config + mapper/compiler beans。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/spring/AgentScopeInfrastructureAutoConfiguration.java`（`AgentScopeInfrastructureAutoConfiguration`）；同包 `AgentRuntimePortAutoConfiguration.java`（`AgentRuntimePortAutoConfiguration`） | 官方 core starter 提供 prototype `Memory`/`Toolkit` 和默认 `ReActAgent`；provider starter 按属性建 `Model`；AG-UI/A2A starter 提供 manager/controller/server/ready listener。证据：`tmp/agentscope-java/agentscope-extensions/agentscope-spring-boot-starters/agentscope-spring-boot-starter/src/main/java/io/agentscope/spring/boot/AgentscopeAutoConfiguration.java`（`AgentscopeAutoConfiguration`）；同树 `OpenAIAutoConfiguration.java`（`OpenAIAutoConfiguration`）、`AgentscopeAguiMvcAutoConfiguration.java`（`AgentscopeAguiMvcAutoConfiguration`）、`AgentscopeA2aAutoConfiguration.java`（`AgentscopeA2aAutoConfiguration`） | 部分重叠 | DB 动态多模型、版本化 HarnessAgent、端口条件装配、AAF tool gateway、Redis Spring adapter、租约/计量/冻结信封 | **应改造为官方扩展点**：保留 AAF core runtime wiring；标准 provider、AG-UI、A2A 端点优先委托官方 starter/customizer，避免复制 controller 与协议 Bean |

分类统计：**18 项 = 应删除复用官方 3 项 + 应保留自研 7 项 + 应改造为官方扩展点 8 项**。其中“应删除复用官方”三项是 model provider、protocol wire conversion、未来 Nacos/Higress provider；不包含 AAF 领域治理。证据见上表逐行。

## JsonSchemaUtils 同名覆盖风险

### 冲突动机

官方 `JsonSchemaUtils` 按 Jackson 2 编译，导入 `com.fasterxml.jackson.databind.JsonNode`，并使用 victools `jsonschema-module-jackson` 的 `JacksonModule`。AAF 运行在 Spring Boot 4 / Spring AI 2，默认 Jackson 3，并锁定 jsonschema-generator 5.0.0；AAF shadow 类改用 `tools.jackson.databind.JsonNode` 和 victools 5 的 `JacksonSchemaModule`，但 `TypeReference` 仍来自 `com.fasterxml.jackson.core.type.TypeReference`。类注释明确说明其目标是规避 AgentScope RC4 产物在 Jackson 3/jsonschema-generator 5 环境中的 `NoSuchMethodError`。证据：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（官方 `JsonSchemaUtils`）；`apps/service/aaf-framework/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（AAF `JsonSchemaUtils`）；`apps/service/aaf-dependencies/pom.xml`（Spring Boot、Spring AI、AgentScope 与 jsonschema-generator 版本属性）。

AAF POM 又从 `agentscope-harness` 排除了 `jsonschema-generator` 与 `jsonschema-module-jackson`，让 Spring AI 所需的 5.0.0/Jackson 3 版本进入最终依赖图。因此覆盖不是业务定制，而是解决同一 JVM 内 Jackson 2 AgentScope 代码与 Jackson 3 应用依赖不兼容的构建折中。证据：`apps/service/aaf-framework/pom.xml`（AgentScope exclusions）；`apps/service/aaf-dependencies/pom.xml`（依赖版本管理）；`apps/service/aaf-framework/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（AAF `JsonSchemaUtils`）。

### 技术本质

这是**同包名、同类名、同 binary name 的 classpath shadowing**：AAF 源码与依赖 jar 都声明 `io.agentscope.core.util.JsonSchemaUtils`。它不是继承、SPI、adapter 或 Java module patch；JVM 对一个 ClassLoader 只定义先找到的一个 binary name，另一个实现不可同时正常使用。证据：`apps/service/aaf-framework/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（AAF `JsonSchemaUtils`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（官方 `JsonSchemaUtils`）。

编译 AAF 源码时，当前模块的 `target/classes` 通常由 javac 输出并在依赖 jar 之前被后续模块消费，所以 AAF shadow 往往成为编译所见实现；但 Maven/Javac 对重复类没有“覆盖契约”，编译环境、IDE incremental compiler、annotation processor classloader 均可各自看到不同副本。运行时由实际 ClassLoader 的 URL/entry 搜索顺序决定，JVM 规范只保证已加载类的身份，不保证 Maven 依赖与应用 classes 的业务优先级。证据：`apps/service/aaf-framework/pom.xml`（依赖排除与构建输入）；`apps/service/aaf-framework/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（重复 binary name）。

### 升级与运行风险

| 场景 | 结果 | 风险性质 |
| --- | --- | --- |
| 上游新增静态方法，AgentScope 新代码调用它，AAF shadow 未同步 | 若新调用点编译时解析到官方类但运行加载 shadow，触发 `NoSuchMethodError`；若编译直接解析 shadow，可能先编译失败 | 显式失败 |
| 上游修改参数/返回类型或删除方法 | 调用 descriptor 不匹配，可能编译失败或运行时 `NoSuchMethodError` | 显式失败 |
| 方法签名不变但 Schema option、`$defs`、required 或 draft 行为变化 | AAF 继续加载旧 shadow，不报错却生成不同 Schema | **静默行为差异，风险最高** |
| Jackson 2 `JsonNode` 与 Jackson 3 `tools.jackson.databind.JsonNode` 穿越边界 | descriptor、cast 或 codec 不兼容，可能 `NoClassDefFoundError`、`NoSuchMethodError`、`ClassCastException` | 显式或延迟失败 |
| victools 模块 API 再变化 | 构造器/option 不匹配，启动或首次生成 Schema 时报 linkage error | 延迟失败 |

证据：`apps/service/aaf-framework/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（AAF 方法与 Jackson 3 类型）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（官方方法与 Jackson 2 类型）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/ToolSchemaGenerator.java`（AgentScope 内部静态调用点）。

不同打包方式不能把重复类变成稳定设计：

- Spring Boot executable fat jar 通常由 Boot launcher 先暴露 `BOOT-INF/classes` 再组织 `BOOT-INF/lib`，所以 application shadow 往往获胜；这是 launcher 布局行为，不是 `JsonSchemaUtils` 的兼容保证。
- layered jar 的 layer metadata 本身不改变类语义；但镜像分层解包后若用自定义 `java -cp`、classpath index、容器脚本或插件 ClassLoader 启动，实际顺序可能变化。
- Maven Surefire/IDE 通常把 test-classes、main classes、dependencies 依次放入测试 classpath，因此测试可能稳定命中 AAF shadow；普通 `java -cp`、集成测试插件、shade 后 jar 或某些隔离 ClassLoader 不必保持相同顺序。
- 即使顺序始终让 AAF 获胜，也只证明“稳定加载 shadow”，不能证明 shadow 与升级后的 AgentScope ABI/行为兼容。

证据：`apps/service/aaf-framework/pom.xml`（Maven/AgentScope 依赖布局）；`apps/service/aaf-framework/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（重复类）；`apps/service/aaf-api/pom.xml`（Spring Boot 打包入口）。

### 与 AAF 禁兼容层约束的关系

该类的唯一目的就是让一个按旧 Jackson/victools ABI 编译的依赖在新 ABI 环境继续工作，且通过复制上游同名类实现，属于典型 temporary compatibility shim。AAF 尚未 v1.0，硬约束明确禁止 fallback/shim/legacy adapter/dual path；因此它违反“禁兼容层”，不能被包装成正式基础设施边界。证据：`.kiro/skills/coding-standards/SKILL.md`（禁兼容层）；`AGENTS.md`（AI 行为硬规则）；`apps/service/aaf-framework/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（覆盖动机注释）。

### 替代方案比较

| 方案 | 优点 | 缺点与判据 | 建议 |
| --- | --- | --- | --- |
| 向上游提交 Jackson 3/victools 5 PR | 根治；所有内部调用统一；无重复 binary name；生态共同维护 | 需要上游接受兼容矩阵和发布节奏；应附 AgentScope core/harness/tool schema 测试 | **长期首选，立即做** |
| 等官方 GA/后续版本 | AAF 无 fork 维护成本 | 只有上游已承诺版本、源码已合并且 AAF 验证通过时才成立；当前 AAF BOM 已写 AgentScope 2.0.0，不能假定“GA 自动解决” | 仅作退出条件，不是当前措施 |
| fork `agentscope-core` 并使用独立 Maven 坐标 | 可立即修 ABI；补齐全模块测试；只保留一个 `JsonSchemaUtils` binary name | 必须整体替换原 core，并排除所有传递的官方 core，不能让 fork 与原 jar 同时存在；需跟踪上游安全/bugfix | **短期推荐** |
| Maven Shade 重定位 | 可把冲突依赖隔离在私有 namespace | AgentScope API 大量暴露 Jackson/victools/Msg/Model 类型，跨模块 relocation 复杂；反射、service loader、序列化名和 stacktrace 都增加维护成本 | 仅在 fork 不可行且边界可完全封装时兜底 |
| AAF 自己生成 Tool Schema | AAF 工具目录可完全掌控 Schema，适合当前显式 `ToolBase.inputSchema` 路径 | 不能替代 AgentScope `ToolSchemaGenerator` 等内部对 `JsonSchemaUtils` 的静态调用；只能减少暴露面，不能消除重复类 | 作为业务边界优化，不是根治方案 |

证据：`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/ToolSchemaGenerator.java`（内部静态调用）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/PortBackedAgentTool.java`（AAF 显式 Schema）；`apps/service/aaf-framework/pom.xml`（当前 exclusion 方案）。

**推荐方案：立即以独立坐标的最小 AgentScope fork 整体替换官方 core/harness 依赖并删除同名 shadow，同时向上游提交 Jackson 3/victools 5 兼容 PR；上游正式版本验证通过后删除 fork。**

## 演进路线

### 立即阻断静默风险

- 删除事件 mapper 的 `default -> Optional.empty()` 设计，建立 31 项显式处置矩阵；暂不支持的事件也输出 redacted lifecycle 或结构化 drop metric。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeEventMapper.java`（`AgentScopeEventMapper`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/AgentEventType.java`（`AgentEventType`）。
- 冻结信封按全部 `ContentBlock` 规范化哈希，禁止只用 `getTextContent()` 代表真实请求。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/middleware/PromptEnvelopeCaptureMiddleware.java`（`PromptEnvelopeCaptureMiddleware`）；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/message/ContentBlock.java`（`ContentBlock`）。
- 决定 AgentState 唯一职责：真正纳入恢复状态，或关闭持久历史；不要“先写、下次再拒绝”。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java`（`HarnessAgentExecutionAdapter`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java`（`AgentScopeSpecCompiler`）。
- 按上节方案退出 `JsonSchemaUtils` shadow。证据：`apps/service/aaf-framework/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java`（AAF `JsonSchemaUtils`）。

### 收敛通用 provider 与协议

- Model provider 保持当前官方 extensions 路径；memory、RAG、sandbox、skills、scheduler、studio 和 channel 采用“AAF 领域 port + AgentScope provider adapter”。证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/model/AgentScopeModelResolver.java`（`AgentScopeModelResolver`）；上表各官方 provider 类。
- AG-UI wire conversion 优先迁移到官方 `AguiAgentAdapter`/starter；AAF controller 只做认证、thread ownership、AAF command 和 `aaf.*` custom event。证据：`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AssistantAguiController.java`（`AssistantAguiController`）；`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/adapter/AguiAgentAdapter.java`（`AguiAgentAdapter`）；`tmp/agentscope-java/agentscope-extensions/agentscope-spring-boot-starters/agentscope-agui-spring-boot-starter/src/main/java/io/agentscope/spring/boot/agui/mvc/AgentscopeAguiMvcAutoConfiguration.java`（`AgentscopeAguiMvcAutoConfiguration`）。

### 保留 AAF 权威域

AtomMemory/认知治理、可信知识发布与图谱投影、模型管理与 capability routing、Assistant skill/工具生效画像、durable delegated task、租约 fencing、审批、计量、信用/预算、事件账本和对象文件域都不是官方 provider 的同义替代，应继续由 AAF 掌握。复用原则应固定为：**官方负责通用 runtime/provider/protocol，AAF 负责租户化领域模型和治理政策；两者只通过公开扩展点连接。**证据：`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/engine/memory/AtomMemoryEngineImpl.java`（`AtomMemoryEngineImpl`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/port/ConversationLeasePort.java`（`ConversationLeasePort`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/shared/event/ExecutionEventStorePort.java`（`ExecutionEventStorePort`）；`docs/design/framework/component-overview.md`（组件总览）。


## 后续修正（2026-08-31）

基于对官方 [计划模式](https://java.agentscope.io/v2/zh/docs/harness/plan-mode.html) 文档的精读，重复能力清单新增一项，总数由 18 增至 19，三类结论分布变为「应删除复用官方 3 / 应保留自研 7 / 应改造为官方扩展点 9」。初版正文保留不改。

### 新增重复项 · 工具授权模式

| 重叠面 | AAF 实现及大致类数量 | 官方具体模块/类与源码事实 | 重叠程度 | AAF 特有约束 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 工具授权模式（Permission Mode） | AAF 用 `ActionAuthorizationPolicy` 三态 + `ExecutionContract.allowedActions` 白名单表达，约 3–5 类参与。证据：`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/assistant/vo/AssistantExecutionRequest.java`（`ActionAuthorizationPolicy`）；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/model/ExecutionContract.java`（`ExecutionContract`） | 官方 `PermissionMode` 提供 `DEFAULT` / `BYPASS` / `DONT_ASK` 三态，由权限引擎在评估时使用；`HarnessAgent.setPermissionMode(ctx, mode)` 支持运行期 per-session 切换，保留已配置 allow/deny/ask 规则与工作目录、只改 mode、下一次 call 生效，进行中的 call 沿用启动时引擎。证据：官方 plan-mode 文档「运行期切换权限模式」；`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java` 的 `setPermissionMode` / `getPermissionMode` | 高度重叠 | 授权决策需落 `AUTHORIZATION_*` / `APPROVAL_*` 事件、需与持久审批和 `ToolGatewayPort` 联动、需租户维度约束 | **应改造为官方扩展点**：授权模式本身复用官方 `PermissionMode`，AAF 只保留「决策落事件 + 持久审批 + 租户约束」这层；停止用 `allowedActions` 白名单自行表达 ASK/DENY 语义 |

语义对应关系：

| 官方 `PermissionMode` | AAF `ActionAuthorizationPolicy` | 语义 |
|----------------------|--------------------------------|------|
| `DEFAULT` | `REQUEST_ON_DEMAND` | 正常管控，ASK 时弹确认 |
| `BYPASS` | 无对应 | 关闭全部规则评估（官方标注应配合沙箱使用） |
| `DONT_ASK` | `DENY_AUTHORIZED_ACTIONS` | 无人值守不弹确认，**ASK 决策变为 DENY** 而非放行 |

`DONT_ASK` 的语义与 `DENY_AUTHORIZED_ACTIONS` 几乎完全一致——都表达「不打断执行但也不放行需授权动作」。AAF 目前是靠 `ExecutionContract.allowedActions` 白名单间接达到这个效果，而官方是权限引擎的一等模式。

`BYPASS` 是 AAF 没有的逃生口。是否需要它取决于产品决策：AAF 的治理定位可能刻意不提供「跳过全部权限确认」的开关。若确认不需要，应在 ADR 或规范中显式记录「不采纳 `BYPASS`」，避免后续被当成遗漏补上。

### 方法论教训

审计过程发现官方两篇文档对同一安全边界给出相反结论（Plan Mode 限制是否沿 `agent_spawn` 传播），最终由源码 `harness/agent/tool/AgentSpawnTool.java:299-301` 判定。详见 [03-capability-gap.md](03-capability-gap.md) 的「方法论教训」一节。

由此得出的规则已写入 [agentscope-usage-guide.md](../../../reference/dev/agentscope-usage-guide.md)：涉及安全边界的能力判定，不得凭官方文档推断，必须落到源码。
