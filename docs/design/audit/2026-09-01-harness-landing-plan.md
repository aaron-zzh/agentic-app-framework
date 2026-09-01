---
level: Practice
layer: Product
purpose: 将 ADR-005 的 AgentScope core 复用边界、AAF Harness、AG-UI、非自主 L0 与 EXECUTOR 计划能力转化为可直接实施的分阶段方案
status: draft
version: 0.2.0
date: 2026-09-01
author: Kiro
scope:
  includes:
    - HarnessAgent 到 ReActAgent 的逐参数迁移与依赖收缩
    - AAF Harness 层目标模块、运行治理与 AgentScope 官方模块借鉴边界
    - AG-UI 事件完整性、converter 结构与持久 HITL 线协议
    - 非自主 L0 的 AgentScope Model 直调与旧 Spring AI L0 抽象退出
    - EXECUTOR 先规划后执行、计划持久化、状态机与事件合同
    - 核心流程注释清单、阶段门禁、RQ-01 至 RQ-13 闭环
  excludes:
    - 修改 ADR、规范或现有设计真理源
    - 启用官方 Harness workspace、filesystem、shell、sandbox、subagent 或 Markdown 持久化
    - 用官方 AG-UI starter 替换 AAF TaskBoard 入口
    - 本计划对应的源码、数据库迁移与测试实现
    - AgentScope 版本升级

gains:
  - 能按逐参数映射把 HarnessAgent 安全切换为 ReActAgent 并验证无隐式工具泄漏
  - 能按 AAF 安全事件边界补齐 AG-UI 必需事件与持久中断恢复
  - 能按唯一模块边界实现非自主 L0 直调与 EXECUTOR 持久计划
  - 能按阶段准入准出、证据链接和 RQ 对照表直接拆分开发与验收任务
changelog:
  - 2026-09-01 | 0.1.0 | 综合四份专项调研，形成 ADR-005 落地计划初稿
  - 2026-09-01 | 0.2.0 | 六条决策人类拍板后回写：L0 改回同步签名（依 ADR-003）、shadow 改为保留、思考模式拆三层并新增推理块回放立项、计划由执行 Agent 自产且只对 policy 标记任务生效、两表追加进 v16、新增当前开发阶段约束节
---

# AAF Harness 落地计划

## 背景与目标

ADR-005 已定案三条边界：TaskBoard 继续承担 L3/L4 外部编排，Agent 节点的执行内核从官方 `HarnessAgent` 收缩到 core `ReActAgent`，AG-UI 复用官方事件模型与编码器但不让 starter 接管 AAF 任务入口。本计划不重做决策，只把边界落实为类、接口、数据模型、测试和阶段门禁。`docs/design/adr/ADR-005-agentscope-boundary-and-orchestration.md`

目标态是“**AgentScope core 承担推理循环，AAF Harness 承担运行治理**”：core 负责模型流、ReAct、Toolkit、AgentState、权限原语和细粒度事件；AAF 负责规格冻结、最终工具面、租约与 fencing、预算、终态、事件事实、TaskBoard、HITL、协议投影和持久恢复。该边界与五层架构中 L2 只持执行期工作态、L3 持有 TaskBoard 与交付责任的约束一致。`docs/design/framework/intelligent/architecture.md`；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4143-4744`

本计划同时关闭四类已确认缺口：官方 Harness 在全部能力关闭后仍构造 workspace/filesystem/bus 并注册 `WaitAsyncResultsTool`；AAF 的 31 种 AgentEvent 只显式处理 13 种；非自主 L0 仍走 Spring AI `LlmClient`；EXECUTOR 目前从 child command 直接进入执行，没有自身计划阶段。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:2116-2246,2319-2371`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeEventMapper.java:51-134`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/prompt/PromptInvocationGateway.java:20-161`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/DelegatedTaskCoordinator.java:454-478`

## 已批决策与阶段约束

2026-09-01 人类逐条拍板，以下六条是本计划的执行前提，取代文中任何相反表述。

| # | 决策 | 结论 | 关键约束 |
|---|---|---|---|
| 一 | framework 直接依赖降为 `agentscope-core` | 批准 | 同批删除零使用的 `agentscope-extensions-skill-postgresql-repository` 与 `agentscope-extensions-oss`；victools 两个 `<exclusion>` 原样迁到 core 坐标；保留 redis 与三个 model 扩展 |
| 二 | ADR-005 增补勘误 | 批准 | 三处：starter 论据失实、RQ 状态过期、依赖坐标结论；以追加勘误节形式记录，不改历史正文 |
| 三 | EXECUTOR 计划能力 | 批准，范围收窄 | 只对 policy 显式标记需要计划的任务生效，不是所有 EXECUTOR；自动批准由**确定性白名单**驱动，模型给出的 risk 只作展示与审计字段；审批复用 `ai_hitl_approval`，不新建审批表 |
| 四 | 非自主 L0 单栈 | 批准，签名保持同步 | 按 ADR-003（全量同步 + 虚拟线程，仅 SSE 保留 Flux）；不把非流式路径响应式化；`CapabilityRouter` 调用前定唯一模型，provider 层不静默 fallback，`ResilientChatService` 退出智能层 L0 |
| 五 | 思考模式 | 拆为三层 | 模型侧思考按模型能力默认开启并参数化；原始 CoT 默认不外发；推理块回放单独立项 |
| 六 | `JsonSchemaUtils` shadow | 保留 | 上游 2.0.x 未修，删除必然 `NoSuchMethodError`；改为修正注释 + 用既有测试锁定行为，并登记为禁兼容层的显式例外 |

### 当前开发阶段约束

AAF 未 v1.0 发布，本阶段（截至门禁恢复前）额外适用以下三条，与 `AGENTS.md` / `.kiro/steering/collaboration.md` 的常态规则**存在已授权偏离**，必须显式记录而非静默覆盖：

| 约束 | 含义 | 与常态规则的偏离 | 恢复条件 |
|---|---|---|---|
| 直接修改已有 SQL，不新增迁移文件 | 新表追加进 `apps/service/aaf-api/src/main/resources/db/migration/v16__intelligent_runtime_schema.sql`（`ai_hitl_approval`/`ai_delegated_task`/`ai_task_board` 所在文件），不分配新 `vN` | 常态下 schema 变更应新增可回滚迁移；本阶段依赖重建库 | 首个需要保留生产数据的环境出现时立即恢复增量迁移 |
| 只调整已有测试，不新增测试文件 | 断言补进现有 `AgentScopeSpecCompilerTest`、`HarnessAgentExecutionAdapterTest`、`AgentScopeEventMapperFailureTest`、`AgentScopeRuntimeContextMapperTest`、`PromptEnvelopeCaptureMiddlewareTest`、`ToolResultEvidenceStoreTest`、`AgUiProjectorTest`、`DefaultEffectiveToolResolverTest`、`DelegatedTaskCoordinator*Test` | 常态下新能力应有对应新测试；测试分层规则本身不变（仍禁止把验收塞进单测） | 本阶段结束时按各工作包验收判据补齐缺失测试文件 |
| 本阶段不执行 `check` / `acceptance` | 改动后不跑 `pnpm check:affected` 与 `pnpm acceptance` | 直接偏离「完工门禁必须全绿」这条硬约束 | 门禁恢复前必须一次性跑通 `pnpm check` + `pnpm acceptance`，失败视为本阶段全部工作未完工 |
| **每阶段准出的最低要求：编译通过** | 每个阶段结束时必须 `pnpm nx compile service` 通过（`mvn test-compile`，编译 main + test 源码，不跑用例） | 这是对上一条偏离的强制缓解，不是可选项 | 随门禁恢复一并升级为完整 `check` |

`compile` target 为本次新增（`apps/service/project.json`），因为原先只有 `test`（编译 + 跑用例）与 `build`（打包），没有编译-only 入口，而"不跑测试但要保证能编译"正需要它。它不在 `nx.json` 的 `check.dependsOn` 列表中，不影响现有门禁行为。

> 第三条是本计划风险最高的偏离：编译错误与回归会累积到门禁恢复时集中爆发。第四条是它的强制缓解——`ReActAgent` 类型切换会连带改 compiler、execution adapter 与两个测试的泛型和 import，这类改动不编译完全看不出对错，七个阶段叠加后的定位成本会远超省下的时间。

## 决策基线与证据优先级

实施时按“当前 2.0.2 源码 → ADR-005 → `runtime.md` / `architecture.md` → 使用规范 → 调研报告”的顺序裁决行为事实；文档与源码冲突时记录差异并以源码编写测试。当前依赖版本是 AgentScope `2.0.2`，官方 core 的 `AgentEventType` 实际为 31 项，而旧注释仍写 28，证明不能只按文档或注释实现。`apps/service/aaf-dependencies/pom.xml:54-55`；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/AgentEventType.java:40-90`；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/agent/StreamableAgent.java:41-47`

| 已发现矛盾 | 源码判定 | 本计划处理 | 后续真理源修正建议 |
|---|---|---|---|
| ADR-005 称 `AguiAgentAdapter` 不能发 `Custom`、`ActivitySnapshot`、`outcome.interrupt`，并容易被理解为官方无 resolver 注入点。`docs/design/adr/ADR-005-agentscope-boundary-and-orchestration.md` | `AguiRequestProcessor.Builder` 已有 `agentResolver()` 与 `adapterFactory()`；Custom 和 interrupt outcome 已内置，Activity 可由 converter 发出。`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/processor/AguiRequestProcessor.java:312-359`；`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/adapter/strategy/AgentLifecycleEventConverter.java:60-137` | 保留“不采用 starter”结论；理由改为 TaskBoard 执行粒度、持久 HITL 和线程/入口治理不匹配，不再使用失实的扩展点论据。 | 由协调者增补 ADR-005 勘误：MVC Builder 仍无 resolver，但 processor 有；starter 可扩事件，仍不适合接管 AAF 任务级入口。 |
| `architecture.md` 把 L2 内层描述为“AgentScope Harness 的 ReAct”。`docs/design/framework/intelligent/architecture.md` | AAF 已决定不依赖官方 Harness 包装，内层是 core ReAct，外层才是 AAF Harness。`docs/design/adr/ADR-005-agentscope-boundary-and-orchestration.md` | 本计划中的“Harness”一律指 AAF 自研运行治理层；官方类型写全名 `HarnessAgent`。 | 代码落地后把表述改为“AAF Harness 包裹 AgentScope core ReAct”。 |
| 使用规范仍写“AAF 选用 `HarnessAgent` 承载 Agent 层执行”。`docs/reference/dev/agentscope-usage-guide.md` | ADR-005 已把编译目标改为 `ReActAgent`，且官方 Harness 关闭后仍泄漏工具。`docs/design/adr/ADR-005-agentscope-boundary-and-orchestration.md` | 不按该句实施；继续遵守其响应式、源码优先、builder 默认值必须验证等通用规则。 | 由协调者在 core 切换完成后升级使用规范，区分“官方推荐”与“AAF 选型”。 |
| `runtime.md` 与 `InvocationMode` 仍使用 `AUTONOMOUS_HARNESS`，容易被解读为官方 artifact。`docs/design/framework/intelligent/runtime.md`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/prompt/InvocationMode.java:4-7` | 调用形态仍然需要“自主循环/非自主 L0”两类，但实现不再依赖官方 Harness。 | 原子重命名为 `AUTONOMOUS_AGENT_LOOP`，不保留旧枚举别名。 | 同批更新设计文档术语；这是命名澄清，不改变运行模式合同。 |
| `runtime.md` 的 `### state.request` 标题与正文已改用 `forwardedProps.request` 的契约不一致。`docs/design/framework/intelligent/runtime.md` | Controller 已从 `forwardedProps` 组装三模式请求。`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AssistantAguiController.java:98-181,281-318` | 本计划不触碰该文档，但 AG-UI 实施不得把一次性请求参数写回 state。 | 由协调者修正标题，避免 AG-UI state 补齐时重引入语义错误。 |

## 现状判定：自研 Harness 层的真实边界

AAF 当前已经拥有一个自研 Harness，只是类名和包结构尚未显式表达：`AgentScopeSpecCompiler` 负责编译与缓存，`HarnessAgentExecutionAdapter` 负责活跃执行、取消、三类 timeout、唯一终态、顺序落库和清理，middleware 负责审计与计量，tool adapter 负责治理桥接，TaskBoard 负责外部编排。这些都不是官方 `HarnessAgent` 的替代 ReAct 实现，而是 ReAct 外层的业务运行治理。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:32-310`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java:61-720`

该边界必须继续遵守三个不变量：领域/应用层只依赖 `AgentExecutionPort`；所有模型可见工具都来自 AAF 最终白名单；持久任务事实只在 AAF DB/outbox，不写入官方 workspace 或短命 AgentState。工具链现已按 `AgentScopeToolkitFactory → PortBackedAgentTool → ToolGatewayPort` 经过授权与证据治理，TaskBoard 则持久化团队 DAG。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/AgentScopeToolkitFactory.java:25-48`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/PortBackedAgentTool.java:42-87`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/assistant/persistence/TaskBoardEntity.java:37-38`

切 core 后不得用另一套 wrapper 模拟官方 Harness，也不得保留 `HarnessAgent` 作为 fallback。两条 builder 链、缓存泛型、执行记录、interrupt 和 close 必须同批直接切到 `ReActAgent`，否则会形成双执行路径并继续携带隐式 workspace 对象。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:148-173,188-213`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java:611-613,672-719`

## 目标架构：AAF Harness 层模块划分

包结构以现有目录为基础渐进收敛，不进行任务外 broad refactor；只在职责已经混杂或新增能力有明确边界时增包。现有 `model/tool/state/definition/spring` 包保持位置，避免为“目录好看”移动稳定代码。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/package-info.java:1-60`

```text
com.xuejiai.aaf.framework.intelligent
├── infrastructure/agentscope/
│   ├── compiler/        # 冻结规格 → ReActAgent，最终工具面，缓存/生命周期
│   ├── execution/       # 执行注册、租约、timeout、CAS 终态、interrupt、落库
│   ├── mapping/         # Msg/RuntimeContext/31 类 AgentEvent → AAF ExecutionEvent
│   ├── middleware/      # Prompt envelope、计量、计划 acting gate 等横切能力
│   ├── model/           # Model 解析 + 非自主 L0 直调实现
│   ├── tool/            # Toolkit 白名单、ToolGateway、证据与最终工具校验
│   ├── state/           # AgentState 热状态、四维 namespace、Redis 原子操作
│   ├── definition/      # AAF AgentDefinition → 冻结 AgentSpec
│   └── spring/          # 唯一组合根
├── assistant/
│   ├── application/     # TaskBoard 外部编排与 EXECUTOR planning orchestration
│   ├── model/plan/      # ExecutorPlan 聚合、步骤、状态机、策略
│   ├── port/plan/       # 计划仓储、审批、事件追加端口
│   └── infrastructure/assistant/persistence/plan/ # JPA/Flyway 接线
└── core/prompt/         # 非自主 L0 Function Contract 与治理门面

com.xuejiai.aaf.module.ai.agui
├── AgUiProjector.java              # 唯一安全投影入口
├── converter/                      # ExecutionEvent → AguiEvent
├── AafAguiStreamContext.java       # 每 run 配对/缓冲/interrupt 状态
└── enricher/                       # timestamp/trace/protocol version 等安全补充
```

| 模块 | AAF 职责 | 官方对应参考 | 明确不承担 |
|---|---|---|---|
| compiler | 显式校验冻结规格，构建 `ReActAgent`，冻结最终 Toolkit，管理有界缓存与 close。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:64-304` | `ReActAgent.Builder.build()` 的 toolkit copy、middleware 排序。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4902-4953` | workspace、filesystem、官方 subagent、隐式 skill。 |
| execution | 运行注册、防重放、租约、总/idle/persist timeout、终态 CAS、interrupt 与资源释放。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java:134-717` | `streamEvents`、定向 `interrupt`、`close`。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:706-767,917-977,4133-4140` | TaskBoard 分解和协议投影。 |
| mapping | 穷举 31 种 AgentEvent；每项必须“映射/安全忽略/拒绝”，禁止无说明 default。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeEventMapper.java:51-134` | `AgentEventType` 与各事件字段。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/AgentEventType.java:40-90` | 直接生成面向客户端的 AG-UI 原始事件。 |
| middleware | 以稳定 order 装配 Prompt 审计、计量、executor plan gate；acting gate 拒绝时写标准 DENIED 结果。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/middleware/PromptEnvelopeCaptureMiddleware.java:42-192` | `MiddlewareBase` 五个拦截点与官方 `PlanModeMiddleware` 的 acting gate。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java:60-160`；`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/PlanModeMiddleware.java:168-233` | 只靠 prompt 约束副作用。 |
| model | 一个 `AgentScopeModelResolver` 服务自主与非自主调用；L0 直接调用 `Model.stream(...)`。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/model/AgentScopeModelResolver.java:20-84` | `Model.stream(List<Msg>, List<ToolSchema>, GenerateOptions)`。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/model/Model.java:22-39` | 第二套路由、第二份 provider 配置。 |
| tool | AAF registry 精确版本、Connector、授权、幂等、证据；构建后断言泄漏工具不存在。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/RegistryToolPortAdapter.java:31-204` | `Toolkit.copy()`、registration/group。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/Toolkit.java:264-350,809-1042` | tools.json、默认 filesystem/shell、MCP 自动发现。 |
| state | Redis 只保存可删除的执行热状态；tenant/user/task/agent/execution 组合 namespace；DB 事实不双写。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeRuntimeContextMapper.java:24-74` | `AgentStateStore` 只有 user/session/key，必须外层增维。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/state/AgentStateStore.java:34-104` | ExecutorPlan、审批、TaskBoard 的权威持久化。 |
| assistant plan | L3 持有 executor plan 聚合、审批和恢复；L2 只拿计划快照执行。`docs/design/framework/intelligent/architecture.md` | 官方 Plan Mode 的“状态闸门 + 计划正文分离 + BUILD 重读”。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/workspace/plan/PlanModeManager.java:39-114` | PLAN.md、tasksContext 作为任务真理源。 |
| AG-UI | 从持久且脱敏的 `ExecutionEvent` 投影官方 `AguiEvent`，维护每 run 配对不变量。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/shared/event/publication/ExecutionEventPublicMapper.java:16-206` | 官方 registry/context/enricher 的结构与 28 个当前事件 DTO。`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/adapter/strategy/AgentEventConverterRegistry.java:32-138` | 绕过 AAF 事实源直接把 AgentEvent 发给客户端。 |

## 主线：正确复用 AgentScope core

### builder 逐参数迁移

两条现有 builder 链必须收敛到一个私有构建方法，动态与预定义只负责准备冻结输入；这消除 15 个重复 disable 调用，但不借机重写缓存或定义模型。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:148-221`

```java
private ReActAgent buildAgent(
        String businessAgentId,
        String name,
        String description,
        String systemPrompt,
        Model model,
        Toolkit toolkit,
        AgentStateStore stateStore,
        ExecutionPolicy policy) {
    return ReActAgent.builder()
            .name(name)
            .description(description)
            .sysPrompt(systemPrompt)
            .model(model)
            .toolkit(toolkit)
            .stateStore(stateStore)
            .middleware(promptEnvelopeCaptureMiddleware)
            .maxIters(policy.maxIterations())
            .maxRetries(policy.maxModelRetries())
            .dynamicSkillsEnabled(false)
            .enableMetaTool(false)
            .enablePendingToolRecovery(false)
            .build();
}
```

`model`、正数迭代/重试、非空 Toolkit 和最终工具集合要在进入 builder 前由 AAF 显式校验，因为 core `build()` 没有统一必填校验且会立即 `toolkit.copy()`。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4143-4151,4902-4953`

| 当前 Harness 调用 | `ReActAgent.Builder` 映射 | AAF 处置与不变量 |
|---|---|---|
| `name` / `description` / `sysPrompt` | 原样映射。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4230-4255` | 冻结 Prompt 内容和 hash 继续进入 cache key；不得运行中改写。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:64-105` |
| `model` | 原样映射。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4263-4282` | 只由 `AgentScopeModelResolver` 从 DB modelId 解析，禁止 builder 内第二次路由。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/model/AgentScopeModelResolver.java:31-83` |
| `toolkit` | 原样映射，core 会深拷贝。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4290-4293,4902-4905` | AAF 在 build 前生成白名单，在 build 后通过测试读取最终 schema/name 快照；不得出现 `wait_async_results`、filesystem、shell、plan 或 skill 管理工具。 |
| `stateStore` | 原样映射。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4501-4509` | 保留 Redis 热状态；namespace 继续含 tenant/user/task/agent/execution，结束即删。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java:631-654` |
| `middleware` | 原样映射；明确 order。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4345-4359,4946-4948` | 首批只装 `PromptEnvelopeCaptureMiddleware`；计量继续消费事件流。后续 plan gate 作为独立 middleware 加入，不塞进 compiler switch。 |
| `maxIters` / `maxRetries` | 原样映射。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4301-4304,4525-4531` | 继续来自冻结 `ExecutionPolicy` 并纳入 DIRECT cache key。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:84-105` |
| `agentId` | **无映射**；core 运行时 ID 自动生成。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/agent/AgentBase.java:132-140` | 业务 agentId 只留在 `AgentSpec`、cache key、RuntimeContext 和事件，不伪装成 core runtime ID。 |
| `disableMemoryTools()` / `disableMemoryHooks()` | 删除；core 不会自动安装 Harness MEMORY 工具/hook。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:1713-1725,1981-2003` | 长期记忆仍唯一归 AAF Cognition，不启用 deprecated `longTermMemory`。 |
| `disableWorkspaceContext()` / `disableAtPathExpansion()` | 删除；core 无 workspace 和 `@path`。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:2011-2019` | 上下文只从冻结画像注入。 |
| `disableSubagents()` / `disableDynamicSubagents()` | 删除；core 不会自动注册 spawn 工具。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:1979-1983,2021-2024` | TaskBoard 是唯一外部编排；最终 Toolkit 测试禁止 `agent_spawn/send/list`。 |
| `disableDynamicSkills()` / `disableDefaultWorkspaceSkills()` / `skillsEnabled(false)` | Harness 调用删除；core 显式 `.dynamicSkillsEnabled(false)`。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4707-4716` | AAF Skill 仍是 Assistant 路由/能力冻结，不注册 AgentScope workspace skill。 |
| `disableToolsConfig()` | 删除；core 无 tools.json。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:1745-1748,2026-2029` | 工具只来自 `AgentScopeToolkitFactory`。 |
| `disableFilesystemTools()` / `disableShellTool()` | 删除；core 不自动注册。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:1850-1859` | 最终工具面断言是安全门，不以“没调用 builder”替代测试。 |
| `disableCompaction()` | 删除；core 无 Harness compaction。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:1695-1711` | AAF 继续使用自己的 compressor；若将来迁移，另立设计，禁止双压缩。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/cognition/application/DefaultHybridContextCompressor.java:63-73` |
| `disableToolResultEviction()` | 删除；core 不装该 middleware。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:1727-1742` | `ToolResultEvidenceStore` 只保留审计证据职责，不能假装是大结果 eviction。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/ToolResultEvidenceStore.java:34-164` |
| `enableAgentTracingLog(false)` | 删除；core 不装 Harness trace log。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:1840-1847,2251-2254` | AAF ExecutionEvent、PromptEnvelope、token observer 是唯一审计链。 |
| 当前未设置 `permissionContext` | 可映射，但不在 core 切换批次启用。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4564-4571` | 先保持 `PortBackedAgentTool → ToolGatewayPort` 的授权事实链；PermissionContext 接入需独立安全设计，避免两套 ASK 状态。 |
| 当前未设置 task/meta/pending recovery | 显式 `enableMetaTool(false)`、`enablePendingToolRecovery(false)`，不调用 `enableTaskList()`。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4361-4419` | 不把 core tasksContext、meta tool 或 pending recovery 变成第二真理源。 |

### 类型、生命周期与泄漏消除

`AgentScopeSpecCompiler` 的 `compile/compileDirect/compileDynamicNew/compileNew` 返回值、两个 `BoundedAgentCache` value 和内部构建方法统一改成 `ReActAgent`；`ResolvedExecution`、`ActiveExecution` 也改 core 类型。中断从 `active.agent().getDelegate().interrupt(context)` 改为 `active.agent().interrupt(context)`，`streamEvents` 与 `close` 保持 core 原生调用。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:21,64-221,236-309`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java:374,611-613,672-719`；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:733-767,917-977`

切换后 `WorkspaceManager`、`WorkspaceMessageBus`、`WorkspaceAsyncToolRegistry`、`InboxMiddleware` 与 `WaitAsyncResultsTool` 不再有构造入口；验收不能只检查源码 import，必须对最终 Toolkit 工具名和工作目录副作用做负向测试。官方 Harness 即使全部 disable 仍会创建这些对象并注册 wait tool。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:2215-2246,2319-2321,2362-2371`

缓存继续复用同一 `ReActAgent`，但以 2.0.2 的 per `(userId, sessionId)` FIFO gate 和仓内并发测试为升级契约；不同槽可并行，跨节点一致性仍由 AAF 租约/fencing 保证。每次升级 AgentScope 必须重跑同槽串行、跨槽隔离、cancel/close 竞态测试，不能只信类头旧注释。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/agent/AgentBase.java:228-351`；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:347-359,444-501`

### 依赖坐标建议

**建议把 `aaf-framework` 的直接依赖从 `io.agentscope:agentscope-harness` 降为显式 `io.agentscope:agentscope-core`，同批删除所有生产与测试 `io.agentscope.harness.*` import，不保留 optional harness。** 原因是生产代码只需 core ReAct/Model/Toolkit/State/Middleware；保留 harness 会让隐式 workspace 能力重新进入可见 classpath，也无法由依赖边界证明 AAF 没有误用。当前四处 harness import 集中在 compiler、execution 与对应测试。`apps/service/aaf-framework/pom.xml:140-184`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:21`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java:49`

同批**删除两个零使用依赖**：`agentscope-extensions-skill-postgresql-repository`（全仓无 `io.agentscope.core.skill.repository.postgresql` 与 `AgentSkillRepository` 引用，且与本计划"不用官方 Skill 仓库"的结论矛盾）与 `agentscope-extensions-oss`（全仓无引用，pom 注释给的理由"harness 线程队列/元数据后端"随 harness 一起失效）。`agentscope-extensions-redis` 真在使用（`RedisAgentStateStore`、`RedisClientAdapter`），保留。`apps/service/aaf-framework/pom.xml:170-184`

`agentscope-extensions-agui` 继续只放 `aaf-api`；三个 model extension 与 Redis extension 留在 framework。`agentscope-extensions-postgresql` 不为 AgentState 引入，因为其 artifact 提供 harness `DistributedStore` 且会重新扩大边界；Redis 与 PostgreSQL 也不得双写同一 AgentState。`apps/service/aaf-api/pom.xml:20-25`；`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-postgresql/pom.xml:23-38`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/spring/AgentScopeInfrastructureAutoConfiguration.java:57-87`

**`io.agentscope.core.util.JsonSchemaUtils` shadow 保留，不删除。** 此前"实测若官方类可用即删"的建议不成立：上游 2.0.x 的 `JsonSchemaUtils` 源码仍 import Jackson 2 的 `com.fasterxml.jackson.databind.JsonNode` 与 victools 4 风格的 `JacksonModule`，AgentScope BOM 钉 `jackson 2.21.1` + `jsonschema-generator 4.38.0`；而 AAF 钉 `jsonschema-generator 5.0.0`（Spring AI on Jackson 3 要求）并在 pom 排除 agentscope 带来的 victools。两边 API 不兼容，删除 shadow 必然 `NoSuchMethodError`，实测结果是确定的、无需再验证。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java:17-30`；`tmp/agentscope-java/agentscope-dependencies-bom/pom.xml`；`apps/service/aaf-dependencies/pom.xml:70`；`apps/service/aaf-framework/pom.xml:146-155`

保留的处置要求三条：

- 修正类注释——问题不是"RC4 jar 用 Jackson 2 编译"，而是 **2.0.2 + victools 4→5 的 API 断层加 Jackson 2/3 边界**；当前注释的版本描述已失真。`apps/service/aaf-framework/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java:35-41`
- 在既有 `AgentScopeSpecCompilerTest` 或 `DefaultEffectiveToolResolverTest` 中补断言锁定工具 schema 生成行为（按阶段约束不新增测试文件），使升级 AgentScope 时该 shadow 失效能被测试发现。
- 登记为**禁兼容层硬规则的显式例外**：它确实是 classpath shadowing。退出路径仍是独立坐标最小 fork 或上游 PR，但不在本计划范围。另可低成本验证一条：若 AAF 工具始终显式提供 `ToolSchema`，core 的 schema 生成可能根本不被调用，那时 shadow 可无代价删除——该验证列为可选前置，不阻塞主线。

### core 迁移工作包

| 工作包 | 改动 | 验收判据 | 证据回链 |
|---|---|---|---|
| HLP-C1 | 把 compiler/缓存/执行记录类型原子切为 `ReActAgent`，抽取唯一 `buildAgent`。 | 无 `HarnessAgent` import；预定义、DIRECT、ephemeral 三路径行为不变。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:64-221` |
| HLP-C2 | 删除 15 个 Harness-only 开关，显式关闭 core dynamic skill/meta/pending recovery。 | 最终工具面仅等于 AAF 冻结白名单；无 `wait_async_results`、filesystem、shell、spawn。 | `tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:2213-2621` |
| HLP-C3 | 中断改为 `ReActAgent.interrupt(RuntimeContext)`；保留 CAS、timeout、落库、cleanup。 | cancel/complete 竞争仍只产生一个终态；close 恰好一次。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java:408-466,585-669` |
| HLP-C4 | POM 直接依赖降为 core，更新 BOM/POM 说明并核验 shadow。 | dependency tree 不含直接 harness；schema 测试通过；无兼容 shim。 | `apps/service/aaf-dependencies/pom.xml:127-144`；`apps/service/aaf-framework/pom.xml:140-184` |
| HLP-C5 | 补 RQ-11/12/13：重复订阅事件化、interrupt/close 时序、Redis 原子登记与 SCAN。 | 对应新测试通过；Redis 生产路径不调用 `KEYS`。 | `docs/design/audit/2026-08-30-agentscope-boundary/02-runtime-quality.md` |

## 主线：AG-UI 事件完整性

### 当前覆盖度与目标处置

官方 Java 库覆盖 28/28 个当前非废弃 AG-UI 事件，只缺 5 个已废弃 `THINKING_*` 别名；AAF 当前实际产生 10 种。目标不是机械发满 28 种，而是为每种事件做“必须产出 / 按需产出 / 明确不产出”的协议决策，并用配对测试证明完整性。`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/event/AguiEventType.java:21-130`；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AgUiProjector.java`

| 事件族 | 当前 | 目标处置 | 上游输入改动 |
|---|---|---|---|
| `RUN_STARTED` | 已有，但缺 `parentRunId/input`。`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AgUiProjector.java` | 必须产出；补 `parentRunId`，`input` 只放协议允许且已脱敏的请求摘要，不放系统 Prompt。 | `ExecutionEvent`/run context 保留 parent run 关联。 |
| `RUN_FINISHED` / `RUN_ERROR` | success/failure/cancel 都以空 outcome 结束；cancel 被错误化。`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AgUiProjector.java` | 成功用 `RunFinishedSuccessOutcome`；等待输入用 interrupt outcome，业务取消用稳定 code + finished；不可恢复失败才 `RunError`。 | 保留暂停原因、interruptId、toolCallId、schema、expiresAt。官方字段：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/event/AguiEvent.java:1633-1695`。 |
| `TEXT_MESSAGE_START/CONTENT/END` | 已有，但 messageId 退化为 executionId。`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AgUiProjector.java` | 必须产出；使用 AgentScope `replyId:blockId` 派生稳定 messageId，支持单 execution 多文本块。 | mapper 补 `TEXT_BLOCK_END` 并在 start/delta/end 全程保留 replyId/blockId。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/TextBlockStartEvent.java:23-54`。 |
| `TEXT_MESSAGE_CHUNK` | 未用。 | 明确不产出；已有 canonical start/content/end，避免双路径。 | 无。 |
| `TOOL_CALL_START/ARGS/END/RESULT` | 缺 ARGS，流式参数在 mapper 丢失。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeEventMapper.java:110-113,132` | 四类必须配对产出；ARGS 只输出按 schema 脱敏后的 JSON delta，无法安全逐片脱敏时缓冲到完整对象后再发一个 delta。 | 映射 `TOOL_CALL_DELTA/END`；保留 toolCallId/replyId/name。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/ToolCallDeltaEvent.java:26-74`。 |
| `TOOL_CALL_CHUNK` | 未用。 | 明确不产出；采用 canonical 工具四段事件。 | 无。 |
| `STATE_SNAPSHOT/DELTA` | 请求接收 state，但不消费、不回吐。`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AssistantAguiController.java:320-352` | 首次/重连发 snapshot，运行中发 task-view delta；state 只含页面感知上下文，禁止回吐 `forwardedProps`、凭证、Prompt 或完整 TaskBoard。 | 新增安全 `AgUiStateView`，从 TaskBoard snapshot/public event 生成。 |
| `STEP_STARTED/FINISHED` | 未产出，阶段走 Custom。 | 对稳定 planning/execution/verification/aggregation 阶段必须成对产出；taskId 等完整拓扑仍走 Activity/Custom。 | 计划与 TaskBoard 状态事件需有稳定 step key。 |
| `MESSAGES_SNAPSHOT` | 未产出。 | 断线重连或显式 snapshot 请求时产出，不在每次增量流重复发送。 | 从会话公共消息读取端口构造，不从 AgentState 隐式历史读取。 |
| `ACTIVITY_SNAPSHOT/DELTA` | 未产出。`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AgUiProjector.java` | 用于 TaskBoard、executor plan、审批和子任务进度；snapshot 初始化，delta 用 JSON Patch。 | 从安全 TaskBoard view 和 public events 投影。官方字段：`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/event/AguiEvent.java:1438-1566`。 |
| `REASONING_*` | 全缺。 | **不外发原始 CoT**（这是披露层决策，与模型是否思考无关，见[思考模式的三层边界](#思考模式的三层边界)）；provider 提供可公开 reasoning 摘要时，由租户级开关决定是否发摘要。"正在规划/执行"用 STEP/Activity 表达，不伪造成 reasoning。 | `THINKING_BLOCK_*` 在 `AgentScopeEventMapper` 层就不进入公共事件流（不是在投影层过滤），否则新增一个 converter 即可泄漏。 |
| `REASONING_ENCRYPTED_VALUE` | 未产出。 | 当前明确不产出；只有端到端密文保真需求和独立安全设计后启用。 | 无。 |
| `CUSTOM` | 已作为所有 AAF 事件兜底。 | 保留 `aaf.authorization.*`、审批、澄清、任务拓扑、证据等无一等协议类型的安全扩展；不再把可标准化事件全部降级为 Custom。 | 扩展 AAF converter registry。 |
| `RAW` | 未产出。 | 明确不产出；不得绕过 `ExecutionEventPublicMapper` 暴露内部事件。 | 无。 |

### 协调者与执行者的事件区分（2026-09-01 修正）

AAF 的事件流是**编排层事件 + ReActAgent 事件的并集**，且 TaskBoard 多节点场景下协调者与执行者各自是独立 execution。区分机制定案如下，取代本节此前"三段 messageId + parentRunId + Activity 拓扑"的初步设想。

**采用官方 `source` 路径约定，但由 AAF 自行指定。** 官方靠 `AgentEvent.getSource()` 区分父子（父为 `null`，子为 `"main/researcher"` 斜杠路径），前提是子事件被转发进父的同一条 `streamEvents()` 流——那是嵌套委派模型。AAF 是平级编排（ADR-005 议题二），每个节点是独立流，因此 core 给出的 `source` 对所有节点恒为 `null`，不能直接用。但 `source` 只是一套约定而非 core 的内生能力，AAF 内部有 `ExecutionEvent.parentExecutionId`，可以在映射层自行合成同形状的路径。这样线协议与官方一致，客户端与官方 converter 的既有理解不必改。参考：<https://java.agentscope.io/v2/zh/docs/harness/subagent.html#streamevents>

**线协议形状对齐官方 `SubagentEventConverter`**：

| 节点 | AG-UI 表达 | 理由 |
|---|---|---|
| 根节点（`parentExecutionId == null`，面向用户的应答者） | 标准事件：`TEXT_MESSAGE_*` / `TOOL_CALL_*` / `RUN_*` | 它的输出就是给用户看的助手回复 |
| 非根节点（执行者、子任务） | `CUSTOM`，payload 带 `source` 路径 + 原始事件类型 | AG-UI 的 `TEXT_MESSAGE` 语义是"最终助手回复"，把执行者的中间推理混入会让客户端把碎片当主回复渲染。官方 `SubagentEventConverter` 正是这样处理（`source != null` 一律降级 CUSTOM，不按类型注册）。`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/adapter/strategy/SubagentEventConverter.java:33,51,131-139` |

由此产生的连带简化：**只有根节点发 `TEXT_MESSAGE`**，messageId 的跨执行冲突问题自然消失，`replyId:blockId` 即可满足唯一性；`executionId` 段保留但降为防御性前缀，不再是必需。`RunStarted.parentRunId` 仍建议填真值以表达 run 嵌套，但不再是区分的唯一依赖。

**前置条件不变**：`AafAiTaskEvent` 必须补 `parentExecutionId` 与节点归属（角色或路径）。公共事件不透出这两者，投影层无法判断标准/CUSTOM 分支，也无法合成 `source`。这一步涉及"内部标识是否可对外暴露"的安全边界，需要人类确认暴露形式（原始 agentId 还是稳定节点标签）。

**借鉴 `remoteStreamDetail` 的详细度分级**：官方对远程子 agent 提供 `STATUS` / `FULL` / `VERBOSE` 三档，理由是"对只渲染文本的调用方来说这些事件纯粹是额外流量"。AAF 多节点并行（协调者 + N 执行者）有同样问题，Activity/Step 的详细度应可配置，不一刀切全发。

**已核实无需处理**：执行者失败不会中断整板事件流。`DelegatedTaskCoordinator.executeSubTask` 用 `onErrorResume` 就地吞掉子流异常并转 `Flux.empty()`，`onError` 不传播到 `executeBoard` 的 `flatMap`，因此不会取消其余并行节点——与官方"子 agent 出错写成 TOOL_RESULT、不传播 onError"等价。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/DelegatedTaskCoordinator.java:513-517`

**待确认**：哪个节点是面向用户的应答者。当前存在 AGGREGATOR 概念（`DelegatedTaskCoordinatorAggregatorCompletionTest`），最终文本可能来自聚合者而非协调者。判定规则暂定为 `parentExecutionId == null` 的根执行，实施前需与 `runtime.md` 的交付责任定义核对。

### converter/registry 结构决策

**不直接改用官方 `AgentEventConverterRegistry`/`AguiStreamContext`，但采用同构结构。** 官方 registry 的输入固定为 AgentScope `AgentEvent`，而 AAF 出口输入必须是已持久化并经安全映射的 `ExecutionEvent`；直接套官方 registry 会绕过租户、TaskBoard 和证据脱敏边界。`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/adapter/strategy/AgentEventConverter.java:28-44`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/shared/event/publication/ExecutionEventPublicMapper.java:16-206`

```java
public interface AafAguiEventConverter {
    Set<ExecutionEventType> supportedTypes();
    List<AguiEvent> convert(
            AafAiTaskEvent event,
            AafAguiStreamContext context);
}

public final class AafAguiConverterRegistry {
    public List<AguiEvent> convert(
            AafAiTaskEvent event,
            AafAguiStreamContext context);
}

public interface AafAguiEventEnricher {
    List<AguiEvent> enrich(
            AafAiTaskEvent source,
            List<AguiEvent> converted,
            AafAguiStreamContext context);
}
```

`AgUiProjector` 保留为唯一 facade，只负责创建 context、调用 registry、流尾 `finish()` 和拒绝非法终态；converter 按 lifecycle/text/tool/state/step/activity/interrupt/custom 分小类。registry 启动时必须拒绝重复 `ExecutionEventType` 注册，不能采用“后注册静默覆盖”，因为 AAF 公共事件是安全合同而非插件优先级。官方 registry 的上下文缓冲、流尾闭合和 enricher 顺序可作为算法参考。`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/adapter/strategy/AguiStreamContext.java:36-108,110-289,329-372`

### 持久 interrupt/resume 闭环

`RunFinished.outcome.interrupts[]` 与下一次 run 的 `resume[]` 采用官方线协议，但 pending interrupt 的真理源继续是 AAF HITL/审批表。请求 record 增加 `resume`，字段严格为 `interruptId/status/payload`；`status` 只允许 `resolved/cancelled`，业务拒绝是 `resolved + {approved:false}`，不是 cancelled。`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/model/AguiResume.java:23-104`

```java
public record ResumeInput(
        String interruptId,
        ResumeStatus status,
        Object payload) {}

public enum ResumeStatus { RESOLVED, CANCELLED }
```

Controller 必须在启动新 execution 前以 `(tenantId, threadId, interruptId)` 原子 resolve AAF 持久记录，校验调用人、过期时间、run 关联和精确全集；然后把结果转换为 ToolGateway/HITL 恢复输入。不得实例化官方 `AguiResumeCoordinator` 作为状态仓库，因为其 active run 与 pending interrupt 都只是进程内 `ConcurrentHashMap`。`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/processor/AguiResumeCoordinator.java:39-48,110-198`

### 对 ADR-005 starter 论据的核实结论

“不采用 starter”的最终结论仍正确，但准确理由是：`AguiMvcController.Builder` 不暴露 resolver，默认以 registry 的单 Agent 为执行货币；starter 的 cached thread pool 与 AAF 虚拟线程/安全上下文策略不一致；官方 resume coordinator 非持久；最关键的是 starter 不能替代 AAF TaskBoard、租约、画像和事件事实入口。`tmp/agentscope-java/agentscope-extensions/agentscope-spring-boot-starters/agentscope-agui-spring-boot-starter/src/main/java/io/agentscope/spring/boot/agui/mvc/AguiMvcController.java:82-103,337-448`

必须如实废弃的旧论据是“官方没有 resolver/factory，adapter 不能发 Custom/Activity/interrupt”：processor 已有 resolver/factory，starter 会收集 converter/enricher/factory bean，Custom 与 interrupt 内置。建议以 ADR 增补而非改写历史正文的方式记录 2.0.2 核实结果。`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/processor/AguiRequestProcessor.java:312-359`；`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/adapter/strategy/CustomAgentEventConverter.java:26-49`

### AG-UI 工作包

| 工作包 | 改动 | 验收判据 | 证据回链 |
|---|---|---|---|
| HLP-A1 | 先扩 `AgentScopeEventMapper`：补 text end、tool args/end、tool result 流、confirm/external result；对 31 项建立显式策略。 | 枚举新增项会使测试失败；无无说明 default；CoT 默认不外发。 | `tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/AgentEventType.java:40-90` |
| HLP-A2 | 建 AAF converter registry/context/enricher，瘦身 `AgUiProjector`。 | 每个 converter 独立单测；同 run 的 start/end 配对且 finish 只一次。 | `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AgUiProjector.java` |
| HLP-A3 | 补 tool args、state、step、messages、activity、success/interrupt outcome。 | 官方 `@ag-ui/core` schema 契约测试通过；重连 snapshot 与增量一致。 | `tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/event/AguiEvent.java:43-1695` |
| HLP-A4 | 增加 resume 输入并接 AAF 持久 HITL；不使用官方内存 coordinator。 | 重启/跨副本后可恢复；缺失、重复、未知、过期 interrupt 全部 fail closed。 | `tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/processor/AguiResumeCoordinator.java:53-198` |
| HLP-A5 | 为 ADR-005 准备勘误提案，不在本任务修改 ADR。 | 人类确认后由协调者增补，保留历史决策链。 | `docs/design/adr/ADR-005-agentscope-boundary-and-orchestration.md` |

## 主线：优化 AAF 自研 Harness 层

### 非自主 L0 改为 AgentScope 直调

目标是所有智能层 `NON_AUTONOMOUS_L0` 都经 `PromptInvocationGateway` 调 AgentScope `Model`，不再同时维护 Spring AI 与 AgentScope 两套消息、流、精确模型调用抽象。该变更不创建 `ReActAgent`、Assistant、持久 Task 或工具循环，符合 L0 请求级、无人格无记忆的设计。`docs/design/framework/intelligent/architecture.md`

具体处置必须原子完成，不留兼容层：

- 保留 `PromptInvocationGateway` 作为 Function Contract、classified message、preflight、logicalInvocationId 和计量门面；其执行依赖从 `LlmClient` 替换为 `NonAutonomousModelInvoker`。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/prompt/PromptInvocationGateway.java:20-161`
- 删除 `LlmClient`、`SpringAiLlmClient`；把 `MockLlmClient` 改为同名新端口的测试实现，或直接用测试 fixture，不保留旧接口适配器。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/llm/LlmClient.java:10-46`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/ai/chat/SpringAiLlmClient.java:20-61`
- 新增唯一生产实现 `AgentScopeNonAutonomousModelInvoker`，复用 `AgentScopeModelResolver`，把 `LlmMessage` 替换为 AgentScope `Msg`/`ContentBlock` 映射；调用 `Model.stream(messages, List.of(), options)`，聚合 `ChatResponse.content/usage/finishReason`。官方签名与响应字段见 `tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/model/Model.java:22-39`、`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/model/ChatResponse.java:29-94`。
- 所有智能层直接 `LlmClient.call/callExact` 的类改走 gateway；不允许业务类直接注入 invoker。影响点为 `ParameterExtractionNode`、`IntentUnderstandingService`、`EmotionPerceptionService`、`DefaultSessionContextCompressor`、`DefaultHybridContextCompressor`。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/workflow/node/ParameterExtractionNode.java:40`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/cognition/application/DefaultHybridContextCompressor.java:275`
- 已走 gateway 的 `DefaultRoleSelector`、`DefaultInputClassifier`、`ModelDrivenTaskComplexityAnalyzer`、`ModelSkillSelectionPort` 只需保持契约测试。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/DefaultRoleSelector.java:83`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/ModelSkillSelectionPort.java:38`
- `ResilientChatService` 暂时保留给四个非智能层/非 L0 的 Spring AI 直接用户，不纳入本批重构；但它不再是智能层 L0 后端。`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/trending/TrendingService.java:69`；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/tool/meeting/MeetingOrganizeService.java:70`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/engine/tool/generator/ToolGenerator.java:53`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/engine/dataprocess/AiEnricher.java:74`

模型选择不新增抽象：复用现有 `CapabilityRouter.resolve(CapabilityRoutingContext)` 得到唯一 `AiModel`，再让 `AgentScopeModelResolver` 新增 `resolve(AiModel)` 公共重载；现有 `resolve(ModelSpec)` 委托该重载。`CapabilityRouter` 已承载显式模型、编排配置、AI 选择、用户偏好、系统默认和 YAML 兜底六层决策，不应在 invoker 内复制。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/model/CapabilityRouter.java:17-26`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/model/DefaultCapabilityRouter.java:18-126`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/model/AgentScopeModelResolver.java:31-83`

新端口是对旧 `LlmClient` 的**直接替换**，全部输入/输出类型定义如下；旧接口与新接口不得同时成为 Spring Bean：

```java
public interface NonAutonomousModelInvoker {
    ModelInvocationResult invoke(ModelInvocation request);

    record ModelInvocation(
            AiModel model,
            List<ModelMessage> messages,
            ModelInvocationOptions options,
            String logicalInvocationId,
            Long meteringUserId) {}

    record ModelMessage(String role, String content) {}

    record ModelInvocationOptions(
            Duration timeout,
            int maxAttempts,
            Object responseSchema,
            Integer thinkingBudget,
            String reasoningEffort) {}

    record ModelInvocationResult(
            String text,
            long inputTokens,
            long outputTokens,
            String finishReason) {}
}
```

`PromptInvocationGateway.call(NonAutonomousInvocation)` **保持同步返回值**，签名从 `String` 改为 `ModelInvocationResult`；五个调用者的同步调用链不变，不新增 `Flux` 重载。依据 ADR-003（已 accepted）：AAF 全量同步 + 虚拟线程，**仅 SSE 流式输出保留 Flux**，非流式路径引入响应式与该决策直接冲突。当前五个 L0 调用者（意图理解、参数抽取、情感感知、会话压缩、混合上下文压缩）都不把 L0 输出流给客户端，没有响应式化的业务依据。`docs/design/adr/ADR-003-virtual-threads-over-webflux.md`

生产实现在 invoker 内部消费 `Model.stream(...)` 的 `Flux<ChatResponse>` 并收敛为完整结果——虚拟线程上阻塞等待不 pin carrier thread，正是 ADR-003 选项 B 的预期用法。收敛规则：把 `ModelMessage` 映射为 AgentScope `Msg`，options 映射为 `GenerateOptions`；每个 `ChatResponse` 的 `TextBlock` 是增量，按官方 `ReasoningContext` 的策略顺序拼接，usage 采用最后一个非空快照，finishReason 采用最后一个非空值。将来若出现"L0 输出需要流给前端"的真实场景，再单独加 `Flux` 重载，不为假设需求预留。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/model/Model.java:22-39`；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/model/ChatResponse.java:29-94`；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/agent/accumulator/ReasoningContext.java:44-167`

结构化输出不再保留 `callExact` 第二方法：`PromptInvocationGateway` 按 Function Contract 填写 `ModelInvocationOptions.responseSchema`，生产实现构造 `GenerateOptions.responseFormat`，调用统一 `invoke`，gateway 再做严格 schema 解析；模型不支持 native structured output 时 L0 直接失败或由调用前模型路由重选，不能偷偷升级成 ReAct 工具循环。core 明确通过 `supportsNativeStructuredOutput()` 暴露能力。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/model/Model.java:41-62`

Spring AI/AgentScope 的模型 fallback 也不能并存：`CapabilityRouter` 在调用前解析一个明确 `AiModel`，invoker 只调用该模型；失败按 Function Contract 返回稳定失败，不在 provider 层静默切模型。当前 `ResilientChatService` 仍承载另一条多层路由/fallback，因此它必须退出智能层 L0，而不是被新 invoker 包一层。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/ai/chat/ResilientChatService.java:36-388`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/model/DefaultCapabilityRouter.java:18-126`

| 工作包 | 改动 | 验收判据 | 证据回链 |
|---|---|---|---|
| HLP-L1 | 定义替代端口和 AgentScope 实现，gateway 改注入新端口。 | 智能层生产 Bean 图中没有 `LlmClient`/`SpringAiLlmClient`。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/prompt/PromptInvocationGateway.java:20-60` |
| HLP-L2 | 五个直接调用类统一迁入 gateway，删除 `callExact` 旁路。 | 所有 L0 调用都有 Function Contract、preflight、logicalInvocationId。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/cognition/personalization/IntentUnderstandingService.java:55,74` |
| HLP-L3 | 调整 auto-configuration 条件和测试 fixture，原子删除旧接口与实现。 | 无 fallback/shim/双 Bean；上下文、usage、结构化输出合同测试通过。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/assistant/spring/AssistantInfrastructureAutoConfiguration.java:178-208,245-267,353-355` |
| HLP-L4 | 保留并标记 `ResilientChatService` 的四个非 L0 用户为独立边界。 | 本批不重构四个业务调用；无智能层 L0 反向依赖它。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/engine/dataprocess/AiEnricher.java:74` |

### 思考模式的三层边界

AAF 代码里已存在三个独立概念，此前被"reasoning 默认关闭"一句混为一谈。三者分别决策，互不代替。

| 层 | 是什么 | 当前实现与默认值 | 本计划结论 |
|---|---|---|---|
| 模型侧思考 | 让模型进入 thinking / reasoning 模式，影响**答案质量** | `AiModel.enableThinking`（DB 列 `enable_thinking`，默认 `false`），经 `DynamicChatClientFactory` 用 extraBody 透传；`GenerateOptions` 另有 `thinkingBudget` 与 `reasoningEffort` | **按模型能力默认开启**并参数化。路由侧已有 `FEATURE_REASONING_REQUIRED` 与 `ModelSelectionRequirement.reasoning()`，思考型模型开思考并配 `thinkingBudget`，非思考型不强开 |
| 思考内容外发 | 是否把 CoT 投影到 AG-UI，影响**安全与合规** | 当前无 `REASONING_*` 输出 | **默认不外发原始 CoT**；在 `AgentScopeEventMapper` 层拦截 `THINKING_BLOCK_*`；可公开摘要由租户级开关控制 |
| 推理块回放 | 把上一轮的加密推理块回放给模型，影响**多轮推理连续性** | `AgentMessage.Role.REASONING` 已定义（禁调用方构造、禁压缩器裁剪），但 `AgentScopeMessageMapper` 直接抛异常"推理块回放尚未接入 AgentScope" | **单独立项**，见下方工作包 HLP-R1 |

模型侧默认关闭的现有理由是**技术性的、不是安全性的**：`AiModel` 注释写明"思考型模型流式时会把推理放进 `reasoning_content`、`content` 为空，关闭后正文回到 content 通道正常流式"。因此开启思考的前置条件是先把两个通道分开消费，否则开了思考正文就断流。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/model/AiModel.java:90-96`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/ai/chat/DynamicChatClientFactory.java:128-132`

`PromptEnvelopeCaptureMiddleware` 已记录 `thinkingBudget` / `reasoningEffort`，思考参数的审计链无需新建。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/middleware/PromptEnvelopeCaptureMiddleware.java:174-175`

| 工作包 | 改动 | 验收判据 | 证据回链 |
|---|---|---|---|
| HLP-T1 | 分离 `reasoning_content` 与 `content` 两个流式通道；`AiModel.enableThinking` 由 per-model 能力决定而非全局默认 false。 | 思考型模型开启思考后正文仍正常流式；非思考型模型不被强开。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/model/AiModel.java:90-96` |
| HLP-T2 | `thinkingBudget` / `reasoningEffort` 纳入 `AiModel` 配置并透传到 `GenerateOptions`。 | 参数进入 PromptEnvelope 审计；未配置时不下发该字段。 | `tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/model/GenerateOptions.java:689-713` |
| HLP-T3 | `THINKING_BLOCK_*` 在 mapper 层显式拦截，附拦截原因注释。 | 公共事件流中不存在思考内容；新增 converter 也无法绕过。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeEventMapper.java:51-134` |
| HLP-R1 | 推理块回放接入：`AgentMessage.Role.REASONING` → AgentScope `ThinkingBlock`，替换当前直接抛异常的分支。 | o1/DeepSeek-R1 类模型多轮不丢推理块；回放内容不进入压缩与外发路径。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeMessageMapper.java:29-30`；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/message/ThinkingBlock.java` |

> HLP-R1 是本轮新识别的功能缺口：开启思考模式后，这条 fail-closed 分支会立刻从"未接入"变成"实际阻断多轮推理"。不能继续无声保留。

### EXECUTOR 先规划再执行

COORDINATOR 的 `CoordinationPlan` 回答“由哪些 executor 以什么聚合合同完成根目标”；新增 `ExecutorPlan` 回答“某一个已分配 executor 在不扩权、不新增兄弟节点的前提下，按哪些可验证步骤完成自己的 child task”。两者是父计划与局部执行计划，不是两个竞争的 TaskBoard。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/model/CoordinationPlan.java:12-257`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/model/TaskBoard.java:169-282`

L3 持有计划聚合和持久状态，L2 Agent 只在当前 execution 中读取不可变 plan revision 并执行；这避免违反“L2 无长期状态”。EXECUTOR 不得通过局部计划新增 Agent、扩大工具、改变 Role/Skill、提升预算或改写 coordinator 聚合合同；需要重新分解时只能发 `EXECUTOR_REPLAN_REQUESTED` 给 coordinator。`docs/design/framework/intelligent/architecture.md`

#### 数据模型与迁移判定

**需要新表和 Flyway 迁移。** 不能只把计划塞进 `TaskBoardEntity.board_payload`：executor plan 需要独立版本、审批、乐观锁、步骤状态和审计查询；整板 JSONB 写回会放大锁竞争，也无法用数据库约束保证单个 executor 的活动计划唯一。当前 board 只有单一 payload，官方 PLAN.md 又没有版本/审批/步骤状态，二者都不满足。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/assistant/persistence/TaskBoardEntity.java:37-38`；`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/workspace/plan/PlanModeManager.java:69-114`

两表按当前阶段约束**直接追加进已有迁移文件** `apps/service/aaf-api/src/main/resources/db/migration/v16__intelligent_runtime_schema.sql`（`ai_hitl_approval`、`ai_tool_approval`、`ai_hitl_recovery`、`ai_delegated_task`、`ai_task_board` 均在此文件），不分配新 `vN`、不写回滚脚本——本阶段依赖重建库。该做法是已授权的阶段性偏离，见[当前开发阶段约束](#当前开发阶段约束)；首个需要保留数据的环境出现时必须改回增量迁移。表名与列名沿用该文件已有的 `ai_` 前缀与命名风格。

**审批不新建表**：计划审批复用 `ai_hitl_approval` + `ai_hitl_recovery` 现有链路，`ai_tool_approval` 的合并或删除是审计已登记的独立事项，不并入本计划。`apps/service/aaf-api/src/main/resources/db/migration/v16__intelligent_runtime_schema.sql`

| 表/聚合 | 关键字段 | 约束与语义 |
|---|---|---|
| `ai_executor_plan` | `id`, `tenant_id`, `task_id`, `board_id`, `delegated_task_id`, `executor_agent_id`, `revision`, `status`, `goal`, `policy_snapshot JSONB`, `risks JSONB`, `verification JSONB`, `submitted_by_type/id`, `reviewed_by_type/id`, `review_comment`, `approved_at`, `started_at`, `finished_at`, `lock_version`, timestamps | `UNIQUE(tenant_id, delegated_task_id, revision)`；正文在 `SUBMITTED` 后不可变；修订创建新 revision，不 update 旧正文；Actor 使用 type+id。Actor 约束来源：`docs/reference/dev/apps/service/domain-modeling-standard.md`。 |
| `ai_executor_plan_step` | `id`, `plan_id`, `step_key`, `ordinal`, `title`, `instruction`, `dependencies JSONB`, `required_tools JSONB`, `completion_criteria JSONB`, `status`, `result_ref`, `failure_code`, `started_at`, `finished_at`, `lock_version` | `UNIQUE(plan_id, step_key)` 与 `UNIQUE(plan_id, ordinal)`；依赖只能引用同 plan；required tools 必须是冻结工具集子集；结果只存引用/摘要，不复制正文事实。 |

不新增 TaskBoard active-plan 外键：`delegated_task_id + latest approved revision` 可确定当前计划，TaskBoard 只保存 `activeExecutorPlanId/revision` 的快照引用并通过同一 transition 事务更新。这样计划表是计划真理源，board 是调度快照，不形成双写同一正文。TaskBoard 当前已有事务性 transition/outbox 模式可复用。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/assistant/persistence/JpaTaskTransitionAdapter.java:100-126,708-734`

#### 状态机

```text
DRAFT
  → PLANNING
  → SUBMITTED
  → REVIEW_REQUIRED ──→ REJECTED ──→ 新 revision 的 DRAFT
  └──────────────────→ APPROVED
APPROVED → EXECUTING → COMPLETED
                     ├→ FAILED
                     └→ CANCELLED
PLANNING/SUBMITTED/REVIEW_REQUIRED/APPROVED → CANCELLED
```

- `DRAFT/PLANNING` 只允许只读业务工具与 `submit_executor_plan`；acting gate 对写工具生成标准 DENIED tool result，不能只靠 prompt。官方 Plan Mode 的确定性拒绝与事件回写可直接借鉴。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/PlanModeMiddleware.java:168-233`
- `SUBMITTED` 后内容不可变；是否自动批准由**确定性白名单**判定，不由模型的风险自评判定——白名单按任务类型 + 冻结工具集合声明"该组合下的计划可系统自动 `SUBMITTED → APPROVED`"，命中即自动过，未命中一律 `REVIEW_REQUIRED`。计划正文里模型给出的 `risks` 只作展示与审计字段，**不作门控输入**；让模型判断自己是否需要审批是循环授权。任何人工审批都复用 AAF 持久 HITL，不调用官方 `plan_exit`。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/PersistentHitlCoordinator.java:41-339`
- `APPROVED → EXECUTING` 必须同时冻结 execution profile 中的 planId/revision/hash；恢复只能复用同 revision。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/model/ExecutionProfileSnapshot.java:24-99`
- 步骤状态只允许 `PENDING → RUNNING → COMPLETED|FAILED|CANCELLED`；同 plan 最多一个 RUNNING，依赖未完成不得启动。该不变量对应官方 todo “最多一个 in_progress”，但持久实现落在 AAF DB。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/builtin/TodoTools.java:92-173`
- executor 不得自行从 `REVIEW_REQUIRED` 进入执行；所有转换使用 `lock_version` CAS，并在同一事务追加领域事件/outbox。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/assistant/persistence/JpaTaskTransitionAdapter.java:100-126`

#### 应用接口

```java
public interface ExecutorPlanPort {
    ExecutorPlanDraft beginPlanning(ExecutorPlanningCommand command);
    ExecutorPlan submit(SubmitExecutorPlanCommand command);
    ExecutorPlan review(ReviewExecutorPlanCommand command);
    ClaimedPlan claimApproved(ClaimExecutorPlanCommand command);
    ExecutorPlanStep startStep(StartExecutorPlanStepCommand command);
    ExecutorPlanStep completeStep(CompleteExecutorPlanStepCommand command);
    ExecutorPlan fail(FailExecutorPlanCommand command);
    ExecutorPlan cancel(CancelExecutorPlanCommand command);
    Optional<ExecutorPlan> findActive(TenantId tenantId, DelegatedTaskId taskId);
}
```

`DelegatedTaskCoordinator.executeSubTask` 在 EXECUTOR 分支改为 `requiresPlan → ensureApprovedPlan → executeApprovedPlan`：`requiresPlan` 由 policy 显式标记决定（未标记的任务走原有直接执行路径，不受本能力影响）；无计划则先跑 planning execution 生成结构化 draft；有 `REVIEW_REQUIRED` 则暂停；有 APPROVED/EXECUTING 则从首个未完成步骤恢复。原来的 `commands.execute(childCommand)` 只能出现在已 claim 的步骤执行内。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/DelegatedTaskCoordinator.java:454-478`

#### 计划由谁产出

**由执行 Agent 自己产出，作为同一 Agent 定义下的独立 planning execution。** 不新建专用 planner Agent 定义，也不默认用非自主 L0。三条理由：

- 计划步骤必须与该 executor 真实可用的工具白名单、Role 与激活 Skill 一致。换专用 Agent 出计划要复制一份画像，属并行抽象；两边一旦漂移就产出"计划要调的工具执行时不存在"的不可执行计划，且只在运行时暴露。
- 冻结画像是 per-execution 不变量。planning 与 execution 分成两次 execution，`planId/revision/hash` 才能作为冻结内容进入执行那次的 `ExecutionProfileSnapshot`；若在同一次 execution 内审批后继续，画像会在运行中变化，破坏该不变量。因此**不使用 `ToolSuspendException` 挂起同一次 execution 来做计划审批**，尽管该链路已具备能力。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/model/ExecutionProfileSnapshot.java:24-99`
- planning execution 由 acting gate 限制为只读业务工具 + `submit_executor_plan`，这正是可直接借鉴官方 `PlanModeMiddleware` 确定性拒绝的地方，不靠 prompt 约束。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/PlanModeMiddleware.java:168-233`

非自主 L0 单次调用保留为 policy 可选的轻量路径（工具集很小、明确不需要只读探索时使用），不作默认；L0 无法探索、上下文全靠显式注入，容易产出与实际能力不符的步骤。两条路径由确定性策略选择，**不允许 L0 失败时静默 fallback 到自主 Agent**。

planning 输入必须是结构化 record，不是拼接的自由文本，否则计划质量不可复现、不可回归测试。最小字段集：

```java
public record ExecutorPlanningContext(
        DelegatedTaskId delegatedTaskId,
        String goal,
        List<String> completionCriteria,
        String aggregationContractExcerpt,
        List<FrozenToolDescriptor> availableTools,
        String roleSummary,
        List<String> activatedSkillSummaries,
        List<ArtifactRef> upstreamArtifacts,
        PlanningBudget budget,
        List<String> prohibitions) {}

public record FrozenToolDescriptor(String name, String purpose) {}
```

`FrozenToolDescriptor` 只带名称与用途摘要，**不含凭据、baseUrl、header 或参数默认值**；工具集必须与该 execution 的冻结白名单同源，不允许 planning 阶段单独组装。

#### 领域事件与 AG-UI

| 事件 | 最小载荷 | 投影 |
|---|---|---|
| `EXECUTOR_PLAN_CREATED` | planId, delegatedTaskId, revision, goalHash | ActivitySnapshot/Custom |
| `EXECUTOR_PLAN_SUBMITTED` | planId, revision, risk, stepCount, verificationCount | StepFinished(planning) + ActivityDelta |
| `EXECUTOR_PLAN_REVIEW_REQUIRED` | interruptId, planId, revision, responseSchema, expiresAt | `RunFinishedInterruptOutcome` + `aaf.plan.review_required` |
| `EXECUTOR_PLAN_APPROVED` / `REJECTED` | reviewer Actor、decision、commentRef | Custom + ActivityDelta |
| `EXECUTOR_PLAN_EXECUTION_STARTED` | planId, revision, executionId | StepStarted(execution) |
| `EXECUTOR_PLAN_STEP_STARTED/COMPLETED/FAILED` | planId, stepKey, ordinal, resultRef/failureCode | Step/Activity；完整身份留 Activity/Custom |
| `EXECUTOR_PLAN_COMPLETED/FAILED/CANCELLED` | planId, terminal reason, evidence refs | StepFinished + ActivityDelta |
| `EXECUTOR_REPLAN_REQUESTED` | planId, reason, requestedScope | Custom；交回 COORDINATOR，不自动扩权 |

上述事件先进入 AAF task transition/outbox，再由 AG-UI converter 投影；禁止 planner 直接向 SSE 发“计划已批准”。现有任务 transition 已能在同一事务保存事件与 outbox。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/assistant/persistence/JpaTaskTransitionAdapter.java:708-734`

#### EXECUTOR planning 工作包

| 工作包 | 改动 | 验收判据 | 证据回链 |
|---|---|---|---|
| HLP-P1 | 新增 plan/step 聚合、状态机、端口与两表迁移。 | DB 约束阻止重复 revision/step；所有转换 CAS；迁移可回滚。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/assistant/persistence/TaskBoardEntity.java:37-38` |
| HLP-P2 | 在 EXECUTOR 分支插入 planning/review/execute/recover，不改 COORDINATOR DAG 语义。 | executor 未 APPROVED 前无写工具调用；已有步骤可恢复；不新增兄弟节点。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/DelegatedTaskCoordinator.java:454-602` |
| HLP-P3 | 实现 plan acting gate 和 `submit_executor_plan` 受控工具。 | 写工具被确定性拒绝且上下文/事件一致；shell 始终不可见。 | `tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/PlanModeMiddleware.java:168-242` |
| HLP-P4 | 复用 Persistent HITL 做 review，事件经 transition/outbox。 | 重启、接管、重复回调下审批恰好一次；AgentState 丢失不丢计划。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/PersistentHitlCoordinator.java:41-339` |
| HLP-P5 | AG-UI 投影 plan/step/activity/interrupt。 | UI 可从 snapshot 重建计划进度；Custom 不含计划正文/内部 Prompt。 | `tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/event/AguiEvent.java:1179-1258,1438-1695` |

## 主线：核心流程注释补充

注释与对应代码同批提交，不单独制造“全仓补注释”重构。统一使用中文，解释 **why、所有权、状态机和不变量**；禁止翻译方法名、复述 if/for、给 getter/record 写废话。注释中的事件数量、默认值和官方行为必须引用测试或准确版本，不能继续写“RC4”“28 events”等过期常量。`apps/service/aaf-framework/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java:35-41`；`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/AgentEventType.java:40-90`

| 类/位置 | 必须补充的 why 与不变量 | 不应写的注释 | 证据回链 |
|---|---|---|---|
| `AgentScopeSpecCompiler` | 为什么使用 core ReAct；最终工具面何时冻结；两个缓存 key 与淘汰 close；同实例并发契约和升级测试。 | “编译 Agent”“调用 builder”。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:30-60,261-309` |
| `HarnessAgentExecutionAdapter`（建议重命名 `AgentScopeExecutionAdapter`） | AAF Harness 与官方 Harness 的区别；唯一终态 CAS；三类 timeout；interrupt 与 close 顺序；为什么事件先持久化再投影。 | 逐行解释 Reactor 操作符。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java:377-466,680-717` |
| `AgentScopeEventMapper` | 31 项的映射/安全忽略策略；CoT、RAW、敏感 tool args 为什么不透传；replyId/blockId 配对。 | 无说明的 `default -> empty`。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeEventMapper.java:51-134` |
| `AgentScopeRuntimeContextMapper` | state key 为什么包含 tenant/user/task/agent/execution；AgentState 是热状态而非事实；结束删除。 | 只列 key 拼接格式。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeRuntimeContextMapper.java:24-74` |
| `PromptEnvelopeCaptureMiddleware` | 记录 hash/长度而不落正文的安全原因；attempt 判定；排除 apiKey/baseUrl/header。 | “记录日志”。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/middleware/PromptEnvelopeCaptureMiddleware.java:42-192` |
| `PortBackedAgentTool` / `RegistryToolPortAdapter` | 模型永远不能绕过 ToolGateway；suspend、授权证据、凭据参数和 connector 边界。 | 重复 `ToolBase` API 文档。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/PortBackedAgentTool.java:42-87`；`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/RegistryToolPortAdapter.java:48-203` |
| `SpringRedisClientAdapter` | Spring 拥有连接生命周期；为什么 close no-op；Lua/SCAN 的原子与阻塞约束。 | “Redis 适配器”。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/state/SpringRedisClientAdapter.java:25-89` |
| `PromptInvocationGateway` / `AgentScopeNonAutonomousModelInvoker` | L0 不创建 Agent/Task；Function Contract、模型选择与 structured output 的责任边界；禁止 provider fallback。 | “调用大模型”。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/prompt/PromptInvocationGateway.java:20-161` |
| `DelegatedTaskCoordinator.executeSubTask` | CoordinatorPlan 与 ExecutorPlan 的父子边界；APPROVED 前禁止执行；replan 必须交回 coordinator。 | “先规划再执行”。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/DelegatedTaskCoordinator.java:454-602` |
| `ExecutorPlan` / `ExecutorPlanStep` | 状态机、内容不可变 revision、单 RUNNING、工具子集、CAS；L3 持久而 L2 只读快照。 | 字段逐个复述。 | `docs/design/framework/intelligent/architecture.md` |
| `AgUiProjector` / registry/context | 为什么只消费安全公共事件；start/end 配对、finish 一次、interrupt/resume 的持久真理源；Reasoning 默认关闭。 | 枚举 28 个事件的重复 Javadoc。 | `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AgUiProjector.java` |
| agentscope `package-info.java` 与 POM 注释 | AAF Harness 边界、core 依赖、组件链路；删除 HarnessAgent 推荐入口的过时叙事。 | 大段复制 ADR。 | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/package-info.java:11-41`；`apps/service/aaf-framework/pom.xml:140-144` |

## 分阶段路线

所有阶段都超过 5 个文件或涉及接口/安全/数据，因此整体按 🔴 高风险执行；每阶段开始前均需独立 design/task 审核，不以本计划替代具体任务的变更审查。估算仅用于拆批，不是承诺；测试文件计入文件量。风险判定依据：`docs/reference/team/collaboration-standard.md` 的“Agent 派发触发条件”和数据库/安全强制升级规则。

| 阶段 | 范围 | 准入 | 准出 | 影响量级（估算） | 人类审核 |
|---|---|---|---|---:|---|
| 基线冻结 | 拍板本计划决策点；为 core、AG-UI、L0、plan、thinking 分配独立 AAF 任务；冻结 AgentScope 2.0.2。 | ADR-005 与本计划可用。 | 任务设计、AC、既有测试改动清单就绪；无代码改动。 | 4–6 份任务文档 | 🔴 审边界、ADR 勘误、DB 方案 |
| Core 收缩与运行时加固 | HLP-C1～C5；类型切换、工具泄漏断言、依赖降级（含删两个僵尸扩展）、RQ-11～13、必要注释。 | 无并行大功能分支修改 compiler/execution/state。 | 无 harness import/直接依赖；工具负向断言、并发、cancel/close、Redis 行为在既有测试中锁定；`compile` 通过。 | 18–28 文件、约 600–1000 行 | 🔴 审依赖、安全工具面、Redis 原子语义 |
| 事件骨干与 AG-UI | HLP-A1～A4；31 项策略、AAF converter、state/activity/interrupt/resume。 | Core 阶段合入；公共事件 schema 已评审。 | 配对不变量与 schema 形状在 `AgUiProjectorTest` 中锁定；不产生 RAW/CoT；`compile` 通过。 | 22–34 文件、约 1200–2000 行 | 🔴 审协议、安全披露、持久 HITL |
| 非自主 L0 单栈 | HLP-L1～L4；原子替换 `LlmClient`，迁移所有智能层调用；签名保持同步。 | Core Model 解析稳定；Function Contract 用例齐全。 | 智能层无 Spring AI L0 Bean/直接调用；usage/structured output/失败合同锁定；无 shim；`compile` 通过。 | 16–24 文件、约 700–1200 行 | 🔴 审接口删除、模型路由与成本语义 |
| 思考模式与推理块 | HLP-T1～T3、HLP-R1；通道分离、参数化、mapper 拦截、推理块回放。 | L0 单栈已合入（共用 `GenerateOptions` 映射）。 | 思考型模型开启后正文仍正常流式；公共事件无思考内容；多轮推理块不丢；`compile` 通过。 | 10–16 文件、约 400–700 行 | 🔴 审披露边界与回放内容处置 |
| EXECUTOR 持久计划 | HLP-P1～P5；新聚合/表、planning execution、acting gate、审批、恢复、AG-UI。 | AG-UI interrupt 可用；planning execution 可用。 | 未批准零副作用；计划/步骤可恢复；重复审批/接管幂等；policy 未标记的任务行为不变；`compile` 通过。 | 30–45 文件、约 2200–3500 行，2 个新表（追加进 v16） | 🔴 必审 DB schema、状态机、权限与计划披露 |
| 门禁恢复与集成收口 | 一次性跑通全量门禁；更新受影响设计/规范；补齐本阶段欠下的测试文件；删除过时命名/注释。 | 前六阶段全部合入。 | **`pnpm check` + `pnpm acceptance` 全绿**；欠下的测试文件补齐；ADR 勘误与 runtime/architecture/usage guide 同步完成；质量门控通过。 | 8–15 文件 + 补测（另任务） | 🔴 审真理源一致性与发布结论 |

各阶段准出**按当前阶段约束不含 `check` / `acceptance` 门禁，但一律含 `pnpm nx compile service` 通过**——这是最低硬要求，编译不过即该阶段未完成，不得进入下一阶段。完整门禁集中在最后一个阶段一次性跑通，这是已授权偏离，也是本计划最大的技术债：错误会累积到最后集中爆发。除编译门槛外，缓解措施还包括把每阶段的准出判据都写成可被单测断言的形式，便于门禁恢复时快速定位。

测试分层规则本身不变（`*Test.java` → Surefire → developer；`*IT.java` / `*AcceptanceTest.java` → Failsafe → tester；不得把验收塞进单测，不得用 `@Disabled` 绕竞态），但本阶段**只在既有测试文件内补断言、不新增测试文件**，欠下的测试在门禁恢复阶段补齐。可改的既有文件清单见[当前开发阶段约束](#当前开发阶段约束)。`docs/reference/team/process-standard.md:212-240`；`.kiro/steering/collaboration.md:77-84`

## 与 RQ-01～RQ-13 的对应关系

RQ-01～RQ-10 已在 2026-08-31 基线修复，本计划不重复实现，但 core 切换必须保留其回归测试；RQ-11～RQ-13 纳入 Core 收缩阶段正式关闭。`docs/design/audit/2026-08-30-agentscope-boundary/02-runtime-quality.md`

| RQ | 当前状态 | 本计划阶段 | 关闭/保持判据 |
|---|---|---|---|
| RQ-01 唯一终态 | 已关闭 | Core 回归 | `ACTIVE/CANCELLING/TERMINATED` CAS 测试在 ReAct 类型切换后仍通过，不出现双终态。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java:680-717` |
| RQ-02 阻塞 I/O 线程 | 已关闭 | Core 回归 | 边界事件才在 blocking scheduler 校验；文本 delta 不查 DB/Redis。`docs/design/audit/2026-08-30-agentscope-boundary/02-runtime-quality.md` |
| RQ-03 timeout 混用 | 已关闭 | Core 回归 | total/idle/persist 三条时限在 core 流上语义不变。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java:403-460,475-491` |
| RQ-04 失败合同 | 已关闭 | Core + AG-UI 回归 | `failureCategory/retryable/failureId` 保留，AG-UI 只暴露稳定 code，不泄露异常。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeFailureClassifier.java:25-84` |
| RQ-05 重放幂等 | 已关闭，保留中途崩溃前缀重复残留 | Core + plan 恢复回归 | 已有终态不再调用模型；plan step 用 receipt/CAS 避免重复副作用。残留前缀重复不通过双写修复。`docs/design/audit/2026-08-30-agentscope-boundary/02-runtime-quality.md` |
| RQ-06 无界缓存 | 已关闭 | Core 回归 | 两缓存容量可配置，淘汰 close；指标仍可观测。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:261-304` |
| RQ-07 cache key 缺策略 | 已关闭 | Core 回归 | `ExecutionPolicy` 继续进入 DIRECT key；不同策略不复用。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:84-105` |
| RQ-08 hidden history TOCTOU | 已关闭 | Core 回归 | execution 私有 state slot；结束删除；core 每 call reload 不引入跨 execution 历史。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeRuntimeContextMapper.java:24-74` |
| RQ-09 Agent/execution 隔离 | 已关闭 | Core 回归 | state key 保留 agentIdentifier 与 executionId，L0 不使用 AgentState。`docs/design/audit/2026-08-30-agentscope-boundary/02-runtime-quality.md` |
| RQ-10 工具证据泄漏 | 已关闭 | Core 回归 | finally clear + TTL + 容量继续有效；工具 args 补齐不把证据 map 当协议缓冲。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/ToolResultEvidenceStore.java:34-164` |
| RQ-11 重复订阅裸错误 | 未关闭 | Core 收缩 | 失败方产生稳定 `RUN_FAILED` 或可识别拒重事件并释放资源；只调用一次模型。`docs/design/audit/2026-08-30-agentscope-boundary/02-runtime-quality.md` |
| RQ-12 interrupt/close 竞态 | 未关闭 | Core 收缩 | close 后不得再 interrupt；或通过同一 lifecycle CAS 证明有序；竞态测试不用 sleep。`docs/design/audit/2026-08-30-agentscope-boundary/02-runtime-quality.md` |
| RQ-13 Redis 非原子与 KEYS | 未关闭 | Core 收缩 | Lua/事务原子维护 value+registry；生产扫描用 SCAN；故障注入可恢复。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/state/SpringRedisClientAdapter.java:24-84` |

## 明确不做的事

- 不引入或保留官方 `HarnessAgent` 作为 fallback、实验开关或 optional 双路径；core 迁移是直接替换。官方 Harness 无法彻底关闭 workspace/bus/wait tool。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:2116-2371`
- 不引入 workspace、filesystem、shell、sandbox、tools.json、官方 subagent、Markdown memory/plan/session/task；需要的能力走 AAF Tool、Cognition、TaskBoard 与 DB。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/workspace/WorkspaceManager.java:84-163`
- 不用官方 Plan Mode 直接控制 TaskBoard，不把 `plans/PLAN.md`、`AgentState.planModeContext` 或 `tasksContext` 当计划事实。`tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/workspace/plan/PlanModeManager.java:39-114`
- 不采用 AG-UI starter/controller/内存 resume coordinator接管主入口；只复用事件 DTO、encoder 和 wire contract。`apps/service/aaf-api/pom.xml:20-25`
- 不为了“28/28”发 RAW、deprecated THINKING alias、内部 CoT 或冗余 Chunk；协议完整性以业务语义与配对不变量衡量。`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/event/AguiEventType.java:21-130`
- 不用 `agentscope-extensions-skill-postgresql-repository` 替换 AAF Skill 主仓；该实现无版本、审核、发布、回滚语义。本计划进一步**删除该坐标**（当前零使用）。`tmp/agentscope-java/agentscope-extensions/agentscope-extensions-skills/agentscope-extensions-skill-postgresql-repository/src/main/java/io/agentscope/core/skill/repository/postgresql/PostgresSkillRepository.java:655-729`
- 不把 `PromptInvocationGateway` 或其五个调用者响应式化；ADR-003 已定全量同步 + 虚拟线程，仅 SSE 保留 Flux。`docs/design/adr/ADR-003-virtual-threads-over-webflux.md`
- 不删除 `JsonSchemaUtils` shadow；上游 2.0.x 仍是 Jackson 2 + victools 4，删除必然 `NoSuchMethodError`。退出路径（独立坐标 fork 或上游 PR）不在本计划范围。`tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/util/JsonSchemaUtils.java:17-30`
- 不让所有 EXECUTOR 强制先规划；只对 policy 显式标记的任务生效，未标记任务行为完全不变。
- 不让模型的风险自评充当审批门控；自动批准只由确定性白名单驱动。
- 不把 AgentState 从 Redis 迁到 PostgreSQL，也不 Redis+PG 双写；计划、审批与任务事实进入 AAF 自有表。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/spring/AgentScopeInfrastructureAutoConfiguration.java:57-87`
- 不在 L0 迁移中顺手删除 `ResilientChatService` 或重构其四个非 L0 用户；这属于独立边界。`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/ai/chat/ResilientChatService.java:36-388`
- 不在本计划内升级 AgentScope 版本、引入兼容层或进行无关包移动；先锁 2.0.2 行为测试。`apps/service/aaf-dependencies/pom.xml:54-55`

## 决策记录去向

六条决策已于 2026-09-01 拍板（见[已批决策与阶段约束](#已批决策与阶段约束)），本节只记录各条应落到哪个真理源。

| 决策点 | 结论 | 记录去向 | 状态 |
|---|---|---|---|
| ADR-005 starter 论据失真 | 保留"不采用 starter"，理由改为 TaskBoard 执行粒度、持久 HITL 非持久化、线程模型与 ADR-003 冲突 | ADR-005 追加勘误节 | 待协调者写入 |
| ADR-005 RQ 状态过期 | RQ-01 + RQ-02～10 已修复，仅剩 RQ-11/12/13 minor；后续动作第 1、2 项已完成 | 同上勘误节 | 待协调者写入 |
| framework 直接依赖降为 core | 直接依赖 `agentscope-core`，删除 harness 与两个零使用扩展 | 同上勘误节（作为后续动作第 4 项的结论） | 待协调者写入 |
| EXECUTOR plan 两表与审批策略 | 两表追加进 v16、不可变 revision、确定性白名单自动批准、审批复用 `ai_hitl_approval`、只对 policy 标记任务生效 | 新 ADR（涉及 schema + 权限 + 状态机） | 待立 ADR |
| L0 单栈与 fallback 语义 | 同步签名（依 ADR-003）；`CapabilityRouter` 调用前定唯一模型；provider 层不静默 fallback；`ResilientChatService` 退出 L0 | 新 ADR 或 core model-router 设计增补——因为它改变了 L0 的可用性与成本语义 | 待立 ADR |
| 思考模式三层边界 | 模型侧按能力默认开启并参数化；原始 CoT 默认不外发；推理块回放单独立项 | 不新开 ADR：披露白名单写入 `docs/design/framework/intelligent/runtime-event.md`；模型侧参数写入 `docs/design/framework/intelligent/core/model-router.md` | 待协调者写入 |
| `JsonSchemaUtils` shadow | 保留，修正注释，登记为禁兼容层显式例外 | 例外登记 + 改进意见池（退出路径为独立坐标 fork 或上游 PR） | 待登记 |

## 参考源码与文档索引

| 主题 | 官方源码路径 | 官方文档 URL | AAF 对应文件 |
|---|---|---|---|
| ReAct builder 与生命周期 | `tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4128-4953` | <https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html> | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:32-310` |
| Model 直调 | `tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/model/Model.java:22-85` | <https://java.agentscope.io/v2/zh/docs/building-blocks/model.html> | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/model/AgentScopeModelResolver.java:20-84` |
| Middleware | `tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java:60-160` | <https://java.agentscope.io/v2/zh/docs/building-blocks/middleware.html> | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/middleware/PromptEnvelopeCaptureMiddleware.java:42-192` |
| 31 类 AgentEvent | `tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/event/AgentEventType.java:40-90` | <https://java.agentscope.io/v2/zh/docs/building-blocks/agent.html> | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeEventMapper.java:41-503` |
| Toolkit 与工具组 | `tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/Toolkit.java:146-1042` | <https://java.agentscope.io/v2/zh/docs/building-blocks/tool.html> | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/tool/AgentScopeToolkitFactory.java:16-49` |
| Harness 默认泄漏 | `tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/HarnessAgent.java:2096-2621` | <https://java.agentscope.io/v2/zh/docs/harness/architecture.html> | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/compiler/AgentScopeSpecCompiler.java:148-213` |
| 官方 Plan Mode | `tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/middleware/PlanModeMiddleware.java:144-242` | <https://java.agentscope.io/v2/zh/docs/harness/plan-mode.html> | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/DelegatedTaskCoordinator.java:454-602` |
| 官方 Subagent | `tmp/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/tool/AgentSpawnTool.java:287-1285` | <https://java.agentscope.io/v2/zh/docs/harness/subagent.html> | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/model/TaskBoard.java:74-282` |
| AG-UI 事件模型 | `tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/event/AguiEvent.java:43-1747` | <https://java.agentscope.io/v2/zh/docs/integration/ag-ui.html> | `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/agui/AgUiProjector.java` |
| AG-UI converter/context | `tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/adapter/strategy/AgentEventConverterRegistry.java:32-138` | <https://java.agentscope.io/v2/zh/docs/integration/ag-ui.html> | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/shared/event/publication/ExecutionEventPublicMapper.java:16-206` |
| AG-UI resume | `tmp/agentscope-java/agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/processor/AguiResumeCoordinator.java:39-228` | <https://java.agentscope.io/v2/zh/docs/integration/ag-ui.html> | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/assistant/application/PersistentHitlCoordinator.java:41-339` |
| AgentState store | `tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/state/AgentStateStore.java:34-166` | <https://java.agentscope.io/v2/zh/docs/building-blocks/state.html> | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/state/SpringRedisClientAdapter.java:16-90` |
| 思考 / 推理参数 | `tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/model/GenerateOptions.java:689-713` | <https://java.agentscope.io/v2/zh/docs/building-blocks/model.html> | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/core/model/AiModel.java:90-96` |
| 推理块回放 | `tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/message/ThinkingBlock.java` | <https://java.agentscope.io/v2/zh/docs/building-blocks/message-and-event.html> | `apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/mapping/AgentScopeMessageMapper.java:29-30` |
| 并发模型约束 | — | — | `docs/design/adr/ADR-003-virtual-threads-over-webflux.md` |
| 智能层表结构 | — | — | `apps/service/aaf-api/src/main/resources/db/migration/v16__intelligent_runtime_schema.sql` |
| AAF 运行时质量 | — | — | `docs/design/audit/2026-08-30-agentscope-boundary/02-runtime-quality.md` |
| AAF 边界决策 | — | — | `docs/design/adr/ADR-005-agentscope-boundary-and-orchestration.md` |
| AAF 动态运行合同 | — | — | `docs/design/framework/intelligent/runtime.md` |
| AAF 静态分层 | — | — | `docs/design/framework/intelligent/architecture.md` |

## 完成定义

本计划全部落地的完成条件是：framework 不再直接依赖或引用官方 harness，两个零使用扩展已删除；自主与非自主调用分别只有 `ReActAgent` 和 `Model` 两个 core 入口，且非自主路径保持同步签名；最终工具面无隐式工具；31 种 AgentEvent 有显式策略；AG-UI 的文本、工具、state、step、activity、interrupt/resume 可配对重放且不外发原始 CoT；思考模式按模型能力开启且正文不断流，推理块回放已接入；policy 标记的 EXECUTOR 未批准前零副作用且计划可跨重启恢复，未标记任务行为不变；RQ-01～RQ-13 全部关闭或有已批准残留边界；`JsonSchemaUtils` shadow 注释已修正并登记为例外；核心注释解释 why 与不变量；**门禁恢复阶段 `pnpm check` 与 `pnpm acceptance` 一次性全绿，本阶段欠下的测试文件已补齐**。证据基线分别来自 `tmp/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:4128-4953`、`apps/service/aaf-framework/src/main/java/com/xuejiai/aaf/framework/intelligent/infrastructure/agentscope/execution/HarnessAgentExecutionAdapter.java:61-720` 和 `docs/design/audit/2026-08-30-agentscope-boundary/02-runtime-quality.md`。
