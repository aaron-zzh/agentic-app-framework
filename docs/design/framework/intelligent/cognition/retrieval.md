---
level: Practice
layer: Model
purpose: 定义记忆与知识库的混合检索架构、通道权重、融合重排与引用返回
status: draft
version: 1.1.0
date: 2026-08-25
author: Kiro
tags:
  - 混合检索
  - 融合重排
  - 引用
dependencies:
  - ../architecture.md
  - ./cognition.md
related:
  - ./memory.md
scope:
  includes:
    - 检索双层架构与统一门面
    - 通道权重、预算分配与并行执行
    - 融合与重排策略
    - 引用与来源返回契约
  excludes:
    - 记忆分类与写入治理（见 memory.md）
    - 知识库引擎实现（见 engine/data-knowledge/）
    - 受控上下文的披露与预算（见 cognition.md）
gains:
  - 能说明一次检索的通道分配与融合过程
  - 能为新检索通道定义权重与预算
  - 能解释返回结果中每条引用的来源与被选原因
---

# 混合检索

## 定位与边界

混合检索是 L1 内部的统一检索能力：记忆与知识经同一门面规划通道、并行召回、融合、重排，再由 [cognition.md](cognition.md) 的披露与预算契约形成受控上下文。

- 上层只调用 `L1ContextPort.resolve(ContextRequest)`，不直接选择存储引擎
- 检索不作为开放业务工具暴露，不能绕过主体、知识绑定与来源授权
- 查询改写与重排属于纯计算或非自主 L0 调用，不创建 Agent、Harness Loop 或持久 Task
- 通道失败允许受控降级，但必须保留失败事实，不能改用未授权来源

## 领域模型

### 双层架构

```text
统一门面层
L1ContextPort
  → DefaultL1ContextCollaborator
    → UnifiedRetrievalPort（跨源编排门面）
       负责：授权校验、线索识别、ChannelPlan、并行、融合、重排、引用

通道层
  ├─ AtomicMemoryChannel       原子记忆语义/时序召回
  ├─ EpisodicBundleChannel     情景证据链 bundle
  ├─ ProceduralMemoryChannel   方法与流程经验
  ├─ KnowledgeChannel          已授权知识库混合检索
  └─ GraphChannel              实体关系与多跳扩展
```

统一门面返回候选与引用，不负责 Prompt 装配；通道只实现单一来源检索，不做跨源融合。新增通道必须实现同一候选契约，不得新增第二个上层门面。`UnifiedRetrievalPort` 为唯一门面，记忆侧对应的唯一检索入口为 [memory.md](memory.md) 定义的 `MemoryRetrievalPort`（内部并行原子、情景 bundle、程序化三通道，不做跨源预算与融合）；知识侧检索入口为现有 `HybridSearchService`。`DefaultL1ContextCollaborator` 统一调用 `UnifiedRetrievalPort`，不再分别调用 `MemoryContextPort` 与 `HybridSearchService`。旧 `UnifiedRetrievalService` 与 `MemoryRetrievalService` 两个未接主链的并行门面已整体删除，可复用的意图分类规则、预算分配公式与 RRF 融合逻辑迁移至 `UnifiedRetrievalPort`。**图谱通道非本轮范围**，单独排期。

### 检索计划

```text
ChannelPlan = {
  intent,
  channels: [{channel, quota, weight, timeout, required}],
  maxRewriteRounds,
  maxReflectionRounds,
  deadline
}
```

不变量：所有 quota 总和不超过 `ContextRequest.budget.maxItems`；每个通道先授权后执行；`required` 通道失败时整体失败，否则返回带降级说明的部分结果。

### 统一候选

| 字段 | 契约 |
|---|---|
| `candidateKey` | 来源内稳定且可去重的键 |
| `content` / `redactedSummary` | 内容仅在披露允许时返回；摘要必须脱敏 |
| `sourceType`、`sourceId`、`version`、`scope` | 可定位权威来源与版本 |
| `channel`、`rawRank`、`rawScore` | 保留通道原始排序证据 |
| `eventTime`、`validTime` | 支持情景与时态判断；无则显式为空 |
| `reason` | 说明命中、融合与最终选中原因 |
| `userManageable` | 由服务端策略计算，不由通道自行提权 |

## 契约

### 通道与预算

下表配额是通用策略上限，最终值必须受上游剩余预算、意图和通道可用性约束。

| 通道 | 适用场景 | 通用配额 | 实现态 |
|---|---|---|---|
| 原子记忆 | 稳定事实、偏好、语义近邻 | `min(6, remaining)` | ✅ 已实现 · `MemoryRetrievalPort` 提供检索，`UnifiedRetrievalPort` 统一分配跨源预算（`DefaultMemoryRetrievalPort.java`、`DefaultUnifiedRetrievalPort.java`） |
| 情景 bundle | 时序、因果、上次经历与证据链 | `min(4, remaining)` | ✅ 已实现 · `MemoryRetrievalPort` 提供检索并参与 `UnifiedRetrievalPort` 的融合 |
| 程序化记忆 | “如何做”、步骤、失败教训 | 默认 `min(2, remaining)`；程序化意图最高 5 | ✅ 已实现 · `MemoryRetrievalPort` 提供检索并参与融合；专用入口（旧 `retrieveProcedural`）未重建，非本轮范围 |
| 知识库 | 事实问答、文档依据、租户共享资料 | `min(knowledge.topK, remaining)` | ✅ 已实现 · 主链经 `UnifiedRetrievalPort` 调用 `HybridSearchService` |
| 图谱 | 实体关系、多跳依赖、关系补全 | 仅关系意图启用，`min(2, remaining)` | ⚠️ 部分实现 · 图谱存取存在（`GraphMemoryService.java:17-65`），未接统一门面，非本轮范围 |

预算分配顺序：保留任务必需材料 → 分配 required 通道 → 按 intent 与权重分配 optional 通道 → 未使用配额才允许回收。配额回收不得扩大来源权限。

### 并行执行

- 门面按 `ChannelPlan` 同时启动通道，使用结构化并发或等价的有界生命周期
- 每通道独立 timeout；达到总 deadline 后取消未完成任务
- 结果记录成功、空命中、超时、失败四态
- required 通道失败则返回可判定错误；optional 通道失败则输出降级说明
- 禁止“并行失败后无预算串行重跑”；重试必须计入同一 deadline 与调用预算

实现态：✅ 已实现 · `UnifiedRetrievalPort` 并行调用 `MemoryRetrievalPort` 与 `HybridSearchService`（`DefaultUnifiedRetrievalPort.java`）；异常降级仍计入同一 deadline 与调用预算，不做无界串行重跑；知识查询使用调用方已解析授权主体，不再使用 `AuthorizationSubject.unresolved()`。

### 融合与重排

跨通道先按稳定来源键去重，再用加权 RRF 融合：

```text
RRF(d) = Σ channelWeight(c) / (60 + rank_c(d))
```

| 阶段 | 规则 |
|---|---|
| 归一 | rank 从 1 开始；只纳入成功通道；不直接比较不同引擎 rawScore |
| 去重 | 相同权威来源版本合并命中通道与原因；不同版本不得误合并 |
| 融合 | `k=60`；权重来自冻结 `ChannelPlan`，总权重归一化 |
| 轻量重排 | 综合融合分、时效、可信度、重要性与词法覆盖；默认路径 |
| 模型重排 | 仅候选 2..20、预算允许且能获得专用模型时启用；属于非自主 L0 |
| 输出 | 重排必须作用于最终跨源候选；返回分数、来源、原因与降级信息 |

实现态：✅ 已实现 · 跨原子记忆、情景 bundle、程序化与知识的 `k=60` RRF 融合在 `UnifiedRetrievalPort`（`DefaultUnifiedRetrievalPort.java`）；重排调用时机为融合之后作用于最终跨源候选。专用模型门控复用现有 `MemoryRerankerService`（新增 `rerankContents` 通用重排方法，`MemoryRerankerService.java`），轻量默认 + 候选 2..20 门控 + 失败降级设计不变。

### Agentic 检索

仅在下列任一条件成立时启用增强检索：查询含多目标或指代不明、首轮关键通道空命中、候选冲突、关系问题需多跳扩展。简单明确查询不得调用模型改写。

| 预算 | 上限 |
|---|---|
| 查询改写 | 最多 2 轮，每轮最多 3 个并行查询 |
| 检索后反思 | 最多 1 轮，只判断“充分/缺口”，不生成最终答案 |
| 通道调用 | 所有轮次共享原始条数、字符、时限与模型调用预算 |
| 副作用 | 只读；不得调用业务工具、写记忆或修改知识 |
| 升级边界 | 不创建自主 Agent；需要独立租约、恢复或业务重试时才由 L3 建系统节点 |

实现态：🎯 目标态，当前没有接入统一 L1 门面的查询改写与检索后反思，当前不得声称已执行。

### 引用返回

每条最终结果必须返回：来源类型、稳定标识、版本、scope、命中通道、被选原因、脱敏摘要、管理性和降级状态。引用与内容一一对应；内容被字符预算截断时引用仍保留，未返回的候选不得伪装为已使用来源。

实现态：⚠️ 部分实现 · 当前 L1 返回来源类型、稳定键、版本、scope、原因、摘要与管理性（`DefaultL1ContextCollaborator.java:87-188`）；知识命中通道仅编码进摘要文本，记忆引用无命中通道，且 `SourceReference` 没有结构化降级状态（`EffectiveContextManifest.java:30-54`）。

## 实现态

| 契约 | 实现态 |
|---|---|
| 上游唯一入口 `L1ContextPort` 与基础来源引用 | ⚠️ 部分实现 · 主链入口已统一（`DefaultL1ContextCollaborator.java:25-286`）；引用缺结构化命中通道与降级状态 |
| 内部统一检索门面覆盖全部通道 | ✅ 已实现 · 覆盖原子、情景、程序化、知识四通道（`DefaultUnifiedRetrievalPort.java`）；图谱非本轮范围，仍为目标态 |
| 条数与字符双预算 | ✅ 已实现 · `ContextRequest.java:131-143`、`DefaultL1ContextCollaborator.java:38-83` |
| 多通道有界并行 | ✅ 已实现 · 接主链且降级纳入同预算约束（`DefaultUnifiedRetrievalPort.java`） |
| 加权 RRF 覆盖原子、情景、程序化、知识与图谱 | ✅ 已实现 · 覆盖原子、情景、程序化、知识；图谱非本轮范围 |
| 融合后轻量/模型重排 | ✅ 已实现 · 调用顺序为先融合再重排（`DefaultUnifiedRetrievalPort.java`） |
| Agentic 检索有界且非自主 | 🎯 目标态，当前不得声称已执行 |

## 验收基线

- 上层只能经 `L1ContextPort` 请求检索，不直接调用存储引擎或通道
- 每条返回结果可定位权威来源、版本、命中通道和被选原因
- 所有通道共享条数、字符、时限与模型调用预算，超限取消而非无界补偿
- RRF 先去重再融合，重排作用于最终跨源候选
- 查询改写和反思不创建自主 Agent、持久任务或业务副作用
- 无授权来源、unresolved subject 与超出任务 scope 的内容不能进入候选
