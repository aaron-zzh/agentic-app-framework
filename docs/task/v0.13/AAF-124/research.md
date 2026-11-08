---
level: Practice
layer: Model
purpose: AAF-124 助理执行过程可观测与智能体评测调研，对比 OpenJudge 等外部框架并给出推荐方案
status: draft
version: 1.0.0
date: 2026-11-08
author: AaronZZH & Kiro
---

# AAF-124 执行可观测与评测调研

## 背景

用户在排查一次助理对话时提出两个需求：

- 日志里看不出一次执行的主要过程：用了什么角色、什么技能、哪些工具，检索了哪些记忆和知识库，调用了哪些工具，是否拆分了步骤或子智能体，执行后有没有学习。希望按执行分组打印这些关键节点。
- 参考 AgentScope OpenJudge 等知名评测框架，结合当前代码给出评测方案。

两件事共用同一份数据：执行轨迹。日志是给开发者看的轨迹，评测是对轨迹打分。所以本文把它们放在一个方案里，分阶段落地。

## 样例日志暴露的问题

样例是一次 DIRECT 模式执行（`executionId=5dSef08`，12 个工具，1 次模型调用）。从日志能看出：

- **关键决策只在 DEBUG，而且分散**：角色在 `agent=user.assistant.default.1:system.role.generalist:coordinator:coordinator` 这个拼接串里，技能、工具名单、L1 检索命中都没打印。`[Prompt预检]` 是 INFO，其余 `[AgentLoop]`、`stage=event_emitted` 都是 DEBUG，生产环境关掉 DEBUG 后基本只剩积分日志。
- **执行后学习没有日志**：`RUN_COMPLETED` 之后出现的 Embedding 调用来自长期记忆学习 `memoryGovernance.learn`，但日志里没有任何一行说明"开始学习"，只能靠积分日志推断。
- **失败日志丢了堆栈**：`AssistantApplicationService.invoke` 的失败收口（约 264-273 行）用 `log.debug` 只打印异常类名和 message，没有 Throwable，所以 `PSQLException: 查询没有传回任何结果` 无法定位到具体 SQL。
- **学习失败把成功的回复判为失败**：模型回复已经生成完毕，`learn` 抛异常后整轮执行变成 `EXECUTION_FAILED`。见文末附录。
- **交错日志难以阅读**：同一时间段插入了 ASR WebSocket 的积分日志，按线程名和时间无法把一次执行的日志拼起来。

## 现状盘点

### 执行主链路与可观测现状

| 阶段 | 代码位置 | 日志 | 结构化事件 / 落库 |
|------|---------|------|------------------|
| 请求接收 | `AssistantApplicationService.invoke` | DEBUG `stage=invocation_received`，含 requestedSkill、模型选择、记忆模式 | 无 |
| 定义与角色解析 | `requireDefinition`、`resolveExecutionProfile`、`roleResolvedEvent` | DEBUG "Agent 画像候选已决策"，含 roleKey、技能数、有效工具数，不含名单 | DIRECT 路径有角色事件 |
| L1 上下文（记忆 + 知识库预检索） | `executionContext` → `DefaultL1ContextCollaborator`（`UnifiedRetrievalPort` 合并记忆与 `HybridSearchService`） | DEBUG "L1 上下文已冻结"，只有来源数、消息数、摘要 | 无命中明细 |
| Prompt 组装 | `HarnessAgentExecutionAdapter.logPromptPreflight` | INFO `[Prompt预检]`，字符数与 token 估算完整 | 无 |
| 物理模型调用 | `PromptEnvelopeCaptureMiddleware` | DEBUG `[PromptEnvelope]` | 是，`PromptEnvelopePort` 落库，可复现调用 |
| 模型与工具事件 | `AgentScopeEventMapper` | DEBUG `stage=event_emitted` | 是，`ai_task_event`，并实时推送 AG-UI |
| 任务拆分与子智能体 | `promote_to_task`、TaskPlan、TaskNode、`finalizeNodeExecution` | 节点验证 INFO `stage=task_node_validation` | 是，Task / TaskNode 状态与事件 |
| 完成验证 | `completionValidator.validate` | DEBUG "完成条件已判定" | `VALIDATION_*` 事件；这里是规则判定，不是 LLM 评审 |
| 执行后学习 | `appendSessionTurn`（Redis 短期）、`memoryGovernance.learn`（`ai_cognition_memory` 长期） | 无 | 无 |
| 计费 | `DefaultAiCreditGuard` | INFO 结算明细 | 是，积分流水与 `AiUsageRecord` |

### 基础设施

- 已有：Actuator、`micrometer-registry-prometheus`、Sentry。
- 没有：Micrometer Tracing、OpenTelemetry、Langfuse，Java 源码里也没有 `ObservationRegistry`。`TurnOutcome` 注释明确写了上下文用量和物理重试指标只存在于 `[Prompt预检]` 日志里。
- AgentScope 用的是 `agentscope-core` 2.0.4，没有引入 harness（ADR-005 议题三）。core 自带 `Tracer` / `TracerRegistry` 扩展点（`callAgent`、`callModel`、`callTool`、`callFormat`、`runWithContext`），以及 `JsonlTraceExporter` 钩子。

### 评测

- 没有评测代码：没有 Judge、Grader、数据集、打分表。
- 唯一的质量信号是点踩：前端 `NegativeFeedbackButton`，后端 `MessageFeedbackService`（AAF-114）。
- 已有规划：`docs/design/apps/service/tech-stack.md` 规划了 Evaluation Framework（相关性、忠实度、用户反馈、RAGAS）和基于 OTLP 的 LLM 可观测。`docs/design/audit/2026-08-30-agentscope-boundary/03-capability-gap.md` 已决定"技术可观测用官方 trace/OTel，业务审计用 AAF EventStore"。
- 相关故事：AAF-027（LLM 调用录制与回放）是回归评测的前置能力。

## 外部方案

### OpenTelemetry GenAI 语义约定

OTel 为生成式 AI 定义了统一的 span 名称和属性，核心是三类 span：`invoke_agent`（一次智能体运行）、`chat`（一次模型调用，带 token 用量）、`execute_tool`（一次工具调用），还有处于开发状态的 plan span。属性如 `gen_ai.agent.name`、`gen_ai.request.model`、`gen_ai.usage.input_tokens`。规范状态仍是 Development，但主流后端（Langfuse、Phoenix、阿里云云监控、Honeycomb、Azure Monitor）都按它解析。[规范](https://github.com/open-telemetry/semantic-conventions-genai/blob/main/docs/gen-ai/gen-ai-agent-spans.md)

对 AAF 的意义：按这套约定出 span，后端可以随时换，不绑定某个平台。

### AgentScope Java 自带能力

- **TelemetryTracer**：通过 `TracerRegistry.register(...)` 注册后，自动为 agent 调用、模型调用、工具执行、消息格式化生成 span，经 OTLP 导出到 Langfuse、Jaeger 等。[文档](https://java.agentscope.io/v1/en/docs/task/observability)
- **Studio**：Web 界面实时查看智能体消息流，适合本地调试，不适合生产。
- **AgentTraceMiddleware**（harness 包）：INFO 打印智能体名、模型、工具名和消息长度，DEBUG 打印参数与结果。AAF 没用 harness，但可以参考它的日志分级方式。
- 阿里云云监控 2.0 和 AgentLoop 提供 Java 探针，按 OTel 标准自动采集 AgentScope Java 应用的智能体、模型、工具调用。AAF 部署在阿里云上，可以作为生产后端候选。[云监控](https://help.aliyun.com/en/cms/cloudmonitor-2-0/connecting-to-an-agentscope-java-application)

### AgentScope OpenJudge

OpenJudge 是 AgentScope 团队的开源评测框架（Python，`pip install py-openjudge`）。[仓库](https://github.com/agentscope-ai/OpenJudge)

- **评分器库**：50 多个评分器，分通用、智能体、多模态三类。智能体类覆盖整个生命周期：
  - 工具：`ToolSelectionGrader`、`ToolCallAccuracyGrader`、`ToolParameterCheckGrader`，以及纯代码的 `ToolCallStepSequenceMatchGrader`。
  - 记忆：`MemoryAccuracyGrader`、`MemoryRetrievalEffectivenessGrader`。
  - 规划与反思：`PlanFeasibilityGrader`、`ReflectionAccuracyGrader`。
  - 动作与轨迹：`ActionLoopDetectionGrader`（代码）、`TrajectoryComprehensiveGrader`。
  - 每个评分器带基准数据和准确率，例如用 qwen3-max 时 `ToolCallSuccessGrader` 与人工标注一致率 95%，`ReflectionProgressAwarenessGrader` 74%。[评分器列表](https://agentscope-ai.github.io/OpenJudge/built_in_graders/agent_graders/)
- **评分标准生成**：没有标注数据时，根据任务描述零样本生成评分标准；有少量标注时，从数据中归纳评分标准。
- **运行与聚合**：`GradingRunner` 批量并发评测，`WeightedSumAggregator` 加权汇总，再做分布统计。
- **集成**：可接 LangSmith、Langfuse，从平台拉 trace 评分再写回；可把分数转成强化学习奖励（VERL）。
- **与 AgentScope 的关系**：Python 版 AgentScope 的 `MetricBase` 可以包一层适配器使用 OpenJudge 评分器。Java 版没有对应的评测模块。

### 其他框架

| 框架 | 定位 | 对 AAF 的适配度 |
|------|------|----------------|
| [Langfuse](https://langfuse.com/docs/deployment/v3/overview) | 追踪 + 评测 + Prompt 管理一体，MIT 开源，可自部署 | 功能最全；但 v3 自部署要 web、worker、PostgreSQL、ClickHouse、Redis、S3 六个组件，官方建议 4 核 16G 起，对当前部署偏重 |
| Arize Phoenix | OTel 原生的追踪与 RAG 评测 | 单容器即可运行，适合开发和测试环境看 trace |
| DeepEval | pytest 风格的 LLM 单测 | 适合 CI 回归，但 Python 生态，AAF 测试是 JUnit/Vitest |
| Ragas | RAG 指标（忠实度、上下文召回等） | 只覆盖知识库检索质量，OpenJudge 已有同类评分器 |
| Promptfoo | 配置驱动的 Prompt 对比与红队测试 | 适合 Prompt 改动前后对比，不覆盖智能体轨迹 |
| LangSmith / Braintrust | 商业 SaaS | 数据出境与成本问题，不考虑 |

## 方案选项

| 选项 | 内容 | 优点 | 缺点 |
|------|------|------|------|
| 只增强日志 | 每次执行结束打印一段分组摘要 | 零新增依赖，马上能用 | 不能跨服务关联，不能检索统计，评测无数据可用 |
| 全量接 Langfuse | AgentScope TelemetryTracer + AAF 自定义 span 导到 Langfuse，在 Langfuse 里做评测 | 一站式，OpenJudge 有现成集成 | 部署重；评测数据和分数在 Langfuse，违反"PG 唯一真理源"；业务事件和 trace 形成两份轨迹 |
| 分层组合（推荐） | 日志摘要立即补齐；OTel 按 GenAI 约定出 span，后端可替换；评测数据与分数落 AAF 的 PG，评分执行交给 OpenJudge 旁路服务 | 每一层都可单独上线和回退；轨迹真理源仍是 `ai_task_event` 与 PromptEnvelope；复用 OpenJudge 评分器，不自己写评测 Prompt | 引入 Python 旁路服务，部署多一个容器；分三期交付 |

## 推荐方案

### 第一期：执行摘要日志与失败可定位

目标是不加任何依赖，让一次执行在 INFO 级别能被完整读懂。

- 新增按执行聚合的轨迹收集器，挂在 `invoke` 的事件流上，在 `doFinally` 时一次性打印一个分组块。数据来自已有的 `ExecutionEvent` 流，加上少量现在只在局部变量里的信息（技能名、工具名单、L1 命中、学习结果）。
- 失败收口改为 WARN，并把 Throwable 作为最后一个参数传入，打印完整堆栈。
- 长期记忆学习改为尽力而为：失败只打 WARN，不改变本轮执行结果。这一条改了失败语义，需要协调者确认。
- 输出示例（按样例日志填写，日志中没有的值用 `…` 表示）：

```text
[执行轨迹] executionId=5dSef08 模式=DIRECT 结果=FAILED 耗时=33.1s 积分=16
  ├ 角色    system.role.generalist（coordinator）
  ├ 技能    未命中，走默认角色
  ├ 工具    12 个：webSearch, queryWeather, knowledge_search, context.load, promote_to_task ...
  ├ 上下文  记忆 … 条，知识库 … 条，Prompt 1930 tokens（2%）
  ├ 模型    #1 model=2 首字 19.1s 总耗时 28.8s in=… out=… 积分 15
  ├ 工具调用 无
  ├ 拆分    无（未提升为任务）
  ├ 验证    完成条件已满足
  └ 学习    短期会话已写入；长期记忆召回 Embedding 积分 1，失败：PSQLException 查询没有传回任何结果
```

- 树中每一行对应一个已有阶段，任务拆分和子智能体委派时追加子节点（TaskNode、Worker 角色、各自工具调用）。
- 摘要打印由配置开关控制，默认开；参数值和工具结果只在 DEBUG 打印，避免敏感内容进入日志。

### 第二期：OpenTelemetry 追踪

目标是可检索、可跨服务关联、可统计的轨迹，后端可替换。

- 引入 Micrometer Tracing 的 OTel 桥接和 OTLP 导出（Spring Boot 4 原生支持），默认关闭。
- 注册 AgentScope `TelemetryTracer` 并共用同一个 OTel SDK，让 agent、chat、execute_tool span 自动挂在 AAF 的 span 下。需要验证 `Tracer.runWithContext` 与 AAF 写入 Reactor Context 的组织上下文能否一起传播。
- AAF 补业务 span：角色解析、L1 检索（记忆与知识库分开）、完成验证、任务拆分、子智能体委派、执行后学习、积分结算。命名优先用 GenAI 约定，约定没有的用 `aaf.` 前缀。
- 默认不采集 Prompt 和回复正文，只采集长度和摘要哈希；需要完整内容时用 PromptEnvelope 按 executionId 回查。
- 后端：开发环境用 Phoenix 或 Jaeger 单容器；生产优先评估阿里云云监控 2.0（已在用阿里云，免运维）；Langfuse 作为可选，不作为默认依赖。

### 第三期：评测闭环

目标是用数据回答"这次改 Prompt、换模型、改工具之后，效果变好还是变差"。

- **数据归 AAF**：在 PG 建评测数据集、用例、评测批次、分数四类实体，管理后台用 EntityDef 声明。用例来源：
  - 点踩反馈自动入候选集，人工确认后成为用例。
  - 从 `ai_task_event` 和 PromptEnvelope 抽取真实执行轨迹（输入、工具定义、工具调用序列、检索上下文、回复）。
  - 人工编写的黄金用例。
- **评分交给 OpenJudge 旁路服务**：一个 Python 容器，从 AAF 拉取待评用例和轨迹，按配置的评分器打分，结果写回 AAF。首批评分器：
  - 回复：`RelevanceGrader`、`HallucinationGrader`（用知识库检索结果作 context）。
  - 工具：`ToolSelectionGrader`、`ToolCallAccuracyGrader`。
  - 记忆：`MemoryRetrievalEffectivenessGrader`。
  - 规划：`PlanFeasibilityGrader`（只对提升为任务的执行）。
  - 轨迹：`TrajectoryComprehensiveGrader`、`ActionLoopDetectionGrader`。
- **确定性检查留在 Java**：工具调用序列匹配、完成条件、格式校验不需要 LLM，在 Java 里实现，作为 JUnit 回归测试运行，配合 AAF-027 录制回放保证可重复。
- **两种运行方式**：离线批次（改 Prompt、换模型、发版前跑全量数据集，对比上一批次）；线上抽样（按比例抽取真实执行打分，看趋势，不阻塞用户请求）。
- **评委模型成本**：评测本身调用 LLM，需要单独计费归属（系统账号），并设置每日上限。

### 不推荐的做法

- 把 OpenJudge 评分 Prompt 移植成 Java：评分器还在快速迭代（v0.2 已与 v0.1 不兼容），移植后要长期跟进上游，维护成本高于跑一个旁路容器。
- 把 Langfuse 当作轨迹和评分的真理源：会和 `ai_task_event` 形成两份轨迹，也让评测数据脱离 AAF 的权限和多租户体系。
- 在线同步评测：评委 LLM 调用会拖慢用户请求并翻倍成本，线上只做异步抽样。

## 风险与待决事项

- **风险等级**：整体为 🔴 高风险（新增基础设施、跨 framework 与 api 模块、新增数据表、修改失败语义），需要 architect 出设计并经人类审核后开发。第一期中"日志摘要 + 失败堆栈"可以单独作为 🟡 先做。
- **敏感数据**：Prompt、工具参数、记忆内容含用户数据。日志与 span 默认只记录长度、名称和哈希，正文按 executionId 回查 PromptEnvelope。
- **新增事件类型**：如果为学习、检索等阶段新增 `ExecutionEventType`，会影响前端 AG-UI 映射和事件表，属于接口变更，需协调者同步使用方。第一期建议不加事件类型，只在收集器里聚合。
- **Python 运行时**：旁路服务需要新的镜像与部署流水线，评估是否可以与现有部署共机。
- **OpenJudge 许可证**：仓库 LICENSE 为 Apache 风格模板，立项前复核。
- **评委一致性**：LLM 评分器与人工标注一致率在 74% 到 95% 之间，需要用点踩数据做一轮校准再用于发版门禁。

## 任务拆分草案

以下编号为草案，architect 设计后再定稿：

- **#12401** 执行轨迹摘要日志、失败收口打印堆栈、长期记忆学习改为尽力而为 — developer-service
- **#12402** 定位并修复长期记忆学习的 `PSQLException`（依赖 #12401 拿到堆栈） — developer-service
- **#12403** Micrometer Tracing + OTLP 接入，注册 AgentScope TelemetryTracer，补 AAF 业务 span — developer-service
- **#12404** 评测数据集、用例、批次、分数数据模型与 EntityDef 管理页 — developer-service / developer-webui
- **#12405** OpenJudge 旁路评测服务与离线批次 — developer-service
- **#12406** 点踩反馈转评测候选用例、线上抽样评测 — developer-service / developer-webui

## 附录：样例日志的失败定位

- **执行路径**：DIRECT 路径 `finalizeDirect` 判定完成后，先写 Redis 短期会话，再在长期记忆开启且写范围含 `PERSONAL` 时调用 `memoryGovernance.learn`。
- **学习链路**：`MemoryGovernanceService.learn` → `RuleBasedMemoryGovernanceAdapter.resolve` → `JpaCognitionMemoryAdapter.recall`（调用 Embedding，即日志中 `inputTokens=20` 那次）→ `CognitionMemoryRepository.search`（pgvector 原生查询）→ 不重复时 `append` / `saveAll`。
- **已排除的位置**：
  - 积分结算：`DefaultAiCreditGuard.doSpend` 和 `saveUsageRecord` 吞掉所有异常，不会抛出。
  - 事件落库：`eventStore.append` 在 `onErrorResume` 下游，异常不会进入失败收口。
  - 本链路上的原生 DML 都带了 `@Modifying`。
- **可疑点**：`CognitionMemoryEntity.embedding` 是 `float[]`，只声明了 `columnDefinition = "vector(1536)"`，没有 `@JdbcTypeCode(SqlTypes.VECTOR)`，也没引入 `hibernate-vector`。读写 vector 列依赖隐式转换；另外 Embedding 模型输出维度必须正好是 1536。
- **结论**：日志没有堆栈，无法确定是哪条 SQL。需要先完成 #12401 的堆栈打印，或临时打开 `org.hibernate.SQL` 日志复现一次。

## 参考来源

- [OpenJudge 仓库](https://github.com/agentscope-ai/OpenJudge)
- [OpenJudge 智能体评分器](https://agentscope-ai.github.io/OpenJudge/built_in_graders/agent_graders/)
- [AgentScope 使用 OpenJudge 评测](https://doc.agentscope.io/tutorial/task_eval_openjudge.html)
- [AgentScope Java 可观测与 Studio](https://java.agentscope.io/v1/en/docs/task/observability)
- [OpenTelemetry GenAI 智能体 span 约定](https://github.com/open-telemetry/semantic-conventions-genai/blob/main/docs/gen-ai/gen-ai-agent-spans.md)
- [阿里云云监控 2.0 接入 AgentScope Java](https://help.aliyun.com/en/cms/cloudmonitor-2-0/connecting-to-an-agentscope-java-application)
- [Langfuse 自部署概览](https://langfuse.com/docs/deployment/v3/overview)
- [Langfuse Docker Compose 部署要求](https://langfuse.com/docs/deployment/v3/guides/docker-compose)

外部资料内容已改写摘要，以原文为准。
