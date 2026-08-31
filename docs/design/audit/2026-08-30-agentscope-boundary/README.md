---
level: Reality
layer: Model
purpose: AgentScope 复用边界专项评估的索引与结论摘要
status: draft
version: 1.1.0
date: 2026-08-31
author: AaronZZH
scope:
  includes:
    - AAF 对 agentscope-java v2 的实际复用/自研替代测绘
    - 自研 Harness 编排层的质量评估与能力差距
    - 三条演进路线对比与推荐
  excludes:
    - 五层智能架构本身的设计说明（见 docs/design/framework/intelligent/）
    - 具体修复任务的技术设计（待 ADR 通过后另立任务）
gains:
  - 一眼看清 AAF 到底复用了 AgentScope 的哪些能力、自研替代了哪些
  - 判断自研编排层是否可作为生产执行底座，以及卡在哪一条 blocker
  - 依据可观测判据决定"继续用 Harness / 只留 core / 换 Spring AI"
changelog:
  - 2026-08-30 初次评估，覆盖 8 个确认问题，产出 5 篇文档
  - 2026-08-31 补录官方 v2 文档入口、模块划分与单项对比议题顺序
  - 2026-08-31 追加后续修正：Plan Mode / Subagent 判定改判，重复清单增至 19 项
---

# AgentScope 复用边界专项评估

> 本次评估回答一个问题：**AAF 只借用 AgentScope 的 ReAct 内核、把 Harness 周边能力全部自研替代——这条边界划得对不对，接下来往哪走。**

AAF 把 HarnessAgent 当作纯 ReAct 内核，Harness 模块 215 个类中的绝大部分被自研实现替代。取舍已写在 `infrastructure/agentscope/package-info.java`：只借用推理循环与工具调用，内置工作区、记忆、技能、子智能体、文件与 Shell 工具"全部关闭"。

是自研 Assistant 层的产物，而 HarnessAgent 本身并不排斥它们；被替代的是 Harness 的技能运行时、子智能体编排与记忆治理，动因是**企业治理要求（多租户、审批、计量、持久化）**而非 Role/Skill 模型冲突。

## 文档索引

| 文档 | 回答的问题 | 核心产出 |
|------|-----------|---------|
| [01-reuse-map.md](01-reuse-map.md) | 复用了什么、自研替代了什么；五层智能架构完成度与真实调用链 | 59 项能力对照表（core 15 + Harness 15 + middleware 16 + tool 13）；五层实现现状；DIRECT / DELEGATED 两条逐跳调用链 |
| [02-runtime-quality.md](02-runtime-quality.md) | 自研编排层是否可行、稳定、可靠 | 13 条风险（RQ-01…RQ-13）+ 必查项逐项判断 + 缺失测试清单 |
| [03-capability-gap.md](03-capability-gap.md) | 相对官方 Harness 缺什么、等价什么、超越什么；超越部分是否值得自研 | 缺失/等价/超越三类清单 + 每个超越项的"刚需自研 / 可改造为扩展点 / 可削减"判定 |
| [04-reuse-correctness.md](04-reuse-correctness.md) | 是否正确使用 agentscope-core；哪里重复造轮子 | 扩展点使用正确性逐项判定 + 18 项重复造轮子清单 + `JsonSchemaUtils` 包名覆盖风险专章 |
| [05-evolution-options.md](05-evolution-options.md) | 往哪走 | 三条路线定义 + 六维对比矩阵 + 单一推荐 + 三阶段迁移路径 + 可观测切换判据 |

## 结论摘要

### 边界划在哪（01）

59 项能力对照：**复用 10 项 / 自研替代 47 项 / 未使用 2 项**。复用集中在 Model、Msg、Toolkit、ReAct 循环、AgentState、AgentEvent、Middleware 扩展面；Memory、Skill、Subagent、Plan、RAG、Gateway 与持久任务语义全部由 AAF 自研层接管。最硬的证据是 `AgentScopeSpecCompiler` 在构造 HarnessAgent 时的 15 个关闭调用。

两个需要注意的事实：

- **"全部关闭"在文档层比运行层强**。Harness builder 仍默认创建 `LocalFilesystem`、`WorkspaceManager`、`WorkspaceMessageBus`、`WorkspaceAsyncToolRegistry`，并装配 `InboxMiddleware`、注册 `WaitAsyncResultsTool`。`disableSessionPersistence()` 在 AgentScope 2.0 已是 no-op，不能当作关闭手段。
- **Team 层不是空壳**。`TeamDefinition`、`AssistantExecutionService.startTeam`、`DelegatedTaskCoordinator.executeBoard` 已形成静态 `LEADER_COORDINATED` MVP；缺的是动态成员、Pipeline/Peer 模式、外部 A2A、成员级预算，`ConflictArbitrator` 尚未接主链。

### 自研编排层能不能上生产（02）

**不能，卡在一条 blocker**。问题统计 blocker 1 / major 9 / minor 3。

RQ-01：取消与源流完成没有原子终态仲裁器。`HarnessAgentExecutionAdapter` 用五个 `AtomicBoolean` 表达状态机，只能保证单标志原子性，存在两个确定竞态窗口——要么持久事件出现"COMPLETED 与 CANCELED 并存"的矛盾终态，要么 `cancel()` 返回 `true` 却没有任何 `EXECUTION_CANCELED` 事件落库。

其余 major 集中在同一类根因：为治理需求新增的"执行注册 → 取消 → 事件落库 → 状态预检"协议不是单一状态机。典型几条：`doOnNext` 在事件发射线程同步做 Redis + JPA 校验（RQ-02）；`timeout` 实际是事件静默超时而非总墙钟时限，且不覆盖入库（RQ-03）；HarnessAgent 缓存无界且 `DirectKey` 缺执行策略（RQ-06/07）；隐藏历史校验存在 TOCTOU 绕过窗口（RQ-08）；状态键未含 Agent 身份（RQ-09）；工具证据在取消路径永久泄漏（RQ-10）。

一个反直觉的正向结论：缓存实例并发复用是**安全**的。`ReActAgent` 按 `(userId, sessionId)` 串行同槽、不同会话并行，已被上游测试 `ReActAgentPerSessionStateTest` 验证；类头"单实例不线程安全"的注释是陈旧文档。

### 自研值不值（03）

缺失 10 项 / 等价 5 项 / 超越 10 项。

10 项超越能力中，只有 **3 项是刚需自研**：tenant 级多租户隔离、Conversation lease/fencing、任务持久化与恢复 outbox。其余 **7 项可改造为官方扩展点**：token 计量与预扣、持久 HITL 审批、Prompt 预检与信封留痕、执行事件溯源、工具授权与 Connector 受信参数、Role/Skill 意图路由、置信度门控。

同时暴露三处质量问题：`GovernedSandboxAdapter` 用宿主 `ProcessBuilder` 而非真正隔离；`TokenQuotaService` 当前恒返回 0，配额未落地；`JpaPromptEnvelopeAdapter` 的 `findMaxSeq()+1` 并发安全性未在源码中体现（标注为未验证推断）。

### 用对了没有、造了多少重复轮子（04）

Toolkit/ToolBase、Model、RuntimeContext **用法正确**；Middleware 与 RedisAgentStateStore 接线正确但状态职责仍需收敛。两处明确问题：

- `AgentEvent` 31 个类型只映射 13 个，其余 18 个默认丢弃——前端可见信息因此缺失。
- `Msg`/`ContentBlock` 映射不完整。

重复造轮子 18 项：**应删除复用官方 3 项 / 应保留自研 7 项 / 应改造为官方扩展点 8 项**。经 2026-08-31 修正增至 19 项，扩展点一类增至 9 项（新增 `PermissionMode`）。

`JsonSchemaUtils` 专章结论：AAF 在自己源码树里以 `io.agentscope.core.util` 同包同名类覆盖 jar 内实现，绕过 agentscope-core RC4 的 Jackson 2.x 与 Spring Boot 4 的 Jackson 3.x 冲突。这是 classpath shadowing，既违反"禁兼容层"硬约束，又在升级后会静默行为漂移。推荐方案：改为独立坐标的最小 AgentScope fork 整体替换官方依赖并删除同名 shadow，同时向上游提 Jackson 3 / victools 5 兼容 PR，上游 GA 后删 fork。

### 往哪走（05）

**推荐路线三：保留 agentscope-harness，收窄 AAF 自研边界，把横切治理迁入官方 Middleware/Toolkit/Repository 扩展点，再分批启用 Harness 原生能力。**

三条核心论据：

1. 换核不解决正确性问题。RQ-01～RQ-05、RQ-08～RQ-10 全部位于 AAF 自己的取消、事件、状态、幂等与证据协议，路线一和路线二照样要修。
2. Harness 已提供 AAF 当前缺失的 10 项通用能力。完整替代或自研 Harness 意味着重新承担 tool eviction、session search、Plan Mode、async tool、skill runtime、filesystem/sandbox、gateway/bus 的实现与维护成本。
3. 10 项企业超越能力中 7 项可迁扩展点，只有 3 项必须刚需自研——说明当前自研规模明显大于必要规模，收窄比换核收益更高。

三阶段路径：**生产正确性封口 → 边界收窄与扩展点迁移 → Harness 原生能力分批启用**。

## 后续修正（2026-08-31）

对官方 [子 Agent](https://java.agentscope.io/v2/zh/docs/harness/subagent.html) 与 [计划模式](https://java.agentscope.io/v2/zh/docs/harness/plan-mode.html) 两篇文档做了精读并复核源码，修正了初版四处判定。详见 [03-capability-gap.md](03-capability-gap.md#后续修正2026-08-31) 与 [04-reuse-correctness.md](04-reuse-correctness.md#后续修正2026-08-31)。

| 修正 | 初版判定 | 修正后 |
|------|---------|-------|
| Plan Mode | 完全缺失、无需自研 | 可选启用。它是**执行内的阶段闸门**（AAF 只有整次执行级 `READ_ONLY`），且有程序化入口 `enterPlanMode`/`exitPlanMode` 且不触发 HITL，可绕开"模型自主决定"的四种歧义终态 |
| Subagent 的租户与授权 | 「Harness 的 subagent 不做租户隔离与逐节点授权」 | **推翻**。`userId` 自动透传父→子，父的全部 DENY 规则自动继承，Plan Mode 限制确实沿委派链传播（`AgentSpawnTool.java:299-301`） |
| 编排归属 | 隐含二选一 | 按**确定性要求**分流：Team 执行（需预先审核）留 TaskBoard；探索性分解可用 Harness 原生 subagent，前置条件是补全事件 `source` 投影 |
| 重复清单 | 18 项 | 19 项，新增 `PermissionMode`（`DEFAULT`/`BYPASS`/`DONT_ASK` 与 AAF `ActionAuthorizationPolicy` 高度重叠，`DONT_ASK` 语义等于 `DENY_AUTHORIZED_ACTIONS`） |

修正后的 Harness 原生能力启用建议：`PermissionMode` **启用并替换自研** · Plan Mode 与 Subagent **可选启用** · `todo_write`/`tasksContext` **不启用**（模型全量替换、无一等变更事件，与 TaskBoard 冲突）· workspace/filesystem/shell/sandbox **保持关闭**。

方法论教训：官方两篇文档对同一安全边界给出相反结论，最终由源码判定。由此得出的三条能力判定规则已写入 [agentscope-usage-guide.md](../../../reference/dev/agentscope-usage-guide.md#能力判定三条规则)。

## 下一步

本评估的结论需要落成一条 ADR 后才能驱动代码改动。建议标题 **ADR-004：AgentScope Harness 执行面与 AAF 企业控制面边界**，status 起始为 `proposed`，等人类审核。

注意 ADR 目录存在编号显示错误：`docs/design/adr/Readme.md` 索引把 `ADR-003-virtual-threads-over-webflux.md` 标为 ADR-004，而该文件正文标题也写作 `# ADR-004`。创建新 ADR 时需同时修正编号一致性，不要把索引里的误标当成已占用的实体文件。

按协作红线，路线选择属 🔴 高风险架构决策，**必须人类审核后才能开始编码**。可直接执行的低风险项与需审核项的划分见 [05-evolution-options.md](05-evolution-options.md#后续动作)。

## 参考资料

### 官方真理源

| 类型 | 位置 | 说明 |
|------|------|------|
| 官方文档（v2 中文） | <https://java.agentscope.io/v2/zh/docs/index.html> | 本评估的官方能力基线。页面标注 Last updated 2026-06-09 |
| 官方文档（v2 英文） | <https://java.agentscope.io/v2/en/docs/index.html> | 部分章节中文缺失时以英文版为准 |
| 本地源码副本 | `tmp/agentscope-java/` | 评估时逐类核实用的源码，含 `agentscope-core`、`agentscope-harness`、`agentscope-extensions`；`SKILL.md` 是官方能力速查 |
| AAF 依赖版本 | `io.agentscope:agentscope-harness:2.0.0` | 见 `apps/service/aaf-dependencies/pom.xml` 与 `aaf-framework/pom.xml` |

本地 `tmp/agentscope-java/docs/v2/` 只有 index 与 quickstart 两篇，其余章节需访问在线文档。

### 官方 v2 模块划分

按官方 TOC 三层组织，后续单项对比讨论以此为议题划分依据。

**核心组件（Building Blocks）** — `/v2/zh/docs/building-blocks/`

| 模块 | 路径 |
|------|------|
| 智能体 | `agent.html` |
| 消息与事件 | `message-and-event.html` |
| Middleware | `middleware.html` |
| 模型 | `model.html` |
| 权限系统 | `permission-system.html` |
| 工具 | `tool.html` |
| 上下文与 AgentState | `context.html` |

**Harness** — `/v2/zh/docs/harness/`

| 模块 | 路径 |
|------|------|
| 架构 | `architecture.html` |
| 上下文压缩 | `compaction.html` |
| 工作区 | `workspace.html` |
| 记忆 | `memory.html` |
| 文件系统 | `filesystem.html` |
| 沙箱 | `sandbox.html` |
| 子 Agent | `subagent.html` |
| 技能 | `skill.html` |
| 计划模式 | `plan-mode.html` |
| Channel | `channel.html` |

**参考** — `/v2/zh/docs/`

| 模块 | 路径 | 对本评估的用途 |
|------|------|--------------|
| 迁移指南 | `change-log.html` | 判断上游 API 演进节奏，喂给 [05](05-evolution-options.md) 的切换判据 |
| Release Notes | `others/release-notes.html` | 同上 |
| 上生产 | `others/going-to-production.html` | 作为外部基准对标 [02](02-runtime-quality.md) 的 13 条 RQ 风险，检查是否漏掉官方已知的生产问题 |
| 常见问题 | `others/faq.html` | — |

**集成** — `/v2/zh/integration/`

| 分组 | 具体适配 | 与 AAF 的重合面 |
|------|---------|---------------|
| 模型提供商 | OpenAI、DeepSeek、GLM、Kimi、MiniMax、DashScope、Gemini、Anthropic、Ollama | AAF `core/model` 的 `CapabilityRouter` / `ModelManagementService` |
| 记忆 | Mem0、百炼记忆、ReMe | AAF `cognition/memory` + `engine/memory` |
| Agent 状态存储 | MySQL、Redis、OSS | AAF 已用官方 Redis store，但自包 `SpringRedisClientAdapter`（见 [02](02-runtime-quality.md) RQ-13） |
| RAG 知识库 | Simple、百炼知识库、Dify、HayStack、RAGFlow | AAF `engine/knowledge` 整套自研 |
| 技能仓库 | Git、MySQL、**PostgreSQL** | AAF 用 PostgreSQL 且自研 skill 存储，直接重合（见 [04](04-reuse-correctness.md)） |
| 智能体协议 | A2A、AG-UI、Agent Protocol | AAF 自研 AG-UI 投影与 SSE 入口，[04](04-reuse-correctness.md) 判定应向官方 adapter 收敛 |
| 基础设施 | Higress、Nacos、Scheduler | AAF 自研 `DelegatedTaskScheduler`、`AutomationScheduler` |
| 生态扩展 | Chat Completions Web、Studio、在线训练 | AAF 前端 trace 与可观测 |

### 单项对比讨论议题顺序

前四项定住路线三的可行性，之后按官方 TOC 顺序补齐：

1. **技能（Skill）** — 官方 Skill 是磁盘 Markdown 自进化仓库，AAF Skill 是意图路由规则，同名不同物，先判定冲突还是可共存
2. **权限系统与 HITL** — 官方三态决策是框架内生能力，AAF 自研 `ToolApprovalService`，最大重复造轮子嫌疑
3. **上下文与 AgentState** — 官方原生四维隔离 `session/user/agent/org` vs AAF 自拼 `stateUserKey`，是 RQ-08/RQ-09 的根
4. **上下文压缩（Compaction）** — `disableCompaction()` 的代价评估，直接决定长任务可行性

## 与其他文档的关系

| 文档位置 | 定位 |
|---------|------|
| 本目录 | **一次性专项评估**，记录 2026-08-30 时点的事实与判断，不随代码演进更新 |
| [docs/design/framework/intelligent/](../../framework/intelligent/Readme.md) | 五层智能架构的**设计真理源**；本评估发现的设计/代码差异应回流到这里修正 |
| [docs/design/adr/](../../adr/Readme.md) | 路线决策的**权威记录**；本评估是 ADR 的输入材料 |
| [docs/reference/dev/agentscope-usage-guide.md](../../../reference/dev/agentscope-usage-guide.md) | AgentScope 使用规范；扩展点用法结论应回流补充 |
| [docs/prd/improvements.md](../../../prd/improvements.md) | 本评估中未立即处理的改进项登记处 |
