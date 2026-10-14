---
level: Practice
layer: Model
purpose: 设计 AAF-113 项目创作闭环与一次性项目 AI 定时提示词
status: active
version: 2.22.0
date: 2026-09-17
author: AaronZZH & Kiro
tags:
  - AAF-113
related:
  - requirement.md
  - ui-design.md
  - tasks.md
  - blueprint-materialization-analysis.md
---

# 设计：Assistant 项目创作闭环（AAF-113）

## 设计目标

- 目录 Blueprint 与 Assistant 根据当前项目需求生成的 BlueprintSnapshot 进入同一校验、持久化和物化链。
- 保留 AIGC Project 专业聚合，以最小字段和不变量硬化现有项目制品图。
- canonical TaskPlan 成为 Assistant 文本创作、专业 Tool 调用、等待和恢复的唯一智能计划。
- readiness、输入版本、输出完成、权限和实际成本均由服务端权威事实判定。
- 真实跑通文本、文档、图像；音频和视频复用同一完成合同和可用专业 Tool。
- 用户可设置一次性时间点和提示词，到点后只创建既有 canonical Assistant Task，并在项目页和绑定对话查看状态。

## 数据结构总览

AIGC 项目不是单一实体，是一组各自拥有真理源、按聚合关系协作的类型化资源；不建立万能 `ProjectObject`，不迁移到通用 Project。相关类按归属分为三层，AAF-113 只拥有第一层，第二、三层是消费的既有能力：

### AIGC 项目层（AAF-113 自身拥有，本设计的核心聚合）

```text
AigcProject（项目身份、协作模式、粗粒度治理）
└─ AigcProjectConfigSnapshot（持久化实体，冻结基线的存储载体）
    └─ AigcProjectBlueprintSnapshotPayload（简称 BlueprintSnapshot，实体内承载的结构内容载荷：slotTemplates/objectSpecs/relationSpecs/actions/deliverableSets/processPolicy）
        └─ AigcProjectObject（内容对象树，parentId 表达层级）
            ├─ AigcProjectRelation（对象间非层级关系，只提交 relationType）
            ├─ ObjectVersion（结构化内容 + candidate/adopted 治理）
            │   ├─ DocumentVersion（自由文本正文真理源）
            │   └─ MediaVersion（图像/音频/视频资源）
            └─ ObjectReadiness（服务端派生的只读投影，不持久化到对象）

AigcProjectBlueprint（目录模板，独立持久化实体，仅 CATALOG 来源存在，宽松 Map 字段）
    ↓ 解析转化生成（AigcConfigurationService.blueprintSnapshot()）
AigcProjectBlueprintSnapshotPayload（同上，类型化载荷）

manifest / evidenceHash / Review / AigcWork / Publication（交付证据链，精确引用 ObjectVersion）
ProjectSchedule（一次性时间点，只负责创建 canonical Task，不持有第二计划状态）
```

`AigcProjectConfigSnapshot` 是持久化实体（数据库表），`AigcProjectBlueprintSnapshotPayload`（本文档及既有讨论中简称"BlueprintSnapshot"，不是另一个真实类名）不是另一个独立实体，是该实体的 `snapshot` 字段所承载的结构内容本身——两者是"容器与内容"关系，不是同级的两个聚合节点，文档和讨论中提到"冻结 BlueprintSnapshot"时，实际动作是"把这份载荷写入 `AigcProjectConfigSnapshot` 并落库"。

`AigcProjectBlueprint` 是另一个与 `AigcProjectBlueprintSnapshotPayload` 名字相近但完全独立的类——它是目录里发布好的可复用模板（有自己的 `code`/`status` 发布态，字段是宽松 `Map<String,Object>`），只在 CATALOG 来源下存在；两种来源在这一步之后分流又汇合：CATALOG 从 `AigcProjectBlueprint` 解析生成 `AigcProjectBlueprintSnapshotPayload`，ASSISTANT_GENERATED 由 Assistant 直接生成 `AigcProjectBlueprintSnapshotPayload`（无对应模板实体），此后两者共用同一份类型化载荷结构和下游链路。

### AIGC 能力层（AIGC 模块既有能力，早于本设计存在，AAF-113 复用不重建）

```text
ExecutionRun / AigcTask（专业动作与供应商执行的既有执行记录机制）
```

`ExecutionRun`/`AigcTask` 不映射为下方 Assistant 层的 `TaskNode`（两者是同名巧合，`AigcTask` 与 AAF-115 的 `Task` 是完全不同的两个类，分属不同模块）。

### 助理层（AAF-115 交付的框架级 Assistant 通用能力，AAF-113 只调用，不拥有）

```text
Task / TaskPlan / TaskNode / Execution（Assistant 目标、计划、等待的唯一智能计划真理源）
└─ InvocationReceipt（Tool 副作用与完成标准冻结）
```

Credit Ledger（实际消费唯一真理源）属于引擎层通用计费能力（`aaf-framework` 的 `engine.credit`），与助理层平级，不专属 Assistant；AIGC 项目执行专业动作和 Assistant 执行 Tool 调用消费积分时共用同一记账真理源，AAF-113 不重复记账。

三条主线各自独立又互相引用：**内容树**（Project→Snapshot→Object→Version，AIGC 项目层）表达"项目里有什么"；**智能计划**（Task→TaskPlan→Execution，助理层）表达"Assistant 本次准备做什么"，不复制内容树；**交付证据**（manifest→Review→Publication，AIGC 项目层）表达"什么已经被验收和发布"。ProjectProgressSnapshot 是聚合查询这三条主线的只读投影（对应 DEC-03、DEC-07）。

## 阶段流程总览

UI 结构化创建、对话创建和定时提示词最终进入同一项目链路；差异只在意图如何收集、BlueprintSnapshot 如何产生以及是否需要即时确认。一次性定时提示词只创建既有 canonical Assistant Task，不参与首次物化。

```text
CONFIGURING → MATERIALIZING → MATERIALIZED → CREATING → REVIEWING → DELIVERING
```

技术流程在生命周期内部进一步分为四段：

```text
Blueprint 初始化 Draft
    ↓
物化前 AI 辅助完善初始化字段（可选）
    ↓
确定性校验、冻结 Snapshot 与原子物化
    ↓ COMMIT
物化后 AI 辅助生成项目文档和 ProjectObject 内容（可选）
```

AI 辅助统一采用 `Proposal → Approval → TaskPlan → Candidate → Adoption` 内循环，但物化事务本身禁止模型调用。

### CONFIGURING（配置中）

**目标价值**：把用户意图收敛为可验证的初始化 Draft，并明确哪些事实由用户填写、哪些可以由 AI 提议。

- 已发布 CATALOG Blueprint 的 `projectInitialization` 在现有 JSONB 中选择和配置系统固定项目字段，以及项目自产文档、外部文档、品牌、渠道和默认策略；字段值写入 `AigcProject` 强类型列或对应 Ref，不新增初始化子表。
- 用户填写 `name` 与 `description`，选择品牌、渠道和精确版本外部文档；`briefJson` 和 `AigcProject.brief` 不再作为目标模型。
- 需要 AI 辅助的初始化字段先生成 ProposalItem candidate；用户可批量确认并调整单项目标指令，确认值才进入最终 ProjectSpecification。
- 物化前 AI 只生成字段候选和结构提案，不直接写 ProjectGraph。
- 退出依据：所有影响结构的必需字段已确认，引用资源版本可访问，初始化 Draft 具备确定性输入。

### MATERIALIZING（生成并冻结结构）

**目标价值**：把已确认的初始化 Draft 转为合法、可审计、可重复物化的 BlueprintSnapshot。

```text
CATALOG
→ CatalogBlueprintSnapshotProvider 解析已发布 Blueprint Definition

ASSISTANT_GENERATED
→ AssistantBlueprintProposal 经用户确认或有效结构授权

两种来源
→ BlueprintSnapshotValidator
→ BlueprintSnapshotNormalizer
→ BlueprintSnapshotFreezer
→ AigcProjectMaterializer
```

- CATALOG 是当前第一优先实现路径：先跑通已有已发布 Blueprint 的 `projectInitialization`、动态槽位、Action、关系、项目文档引用和空 ProjectGraph。
- ASSISTANT_GENERATED 由 Assistant 生成项目级 Proposal，系统校验后冻结；它不自动创建或发布目录 Blueprint。
- Assistant 只提交逻辑 key 和受控 Schema；数据库 ID、relation canonical semantics、执行绑定版本、权限、hash 和确认回执均由系统计算。
- Materializer 只消费已验证 Snapshot，在一个事务内创建 Project、ConfigSnapshot、Profile/Channel/Document Ref、ProjectObject、Relation 和首条 Revision。
- 退出依据：Snapshot 已确认、canonical hash 可重现且物化事务提交成功。

### MATERIALIZED（结构已就位）

**目标价值**：项目身份、上下文引用和空对象图已经完整持久化，但尚未把 AI 生成结果冒充为正式内容。

- `ProjectMaterializedEvent` 在事务提交后触发项目级文档与对象生成 Proposal；模型调用不进入 Materializer 事务。
- Brief、项目规范、风格指南等纯项目上下文不作为 ProjectObject，统一由 `projectInitialization.documents` 和项目级 `AigcProjectDocumentRef` 管理。
- `AigcProjectDocumentRef.selectedDocumentVersionId` 是当前项目采用/冻结版本；AI 新文档版本先作为 candidate，确认后再更新指针。
- ProjectGraph 只包含需要对象依赖、候选治理、审核或交付的生产对象。
- 退出依据：用户发起创作，或 auto 模式在既有授权范围内创建首个生成 Task。

### CREATING（创作中）

**目标价值**：通过可追溯的计划和候选循环形成项目文档与生产对象内容。

- 项目文档和 ProjectObject 内容共用 `Proposal → Approval → TaskPlan → Candidate → Adoption`，只在 target adapter 和结果落点上不同。
- 用户确认的是生成范围、精确上下文版本、目标指令和成本，不审批内部完整 System Prompt。
- 每个目标的 `generationInstruction` 可单独调整并版本化；修改后旧 proposalHash/确认失效。
- 手动模式按计划确认执行；auto 模式只在授权范围内自动生成 candidate，默认不自动采用。
- Assistant 生成文本/文档 candidate，专业 Tool 生成媒体 candidate；外部等待和恢复继续使用 canonical Task/Execution 与 InvocationReceipt。
- 退出依据：全部必需生产对象和必需项目上下文满足采用/冻结合同。

### REVIEWING（审核中）

- 送审已采用的生产对象与项目上下文精确版本。
- 审核依据 manifest、DocumentVersion/MediaVersion、ProjectRevision 和 Prompt/执行审计事实做出通过或退回决定。
- 退出依据：审核通过。

### DELIVERING（交付中）

- 冻结 DeliverableSet manifest 与 evidenceHash；纯上下文文档除非被交付合同显式纳入，否则不进入交付清单。
- 发布交付物，并按账本展示实际积分。
- 退出依据：manifest 已冻结并发布。

两种 Blueprint 来源从 Validator 开始共享下游，物化后不再按来源分叉。当前实施顺序是工程优先级：先固化 CATALOG 确定性闭环，再叠加 AI 辅助初始化、项目文档生成和 ASSISTANT_GENERATED；Blueprint 可视化编辑器最后实施，不阻塞已定义 Blueprint 的真实跑通。

## 真理源与聚合边界

| 事实 | 真理源 | 设计约束 |
|---|---|---|
| 项目身份、协作模式、粗粒度治理 | `AigcProject` | `description` 是用户原始目标；不保存完整 Brief 正文 |
| Blueprint 初始化字段定义 | 冻结 Snapshot 的 `projectInitialization.fields` | binding/semanticRole 由服务端目录校验 |
| 项目初始化字段值 | `AigcProject` 固定通用字段列 | Blueprint 只配置字段可见性/必填/AI 策略；Snapshot 保存物化时副本 |
| 项目上下文文档 | `AigcProjectDocumentRef` + 精确 `DocumentVersion` | `selectedDocumentVersionId` 不随 Document 当前版本漂移 |
| 文档摘要与主题 | `DocumentVersionSemanticMetadata` | 按 documentVersionId 派生，可人工覆盖，不写入 DocumentRef |
| 项目结构基线 | `AigcProjectConfigSnapshot` | 两种来源共用不可变 Snapshot |
| Assistant Blueprint 提案 | `AssistantBlueprintProposal` | Proposal 只负责提议/确认，不承担执行调度 |
| 内容对象树和非层级图关系 | `AigcProjectObject` / `AigcProjectRelation` | 仅承载生产对象；parentId 与 relation 分工明确 |
| 对象默认动作 | `AigcProjectObject.defaultActionKey` | 全部适用 Action 及策略仍来自 Snapshot.actions |
| 对象类型扩展数据 | `AigcProjectObject.payload` | 不写模板规则、Action、Prompt 或 userInstruction |
| 生成提案与目标指令 | `ProjectGenerationProposal` / canonical TaskPlan | `generationInstruction` 版本化并长期保存 |
| 最终 Prompt 来源与 hash | 不可变 `PromptManifest` | 完整 Prompt 按审计级别加密限期保存 |
| Assistant 目标、计划和等待 | Task / TaskPlan / TaskNode / Execution | 唯一智能计划真理源 |
| 专业动作和供应商执行 | ExecutionRun / AigcTask | 与 TaskPlan 分层，不复制为 TaskNode |
| Tool 副作用和完成标准 | InvocationReceipt | 冻结关联和 CompletionCriteria，不复制 Prompt |
| 实际消费 | Credit Ledger | costUsed 仅为可重建投影 |
| 交付证据 | manifest / evidenceHash / Review / Publication | 精确引用采用/选定的资源版本 |

## 阶段流程与字段来源详解

本节按 `AigcProjectLifecycle` 六阶段（[阶段流程总览](#阶段流程总览)）叙述创建到交付全过程每一步做什么、生成哪些对象，并用同一个贯穿示例说明：**用户小李通过 UI 选择"图文推广套餐" CATALOG Blueprint 创建一个项目**。原有按字段逐条盘点"谁填、谁算、来自哪里"的结论保留在每个阶段内，发现的冗余与死数据一并记录，指导后续清理。

### CONFIGURING（配置中）——做什么：把用户意图收敛成配置命令

用户通过 UI 结构化表单填写项目基础身份和配置意图，系统据此构造一条物化命令，本阶段不生成任何 `AigcProjectObject` 或版本内容，只产出"待提交的配置参数"。

字段来源（用户创建表单）：

| 字段 | 归属 | 谁填/谁定 | 说明 |
|---|---|---|---|
| `name` | `AigcProject.name` | 用户必填 | 项目名称 |
| `description` | `AigcProject.description` | 用户可选 | ≤500 字的项目简介/原始目标，供后续生成项目级 Brief |
| `projectTypeCode` | `AigcProject.projectTypeCode` | 用户必选 | 决定后续生成的领域上下文 |
| `blueprintCode`/`blueprintVersion` | `AigcProject.blueprintCode`/`blueprintVersion` | 用户选 Blueprint 后系统带出 | 记录所选模板及版本 |
| `productionMode` | `AigcProject.productionMode` | 系统按项目类型给默认值，用户可改 | 生产严谨度（standard/short_drama/motion_comic） |
| 渠道选择 | `AigcProjectChannelRef` 关联表 | 用户多选 | 目标发布渠道 |
| 品牌档案 | `AigcProjectProfileRef` 关联表 | 用户可选 | 引用已有品牌库 |
| Blueprint 配置的通用字段 | `AigcProject` 固定列 | 用户填写或 AI 提议后确认 | 由 `projectInitialization.fields` 选择启用，不创建动态列 |
| 项目文档/外部文档 | `AigcProjectDocumentRef` | 用户选择或物化后 AI 生成 | 使用 fieldKey、role 和精确 selectedDocumentVersionId |
| 封面 | `AigcProject.coverMediaVersionId`/`coverStatus` | 用户选上传或 AI 生成 | AI 生成在物化提交后执行 |
| `slotOverrides` | 快照 `projectSpecification` | 用户覆盖 | 受模板 min/max 与渠道约束校验 |

**示例**：小李填写项目名“618大促推广”和简介“面向年轻女性用户，突出五折力度和限时紧迫感”，选择“图文推广套餐”、渠道、品牌档案及带角色的产品说明文档。阶段一直接校验这些确定性输入；阶段二可先由 AI 提议 audience、keyFacts、styleGuidance 等初始化字段，确认后再进入同一物化命令。

### MATERIALIZING（生成结构中）——做什么：把配置命令解析成结构提案并校验

系统调用 `AigcConfigurationService.resolve()`，把上一步的命令和 CATALOG 模板结合，计算出完整的结构提案（`AigcProjectBlueprintSnapshotPayload`），本阶段只做计算和校验，尚未写入任何持久化的项目对象，产出是"待冻结的快照内容"。

字段来源（`AigcConfigurationService.resolve()` 系统计算）：

| 字段 | 来源方法 | 说明 |
|---|---|---|
| `sourceType`/`sourceRef` | `AigcProjectMaterializer.materialize()` | CATALOG 来源固定，指向具体 Blueprint 版本 |
| `snapshotHash` | `AigcProjectMaterializer.canonicalSnapshotHash()` | canonical JSON 的 SHA-256 |
| `projectSpecification.channels`（宽高比/时长上限/文案结构/免责声明） | `AigcConfigurationService.channelSnapshot()` | 系统查询渠道库得到；用户只选了渠道代号 |
| `projectSpecification.executionBindings` | `AigcConfigurationService.executionBindings()` | actionKey 到执行绑定的映射，现场按 `blueprint.getActionSpec()` 声明的动作逐一查询已发布 `AigcExecutionBinding`（`AigcProjectTypePackage` 已删除，不再经过兼容包） |
| `projectSpecification.package`（原兼容包 id/version/compatibilityResult） | 原 `AigcConfigurationService.packageSnapshot()` | **已删除**：`AigcProjectTypePackage` 整表及配套代码已确认全部字段可现场推导并删除，该子对象和产出方法一并移除，不再出现在新生成的快照里 |
| `projectSpecification.projectType.definitionVersion` | `AigcConfigurationService.projectTypeSnapshot()` | **死数据**：审计留痕，无代码读取 |
| `projectSpecification.blueprint`（原始 `slotTemplateSpec`/`relationSpec`/`actionSpec`/`deliverableSpec`/`processPolicy`） | `AigcConfigurationService.blueprintSnapshot()` | **真冗余**：与本 payload 顶层已解析的 `slotTemplates`/`relationSpecs`/`actions`/`deliverableSets`/`processPolicy` 是同一份 Blueprint 事实的两种形态（一份原始未解析、一份类型化），违反单一真理源；清理时应直接删除该子对象，不产生第二份拷贝 |

**示例（续）**：系统从“图文推广套餐”读取 `projectInitialization`（项目简介、产品说明、Brief 项目文档策略）以及 `concept`/`package`/`image`×3/`video`×1/`copy` 生产槽位；渠道约束决定视频激活和媒体规格，Action 现场解析已发布执行绑定。该提案仍是内存计算结果，系统校验 projectInitialization、动态数量、关系、Action 和引用版本后才允许冻结。

### MATERIALIZED（结构已就位）——做什么：冻结结构基线，原子生成真实的对象节点

结构提案通过校验后，系统在一个事务内**原子完成**：冻结不可变快照（写入 `AigcProjectConfigSnapshot`）、创建 `AigcProject` 实体本身、按快照的 `slotTemplates` 批量生成真实的 `AigcProjectObject` 节点、装配 `ProjectContextSnapshot` 与 `ObjectReadiness` 供后续查询。这是本设计里"结构第一次从计算结果变成持久化事实"的关键一步。

字段来源（每个 `AigcProjectObject` 的字段来源，`AigcProjectMaterializer` 物化循环）：

| 字段 | 谁定 | 来源 |
|---|---|---|
| `objectType`/`stableKey`/`blueprintTemplateKey`/`instanceNo` | 系统按 Blueprint 槽位模板批量生成 | `AigcProjectMaterializer` 物化循环 |
| `contractRole` | Blueprint 模板预设，系统写入 | `AigcBlueprintSlotTemplateSpec.defaultContractRole` |
| `semanticRole`/`outputContract` | Blueprint 模板预设，系统写入；创建后只能靠新快照修订，不直接改字段 | 同上 |
| `parentId` | 系统按模板父子关系计算 | `AigcProjectMaterializer` |
| `status`（EMPTY/CANDIDATE_AVAILABLE/ADOPTED） | 初始 EMPTY，此后系统重建，不接受直接写 | `AigcProjectObjectMaturityProjector` |
| `source`（blueprint/user/assistant/workflow/import） | 创建时决定：模板来的填 `blueprint`，手动追加填 `user` | — |
| `title` | 系统按模板 `displayNamePattern` 生成默认值，用户可后续改 | — |
| `defaultActionKey` | Blueprint compiler 显式解析默认 Action | 写入 `aigc_project_object.default_action_key`，不依赖 action 数组顺序 |
| `payload` | Blueprint `initialPayload` | 只承载对象类型特有实例扩展数据，不复制模板规则 |

**示例（续）**：事务创建 `concept`、`package`、`image.01/02/03`、按条件激活的 `video.01` 和 `copy` 等生产对象，全部初始为 EMPTY；同时写入品牌、渠道、外部资料等 Ref。Brief 不进入 ProjectGraph，待提交后按 `projectInitialization.documents[fieldKey=brief]` 生成项目级 DocumentVersion candidate。

用户在此后唯一的主动结构入口是"追加对象"：从当前快照 `slotTemplates` 中 `userAddable=true` 的模板里选一个补实例（比如小李觉得三张图不够，想再加一张），不能凭空定义任意类型。

### CREATING（创作中）——做什么：为每个对象节点反复生成候选、决策采用

围绕 `MATERIALIZED` 阶段生成的每一个对象节点，持续执行"创建 candidate → 比较 → 采用（或继续生成）"的循环，这是内容真正被生产出来的阶段。

字段来源（交付物生成，即每个对象节点内容的产生方式）：

| 数据 | 归属 | 谁生成 |
|---|---|---|
| 文本/文档正文 | DocumentVersion + ObjectVersion candidate | 当前人工手写（`recordTextCandidate`/`recordDocumentCandidate`）；Assistant 落地后与人工共用同一 Tool，不建两条路径 |
| 图片/视频 | MediaVersion + ObjectVersion/ref candidate | 当前用户填明确 Prompt 调用专业媒体 API；Assistant 落地后可代填 Prompt |
| 采用/否决决策 | `adoptedVersionId` 指针 | 用户决策，不自动化 |

**示例（续）**：物化提交后，系统按已确认计划生成 Brief DocumentVersion candidate；小李确认后，Brief DocumentRef 的 `selectedDocumentVersionId` 指向该版本。随后 `concept`、`image.*`、`video.01` 和 `copy` 按 readiness 生成各自 candidate；媒体继续形成 ExecutionRun + MediaVersion + ObjectVersion，重新生成产生新 candidate，不覆盖已采用版本。

### REVIEWING（审核中）——做什么：送审，独立于内容生产者的把关决定

全部必需生产对象和必需项目上下文都已采用/冻结后，用户送审。审核精确引用 ProjectObject adoptedVersionId 和 ProjectDocumentRef selectedDocumentVersionId，不按文档当前版本漂移。

**示例（续）**：小李确认必需项目上下文和全部必需生产对象后点击“提交审核”，系统创建 review 记录并冻结送审范围；审核角色查看精确版本后通过。

### DELIVERING（交付中）——做什么：冻结交付清单，正式发布，展示真实成本

审核通过后，系统冻结 manifest 与 evidenceHash，发布交付物，并聚合展示项目实际积分消耗。

**示例（续）**：系统按 `package` 节点（交付内容包）精确引用其下 `image.01/02/03`、`video.01`、`copy` 当前 adopted 的 `ObjectVersion` ID，冻结成一份不可变 `manifest`，计算 `evidenceHash`。项目状态变为 `DELIVERING`。小李在项目头部看到"本项目实际消耗 128 积分"（由 `AigcProjectProgressEvidenceAdapter` 按全部关联 `CreditTransaction` 结算流水聚合得出，已扣除 `image.02` 第一次生成失败的自动退款）。

### 已确认的 ProjectSpecification 收敛

- `projectSpecification` 必须收敛为类型化 `AigcProjectSpecification`，不再使用 `Map<String, Object>` 作为目标合同。
- 该类型只承载项目级输入事实：项目类型标识、生产模式、已确认初始化值、渠道快照/版本引用、执行绑定版本引用和 `slotOverrides`。
- 删除 `projectSpecification.blueprint`；Blueprint 规则只存在于 payload 顶层类型化 `slotTemplates/objectSpecs/relationSpecs/actions/deliverableSets/processPolicy/projectInitialization`。
- 删除无消费的 `package` 与 `projectType.definitionVersion` 子对象；不得为兼容历史 Map 保留双读或双写。
- `AigcProject` 强类型字段是当前项目事实，`AigcProjectSpecification` 是物化时冻结副本；后续项目字段变化按 ProjectRevision/新快照规则处理。
- 此项归 #11301；未完成前 #11301 不得标记完成，但不改变 AC-01 已有 CATALOG 功能链可继续静态收口的事实。
- **新发现待处理**（2026-09-17 独立审查）：`AigcProjectConfigSnapshot.compatibilityResult` 仍是 `Map<String,Object>`，`AigcProjectMaterializer` 每次物化硬编码 `{compatible:true, blueprintId, blueprintVersion}`，`AigcProjectService` 结构修订与查询继续复制/暴露该字段。这是 `projectSpecification` 之外的第二份兼容事实，且当前值无实际校验消费，与"`compatibilityResult` 可现场推导、不需要预持久化"的既有结论冲突。清理属于独立字段变更（涉及实体、快照写入路径、查询 API 和前端），超出本轮 #11301 最小范围，留待专项任务或代码审查阶段处理。

### 待处理事项索引

- `slotOverrides` 后端命令层已支持，前端创建表单未暴露对应输入框，属于已知未启用能力，不是缺陷。
- `budgetTier`/`qualityTier` 已于 2026-09-16 整体删除（数据库列、命令层字段、`projectInitialization` 白名单、种子数据、前端类型全部移除）：其驱动槎位数量的机制（`countByBudgetTier`/`countByQualityTier`）无法表达真实的实例级依赖场景且从未被前端启用，字段本身也无其他用途，判断为不必要的复杂度而非待启用能力。
- `AigcDomainExtension`（领域扩展）整张表及全部关联代码、字段、种子数据已删除，替代方案见下方"领域规则的替代承载方式"。

### 领域规则的替代承载方式（AigcDomainExtension 删除后）

`AigcDomainExtension` 原本设计承载两类不同性质的信息，删除后按性质分别替代，不再用独立表和 `domainExtensionId` 关联：

**硬性规则**（`ruleSets`/`validators`/`actionConstraints`，如"宣传声明必须有证据支撑"）——已确认这类字段设计的确定性校验从未接入任何执行框架（`validators` 字段里的动作标识如 `property.fact.validate` 全仓搜索无对应校验器实现），当前无法提供代码层面的强制门禁。若未来需要真正的硬性门禁，应作为独立的、不依赖 `domainExtension` 概念的校验框架设计（参考 `ArtifactCompletionEvaluator` 按 `outputContract` 类型分派判定的模式），不复用本次删除的结构。

**软性参考信息**（`knowledgeRequirements`/`profileSchemaExt`/`roleRecommendations`，如"房地产项目建议准备资质证、价格表、推广条款"）——改为项目关联知识库或参考文档：

```text
项目创建时可选关联一个知识库（engine.knowledge 既有能力）
或上传若干参考文档（AigcProjectDocumentRef，已有关联表，见"初始配置"阶段字段来源）
    ↓
知识库/文档内容用自然语言描述行业要求、常见规则、需要准备的材料
    ↓
Assistant 生成内容时通过既有 RAG 检索或直接引用这些参考资料
    ↓
生成结果尽力符合这些要求（软约束，由用户/审核角色最终把关，与"候选驱动完成"原则一致）
```

这个方式不需要独立的 `AigcDomainExtension` 表、`domainExtensionId` 外键关联、管理后台 CRUD 和发布工作流；用户在"初始配置"阶段的"参考资料"环节直接上传或选择知识库即可，不新增字段。约束力比原设计的"硬性规则"设想更弱（软性提示而非确定性拒绝），但比当前实际生效的约束力（零，因为硬性规则从未被任何代码执行）更强，是净提升。

`AigcExecutionBinding.channelCode`/`AigcProjectTypePackage.domainExtensionId` 原本用于按领域扩展匹配不同执行绑定的能力同步移除；`AigcConfigurationService.constrainCount()` 中按 `extension.getActionConstraints()`/`getChannelOverrides()` 限制槽位数量的计算逻辑（曾是真实可执行、只是从未被种子数据触发的完整实现）已随之删除，槽位数量约束现在只由渠道自身的 `copyStructure` 规则计算，不再有领域扩展这一层。

**数据库 schema 处理**：按当前开发期原则（`tasks.md`"实施原则"：开发期 schema 按人类明确指令直接修改原始 SQL，不新增迁移文件），`aigc_domain_extension` 表本身（含索引、注释）及 `aigc_project_type_package.domain_extension_id`、`aigc_project.domain_extension_code`/`domain_extension_version`、`aigc_project_config_snapshot.domain_extension_version`、`aigc_execution_binding.domain_extension_code` 全部列已直接从 `v7__aigc_schema.sql` 迁移文件中删除，不是保留列定义只停业务读写；进入共享或发布环境前需重新确认 Flyway checksum 与前向迁移策略。

### aigc_project_type_package 整表删除（resolve() 改为现场校验）

深入核查发现 `AigcProjectTypePackage` 剩余字段（`domainExtensionId` 删除后）**全部可从 `AigcProjectBlueprint`/`AigcProjectType`/`AigcExecutionBinding` 现场推导，没有任何独立信息**：

- `projectTypeId`/`productionMode`：`validateCompatibility()` 原有的强制一致性校验本身证明这两个字段的合法值完全由 `blueprint.getProjectTypeCode()`/`blueprint.getProductionMode()` 决定，存储是纯冗余；
- `channelSpecIds`：种子数据生成逻辑本身就是"按 `projectType.default_channels` 过滤已发布渠道"这条 SQL 现场算出来的，运行时存储的只是这次计算的快照；
- `executionBindingIds`：同样由种子数据"按 `blueprint.action_spec.actions` 过滤已发布执行绑定"现场算出，且真实使用点 `AigcExecutionBindingResolver`（"仅解析项目快照固定的动作执行绑定版本"）本身就是运行时按 `actionKey` 现场匹配最高优先级绑定的机制，不依赖预先存好的 ID 列表；
- `compatibilityResult`：不是纯展示缓存，`AigcConfigurationService.requireCompatiblePackage()` 曾用它做硬性门禁（`compatible!=true` 拒绝），但这个值本身是 `validateCompatibility()` 纯逻辑计算的结果（无人工审核环节），门禁条件可以在 `resolve()` 入口现场重新判断，不需要预先持久化。

**已执行的重构**：`AigcConfigurationService.resolve()` 改为直接查询链路——`requireProjectTypeByCode(command.projectTypeCode())` → `requireBlueprint(command.blueprintVersionId())` → `requireCompatible()` 现场校验类型与生产模式一致性（`blueprint.status=published` 天然承担原来"未发布不可用"的门禁语义，不再需要 `compatibilityResult` 这个中间态）→ `channelPool()` 现场从 `projectType.getDefaultChannels()` 过滤已发布渠道 → `executionBindings()` 改为对每个 `actionKey` 调用 `AigcExecutionBindingRepository.findByActionKeyAndStatusOrderByPriorityDesc()` 现场取最高优先级绑定。原本依赖 `AigcProjectTypePackage` 才能表达的"发布"工作流（DRAFT→PUBLISHED，需要运营先创建兼容包再发布才能使用）不再存在，`AigcProjectBlueprint` 自身的 `status=published` 是唯一门槛。

`AigcProjectTypePackage` 实体、Repository、Service、Controller、Resource、三个 VO/DTO，以及依赖它的 `AigcExecutionBindingCompatibilityPort`/`Adapter`（发布时校验执行绑定的跨子域端口，随发布工作流一起失去用途）已全部删除；`aigc_project_type_package` 表定义、种子数据、相关权限点（`aigc:project-type-package:*`）、`EntityDef` 语义 UI 定义已同步从既有 SQL 文件中直接删除。

## 模块落点

### aaf-api AIGC 项目模块

现有包 `com.xuejiai.aaf.module.ai.aigc` 负责：

- BlueprintSnapshot 解析、校验、哈希和持久化；
- `AigcProjectMaterializer` 的原子 ProjectGraph 物化；
- ObjectReadiness、ProjectProgressSnapshot 和项目语义目录；
- ObjectVersion、DocumentVersion、MediaVersion candidate 登记；
- ExecutionRun/AigcTask、Activity 和积分关联；
- 项目只读及确定性写 API；
- ProjectSchedule CRUD、到期记录认领、触发协调和 schedule/task 状态投影。

### aaf-framework 智能运行时

现有 Assistant 运行时负责：

- canonical TaskPlan 创建、修订、调度和 TaskNode 依赖；
- `AWAITING_EXTERNAL` 状态与 InvocationReceipt；
- 外部完成事件消费、补偿扫描和新 Dispatch 恢复；
- Assistant Tool 执行画像、Skill/Role/policy 交集和审计；
- 复用 `ScheduledTaskExecutor` 运行唯一的 ProjectSchedule due scanner；不在智能运行时新增另一套 scheduler。

框架模块通过接口依赖项目能力，不反向依赖 aaf-api 实体。aaf-api 在启动层提供端口实现和事件适配。

### webui

- UI 增量方案以 [ui-design.md](ui-design.md) 为准，复用现有 NewProjectLauncher、ProjectWorkbenchHeader、ContentActionBar、结构/图谱视图、对象 Dialog/Canvas、交付面板和状态栏；
- TanStack Query 获取项目、current Task、进度、readiness、候选、Run 和语义投影；
- Zustand 只保存 focusedObject、面板状态和未提交表单；
- 项目级批量生成调用 Assistant Task API；current Task 未终结时按 ProjectProgressSnapshot 禁用批量入口，不以 AG-UI 流状态判断；
- 生成图像、重新生成、重新编辑和添加内容等确定性微调调用 Project/Execution/专业媒体 API，并以 ObjectReadiness.allowedActions 决定可用性；
- SSE 到达后失效对应 Query，不在浏览器推进 Task 状态；
- 项目头部打开定时任务 Sheet；触发后的 canonical Task 在既有 Assistant 对话与项目进度中显示同一状态。

## BlueprintSnapshot 设计

### 持久化模型

复用 `AigcProjectConfigSnapshot`（持久化实体，字段与实现一致，不再展开）。快照内容载荷真实类名为 `AigcProjectBlueprintSnapshotPayload`（简称 BlueprintSnapshot）。目标载荷由类型化 `AigcProjectSpecification` 与顶层 `projectInitialization/slotTemplates/objectSpecs/relationSpecs/outputContracts/actions/deliverableSets/processPolicy/snapshotVersion` 组成；顶层解析结果是 Blueprint 规则与对象结构的唯一快照表达。

当前源码已实现类型化 `AigcProjectSpecification`：只冻结项目类型 code/version、生产模式、渠道版本 ID、执行绑定版本、已确认初始化字段和 `slotOverrides`。`AigcConfigurationService` 不再组装临时 `snapshotJson`，Snapshot 不再保存原始 Blueprint、`package` 或 `projectType.definitionVersion` 副本；后续结构修订继续复用同一类型化合同。

设计约束：

- `sourceType=CATALOG` 时 sourceRef 指向已发布 Blueprint 及版本；
- `sourceType=ASSISTANT_GENERATED` 时 sourceRef 固定为 `assistant-proposal:{proposalId}@{proposalVersion}`；
- `project_id + revision_no` 唯一；同项目首次 revision 为结构基线；
- snapshotHash 对 canonical JSON 计算 SHA-256，包含全部影响物化、上下文 readiness 和完成的字段；
- repository 不提供原地更新快照载荷的方法；结构变化创建新 revision；
- ProjectRevision 与新快照 revision、projectVersion、graphRevision 和确认/授权凭据关联。

### 快照生成端口

目标边界拆为两种 Provider 和一条共享链：

```text
CatalogBlueprintSnapshotProvider
AssistantBlueprintSnapshotProvider
    ↓
BlueprintSnapshotValidator
→ BlueprintSnapshotNormalizer
→ BlueprintSnapshotFreezer
→ AigcProjectMaterializer
```

当前真实代码只实现 CATALOG：`AigcConfigurationService.resolve()` 查询目录 Blueprint，`AigcProjectMaterializer` 内嵌部分校验并拒绝非 CATALOG，数据库约束也只允许 CATALOG。共享 Validator/Normalizer/Freezer 由 #11302 建立；#11325 提供类型化目录 Definition 输入；AssistantBlueprintProvider/Proposal 由 #11321 接入并复用该链。设计不得把这些目标能力描述成已实现。

### 统一校验

共享 Validator 至少校验：

1. source、ProjectSpecification 和 projectInitialization 完整性；
2. object localKey 唯一、objectType 已注册、父级存在且无环；
3. semanticRole、outputContract、defaultActionKey 和 payloadSchema 合法；
4. relationType 已注册，端点、方向和无环约束有效；
5. actionKey、执行绑定、requiredContextKeys、权限与 outputContract 匹配；
6. REQUIRED/OPTIONAL/EXCLUDED、DeliverableSet 和 ProcessPolicy 一致；
7. 文档 fieldKey/role/referenceType/精确版本和数量约束合法；
8. CATALOG 引用版本已发布，Assistant Proposal 已确认或具有有效结构授权；
9. canonical JSON 与 snapshotHash 可重现。

校验完成后两种来源使用同一个 Freezer 和 `AigcProjectMaterializer.materialize(...)` 事务。

## ProjectGraph 设计

### ObjectSpec 与物化字段

目标 `AigcResolvedObjectSpec`（简称 ObjectSpec）包含 `stableKey`/`blueprintTemplateKey`/`instanceNo`/`objectType`/`displayName`/`parentKey`/`orderNo`/`contractRole`/`semanticRole`/`outputContract`/`adoptionPolicy`/`defaultActionKey`/`userInstructionJson`/`payloadSchema`/`initialPayload`。当前源码已将旧 `schemaJson` 更名为 `initialPayloadJson`，Materializer 只把该值写入对象 payload，并通过 Blueprint Action 的 `isDefault` 显式解析 `AigcProjectObject.defaultActionKey`；尚缺独立 `payloadSchema` 合同，以及把默认动作直接纳入 resolved ObjectSpec 的编译结果。该剩余范围仍归 #11325。

`AigcProjectObject`（持久化实体）同步移除 `planned_phase` 列对应的业务语义；`semantic_role`/`output_contract` 两列与实现一致，`semanticRole` 是 ProjectGraph 一等字段，`outputContract` 是从不可变 ObjectSpec 物化的只读投影。创建后修改结构合同时必须通过 ProjectRevision 和新快照版本，不直接写散落字段。

### 层级和关系

`AigcProjectMaterializer` 只写 `parentId` 表达层级，不再持久化 contains relation。图查询通过 parentId 派生 contains edge。关系语义映射由 `AigcProjectRelationSemantics`（简称 ProjectRelationSemantics）承担，字段结构（`canonicalLayer`/`direction`/`allowedSourceSemanticRoles`/`allowedTargetSemanticRoles`/`acyclic`/`affectsReadiness`/`configurationAllowed`）与真实实现一致，8 种 relationType（`contains`/`derives`/`constrains`/`composes`/`variant`/`references`/`orders`/`execution_depends`）映射与核心 layer 分类（domain/reference/story_order/execution）均已核实与实现一致，环检测在物化时对 `acyclic=true` 的关系类型真实执行（`AigcProjectMaterializer.validateAcyclicRelations()`）。

- BlueprintSnapshot 只提交 relationType；系统写入 canonical layer。
- source 固定表示上游/来源，target 固定表示下游/结果。
- `AigcProjectRelationView` 增加 `layer`，后端与前端 API 类型保持一致。
- 移动对象只更新 parentId 并执行同项目、单父、无环和 graphRevision CAS 校验。

### ProjectObject 内容成熟度

保留现有 status 列作为可重建投影，值统一为：

```text
EMPTY
CANDIDATE_AVAILABLE
ADOPTED
```

`ProjectObjectMaturityProjector` 根据 adoptedVersionId 和有效 candidate 重建。contractRole 表达 EXCLUDED；ObjectReadiness 表达 ready/blocked/active；Review、manifest 和 Publication 表达交付状态。任何服务不得再向 status 写入其他领域状态。

### 项目级上下文文档与 Brief

Brief、项目规范、风格指南、术语表等面向全项目且不进入交付清单的文档，不作为 ProjectObject；它们由冻结 Snapshot 的 `projectInitialization.documents` 定义，并通过项目级 `AigcProjectDocumentRef` 绑定稳定文档身份和采用版本。

```text
projectInitialization.documents[key=brief]
    ↓ generationMode=AI_ASSISTED | USER_PROVIDED | NONE
AigcProjectDocumentRef
├─ fieldKey = brief
├─ referenceType = PROJECT_DOCUMENT
├─ documentId
├─ selectedDocumentVersionId
├─ role = brief
└─ contextNote
```

- 删除目标模型中的 `briefJson` 和 `AigcProject.brief`；创建页“项目简介”写入 `AigcProject.description`，仅表达用户原始目标。
- AI 生成 Brief 时创建或追加 DocumentVersion candidate；确认后才更新 `selectedDocumentVersionId`，Document 当前版本不能自动成为项目采用版本。
- 外部资料使用 `EXTERNAL_REFERENCE` 和精确 `selectedDocumentVersionId`；`fieldKey` 对应 Blueprint 初始化字段，`role` 使用通用英文机器码。
- `docType` 从 Document 读取；summary/topics 按 documentVersionId 进入 `DocumentVersionSemanticMetadata`，不复制到 DocumentRef。
- 项目上下文 Action 通过 `requiredContextKeys` 依赖 Brief 等文档；生产对象之间仍通过 dependencyTemplateKeys/Relation 依赖。
- 文章、文案、脚本等需要对象级 readiness、候选治理、审核或交付的文档继续作为 ProjectObject。

项目文档和生产对象共用 Action、ExecutionRun、Tool、幂等、积分和回调基础设施；生成结果分别写入 DocumentVersion candidate 与 ObjectVersion candidate。项目文档的新版本采用由项目文档决策记录审计，不重建 Brief ObjectVersion 链。

当前源码已删除 `briefJson`、`AigcProject.brief` 和 Blueprint brief slot，并在创建页/Materializer 统一接入结构化 `projectInitialization.documents`。剩余 ProjectContext 注入由 AAF-115 DOMAIN_CONTEXT 收口。

## ObjectReadiness 设计

### 查询合同

真实类名 `AigcObjectReadiness`/`AigcObjectReadinessState`（简称 ObjectReadiness/ReadinessState），字段结构（`projectObjectId`/`actionKey`/`state`/`dependencyObjectIds`/`inputVersions`/`blockedReason`/`allowedActions`）与七态枚举均已核实与实现一致，查询入口为 `AigcProjectApi.readiness(projectId, scope)`。

### 判定顺序

对每个范围内对象按以下顺序判定：

1. EXCLUDED 或未选 OPTIONAL 不进入范围；
2. 存在 active reservation/Run → ACTIVE_RUN；
3. 存在待决 candidate 且本次未明确要求重生成 → PENDING_CANDIDATE；
4. 存在 adopted 且未明确要求重生成 → SATISFIED；
5. action/binding/权限/预算/安全策略不可用 → BLOCKED_BY_POLICY；
6. 任一必需上游无可用版本 → BLOCKED_BY_DEPENDENCY；
7. 当前动作需要且缺少确认 → AWAITING_CONFIRMATION；
8. 其余 → READY，并返回冻结 inputVersion refs。

可用上游版本选择：adopted 优先；同一已授权 current TaskPlan 中唯一有效 candidate 次之，但不自动 adopted；多个 candidate 必须暂停选择。阶段一尚无 canonical TaskPlan，"已授权 current TaskPlan"这一前提天然不成立，因此阶段一期间任意待决 candidate（一个或多个）均归为 BLOCKED_BY_DEPENDENCY，要求先完成采用；这是该规则在无 TaskPlan 场景下的自然结果，不是对目标架构的偏离。专业调用创建时再次执行同一判定并冻结 version ID，避免检查后使用时变化。

readiness 不写回 ProjectObject，不由 Assistant 或浏览器计算。

## Assistant 编排设计

### 上下文装配

真实接口 `ContentStudioProjectContextProvider.load(Long projectId, Long focusedObjectId, InvocationContext context)`（参数名 `InvocationContext` 与本节早期草案用词 `AssistantInvocationContext` 不同，是同一个既有类型，非命名冲突）。`ProjectContextSnapshot` 包含 requirement.md 规定的紧凑字段和当前项目使用的语义子集。完整对象、版本正文、Run、candidate、素材和语义定义通过只读 Tool 查询。当前实现方 `DefaultContentStudioProjectContextProvider` 已存在但无调用方接入 Assistant 实际对话链路（详见 tasks.md 记录的 AAF-115 框架缺口：`PromptLayerKind` 缺少动态业务域事实层）。

### TaskPlan 生成规则

- 只读状态问答使用直接 Execution；首次副作用前 promotion 为 Task。
- TaskNode 表达 Assistant 业务工作包，不按每个项目对象机械拆分。
- Assistant 在 TaskPlan 中表达文本创作、文档形成、专业 Tool 调用、候选比较和结果汇总等步骤。
- ObjectReadiness 是计划输入；TaskPlan 不缓存为第二项目依赖图。
- 每个调用冻结 projectVersion、graphRevision、objectId、actionKey、inputVersionIds、CompletionCriteria 和 ConfirmationEvidence。
- 有效并行度为：

```text
min(taskPlan.maxParallelism, projectPolicy, budgetWindow, providerLimit, readyWorkPackages)
```

并保持 TaskPlan 既有 1..8 上限。

### ASSISTANT_GENERATED BlueprintSnapshot 提案流程

无目录 Blueprint 创建仍必须在物化前形成合法 BlueprintSnapshot。Assistant 只创建版本化 `AssistantBlueprintProposal`，不直接写 Project/Object/Relation：

```text
对话或表单收集 ProjectSpecification
    ↓
Assistant 通过类型化 Tool 生成逻辑 objectSpecs/relationSpecs/actions/projectInitialization
    ↓
BlueprintSnapshotValidator + Normalizer
    ↓
展示结构、项目上下文、交付范围、确认门和预计成本
    ↓
用户确认或校验既有结构自主授权
    ↓
冻结 sourceType=ASSISTANT_GENERATED Snapshot
    ↓
共用 AigcProjectMaterializer
```

`AssistantBlueprintProposal` 至少持久化 proposalVersion/hash、rawProposal、normalizedSnapshot、validationResult、validatorVersion、semanticCatalogVersion、来源 Task/Execution、确认或授权回执和过期时间。客户端只提交 proposalId/version/hash，Materializer 重新加载并校验，不接受任意 Snapshot JSON。

- Assistant 只输出逻辑 key；relation canonical semantics、执行绑定版本、权限、预算和 Snapshot hash 由系统计算。
- Proposal 修改后产生新版本和 hash，旧确认失效。
- manual 模式默认等待用户确认；auto 仅在明确结构授权范围内自动确认，仍保留同一 Proposal/Approval 审计。
- ASSISTANT_GENERATED Snapshot 默认只属于当前项目；保存为目录 Blueprint 必须走独立 Draft、编辑和发布流程。
- 两种来源从 Validator 开始共用 Normalizer、Freezer、Materializer 和物化后生成链，不建立 MANUAL/EMPTY 来源或第二快照表。

### 统一生成 Proposal 与 Prompt

物化前初始化字段、物化后项目文档和生产对象生成共用三步内循环：

```text
生成计划和每项目标指令
→ 批量确认执行范围（单项可调整）
→ 执行并产生 candidate，随后采用或重新生成
```

用户确认的是目标、资料版本、目标指令、成本和采用策略；完整 Prompt 在执行前由可信 Blueprint/Action 层、ProjectContext、目标指令和模型适配层即时编译。

- `ProjectGenerationProposal/GenerationProposalItem` 保存可编辑的 `generationInstruction`、itemVersion/hash、上下文键和成本。
- 批准后 `TaskPlan.TaskNode` 冻结 generationInstruction/proposalItemRef/instructionHash；不得复用 description。
- `AigcExecutionRun.effectiveInput/contextSnapshot` 保存 Proposal 引用、Snapshot/图修订和精确资源版本，不复制文档正文。
- 每次物理模型调用永久保存不可变 PromptManifest 和 hash；完整 Prompt/Messages 按审计级别加密限期保存。
- 表单按钮和对话确认解析为同一结构化 Approval 命令；自然语言“确认”不能绕过 proposalId/version/hash。

### 文本与文档 Tool

Assistant 在自身模型执行中生成正文，再调用仅负责校验和持久化的 Tool。**本节接口签名与真实实现存在已知偏差，尚未协调统一**：本节设计 `ProjectArtifactCommandApi.recordTextCandidate`/`recordDocumentCandidate` 返回 `CandidateRef`，命令类含 `actionKey` + `CompletionCriteria criteria`；真实实现 `ProjectArtifactCommandApi`（同名接口已存在）返回 `AigcObjectVersionView`，命令类为 `AigcTextCandidateCommand`/`AigcDocumentCandidateCommand`，缺 `actionKey` 和 `criteria` 字段，且多一个 `InvocationContext` 参数。该偏差影响 `#11308`（AWAITING_EXTERNAL 完成判定）依赖的 `CompletionCriteria` 链路，是否改代码匹配设计、改设计匹配代码，还是接受当前实现作为最小可行版本，留待专项讨论确认。

Tool 校验来源 Task/Execution/toolCall、权限、readiness、schema 和 outputContract；不会再次调用模型生成文本。

### 专业媒体 Tool

Assistant 根据项目上下文形成 Prompt 和参数，调用现有图像、音频或视频专业 Tool。本节设计的 `ProfessionalExecutionReceipt` 抽象未落地为独立类；媒体执行实际复用既有 `AigcExecutionRun`/`AigcTask` 机制（`objectId`/`actionKey`/`costCredits` 等字段承担同等语义），返回稳定 Run 引用供后续查询完成状态。

图像在 AAF-113 中必须端到端落盘；音频/视频仅在已注册可用 Tool 存在时暴露，缺失时返回稳定 capability unavailable，不创建替代状态模型。

## generationMode 与双入口

### 模式命令

```java
public record ChangeProjectGenerationModeCommand(
        Long projectId,
        String mode,
        Integer expectedProjectVersion,
        String idempotencyKey) {}
```

`ProjectGenerationModeService` 只修改 AigcProject policy：

- manual → auto 前校验已确认目标、范围、预算上限和质量门；
- auto → manual 后拒绝新的自动提交，已提交 Run 按快照完成；
- 不跨聚合修改 TaskPlan；Task 变化使用 Task 控制 API；
- Run effective input 保留提交时 generationMode 与授权证据。

### UI 路由

```text
需要理解/创作/组织 Prompt/规划后续
→ POST Assistant Task API

目标、输入、处理方式均确定
→ Project / Execution typed API
```

Assistant Task API 接收结构化 objective、projectId、focusedObjectId 和 explicitScope，不伪造聊天文本。项目级批量生成始终进入该 API；同项目 current Task 处于规划、执行、外部等待、恢复、待确认或暂停等非终态时，批量按钮禁用并链接到原 Task。确定性 API 包括生成明确图像、重新生成、重新编辑、添加内容、结构编辑、手工版本保存、采用/否决候选和明确 Run 控制；新内容默认形成 candidate，不自动替换 adopted。

## 项目 AI 定时任务设计

### 最小数据模型

首期一条记录只表达一个 RUN_ONCE 时间点，不拆分 schedule/fire 两张表：

```java
public record ProjectSchedule(
        Long id,
        Long projectId,
        Long ownerId,
        String assistantId,
        String conversationId,
        String prompt,
        Instant executeAt,
        ZoneId timezone,
        ProjectScheduleStatus status,
        Instant nextAttemptAt,
        String linkedTaskId,
        long dispatchFence,
        Instant claimUntil,
        String lastError,
        long version) {}

public enum ProjectScheduleStatus {
    SCHEDULED,
    PAUSED,
    DEFERRED,
    DISPATCHING,
    TRIGGERED,
    FAILED,
    CANCELED
}
```

持久化到 `aigc_project_schedule`。owner/org/workspace 使用既有 BaseEntity scope；`executeAt` 保存 UTC Instant，`timezone` 保存 IANA ZoneId 供输入解释和展示。关键约束与索引：

- `(status, next_attempt_at, execute_at)` 支持 due scan；
- `project_id, status` 支持项目列表和 current Task 冲突检查；
- `linked_task_id` 在非空时唯一；
- `prompt` 非空且受既有输入长度与内容安全限制；
- optimistic version 保护编辑、暂停、恢复和取消；
- `TRIGGERED/FAILED/CANCELED` 不再修改业务输入，只保留审计。

用户一次选择多个时间点时，`CreateProjectSchedulesCommand` 在同一事务中创建多条独立记录；任一时间点无效则全部拒绝。每条记录后续独立触发和管理。

### API 合同

```java
public record CreateProjectSchedulesCommand(
        Long projectId,
        String prompt,
        List<ProjectScheduleTimePoint> timePoints,
        String assistantId,
        String conversationId,
        Integer expectedProjectVersion,
        String idempotencyKey) {}

public record ProjectScheduleTimePoint(
        Instant executeAt,
        String timezone) {}
```

项目路由：

| API | 语义 |
|---|---|
| `GET /api/aigc/projects/{projectId}/schedules` | 返回时间、时区、schedule 状态、linkedTaskId 和当前 Task 摘要 |
| `POST /api/aigc/projects/{projectId}/schedules` | 原子创建一个或多个时间点 |
| `PATCH /api/aigc/projects/{projectId}/schedules/{id}` | 仅在未触发状态用 CAS 修改 prompt 或未来时间 |
| `POST .../{id}/pause` | SCHEDULED/DEFERRED → PAUSED |
| `POST .../{id}/resume` | PAUSED → SCHEDULED 或 DEFERRED，并重新校验未来/到期语义 |
| `DELETE .../{id}` | 软取消；不删除 linked Task 或执行历史 |

首期不提供 run-now、cron、recurrence、handler/actionType 或模型可调用的 schedule Tool。

### 统一调度接线

在既有 `sys_scheduled_task` 注册一个内置 `project_assistant_schedule_scan`，使用 FIXED_DELAY 运行 `ProjectScheduleDueScanner`：

```text
ScheduledTaskExecutor
→ DistributedLeasePort 保证 scanner 集群单实例
→ 查询 status in (SCHEDULED, DEFERRED) 且 nextAttemptAt/executeAt <= now
→ 短事务 FOR UPDATE SKIP LOCKED 原子 claim
→ status=DISPATCHING, dispatchFence++, claimUntil=now+lease
→ ProjectScheduleDispatcher.dispatch(scheduleId, fence)
```

统一 scheduler 只负责周期唤醒。项目归属、冲突、权限和 Assistant 触发均由 AIGC 项目应用服务处理。scanner 重启后自然重新发现到期记录；`DISPATCHING` 超过 claimUntil 且 linkedTaskId 为空时可由同一扫描器重新认领。

### 到点触发

`ProjectScheduleDispatcher` 每次处理都重新读取权威事实：

1. 校验 fence、schedule 状态和未产生 linkedTaskId；
2. 以 schedule owner 身份重新校验 org/workspace/project 访问权、项目 lifecycle 和 Assistant 可执行性；
3. 查询 ProjectProgressSnapshot 的 current Task；若存在未终结 Task，将状态改为 DEFERRED 并设置短延后 nextAttemptAt，不创建竞争 Task；
4. 读取当前 projectVersion/graphRevision、预算、安全和强制确认策略，不使用创建 schedule 时的 readiness 或版本快照；
5. 以 `project-schedule:{scheduleId}:{executeAt}` 作为幂等键调用现有结构化 Assistant Task API：

```text
objective = 原样保存的 prompt
projectId = schedule.projectId
assistantId / conversationId = schedule 绑定值
triggerSource = PROJECT_SCHEDULE
triggerRef = scheduleId
```

6. 保存返回的 `linkedTaskId` 并将 schedule 标记为 TRIGGERED。

若进程在 Task 创建后、schedule 回写前中断，重复 dispatch 使用同一幂等键取得原 Task，不创建第二个 Task。可恢复基础设施错误保留 DISPATCHING 供 lease 到期重试；权限撤销、项目不可写、Assistant 不可执行或输入被安全策略拒绝则标记 FAILED 并保存稳定原因。

ProjectSchedule 只负责创建 Task。Task 后续照常装配 ProjectContextSnapshot 和 ObjectReadiness，生成 canonical TaskPlan，调用专业 Tool，并进入 AWAITING_EXTERNAL 或 AWAITING_CONFIRMATION。schedule 不自动采用、送审、发布、完成或归档。

### 状态投影与对话

`ProjectScheduleView` 返回 schedule 状态、lastError、linkedTaskId，以及从 canonical Task 查询的只读摘要。TRIGGERED 后不把 Task 状态回写到 schedule：

```text
ProjectScheduleView
├─ schedule[id, executeAt, timezone, status, lastError]
└─ task[id, status, progress, waitingReason]  // linkedTaskId 非空时查询
```

项目页 schedule Sheet 和绑定的 Assistant 对话都使用该投影。Assistant 对话将该 Task 标记为“定时触发”，其计划、执行、等待、确认和完成状态仍来自 canonical Task。Activity/SSE 只失效 schedule、Task 和 ProjectProgressSnapshot Query。

## 完成合同与持久等待

### CompletionCriteria

**本节 sealed interface 设计与真实实现存在已知偏差**：本节设计两分支 `SubmissionAccepted`（无字段）+ `ArtifactSet`（`expectedObjects`+`failurePolicy`）；真实实现 `ArtifactCompletionContract.CompletionCriteria` 只有单一 record，对应 `ArtifactSet` 语义（`expectedObjects: Map<Long,OutputContract>` + `failurePolicy`），缺 `SubmissionAccepted` 分支。`OutputContract` 五值枚举（`INLINE_OBJECT_VERSION`/`DOCUMENT_VERSION`/`MEDIA_VERSION`/`COMPOSITE_MANIFEST`/`REFERENCE_ONLY`）与实现一致。

`ArtifactCompletionEvaluator` 对每种合同查询权威资源：

| 合同 | 判定 |
|---|---|
| INLINE_OBJECT_VERSION | 目标结构化 JSON ObjectVersion candidate 已持久化，不含自由文本正文 |
| DOCUMENT_VERSION | 自由文本 DocumentVersion 与对应 ObjectVersion candidate 均存在 |
| MEDIA_VERSION | MediaVersion 与 ObjectVersion/ref 均存在 |
| COMPOSITE_MANIFEST | manifest ObjectVersion 与 evidenceHash 均存在 |
| REFERENCE_ONLY | 类型化引用存在且通过权限与完整性校验 |

Run SUCCEEDED 但资源缺失仍视为未满足；资源存在但专业 Run 未终态时继续等待。部分成功按调用时冻结的 failurePolicy 处理。

自由文本版本遵循以下边界：

- 首次 AI 生成成功、内容变化后的重新生成、用户手工修改后明确提交，才创建或追加 DocumentVersion 与 candidate。
- 输入过程、预览、比较、采用、否决不创建 DocumentVersion；同一幂等请求重放不重复创建 candidate。
- 规范化后的 title/content/format 与当前版本完全相同时复用当前 DocumentVersion；不同执行仍可形成独立 ObjectVersion candidate。
- 同一项目对象的全部自由文本 candidate 必须共享同一个 Document 身份；ObjectVersion 只保存精确版本元数据和治理状态，不保存正文。

### 状态转换

向 canonical Execution 与 TaskNode 状态加入 `AWAITING_EXTERNAL`：

```text
RUNNING
→ Tool receipt SUCCEEDED + awaitExternal=true
→ AWAITING_EXTERNAL
→ 当前 Dispatch 结束并释放 lease
→ terminal/candidate event 或 recovery scan
→ 校验 current task/plan/execution/fence
→ 创建新 Dispatch
→ 查询 Run 与资源
→ COMPLETED | RETRYABLE | FAILED | PAUSED
```

Task 根保持 RUNNING。InvocationReceipt 保持 SUCCEEDED，因为 Tool 副作用已完成；等待属于 TaskNode/Execution。

### 外部完成端口

外部完成信号消费、补偿扫描恢复属于 AAF-115（`aaf-framework`）已实现的框架能力（`#11506`：durable take-over、fresh attempt、AgentState 保留等机制），AAF-113 只作为消费方通过统一 `AssistantCommandPort → AssistantApplicationService → AgentExecutionPort` 接入，不在 AAF-113 范围内重新定义端口接口或类名。

不依赖 SSE 推进 Task；SSE 仅让前端重新查询。

## ProjectSemanticCatalog 设计

### 编译来源

```text
固定核心枚举注册表
+ 已发布 ProjectType
+ 当前 BlueprintSnapshot
+ relationType 目录
+ Tool/Action metadata
→ ProjectSemanticCatalog
→ semanticCatalogVersion
```

不新建人工维护的第二语义数据库。`ProjectSemanticCatalogCompiler` 生成 canonical JSON 和版本哈希，可按 projectId/version 缓存；发布或快照 revision 变化时失效。

### Tool 合同

```java
public record InspectProjectSemanticsRequest(
        Set<String> semanticKeys,
        Set<SemanticDetail> include) {}

public record ProjectSemanticDefinition(
        String semanticCatalogVersion,
        String key,
        String definition,
        Map<String, Object> schema,
        Map<String, Object> constraints,
        Set<String> allowedActions,
        Map<String, Object> completionEvidence,
        boolean deprecated) {}
```

scope 不由模型传入，由 InvocationContext 绑定。Tool 可见性是 Resource Catalog、Assistant policy、Role allowlist、SkillVersion requirement、项目上下文和用户权限的交集。版本不一致、未知或废弃值 fail closed。

## ProjectProgressSnapshot 与积分

### 投影合同

真实类名 `AigcProjectProgressSnapshot`（简称 ProjectProgressSnapshot），字段结构（`projectId`/`projectVersion`/`graphRevision`/目标与范围/TaskPlan 进度/readiness 列表/执行引用/候选决策/待确认/schedule 摘要/交付进度/积分摘要）与实现一致，聚合查询不拥有底层状态，服务端返回 projectionVersion/updatedAt，前端通过 Query cache 和 SSE invalidation 刷新。

### 积分关联

统一关联字段：

```text
projectId
canonicalTaskId
assistantExecutionId
toolCallId
executionRunId / aigcTaskId
candidateVersionId
creditTxId
```

Agent token metering 与媒体 CreditTransaction 都必须携带可用关联。`ActualCreditSummary` 分别聚合 estimated、reserved、settled、refunded，netActual=`settled-refunded`。无法归属的交易显示待归集，不按零成本处理。AigcProject.costUsed 由投影重建或异步回填，不参与结算真理判断。

## 交付证据

- DeliverableSet completion 只计算 REQUIRED 和已选 OPTIONAL；
- 每个纳入对象必须有 adoptedVersionId、有效 validationStatus，且无活动 reservation；
- manifest 冻结 graphRevision、includedObjectIds、精确 ObjectVersion、资源引用、validationStatus 和 evidenceHash；
- ObjectVersion 及其 DocumentVersion/MediaVersion 引用不可原地漂移；
- adopted 指针变化使依赖旧 manifest 的 Review/Publication stale，但不改写历史事实。

## API 与 Tool 面

### 只读

| 能力 | 输出 |
|---|---|
| inspect_project | 版本、模式、规范摘要、allowed actions |
| inspect_project_objects | 对象树、关系 layer、语义字段、采用版 |
| inspect_project_readiness | 目标范围的权威 ObjectReadiness |
| inspect_project_runs | Run/AigcTask、终态、错误和成本引用 |
| inspect_project_candidates | candidate、adopted、资源引用和来源 |
| inspect_project_materials | 授权范围内文档、媒体和参考素材 |
| inspect_project_semantics | 版本化语义定义和完成证据 |
| inspect_project_progress | ProjectProgressSnapshot |
| list_project_schedules | ProjectScheduleView 列表；仅项目页面和应用服务调用，不作为 Assistant Tool |

### 受控写入

| 能力 | 边界 |
|---|---|
| set_project_generation_mode | 专用 CAS 模式命令 |
| persist_project_blueprint_snapshot | 校验并冻结当前项目快照 |
| revise_project_structure | ProjectRevision + 新快照版本 + 原子图变更 |
| record_project_text_candidate | Assistant 正文登记，不二次生成 |
| record_project_document_candidate | DocumentVersion + ObjectVersion candidate |
| generate_project_media | 调用已注册专业 Tool |
| adopt/reject_project_candidate | 版本 CAS、确认和原因 |
| control_project_run | 控制明确 Run |
| freeze_delivery_manifest | 精确版本与 evidenceHash |
| create/update/pause/resume/cancel_project_schedule | 类型化项目 API；只管理一次性提示词时间点 |

模型不得传入 workspace、bindingId、执行器类型、relation layer 或其他项目 scope。每个写入从 InvocationContext 取得 Task/Execution/toolCall 身份，并重新校验版本、权限、readiness、预算和幂等键。

## 安全与失败处理

- 项目、语义、资源和积分查询均按 tenant/org/workspace/user/project 隔离；
- 内容和 Tool 输入通过既有安全策略；auto 不降低安全等级；
- 同 idempotencyKey 同参数返回原结果，同 key 异参冲突；
- graph/project/adopted 指针使用乐观锁；
- Task 失败、聊天删除、模式切换或页面关闭不删除项目资源；
- schedule 到点按 owner 当前权限执行，创建时权限不构成永久授权；
- schedule 提示词只作为 Assistant objective，不解析为内部 actionType、handler 或 cron；
- 资源落盘与 Activity/完成信号使用 after-commit 语义，扫描负责补偿；
- 恢复失败达到策略上限后转 PAUSED 并提供可解释原因，不靠跳过测试或降低完成标准继续。

## 数据迁移与切换

### Schema 与数据迁移

按当前 Flyway 发布状态选择尚未部署迁移或新增前向迁移，禁止修改已部署迁移：

- `aigc_project_config_snapshot` 增加 source_type、source_ref、snapshot_hash，并回填现有目录来源；
- snapshot JSONB 迁移为类型化 v2 payload，保留精确版本引用；
- `aigc_project_object` 增加 semantic_role、output_contract，并按已发布 Blueprint/objectType 目录回填（原设计含 `planned_phase` 列，该字段已确认为无实际业务消费的纯装饰性字段并从代码移除，`aigc_project_object.planned_phase` 数据库列已于 2026-10-14 随 AAF-112 审查整改（B-06）从 v7 SQL 删除，不再有占位写入，详见"阶段流程与字段来源详解"）；
- status 回填为 EMPTY/CANDIDATE_AVAILABLE/ADOPTED，并收紧 CHECK；
- 清理持久 contains relation，改由 parentId 派生；
- relation layer 按 relationType 目录重算并收紧约束；
- canonical TaskNode/Execution 状态约束加入 AWAITING_EXTERNAL；
- 为 Task/toolCall/Run/candidate/creditTx 关联增加必要列和索引；
- 新建 `aigc_project_schedule`，包含一次性时间点、IANA 时区、提示词、状态、claim/fence、linkedTaskId、scope 与审计字段，并注册唯一的 due scanner 系统任务。

所有回填先提供 dry-run 统计和不可映射报告；存在未知语义时 fail closed，不猜测默认值。

### 一次性切换

```text
新文本/文档/图像闭环与恢复扫描通过门禁
→ UI、Assistant Tool、动作权限原子切换
→ 停止接受旧批量入口新请求
→ 在途执行完成或取消
→ 结清 reservation/settled/refunded
→ 删除旧服务特判、seed、binding、调用方和测试
→ 保留历史引用只读审计
```

不保留 fallback、dual-write 或长期并行入口。切换失败在发布事务内回滚入口配置；已生成的新资源保留为可审计 candidate，不做破坏性删除。

## 可观测性

统一日志和指标标签：

```text
projectId / taskId / executionId / toolCallId
executionRunId / aigcTaskId / objectId / candidateVersionId
snapshotHash / semanticCatalogVersion / generationMode
creditTxId / completionKind / recoverySource
projectScheduleId / scheduledExecuteAt / scheduleStatus / triggerSource
```

核心指标：

- snapshot validation failure；
- readiness 各状态数量和阻塞原因；
- TaskNode 外部等待时长、事件恢复/扫描恢复比例；
- Run 成功但资源缺失次数；
- candidate 形成、采用和多候选暂停次数；
- settled/refunded/netActual 与待归集交易；
- 旧入口拒绝和在途清理数量；
- schedule 到期延迟、DEFERRED 时长、claim 恢复、幂等重放和触发失败原因。

## 实施阶段

### 阶段一：已定义 CATALOG Blueprint 确定性闭环

当前第一优先级不是建设 Blueprint 编辑器，而是把现有已发布 Blueprint 从初始化表单到交付真实跑通：

```text
读取已发布 Blueprint Definition
→ 渲染 projectInitialization 字段和文档输入
→ 校验 ProjectSpecification 与精确引用版本
→ resolve 动态数量、激活条件、Action 和关系
→ 冻结 CATALOG BlueprintSnapshot
→ 单事务创建 Project/Refs/空 ProjectGraph/Revision
→ 确定性编辑、专业媒体、candidate、采用、进度、积分和交付
```

阶段一不调用 Assistant，不引入 MANUAL/EMPTY 来源或空快照 fallback；但必须直接采用最终字段边界，包括 `defaultActionKey` 显式列、payload 只承载 initialPayload、项目级 DocumentRef 和无 `briefJson/AigcProject.brief` 的目标合同。内置 Blueprint 可继续由 seed/类型化配置维护，编辑器不是 CATALOG 闭环的前置条件。

### 阶段二：CATALOG 项目的 AI 辅助初始化与生成

阶段一稳定后，在同一个 CATALOG 项目链上增加 AI，而不改变物化事务：

```text
物化前：初始化字段 Proposal → 批量确认/调整 → 写入最终 ProjectSpecification
物化：Validator → Freezer → Materializer（无 AI）
物化后：项目文档/对象 Proposal → TaskPlan → candidate → 采用
```

本阶段落地 ProjectGenerationProposal、项目文档 target、PromptManifest、表单/对话统一确认、manual/auto 授权边界和 DocumentVersionSemanticMetadata。先证明 AI 能在已知结构上可靠工作，再允许 AI 同时设计结构。

### 阶段三：ASSISTANT_GENERATED 与完整对话协作

在统一 Proposal、Validator、Prompt 和候选链稳定后实现无目录 Blueprint：

```text
AssistantBlueprintProposal
→ 系统校验/规范化
→ 用户确认或结构授权
→ 冻结 ASSISTANT_GENERATED Snapshot
→ 共用 Materializer
→ 共用项目文档和 ProjectObject 生成链
```

同时开放对话单点修改、项目级批量生成、manual/auto 连续推进、AWAITING_EXTERNAL 恢复和旧入口单路径切换。Assistant 不直接访问项目 Repository，不复制 readiness、Run、candidate 或交付状态。

### 阶段四：一次性 AI 定时触发

Assistant 闭环稳定后再实现 ProjectSchedule、统一 due scanner 和绑定对话状态。定时触发只创建阶段三已有的 canonical Assistant Task，不参与首次物化，也不引入 cron、recurrence 或第二执行器。

### 后置能力：Blueprint 可视化编辑器

Blueprint 编辑器在上述运行链路全部稳定后实施：

- 编辑的是类型化规则图，不是把模板保存成真实 AigcProject；
- 复用图画布和基础组件，不复用 Project 持久化模型；
- 支持 projectInitialization、动态数量、激活条件、Action、关系、DeliverableSet、流程策略和解析预览；
- 发布前调用与运行时相同的 Blueprint compiler/validator；
- 不阻塞内置 Blueprint seed、CATALOG 创建、AI 辅助生成或 ASSISTANT_GENERATED 的交付。

### 阶段门禁

- 阶段一门禁：至少一份已定义 CATALOG Blueprint 完成初始化、原子物化、空图展示、确定性生成、candidate/采用和交付真实链路。
- 阶段二门禁：物化前字段 Proposal、物化后项目文档/对象 Proposal、PromptManifest 和 manual/auto 候选生成均复用阶段一事实。
- 阶段三门禁：无目录 Blueprint、对话修改、批量生成、外部等待和恢复共用阶段一 Validator/Materializer 与阶段二 Proposal/Prompt 合同。
- 阶段四门禁：schedule 幂等创建阶段三 Task，项目忙时 DEFERRED，项目页与对话显示同一 linkedTaskId。
- Blueprint 编辑器不属于前四阶段门禁；不得因编辑器未实现阻塞已定义 Blueprint 闭环。
- 最终发布仍执行 tasks.md 定义的 developer、review、acceptance 和 QA 门禁。

## 需求与设计映射

| 验收标准 | 设计组件 | 主要验证 |
|---|---|---|
| AC-01 | SnapshotService、Validator、Materializer | 目录来源原子创建 |
| AC-02 | Assistant proposal、统一 Validator | Assistant 来源权限与物化 |
| AC-03 | immutable snapshot、ProjectRevision、项目级 DocumentRef 选定版本 | 历史不可变与上下文版本不漂移 |
| AC-04 | ProjectObject 字段、RelationSemantics、Materializer | 树、layer、方向和成熟度 |
| AC-05 | ProjectReadinessService | 去重、范围、依赖和输入版本 |
| AC-06 | generationMode、TaskPlan | manual/auto 授权边界 |
| AC-07 | Assistant Task API、typed API、Query state | 双入口同一事实 |
| AC-08 | ArtifactCommandApi、CompletionEvaluator | 文本/文档/图像完成证据 |
| AC-09 | 专业 Tool capability gate | 音视频可用/不可用路径 |
| AC-10 | AWAITING_EXTERNAL、receipt、event/scan recovery | 持久等待和幂等恢复 |
| AC-11 | input version resolver、candidate CAS | candidate 输入与 adopted 分离 |
| AC-12 | SemanticCatalogCompiler、semantic Tool | 版本、权限和 fail closed |
| AC-13 | ProgressProjection、Credit Ledger correlation | 实际成本和进度 |
| AC-14 | cutover checklist | 旧入口不可达和在途结清 |
| AC-15 | DeliveryService、manifest/evidenceHash | 精确版本可重现 |
| AC-16 | ProjectSchedule、DueScanner、Assistant Task API、ScheduleView | 一次性触发、忙时延后、幂等和双端状态 |

## 不变量

- AIGC Project 聚合保留，ProjectObject 不承载 Task、Run 或通用项目治理对象。
- AigcProjectConfigSnapshot 是两种 Blueprint 来源共同的不可变基线存储。
- parentId 是层级唯一写源；relation layer 只能由 relationType 目录计算。
- semanticRole 和 outputContract 来自冻结 ObjectSpec。
- ProjectObject status 只表达 EMPTY/CANDIDATE_AVAILABLE/ADOPTED。
- readiness 由服务端实时派生，Assistant 和 UI 不自行计算。
- canonical TaskPlan 是唯一智能编排计划；专业执行不复制为 TaskNode。
- Tool accepted、资源完成、Task 完成和项目交付完成彼此独立。
- AWAITING_EXTERNAL 不持有 Agent 线程、Dispatch lease 或前端长连接。
- 积分账本是实际消费唯一真理源，costUsed 可重建。
- auto 不扩大范围，不自动采用、送审、发布、完成或归档。
- 新路径上线后一次性退役旧入口，不保留兼容双路径。
- 一条 ProjectSchedule 只表示一个 RUN_ONCE 时间点；不建立 ProjectScheduleFire 或用户 cron。
- 到点只创建既有 canonical Task；同项目 current Task 未终结时固定 DEFERRED。
- schedule 与 linked Task 分层展示，项目页和对话不复制 Task 状态。
