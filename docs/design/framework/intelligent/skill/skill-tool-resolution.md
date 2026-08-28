---
level: Practice
layer: Model
purpose: 定义 Skill 分层激活、两层技能合并、工具声明与解析结果冻结
status: draft
version: 1.2.0
date: 2026-08-25
author: Kiro
tags:
  - Skill 激活
  - 工具解析
  - 最小权限
dependencies:
  - ../architecture.md
  - ./skill.md
related:
  - ../action-governance.md
scope:
  includes:
    - Skill 作用域与分层激活规则
    - 角色与内置两层技能合并
    - Skill 侧工具声明、required/optional 与解析结果冻结
    - 激活与解析结果的冻结与审计
  excludes:
    - 有效工具的完整交集与调用门禁（见 action-governance.md）
    - Skill 版本与加载机制（见 skill.md）
gains:
  - 能预测一次执行的最终激活 Skill 集合
  - 能为一个 Skill 选择正确的工具访问模式
  - 能解释某个工具为何未进入模型上下文
---

# 技能激活与工具解析

## 定位与边界

> 能力按激活结果可见；工具仅从最终激活结果解析。未激活 Skill 不加载正文，也不暴露工具。

本文只定义 Skill 侧候选、激活和工具声明。有效工具的完整交集、任务 grant 与每次调用放行见 [动作治理](../action-governance.md)。

## 领域模型

### 绑定作用域与激活模式

| 作用域 | 来源 | 生效范围 |
|---|---|---|
| `SYSTEM` | 平台审核的系统绑定 | 当前平台策略允许的执行 |
| `ASSISTANT` | 助理绑定 | 当前 Assistant |
| `ROLE` | 角色绑定 | 本次选定 Role |

| 激活模式 | 行为 |
|---|---|
| `ALWAYS` | 绑定进入授权 Scope 后自动激活，不参与模型筛选 |
| `ON_DEMAND` | 仅元数据进入候选，由非自主 L0 结构化调用筛选 `0..N` |

Skill 的 `builtIn` 是目录来源，不是第四个作用域。作用域回答“谁绑定”，`builtIn` 回答“谁维护”，两者不得混为一套优先级。

### 解析结果

```text
SkillSelectionManifest
  → ActivatedSkill[]
  → SkillExecutionProfile(selection, activatedSkills, effectiveTools)
  → ExecutionProfileSnapshot
```

冻结项至少包含：`code`、`SkillVersionRef`、作用域、激活模式、正文哈希、工具/模型要求、reference keys、知识绑定 IDs、选择者与原因。

## 契约

### 两层技能合并

“两层”只指当前 Role 的**显式角色技能**与目录中的**内置通用技能**，不新增“助理专属目录”或 AgentScope Skill 路径：

```text
BuiltInFallback = 已发布、当前租户可见、builtIn=true 的 Skill
RoleExplicit    = 当前 Role.skillBindings 中引用的已发布 Skill
Merged          = overlay(BuiltInFallback, RoleExplicit, key=code)
Final           = Merged 按 code 升序形成不可变列表
```

| 规则 | 合同 |
|---|---|
| 候选边界 | 两层结果仍须与当前 SYSTEM / ASSISTANT / ROLE 授权 Scope 取交集；客户端请求不能新增候选 |
| 同 key 冲突 | `RoleExplicit` 覆盖 `BuiltInFallback`；覆盖的是精确 `SkillVersionRef` 与摘要，不合并正文、工具或知识范围 |
| Role 内重复 | 同一 Role 重复 `skillKey` 为配置错误，发布时拒绝 |
| 内置重复 | `code` 必须唯一；重复为目录错误，fail-closed |
| 缺失引用 | 角色引用不存在、未发布或不可见 Skill 时拒绝物化 Role，不跳过、不退回内置同名项 |
| 稳定排序 | 合并完成后统一按规范化 `code ASC`；不得依赖数据库返回、Set 遍历或插入顺序 |
| 审计 | 对每个 code 记录来源、命中版本、是否发生覆盖及被覆盖版本 |

SYSTEM、ASSISTANT、ROLE 三个绑定作用域在合并后组合；同一 `code` 不允许跨作用域重复绑定，因为一个激活结果只能有一个权威作用域。跨作用域重复是定义错误，不使用隐式覆盖消歧。

### 激活流程

```text
构建三作用域授权 Scope
→ 执行角色 / 内置两层目录合并
→ 自动加入 ALWAYS
→ ON_DEMAND 仅向模型暴露摘要
→ 校验模型返回属于候选、无重复且不超过上限
→ 解析精确已发布版本
→ 冻结 ActivatedSkill 与选择审计
```

筛选是 `NON_AUTONOMOUS_L0`：无工具、严格输出 Schema、单次调用，不创建 Agent、Harness Loop 或持久 Task。非法模型输出 fail-closed；只有清单显式声明 `defaultSkillKey` 时才允许使用该确定性默认项。

### Skill 工具声明

`toolAccessMode`、版本化 `ToolRef` 与 required/optional 是 SkillVersion 的声明字段，不构成授权。空集合、`RESTRICT/INHERIT`、`BaseToolProfile`、多层交集及排除处置的完整语义只在 [动作治理](../action-governance.md#有效工具交集) 定义。

Skill 侧只保证：声明引用精确工具版本；同名不同版本不自动择新；输入按 `name ASC, version ASC, toolId ASC` 稳定排序；`INHERIT` 只能由已审核系统 Skill 声明；用户 Skill 发布时拒绝该值。

Role 与 Agent 的安全交集归 [动作治理](../action-governance.md#有效工具交集) 定义，本篇只记录接线事实：当前 Role allowlist 与 Agent 声明工具已在运行链取交集，Role 空白名单由调用方收敛为空工具集；这只证明该双层边界已落地，不代表动作治理定义的完整八层交集已经闭合。

### 冻结与重试

Skill 解析结果作为 `SkillExecutionProfile` 进入 [runtime.md 的 `ExecutionProfileSnapshot`](../runtime.md#executionprofilesnapshot-冻结项)。本篇不重复画像字段；重试不得漂移 Skill 版本或声明的工具要求。

## 实现态

| 契约 | 实现态 |
|---|---|
| 三作用域与两激活模式 | ✅ 已实现 · `SkillScope.java:4-8`、`SkillBinding.java:11-57` |
| `ON_DEMAND` 允许选择 `0..N`，ALWAYS 自动激活 | ✅ 已实现 · `AssistantApplicationService.java:1190-1265` |
| 筛选为非自主结构化调用 | ✅ 已实现 · `ModelSkillSelectionPort.java:16-130`、`AssistantApplicationService.java:1190-1238` |
| 激活结果冻结到执行画像 | ✅ 已实现 · `ActivatedSkill.java:12-48`、`SkillExecutionProfile.java:7-18` |
| 角色 / 内置两层合并、角色同 key 优先 | ⚠️ 部分实现 · `SkillCatalogPort.java:10-17` 与 `JpaSkillCatalogAdapter.java:16-72` 已提供内置目录读取，`DefaultEffectiveSkillResolver.java:19-42` 已做授权边界、精确加载和 code 稳定排序；生产调用链未调用 `findBuiltIn`，且 `AssistantApplicationService.java:1574-1602` 对跨 Scope 同 key 直接拒绝，当前不得声称已执行两层覆盖 |
| 合并结果稳定排序 | ⚠️ 部分实现 · `DefaultEffectiveSkillResolver.java:31-41` 已对请求集合按 code 排序；内置合并后的统一排序尚未接线，当前不得声称完整两层结果已稳定排序 |
| Role allowlist 与 Agent 声明工具取交集 | ✅ 已实现 · 调用接线及空 Role 收敛见 `AssistantApplicationService.java:1323-1327`；双层过滤与 required 缺失拒绝见 `DefaultEffectiveToolResolver.java:30-44` |
| 工具三态 | 🎯 目标态；当前不得声称已执行 |
| required / optional 区分 | ⚠️ 部分实现 · `SkillToolRequirement.java:13-43` 已持久化 required 标记；`DefaultEffectiveToolResolver.java:10-44` 仍把输入集合统一当必需工具 |
| 用户 Skill 禁止 `INHERIT` | ⚠️ 部分实现 · `SkillVersion.java:52-53`、`SkillService.java:976-986` 已有 `RESTRICT` 默认值与枚举校验；发布路径已校验 `builtIn + 已审核版本`（`SkillService.java:833-846` `requireInheritOnlyForBuiltIn`，`requireApprovedVersion` 保证版本已人工审核），非内置 Skill 发布 `INHERIT` 即拒绝；create/update 草稿阶段仍允许暂存该值，只在发布时兜底 |
| 工具排除层归因完整 | 🎯 目标态；当前不得声称已执行 |

## 验收基线

- 给定同一目录和 Role，合并结果与输入顺序、数据库返回顺序无关。
- 角色 Skill 与内置 Skill 同 key 时仅保留角色精确版本，并留下覆盖审计。
- 跨 SYSTEM / ASSISTANT / ROLE 重复绑定在发布时拒绝，不到运行期猜测优先级。
- 未激活 Skill 的正文、references、知识绑定和工具均不可见。
- 空工具集合只按显式访问模式解释，用户 Skill 无法借 `INHERIT` 扩权。
- required 工具缺失时执行前失败，optional 缺失只记录排除原因。
- 重试不会漂移 Skill 版本或有效工具集合。
