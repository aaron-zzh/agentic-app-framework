---
level: Practice
layer: Model
purpose: 定义 L1 认知基础的状态分区、核心组件、受控上下文契约与用户理解
status: draft
version: 1.1.1
date: 2026-08-25
author: Kiro
tags:
  - L1 Cognition
  - 受控上下文
  - 状态分区
dependencies:
  - ../architecture.md
related:
  - ./memory.md
  - ./retrieval.md
  - ./learning.md
scope:
  includes:
    - L1 的职责边界与被动响应约束
    - 状态分区与隔离规则
    - ContextRequest 契约与披露、预算边界
    - 用户理解与偏好的异步提炼
    - 有效上下文透明度
  excludes:
    - 记忆读写管道细节（见 memory.md）
    - 混合检索算法（见 retrieval.md）
    - 学习候选治理（见 learning.md）
    - 上下文如何装配进提示词（属 L0）
gains:
  - 能按 ContextRequest 请求受控上下文并预判返回形态
  - 能判断某项内容应存入哪个分区
  - 能说明用户如何查看并纠正本次实际使用的上下文来源
---

# L1 认知基础

## 定位与边界

L1 管理记忆、知识、价值观与决策依据，为助理与智能体提供回忆、检索、上下文组织与学习沉淀。

- **持久级，被动响应**：只响应授权请求或既有事实事件，不主动发起认知活动
- **受控访问**：不把知识库或记忆作为开放业务工具暴露
- **单一真理源**：知识、记忆与执行上下文可引用组装，不复制成可独立修改的副本
- **不授予权限**：画像、偏好、记忆命中和知识命中均不能扩大调用方权限
- **不持有会话责任**：会话焦点与任务推进归 L3；L1 只保存被治理的持久认知

上游主流程、分层职责与执行反哺原则见 [architecture.md](../architecture.md)。

## 领域模型

### 核心内容

| 内容 | 含义 | 真理源 |
|---|---|---|
| 记忆 | 主体经历、稳定偏好与可复用经验 | 记忆存储；分类与治理见 [memory.md](memory.md) |
| 知识 | 经授权共享的资料与事实 | 知识库；L1 只持引用与检索结果 |
| 价值观 | 不可越过的伦理与优先级约束 | 已发布规则，不由学习过程直接改写 |
| 决策依据 | 决策点、备选项、理由摘要、置信度与证据引用 | 审计事实，不保存私有思维链 |
| 用户理解 | 从受治理事件提炼的画像与偏好候选 | 用户画像版本，不替代原始事件与记忆 |

### 状态分区

| 分区 | 归属 | 允许内容 | 隔离键 | 生命周期 |
|---|---|---|---|---|
| 个人私有 | 用户或访客 | 个人记忆、偏好、画像 | `tenantId + subjectKind + subjectId` | 用户持久；访客必须 TTL |
| 全局共享 | 租户或组织 | 已发布知识、共享规则 | `tenantId + organization/workspace` | 版本化持久 |
| 执行工作区 | 任务 | 本次任务材料、局部摘要与引用 | `tenantId + taskId + executionId` | 任务级，可回收 |
| 审计留存 | 平台 | 决策依据、门禁结果与来源清单 | `tenantId + taskId + eventOffset` | 按合规策略保留 |

不变量：跨分区只能保存稳定引用；不得把个人内容复制到共享分区；审计留存不得保存凭证、原始正文或私有思维链。

### 用户画像与偏好

| 画像维度 | 允许提炼内容 | 禁止推断或用途 |
|---|---|---|
| 表达偏好 | 语气、语言、回复长度、信息密度、格式 | 不作为权限或风险豁免依据 |
| 专业与兴趣 | 领域熟悉度、稳定关注主题 | 不推断敏感身份属性 |
| 行为模式 | 常用能力、活跃时段、交互方式 | 不跨租户追踪，不做隐藏式行为操控 |
| 设备与可用性 | 用户显式允许的端偏好、无障碍需求 | 不持久化无必要的设备指纹 |
| 情绪交互模式 | 用户明确表达或多次验证的沟通偏好 | 瞬时情绪不得固化为人格标签 |
| 资源偏好 | 质量/速度/成本的显式取舍 | 不改变平台、租户与任务硬预算 |

偏好采用版本化证据模型：

```text
PreferenceClaim = {
  preferenceKey, value, sourceKind, sourceRefs[],
  confidence, observedAt, effectiveFrom, expiresAt,
  status: CANDIDATE | CONFIRMED | REJECTED,
  revision
}
```

冲突优先级：用户本次显式设置 > 用户已确认偏好 > 较新的验证证据 > 推断候选。低置信或单次行为只能保留为候选。

## 契约

### 受控上下文

L3 通过 `ContextRequest` 请求上下文；L1 按已授权候选、主体、用途、披露方式与预算返回 `ControlledContextSnapshot`。

| 要素 | 契约 | 实现态 |
|---|---|---|
| 身份 | `tenantId`、`userId`、`taskId`、`executionId`、`assistantId`、`memorySubject` 必须一致 | ⚠️ 部分实现 · 字段非空且 `memorySubject.tenantId = tenantId` 已校验（`ContextRequest.java:21-65`）；`userId` 与 `memorySubject.subjectId` 的对应关系，以及 task/execution/assistant 的归属关系由上游构造保证，L1 未独立校验 |
| scope | `MEMORY` / `KNOWLEDGE` / `TASK_MATERIAL`；携带材料或知识计划时必须声明对应 scope | ✅ 已实现 · `ContextRequest.java:67-105` |
| disclosure | `SUMMARY_ONLY` / `CONTENT_ALLOWED`；Coordinator 与 Aggregator 只能请求摘要 | ✅ 已实现 · `ContextRequest.java:55-63`、`ContextRequest.java:121-129` |
| budget | `maxItems` 为 1..32，`characterBudget` 为 128..8192 | ✅ 已实现 · `ContextRequest.java:131-143` |
| 授权候选 | 任务材料与知识库 ID 必须属于 `authorizedCandidates` | ✅ 已实现 · `ContextRequest.java:77-105` |
| 返回 | 消息、稳定来源引用、scope digest、冻结时间；空命中返回空快照 | ✅ 已实现 · `DefaultL1ContextCollaborator.java:38-83` |

节点隔离目标：Coordinator 默认只获规划摘要；Executor 只获本节点目标、依赖结果与最小上下文；Aggregator 只获聚合所需摘要。当前只强制 Coordinator/Aggregator 使用 `SUMMARY_ONLY`，`ContextRequest` 不携带节点标识或依赖边界，Executor 的材料隔离依赖上游 `authorizedCandidates` 预先收窄：⚠️ 部分实现 · `ContextRequest.java:55-105`。L1 动态内容只能使用 USER 消息角色，不得伪装 SYSTEM 指令（`DefaultL1ContextCollaborator.java:190-208`）。

### 用户理解与个性化

| 触发 | 输入 | 输出 | 时序 |
|---|---|---|---|
| 显式设置或纠正 | 用户确认的偏好变更 | `CONFIRMED` 偏好版本 | 同步校验、异步持久化 |
| 任务终态尾随 | 已持久化的完成、失败、验证与反馈事实 | 画像或偏好候选 | 不阻塞任务终态 |
| 批量提炼 | 达到数量或时间阈值的同主体事件 | 合并候选、漂移提示 | 异步有界批处理 |
| 周期治理 | 过期、低置信或冲突候选 | 降权、过期或待确认状态 | 定时治理 |

治理边界：只消费允许的脱敏事件；保存证据引用而非复制轨迹正文；敏感属性默认不推断；用户可查看、纠正、删除；画像变更不得影响已冻结的执行画像，只作用于后续运行。

实现态：🎯 目标态，当前未发现 Cognition 用户画像证据模型、提炼器与治理入口，当前不得声称已执行。

### 有效上下文透明度

`EffectiveContextManifest` 是某次任务实际生效上下文的不可变引用清单，不复制源正文。

| 层级 | 字段 | 约束 |
|---|---|---|
| 清单 | `taskId`、`assistantId`、`assistantRevision`、`roleKey`、`sources[]`、`createdAt` | 每个任务在租户内唯一；历史清单不随偏好修改 |
| 来源 | `type` | `RULE` / `SKILL` / `TASK_MATERIAL` / `KNOWLEDGE` / `MEMORY` |
| 来源 | `sourceKey`、`version`、`scope` | 稳定定位源与生效版本，不携带凭证 |
| 来源 | `reason` | 说明为何被选入本次上下文 |
| 来源 | `summary` | 只允许脱敏摘要，最大 256 字符 |
| 来源 | `userManageable` | 服务端按所有权、强制性与合规策略计算，不信任调用方声明 |

对外展示只包含：任务与助理版本、Role、生成时间、来源类型、脱敏名称或摘要、scope、版本、选中原因和允许的管理动作。不得展示原始正文、内部 Prompt、凭证、私有思维链或未授权标识。

可管理来源判定：

| 来源 | 可管理条件 | 不可管理条件 |
|---|---|---|
| `RULE` | 无 | 平台、租户与已发布 Role 硬规则始终不可管理 |
| `SKILL` | 用户有管理权且绑定为可选 | 强制 Skill、任务合同要求或无管理权 |
| `TASK_MATERIAL` | 用户提供且移除不破坏已冻结合同；只影响后续运行 | 系统材料、完成证据或已冻结必需材料 |
| `KNOWLEDGE` | 用户拥有或可管理该绑定 | 组织强制知识、仅有读取权 |
| `MEMORY` | 归属当前用户且非审计留存 | 他人、组织共享或法定留存 |

偏好键为 `tenantId + userId + assistantId + sourceType + sourceKey`，取值为 `DEFAULT / PREFERRED / DISABLED / REMOVED`。偏好只影响后续解析；`DISABLED/REMOVED` 不得回写或篡改历史清单。

实现态：⚠️ 部分实现 · 字段、不变量与脱敏摘要已实现（`EffectiveContextManifest.java:11-63`）；清单持久化、偏好排序及禁用/移除过滤已实现（`JpaEffectiveContextAdapter.java:39-136`，`ContextSourcePreferenceRepository.java:8-21`）。缺口：主链把全部 `command.contextCandidates` 与实际 L1 引用合并后交给清单解析，已授权但未命中或未进入字符预算的候选仍可能被记录为“生效来源”（`AssistantApplicationService.java:385-395`）；无对外查询/管理 API；`userManageable` 由候选直接携带，尚未由服务端按上表判定。因此当前清单只能视为“经偏好过滤的候选与命中引用合集”，不得对外声称是严格的实际使用清单。

## 实现态

| 契约 | 实现态 |
|---|---|
| 受控上下文按 scope、披露与双预算返回 | ✅ 已实现 · `ContextRequest.java:21-190`、`DefaultL1ContextCollaborator.java:25-286` |
| 节点级上下文隔离 | ⚠️ 部分实现 · Coordinator/Aggregator 的 `SUMMARY_ONLY` 已强制（`ContextRequest.java:55-63`）；Executor 的材料与依赖隔离仍依赖上游预先收窄 `authorizedCandidates`，请求本身无节点/依赖边界（`ContextRequest.java:77-105`） |
| 本会话短期上下文接入 L1 | 🎯 目标态，`MemoryRecallPort` 无会话维度（`MemoryRecallPort.java:11-26`），当前不得声称已执行 |
| 记忆检索并行抽象已消除 | ✅ 已实现 · `MemoryRetrievalService`/`UnifiedRetrievalService` 已删除，收敛为 `MemoryRetrievalPort` + `UnifiedRetrievalPort` 两层门面（见 [memory.md](memory.md)、[retrieval.md](retrieval.md)） |
| 四分区隔离 | ⚠️ 部分实现 · 个人记忆按 tenant/subject 隔离（`MemoryRecord.java:11-55`），任务材料按任务与执行身份授权（`ContextRequest.java:21-105`）；缺少统一的共享、工作区、审计分区模型与跨分区写入门禁 |
| 用户画像与偏好证据模型 | 🎯 目标态，当前不得声称已执行 |
| 有效上下文清单可持久化并应用偏好 | ✅ 已实现 · `JpaEffectiveContextAdapter.java:39-136` |
| 有效上下文对用户可见且管理性由服务端判定 | 🎯 目标态，当前不得声称已执行 |

## 验收基线

- L1 不暴露开放的知识或记忆业务工具，所有运行时访问经 `ContextRequest`
- 同一内容不在两个分区各存一份可独立修改的副本
- Coordinator、Executor、Aggregator 只能取得各自用途允许的最小上下文
- 画像候选能追溯证据、置信度与版本，且不能授予权限或改写已冻结运行
- 用户能查询历史有效上下文清单；偏好修改只影响后续运行
- `userManageable` 由服务端判定，强制规则和无管理权来源不可被禁用
