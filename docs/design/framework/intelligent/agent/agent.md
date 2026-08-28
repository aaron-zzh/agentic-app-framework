---
level: Practice
layer: Model
purpose: 定义 L2 智能体的四种执行身份、任务循环合同、执行端口与 Harness 运行边界
status: draft
version: 1.1.0
date: 2026-08-25
author: Kiro
tags:
  - L2 Agent
  - 执行身份
  - TaskLoopContract
  - Harness
dependencies:
  - ../architecture.md
  - ../runtime.md
related:
  - ../assistant/coordination.md
  - ../action-governance.md
scope:
  includes:
    - L2 的职责边界与状态约束
    - 四种执行身份的职责分离
    - TaskLoopContract 的冻结项
    - AgentExecutionPort 契约与 Harness 运行边界
    - 关闭的旁路能力与子智能体解析
  excludes:
    - 跨节点编排与聚合（见 assistant/coordination.md）
    - 工具交集与动作放行（见 action-governance.md）
    - 事件对外投影（见 runtime-event.md）
gains:
  - 能为一个子任务选择正确的执行身份并冻结其循环合同
  - 能说明内层 ReAct 循环与外层任务循环的责任边界
  - 能列出必须关闭的 Harness 旁路能力及原因
---

# L2 智能体

## 定位与边界

> L2 接收 L3 指派的明确任务，将目标拆成可验证步骤，调用获准工具执行并反馈过程。
> 任务级，只保留执行期工作状态；无人格、无长期记忆，不得扩大 Role、grant 或工具边界。

AAF 外层任务循环与 Harness 内层 ReAct 的责任边界由 [五层智能架构](../architecture.md#双层循环) 定义。L2 不持有跨节点编排、最终交付或用户会话责任。

## 领域模型

### 四种执行身份

| 身份 | 理解与规划 | 执行 | 验证与升级 |
|---|---|---|---|
| `PRIMARY` | 理解用户目标、约束、交付与成功标准；决定直答、TaskBoard 或 Team | 冻结任务、交付和聚合合同 | 验证整体结果并向用户交付；披露假设和未验证项 |
| `COORDINATOR` | 只基于冻结目标识别阻塞项 | 生成严格计划；不调用业务工具、不生成最终业务内容 | 校验候选、Schema 与无环依赖；非法时 fail-closed |
| `EXECUTOR` | 理解单一子目标，不扩大上级目标 | 执行下一项最小行动并观察结果，可在预算内修复 | 返回结果、证据、假设与阻塞；越界或重复失败时升级 |
| `AGGREGATOR` | 核对冻结结果集合与聚合合同 | 透传、拼接或归并；不补造未执行内容 | 验证覆盖和格式；缺失必需结果时报告 |

`COORDINATOR`、`EXECUTOR`、`AGGREGATOR` 不得绕过上级直接向用户追问。

### 任务循环合同

```text
TaskLoopContract
- key · version · agentKind · goal · phases · allowedActions
- completion · stop · evidence · maxIterations
```

所有自主 Harness 节点共享：

```text
Understand → Resolve Gaps → Plan → Execute → Verify
→ Deliver / Iterate / Escalate
```

每个节点必须冻结独立、版本化合同；角色文本、完成条件和迭代预算散落在多个对象中不构成完整合同。

当前代码中不存在 `TaskLoopContract` 类型或同名字符串。其职责分散在：`InvocationPolicy.java:4-36`（身份指令与合同版本）、`ExecutionPolicy.java:12-30`（Harness 迭代、模型重试、超时与上下文窗口）、`ExecutionContract.java:10-161`（动作、停止条件、任务预算与接管）、`CompletionCriteria.java:10-47`（完成证据），以及 `TaskBoard.java:992-997`（外层迭代停止原因）。这些对象各自生效，但尚无统一 key/version、phase、证据和停止语义的节点级合同。

### 子智能体规格

```java
sealed interface SubagentSpec {
    record Predefined(AgentId agentId, long version) implements SubagentSpec {}
    record Dynamic(
        String name,
        String description,
        String systemPromptFragment,
        List<ToolRef> tools,
        ExecutionPolicy executionPolicy,
        ModelSelectionRequirement modelRequirement,
        boolean inheritParentTools
    ) implements SubagentSpec {}
}
```

| 类型 | 来源 | 生命周期 | 模型与工具 |
|---|---|---|---|
| `Predefined` | `ai_agent_definition` 的精确 `agentId@version` | 定义可复用；按完整执行画像缓存 Harness | 定义提供技术上限，仍与冻结画像取交集 |
| `Dynamic` | L3 根据当前 Assistant、Role 与已激活 Skill 现场构造 | 不落库；委托态每次编译并在终止后释放 | 模型显式传入；工具不得继承扩权 |

`ai_agent_definition` 只作为治理型预定义模板目录，不是每次执行的必经路径。缺失 Predefined 定义时 fail-closed；**“定义降级”表示表职责降级，不表示运行时从 Predefined 静默 fallback 到 Dynamic。**

## 契约

### 执行端口与 Harness

领域与应用层只依赖 `AgentExecutionPort`：

```text
AAF 编译不可变 Prompt、Model、Toolkit、Context 与迭代上限
→ AgentExecutionPort.execute(command)
→ Harness ReAct 推理与工具请求
→ ToolGateway 放行后返回 Observation
→ 节点结果或规范停止原因
```

替换内层引擎不得改变 `AgentExecutionCommand`、外层任务状态、授权、事件和完成门禁。

### 子智能体调用前解析

AAF 自持子智能体解析发生在调用 Harness **之前**：

```text
L3 / TaskBoard 选择节点身份与目标
→ 冻结 Role、ActivatedSkill、模型、有效工具、预算与授权
→ 构造 SubagentSpec + AgentExecutionCommand
→ 订阅 execute() 时校验 Prompt 与租约
→ resolveExecution(SubagentSpec)
   ├─ Predefined：按 agentId@version 读取 AgentSpec；缺失即失败
   └─ Dynamic：使用冻结模型与画像现场编译
→ 创建单个叶子 HarnessAgent 执行
```

解析不允许：按“最新定义”漂移、缺失时换类型、从父 Agent 读取未冻结配置、运行中修改 Toolkit/System Prompt、用共享可变 Agent manager 覆盖并发请求。

### 与关闭原生子智能体的边界

| AAF 自持解析 | AgentScope 原生/动态子智能体 |
|---|---|
| 外层 TaskBoard 创建、调度和恢复独立节点 | Harness 内部通过 `agent_spawn` 递归创建节点 |
| AAF 冻结 `SubagentSpec`、租约、预算、授权与事件谱系 | AgentScope manager/factory 持有定义与生命周期 |
| 每个节点编译为一个叶子 HarnessAgent | 父 HarnessAgent 可在 ReAct 中自主扩张 |
| **允许，由 AAF 外层持有** | **关闭，禁止启用** |

因此“AAF 子智能体定义解析”与“关闭 AgentScope 原生子智能体”不矛盾：前者是 AAF 外层编排的调用前编译，后者是禁止 Harness 内部旁路委派。

### 必须关闭的旁路能力

| 旁路能力 | 关闭原因 | 当前关闭点（Dynamic / Predefined） |
|---|---|---|
| 原生子智能体 / 动态子智能体 | 任务身份、恢复、授权、预算与事件谱系归 AAF | `AgentScopeSpecCompiler.java:145-146 / 184-185` |
| 内建长期记忆 | 记忆统一归 L1 | `AgentScopeSpecCompiler.java:141-142 / 180-181` |
| 内建工作区 | 工作态归 AgentState，产物经获权工具 | `AgentScopeSpecCompiler.java:143-144 / 182-183` |
| 动态 Skill | Skill 版本与授权必须经 AAF 审核 | `AgentScopeSpecCompiler.java:147-148,154 / 186-187,193` |
| 文件与 Shell | 副作用必须经 ToolGateway | `AgentScopeSpecCompiler.java:150-151 / 189-190` |
| 默认工具配置与上下文压缩旁路 | 有效工具与上下文预算必须由冻结画像决定 | `AgentScopeSpecCompiler.java:149,152-153 / 188,191-192` |

`ExecutionMode` 只决定 Dynamic 规格的 Harness 实例策略：`DIRECT` 走可复用缓存，`DELEGATE` 走现场编译并在结束后释放（`HarnessAgentExecutionAdapter.java:396-414`）。持久任务租约与可继续执行校验由 `InvocationContext.controlMode == DELEGATED` 独立控制（`HarnessAgentExecutionAdapter.java:420-430`）。因此 `DELEGATE` 不等于开启 Harness 多 Agent 委派，也不单独证明节点受外层持久任务控制。

### 生命周期与缓存

- Predefined 缓存键必须包含 `agentId`、定义版本、有效工具和完整 Prompt 指纹；画像变化即新条目。
- Dynamic 直答可按完整不可变画像缓存；Dynamic 委托态不缓存，完成、失败、取消和同步异常均释放。
- `RuntimeContext` 只承载租户、主体、会话、租约、grant 与观测调用态，不能重配置 Harness。
- 取消只作用于当前 `executionId` 的活跃句柄，不能跨节点传播为隐式全局取消。

### 无新进展停止

“模型停止输出”不是完成，“仍可继续调用”也不是继续的充分条件。满足任一条件必须停止并返回原因：

| 条件 | 结果 |
|---|---|
| 连续两轮未增加新证据、未改变计划状态、未减少未满足完成条件 | `NO_PROGRESS`，升级上级 |
| 重复同一工具与等价参数且 Observation 无变化 | `NO_PROGRESS`，禁止第三次盲重试 |
| 达到迭代、时间、Token、动作或重试预算 | 对应预算停止原因 |
| 需要不可替代输入、授权或越界决策 | `BLOCKED`，返回上级而非直接问用户 |
| 完成证据满足 | 交由外层 CompletionValidator 判定，不由模型自报完成 |

进展判定使用结构化节点状态、工具 receipt、产物版本和完成证据，不使用思维链文本相似度。

## 实现态

| 契约 | 实现态 |
|---|---|
| 四身份与调用策略分离 | ✅ 已实现 · `InvocationProfile.java:14-193`、`InvocationPolicy.java:4-36`、`ContextDisclosurePolicy.java:10-63` |
| Coordinator 与 Aggregator 的最终工具集强制为空 | ✅ 已实现 · 身份判定见 `AssistantApplicationService.java:1302-1306`，解析后强制置空见 `AssistantApplicationService.java:1336`；空列表经 `AgentScopeToolkitFactory.java:31-45` 编译为空 Toolkit |
| `AgentExecutionPort` 单一依赖 | ✅ 已实现 · `AgentExecutionPort.java:11-18` |
| `SubagentSpec` Predefined / Dynamic 领域模型 | ✅ 已实现 · `SubagentSpec.java:9-70` |
| Assistant 调用前构造 Dynamic 规格并冻结到命令 | ✅ 已实现 · `AssistantApplicationService.java:1523-1565,1915-1946`、`AgentExecutionCommand.java:10-102` |
| 调用前按类型解析，Predefined 缺失 fail-closed | ✅ 已实现 · `HarnessAgentExecutionAdapter.java:369-414` |
| 关闭 AgentScope 子智能体及其他旁路 | ✅ 已实现 · Dynamic 路径 `AgentScopeSpecCompiler.java:141-154`，Predefined 路径 `AgentScopeSpecCompiler.java:180-193` |
| `ExecutionMode` 仅控制 Dynamic 缓存 / 临时编译策略 | ✅ 已实现 · `AgentExecutionCommand.java:42-45`、`HarnessAgentExecutionAdapter.java:396-414`；持久委托校验另由 `HarnessAgentExecutionAdapter.java:420-430` 的 `controlMode` 控制 |
| Dynamic 委托态临时实例释放 | ✅ 已实现 · `HarnessAgentExecutionAdapter.java:236-339,455-467` |
| `inheritParentTools` | ⚠️ 部分实现 · 字段已存在于 `SubagentSpec.java:31-42`，编译器在 `AgentScopeSpecCompiler.java:96-99` 明确拒绝；不得声称支持继承 |
| `TaskLoopContract` 按身份版本化冻结 | 🎯 目标态 · 当前不存在该类型；职责分散于 `InvocationPolicy.java:4-36`、`ExecutionPolicy.java:12-30`、`ExecutionContract.java:10-161`、`CompletionCriteria.java:10-47` 与 `TaskBoard.java:992-997`，当前不得声称已形成统一合同 |
| 无新进展即停止 | ⚠️ 部分实现 · `TaskBoard.IterationStopReason.NO_PROGRESS`（`TaskBoard.java:993-999`）+ `IterationState.lastIterationResults/unchangedStreak` 结构化比对（`TaskBoard.java:404-462`）已实现"连续两轮成员结果无变化即停止"；"同一工具+等价参数+Observation 无变化禁止第三次盲重试"（单 SubTask 级重复调用检测）仍不得声称已执行，需要逐次尝试的 observation 历史 |

## 验收基线

- 每个执行节点可定位身份、`SubagentSpec`、循环合同版本、精确模型、工具与预算。
- Predefined 缺失不降级，Dynamic 不落库，二者都不能在运行中扩大画像。
- AAF 外层可以创建多个叶子节点，但任一 Harness 内无法启用原生或动态子智能体。
- 下级身份只能向上返回澄清或升级建议，不能直接向用户追问。
- 连续无实质进展触发结构化停止原因，不以盲目重试消耗预算。
- 替换内层 Agent 引擎不需要修改外层任务合同。
