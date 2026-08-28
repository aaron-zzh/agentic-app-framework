---
level: Practice
layer: Model
purpose: 定义记忆分类、读写管道、短期与长期与图谱记忆的边界及记忆治理
status: draft
version: 1.1.1
date: 2026-08-25
author: Kiro
tags:
  - 记忆
  - 读写管道
  - 记忆治理
dependencies:
  - ../architecture.md
  - ./cognition.md
related:
  - ./retrieval.md
  - ./learning.md
scope:
  includes:
    - 记忆分类与各自生命周期
    - 读管道（可编排检索）与写管道（固定持久化）
    - 短期、长期、情景、程序化与图谱记忆的边界
    - 记忆治理：去重、冲突、遗忘
  excludes:
    - 混合检索的通道与融合算法（见 retrieval.md）
    - 学习候选的审核门禁（见 learning.md）
    - 受控上下文契约（见 cognition.md）
gains:
  - 能判断一条信息应写入哪类记忆及其生命周期
  - 能说明一次回忆经过读管道的哪些阶段
  - 能识别当前哪类记忆尚未接入主链
---

# 记忆

## 定位与边界

记忆是 L1 的主体私有持久认知。**读可编排，写固定治理**：回忆按请求和预算选择通道，持久化必须经过统一顺序。

- Agent 不持有长期记忆，只通过 L1 借用受控上下文
- 原始会话消息与任务工作态归 L3/L2；L1 只接收受治理的摘要或候选
- 情景 bundle 与图谱是记忆的检索视图或投影，不得成为可独立修改的第二真理源
- 稳定、可复用且能改变执行方式的程序化经验应进入学习候选；发布为 Skill 后以 SkillVersion 为能力真理源

## 领域模型

### 记忆分类

| 类别 | 内容 | 生命周期 | 真理源与边界 | 实现态 |
|---|---|---|---|---|
| 短期 | 当前会话的压缩摘要与最近交互引用 | 会话 TTL | 原始消息仍归会话；不得复制成无期限长期记忆 | ✅ 已实现 · Redis 存储 `ShortTermMemoryService.java:21-88`，经 `SessionMemoryPort` 接入读管道（`RedisSessionMemoryAdapter`、`DefaultMemoryContextCollaborator`）；执行完成时按 MemoryMode 写入本轮交互 |
| 长期 | 跨会话稳定事实、偏好与重要决定 | 持久或显式过期 | 按 tenant 与主体隔离 | ✅ 已实现 · `JpaCognitionMemoryAdapter.java:39-70` |
| 情景 | 带时间、参与者、因果与证据链的经历片段 | 持久，可衰减 | 由原子记忆与关系形成 bundle，不单独复制正文 | ✅ 已实现 · bundle 检索经 `MemoryRetrievalPort` 接入 L1 主链（`DefaultMemoryRetrievalPort.java`、`AtomMemoryEngine.java:31-39`） |
| 程序化 | “如何做/避免什么”的经验候选 | 持久、版本化 | 记忆只保存经验；可执行能力发布到 SkillVersion | ✅ 已实现 · scope 检索经 `MemoryRetrievalPort` 接入 L1 主链（`DefaultMemoryRetrievalPort.java`）；专用入口仍返回空（`MemoryRetrievalService.retrieveProcedural` 已随旧类删除，等价能力未单独重建），非本轮范围 |
| 图谱 | 记忆实体、关系与时序连接 | 投影可重建 | 只作关系检索索引，原子记忆仍是真理源 | ⚠️ 部分实现 · Neo4j 存取存在（`GraphMemoryService.java:17-65`），未接入任一层门面，非本轮范围，单独排期 |

### 记忆记录

权威长期记忆记录采用 `MemoryRecord`：

| 字段 | 约束 |
|---|---|
| `memoryId` | 稳定标识；写入幂等键应防止同一候选重复落库 |
| `subject` | `tenantId + USER/VISITOR + subjectId`；访客必须 TTL |
| `content` / `redactedSummary` | 正文仅存权威记录；对外引用只返回最长 256 字符脱敏摘要 |
| `importance` / `confidence` | 0..1；重要性用于保留策略，可信度用于写入门禁 |
| `privacy` | `PUBLIC / PERSONAL / SENSITIVE / SECRET`；`SECRET` 禁止自动沉淀 |
| `tags` | 包含分类与 `scope:*` 标签，不替代主体隔离 |
| `expiresAt` / `createdAt` | 支持 TTL、遗忘与审计 |

实现态：✅ 已实现 · `MemoryRecord.java:11-152`。

## 契约

### 读管道

唯一上游入口仍是 [cognition.md](cognition.md) 的 `ContextRequest`。记忆内部检索由 `MemoryRetrievalPort` 提供单一入口；跨源编排（线索识别、预算分配、融合、重排）由 [retrieval.md](retrieval.md) 的 `UnifiedRetrievalPort` 统一负责。

| 阶段 | 输入 | 输出 | 判定规则 |
|---|---|---|---|
| 线索识别 | `subject`、query、任务用途、可选会话引用 | `RetrievalIntent`：语义/时序/因果/程序化/关系 | 优先确定性规则；需模型时只能使用非自主 L0 调用 |
| 预算分配 | 上游剩余条数、字符、时限与 intent | 每通道 `quota + timeout + required` | 配额总和不得超过上游预算；程序化和图谱按意图启用 |
| 并行检索 | 已授权主体、线索与通道计划 | 分通道候选、失败与耗时 | 通道相互隔离；超时停止；失败必须显式记录，不静默扩大其他通道权限 |
| 重排 | 候选、来源、时效、置信度与权重 | 去重后的有序候选 | 先融合再重排；模型重排是非自主 L0，受调用预算约束 |
| 格式化输出 | 有序候选与剩余字符预算 | `MemoryContext(messages, references)` | 只输出脱敏摘要与稳定引用；截断不得破坏引用对应关系 |

#### 单一编排结论

生产主链现为两层门面：记忆自身只暴露一个检索入口 `MemoryRetrievalPort`（内部按原子、情景 bundle、程序化三通道并行检索并返回候选，不做跨源预算分配、不做跨源融合），跨源编排由 [retrieval.md](retrieval.md) 的 `UnifiedRetrievalPort` 统一负责（意图分类、跨通道预算切分、并行调用 `MemoryRetrievalPort` 与知识检索、RRF 融合、融合后重排）。记忆通道只对上一层门面负责，不持有跨通道预算、融合与重排真理源。

```text
L1ContextPort
  → DefaultL1ContextCollaborator（scope 编排与总预算；短期会话与任务材料前置直接注入）
    → UnifiedRetrievalPort（跨源编排：意图分类、预算切分、并行、RRF 融合、重排）
      ├─ MemoryRetrievalPort（记忆自有检索入口：原子 / 情景 bundle / 程序化三通道）
      └─ HybridSearchService（知识库自有检索入口）
```

短期会话上下文与任务工作记忆不经检索决策，由 `DefaultL1ContextCollaborator` 前置直接注入（见「短期会话上下文经 L1 返回」实现态行），不纳入 `MemoryRetrievalPort`/`UnifiedRetrievalPort` 的检索范围。

2026-08-28 完成收敛：新建 `MemoryRetrievalPort`（迁移 `MemoryRetrievalService` 的原子、情景 bundle、程序化三通道检索逻辑，不含意图分类与预算分配）与 `UnifiedRetrievalPort`（迁移意图分类规则、预算切分、RRF 融合、接入 `MemoryRerankerService` 做融合后重排），`DefaultL1ContextCollaborator` 改为统一调用 `UnifiedRetrievalPort`；`MemoryRetrievalService` 与 `UnifiedRetrievalService` 两个旧并行门面已整体删除。**图谱记忆通道不在本轮范围内**，图谱仍未接入任一层门面，单独排期。

实现态：✅ 已实现 · 主链经 `UnifiedRetrievalPort` 统一编排短期前置注入之外的检索（`DefaultL1ContextCollaborator.java`、`DefaultUnifiedRetrievalPort.java`、`DefaultMemoryRetrievalPort.java`），回归测试见 `DefaultL1ContextCollaboratorTest`、`DefaultUnifiedRetrievalPortTest`、`DefaultMemoryRetrievalPortTest`；图谱通道未接入，仍为目标态。

### 写管道

自动学习只接受通过 [learning.md](learning.md) 门禁的记忆候选；用户显式“请记住”可直接形成已确认候选，但仍不能跳过隐私、去重与冲突检查。

| 固定步骤 | 输入 | 输出 | 判定规则 |
|---|---|---|---|
| 提取 | 已确认输入或已审核学习候选 | 原子 `Candidate(content,tags,eventAt)` | 每条只表达一个事实；提取阶段不得写库；模型失败即不产生候选 |
| 去重 | 候选 + 同主体相关记忆 | `ADD / DUPLICATE / CONFLICT_REQUIRES_CONFIRMATION` | 先规范化精确去重，再语义近邻；冲突不得当作新增 |
| 写入 | `ADD` 且门禁通过的评估结果 | `MemoryRecord` + 写入回执 | 按候选/事件幂等；主体、隐私、置信度和证据不可丢失 |
| 遗忘 | 写入回执 + 保留策略 | TTL、降权、失效或保留决定 | 写后异步执行；访客强制 TTL；用户删除需显式确认；高价值不等于永久保留 |

顺序固定为 `提取 → 去重/冲突 → 写入 → 遗忘`。可配置项仅限提取策略、阈值与保留策略，不得重排或跳过步骤。

实现态：⚠️ 部分实现 · `MemoryGovernanceService.learn` 已按提取、评估、去重/冲突、写入执行（`MemoryGovernanceService.java:30-69`）；访客 TTL 和显式遗忘已实现（`MemoryGovernanceService.java:50-66`、`JpaCognitionMemoryAdapter.java:119-176`）。缺口：写后遗忘/衰减未形成固定第四步，自动 `learn` 仍可绕过 `learning.md` 定义的通用学习候选（记忆候选类型化分流仍是目标态）。旧并行写入口 `MemoryExtractionService`/`MemoryDeduplicationService`（完全脱离本文治理规则、零生产调用方、fail-open 默认 `ADD`）已删除，不再是可注入风险。

### 记忆治理

| 治理项 | 契约 | 当前实现 |
|---|---|---|
| 去重 | 同主体内先标准化精确匹配，再检索语义近邻；等价内容返回既有 ID | ⚠️ 向量召回候选中的标准化精确匹配已实现；语义等价判定未实现（`RuleBasedMemoryGovernanceAdapter.java:70-81`） |
| 冲突 | 同一受治理主题出现相反或不兼容事实时进入用户确认，不覆盖 | ⚠️ 仅偏好/决策标签和简化否定词规则已实现（`RuleBasedMemoryGovernanceAdapter.java:82-105`） |
| 隐私 | 疑似凭证为 `SECRET` 并拒绝；摘要脱敏手机号、邮箱和密钥 | 已实现（`RuleBasedMemoryGovernanceAdapter.java:41-67`、`RuleBasedMemoryGovernanceAdapter.java:116-126`） |
| 可信度 | 自动写入至少 `confidence >= 0.70`、`importance >= 0.55` | 已实现（`MemoryGovernanceService.java:16-18`、`MemoryGovernanceService.java:35-43`） |
| 遗忘 | 访客 TTL、用户显式删除、过期清理、访问/价值衰减；审计留存独立治理 | ⚠️ TTL 与显式删除已实现；访问/价值衰减为目标态（`JpaCognitionMemoryAdapter.java:119-195`） |
| 仲裁 | `DUPLICATE` 不写；`CONFLICT` 等待确认；只有 `ADD` 可自动写 | ⚠️ 内置适配器当前只产生 `ADD/DUPLICATE/CONFLICT_REQUIRES_CONFIRMATION`；服务对未来 `REPLACE` 决策未 fail-closed，会落入写入分支（`MemoryGovernanceService.java:43-66`、`MemoryRecord.java:118-123`） |

隐私或可信度门禁失败时必须拒绝，不允许 fallback 为新增。原 `MemoryDeduplicationService` 在模型调用异常与解析失败时均默认 `ADD`，不符合本契约；该类零生产调用方且与 `RuleBasedMemoryGovernanceAdapter` 并行重复，已整体删除，不再作为权威写管道候选。

### AgentScope 整合边界

历史 `LongTermMemory`、`StaticLongTermMemoryHook` 与 `LongTermMemoryTools` 方案不再采用。AAF 在调用 Harness 前通过 L1 组装受控上下文，Harness 只消费冻结输入；不得自行检索、记录或持有长期记忆。

- 禁用 AgentScope memory tools 与 memory hooks
- 禁用 workspace context、子智能体、动态 Skill、文件与 Shell 旁路
- 记忆写回由 AAF 学习候选与固定写管道触发，不绑定模型一次回复的 PostCall Hook

实现态：✅ 已实现 · `AgentScopeSpecCompiler.java:104-153`。

## 实现态

| 契约 | 实现态 |
|---|---|
| 权威长期记忆召回与双预算裁剪 | ✅ 已实现 · 权威召回见 `JpaCognitionMemoryAdapter.java:39-70`，条数与字符裁剪见 `DefaultMemoryContextCollaborator.java:14-63` |
| 短期会话记忆经 L1 返回 | ✅ 已实现 · `SessionMemoryPort` 为记忆读管道的一个通道，非独立 scope；`RecallQuery` 已含 `sessionId` 维度，协调与聚合用途强制置空以隔离用户会话历史（`ContextRequest`） |
| 情景 bundle 接入唯一读管道 | ✅ 已实现 · `MemoryRetrievalPort` 统一入口（`DefaultMemoryRetrievalPort.java`） |
| 程序化记忆接入唯一读管道 | ✅ 已实现 · `MemoryRetrievalPort` 统一入口（`DefaultMemoryRetrievalPort.java`） |
| 图谱记忆作为可重建投影接入唯一读管道 | 🎯 目标态，非本轮范围，当前不得声称已执行 |
| 五阶段读管道 | ✅ 已实现 · 线索识别、预算分配、融合、重排统一在 `UnifiedRetrievalPort`（`DefaultUnifiedRetrievalPort.java`），`MemoryRetrievalPort` 负责通道并行检索（`DefaultMemoryRetrievalPort.java`） |
| 固定四步写管道 | ⚠️ 部分实现 · `MemoryGovernanceService.java:30-69`；缺固定遗忘阶段与候选先行 |
| 隐私、可信度、去重与冲突治理 | ⚠️ 部分实现 · 隐私与阈值门禁已生效（`RuleBasedMemoryGovernanceAdapter.java:41-67,116-126`、`MemoryGovernanceService.java:16-43`）；去重仅覆盖标准化精确匹配、冲突仅覆盖简化规则，且未来未知仲裁决策未 fail-closed（`RuleBasedMemoryGovernanceAdapter.java:70-105`、`MemoryGovernanceService.java:43-66`） |
| 记忆读写各自只有一个生产编排入口 | ✅ 已实现 · 读管道收敛为 `MemoryRetrievalPort`（记忆自有）+ `UnifiedRetrievalPort`（跨源编排），`MemoryRetrievalService`/`UnifiedRetrievalService` 已删除；写管道单一入口 |
| 关闭 AgentScope 内建记忆 | ✅ 已实现 · `AgentScopeSpecCompiler.java:104-153` |

## 验收基线

- 任一记忆能定位类别、主体、分区、证据与生命周期
- 自动学习结果未经候选门禁无法进入长期记忆
- 读管道记忆侧只有 `MemoryRetrievalPort` 一个检索入口，跨源编排只有 `UnifiedRetrievalPort` 一个门面，`MemoryRetrievalService`/`UnifiedRetrievalService` 不再作为并列门面存在
- 写管道严格执行提取、去重/冲突、写入、遗忘，任一步失败不 fallback 为新增
- 情景 bundle 与图谱可从权威记忆重建，不形成第二真理源
- 回忆结果只含脱敏摘要与稳定来源引用，可被授权用户查看、纠正或遗忘
