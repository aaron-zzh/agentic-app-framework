---
level: Practice
layer: Model
purpose: 定义 Skill 的在线版本模型、渐进加载、参考资料与知识边界及发布治理
status: draft
version: 1.1.0
date: 2026-08-25
author: Kiro
tags:
  - Skill
  - 版本模型
  - 渐进加载
  - 知识绑定
dependencies:
  - ../architecture.md
related:
  - ./skill-tool-resolution.md
  - ../cognition/cognition.md
scope:
  includes:
    - Skill 的在线版本模型与不可变版本内容
    - 渐进加载机制与加载时机
    - references 与知识库绑定的边界
    - Skill 侧候选声明、发布治理与动作治理引用
  excludes:
    - 分层激活与工具解析（见 skill-tool-resolution.md）
    - 工具调用门禁（见 action-governance.md）
    - Skill 管理界面的增删改查设计
gains:
  - 能发布一个合法的 SkillVersion 并预判校验结果
  - 能判断某项内容应放入版本正文、references 还是知识绑定
  - 能说明 Skill 在一次执行中何时被加载
---

# Skill

## 定位与边界

> Skill 是 AAF 的版本化能力单元：由 L3 的 Role 组织，被 L2 的执行画像消费。
> AAF Skill 不是 AgentScope Skill；Tool 是 AgentScope `@Tool` 方法，但其可见性与调用权由 AAF 治理。

| Skill 负责 | Skill 不负责 |
|---|---|
| 固定能力方向、执行正文、输入输出与完成证据 | 判断任务应使用单 Agent 还是 Team |
| 声明所需工具、模型能力、references 与知识范围 | 授予工具调用权或扩大 Role/主体权限 |
| 提供不可变版本和可复现引用 | 代替 L1 检索、ACL 或动态事实真理源 |

Skill 的工具声明只产生候选，激活与解析见 [技能激活与工具解析](skill-tool-resolution.md)，每次动作的授权门禁见 [动作治理](../action-governance.md)。

## 领域模型

### 稳定根对象

`SkillDefinition` 只承载稳定身份、发现和可见性；执行正文只属于版本。

| 字段 | 约束 |
|---|---|
| `id` | 数据库主键 |
| `orgId`、`workspaceId` | 租户与工作区归属；系统 Skill 使用系统范围 |
| `code` | 全局稳定业务键；Role 与路由只引用该键 |
| `name`、`summary`、`locale` | 展示与候选发现元数据；`summary` 不承载执行指令 |
| `instancePrompt` | 用户可见示例，不进入执行 Prompt |
| `visibility` | `PRIVATE` / `WORKSPACE` / `PUBLIC` |
| `builtIn` | 系统 Skill 标识，不等同于自动获权 |
| `currentVersionId` | 当前可执行版本指针；为空表示无线上版本 |
| `sourceSkillId` | 克隆或派生血缘，不继承授权 |
| `categories` | 多分类；`category` 只标记能力族，不创建平行运行时 |
| `ownerId` 与审计字段 | 责任主体、创建修改、软删除事实 |

### 不可变版本

`SkillVersion` 的完整内容快照如下；任一执行字段或固定附属资源变化都必须创建下一版本。

| 字段 | 约束 |
|---|---|
| `id`、`skillId`、`version` | `skillId + version` 唯一，`version >= 1` |
| `status` | 审核态：`DRAFT` / `IN_REVIEW` / `APPROVED` / `REJECTED` / `RETIRED` |
| `content` | 唯一 Markdown 执行正文；不得并存 `instructions` / `systemPrompt` 第二正文 |
| `inputSchema`、`outputSchema` | 可选 JSON Schema；存在时必须可解析 |
| `outputContract` | Schema 无法表达的短输出约束，不复制正文 |
| `toolAccessMode` | `RESTRICT` / `INHERIT`；适用边界见授权治理 |
| `changeSummary`、`contentHash` | 版本差异说明与完整性校验 |
| `authoredBy`、`submittedBy/At` | 作者与送审事实 |
| `reviewedBy/At`、`reviewComment` | 审核事实 |
| `createTime` | 版本创建时间；已发布版本不得更新 |

运行时只引用 `SkillVersionRef(skillId, versionId, version)`，不得按“最新版本”漂移。冻结的 `ActivatedSkill` 同时保存作用域、激活模式、正文、工具/模型要求及 reference key、知识绑定 ID。

### 最终持久化模型

| 表 | 真理内容 | 版本关系 |
|---|---|---|
| `ai_skill_definition` | 稳定身份、可见性、当前版本指针 | 根对象 |
| `ai_skill_version` | 不可变执行正文与 Schema | `N:1 SkillDefinition` |
| `ai_skill_category_relation` | 发现分类 | 绑定根对象 |
| `ai_skill_tool_requirement` | 工具版本、required/optional、用途与顺序 | 绑定版本 |
| `ai_skill_model_requirement` | 能力、是否必需、最小上下文与原因 | 绑定版本 |
| `ai_skill_reference` | 固定文档版本、selector、加载条件与预算 | 绑定版本 |
| `ai_skill_knowledge_binding` | 可检索知识范围、检索条件与预算 | 绑定版本 |
| `ai_skill_media` | 展示媒体；不自动进入 Prompt | 绑定根或版本，二选一 |
| `ai_skill_publication` | 可见性发布审核 | 绑定版本 |
| `ai_skill_evaluation`、`ai_skill_eval_case` | 评估证据与回归案例 | 绑定版本，不改正文 |
| `ai_skill_decision_audit` | 选择、激活、编译与排除原因 | 追加式运行事实 |

## 契约

### 生命周期、引用与回滚

对执行方只暴露三个生命周期状态；审核态是治理子状态，不创建第二套执行状态机。

```text
草稿 DRAFT ──发布校验与审核通过──> 已发布 PUBLISHED ──下线──> 已下线 RETIRED
       ▲                                  │
       └────编辑一律创建新草稿版本──────────┘
```

| 生命周期 | 判定 | 可执行性 |
|---|---|---|
| `DRAFT` | 未被 `currentVersionId` 引用；可处于 `DRAFT/IN_REVIEW/APPROVED/REJECTED` 审核态 | 不可执行 |
| `PUBLISHED` | `currentVersionId = versionId` 且版本审核态为 `APPROVED` | 可执行并按精确版本冻结 |
| `RETIRED` | 版本审核态为 `RETIRED`，且根对象不得继续指向该版本 | 不可新执行；历史运行仍可审计 |

- 发布是原子更新 `currentVersionId`，不修改版本内容。
- 新草稿与当前已发布版本并存；编辑草稿不影响线上执行。
- 回滚只允许把指针原子切回**同一 Skill 的既有 `APPROVED`、未下线版本**，记录操作者、原因和前后版本；不得复制旧内容生成“伪回滚”版本。
- 已启动运行继续使用冻结的 `SkillVersionRef`；发布、回滚或下线只影响后续运行。
- 指定版本不存在、跨 Skill、未审核或已下线时 fail-closed，不降级到最新版本。

### 渐进加载

加载预算属于执行上下文预算的一部分；裁剪必须留决策记录，required 内容超预算时阻塞，不得静默丢弃。

| 层级 | 内容 | 加载时机 | 预算与硬约束 |
|---|---|---|---|
| 元数据 | `code/name/summary/category`、输入输出标签、工具用途与模型能力摘要 | 在已授权 Scope 内供路由和非自主 L0 选择 | 只加载候选摘要；不含正文、reference 内容、知识片段 |
| 正文 | 已激活版本的 `content` | 选择结果校验并冻结后、编译执行画像前 | 每版本上限 500 行或 5,000 tokens；超限拒绝发布 |
| references | 精确文档版本与 selector 指向的片段 | `ON_ACTIVATION` 在激活后；`ON_DEMAND` 仅在 `loadWhen` 命中时 | 每项不超过 `maxTokens`；只能使用已激活版本声明的 `referenceKey` |
| 知识 | L1 返回的动态检索片段、来源和版本证据 | 当前任务命中 binding 的 `loadWhen` 后 | `defaultTopK` 与 `maxTokens` 双限；仍受 L1 总预算和 ACL 收窄 |

硬约束：**未激活 Skill 不加载正文、不读取 references、不检索其知识绑定，也不暴露其工具。** references 与知识结果按需进入 P8 数据，不拼入 Skill 权威正文。

### references 与知识库边界

| 判断项 | Skill reference | 知识库绑定 |
|---|---|---|
| 真理内容 | 可复现的固定资料快照 | 持续演进的动态事实集合 |
| 适用内容 | SOP、模板、字段字典、渠道规则、固定协议 | 产品、政策、项目、案例与时效事实 |
| 定位方式 | `documentId + documentVersionId + selector` 精确读取 | `knowledgeBaseId + scopeSelector + query` 混合检索 |
| 版本关系 | 与 SkillVersion 同审、同复现 | 独立演进；只冻结本次检索证据 |
| 授权 | 运行时重验文档、租户、工作区、主体与任务权限 | L1 重验知识库 ACL、标签和范围 |
| 失败 | required 缺失则阻塞；optional 缺失留痕 | required 无可信结果则阻塞；optional 无结果留痕 |

Reference 只能指向 AAF 托管文档的固定版本，不接受任意 URL、文件路径或临时签名地址。在线资料先作为不可信外部数据进入受控采集/知识链路，保留来源、抓取时间、版本与可信度；检索结果必须携带引用，不能覆盖系统指令、授予权限或直接触发动作。需要版本复现的在线资料必须先审核并固化为托管 reference。

### 完成证据与平台约束

Skill 可声明结构化完成证据，由统一 `CompletionValidator` 对照实际事件验证。只有声明式规则无法表达且稳定复用的领域规则才注册专用验证逻辑，不为每个 Skill 创建验证器。

目标平台及导出约束可以声明，但平台导出器属于内容服务层的确定性适配能力；选择格式不能改写规范正文、扩大工具权限或获得发布权。

### 授权与发布治理

SkillVersion 只产生版本化的工具、模型、reference 与知识范围候选，不签发 grant，也不计算最终有效工具集。完整工具交集、`RESTRICT/INHERIT` 的运行时语义、required/optional 排除处理与每次动作门禁仅由 [action-governance.md](../action-governance.md#有效工具交集) 定义。

`INHERIT` 资格仍属于 Skill 发布约束：只允许经平台审核且 `builtIn=true` 的系统 Skill；用户创建、克隆或派生 Skill 一律默认 `RESTRICT`，即使来源是系统 Skill 也不继承该资格。

发布前必须完成：

- `content` 非空、规模合规，Markdown 与 JSON Schema 可解析；`contentHash` 与固定附属资源一致。
- required 工具、模型能力、reference、知识库与完成证据均存在、可见且可验证。
- 每个 reference/知识绑定有明确 `loadWhen`；reference 固定文档版本并设置预算。
- `INHERIT` 满足系统 Skill 与人工审核条件；PUBLIC 发布具有人类审核记录。
- 安全扫描、越权检查和回归案例通过；AI 评分只能作为证据，不能替代责任主体。

迁移与治理约束：`description → summary`；`instructions` 与 `systemPrompt` 一次性合并为 `content`；意图词与优先级迁往 Assistant 路由；移除 `agentId/isGlobal/isPublic/skillVersion` 等旧字段。迁移后禁止 fallback、双读、双写或 AgentScope 工作区 Skill 第二真理源。

## 实现态

| 契约 | 实现态 |
|---|---|
| 稳定根对象与核心不可变版本字段 | ✅ 已实现 · `SkillDefinition.java:27-87`、`SkillVersion.java:23-88` |
| 新版本追加、工具/模型要求继承与哈希 | ✅ 已实现 · `SkillService.java:469-572` |
| 精确版本引用冻结到执行画像 | ✅ 已实现 · `SkillVersionRef.java:6-15`、`ActivatedSkill.java:12-48` |
| 发布及选择既有 APPROVED 版本切换指针 | ✅ 已实现 · `SkillService.java:286-314` |
| 草稿 / 已发布 / 已下线完整状态机 | ⚠️ 部分实现 · `SkillService.java:63-65,286-314,976-980`；已有审核态与发布指针，缺下线命令、清理当前指针及状态转换门禁 |
| references、知识绑定及治理表 | ⚠️ 部分实现 · `v2__ai_schema.sql:511-731`；表结构已建，缺应用实体、仓储、发布校验与运行时加载端口 |
| 渐进加载四层与预算 | ⚠️ 部分实现 · `JpaSkillCatalogAdapter.java:16-72` 已分离摘要/正文读取，`ActivatedSkill.java:12-48` 已冻结引用标识；references 与知识按需加载未接线，当前不得声称已执行完整渐进加载 |
| 知识绑定限定检索范围 | 🎯 目标态；当前不得声称已执行 |
| 声明式完成证据 | 🎯 目标态；当前不得声称已执行 |
| 用户 Skill 默认 `RESTRICT` 且禁止 `INHERIT` | ⚠️ 部分实现 · `SkillVersion.java:52-53`、`SkillService.java:982-986`；默认值与枚举校验已实现，缺 `builtIn + 人工审核` 发布门禁 |

## 验收基线

- 已发布版本的正文与固定资源不可变，任一修改生成新版本。
- 运行只使用冻结的精确版本；发布、回滚和下线不改变在途运行。
- 未激活 Skill 不加载正文、reference、知识，也不暴露工具。
- 每次 reference 读取和知识检索都可复算授权、触发条件与预算。
- 用户 Skill 不能使用 `INHERIT`，Skill 任一字段都不能扩大已有权限。
- required 资源缺失或超预算时 fail-closed，optional 资源缺失留下审计原因。
- 在线资料始终作为不可信数据处理，并携带来源与可信度证据。
