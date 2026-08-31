---
level: Thought
layer: Principle
purpose: 对比 AAF 的三条 AgentScope 演进路线并给出唯一推荐、迁移路径与可观测切换判据
status: superseded
superseded-by: docs/design/adr/ADR-005-agentscope-boundary-and-orchestration.md
version: 1.1.0
date: 2026-08-30
author: AaronZZH
changelog:
  - 2026-08-30 初版，产出三条路线定义、六维对比矩阵与单一推荐（路线三）
  - 2026-09-01 标记为 superseded：路线推荐被 ADR-005 改判，新增「被 ADR-005 改判的部分」并清理已过期行动项
---

# AAF AgentScope 演进路线决策建议

> **本文的路线推荐已被 [ADR-005](../../adr/ADR-005-agentscope-boundary-and-orchestration.md) 改判，不再是当前决策。**
> 本文保留为 ADR-005 的输入材料与 2026-08-30 时点的事实基线（路线定义、对比矩阵、迁移路径、切换判据仍可引用）。哪些结论失效见下方[被 ADR-005 改判的部分](#被-adr-005-改判的部分)。

## 决策摘要

**单一推荐：路线三——保留 `agentscope-harness`，收窄 AAF 自研边界，把横切治理迁入官方 Middleware/Toolkit/Repository 等扩展点，再按风险逐步启用 Harness 原生能力。**（⚠️ 本推荐已被 ADR-005 改判，见[被 ADR-005 改判的部分](#被-adr-005-改判的部分)）

该推荐不是维持现状。当前 59 项能力中只有 10 项复用、47 项由 AAF 自研替代、2 项未使用，且 `HarnessAgent` 仍非预期装配 workspace/filesystem/message bus、`InboxMiddleware` 与 `WaitAsyncResultsTool`；路线三必须先封住这些默认表面，再执行边界收窄，不能把现状直接视为目标态。证据见 [01-reuse-map.md](01-reuse-map.md) 的“结论摘要”“Harness 默认开启项与 AAF 实际结果”。

推荐顺序固定为：**先修运行正确性 → 再迁移扩展点与收窄边界 → 最后启用原生能力**。原因是当前存在 RQ-01 一个 blocker 与 RQ-02～RQ-10 九个 major；换成 core 或 Spring AI 都不会自动修复其中大部分 AAF 执行注册、取消、事件、状态和幂等协议问题。证据见 [02-runtime-quality.md](02-runtime-quality.md) 的“风险清单”与“最终判定”。

## 被 ADR-005 改判的部分

[ADR-005](../../adr/ADR-005-agentscope-boundary-and-orchestration.md) 在本文基础上做了代码级验证并定案，实际取的是**路线二的执行面骨架 + 本文路线三第二阶段的扩展点迁移**，不是本文推荐的完整路线三。逐条对应关系如下。

| 本文结论 | ADR-005 实际决定 | 当前状态 |
| --- | --- | --- |
| 单一推荐路线三：以 Harness 为执行面，不自研 harness 层 | 议题三选 B：编译目标改为 `io.agentscope.core.ReActAgent`（core 包），理由是 15 个 `disableXxx()` 使用率为零且 `WaitAsyncResultsTool` 构成真实工具泄漏 | **失效**。执行面外层（builder、生命周期、缓存、middleware 装配、中断、能力启停）由 AAF 自持，即本文路线二的技术含义 |
| 路线三第三阶段：Harness 原生能力分批启用 | 议题二选 A：不采用原生 Subagent/Plan Mode，保留自建 TaskBoard 外部编排（两者是容器关系 vs 平级编排关系，结构不同不可互换） | **基本作废**。workspace/filesystem/shell/sandbox 保持关闭；仅 `PermissionMode` 仍在采纳评估中——它是 `ReActAgent.builder()` 基础参数，非 Harness 专属 |
| 路线三第二阶段：横切治理迁入官方 Middleware/Toolkit/AgentStateStore/repository 扩展点 | ADR-005 未改判 | **仍有效**。这些扩展点位于 core，不依赖 `HarnessAgent` |
| 第一阶段：生产正确性封口（RQ-01 blocker + 9 项 major + `JsonSchemaUtils` shadow 退出） | ADR-005 明确「本 ADR 不改变这份工作量」 | **仍有效**，且是 ADR-005 后续动作第 1、2 项 |
| AG-UI 收敛到官方 adapter（见 [04-reuse-correctness.md](04-reuse-correctness.md)） | 议题一选 B：采用 `agentscope-extensions-agui` 的**事件模型库**，删除自研 `AgUiEvent`；**不采用** starter/`AguiAgentAdapter`（抽象层级错配：starter 的货币单位是"一个 Agent"，AAF 的执行单元是"一个任务"） | **部分改判**。收敛止于数据层，协议入口与入参 record 仍由 AAF 自持 |
| 切换判据表中「Harness 默认表面持续漂移」「上游提供稳定最小模式（minimalMode）」两行 | — | **失效**。两者均以 `HarnessAgent` 为执行面为前提 |

本文的一处表述瑕疵一并记录：路线三定义写作"继续以 `HarnessAgent`/`ReActAgent` 作为执行面，不新建 AAF ReAct 或自研 Harness"，把 `ReActAgent` 也纳入路线三，与同句"不自研 Harness"自相矛盾。区分路线二与路线三的真正判据是**谁拥有 Agent 外层装配与生命周期**，不是编译目标类名。

ADR-005 留下的未决口子（不属本文范围，已回流至 ADR-005 后续动作）：依赖坐标是否由 `agentscope-harness` 降为 `agentscope-core`。ADR-005 只决定了编译目标类，而 `aaf-dependencies/pom.xml` 与 `aaf-framework/pom.xml` 目前仍声明 harness 坐标。

## 证据基线与估算口径

路线比较采用以下共同事实：

- 复用边界共 59 项：复用 10、自研替代 47、未使用 2；AAF 已直接复用 AgentScope 的 Model、Msg、Toolkit、ReAct、AgentStateStore、AgentEvent 和 Middleware，Harness 上层能力大多被关闭。[01-reuse-map.md](01-reuse-map.md)“AgentScope core 逐项对照”“AgentScope harness 领域逐项对照”。
- AAF `intelligent` 控制面约 452 个 Java 文件、约 4.5 万行；AgentScope Harness 调查面约 215 个 Java 文件、约 4.2 万行。该数据只说明维护面的量级，不能等同于应删除代码量。[03-capability-gap.md](03-capability-gap.md)“结论”。
- 能力结果为缺失 10、等价或替代 5、超越 10；10 项超越中 3 项是刚需自研，7 项可迁入官方扩展点。[03-capability-gap.md](03-capability-gap.md)“缺失清单”“超越项最终判定”。
- 重复能力共 18 项：删除并复用官方 3、保留自研 7、改造为官方扩展点 8。[04-reuse-correctness.md](04-reuse-correctness.md)“重复能力清单”。
- Spring Boot 4/Jackson 3 与 AgentScope 当前 Jackson 2/victools ABI 冲突已导致一个同 binary name 的 `io.agentscope.core.util.JsonSchemaUtils` shadow；这不是公开扩展点，而是需退出的兼容 shim。[04-reuse-correctness.md](04-reuse-correctness.md)“JsonSchemaUtils 同名覆盖风险”。

下文“影响类数量”是规划估算，不是源码精确盘点。估算以 [01-reuse-map.md](01-reuse-map.md) 逐项表列出的 AAF 直接适配类为下界，再按 [03-capability-gap.md](03-capability-gap.md) 与 [04-reuse-correctness.md](04-reuse-correctness.md) 所列控制面、provider 和协议类群扩大范围；实施前仍需用依赖图和引用搜索生成精确清单。

## 三条路线的固定定义

### 路线一：Spring AI 完整替代 AgentScope

**技术含义写死：**彻底删除所有 `io.agentscope` 依赖。AAF 基于 Spring AI `ChatClient`、`ToolCallback` 自建 ReAct loop，并自行实现迭代停止、中断/恢复、事件流、状态持久化与工具 observation 协议；这不是“用 Spring AI Agent 替换一层 adapter”，而是由 AAF 接管 AgentScope core 与 Harness 当前承担的完整执行内核。

**需要改动的范围：**

- 必须替换 [01-reuse-map.md](01-reuse-map.md) C01～C05、C07～C08、H01、M07、T04 对应的 10 项直接复用能力，并删除 AgentScope model/message/tool/state/event/middleware/harness 接线。
- 直接受影响的现有生产类至少覆盖 `AgentScopeModelResolver`、`AgentScopeMessageMapper`、`AgentScopeToolkitFactory`、`PortBackedAgentTool`、`AgentScopeSpecCompiler`、`HarnessAgentExecutionAdapter`、`AgentScopeEventMapper`、`AgentScopeRuntimeContextMapper`、`PromptEnvelopeCaptureMiddleware`、`AgentScopeTokenMeteringObserver`、`SpringRedisClientAdapter` 及 AgentScope 两组 AutoConfiguration；这些类均见 [01-reuse-map.md](01-reuse-map.md) 的 core 对照、编译期边界和真实调用链。
- 规划量级为 **现有 13～18 个直接生产类重写/删除，新增约 8～15 个 loop、状态、事件和中断实现类，并重做对应测试与依赖配置**。该估算只覆盖执行边界，不包括 452 个文件的 AAF 企业控制面；上层仍可通过 `AgentExecutionPort` 隔离而保留。[01-reuse-map.md](01-reuse-map.md)“中期收敛到 core ReAct”“真实请求调用链”。

**AAF 必须自己承担：**

- AgentScope 当前提供而 AAF 不应重复实现的 ReAct、模型/消息/工具 schema、AgentState、类型化事件和 Middleware 链全部转为 AAF 责任。[01-reuse-map.md](01-reuse-map.md) C01～C08。
- [03-capability-gap.md](03-capability-gap.md) 的 10 项缺失能力不能再通过 Harness 恢复；若产品需要，AAF 需自行实现或寻找 Spring 生态替代，包括工具结果卸载、会话搜索、Plan Mode、任意异步工具、Skill 安全/灰度、完整 filesystem/sandbox、`@path`、Gateway/Channel 和 Agent MessageBus。
- 3 项刚需自研仍不能删除：tenant 隔离、conversation lease/fencing、任务持久化/恢复/outbox。[03-capability-gap.md](03-capability-gap.md)“超越项最终判定”。

### 路线二：保留 agentscope-core，自研 Harness 编排层

**技术含义写死：**保留 AgentScope core 及必要 extensions，移除 `agentscope-harness` 依赖；AAF 直接构建 core `ReActAgent`，自行实现当前 `HarnessAgent` 外层的 builder、生命周期、缓存、middleware 装配、状态策略、中断、能力启停和执行卫生。AAF 现有 `TaskBoard`、Role-Skill-Task、持久任务与治理控制面继续保留。

**需要改动的范围：**

- 保留 [01-reuse-map.md](01-reuse-map.md) C01～C08 中已复用的 core 边界，替换 H01，并消除由 H01 带入的 M07 `InboxMiddleware` 与 T04 `WaitAsyncResultsTool` 默认表面。
- 直接改动集中在 `AgentScopeSpecCompiler`、`HarnessAgentExecutionAdapter`、生命周期/AutoConfiguration 与其测试；规划量级为 **改造 4～8 个现有生产类、新增约 6～12 个自研 harness 类**。依据是 [01-reuse-map.md](01-reuse-map.md) 的调用链中只有 compiler/adapter 直接持有 `HarnessAgent`，但 Harness 当前还替 AAF 包装了 `ReActAgent` 构建、事件与关闭流程。

**AAF 必须自己承担：**

- 自建 Harness 的 Agent 构造约束、middleware 顺序、缓存/淘汰、interrupt/close、状态恢复和每次升级的 core 契约回归。
- [03-capability-gap.md](03-capability-gap.md) 的 10 项 Harness 缺失能力默认继续缺失；若要补齐，需逐项自研或重新引入其他实现。路线二不能获得 Harness 的 compaction、session、skill runtime、subagent、filesystem/sandbox、gateway 与 bus 演进红利。
- 3 项刚需企业控制面以及 7 项可迁扩展点的领域部分仍由 AAF 承担。[03-capability-gap.md](03-capability-gap.md)“超越项最终判定”。

#### 路线二对 RQ 风险的影响

“直接用 core `ReActAgent`”只消除 Harness 默认 workspace/bus 泄漏，不等于修复 AAF 执行协议。逐项结论如下：

| 风险 | 路线二结果 | 判定依据 |
| --- | --- | --- |
| RQ-01 终态竞态 | **保留** | 竞态位于 AAF `cancel`、源流和持久终态仲裁，不在 Harness builder。[02-runtime-quality.md](02-runtime-quality.md) RQ-01。 |
| RQ-02 同步 I/O 阻塞事件线程 | **保留** | Redis/JPA 校验位于 AAF `doOnNext`。[02-runtime-quality.md](02-runtime-quality.md) RQ-02。 |
| RQ-03 timeout 语义 | **保留** | timeout 在 AAF 映射流和事件入库之间的位置不因换 Agent 类改变。[02-runtime-quality.md](02-runtime-quality.md) RQ-03。 |
| RQ-04 失败分类不足 | **保留** | 失败收口和事件 payload 由 AAF adapter/mapper 定义。[02-runtime-quality.md](02-runtime-quality.md) RQ-04。 |
| RQ-05 execution 重放不幂等 | **保留** | 模型是否再次执行由 AAF execution claim 决定，不由 Harness/core 决定。[02-runtime-quality.md](02-runtime-quality.md) RQ-05。 |
| RQ-06 无界 HarnessAgent 缓存 | **当前具体实现消失，但风险需重新验收** | 删除现有 `cache/directCache` 可去掉该缺陷；自研 harness 若缓存 `ReActAgent`，必须重新证明容量、TTL 与 close。[02-runtime-quality.md](02-runtime-quality.md) RQ-06。 |
| RQ-07 `DirectKey` 缺执行策略 | **当前具体键缺陷消失，但策略身份风险需重新验收** | 删除 `DirectKey` 去掉原缺陷；任何新缓存键仍须包含完整 `ExecutionPolicy`。[02-runtime-quality.md](02-runtime-quality.md) RQ-07。 |
| RQ-08 隐藏历史 TOCTOU | **保留** | 真正状态加载发生在 core `ReActAgent.activateSlotForContext`，路线二仍使用它。[02-runtime-quality.md](02-runtime-quality.md) RQ-08。 |
| RQ-09 状态键缺 Agent/execution | **保留** | 键由 AAF `AgentScopeRuntimeContextMapper` 生成，底层仍是 core state 语义。[02-runtime-quality.md](02-runtime-quality.md) RQ-09。 |
| RQ-10 工具证据泄漏 | **保留** | `ToolResultEvidenceStore` 位于 AAF tool/event 映射边界。[02-runtime-quality.md](02-runtime-quality.md) RQ-10。 |
| RQ-11 重复订阅未事件化 | **保留** | `activeExecutions` 是 AAF adapter 协议。[02-runtime-quality.md](02-runtime-quality.md) RQ-11。 |
| RQ-12 cancel/close 竞态 | **原 HarnessAgent 竞态消失，新增 ReActAgent 生命周期竞态** | 原问题显式涉及临时 HarnessAgent close；自研 harness 必须重新定义 interrupt、dispose、close 顺序。[02-runtime-quality.md](02-runtime-quality.md) RQ-12。 |
| RQ-13 Redis 非原子登记与 `KEYS` | **保留** | 仍复用同一 Redis AgentState adapter/store 路径。[02-runtime-quality.md](02-runtime-quality.md) RQ-13。 |

路线二还新增三类风险：自研 builder 的 middleware 顺序与 tool surface 漂移、ReActAgent 生命周期/恢复契约升级、Harness 原生能力缺失后继续扩张第二套通用运行时。前两项来自 [01-reuse-map.md](01-reuse-map.md) 对 Harness 默认装配和 core 状态/事件复用的源码证据；第三项来自 [03-capability-gap.md](03-capability-gap.md) 的 10 项缺失清单与 452 文件控制面规模。

### 路线三：保留 Harness，收窄自研边界

**技术含义写死：**继续以 `HarnessAgent`/`ReActAgent` 作为执行面，不新建 AAF ReAct 或自研 Harness；AAF 只保留企业控制面。先用最终 Toolkit/middleware 契约阻断非预期默认装配，再把 token、HITL、事件、工具治理、Role/Skill、provider/protocol 等横切逻辑迁入官方 `MiddlewareBase`、`Toolkit`/`ToolBase`、`AgentStateStore` decorator、Skill/Task repository adapter 与标准 protocol/starter；最后按 AAF policy 显式启用 Harness 原生能力。

**需要改动的范围：**

- 初始封口直接涉及 compiler、execution adapter、event/message mapper、PromptEnvelope、state/tool evidence 和依赖接线，规划约 **12～20 个生产类及相应测试**；证据面见 [01-reuse-map.md](01-reuse-map.md)“HarnessAgent 编译期边界证据”与 [02-runtime-quality.md](02-runtime-quality.md) RQ-01～RQ-10。
- 边界收窄覆盖 [03-capability-gap.md](03-capability-gap.md) 的 7 项“可改造为官方扩展点”和 [04-reuse-correctness.md](04-reuse-correctness.md) 的 8 项“应改造为官方扩展点”。两组有职责交叉但不是同一清单，规划约 **30～60 个生产/测试/配置文件分批迁移**，不能一次大切换。
- AAF Role-Skill-Task 模型保持权威：Role 候选和 Skill 授权在调用前确定并注入官方扩展点，TaskBoard/recovery/outbox 继续作为业务事实源；Harness 只接管通用 Agent/Tool/临时 subagent 执行。[03-capability-gap.md](03-capability-gap.md)“分离数据控制面与执行面”“超越项最终判定”。

**AAF 必须自己承担：**

- 3 项刚需自研：tenant 隔离、conversation lease/fencing、任务持久化/恢复/outbox。[03-capability-gap.md](03-capability-gap.md)“超越项最终判定”。
- 7 项可迁扩展点的领域真理源仍归 AAF，只把采集/拦截入口迁走：token 价格结算、持久审批记录、PromptEnvelope、业务 EventStore、Connector credential/receipt、Role/Skill 授权候选、Confidence policy。[03-capability-gap.md](03-capability-gap.md)“超越项最终判定”。
- 认知治理、可信知识、模型管理、业务文件、领域 JPA 聚合等 7 项“应保留自研”不能误删。[04-reuse-correctness.md](04-reuse-correctness.md)“重复能力清单”。

## 六维度对比矩阵

评分采用 **5 分最佳、1 分最差**；“迁移成本/维护成本/适配代价”按成本越低得分越高，“升级与断供风险”按风险越低得分越高。

| 维度 | 路线一：Spring AI 完整替代 | 路线二：core + 自研 Harness | 路线三：保留 Harness 收窄边界 |
| --- | --- | --- | --- |
| 迁移成本 | **1/5**：需替换 10 项已复用执行能力并自建 loop/状态/事件/中断；[01-reuse-map.md](01-reuse-map.md) C01～C08、H01。 | **3/5**：保留 core 映射和 provider，但需重建 Harness builder、生命周期和能力启停；[01-reuse-map.md](01-reuse-map.md)“中期收敛到 core ReAct”。 | **4/5**：执行主链不换核，先修 adapter，再迁 7+8 个扩展面；改动广但可按扩展点分批。[03-capability-gap.md](03-capability-gap.md)“超越项最终判定”；[04-reuse-correctness.md](04-reuse-correctness.md)“重复能力清单”。 |
| 长期维护成本 | **2/5**：AAF 永久承担 ReAct 与 10 项缺失能力的取舍和回归，扩大当前约 4.5 万行控制面。[03-capability-gap.md](03-capability-gap.md)“结论”“缺失清单”。 | **2/5**：既跟随 core 契约，又维护第二套 Harness；RQ-01～RQ-05、RQ-08～RQ-13 不自动消失。[02-runtime-quality.md](02-runtime-quality.md)“风险清单”。 | **4/5**：通用 runtime/provider/protocol 回归官方，AAF 聚焦 3 项刚需和领域真理源；仍需维护扩展适配与上游契约测试。[03-capability-gap.md](03-capability-gap.md)“分离数据控制面与执行面”。 |
| 能力上限 | **3/5**：理论上可完全定制，但 10 项 Harness 能力需自行建设，短中期上限受实现投入约束。[03-capability-gap.md](03-capability-gap.md)“缺失清单”。 | **3/5**：保留 core ReAct 上限，但失去 Harness compaction、skill、subagent、filesystem/sandbox、gateway/bus 的现成演进面。[03-capability-gap.md](03-capability-gap.md)“Harness 基线状态”。 | **5/5**：可同时保留 AAF 10 项企业超越能力并恢复 Harness 通用能力；其中 7 项企业能力已有官方扩展切点。[03-capability-gap.md](03-capability-gap.md)“超越清单与必要性论证”。 |
| 升级与断供风险 | **4/5**：消除 AgentScope 供应依赖，但把风险转为 Spring AI API 与自研 loop 的长期正确性责任；10 项能力缺口仍需 AAF 承担。[03-capability-gap.md](03-capability-gap.md)“缺失清单”。 | **3/5**：不受 Harness 默认能力漂移，但仍依赖 core 的状态、事件、模型和工具 ABI，并新增自研 harness 升级适配面。[01-reuse-map.md](01-reuse-map.md) C01～C08。 | **2/5（治理后可到 3/5）**：当前 Harness 会注入未请求的 workspace/bus/Inbox/Wait，未来默认值可能继续漂移；最终 Toolkit/middleware 契约和最小 fork 是提高分数的前提。[01-reuse-map.md](01-reuse-map.md)“Harness 默认开启项与 AAF 实际结果”；[04-reuse-correctness.md](04-reuse-correctness.md)“替代方案比较”。 |
| Spring Boot 4 生态兼容性 | **5/5**：移除 `io.agentscope` 后不再需要当前 Jackson 2/3 同名 shadow；现有冲突事实见 [04-reuse-correctness.md](04-reuse-correctness.md)“冲突动机”。 | **2/5**：冲突位于 AgentScope core `JsonSchemaUtils`/ToolSchemaGenerator，删除 Harness 不能根治。[04-reuse-correctness.md](04-reuse-correctness.md)“技术本质”“替代方案比较”。 | **2/5（fork/上游修复后 4/5）**：现状存在一个重复 binary name；必须整体替换为独立坐标 fork 并删除 shadow，不能继续 classpath 覆盖。[04-reuse-correctness.md](04-reuse-correctness.md)“推荐方案”。 |
| Role-Skill-Task 适配代价 | **2/5**：Role prompt、Skill 工具投影和 Task 事件都要重新接入 Spring loop；AAF 现有模型虽可保留，但执行连接面全部重写。[01-reuse-map.md](01-reuse-map.md)“真实请求调用链”。 | **4/5**：外层 Role-Skill-Task 可保留，但 Skill/subagent 的 Harness runtime 不可复用，AAF 继续承担映射和生命周期。[03-capability-gap.md](03-capability-gap.md)“Skill 渐进披露”“持久子任务/子智能体编排”。 | **5/5**：Role/Skill 候选可经 `onSystemPrompt`、SkillRepository、Toolkit group 输出，TaskBoard 保持业务真理源，Harness 管通用执行。[03-capability-gap.md](03-capability-gap.md)“Role/Skill 业务意图路由”“任务持久化、恢复与 outbox”。 |

## 推荐路线与论据

### 推荐结论

> 本节推荐已被 ADR-005 改判，见[被 ADR-005 改判的部分](#被-adr-005-改判的部分)。下述"执行面/企业控制面"的**边界划分**仍成立，改判的是执行面由谁装配以及是否启用 Harness 原生能力。

选择**路线三**，目标边界固定为：

- **Harness 执行面**：ReAct、Model/Msg/Event、Toolkit、通用 compaction/session/skill/subagent/filesystem/sandbox/gateway 能力。
- **AAF 企业控制面**：tenant、Role-Skill 授权候选、conversation lease/fence、价格/预算、持久 HITL、PromptEnvelope、业务 EventStore、Connector credential/receipt、TaskBoard/recovery/outbox、Confidence policy。
- **连接方式**：只使用公开 Middleware、Toolkit/ToolBase、AgentStateStore decorator、repository adapter、RuntimeContext typed attributes 和 protocol adapter；禁止新增同包 shadow、Hook 或第二套 loop。[03-capability-gap.md](03-capability-gap.md)“分离数据控制面与执行面”；[04-reuse-correctness.md](04-reuse-correctness.md)“保留 AAF 权威域”。

### 核心论据

- **换核不能替代正确性修复。** RQ-01～RQ-05、RQ-08～RQ-10 均在 AAF 取消、事件、状态、幂等和证据协议上；路线二或路线一仍需重做同样的生产级合同。[02-runtime-quality.md](02-runtime-quality.md) RQ-01～RQ-10。
- **Harness 已提供 AAF 当前缺失的 10 项通用能力。** 完整替换或自研 Harness 会把 tool eviction、session search、Plan Mode、async tool、skill 治理、filesystem/sandbox、gateway/bus 等重新变成 AAF 维护责任。[03-capability-gap.md](03-capability-gap.md)“缺失清单”。
- **AAF 的差异化能力可在官方扩展点上保留。** 10 项超越能力中 7 项可迁 Middleware/Toolkit 等扩展点，只有 tenant、fencing、持久任务控制面必须刚需自研。[03-capability-gap.md](03-capability-gap.md)“超越项最终判定”。
- **现有端口已隔离执行核，适合渐进收敛。** `AgentExecutionPort` 隔离上层，当前 Model/Toolkit/State/Middleware 接法总体正确，无需为缩小边界先重写整个 Role-Skill-Task 控制面。[01-reuse-map.md](01-reuse-map.md)“中期收敛到 core ReAct”；[04-reuse-correctness.md](04-reuse-correctness.md)“官方扩展点复用正确性”。
- **路线三的主要技术反例可被明确封口。** Harness 默认 Wait/Inbox 可由最终 surface 契约阻断，Jackson 冲突可用整体独立坐标 fork + 上游 PR 删除 shadow；两者都有可验收退出条件。[01-reuse-map.md](01-reuse-map.md)“立即收紧运行边界”；[04-reuse-correctness.md](04-reuse-correctness.md)“替代方案比较”。

### 推荐路线的代价

- 必须承受一次跨模块、跨 30～60 个生产/测试/配置文件量级的扩展点迁移；[03-capability-gap.md](03-capability-gap.md) 的 7 项与 [04-reuse-correctness.md](04-reuse-correctness.md) 的 8 项不能一次完成。
- 短期需维护一个**整体替换官方坐标**的最小 AgentScope fork，直到上游 Jackson 3/victools 5 版本通过 AAF 验证；fork 与官方 core 不能同时进入 classpath。[04-reuse-correctness.md](04-reuse-correctness.md)“替代方案比较”“推荐方案”。
- 必须建立 AgentScope 升级契约测试，否则 Harness 新默认 middleware/tool 仍可能越过 AAF 冻结画像。[01-reuse-map.md](01-reuse-map.md)“Harness 默认开启项与 AAF 实际结果”。
- 原生能力启用会改变 tool/event/session 行为，必须逐项灰度和故障注入，不能以“官方已有”替代 AAF 的 tenant、fence、审批和审计验收。[03-capability-gap.md](03-capability-gap.md)“按风险分批替换”。

### 推荐路线无法自动解决的问题

- 路线三本身**不会修复** RQ-01 blocker 和九个 major，必须完成首阶段代码修复。[02-runtime-quality.md](02-runtime-quality.md)“上线前修复”“近期加固”。
- 路线三**不会消除** 3 项刚需自研的长期维护成本，也不会让 TaskBoard、lease/fence、outbox 自动由 Harness 替代。[03-capability-gap.md](03-capability-gap.md)“超越项最终判定”。
- 路线三**不会自动解决** AgentScope core 的 Jackson 2/3 ABI；必须 fork 或等待已验证的上游兼容版本。[04-reuse-correctness.md](04-reuse-correctness.md)“JsonSchemaUtils 同名覆盖风险”。
- 路线三不要求开启全部 Harness 能力。Memory 写入、业务通知 outbox、AAF Skill 意图路由等存在不同真理源，仍须保持 AAF 权威边界。[01-reuse-map.md](01-reuse-map.md)“重复造轮子判定”；[04-reuse-correctness.md](04-reuse-correctness.md)“保留 AAF 权威域”。

## 分阶段迁移路径

### 生产正确性封口

**目标：**在不改变执行核的前提下达到生产正确性门槛，消除 RQ-01、九个 major、Harness 未授权表面和 `JsonSchemaUtils` shadow。

**具体动作：**

- 用单一原子终态枚举/CAS 仲裁完成、失败、取消；取消主动终止上游，并让 `cancel()` 返回值与持久终态同源，关闭 RQ-01。[02-runtime-quality.md](02-runtime-quality.md)“上线前修复”。
- 将 Redis/JPA fence 校验移出模型事件线程但保持 fail-closed；拆分 total deadline、idle timeout、事件写入 SLA；新增稳定 `failureCategory/retryable/failureId` 并记录原异常，分别关闭 RQ-02、RQ-03、RQ-04。[02-runtime-quality.md](02-runtime-quality.md) RQ-02～RQ-04。
- 以 `(tenantId, executionId/idempotencyKey)` 建持久 execution claim，重复订阅读已有结果而不重调模型/工具，关闭 RQ-05。[02-runtime-quality.md](02-runtime-quality.md) RQ-05。
- 将两类 Agent 缓存改为有界、可观测、淘汰时 close；缓存身份纳入完整 `ExecutionPolicy`，关闭 RQ-06、RQ-07。[02-runtime-quality.md](02-runtime-quality.md) RQ-06～RQ-07。
- 把隐藏历史校验和槽占用放入同一串行/租约边界；明确 AgentState 只承载执行工作态还是正式恢复态；状态键补稳定 Agent 身份，并明确 execution 是否共享，关闭 RQ-08、RQ-09。[02-runtime-quality.md](02-runtime-quality.md) RQ-08～RQ-09；[04-reuse-correctness.md](04-reuse-correctness.md)“AgentState 与 AgentStateStore”。
- 为 `ToolResultEvidenceStore` 增加 execution 级 `clear`、TTL 和容量上限，所有终态在 `doFinally` 清理，关闭 RQ-10。[02-runtime-quality.md](02-runtime-quality.md) RQ-10。
- 在 `HarnessAgent` build 后断言最终 Toolkit 与冻结 `effectiveTools` 完全相等；维护允许 middleware 清单，未显式授权的 `InboxMiddleware`/`WaitAsyncResultsTool` 直接启动失败。[01-reuse-map.md](01-reuse-map.md)“立即收紧运行边界”。
- 建立官方 31 个 `AgentEventType` 的显式处置矩阵；所有事件必须映射、脱敏映射或产生结构化 drop metric，禁止 default 静默吞；PromptEnvelope 按完整 `ContentBlock` 规范化哈希。[04-reuse-correctness.md](04-reuse-correctness.md)“AgentEvent 与 AgentEventType”“Middleware”。
- 以独立 Maven 坐标的最小 fork **整体替换**官方 core/harness，完成 Jackson 3/victools 5 修复并删除 AAF 同名 `JsonSchemaUtils`；同时向上游提交 PR。禁止官方 core 与 fork 同时存在。[04-reuse-correctness.md](04-reuse-correctness.md)“推荐方案”。
- 补齐 [02-runtime-quality.md](02-runtime-quality.md)“缺失测试清单”中的全部 P0 与对应 RQ-05～RQ-10 的 P1 测试；竞态测试使用可控 Publisher/barrier/虚拟时间，不用 sleep。

**验收判据：**

- 可控交错测试中每个 execution 恰有一个终态；`cancel=true` 必有且仅有 CANCELED，完成胜出时 cancel 返回 false。
- total deadline、idle timeout、事件写入 SLA 三类测试分别通过；Redis/JPA 延迟不占用模型事件线程。
- 同一 idempotency key 重试不再次调用模型/工具；缓存规模始终不超过配置上限；终止后工具证据为零。
- 两个同槽并发调用不能绕过隐藏历史校验；状态键按已批准的 Agent/execution 语义隔离。
- 最终 Toolkit 无 `effectiveTools` 外工具，middleware 契约无未授权项；31 个事件类型均有显式策略。
- 构建产物只存在一个 `io.agentscope.core.util.JsonSchemaUtils` binary name，且它来自整体替换后的 fork；AAF 源码不再声明该同名类。

**预估影响范围：****18～30 个生产/测试/POM 文件**。估算覆盖 [02-runtime-quality.md](02-runtime-quality.md) 涉及的 adapter/compiler/mapper/state/event/tool store，以及 [04-reuse-correctness.md](04-reuse-correctness.md) 指出的 framework/dependencies POM 和 shadow 类。

**风险与回退：**终态、幂等和状态键变化会影响恢复语义，属于高风险。每个子项单独提交、先以契约测试锁定旧业务事件字段；回退只能回到上一项已通过的单一路径，不保留双状态机、双写或 shadow 兼容层。fork 若未通过全量 schema/tool/provider 测试，则保持当前版本但禁止升级和生产上线，不能恢复同名 shadow 作为长期方案。

### 边界收窄与扩展点迁移

**目标：**不改变 Role-Skill-Task 业务真理源，把通用横切采集和 provider/protocol 接线迁到 AgentScope 公开扩展点，减少 execution adapter 与自研通用设施职责。

**排序与具体动作：**

| 顺序 | 迁移批次 | 动作与保留边界 | 证据 |
| --- | --- | --- | --- |
| A | 已有扩展点先加固 | `PromptEnvelopeCaptureMiddleware` 保持唯一捕获入口，补完整 ContentBlock canonical hash 与并发序号；不增加第二捕获路径。 | [03-capability-gap.md](03-capability-gap.md)“Prompt 预检与 PromptEnvelope 留痕”；[04-reuse-correctness.md](04-reuse-correctness.md)“Middleware”。 |
| B | 纯观察横切 | token observer 迁 `onModelCall`；业务 EventStore 由统一 middleware/stream subscriber 捕获，同时并行输出官方 trace/OTel/Studio；保留价格结算、事件账本和 reducer。 | [03-capability-gap.md](03-capability-gap.md)“Token 计量、预扣与信用额度”“执行事件溯源入库”；[04-reuse-correctness.md](04-reuse-correctness.md)“Studio、trace 与可视化”。 |
| C | 工具与人工门控 | 工具授权/Connector 信任链迁 Toolkit/ToolBase/permission/`onActing`；持久 HITL 接 `RequireUserConfirmEvent`/external suspend-resume，合并或删除遗留 `ToolApprovalService`；ConfidenceGate 保留纯领域策略并在 Agent/Tool 边界调用。 | [03-capability-gap.md](03-capability-gap.md)“持久 HITL 工具审批”“工具授权与 Connector 受信参数”“置信度门控”。 |
| D | Role/Skill 接线 | 保留 AAF Role 候选、Skill 授权与发布版本；经 `onSystemPrompt`、SkillRepository、Toolkit group 输出，先适配 repository，再迁 runtime，禁止把 AgentScope Skill 与 AAF 意图路由混为一类。 | [03-capability-gap.md](03-capability-gap.md)“Role/Skill 业务意图路由”；[04-reuse-correctness.md](04-reuse-correctness.md)“Skill repository”“Skill selection/runtime”。 |
| E | 数据与 provider | 依次把长期记忆 provider、RAG reader/chunker/embedding/vector store、sandbox provider 适配到官方接口；保留 AAF 原子记忆、知识治理、tenant/fence 和安全审批。 | [04-reuse-correctness.md](04-reuse-correctness.md)“长期记忆 provider”“RAG、chunker、embedding、vector/search/graph”“Sandbox”。 |
| F | 调度、协议与入口 | scheduler 只负责触发并回到 AAF durable task；Agent 双向 channel、AG-UI wire、标准 provider/starter 优先官方实现，AAF 只保留鉴权、thread ownership、命令组装、业务通知和 `aaf.*` 事件。 | [04-reuse-correctness.md](04-reuse-correctness.md)“Scheduler”“Protocol：AG-UI/A2A/chat-completions”“Channel”“Spring Boot starters 与 AAF AutoConfiguration”。 |

该顺序完整覆盖 [03-capability-gap.md](03-capability-gap.md) 的 7 项可改造扩展点：token、HITL、PromptEnvelope、执行事件、工具信任链、Role/Skill、ConfidenceGate；也覆盖 [04-reuse-correctness.md](04-reuse-correctness.md) 的 8 项改造项：长期记忆 provider、RAG provider、sandbox、Skill repository、scheduler、Studio/trace、Channel、Spring Boot starter。重叠处只建一条适配链，不双写。

**验收判据：**

- 每次模型调用的 PromptEnvelope、usage settlement、execution event 可按 executionId/attempt 对齐，且只有一个采集入口。
- ASK/external suspend 可跨进程恢复；审批、grant、credential、receipt 与 task transition 各有且仅有一个事实源。
- Role、Skill、Task 的发布版本与授权结果在迁移前后相同；TaskBoard、lease/fence、recovery/outbox 不依赖 Harness workspace 文件成为真理源。
- execution adapter 不再承载 token observer、重复 permission 规则、Skill loader 或 provider/protocol 转换；删除项均有对应官方扩展点契约测试。
- 3 项刚需自研与 [04-reuse-correctness.md](04-reuse-correctness.md) 的 7 项保留自研能力没有被回迁为 Harness 文件真理源。

**预估影响范围：****30～60 个生产/测试/配置文件**，按 A～F 六批实施；依据是两份评估共 15 个扩展点判定及其列出的代表类群，而不是一次性修改 452 个 `intelligent` 文件。

**风险与回退：**HITL、工具授权、Skill 和 protocol 涉及安全与对外契约。每批先建立黑盒契约测试，再切换唯一入口并删除旧入口；若失败，回退整批提交。禁止长期双写 EventStore/计量、双审批栈或新旧 AG-UI converter 并存。

### Harness 原生能力分批启用

**目标：**在边界和真理源已经明确后，补齐通用执行能力，但只启用通过 tenant、fence、授权和事件审计验证的能力。

**具体动作与启用顺序：**

- **低风险执行卫生：**启用 ToolResultEviction、官方 trace/OTel、受控 session log/list/search；大结果落盘必须经过 AAF fenced filesystem decorator，长期记忆写入仍走 AAF 治理。[03-capability-gap.md](03-capability-gap.md)“恢复通用执行卫生”。
- **中风险模型协作：**接入 Skill runtime 的安全/Canary/Promotion、Plan Mode、任意 async tool 与 `@path`；`wait_async_results` 只有在被 AAF 冻结工具策略显式授权时才能进入 Toolkit。[03-capability-gap.md](03-capability-gap.md)“缺失清单”；[01-reuse-map.md](01-reuse-map.md) T04。
- **高风险资源与多 Agent：**以官方隔离 sandbox 替换宿主 `ProcessBuilder`；再接 filesystem/workspace、临时 subagent、Gateway/Channel、MessageBus/Inbox。Harness 管实例和消息，AAF TaskBoard、outbox、租约与恢复仍是业务控制面。[03-capability-gap.md](03-capability-gap.md)“按风险分批替换”“分离数据控制面与执行面”。
- 每项启用前建立 allowlist、事件处置、资源上限、租户隔离、fence 失效与故障恢复测试；启用后删除对应通用重复实现，不保留双路径。[03-capability-gap.md](03-capability-gap.md)“验收标准”。

**验收判据：**

- 大工具结果不会完整进入下一轮 prompt，且可按授权回读；历史会话只能按 tenant/user/task policy 检索。
- Plan/async/Skill/`@path` 的新增事件全部进入显式处置矩阵；未授权工具和路径 fail closed。
- 不可信代码只在真正隔离的 sandbox 执行；生产路径不再把宿主 `ProcessBuilder` 当安全边界。[03-capability-gap.md](03-capability-gap.md)“真正隔离的 sandbox 与快照”。
- 临时 subagent 与 bus 故障不改变 TaskBoard/outbox 的唯一事实源；旧 fence 的工具、文件、事件与任务写入全部失败。
- 每个能力都有独立启用开关、SLO 和回退演练；开关只选择单一路径，不做双写兼容。

**预估影响范围：****25～50 个生产/测试/配置文件**，按低/中/高风险三批拆分。规模依据 [03-capability-gap.md](03-capability-gap.md) 10 项缺失能力及 filesystem/sandbox/subagent/gateway 的代表类群。

**风险与回退：**高风险批涉及安全隔离、任务恢复和跨 Agent 消息，必须由人类审核后实施。每项以默认关闭的能力开关独立启用；触发 SLO、隔离或数据一致性门槛时关闭该能力并回到已验证的 Harness 基线，而不是回到 AAF 平行实现。

## 可观测切换判据

以下阈值是决策门，不是对当前事实的描述。任一信号达到阈值即创建新 ADR 重新评估；达到“立即动作”条件时停止依赖升级或回切对应路线。

> 其中「Harness 默认表面持续漂移」与「上游提供稳定最小模式」两行以 `HarnessAgent` 为执行面为前提，ADR-005 议题三改判后已失效；ADR-005 自带的 [Reversal Triggers](../../adr/ADR-005-agentscope-boundary-and-orchestration.md) 是当前的回切判据。其余各行仍适用。

| 可观测信号 | 明确阈值 | 动作方向 |
| --- | --- | --- |
| Harness 默认表面持续漂移 | 任一候选升级出现 **1 个未授权 tool/middleware**，或连续 **2 个上游版本**无法通过最终 Toolkit/middleware 契约 | 暂停升级；若 core 契约测试仍通过，评估从路线三切到路线二。当前存在 Wait/Inbox 默认注入的事实基线见 [01-reuse-map.md](01-reuse-map.md)“Harness 默认开启项与 AAF 实际结果”。 |
| 企业刚需无法经公开扩展点实现 | 对 tenant、fence、持久任务任一项完成一次不超过 **10 个生产类/两周**的 spike 后，仍必须修改 **3 个及以上 Harness internal/private 类**或使用同包 shadow 才能满足 fail-closed | 切路线二；若限制来自 core 而非 Harness，则直接评估路线一。公开扩展面基线见 [03-capability-gap.md](03-capability-gap.md)“超越项最终判定”。 |
| Jackson/ABI 冲突扩散 | 除当前 `JsonSchemaUtils` 外又需要第 **2 个重复 binary-name shadow**，或 fork 连续落后上游 **2 个发布版本**且无法合并安全/缺陷修复 | 停止路线三/二升级并评估路线一。当前只有一个 shadow 且风险可静默漂移，见 [04-reuse-correctness.md](04-reuse-correctness.md)“升级与运行风险”。 |
| 上游维护停滞 | AgentScope core/harness 连续 **9 个月无正式发布且无修复提交**，同时 AAF 存在一个已复现 blocker/CVE/Java 25 或 Spring Boot 4 兼容问题超过 **30 天**无可接受补丁 | 若 core 仍可由小 fork维护，路线三转路线二；否则转路线一。 |
| fork 成本失控 | 每次上游升级需要维护超过 **500 行非 Jackson 兼容补丁**，或最近 **3 次升级中有 2 次**需要修改 AAF 业务 adapter 才能恢复同一契约 | 启动路线二 PoC；若变更来自 core Model/Msg/Tool/State ABI，则启动路线一 PoC。 |
| Harness 原生能力收益不足 | 第三个原生能力完成迁移后，仍有 **2 项及以上**无法替代 AAF 通用重复实现，或维护类数量没有净减少 **10 个** | 停止后续原生能力迁移，评估路线二；不因单项失败立即推翻已稳定能力。重复能力基线见 [04-reuse-correctness.md](04-reuse-correctness.md)“重复能力清单”。 |
| 运行可靠性回归 | 任一版本在故障注入中出现 **1 次双终态、跨租户/旧 fence 写入或重复副作用**，或生产同类事件非零 | 立即回退该阶段；问题若由 Harness 内部且无法通过公开扩展点修复，触发路线二评估。可靠性基线见 [02-runtime-quality.md](02-runtime-quality.md) RQ-01、RQ-05、RQ-08～RQ-09。 |
| Spring AI 替代成熟 | Spring AI 路线 PoC 同时覆盖 ReAct、中断恢复、类型化事件、state、tool suspend，且 [02-runtime-quality.md](02-runtime-quality.md) P0/P1 契约全绿；预计删除 AgentScope 专属生产类 **不少于 15 个**，新增自研 loop 类 **不超过 8 个** | 只有同时满足才允许从路线三/二切到路线一，避免仅为生态一致性重造 Harness。 |
| 上游提供稳定最小模式 | AgentScope 正式版本提供关闭 workspace/filesystem/message bus/Inbox/Wait 的公开开关，且连续 **2 个版本**通过 AAF surface 契约 | 删除 fork 中对应补丁并继续路线三；这是留在路线三的正向判据。[01-reuse-map.md](01-reuse-map.md)“中期收敛到 core ReAct”提出的 `minimalMode` 方向。 |

## 后续动作

### 🔴 需人类审核后才能动代码

> 首条已由 [ADR-005](../../adr/ADR-005-agentscope-boundary-and-orchestration.md) 取代（实际定案为路线二执行面骨架 + 扩展点迁移，见[被 ADR-005 改判的部分](#被-adr-005-改判的部分)）；其余四条仍待审核。

- ~~批准路线三及“执行面/企业控制面”边界~~——已由 ADR-005 定案，不再按本文路线三批准。
- 批准 AgentScope 独立坐标最小 fork、整体替换官方 core/harness、删除 `JsonSchemaUtils` shadow，并确定上游 PR 与退出 fork 的责任人。[04-reuse-correctness.md](04-reuse-correctness.md)“推荐方案”。
- 批准 RQ-01 终态协议、execution idempotency、AgentState 身份/恢复语义；这些变化影响持久状态、事件与恢复合同。[02-runtime-quality.md](02-runtime-quality.md) RQ-01、RQ-05、RQ-08～RQ-09。
- 批准 HITL/Permission、Role-Skill runtime、AG-UI protocol、filesystem/sandbox、subagent/message bus 的每个切换批次；这些改动涉及安全、接口或跨模块真理源。[03-capability-gap.md](03-capability-gap.md)“按风险分批替换”。
- 每个阶段进入开发前按高风险流程产出 design/tasks，并由与 developer 不同模型的 architect 审查。

### 可直接执行的低风险项

- 为 59 项能力表、31 项事件处置、最终 Toolkit/middleware surface 建只读清单与契约测试设计，不改变运行路径。[01-reuse-map.md](01-reuse-map.md)“立即收紧运行边界”；[04-reuse-correctness.md](04-reuse-correctness.md)“AgentEvent 与 AgentEventType”。
- 将 RQ-01～RQ-13、10 项缺失、7 项可迁扩展点、8 项重复能力改造项拆成 backlog 候选并标注依赖，不修改代码。
- 建立 AgentScope 上游发布、最后提交日期、fork diff 行数、契约测试失败数与未授权 surface 数的观测看板，为切换判据提供数据。

### ADR 落地结果

已落地为 [ADR-005：AgentScope 复用边界与双层编排模型定案](../../adr/ADR-005-agentscope-boundary-and-orchestration.md)（2026-08-31，status `proposed`）。本文初版建议的编号 ADR-004 与文件名 `ADR-004-agentscope-runtime-boundary.md` **未采用**；ADR 索引的编号不一致已在创建 ADR-005 时同步修正，该遗留提示不再有效。
