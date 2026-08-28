---
level: Practice
layer: Model
purpose: 定义执行结果反哺的候选生成、产出分流与版本化审核门禁
status: draft
version: 1.1.1
date: 2026-08-25
author: Kiro
tags:
  - 学习反哺
  - 候选治理
  - 版本化
dependencies:
  - ../architecture.md
  - ./cognition.md
related:
  - ./memory.md
scope:
  includes:
    - 学习触发时机与输入来源
    - 候选生成与产出分流
    - 隐私、可信度、去重与冲突门禁
    - 版本化与发布约束
  excludes:
    - 记忆写管道的持久化细节（见 memory.md）
    - Skill 版本发布流程（见 skill/skill.md）
    - 自进化的代码重生成（见 auto-dev）
gains:
  - 能判断一次执行的哪些产出可进入学习候选
  - 能说明候选经过哪些门禁才写入权威真理源
  - 能识别学习结果为何不能直接改写运行时
---

# 学习反哺

## 定位与边界

学习反哺把已持久化执行事实转为**版本化候选**，经治理后才进入记忆、知识或能力定义。

- 学习不直接改写生产定义、已冻结执行画像或当前运行
- 成功、失败、人工干预和验证事实均可成为候选来源，但模型输出本身不是可信事实
- 候选只保存脱敏摘要、类型化负载与证据引用，不复制完整工具轨迹、凭证或私有思维链
- 影响 Skill、编排、规则、授权或引擎的变更必须人工审核并走各自真理源的发布流程

执行反哺原则与九步主流程见 [architecture.md](../architecture.md)。

## 领域模型

### 输入来源

| 输入来源 | 可采集内容 | 前置条件 |
|---|---|---|
| 执行结果 | 终态、产物引用、完成证据 | 已写入执行事实源；完成结果已验证 |
| 用户反馈 | 评分、纠正、采纳、拒绝 | 主体与目标对象已确认 |
| 工具轨迹 | 工具名、结果代码、耗时、脱敏观察摘要 | 参数与结果已脱敏，不保存凭证 |
| 决策记录 | 决策点、备选项、理由摘要、门禁结果 | 不包含私有思维链 |
| 人工干预 | 授权、拒绝、修改与接管结果 | 使用审计事件引用 |

### 候选信封

现行通用信封包含任务与事件身份、来源种类、安全摘要、证据引用、状态、审核信息和 revision。类型化目标负载在该信封之上扩展：

```text
LearningCandidateEnvelope = {
  candidateId, schemaVersion,
  tenantId, taskId, executionId, runId, sessionId,
  sourceEventId, sourceEventOffset, sourceKind,
  targetType, payloadRef, payloadHash,
  summaryCode, safeSummary, evidenceRefs[],
  gateResults[], status, reviewerId, decisionCode,
  createdAt, updatedAt, revision
}
```

`sourceEventId + targetType + payloadHash` 构成目标候选幂等键。`payloadRef` 指向受控候选负载，不得把敏感正文嵌入公共事件或安全摘要。

现行实现：⚠️ 部分实现 · 基础身份、证据、状态与 revision 已实现（`LearningCandidate.java:14-146`）；已用 `(tenantId, sourceEventId)` 唯一约束和查询实现“每个来源事件一个通用候选”的过渡幂等（`LearningCandidateEntity.java:19-24`、`JpaLearningCandidateAdapter.java:35-69`）。缺口：无 `targetType`、负载引用/哈希与门禁结果，因此同一事件尚不能安全分流多个目标候选。

### 候选状态

```text
DRAFT → REVIEWED → PUBLISHED
  └──────────────→ REJECTED
REVIEWED ─────────→ REJECTED
```

`PUBLISHED` 只表示候选治理完成；真正影响后续执行还必须由目标真理源创建新版本并发布。已发布或拒绝候选不可原地回退，修正必须新建候选。

实现态：✅ 已实现 · `LearningCandidate.java:56-116`、`LearningCandidateApplicationService.java:101-145`。

## 契约

### 触发时机与预算

| 触发方式 | 用途 | 执行约束 | 实现态 |
|---|---|---|---|
| 同步尾随 | 从已持久化终态、HITL、验证事实创建轻量 DRAFT | 每个源事件最多一个信封；只做安全投影与持久化；异常不改变任务终态 | ⚠️ 部分实现 · 事件监听、来源白名单、异常隔离及 `(tenantId, sourceEventId)` 过渡幂等已实现（`LearningCandidateApplicationService.java:41-99`、`JpaLearningCandidateAdapter.java:35-69`）；缺可靠补偿，且幂等粒度尚不能支持同一事件分流多个目标类型 |
| 异步批处理 | 聚合同主体、同目标或同模式候选，执行评估与蒸馏 | 使用持久游标；每批有条数、时限、Token 与模型调用上限；失败可重放 | 🎯 目标态，当前不得声称已执行 |
| 阈值触发 | 高频成功模式、重复失败、冲突率或用户反馈达到阈值时生成类型化候选 | 阈值按 tenant + targetType + 时间窗统计；单次异常不得触发能力变更 | 🎯 目标态，当前不得声称已执行 |

默认预算合同：同步尾随不得调用模型；异步批次最多 100 个信封、最多 2 次非自主 L0 调用，达到 30 秒或租户 Token 预算即停止并保存游标。阈值和预算必须配置化并随候选记录版本，不得静默放宽。

### 产出分流

| 候选类型 | 类型化字段 | 分流判定 | 目标真理源 | 审核 | 实现态 |
|---|---|---|---|---|---|
| 知识候选 | `knowledgeBaseId`、`factKey`、`statementRef`、`validTime`、`confidence`、`evidenceRefs` | 可跨主体复用、是可验证事实、拥有目标知识库写权限 | 知识库新事实/版本 | 目标库策略决定；共享事实默认人工 | 🎯 目标态，当前无类型化知识候选，当前不得声称已执行 |
| 记忆候选 | `MemorySubject`、`memoryType`、`contentRef`、`tags`、`eventAt`、`importance`、`confidence`、`privacy`、`expiresAt` | 主体私有经历、稳定偏好或个人经验；不得分流到共享知识 | 长期、情景或程序化记忆 | 显式“记住”可自动；推断与冲突需确认 | 🎯 目标态，当前自动 `MemoryGovernanceService.learn` 可直接写入，未经过通用候选，当前不得声称已执行 |
| Skill 候选 | `skillId`、`baseVersion`、`changeSetRef`、`expectedGain`、`riskLevel`、`evidenceRefs` | 多次验证的可复用执行方法，包含明确输入输出与完成条件 | SkillVersion 草稿 | 必须人工 | 🎯 目标态，当前无类型化 Skill 候选，当前不得声称已执行 |
| 编排候选 | `profileId`、`baseVersion`、`planPatchRef`、`metricsBeforeAfter`、`riskLevel` | 多任务证据显示稳定改善 owner/process/coordination 决策 | 编排或任务配置新版本 | 必须人工 | 🎯 目标态，当前无类型化编排候选，当前不得声称已执行 |
| 规则/引擎建议 | `targetId`、`baseVersion`、`proposalRef`、`riskLevel` | 涉及价值观、权限、安全或引擎行为 | 仅形成审查建议 | 强制人工 | 🎯 目标态，当前无类型化规则/引擎建议，当前不得声称已执行 |

Skill、编排、规则与引擎候选发布后也不得直接修改运行中对象；必须由对应模块校验 baseVersion、生成新版本并走发布流程。

### 六道门禁

候选按以下顺序执行，任一步拒绝即停止后续自动发布并记录结果：

```text
隐私 → 可信度 → 去重 → 冲突 → 版本 → 审核
```

| 门禁 | 通过条件 | 不通过动作 | 当前实现 |
|---|---|---|---|
| 隐私 | 无凭证；敏感信息已脱敏；分区与用途允许 | 拒绝或转人工，不保留敏感正文 | ⚠️ 记忆路径有凭证识别和摘要脱敏（`RuleBasedMemoryGovernanceAdapter.java:41-67`、`RuleBasedMemoryGovernanceAdapter.java:116-126`）；通用候选仅消费安全事件投影，尚无类型化负载隐私门禁 |
| 可信度 | 来源事实已持久化，证据充分，分数达类型阈值 | 保留 DRAFT 或拒绝 | ⚠️ 通用候选已校验来源事件真实持久化且身份边界一致（`JpaLearningCandidateAdapter.java:71-88`）；记忆阈值已实现（`MemoryGovernanceService.java:16-43`）；证据充分性与各类型阈值未统一 |
| 去重 | 幂等键和语义比较均无等价候选/真理源记录 | 合并证据或引用既有记录 | ⚠️ 通用候选已有 `(tenantId, sourceEventId)` 唯一约束与创建前查询（`LearningCandidateEntity.java:19-24`、`JpaLearningCandidateAdapter.java:35-69`）；记忆仅实现标准化精确去重（`RuleBasedMemoryGovernanceAdapter.java:70-81`）；目标幂等键与语义等价仍缺失 |
| 冲突 | 与当前有效版本兼容，或仲裁已完成 | `CONFLICT_REQUIRES_CONFIRMATION`，禁止覆盖 | ⚠️ 记忆仅实现偏好/决策简化冲突规则（`RuleBasedMemoryGovernanceAdapter.java:82-105`），其他类型未实现 |
| 版本 | `schemaVersion` 合法，baseVersion 未过期，revision CAS 成功 | 拒绝陈旧发布并重新评估 | ⚠️ `schemaVersion >= 1`、领域 revision 与持久化 CAS 已实现（`LearningCandidate.java:14-55`、`JpaLearningCandidateAdapter.java:91-117`）；合法 schema 注册表和目标真理源 baseVersion 未纳入 |
| 审核 | 满足类型审核策略，审核者身份与决定完整 | 保持 DRAFT/REVIEWED 或转 REJECTED | ⚠️ 状态迁移、非空审核者/决定和 revision 检查已实现（`LearningCandidate.java:56-116`、`LearningCandidateApplicationService.java:101-145`）；无候选类型审核策略，也未校验审核者授权身份 |

重要性是记忆保留与分流评分，不是第七道通用门禁。

### 发布约束

- 候选、门禁结果和目标版本必须可审计、可重放、可回滚
- 知识与记忆发布只能写各自分区，禁止个人内容自动升级为全局共享
- Skill 与编排候选必须由对应真理源生成新版本；候选状态不能替代目标发布状态
- 规则、价值观、权限、模型策略与引擎级变更强制人工审核
- 新版本只作用于后续执行；已冻结执行画像不回溯变化

## 实现态

| 契约 | 实现态 |
|---|---|
| 从执行事实生成通用 DRAFT 候选 | ✅ 已实现 · `LearningCandidateApplicationService.java:41-99` |
| 候选先行、不直接改写运行时 | ⚠️ 部分实现 · 通用候选不会自动改 Role/Skill/Prompt（`LearningCandidateApplicationService.java:25-39`）；自动记忆学习仍直接写库（`MemoryGovernanceService.java:30-69`） |
| 同步尾随失败不改变任务终态 | ✅ 已实现 · `LearningCandidateApplicationService.java:41-57` |
| 异步批处理与阈值触发 | 🎯 目标态，当前不得声称已执行 |
| 知识、记忆、Skill 与编排类型化候选 | 🎯 目标态，当前不得声称已执行 |
| 六道门禁作用于所有候选类型 | ⚠️ 部分实现 · 记忆路径覆盖隐私、可信度、去重与简化冲突（`RuleBasedMemoryGovernanceAdapter.java:21-128`、`MemoryGovernanceService.java:30-69`），通用候选覆盖 revision CAS 与审核状态（`JpaLearningCandidateAdapter.java:91-117`、`LearningCandidateApplicationService.java:101-145`）；尚未串成作用于所有类型的统一门禁链 |
| 记忆治理规则化 | ✅ 已实现 · `RuleBasedMemoryGovernanceAdapter.java:21-128`、`MemoryGovernanceService.java:30-69` |
| Skill/编排候选经目标真理源版本化发布 | 🎯 目标态，仅有蒸馏接口占位（`ProceduralDistiller.java:10-34`），当前不得声称已执行 |

## 验收基线

- 任一学习产出在入库前可查候选信封、类型化负载引用与六道门禁结果
- 同一源事件重放不会产生重复候选或重复发布
- 未经目标真理源版本化发布的候选不影响任一次执行
- 凭证、敏感正文和私有思维链不会进入候选、安全摘要或公共事件
- 与既有真理源冲突的候选进入仲裁，不静默覆盖
- 个人记忆候选不能自动分流到全局知识，Skill/编排/规则变更必须人工审核
