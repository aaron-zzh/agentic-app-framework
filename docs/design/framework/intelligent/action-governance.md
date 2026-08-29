---
level: Practice
layer: Model
purpose: 定义有效工具交集、动作放行门禁、人工干预与审查、授权与预算及置信度门控
status: draft
version: 1.0.2
date: 2026-08-25
author: Kiro
tags:
  - 工具治理
  - HITL
  - 授权
  - 置信度门控
dependencies:
  - ./architecture.md
  - ./runtime.md
related:
  - ./skill/skill-tool-resolution.md
scope:
  includes:
    - 工具分层与基础工具档案
    - 有效工具的逐层交集公式
    - ToolGateway 的动作放行门禁链
    - HITL 同步干预与异步人工审查
    - 任务级授权 grant、预算与置信度门控
  excludes:
    - Skill 侧的工具声明与激活（见 skill/skill-tool-resolution.md）
    - 执行意图中的授权策略字段定义（见 runtime.md）
    - 积分、结算与配额计费（见 engine/governance/）
gains:
  - 能计算某次执行的有效工具集并解释每层为何排除某工具
  - 能判断一次动作应自动放行、请求确认还是拒绝
  - 能区分能力可见与动作获权两件事
---

# 动作治理

> **能力可见不等于动作获权。** 工具进入模型上下文只表示可选择；每次调用仍须通过控制策略、任务授权、主体权限与参数策略。
> 模型、Skill 与前端**不能签发授权**，只能在已有授权内决定是否调用。

## 工具分层

| 层 | 示例 | 默认风险 |
|---|---|---|
| 基础只读工具 | Web 搜索、受控网页读取、知识检索、计算、时间 | 低 |
| Skill 专属工具 | 行业数据查询、格式转换、内容审校 | 由目录定义 |
| 产物工具 | 创建草稿、更新草稿版本、登记 AIGC 素材 | 可撤销写 |
| 外部动作工具 | 发布、发送、支付、删除、修改外部系统 | 高或不可逆 |

基础工具不是“所有通用工具”。`BaseToolProfile` 必须**版本化**，只包含无副作用且参数受控的能力。网络工具需要域名策略、SSRF 防护、响应大小与超时限制，并把外部内容视为**不可信数据**。

实现态：⚠️ 部分实现 · `BaseToolProfile` 已落地为版本化常量清单（`BaseToolProfile.java`，`VERSION=1`），收录 4 个 `ai_tool_catalog` 中 `risk_level=LOW` 且 `read_only=TRUE` 的工具；网络工具域名策略、SSRF 防护、响应大小与超时限制未落地，当前清单不含网络工具。

## 有效工具交集

```text
CandidateTools = BaseToolProfile ∪ SkillAllowedTools

EffectiveTools = CandidateTools
  ∩ RoleCapabilityCeiling
  ∩ AssistantToolPolicy
  ∩ AgentDeclaredTools
  ∩ RequestToolCeiling
  ∩ ExecutionContract.allowedActions（存在时）
  ∩ SubjectAndTenantPolicy
  ∩ ControlModePolicy
```

工具要求区分 required 与 optional：**required 被任一层排除时在执行前明确失败**，optional 被排除时只记录决策，不阻断执行。

Skill 的工具访问模式必须保留到运行时，不得用裸空集合同时表达“禁止全部”与“未限制”：

```text
RESTRICT + 空集合       → 仅基础工具
RESTRICT + 声明工具     → 基础工具 + 声明工具
INHERIT                 → 基础工具 + Role 业务工具
```

`INHERIT` 只适用于经过审核的系统 Skill；用户 Skill 默认 `RESTRICT`。

> ⚠️ 部分实现 · 解析器处理 Skill required、Role/Assistant allowlist、Agent tools 三组并入 `BaseToolProfile`（`DefaultEffectiveToolResolver.java`）。三态语义简化为布尔开关（`ActivatedSkill.inheritRoleTools()`，不新增枚举类型）：`RESTRICT`（false）+ 空集合 → 仅 `BaseToolProfile`；`RESTRICT` + 声明工具 → `BaseToolProfile` + 声明工具；`INHERIT`（true）→ `BaseToolProfile` + Role/Assistant 与 Agent 交集全部业务工具，跳过 Skill 必需工具限制。`toolAccessMode` 到 `inheritRoleTools` 的映射与发布门禁见 `skill-tool-resolution.md`。`BaseToolProfile` 恒定并入候选集，但仍需工具本身在 Agent 声明范围内且通过 Role/Assistant 白名单交集——不绕过 `RoleCapabilityCeiling`。

## 动作放行门禁链

每次工具调用按顺序通过以下门禁，任一不过即拒绝或转 HITL：

```text
可见性（是否在 EffectiveTools 内）
→ 策略（控制策略与风险分级）
→ grant（任务级授权是否覆盖该动作与资源）
→ 参数（Schema 与参数策略校验）
→ lease（执行租约有效性）
→ 幂等 receipt（是否已执行过）
→ 预算（Token、调用次数、时限）
```

实现态：⚠️ 部分实现 · 可见性、策略、grant、参数、lease 与写动作 receipt 已接线（`DefaultToolGateway.java:67-171`）；工具调用预算只在 `DELEGATED` 分支经 `DelegatedTaskPort` 预留/记账（`DefaultToolGateway.java:174-229`），非委派路径与 Token/时限预算未在该网关闭合。

`DIRECT` 只允许产生无副作用输出。需要业务写动作时必须进入 Agent execution，或进入由获权执行体经 ToolGateway 执行的 Workflow action node；**Controller 与 Workflow Service 都不能直接落业务副作用**。

## 人工干预与审查

### 同步 HITL

同步 HITL 在动作执行**之前**切换给人，支持确认、补参、暂停、继续、重试与取消。以下任一条件命中即同步阻塞：

- 缺少有效 grant、关键参数或凭证
- `0.7 ≤ confidence ≤ 0.9` 需要确认；`confidence < 0.7` 必须转人工
- 动作不可逆、无可靠补偿，或平台硬策略要求确认
- 权限、主体、资源、租户、预算或租约无法自动证明

任务级授权示例：

```text
actions:    content.draft.upsert, aigc.asset.register
resources:  artifact:task/{taskId}/*
scope:      TASK
reversible: true
expiresAt:  任务截止时间
```

实现态：⚠️ 部分实现 · ToolGateway 缺 grant 时会原子进入待审批并停止动作（`DefaultToolGateway.java:67-146`），审批批准/拒绝、窄授权与恢复已接线（`PersistentHitlCoordinator.java:72-190`）；通用补参、显式暂停/继续/重试/取消及下述三段置信度策略尚未在统一 ToolGateway 闭合。

### 异步人工审查

异步审查发生在动作或产出**已经安全执行/暂存之后**，用于质量纠偏，不签发权限、不替代同步 HITL，也不能把原本需确认的动作改成先执行后审查。

| 机制 | 正式契约 |
|---|---|
| 统一审查队列 | 所有 AI 产出归一为 `AiOutput`，至少记录 `taskId/executionId`、来源、类别、风险、内容安全引用/快照、回退信息与状态 |
| 风险提醒 | 高风险即时通知并标红；中风险定时摘要；低风险仅进入总览。**风险等级只决定通知优先级，不阻塞执行，也不构成授权** |
| 快速反馈 | 用户可查看、调整或回退；反馈必须记录操作者、原因、时间和关联执行事实 |
| 回退前提 | 仅在有完整 `revertInfo`、幂等补偿和当前资源版本校验时自动回退；否则转同步人工处理 |
| 审查超时 | 不自动“通过”、不扩大授权；暂存结果保持暂存，已生效可撤销结果保持原状态并继续可追溯 |
| 学习反馈 | 调整/回退只生成学习候选，不能直接改写 Role、Skill、Prompt 或权威知识 |

同步与异步分工：

| 问题 | 同步 HITL | 异步审查 |
|---|---|---|
| 何时发生 | 动作前 | 安全执行或暂存后 |
| 是否阻塞 | 是 | 否 |
| 决定什么 | 是否允许动作及其条件 | 产出质量、调整与补偿 |
| 适用范围 | 不可逆、高影响、缺授权/凭证、低/中置信 | 无副作用或可撤销且已获权的产出 |
| 真理源 | `HumanApproval` + `AuthorizationGrant` | `AiOutput` 审查记录；不得反向充当 grant |

实现态：⚠️ 部分实现 · 已有 `AiOutput` 记录、列表筛选、高风险通知、调整与回退入口（`AiOutputService.java:20-78`）；详情/调整/回退已强制按归属主体读取，越权与不存在统一按不存在处理（`AiOutputService.java:56-93`、`AiOutputController.java:37-56`）。尚未接入统一智能运行时、未实现中风险摘要，`revert` 只改状态不执行补偿。

## 置信度门控

### 二维模型

置信度先决定主导权，可验证性只决定自动校验与审查方式，**不得把低置信“升级”为自动执行**。

| 置信度 | 可验证 | 不可验证 |
|---|---|---|
| `> 0.9` | 自动执行 → 自动验证 → 结果暂存/提交；异步通知 | 仅无副作用或可撤销且已获权时执行 → 决策摘要 → 异步审查；否则同步确认 |
| `0.7 ≤ c ≤ 0.9` | 展示计划并等待确认；批准后执行并自动验证 | 展示计划、假设与风险并等待确认；批准后执行并异步审查 |
| `< 0.7` | 暂停、说明不确定性与验证方案，转人工 | 暂停、说明原因，转人工决策 |

统一边界为：**严格大于 0.9 才有资格自动执行；0.9 与 0.7 均落在确认区间**。这与协作红线一致，并否决历史设计中“低置信但可验证即可先执行回滚”的越级规则。

### 不可逆操作

以下动作无论置信度与可验证性如何都必须同步确认，且人类未响应时保持等待：

- 无可靠恢复点的删除、覆盖或数据迁移
- 发布、部署、对外发送、支付与外部系统提交
- 权限、密钥、访问策略或生产配置变更
- 代码/规范提交及其他平台标记为不可逆或高影响的动作

“可回滚”必须由补偿动作、版本前置条件和幂等 receipt 证明，不能由模型声明。

### 置信度来源

| 来源 | 契约 |
|---|---|
| 意图确定性 | 目标、约束、主体、资源与成功标准是否明确 |
| 规范匹配 | 输入与 Route、Schema、政策、完成合同的一致程度 |
| 证据充分性 | 检索证据的新鲜度、覆盖、冲突和来源可信度 |
| 历史校准 | 同任务族、模型、工具链的历史成功率下界；样本不足时不得抬高评分 |

最终置信度由服务端校准器产生，采用保守下界约束；模型自报分数只能作为一个输入，不能单独决定门控。动作风险与置信度保持正交：高置信不降低风险等级。

### 可验证性判定

仅同时满足以下条件才标为可验证：存在确定性 oracle；可在提交不可逆副作用前运行；失败可阻断或有已验证补偿；结果覆盖完成合同关键项。

| 可验证 | 不可验证 |
|---|---|
| 编译/单测、JSON Schema、签名/约束、确定性规则、可重放对账 | 创意质量、架构价值判断、用户体验、无客观 oracle 的语义正确性 |

部分可验证按关键未验证项处理：若未验证项影响副作用或完成条件，整体按不可验证门控。

### 防退化约束

- 人类未响应不自动超时执行，保持等待态
- 禁止静默降低阈值、风险、可验证性标准或完成条件
- 新证据可重新评分，但必须记录旧值、新值、来源与策略版本
- 主导权切换、确认、拒绝、回退与异步审查均形成审计事实
- 置信度缺失、越界、未校准或来源不可追溯时 fail-closed

实现态：⚠️ 部分实现 · 三段阈值已落地：`DefaultConfidenceGate.java:21-56` 按 `>0.9 / 0.7..0.9 / <0.7` 判定，不可逆动作强制确认，并已删除“低置信但可验证即可先执行回滚”的越级分支；两个调用方均把确认区间与转人工一并阻塞（`AiBusinessActionExecutor.java:151-165`、`ToolCallDispatcher.java:203-216`）。缺口：置信度仍由调用方传入而非服务端校准器产生，且未接入 `DefaultToolGateway` 的统一门禁链。

## 预算

| 预算类型 | 管辖范围 | 定义处 |
|---|---|---|
| 动作预算 | Token、调用次数、时限、并发 | 本文与 `ExecutionContract` |
| 分解预算 | 子节点数、并行数、深度、总节点、外层迭代 | [architecture.md](architecture.md) |
| Harness 迭代预算 | ReAct 迭代与模型重试 | `ExecutionPolicy` |

## 实现态

| 契约 | 实现态 |
|---|---|
| ToolGateway 七道门禁 | ⚠️ 部分实现 · 可见性、策略、grant、参数、lease 与写动作 receipt 已接线（`DefaultToolGateway.java:67-171`）；调用预算只在委派分支记账，非委派路径及 Token/时限预算未闭合（`DefaultToolGateway.java:174-229`） |
| 任务级可撤销窄授权 | ✅ 已实现 · `PersistentHitlCoordinator.java:132-190` |
| 同步 HITL 全过程控制 | ⚠️ 部分实现 · 授权请求、批准/拒绝与恢复已接线（`PersistentHitlCoordinator.java:72-190`）；通用补参、显式暂停/继续/重试/取消尚未统一闭合 |
| 工具三态与 `BaseToolProfile` | ⚠️ 部分实现 · `BaseToolProfile` 已落地（`BaseToolProfile.java`，4 个 LOW+read_only 工具：`listBusinessActions`/`list_workflows`/`recognizeOcr`/`queryWeather`），恒定并入候选集但仍受 `RoleCapabilityCeiling`/`AgentDeclaredTools` 交集约束，不绕过 Role 白名单；`toolAccessMode` 简化为布尔开关 `ActivatedSkill.inheritRoleTools()`（不新增枚举类型），`RESTRICT`（false）维持原交集逻辑，`INHERIT`（true）跳过 Skill 必需工具限制直接放行 Role/Assistant 与 Agent 交集，接线见 `DefaultEffectiveToolResolver.java`、`AssistantApplicationService` 的 `roleInheritRoleTools`/`assistantInheritRoleTools`；仍缺失的是网络工具域名/SSRF 策略等参数受控细节，本轮不做 |
| 八层交集在单一解析器内闭合 | ⚠️ 部分实现 · 三组在解析器内，其余在上层分散预过滤，`DefaultEffectiveToolResolver.java:27-38,62-66` |
| required / optional 区分 | 🎯 目标态 · 当前不得声称已执行；现有声明统一 fail-closed |
| 异步人工审查接入统一门禁 | ⚠️ 部分实现 · 独立 `AiOutput` 模块已存在，但未接入智能运行时，`AiOutputService.java:20-78` |
| 置信度门控作为运行时门禁 | ⚠️ 部分实现 · 二维接口存在，但阈值与统一门禁不符，`DefaultConfidenceGate.java:6-26` |

## 验收基线

- 有效工具集可由公式逐层复算，每个被排除的工具能定位到排除层
- 空集合不再同时表达“禁止全部”与“未限制”
- required 工具缺失在执行前失败，optional 缺失只留决策记录
- 任何业务副作用都经 ToolGateway，且留下 receipt 与审计事件
- 模型、Skill 与前端在任何路径下都无法扩大已有授权
- 高风险或不可逆动作在无人确认时不执行，且不因超时自动放行
- 风险等级只影响异步通知优先级，绝不替代同步授权和置信度门控
