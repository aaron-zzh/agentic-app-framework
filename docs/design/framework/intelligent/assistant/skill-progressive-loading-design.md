---
level: Practice
layer: Model
purpose: 定义 AAF Skill 的在线版本模型、渐进加载机制、参考资料与知识库边界及运行时授权链
status: draft
version: 1.1.0
date: 2026-08-22
author: Kiro
related:
  - ../architecture.md
  - ../subagent-and-skill-merge-design.md
  - ../../../../reference/dev/agentscope-usage-guide.md
scope:
  includes:
    - 当前 Assistant、Skill 与 AgentScope 调用链事实
    - Skill 正文、references 与知识库的加载和治理边界
    - Skill 根对象、不可变版本及关联数据模型
    - 工具、模型、发布、评估和迁移规则
  excludes:
    - 数据库迁移、接口和 WebUI 的具体实现
    - 任务编号、验收标准和发布计划
    - Knowledge Base 内部索引和召回算法设计
gains:
  - 能区分路由元数据、Skill 正文、Skill references 与知识库检索结果
  - 能按确定的领域边界实现 Assistant 到 Agent 的 Skill 执行流程
  - 能据此设计数据库迁移、API 和 Skill 编辑器
---

# Skill 渐进加载与在线知识边界设计

> 🔴 高风险架构设计草案：本文件确定目标模型与迁移边界；数据库迁移、API 替换、运行时改造及 WebUI 实现必须经人类审核后启动。

AAF 的 Skill 是可复用的执行知识与约束，不是 Assistant 的路由规则、Agent 的归属对象，也不是 AgentScope 的本地工作区 Skill。Skill 由 Assistant 路由选中，经 Role 和任务授权收窄后，作为 Agent 的任务级执行画像组成部分。

## 设计目标与当前事实

### 目标

- 以数据库和 AAF 托管文档为在线唯一真理源，`SKILL.md` 仅作为导入、导出与编辑语义兼容格式。
- 将 Skill 正文命名为 `content`，以 Markdown 作为格式契约；不再并存 `systemPrompt` 与 `instructions` 两个执行正文。
- 先选择 Skill，再加载其正文；只在明确条件满足时加载精确的 references 或检索知识库。
- 保持职责边界：Assistant 路由、Role 授权、Skill 执行知识、Agent 执行环境、Knowledge 事实检索各自独立。
- 保持 AAF 的工具权限和审计链，不能由 Markdown 声明或 AgentScope Skill 激活绕过。

### 当前调用链

当前实现不是“执行 Agent 先读所有 Skill 描述、再自主加载正文”。主路径如下：

```text
AssistantInvocation
  → AssistantApplicationService.resolveRoute()
  → SkillRouter.route(AssistantDefinition, input, userId)
  → SkillRoute(skillKey, roleKey, intentTerms, priority, ...)
  → definition.roleFor(route)
  → EffectiveSkillResolver.resolve(role, skillKey)
  → SkillCatalogPort.findByCode(skillKey)
  → AgentExecutionCommand(skillSystemPromptAppendix, roleAllowedToolNames, ...)
  → HarnessAgentExecutionAdapter
  → AgentScopeSpecCompiler
  → HarnessAgent.streamEvents(...)
```

当前代码的事实与缺口：

- `SkillRoute.intentTerms` 与 `SkillRoute.priority` 是 Assistant 专属路由配置；它们不应继续保存在 Skill 中。
- `DefaultEffectiveSkillResolver` 已校验 `Role.skillKeys()`，然后按 `skillKey` 精确查询 Skill；它不向执行 Agent 提供多个 Skill 的简介目录。
- `JpaSkillCatalogAdapter` 从 `SkillStore.SkillRecord` 构造 `SkillDef` 时丢弃了 `instructions`。
- `AssistantApplicationService.mergeSkillPrompts()` 只排序并拼接 `SkillDef.systemPrompt`；当前 `description` 不进入执行提示词，`instructions` 也不会生效。
- `SkillMatchEngine` 仍按旧的 `triggerIntent` 与 `priority` 匹配，是与 `SkillRoute` 并存的遗留路径，目标态必须移除而非继续扩展。
- `AgentScopeSpecCompiler` 已显式关闭 AgentScope 原生动态 Skills、默认工作区 Skills、文件系统与 Shell；该边界应保留。

### 核心术语

| 概念 | 责任 | 不承担的责任 |
|---|---|---|
| `summary` | Skill 的一句话发现与路由简介；建议采用 `USE WHEN ...` 表述 | 不承载执行指令 |
| `content` | 被选中后注入 Agent 的核心 Markdown 执行正文 | 不存放大规模事实资料 |
| Skill reference | 某 SkillVersion 精确指定、可复现的扩展资料 | 不做全库语义召回 |
| Knowledge binding | 允许当前 Skill 在特定知识范围中检索动态事实的声明 | 不固定某篇资料正文 |
| `SkillRoute` | Assistant 对用户输入的意图路由与优先级 | 不保存 Skill 正文或工具授权事实 |

## 目标执行模型

### Assistant 限定 Scope，非自主 L0 选择最终 Skill

目标态采用两阶段模型：Assistant 不把所有候选 Skill 正文交给执行 Agent，也不让执行 Agent 自由浏览全局目录。Assistant 先确定受限候选 Scope；独立的无副作用 Skill Selector 再根据该 Scope 中的摘要选择最终激活 Skill。Selector 属于 `NON_AUTONOMOUS_L0` 单一函数调用，不是 Executor、Harness Agent 或自主任务循环。

```text
用户输入
  → Assistant 前注意、意图识别与 SkillRoute 选择
  → 解析当前 Role 与受限 Skill Scope
  → Scope = Route 候选范围 ∩ Role.skillKeys ∩ 发布与租户可见范围
  → 生成候选 Skill 摘要清单
  → NON_AUTONOMOUS_L0 Skill Selector（无工具、严格 JSON、独立 PromptEnvelope）
  → AAF 校验候选边界并冻结选择结果
  → 加载每个已激活版本的 content
  → 计算精确工具交集并编译最终执行画像
  → AgentScope Harness 执行阶段
```

Role Selector 与 Skill Selector 是两个可独立计量和追踪的函数调用：Role 先确定能力上限，Skill 只能在选定 Role 与 Route 的交集中继续收窄。它们默认是当前 Assistant 流程中的 child invocation，不创建 TaskBoard 子任务或 `ExecutionProfileSnapshot`；需要异步、耐久恢复或独立业务重试时可提升为显式系统节点，但仍不自动成为 Agent。其动态 Prompt 只包含版本化 Function Contract、授权候选摘要、当前输入和严格 Output Contract，不注入 Harness Constitution、Persona、Role 正文、Skill 正文或自主任务循环。完整合同见 [PromptEnvelope 与模型调用装配设计](../core/prompt.md)。

`SkillRoute` 从“唯一最终 `skillKey`”调整为任务领域入口和选择策略。其目标契约为：

```text
SkillRoute
- candidateSkillKeys          # 本路由允许暴露的候选 Skill；与 Role 取交集
- defaultSkillKey             # 固定执行或选择失败时的默认主 Skill
- skillSelectionMode          # FIXED、SELECT_PRIMARY、SELECT_AND_AUGMENT
- maxActivatedSkills          # 可激活 Skill 上限；默认 1，最多为受控的小集合
- roleKey、intentTerms、priority、handlingMode、actionKey、actionEffect
```

候选范围与 Role 的 Skill 范围不能互相替代：Route 表达“此 Assistant 在此意图下允许选择哪些能力”，Role 表达“当前业务角色的最大能力边界”。最终候选必须同时属于两者。用户显式请求某个 Skill 时，也只能在该受限 Scope 内选择。

三种选择策略如下：

| 策略 | Assistant 行为 | Skill Selector 行为 | 适用场景 |
|---|---|---|---|
| `FIXED` | 直接指定 `defaultSkillKey` | 不调用模型，确定性使用默认项 | 高风险、合规、强流程任务 |
| `SELECT_PRIMARY` | 提供候选摘要与默认项 | 从候选中返回一个主 Skill code | 普通多意图业务任务 |
| `SELECT_AND_AUGMENT` | 提供候选摘要、默认项和最大数量 | 返回主 Skill 和不超过上限的辅助 Skill code | 复合创作、研究、分析任务 |

候选摘要只包含 `code`、`name`、`summary`、分类、输入输出标签、模型能力和工具用途标签；不包含完整 `content`、reference 正文、知识库内容或可直接调用的业务工具。`summary` 应采用意图式 `USE WHEN ...` 表述，以支持 Selector 的细粒度判断。

### 选择、激活与最终执行画像

Skill Selector 接收 `SkillSelectionManifest` 和当前任务输入，并且只承担无副作用候选选择：

```java
public record SkillSelectionManifest(
        String routeKey,
        String defaultSkillKey,
        SkillSelectionMode selectionMode,
        int maxActivatedSkills,
        List<AuthorizedSkillSummary> candidates) {}
```

Selector 只返回候选中的 code 列表。AAF 随后校验数量、重复项、候选范围、Role、版本、可见性、模型和必需资源，再冻结激活结果；非法输出按 Function Contract fail-closed 或采用已声明的确定性安全默认值。选择结果本身不会打开任何 ToolGroup，也不授予工具权限。

选择完成后，AAF 构建不可变 `SkillExecutionProfile`，替换当前无来源语义的 `String skillSystemPromptAppendix`：

```java
public record SkillExecutionProfile(
        SkillSelectionManifest selection,
        List<ActivatedSkill> activatedSkills,
        List<ToolRef> effectiveTools) {}

public record ActivatedSkill(
        SkillVersionRef version,
        String content,
        List<SkillReference> references,
        List<SkillKnowledgeBinding> knowledgeBindings) {}
```

执行提示词由 Agent 基础提示、Role 职责边界和已激活 Skill 的确定顺序 `content` 组成；references 与知识库结果不在此时全量注入。核心正文应保持聚焦，建议小于约 500 行或 5,000 tokens，并明确说明何时读取每份扩展资料。

若候选 Skill 会带来不同工具、模型能力或风险等级，选择阶段结束后必须重新计算执行画像并重新编译或切换 `HarnessAgent`。不得在共享或已运行的 `HarnessAgent` 上直接修改 Toolkit、System Prompt 或权限范围。只有候选 Skill 使用完全相同的已授权只读工具时，才允许在同一会话中仅追加正文；这只是优化，不得成为放宽权限的路径。

### 运行时按需上下文

```text
已激活 Skill 的 content
  ├─ 明确写有“何时读取 reference X”
  │    → load_skill_reference(referenceKey)
  │    → 重新验证租户、工作区、用户和任务权限
  │    → 读取固定 document_version 的指定章节并按预算裁剪
  └─ 当前问题需要动态事实
       → knowledge.search(knowledgeBaseId, query, scope)
       → 检索时执行当前 ACL、标签和范围过滤
       → 返回局部片段、来源与版本证据
```

`load_skill_reference` 只能接收任一已激活 `SkillVersion` 预声明的 `referenceKey`，不得接受任意 URL、任意文件路径或任意文档 ID。`knowledge.search` 只能检索已激活 Skill 绑定的知识库与范围，调用时仍必须通过当前主体和任务授权。

### AgentScope 的使用边界

AgentScope 2.0 的 `AgentSkillRepository` 支持数据库、Git 和远程 API，Harness 也支持按请求可见性过滤及延迟资源读取。这些能力证明在线仓储与渐进资源读取可行，但不改变 AAF 的领域所有权。

AAF 不启用 AgentScope 原生动态 Skill 主路径，继续保持：

```java
.disableDynamicSkills()
.disableDefaultWorkspaceSkills()
.skillsEnabled(false)
```

原因是其原生 `load_skill_through_path` 在读取 `SKILL.md` 时会激活 Skill 并开启关联 ToolGroup。若直接使用，会绕过 AAF 的 Role、ToolPolicy、任务授权和审计链，也会形成 AAF 数据库与 AgentScope 工作区两套 Skill 真理源。AgentScope 只作为 ReAct 推理循环与 AAF Toolkit 的执行基础设施。

### 工具与模型约束

Skill 声明工具是能力收窄和执行说明，不是授权授予。每次最终激活集合确定后，编译期工具集为：

```text
已激活 Skill 允许工具的交集 ∩ Role 允许工具 ∩ Agent 声明的 ToolRef
```

辅助 Skill 只要要求更窄的工具范围，就会进一步收窄最终工具集；如任务需要相互不兼容的工具集合，Assistant 应拆分为独立子任务，而不是在一个执行画像中并集扩权。

工具实际调用时还必须通过：

```text
ToolPolicy ∩ task grant ∩ 当前用户、组织和工作区权限
```

`AgentScopeToolkitFactory` 继续只注册上述交集中的精确 `ToolRef`，并由 `PortBackedAgentTool` 进入 AAF 网关、授权与审计。必需工具不在交集时，Skill 激活必须明确失败或重路由，不能静默放宽权限或降级执行。

Skill 的模型要求声明能力而不是供应商模型名，例如文本、多模态、推理、最低上下文窗口。Assistant/Agent 的模型选择仍由 Core 能力路由完成；选择结果必须同时满足全部已激活 SkillVersion 的必需能力，否则不得执行。

## Skill references 与知识库边界

### 分类原则

| 判断问题 | Skill reference | 知识库 |
|---|---|---|
| 是否能明确指定“读取哪篇资料的哪一节” | 是 | 否 |
| 是否应与 SkillVersion 一同审查、发布和复现 | 是 | 否，知识独立演进 |
| 是否需要从大量资料中按当前问题找事实 | 否 | 是 |
| 是否可接受同一问题因知识更新返回不同证据 | 通常不可接受 | 可以，需保留检索证据 |
| 读取方式 | 精确路径、章节或固定片段 | 向量、关键词、图谱或混合检索 |

判断规则：能写成“执行到步骤 X 时，读取文档 Y 的章节 Z”的内容是 Skill reference；只能描述为“面对当前问题，从资料池检索相关事实”的内容属于知识库。

### Skill reference 的适用内容

以下资料应作为版本固定的 references：

- 执行流程的详细 SOP、异常处理和验收清单；
- Skill 专属输出模板、JSON 样例、字段字典和格式约束；
- 固定品牌语调、渠道规则、行业禁忌；
- 与本 SkillVersion 同步演进的 API 协议、转换规则和示例；
- 需要可重复执行与审计的精确规范。

如果某资料原本由知识库管理，但某一 Skill 每次必须使用其确定版本，应将已审核版本快照绑定为 Skill reference，而不是每次从动态知识库检索。

### 知识库的适用内容

以下资料应由知识库提供：

- 大规模、持续更新的产品、价格、库存、活动或政策信息；
- 客户、项目、会议、工单和历史案例资料；
- 多个 Skill 或 Assistant 共用的企业知识；
- 需要依据当前问题、时间与权限动态召回的事实；
- 需要返回来源证据与检索片段，而非完整固定文件的资料。

知识库结果属于任务级执行上下文，不复制进 SkillVersion，也不因索引内容变动而自动修改 Skill 正文。

## 最终数据模型

### Skill 根对象与版本

`ai_skill_definition` 是稳定身份、发现和可见性根对象，不存放可变执行正文。

| 字段 | 说明 |
|---|---|
| `id` | Skill 主键 |
| `org_id`、`workspace_id` | 组织与工作区归属；系统内置可使用系统范围 |
| `code` | 稳定业务键；Role 与 `SkillRoute` 的候选/默认字段均引用它 |
| `name` | 展示名称 |
| `summary` | 路由与目录用的一句话简介 |
| `locale` | 默认语言/地区 |
| `visibility` | `PRIVATE`、`WORKSPACE`、`PUBLIC` |
| `built_in` | 是否系统内置 |
| `current_version_id` | 当前可执行且已发布的版本 |
| `source_skill_id` | 可选血缘来源，支持派生与克隆追踪 |
| `owner_id` 与审计字段 | 责任主体、创建修改、软删除和备注 |

`ai_skill_version` 是不可变的执行定义。内容或关联的固定资源发生实质修改时，必须创建新版本。

| 字段 | 说明 |
|---|---|
| `id`、`skill_id`、`version` | 版本身份；`skill_id + version` 唯一 |
| `status` | `DRAFT`、`IN_REVIEW`、`APPROVED`、`REJECTED`、`RETIRED` |
| `content` | 核心 Markdown 正文；在线 `SKILL.md` body 的规范实现 |
| `input_schema` | 可选 JSON Schema 输入契约 |
| `output_schema` | 可选 JSON Schema 输出契约 |
| `output_contract` | 无法结构化时的简短输出约束；避免复制长正文 |
| `tool_access_mode` | `RESTRICT` 或 `INHERIT`；新建 Skill 默认 `RESTRICT` |
| `change_summary` | 相对前版的变更说明 |
| `content_hash` | 正文与固定附属资源的完整性标识 |
| 作者与审核字段 | 创建、提交、审核时间与主体 |

不再设置独立的“使用背景”“使用方式”“输出内容”长文本字段；这些执行说明属于 `content`。需要机器校验的输入输出使用 JSON Schema，而不是重复维护自由文本。

### 分类、工具和模型要求

```text
ai_skill_category
ai_skill_category_relation(skill_id, category_id)
```

分类属于根对象，支持多分类。

```text
ai_skill_tool_requirement
- skill_version_id
- tool_id
- tool_version                  # 可空；为空时使用 Agent 声明的有效版本
- tool_name                     # 展示与诊断字段
- required
- usage_purpose
- sort_order
```

```text
ai_skill_model_requirement
- skill_version_id
- capability                    # TEXT、VISION、REASONING 等
- required
- minimum_context_tokens
- rationale
```

### 精确 references 与动态知识绑定

```text
ai_skill_reference
- skill_version_id
- reference_key
- title
- document_id
- document_version_id
- selector                      # 章节、锚点或精确片段
- load_mode                     # ON_ACTIVATION、ON_DEMAND
- load_when                     # 必填的明确触发条件
- required
- max_tokens
- sort_order
- content_hash
```

Reference 只能指向 AAF 管理的文档及其版本，不保存外部签名 URL 或未受控路径。

```text
ai_skill_knowledge_binding
- skill_version_id
- knowledge_base_id
- scope_selector                # 集合、标签、目录、文档类型等范围
- retrieval_instruction
- load_when
- default_top_k
- max_tokens
- required
- sort_order
```

Knowledge binding 只声明检索边界，不能跳过运行时 ACL，也不固定检索结果。

### 展示、发布与质量治理

```text
ai_skill_media
- skill_id 或 skill_version_id
- media_type                    # COVER、DEMO_IMAGE、DEMO_VIDEO、ATTACHMENT
- asset_id
- alt_text
- sort_order
```

媒体用于编辑器和市场展示，不自动注入执行 prompt。

```text
ai_skill_publication
- skill_version_id
- target_visibility
- status                        # PENDING、APPROVED、REJECTED、WITHDRAWN
- submitted_by/time
- reviewed_by/time
- review_comment
```

```text
ai_skill_evaluation
- skill_version_id
- evaluator_type                # AI、HUMAN、AUTOMATED
- dimension                     # ROUTING、CONTENT、OUTPUT、SAFETY、USER_SATISFACTION
- score
- evidence
- feedback
- status
- evaluator 与时间戳

ai_skill_eval_case
- skill_version_id
- input
- expected_route
- expected_output_constraints
- expected_tool_behavior
- trace_id
- result
```

评分、反馈与评估案例是版本外部的不可变治理证据。AI 或用户反馈不可直接修改 `content`；内容变更必须创建版本并再次走审核策略。

## 迁移与治理约束

### 现有字段迁移

| 当前字段 | 目标处理 |
|---|---|
| `code`、`name` | 保留为根对象稳定身份与展示名 |
| `description` | 迁移为根对象 `summary` |
| `category` | 迁移到 `ai_skill_category_relation` |
| `instructions` | 迁移为版本 `content` |
| `system_prompt` | 迁移并入版本 `content` |
| `agent_id` | 删除；Skill 不归属于 Agent |
| `trigger_intent`、`priority` | 删除；迁移到 Assistant `SkillRoute` |
| `is_global` | 删除；不再使用全 Agent 注入 |
| `is_public` | 删除；改为 `visibility` 与 `ai_skill_publication` |
| `skill_version` | 删除；使用独立版本表的 `version` |
| `status` | 拆为版本状态与发布状态 |
| `built_in`、审计字段 | 保留在根对象；版本额外保留作者与审核事实 |

内容迁移规则：仅有 `instructions` 时直接迁移；仅有 `system_prompt` 时直接迁移；两者都有时，以 `instructions` 为正文，并在明确的“运行时补充约束”标题下附加 `system_prompt`。迁移完成后不保留旧字段的运行时 fallback 或双写路径。

### 验证、发布与实施门禁

- `content` 是进入 `APPROVED` 状态的必填字段；必须通过 Markdown 规模、结构和安全校验。
- `RESTRICT` 版本中的每个必需工具、reference、知识库绑定和模型能力都必须在发布前可验证。
- reference 的 `load_when` 必填，禁止“见参考资料”这类无条件模糊提示。
- `PUBLIC` 发布应要求人工审核；AI 评估和自动案例是审核证据，不替代责任主体。
- 所有线上读取与工具调用都必须在执行时重验组织、工作区、用户和任务权限。
- 实施前仍需人类明确确认：内置 Skill 默认可见范围、公开发布审核策略、AI 评分是否可阻止激活，以及第一阶段是否只支持托管文档与知识库、不支持脚本和可执行资产。

本设计确认的不可变边界是：Assistant 负责路由，Role 负责最大能力范围，SkillVersion 负责可复现执行知识，Knowledge 负责动态事实检索，Agent 负责受限执行，AgentScope 不拥有 AAF Skill 的权威状态或授权决定权。
