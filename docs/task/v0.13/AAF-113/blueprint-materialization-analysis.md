---
level: Practice
layer: Model
purpose: 分析 AAF-113 当前 Blueprint 物化机制的真实实现、能力边界与架构权衡，评估"模板即真实项目图、创建时克隆"的替代方案
status: active
version: 1.1.0
date: 2026-09-17
author: AaronZZH & Kiro
tags:
  - AAF-113
  - Blueprint
  - 物化
  - 架构评估
related:
  - design.md
  - requirement.md
---

# Blueprint 物化机制分析与架构权衡

> 本文记录了从备选方案推演到最终决策的完整过程。自 2026-09-17 起，`最终评估结论与实施方案` 及其后的补充决策已采纳；与这些结论冲突的前置章节仅保留为历史分析。`design.md` 仍是唯一实现依据，已采纳结论必须同步到设计和任务后再编码。

## 决策采纳状态

| 决策项 | 采纳结论 | 任务归属 |
|---|---|---|
| Blueprint 是否保存为真实项目并克隆 | 不采用；保留 `Definition → Snapshot → ProjectGraph` 三层边界 | #11325、#11326 |
| 目录 Blueprint Definition | 目标为类型化规则定义；现有五组 JSONB 可继续作为存储载体，但进入编译、发布校验和运行解析前必须收敛为单一类型化 Definition，不允许业务逻辑继续散读宽松 Map | #11325 |
| `projectSpecification` | 必须类型化，只保存项目类型、生产模式、初始化值、渠道、执行绑定引用和数量覆盖等项目级输入事实；删除与 payload 顶层重复的原始 `blueprint` 子对象及无消费的 `package`、`projectType.definitionVersion` | #11301 |
| 对象 payload | 只保存 `initialPayload` 对应的对象类型特有实例数据；模板规则、默认动作和 `userInstructionJson` 不进入 payload | #11325 |
| 默认动作 | 使用显式默认标记解析并物化到 `AigcProjectObject.defaultActionKey`，不依赖数组顺序 | #11325 |
| `budgetTier`/`qualityTier` | 连同数量规则、条件分支、数据库列、命令和前端字段整体删除，不作为编辑器预览维度恢复 | #11325、#11329 |
| Brief | 作为项目级上下文文档，由 `AigcProjectDocumentRef` 选择精确 `DocumentVersion`；不作为 ProjectObject，也不恢复 `AigcProject.brief` | #11325、#11327 |
| AI 与物化事务 | AI 只参与物化前 Proposal 和物化后 candidate；Materializer 保持无 LLM、单事务、确定性 | #11326、#11328 |

## 文档定位

本文回答三个问题：

1. `AigcProjectBlueprint` 当前真实记录了什么；
2. 从蓝图到初始项目的完整物化时序是什么，已核实的能力边界（含图状依赖支持）与已发现的实现缺口分别是什么；
3. 当前"抽象模板 Schema + 显式映射"的设计，与"模板即真实项目图、创建时克隆"的替代方案相比，各自的能力边界和维护成本如何权衡。

## Blueprint 当前记录的内容

`AigcProjectBlueprint` 持有五个独立 JSONB 字段，每个字段是一套**槎位类型层面的生成规则**，不是具体对象的数据：

| 字段 | 内容 | 作用 |
|---|---|---|
| `slotTemplateSpec` | 每条记录含 `templateKey`/`stableKeyPattern`/`objectType`/`parentTemplateKey`/`defaultCount`/`minCount`/`maxCount`/`countByProductionMode`/`semanticRole`/`outputContract`/`defaultContractRole`/`activationCondition`/`userInstruction`/`initialPayload` | 声明"这一类槎位该生成几个对象、什么条件下生成，以及对象实例的初始扩展数据" |
| `relationSpec` | `sourceTemplateKey`/`targetTemplateKey`/`type` | 声明"A槎位类型和B槎位类型之间有什么关系"，物化时展开为具体对象间的边 |
| `actionSpec` | `actionKey`/`targetTemplateKey`/`dependencyTemplateKeys`/`confirmationPolicy` | 声明"哪个动作对应哪个槎位类型" |
| `deliverableSpec` | `setTemplateKey`/`allowedSlotTemplateKeys` | 声明"哪个槎位是交付包，允许纳入哪些槎位" |
| `processPolicy` | `reviewRequired`/`publicationPolicy`/`confirmationGateKeys` | 审核发布策略开关 |

**结论**：Blueprint 不是"留白的项目模板"，是**生成算法的参数集合**——物化时系统读这五套规则，按规则"算出"应该生成多少个对象、每个对象是什么、彼此什么关系，不是简单复制粘贴。

## 完整物化时序（`AigcProjectMaterializer.materialize()`，已核实真实代码）

```text
【0 命令校验】sourceType 必须 CATALOG，sourceRef 格式，封面参数
【1 解析配置】configurationApi.resolve(...) —— 在 AigcConfigurationService 完成，
             产出已展开的具体对象列表（resolvedObjects）、具体关系列表（relations）、
             actions/deliverableSets/processPolicy 解析结果、executionBindings
【2 创建 AigcProject 本体】写入 name/projectTypeCode/blueprintCode/productionMode/
             description 等已启用的强类型初始化字段/status=CREATING/graphRevision=1
【3 处理封面】（如为上传模式）创建 MediaVersion，写 AigcProjectMediaRef
【4 冻结配置快照】snapshotPayload 打包 + 计算 hash，存入 AigcProjectConfigSnapshot
【5 写入关联】遍历 profiles → AigcProjectProfileRef；渠道 → AigcProjectChannelRef；
             参考文档与 Brief 精确版本 → AigcProjectDocumentRef
【6 创建对象节点（两轮循环）】
    第一轮：遍历 resolvedObjects，逐个创建 AigcProjectObject
           （payload = initialPayload，defaultActionKey 写显式列，此时 parentId 未设置）
    第二轮：再遍历一次，回填 parentId（两轮是因为父对象可能排在子对象之后，
           需要第一轮先把全部对象的 id 都建好）
【7 创建关系边】遍历 relations，逐条创建 AigcProjectRelation
             （source/target 已是具体对象 id）
【8 记录首条 ProjectRevision】标记本项目第 1 次修订
```

**关键事实**：全部 8 个阶段在同一个 `@Transactional` 方法内原子完成，从命令提交到数据库出现完整对象图，中间**没有任何 AI 参与、没有"生成后续项目规范"环节**。

## 已核实的能力边界

### 已支持：图状依赖（不限于父子树）

`relationSpecs()` 是双重循环——对声明了关系的两个槎位类型，各自匹配到的全部实例做笛卡尔积，逐对建边：

```java
for (var sourceKey : sources) {
    for (var targetKey : targets) {
        // 每一对都建一条关系
    }
}
```

配合 `validateAcyclicRelations()`（拓扑排序环检测，仅对声明 `acyclic=true` 的关系类型生效），当前物化机制**已支持任意模板间的图状关系声明**，不限于父子树，此项不需要额外设计。

### 已支持：数量可变（按生产模式动态调整）

```java
count = tierCount(raw, "countByProductionMode", productionMode, count);
```

同一槎位类型，根据项目实际的生产模式（如 standard/short_drama），物化出的对象数量不同。

> **决策更新（2026-09-16）**：原设计还支持 `countByBudgetTier`/`countByQualityTier`（按预算档/质量档动态调整数量），已确认删除。删除当时的理由是：这两个字段作为用户输入维度先保留，只是不再驱动槎位数量这项具体机制。**该结论已被推翻**——进一步确认 `budgetTier`/`qualityTier` 在删除数量驱动机制后已无任何实际用途（前端从未渲染对应输入框，只是硬编码默认值随表单提交；`activationCondition` 里的 `budgetTiers`/`qualityTiers` 判断分支同样是从未被种子数据触发的死代码），已于同日追加整体删除：`AigcProject.budget_tier/quality_tier` 数据库列、`AigcProjectInitializationSpec.FIELD_KEYS` 白名单两项、`AigcConfigurationResolveCommand`/`AigcResolvedConfiguration`/`AigcProjectMaterializeCommand`/`AigcProjectMaterializeDTO`/`AigcProjectUpdateDTO`/`AigcProjectVO`/`AigcProjectView` 等全部 record 字段、`AigcConfigurationService.isActive()` 的 `budgetTiers`/`qualityTiers` 判断分支、两个种子 Blueprint 的 `projectInitialization.fields` 定义、前端全部对应类型与表单字段，均已删除，不保留任何残留声明或死代码分支。

### 已支持：条件激活

`activationCondition` 按渠道/生产模式判断槎位是否激活，不满足条件时该槎位实例数量归零，不生成对象。

## 已发现的实现缺口

### 缺口一：`userInstructionJson` 已进入 Snapshot，但尚未被运行期消费

字段沿 `AigcBlueprintSlotTemplateSpec.userInstructionJson → AigcResolvedObjectSpec.userInstructionJson → AigcProjectBlueprintSnapshotPayload.objectSpecs` 保留；Materializer 不把它复制到 Object payload 是正确边界。真实缺口是 ProjectContext、Assistant 和动作执行链尚未按 stableKey/blueprintTemplateKey 从冻结 Snapshot 读取该指令并参与 Prompt 组装，此项由 #11304/#11328 收敛。

### 缺口二：AI 辅助规范与内容生成尚未接入物化前后流程

当前阶段一流程是“用户提交已确认配置 → 系统按规则展开空对象图 → 完成确定性物化”，没有 AI 扩写/细化。该边界本身不是缺陷：物化前由 #11328 基于 `description`、类型化 ProjectSpecification 和精确版本参考资料生成初始化 Proposal；物化后由 #11327/#11328 生成项目文档或 ProjectObject candidate。模型调用不得进入 Materializer 事务，也不得直接覆盖已确认项目字段。

> **补充设计输入（2026-09-16，待 #11327/#11328 落地时参考，本轮不实现）**：用户提出"`projectInitialization` 表单项能否有 AI 优化开关，物化后续阶段自动 AI 优化"。已确认此能力明确超出阶段一（`#11326`）范围，理由：`AigcProjectMaterializer` 架构约束为"无 LLM 参与、纯确定性事务"，AI 辅助初始化字段已被 `design.md` 划归阶段二"物化前：初始化字段 Proposal → 批量确认/调整 → 写入最终 ProjectSpecification"流程，依赖 `#11313` 门禁通过。当前 `AigcProjectInitializationSpec.FieldSpec.aiAssist` 字段已作为预留标记存在（种子数据 `description`/`background`/`targetAudience` 等字段已标记 `aiAssist:true`），但全仓核实确认无任何前端渲染代码或后端逻辑读取消费该标记——这是正确的现状，不是缺陷，因为消费方是阶段二能力。阶段二设计时应明确：①开关应是用户可见、可选触发（而非物化时自动强制优化，避免绕过用户对最终提交内容的确认权）；②触发时机与 manual/auto 模式的关系（同缺口二原有的待讨论问题）；③优化结果写回哪个字段、是否形成可比较的候选而非直接覆盖用户已填内容（呼应 AC-11"候选与正式采用保持分离"的既有原则）。

## 架构权衡：抽象模板 Schema + 显式映射 vs 模板即真实项目图 + 克隆

### 当前设计：两套并行词汇，靠 `resolve()` 做翻译

```text
模板层词汇                          真实对象层词汇
templateKey              ──映射──→  stableKey（按 stableKeyPattern 生成）
parentTemplateKey        ──映射──→  parentId（查父对象实例的真实 id）
sourceTemplateKey/target  ──映射──→  sourceObjectId/targetObjectId（笛卡尔积展开）
```

`resolvedObjects()`/`relationSpecs()` 是专门维护这套翻译的代码。`AigcProjectObject` 新增字段时，`slotTemplateSpec` 的 JSON schema 通常需要跟进对应字段，映射逻辑也要跟着改——同一个概念要在三处维护。

### 替代方案：模板本身是一个被标记的真实项目，创建时克隆其对象图

设想：`AigcProjectBlueprint` 不再是独立的抽象 JSON Schema，而是一个 `AigcProject`（标记为模板），其对象和关系就是真实的 `AigcProjectObject`/`AigcProjectRelation` 行；"创建项目"变成"克隆这个模板项目的对象图，生成新的 `stableKey`，重映射 `parentId`/关系引用"。

**优点**：
- 创建模板不需要手写嵌套 JSON，可以直接用项目工作台的图形化编辑器搭建（跟编辑真实项目体验一致）；
- 只有一套 Schema（`AigcProjectObject`/`AigcProjectRelation`），模板和真实项目复用同一套持久化逻辑和编辑 UI；
- 消除 `templateKey`↔`stableKey` 这类平行词汇转换代码。

**代价（真实存在，不能忽略）**：
- **数量可变**（`defaultCount`/`countByBudgetTier` 等）和**条件激活**（`activationCondition`）这两项能力是业务真实需要，替代方案不能凭空消除它们，只能把这些规则从"独立 JSON Schema"搬到"直接作为 `AigcProjectObject` 的可选字段"（仅模板模式下有意义，正常项目对象该字段恒为 null）；
- 克隆逻辑（读取节点的数量规则/条件规则，决定克隆几份、是否克隆、重新生成 `stableKey`、重映射关系引用）本身复杂度跟现在的 `resolvedObjects()`/`relationSpecs()` 相当，只是操作对象从"两套 Schema 之间的字段映射"变成"同一套 Schema 的两行记录之间的克隆"。

### 结论

替代方案能真正简化的是**"是否需要维护两套并行 Schema"**，不是"是否需要这套数量可变/条件激活规则本身"（这部分无论哪种方案都必须存在）。如果把规则字段直接加到 `AigcProjectObject`/`AigcProjectRelation` 上（模板专用、正常项目恒为 null），可以做到：

- 创建模板复用项目工作台图形化编辑器，不需要手写 JSON；
- 模板和真实项目共用同一套 Schema、编辑 UI、持久化逻辑；
- 只需要一套"克隆时展开"的逻辑，不需要维护 `templateKey`/`stableKey` 之间的平行词汇转换。

## 影响范围与实施建议

若采纳替代方案，需要重新设计的范围：

- `AigcProjectBlueprint` 及其管理界面（Controller/Service/Resource/CRUD）；
- `slotTemplateSpec`/`relationSpec`/`actionSpec`/`deliverableSpec` 四个 JSON Schema 的废弃；
- `AigcConfigurationService.resolve()` 核心逻辑改为"克隆模板项目对象图"；
- 现有种子数据（"图文推广套餐"等内置模板）需要迁移为"标记为模板的真实项目"形式；
- `AigcProjectObject`/`AigcProjectRelation` 新增模板专用字段（数量规则、条件激活规则）。

这是比本轮已执行的两次简化（删除 `AigcDomainExtension`、删除 `AigcProjectTypePackage`）范围更大的重构，涉及新的数据模型设计和管理界面重建，按协作红线"🔴 高风险（架构调整...≥5 文件跨模块）→ 完整派发 product→architect→developer→tester→qa"，需要先经过需求细化和技术设计评审，不应直接编码。本文档仅记录分析结论供后续评估，不代表已批准实施。


## 最终评估结论与实施方案

> 本节是结合现有设计文档和真实代码调用链形成的最终评估结论。确认后应先同步更新 `design.md`，再进入技术任务拆分和编码。

### 最终结论

**不以“真实 `AigcProject` 模板 + 创建时克隆项目图”替换当前物化机制。**

保留以下三层边界：

```text
目录 Blueprint 规则定义
    ↓ BlueprintCompiler 按项目配置解析
项目级不可变 BlueprintSnapshot
    ↓ AigcProjectMaterializer 确定性物化
运行期 ProjectGraph
```

当前方案需要优化，但其“规则定义、项目合同、运行实例”分层是必要的，不是可以通过直接克隆消除的重复抽象。

推荐方向是：

- 保留独立的 `AigcProjectBlueprint` 目录聚合；
- 将当前五组宽松 `Map<String, Object>` 收敛为类型化 Blueprint Definition；
- 增加发布期编译校验和解析结果预览；
- 建设面向“规则图”的可视化 Blueprint 编辑器，复用项目图编辑器的画布和基础组件，但不复用 `AigcProject` 持久化模型；
- 继续以 `AigcProjectBlueprintSnapshotPayload` 作为目录来源和 Assistant 来源汇合后的项目级唯一结构合同；
- 保持 `AigcProjectMaterializer` 为无 LLM 参与、可重复验证的确定性事务。

### 不采用真实项目克隆方案的原因

#### Blueprint 节点和项目对象语义不同

Blueprint 槽位表示的是“对象生成规则”，`AigcProjectObject` 表示已经生成的“具体对象实例”。

一个模板槽位可能因为以下规则生成零个、一个或多个项目对象：

- `defaultCount`；
- `countByProductionMode`；
- `slotOverrides`；
- `activationCondition`；
- 渠道 `slotConstraints`。

> 注：`countByBudgetTier`/`countByQualityTier` 已于 2026-09-16 删除，见上文"数量可变"章节的决策更新。此处论证结论（Blueprint 槎位与项目对象语义不同）不受影响，仅规则清单需同步。

因此，`templateKey` 与 `stableKey` 不是同一概念的重复命名：

- `templateKey` 标识生成规则；
- `stableKey` 标识解析后生成的具体项目对象；
- `blueprintTemplateKey` 保存实例到生成规则的来源关系。

将数量和激活规则放入 `AigcProjectObject`，只会使部分项目对象同时承担“规则节点”和“运行实例”两套不变量，并不会消除解析过程。

#### Blueprint 规则在物化后仍被运行期使用

BlueprintSnapshot 中的模板规则不是创建项目后即可丢弃的一次性参数。

当前 `AigcProjectService` 在项目运行期仍会使用：

- `slotTemplates.userAddable` 判断是否允许追加实例；
- `slotTemplates.userRemovable` 判断是否允许删除实例；
- `slotTemplates.maxCount` 校验追加数量；
- `stableKeyPattern` 生成追加对象的稳定键；
- `parentTemplateKey` 校验对象父级；
- `actions` 计算对象默认动作和 readiness 依赖；
- `deliverableSets` 校验自定义对象类型和交付包边界；
- `processPolicy` 控制审核、发布和确认策略。

因此，即使初始结构来自克隆，项目仍然需要冻结并保留这套规则合同。

#### 动作、交付和流程策略不能由纯对象图完整表达

`actionSpec`、`deliverableSpec` 和 `processPolicy` 不只是节点或关系属性：

- Action 还包含执行绑定、确认策略和模板级依赖；
- DeliverableSet 包含允许的槽位、自定义对象类型及完成策略；
- ProcessPolicy 是项目级审核、发布和自动动作策略。

把这些信息强行塞入 `AigcProjectObject.payload` 或关系元数据，会形成新的隐式 Schema，并降低类型安全和可验证性。

#### 目录 Blueprint 与业务项目的作用域不同

当前 `AigcProjectBlueprint` 使用 `@OrgIgnore`，属于可发布、可版本化的全局目录配置。

`AigcProject` 则属于具体组织、工作空间、所有者和用户，附带：

- 项目生命周期；
- 权限与租户隔离；
- 内容候选和采用状态；
- ExecutionRun 和任务；
- 审核与发布；
- 成本和积分；
- 项目列表及最近活动时间。

把目录模板保存成真实项目，会导致模板进入项目查询、权限、生命周期、成本和内容治理链，必须增加大量模板模式分支，反而扩大系统复杂度。

#### Assistant 来源不依赖目录模板实体

需求已经规定 BlueprintSnapshot 有两种来源：

- `CATALOG`；
- `ASSISTANT_GENERATED`。

Assistant 生成来源没有对应的目录模板项目，但必须与目录来源进入同一条 Snapshot 校验和物化链。因此，真正统一的边界应当是 BlueprintSnapshot，而不是某个可克隆的模板项目实体。

### 对现有缺口的修正判断

#### `userInstructionJson` 不是在解析阶段丢失，而是未被运行期消费

真实调用链为：

```text
AigcBlueprintSlotTemplateSpec.userInstructionJson
    ↓
AigcResolvedObjectSpec.userInstructionJson
    ↓
AigcProjectBlueprintSnapshotPayload.objectSpecs
```

项目结构修订生成新快照时，也会从上一版 `objectSpecs` 保留该字段。

准确的问题是：

- 物化时没有把它写入 `AigcProjectObject.payload`；
- 当前 ProjectContext、Assistant 和动作执行链没有读取它。

由于 BlueprintSnapshot 是项目结构合同的真理源，**不建议再复制一份到 `AigcProjectObject.payload`**。推荐在组装对象生成上下文时，按 `stableKey` 或 `blueprintTemplateKey` 从当前冻结快照读取，并与项目 Brief、对象上游 adopted 版本及用户本次指令合并。

这样可以避免 Snapshot 和 Object payload 出现两份可能漂移的提示词真理源。

#### AI 不应进入物化事务

“生成项目规范”和“结构物化”应保持边界：

```text
用户目标与参考信息
    ↓
形成或由 Assistant 补全 ProjectSpecification
    ↓ 用户确认或检查既有结构自主授权
生成 BlueprintSnapshot 提案
    ↓
校验并冻结 Snapshot
    ↓
确定性物化 ProjectGraph
    ↓
Assistant 创建内容 candidate
```

`AigcProjectMaterializer` 不调用 LLM，也不在数据库事务中等待模型结果。

对于目录 Blueprint，Assistant 可以在提交物化前协助补全 ProjectSpecification；对于无适用模板的项目，Assistant 直接生成 `ASSISTANT_GENERATED` BlueprintSnapshot。物化后的 Brief、创作指引和具体内容应通过 Assistant Task 生成 candidate，不直接改写结构基线。

### 推荐目标模型

建议引入类型化的目录定义模型，例如：

```text
AigcCatalogBlueprintDefinition
├─ slotTemplates
├─ relationTemplates
├─ actions
├─ deliverableSets
├─ processPolicy
└─ schemaVersion
```

其中：

- `slotTemplates` 是规则节点，不是 `AigcProjectObject`；
- `relationTemplates` 描述规则节点之间的展开关系；
- `BlueprintCompiler` 接收 Definition 和项目配置，输出类型化 BlueprintSnapshot；
- BlueprintSnapshot 包含解析后的 `objectSpecs` 和具体 `relationSpecs`；
- Materializer 只消费已经校验的 BlueprintSnapshot；
- 已发布 Blueprint Definition 不可原地修改，修改时创建新版本。

可视化编辑器编辑的是这张“规则图”。它可以复用项目工作台的画布、节点布局、连线和属性面板基础组件，但使用独立的 Blueprint API 和数据合同。

### 实施方案

#### 规范先行

先更新 `design.md`，明确以下决策：

- 不采用真实项目克隆；
- Definition、Snapshot、ProjectGraph 三层职责；
- `userInstructionJson` 的唯一真理源和消费方式；
- CATALOG 与 ASSISTANT_GENERATED 的统一边界；
- AI 生成 ProjectSpecification 的确认时机；
- Blueprint 发布不可变和版本升级规则。

该变更属于跨模块架构调整，按高风险流程评审后再编码。

#### 修复当前正确性缺口

优先完成不改变核心模型的修复：

- Blueprint 发布前执行静态结构校验，而不是只修改 `status`；
- 校验模板键、父子关系、数量上下限、关系语义、Action 引用、DeliverableSet 和 ProcessPolicy；
- 将 `userInstructionJson` 接入 ProjectContext 或对象动作上下文；
- 从 `projectSpecification` 删除与顶层类型化字段重复的原始 Blueprint 副本；
- 保持 Materializer 无 AI、单事务和确定性。

#### 类型化 Blueprint Definition

用明确的 Java 类型替代五组宽松 Map 解析：

- 定义类型化 Definition；
- 抽取独立 `BlueprintCompiler`；
- 将静态校验、按配置解析和 Snapshot 校验分离；
- 由发布流程执行静态校验；
- 由项目创建流程执行配置解析；
- 删除旧 Map 解析和重复字段，不保留双写或兼容路径。

#### 建设规则图编辑器

Blueprint 编辑器至少包含：

- 槽位规则节点编辑；
- 父子结构和非层级关系编辑；
- 数量策略与条件激活配置；
- Action、依赖和确认策略配置；
- DeliverableSet 与流程策略配置；
- 输入生产模式、渠道和 slotOverrides 后的解析预览；
- 发布前错误定位。

解析预览展示的是将要生成的具体 ProjectGraph，但不创建真实项目数据。

#### 接入 Assistant 生成来源

在类型化 Snapshot 合同稳定后接入：

- Assistant 生成 ProjectSpecification；
- Assistant 生成 BlueprintSnapshot 提案；
- 用户确认或结构自主授权校验；
- 与 CATALOG 来源共用 Snapshot 校验、冻结和物化器；
- 放开数据库当前仅允许 `CATALOG` 的来源约束；
- 物化后通过 canonical TaskPlan 生成内容 candidate。

#### 数据与测试

实施时直接迁移现有内置 Blueprint 种子数据，不保留新旧双路径。

至少覆盖以下测试：

- 不同生产模式、渠道约束和 slotOverrides 下的数量解析；
- 条件激活和用户数量覆盖；
- 一对多、多对多关系展开及环检测；
- Action 依赖和 readiness；
- DeliverableSet 追加对象约束；
- Blueprint 发布时拒绝非法定义；
- 相同输入生成相同 Snapshot hash；
- `userInstructionJson` 能进入对象生成上下文；
- CATALOG 与 ASSISTANT_GENERATED 经过同一 Snapshot 校验和物化链；
- Blueprint 不进入租户项目列表、生命周期、成本和内容治理链。

### 决策摘要

| 决策项 | 结论 |
|---|---|
| 是否替换当前物化机制 | 否 |
| 是否将 Blueprint 保存为真实 `AigcProject` | 否 |
| 是否保留 Definition → Snapshot → ProjectGraph | 是 |
| 是否保留动态数量和条件激活规则 | 是 |
| 是否继续使用宽松 Map 作为目标形态 | 否，改为类型化 Definition |
| 是否建设可视化模板编辑器 | 是，编辑规则图而非真实项目 |
| 是否把 `userInstructionJson` 复制到 Object payload | 否，从冻结 Snapshot 消费 |
| 是否允许 AI 参与 Materializer 事务 | 否 |
| CATALOG 与 ASSISTANT_GENERATED 的统一边界 | BlueprintSnapshot |


## ProjectObject payload 与默认动作补充决策

> 实施状态（2026-09-17）：本节决策的核心路径已落地。以下“修复前实现”保留用于说明问题来源；当前对象使用显式 `defaultActionKey` 列和 `initialPayloadJson`，不再写 `payload.defaultActionKey` 或整条 slotTemplate。

### 修复前实现

当前默认动作存在两条读取路径：

```text
Blueprint actionSpec
    ├─ AigcProjectMaterializer：取匹配 targetTemplateKey 的第一个动作
    │      ↓
    │  payload.defaultActionKey
    │      ↓
    │  AigcProjectObjectView.defaultActionKey
    │      ↓
    │  package fan-out
    │
    └─ BlueprintSnapshot.actions
           ↓
       readiness / 动作依赖 / confirmationPolicy / 语义目录
```

这意味着 `payload.defaultActionKey` 与 `BlueprintSnapshot.actions` 已形成两条可能漂移的读取路径。

此外，`AigcResolvedObjectSpec.schemaJson` 当前不是纯对象实例初始数据，而是整条 `slotTemplate` 原始 JSON。物化器将其整体解析为 `AigcProjectObject.payload`，因此以下模板规则被重复写入每个对象实例：

- `templateKey` 和 `stableKeyPattern`；
- `defaultCount`/`minCount`/`maxCount`；
- 按生产模式计算数量的规则；
- `activationCondition`；
- `parentTemplateKey`；
- `userAddable`/`userRemovable`；
- 其他只应属于 Blueprint/Snapshot 的规则。

这些内容不是对象实例 payload，应从物化结果中移除。

### 默认动作字段决策

**调整 `aigc_project_object.payload`，将默认动作提升为 `AigcProjectObject` 的显式字段。**

数据库字段定义为：

```text
default_action_key VARCHAR(100) NULL
```

字段必须命名为 `default_action_key`，不命名为含义过宽的 `action_key`，原因是一个对象可以适用多个动作。

内置博客 Blueprint 已对 `article` 模板同时声明：

- `article.draft`；
- `article.rewrite`；
- `article.seo_optimize`。

因此：

- `defaultActionKey` 只表示自动推进或批量 fan-out 时采用的默认动作；
- 全部适用动作仍由 BlueprintSnapshot `actions` 表达；
- `AigcExecutionRun.actionKey` 表示某次实际执行的动作，不能与对象默认动作混为同一事实。

### 动作真理源边界

| 事实 | 真理源 |
|---|---|
| Blueprint 中动作与模板的适用关系 | 类型化 Blueprint Definition `actions` |
| 每个模板的默认动作选择 | Blueprint Action 显式默认标记 |
| 项目对象当前默认动作 | `AigcProjectObject.defaultActionKey` |
| 项目可用动作、依赖、确认策略、输入预设 | 当前冻结 BlueprintSnapshot `actions` |
| 某次实际执行动作 | `AigcExecutionRun.actionKey` |
| 对象类型特有实例扩展数据 | `AigcProjectObject.payload` |
| 对象生成指令 | BlueprintSnapshot `objectSpecs.userInstructionJson` |

`AigcProjectObject.defaultActionKey` 是 BlueprintSnapshot 解析结果在当前 ProjectGraph 上的显式物化字段，与 `semanticRole`、`outputContract` 等字段性质一致。项目结构发生显式修订时，对象字段和新快照修订必须在同一事务中保持一致。

### Blueprint 动作定义调整

当前通过动作列表顺序和 `.findFirst()` 隐式选择默认动作，不具备稳定合同。类型化 Blueprint Action 定义应增加显式默认标记，例如：

```text
defaultForTarget: boolean
```

编译和发布时执行以下校验：

- 每个 `targetTemplateKey` 最多只能有一个默认动作；
- 需要自动推进或 package fan-out 的模板必须声明默认动作；
- 默认动作必须同时存在于该模板的适用动作集合；
- 每个动作必须存在已发布执行绑定；
- 不允许通过 JSON 数组顺序决定默认动作；
- 项目级动作和对象级动作必须明确区分，项目级动作不写入对象默认动作字段。

解析后，`AigcResolvedObjectSpec` 增加 `defaultActionKey`，由 Blueprint 编译器显式计算。Materializer 只负责把该值写入 `AigcProjectObject.defaultActionKey`。

### payload 目标边界

`AigcProjectObject.payload` 保留，但只用于承载无法由通用强类型字段表达的**对象类型特有实例数据**，例如某一对象类型独有的渲染参数或扩展状态。

以下内容不得再写入 payload：

- `defaultActionKey`；
- Blueprint 模板键和稳定键生成规则；
- 数量与激活规则；
- 父子模板关系；
- Action 依赖和确认策略；
- DeliverableSet 或 ProcessPolicy；
- `userInstructionJson`；
- 已有明确数据库列承载的对象通用属性。

Blueprint 槽位需要为对象提供初始扩展值时，应增加语义明确的 `initialPayload`，并与模板规则、校验 Schema 分离：

```text
slotTemplate
├─ 模板规则                  → Blueprint Definition / Snapshot
├─ payloadSchema             → Snapshot 中的校验合同
└─ initialPayload            → 物化时写入 ProjectObject.payload
```

不得再把整条 `slotTemplate` 序列化为 `schemaJson` 后复制到每个项目对象。

### 实施调整

按无兼容层原则直接替换旧路径：

- `aigc_project_object` 新增可空的 `default_action_key`；
- `AigcProjectObject`、对象 API VO 和前端类型增加 `defaultActionKey`；
- `AigcResolvedObjectSpec` 增加 `defaultActionKey`；
- Blueprint Action 定义增加显式默认标记和唯一性校验；
- Materializer 直接写对象字段，删除写入 `payload.defaultActionKey` 的逻辑；
- `AigcProjectObjectView` 和 package fan-out 直接读取对象字段；
- readiness 根据对象默认动作定位默认动作策略，同时从 Snapshot 计算全部允许动作；
- 模板追加对象时从当前快照解析并写入默认动作；
- 自定义对象如允许指定默认动作，必须通过显式命令字段提交并校验，禁止从任意 payload 透传；
- 将已有 `payload.defaultActionKey` 数据迁移到新列，随后删除该 JSON 键，不保留双写；
- 将 `schemaJson` 拆分为 `payloadSchema` 与 `initialPayload`，停止复制完整模板规则。

### 补充验收标准

- 对象 API 不再通过解析 payload 返回 `defaultActionKey`；
- package fan-out 只读取 `AigcProjectObject.defaultActionKey`；
- readiness、fan-out 和对象 API 对默认动作的判断一致；
- 同一模板存在多个适用动作时，默认动作由显式标记唯一确定；
- 非默认动作仍可按 Snapshot 动作策略被显式调用；
- 发布包含多个默认动作的 Blueprint 必须失败；
- `payload` 中不再出现 `defaultActionKey` 和模板数量、激活、父级规则；
- 迁移后历史项目默认动作和执行行为保持不变；
- 新建和追加的模板对象均正确写入 `default_action_key`；
- `AigcExecutionRun.actionKey` 继续精确记录用户或系统实际执行的动作。


## ProjectInitialization 字段映射与 Brief 真理源补充决策

### 当前模型问题

当前新建项目表单显示“项目简介”，但实际提交链路是：

```text
NewProjectLauncher.brief
    ↓ briefJson
AigcProjectMaterializeCommand.briefJson
    ↓
AigcProject.brief
```

`briefJson` 实际是普通字符串，并不是 JSON。与此同时，`AigcProject` 还存在 `description` 和 `prompt`，项目图中又会物化 `brief` 类型对象，后者可以通过 `AigcObjectVersion.documentVersionId` 关联完整 `DocumentVersion`。这会形成多个可能承载 Brief 的位置：

- `AigcProject.description`；
- `AigcProject.brief`；
- `AigcProject.prompt`；
- `brief` ProjectObject 的 ObjectVersion/DocumentVersion。

完整 Brief 需要候选、版本、采用和审计治理，不应继续使用项目聚合根上的可直接覆盖文本字段。

### ProjectInitialization 的定位

`projectInitialization` 是 Blueprint 为项目创建入口声明的**初始化输入 Schema**，不是动态扩展 `aigc_project` 数据库列的机制。

Blueprint 可以预设项目创建时需要填写的文本、文档、品牌和渠道字段，但字段值必须通过有限的强类型绑定进入现有真理源：

```text
Blueprint projectInitialization.fields
    ↓ 渲染创建表单并校验
用户初始化输入
    ↓ 类型化 MaterializeCommand
Project / ProjectSpecification / 各类 Ref
```

不允许使用任意字符串把 Blueprint 字段直接映射到任意实体属性，也不为每个 Blueprint 创建新的数据库列。

### 初始化字段类型与存储映射

| 初始化事实 | 存储位置 | 约束 |
|---|---|---|
| 项目名称 | `AigcProject.name` | 固定项目身份字段 |
| 项目简介/原始目标 | `AigcProject.description` | 用户输入，供后续 AI 生成 Brief |
| 项目类型、生产模式 | `AigcProject` + BlueprintSnapshot | 使用强类型字段并冻结版本 |
| 预算等稳定项目属性 | `AigcProject` 对应强类型字段 | 不写入动态 payload |
| 影响结构的文本、枚举、数量 | `BlueprintSnapshot.projectSpecification.initializationValues` | 在结构冻结前确认 |
| 外部参考文档 | `AigcProjectDocumentRef(EXTERNAL_REFERENCE)` | 精确冻结 `documentVersionId` |
| 项目自产文档 | `AigcProjectDocumentRef(PROJECT_DOCUMENT)` | 关联文档身份和项目对象 |
| 品牌档案 | `AigcProjectProfileRef` | 精确引用版本 |
| 渠道选择 | `AigcProjectChannelRef` | 使用已发布渠道规格版本 |
| 需要候选、版本和采用治理的长文本 | `ProjectObject → ObjectVersion → DocumentVersion` | 不作为初始化值或 Project 文本列 |

简单文本字段只有在它是“创建事实”且不需要独立内容生命周期时，才进入 `projectSpecification.initializationValues`。完整 Brief、创意方向、提纲、文案和脚本均属于项目内容对象。

### 类型化初始化字段示例

```yaml
projectInitialization:
  fields:
    - key: description
      label: 项目简介
      valueType: TEXT
      binding: PROJECT_DESCRIPTION
      required: true
      maxLength: 500

    - key: productFacts
      label: 产品信息
      valueType: TEXTAREA
      binding: PROJECT_SPECIFICATION
      required: false

    - key: productManual
      label: 产品说明
      valueType: DOCUMENT_REFERENCE
      binding: EXTERNAL_DOCUMENT_REFERENCE
      role: product_manual
      minCount: 0
      maxCount: 1

    - key: sourceMaterials
      label: 参考资料
      valueType: DOCUMENT_REFERENCE
      binding: EXTERNAL_DOCUMENT_REFERENCE
      role: source_material
      minCount: 0
      maxCount: 20

    - key: brandProfile
      label: 品牌档案
      valueType: BRAND_PROFILE_REFERENCE
      binding: PROJECT_PROFILE_REFERENCE
      required: false
```

`binding` 必须是服务端固定枚举。Blueprint 只能组合已支持的输入能力，不能定义新的持久化路径。

### 文档关联字段调整

当前创建命令只接收无语义的 `documentVersionIds` 列表，Materializer 将所有引用统一写成 `role=project`。该模型无法区分产品说明、品牌规范、事实依据和普通素材。

目标命令应改为结构化文档引用，例如：

```json
{
  "fieldKey": "productManual",
  "role": "product_manual",
  "documentVersionIds": [123]
}
```

Materializer 根据已冻结的 `projectInitialization` 字段定义执行：

- 校验 `fieldKey` 存在；
- 校验字段类型为 `DOCUMENT_REFERENCE`；
- 校验数量上下限；
- 校验文档版本可访问；
- 创建 `EXTERNAL_REFERENCE`；
- 将字段 `role` 写入 `AigcProjectDocumentRef.role`；
- 不接受客户端绕过 Blueprint 自定义任意 role 或 binding。

### 删除 briefJson 与项目级 Brief 文本

目标状态删除：

```text
AigcProjectMaterializeDTO.briefJson
AigcProjectMaterializeCommand.briefJson
AigcProject.brief
AigcProjectView/VO.brief
AigcProjectUpdateDTO.brief
```

新建项目表单的“项目简介”改为提交：

```text
AigcProject.description
```

其语义是用户最初输入的项目目标或简短说明，不是完整 Brief，也不自动成为最终创作规范。

`AigcProject.prompt` 不作为 Brief 的替代字段；若后续审计仍无独立业务语义，应在同一设计评审中删除。

### Brief 的唯一真理源

Blueprint 通过图定义声明 Brief 内容对象：

```text
templateKey = brief
objectType = brief
outputContract = DOCUMENT_VERSION
defaultActionKey = brief.refine
```

项目物化后先得到空 Brief 对象。事务提交后，Assistant 使用以下上下文生成 Brief：

- `AigcProject.description`；
- `projectSpecification.initializationValues`；
- 品牌档案版本；
- 带字段 role 的外部参考文档版本；
- Blueprint 项目级和对象级生成指令；
- 当前用户补充指令。

生成结果进入既有候选链：

```text
Assistant Task
    ↓ brief.refine
DocumentVersion
    ↓
AigcObjectVersion(candidate).documentVersionId
    ↓ 用户采用或显式授权决策
Brief ProjectObject.adoptedVersionId
```

完整 Brief 正文的读取路径固定为：

```text
Brief ProjectObject.adoptedVersionId
    → AigcObjectVersion.documentVersionId
    → DocumentVersion
```

不得再从 `AigcProject.brief`、`payload` 或 Snapshot 中读取另一份 Brief 正文。

### 项目自产文档关联

首次为 Brief 对象创建 Document 时，应同时建立对象级项目文档关联：

```text
AigcProjectDocumentRef
├─ referenceType = PROJECT_DOCUMENT
├─ projectId = 当前项目
├─ objectId = briefObjectId
├─ documentId = Brief Document ID
└─ role = brief
```

三类指针职责不同：

| 指针 | 职责 |
|---|---|
| `ProjectDocumentRef.documentId` | 项目与文档身份的关联及导航 |
| `ObjectVersion.documentVersionId` | 某个候选版本使用的精确文档版本证据 |
| `ProjectObject.adoptedVersionId` | 当前被项目采用的 Brief 版本 |

`ProjectDocumentRef` 不能替代 `adoptedVersionId`，文档当前版本也不能自动等同于项目采用版本。

### 调整后的创建与完善流程

```text
用户填写 name + description + Blueprint 初始化字段
    ↓
服务端校验 projectInitialization
    ↓
事务内创建 Project、Refs、Snapshot、空 ProjectGraph
    ↓ COMMIT
创建 canonical Assistant Task
    ↓
针对 brief ProjectObject 执行 brief.refine
    ↓
创建 DocumentVersion + ObjectVersion candidate + PROJECT_DOCUMENT ref
    ↓
用户采用
    ↓
Brief ProjectObject.adoptedVersionId 生效
```

如果用户不启用 AI，也应通过同一 Brief ProjectObject 手工登记文档 candidate，不重新启用项目级 Brief 文本字段。

### 现有读取点调整

删除项目级 Brief 后，以下读取点必须改造：

- 项目基础信息弹窗删除“创作简报”直接编辑框；
- ProjectContext 从 Brief 对象 adopted DocumentVersion 读取正文；
- 文档生成对话从 adopted Brief 读取，不再读取 `project.brief`；
- 封面默认 Prompt 优先使用 `description`，存在 adopted Brief 时可附加其摘要；
- 项目 API 的 `brief` 字段删除；
- Blueprint `briefFields` 收敛到 `projectInitialization.fields`；
- `documentVersionIds` 改为带 `fieldKey/role` 的结构化引用；
- Brief 生成成功后自动建立对象级 `PROJECT_DOCUMENT` 关联。

### 数据处理原则

按未发布前无兼容层原则直接替换旧模型，不保留 `briefJson`/`AigcProject.brief` 双读或双写。

如需保留现有开发数据，应执行一次性迁移：

- 当 `description` 为空时，可将旧 `brief` 迁移为 `description`；
- 需要保留为完整 Brief 的旧正文，迁移为 Brief 对象的 DocumentVersion 和 ObjectVersion；
- 根据迁移结果设置 `adoptedVersionId`；
- 建立对应的 `PROJECT_DOCUMENT` 引用；
- 完成后删除旧 `brief` 列和全部调用点。

### 补充验收标准

- 新建项目请求不再包含 `briefJson`；
- “项目简介”只写入 `AigcProject.description`；
- Blueprint 可以声明多个带 role 和数量约束的文档输入字段；
- 文档输入按字段创建 `EXTERNAL_REFERENCE`，不再统一使用 `role=project`；
- 完整 Brief 只存在于 Brief 对象关联的 DocumentVersion；
- AI 生成 Brief 时只创建 candidate，不直接覆盖项目字段或自动采用；
- Brief 采用状态只由 `BriefObject.adoptedVersionId` 决定；
- 项目文档列表能通过对象级 `PROJECT_DOCUMENT` 关联发现 Brief 文档；
- 修改文档当前版本不会隐式改变项目已采用 Brief；
- 手工编写和 AI 生成 Brief 共用同一 candidate/adopted 链；
- 项目上下文、文档生成和封面提示不再读取 `AigcProject.brief`；
- `projectInitialization` 字段只能使用服务端支持的固定 binding。


## 项目级上下文文档与 DocumentRef 最终决策

> 本节是后续讨论形成的最终修正，**覆盖本文前文“Brief 仍作为 ProjectObject”以及“Brief 通过对象级 PROJECT_DOCUMENT 关联”的结论**。Brief、项目规范、风格指南等初始化上下文文档最终移出 ProjectGraph；ProjectGraph 只保留需要对象依赖、候选治理、审核或交付的生产对象。

### 最终边界

按文档在项目中的业务职责分类：

| 文档性质 | 建模方式 |
|---|---|
| Brief、项目规范、风格指南、术语表等全项目上下文 | 项目级 `AigcProjectDocumentRef(PROJECT_DOCUMENT)` |
| 用户提供的产品说明、品牌规范、参考资料 | 项目级 `AigcProjectDocumentRef(EXTERNAL_REFERENCE)` |
| 文章、推广文案、脚本、提纲、交付报告等工作产物 | `ProjectObject → ObjectVersion → DocumentVersion` |
| 需要进入 DeliverableSet、审核或发布证据链的文档 | ProjectObject，不作为初始化上下文文档 |

判断标准是：

- 面向整个项目、主要供后续生成读取、不进入交付清单的文档属于项目级上下文；
- 有独立工作依赖、候选比较、对象级 readiness、审核或交付职责的文档属于 ProjectObject。

Brief 属于第一类，不再物化 `brief` ProjectObject。

### AigcProjectDocumentRef 的职责

`AigcProjectDocumentRef` 只表达：

> 当前项目的哪个初始化字段，以什么语义角色，引用或自产了哪个文档，并明确采用/冻结哪个文档版本。

它是**项目与文档的关联事实**，不是 Document 或 DocumentVersion 元数据的副本。

建议目标字段：

| 字段 | 是否保留/新增 | 含义 |
|---|---|---|
| `projectId` | 保留 | 所属项目 |
| `objectId` | 保留可空 | 仅生产对象需要额外关联文档时使用；项目上下文文档为空 |
| `fieldKey` | 新增 | 对应冻结的 `projectInitialization.documents[].key` |
| `instanceNo` | 新增 | 同一多值字段下的稳定实例序号 |
| `referenceType` | 保留 | `PROJECT_DOCUMENT` 或 `EXTERNAL_REFERENCE` |
| `documentId` | 保留并统一必填 | 文档稳定身份 |
| `selectedDocumentVersionId` | 由 `documentVersionId` 重命名/统一语义 | 当前项目明确采用或冻结的精确版本 |
| `role` | 保留 | 稳定英文机器码，如 `brief`、`product_manual`、`source_material` |
| `source` | 新增 | `USER`、`ASSISTANT`、`IMPORT` 等来源 |
| `contextNote` | 新增可空 | 当前项目为什么使用该文档、使用范围或注意事项 |
| `sortOrder` | 保留 | 同字段或同角色下的展示/组装顺序 |

其中：

```text
documentId
→ 文档身份，不随正文修订变化

selectedDocumentVersionId
→ 当前项目采用/冻结的正文版本
```

即使 Document 已产生更新版本，项目上下文仍读取 `selectedDocumentVersionId`，不得自动漂移到 `Document.currentVersionId`。

### 不在 DocumentRef 增加摘要、类型和主题字段

**不建议在 `AigcProjectDocumentRef` 增加以下字段：**

```text
documentSummary
documentType
topics
roleLabel
roleDescription
```

原因是这些字段分别属于不同真理源。

#### 文档类型

当前 `Document.docType` 已表达文档身份分类。项目引用不应复制一份 `documentType`，否则修改文档分类后会出现两份值。

读取方式：

```text
DocumentRef.documentId
    → Document.docType
```

#### 文档摘要

摘要描述的是某个具体正文版本的内容。相同 `documentId` 的不同 DocumentVersion 可能有不同摘要，因此摘要不能挂在项目关联上，也不应只挂在 Document 身份上。

若后续需要摘要，应以 `documentVersionId` 为粒度建立版本语义元数据，例如：

```text
DocumentVersionSemanticMetadata
├─ documentVersionId
├─ summary
├─ topics
├─ language
├─ metadataVersion
├─ generatedBy
└─ generatedTime
```

该元数据可以由 AI 异步生成或重建，不修改不可变 DocumentVersion 正文，也不复制到每一条 ProjectDocumentRef。

#### 主题

主题同样来源于具体正文版本，可能由 Front Matter、人工标签或语义分析得到。它应属于 Document/DocumentVersion 的内容元数据或版本语义投影，不属于“项目为什么引用这个文档”的关联事实。

#### role 的中文名称与说明

`role` 保持稳定英文机器码，供权限、上下文组装和 Action 依赖判断使用。中文名称、字段说明、使用提示由冻结的 Blueprint 初始化 Schema 定义：

```yaml
projectInitialization:
  documents:
    - key: brief
      role: brief
      label: 项目简报
      description: 说明项目目标、受众、核心信息、风格和交付约束

    - key: productManual
      role: product_manual
      label: 产品说明
      description: 产品功能、参数和使用限制的事实来源
```

项目 API 可以将 Snapshot 中的 `label/description` 与 Document/Version 元数据组装为只读投影，但数据库关联行不重复保存。

### contextNote 与文档摘要的区别

`contextNote` 可以加入 DocumentRef，但它不是文档摘要。

示例：

```text
文档摘要：
这是一份耳机产品说明书，介绍降噪、续航和连接参数。

项目 contextNote：
本项目只使用第三章的续航参数，不采用旧版价格信息。
```

前者属于文档版本，后者属于“该项目如何使用这份文档”，因此只有后者适合保存在关联表。

`contextNote` 属于用户或 Assistant 提供的项目上下文数据，不得提升为系统级指令，也不能绕过 Blueprint 的安全和输出合同。

### ProjectInitialization 文档定义

建议文档初始化字段统一由 `projectInitialization.documents` 声明：

```yaml
projectInitialization:
  documents:
    - key: brief
      role: brief
      label: 项目简报
      description: 后续全部内容生成共享的项目目标和约束
      documentType: PROJECT_DOCUMENT
      required: true
      minCount: 1
      maxCount: 1
      generationMode: AI_ASSISTED
      actionKey: brief.refine
      triggerPolicy: AFTER_MATERIALIZATION
      confirmationPolicy: REQUIRED

    - key: productManual
      role: product_manual
      label: 产品说明
      description: 产品参数与使用限制的事实来源
      documentType: EXTERNAL_REFERENCE
      required: false
      minCount: 0
      maxCount: 1

    - key: sourceMaterials
      role: source_material
      label: 参考资料
      description: 生成内容时可检索的背景材料
      documentType: EXTERNAL_REFERENCE
      required: false
      minCount: 0
      maxCount: 20
```

这里的 `documentType` 表示初始化字段的关联类型，不等于文档模块的 `Document.docType`。实现时建议使用更明确的名称，如：

```text
referenceType = PROJECT_DOCUMENT | EXTERNAL_REFERENCE
```

避免与 `Document.docType` 混淆。

### Brief 的项目级存储与生成

Brief 不再创建 ProjectObject，目标链路为：

```text
projectInitialization.documents[key=brief]
    ↓
AigcProjectDocumentRef(fieldKey=brief, referenceType=PROJECT_DOCUMENT)
    ├─ documentId
    └─ selectedDocumentVersionId
```

生成流程：

```text
用户填写 description、初始化字段并关联外部文档
    ↓
Materializer 创建 Project、Snapshot、外部 DocumentRef 和生产对象图
    ↓ COMMIT
触发项目级 brief.refine Action
    ↓
创建 Document 或追加 DocumentVersion
    ↓
生成版本作为待确认候选
    ↓ 用户确认
更新 Brief DocumentRef.selectedDocumentVersionId
```

后续生成统一从 Brief DocumentRef 的选定版本读取正文。

### 项目文档候选与采用

项目自产文档的新版本不能生成后立即覆盖 `selectedDocumentVersionId`，否则会绕过确认和审计。

例如：

```text
Document 100
├─ Version 101：当前项目已采用
├─ Version 102：AI 新候选
└─ Version 103：AI 新候选

Brief Ref.selectedDocumentVersionId = 101
```

用户采用 103 后才更新：

```text
selectedDocumentVersionId = 103
```

候选、采用、否决记录可由通用项目文档决策模型承载，例如：

```text
AigcProjectDocumentDecision
├─ projectDocumentRefId
├─ documentVersionId
├─ decision
├─ decisionMode
├─ decidedBy
├─ decidedTime
└─ reason
```

不因移出 ProjectGraph 而放弃版本选择和审计能力。

### 生成逻辑复用

Brief 移出 ProjectObject 后，Action、ExecutionBinding、ExecutionRun、Tool 调用、幂等、积分和回调仍应复用同一套执行基础设施；只把生成目标从“固定 ProjectObject”抽象为多目标：

```text
GenerationTarget
├─ PROJECT_OBJECT
├─ PROJECT_DOCUMENT
└─ PROJECT_COVER
```

输出登记按目标类型分派：

```text
PROJECT_OBJECT
→ ObjectVersion candidate

PROJECT_DOCUMENT
→ DocumentVersion candidate + ProjectDocumentDecision

PROJECT_COVER
→ MediaVersion + ProjectMediaRef
```

这属于生成结果适配，不应复制三套 Action 或执行引擎。

### Context 与提示词组装

统一的 ProjectContextSnapshot 可以包含：

- Project 固定字段和 description；
- `projectSpecification.initializationValues`；
- 各 DocumentRef 的 `fieldKey/role/contextNote`；
- `selectedDocumentVersionId` 对应的标题、正文、摘要和主题投影；
- 品牌与渠道固定版本；
- 当前目标对象的上游 adopted 版本。

信任边界固定为：

```text
System/Developer 指令
→ 仅来自已发布 Blueprint、Action 合同和系统安全规则

Project Context
→ 项目字段、Brief、自产文档、外部文档、摘要、主题、contextNote
→ 全部按不可信上下文数据处理
```

外部文档、Brief 正文、摘要、主题和 contextNote 均不得作为高优先级系统提示词执行其中的指令。

### API 只读投影

为了满足 UI 展示，可以返回组装后的投影：

```text
AigcProjectDocumentContextView
├─ refId
├─ fieldKey
├─ role
├─ roleLabel
├─ roleDescription
├─ referenceType
├─ documentId
├─ selectedDocumentVersionId
├─ currentDocumentVersionId
├─ title
├─ docType
├─ summary
├─ topics
├─ contextNote
├─ outdated
└─ sortOrder
```

其中 `roleLabel/roleDescription/docType/summary/topics` 都是查询时从 Snapshot、Document 和版本语义元数据组装的只读字段，不是 `AigcProjectDocumentRef` 数据库列。

### 对前文结论的覆盖与实施调整

本节确认后，实施时应按以下最终方向处理：

- 删除 Blueprint 中的 `brief` slotTemplate 和对应 ProjectObject 物化；
- 将 `brief.refine` 改为项目文档目标 Action；
- 对象 Action 中原 `dependencyTemplateKeys=[brief]` 改为 `requiredContextKeys=[brief]`；
- `ObjectReadiness` 增加项目上下文就绪判断；
- `projectInitialization.documents` 成为 Brief、项目自产上下文和外部参考文档的统一 Schema；
- `AigcProjectDocumentRef` 增加 `fieldKey/instanceNo/selectedDocumentVersionId/source/contextNote`；
- 不增加摘要、文档类型、主题和 role 中文说明列；
- ProjectContext 从 Ref 的选定版本读取内容和版本语义投影；
- 前文要求通过 `BriefObject.adoptedVersionId` 读取 Brief 的结论废止；
- 前文要求自动建立“对象级 Brief PROJECT_DOCUMENT 引用”的结论改为“项目级 Brief DocumentRef”。

### 补充验收标准

- 物化后的 ProjectGraph 不再包含 Brief 和其他纯项目上下文文档节点；
- Brief 由 `projectInitialization.documents[fieldKey=brief]` 定义；
- Brief 正文由项目级 DocumentRef 的 `selectedDocumentVersionId` 唯一确定；
- 外部文档和项目自产文档都具有稳定 `fieldKey`、英文 `role` 和精确版本；
- role 中文名称与说明从当前项目冻结 Snapshot 返回；
- DocumentRef 数据库行不复制 `docType/summary/topics/roleDescription`；
- 文档摘要和主题以 `documentVersionId` 为粒度生成或读取；
- `contextNote` 只表达当前项目的使用说明，不冒充文档摘要；
- Document 更新当前版本不会导致项目上下文版本自动漂移；
- 项目文档 AI 新版本在确认前不会更新 `selectedDocumentVersionId`；
- 对象 Action 可以通过 `requiredContextKeys` 依赖 Brief 等项目上下文；
- 项目文档与 ProjectObject 共用 Action、ExecutionRun、Tool、幂等、积分和回调基础设施；
- API 可以返回 role label、文档类型、摘要和主题的聚合只读视图；
- 项目上下文中的文档正文、摘要、主题和备注均按不可信数据处理。


## ProjectInitialization 通用字段与文档版本语义元数据

### 当前 DocumentVersion 能力

当前 `DocumentVersion` 只持久化不可变正文版本和来源审计信息：

```text
id
documentId
versionNo
parentVersionId
title
content
format
checksum
sourceType
sourceRef
changeSummary
createBy/createTime
```

当前不存在：

```text
summary
topics
keywords
language
```

`changeSummary` 表示“本次版本修改说明”，不是正文摘要。`Document.docType` 是文档身份级分类，`Document.frontMatter` 也不等于精确 DocumentVersion 的语义元数据。

### ProjectInitialization 字段定义结构

`projectInitialization.fields` 中每个字段建议使用统一定义：

```yaml
- key: audience
  label: 目标受众
  description: 项目希望影响或服务的人群
  valueType: TEXTAREA
  semanticRole: AUDIENCE
  binding: PROJECT_SPECIFICATION
  required: false
  defaultValue: null
  placeholder: 描述受众特征、场景和需求
  orderNo: 20
  validation:
    minLength: 0
    maxLength: 2000
  contextPolicy:
    enabled: true
    priority: 50
```

推荐属性：

| 属性 | 用途 |
|---|---|
| `key` | 当前 Blueprint 内稳定字段键 |
| `label` | UI 显示名称 |
| `description` | 字段用途说明 |
| `valueType` | `TEXT/TEXTAREA/NUMBER/BOOLEAN/DATE/DATE_RANGE/ENUM/MULTI_ENUM/URL` |
| `semanticRole` | 通用语义分类 |
| `binding` | 值进入哪个权威模型 |
| `required` | 是否必填 |
| `defaultValue` | 确定性默认值 |
| `placeholder` | UI 输入提示 |
| `orderNo` | 显示和上下文排序 |
| `validation` | 长度、数值范围、选项等校验 |
| `contextPolicy` | 是否进入 AI 上下文及优先级 |

`contextPolicy` 只控制上下文选择和排序，不改变消息信任等级。所有用户输入仍按不可信上下文数据处理。

### 推荐通用初始化字段

以下是通用字段目录，不要求每个 Blueprint 全部使用。具体行业字段通过自定义 `key/label` 映射到通用 `semanticRole`，不扩展系统枚举。

| 推荐 key | semanticRole | 说明 |
|---|---|---|
| `goal` | `GOAL` | 项目要解决的问题或实现的目标 |
| `background` | `BACKGROUND` | 项目背景和已有上下文 |
| `audience` | `AUDIENCE` | 目标用户、读者或受众 |
| `requirements` | `REQUIREMENT` | 必须满足的要求 |
| `constraints` | `CONSTRAINT` | 禁止项、边界和限制 |
| `keyFacts` | `FACT` | 必须准确使用的事实 |
| `keyMessages` | `MESSAGE` | 希望重点传达的信息 |
| `styleGuidance` | `STYLE` | 风格、语气和表达偏好 |
| `successCriteria` | `SUCCESS_CRITERIA` | 如何判断结果达到预期 |
| `locale` | `LOCALE` | 语言、地区和文化环境 |
| `schedule` | `SCHEDULE` | 时间范围、截止时间或活动周期 |
| `deliveryRequirements` | `DELIVERY_REQUIREMENT` | 格式、数量、尺寸等交付要求 |
| `additionalContext` | `OTHER` | 无法归类的补充说明 |

核心 `semanticRole` 控制为：

```text
GOAL
BACKGROUND
AUDIENCE
REQUIREMENT
CONSTRAINT
FACT
MESSAGE
STYLE
SUCCESS_CRITERIA
LOCALE
SCHEDULE
DELIVERY_REQUIREMENT
OTHER
```

例如行业字段“产品卖点”定义为：

```yaml
key: productSellingPoints
label: 产品卖点
semanticRole: MESSAGE
```

不新增 `PRODUCT_SELLING_POINT` 系统枚举。

### 推荐 binding

```text
PROJECT_NAME
PROJECT_DESCRIPTION
PROJECT_SPECIFICATION
PROJECT_BUDGET
PROJECT_SCHEDULE
```

使用原则：

- `goal` 可绑定 `PROJECT_DESCRIPTION`，成为项目创建时的简短目标；
- 其他 Blueprint 动态字段通常绑定 `PROJECT_SPECIFICATION`；
- 品牌、渠道和文档使用专门引用结构，不通过普通 field binding 表达；
- binding 必须是服务端固定枚举，Blueprint 不得定义任意实体属性路径。

### ProjectInitialization 文档定义结构

`projectInitialization.documents` 中每个文档字段建议使用统一定义：

```yaml
- key: productManual
  role: reference
  label: 产品说明
  description: 产品功能、参数和限制的事实来源
  referenceType: EXTERNAL_REFERENCE
  required: false
  minCount: 0
  maxCount: 1
  acceptedDocTypes:
    - reference
    - specification
  generationMode: NONE
  actionKey: null
  triggerPolicy: MANUAL
  confirmationPolicy: NONE
  orderNo: 20
  contextPolicy:
    mode: AUTO
    priority: 80
    maxTokens: 8000
```

推荐属性：

| 属性 | 用途 |
|---|---|
| `key` | 初始化文档字段稳定键 |
| `role` | 通用英文语义分类 |
| `label` | UI 显示名称 |
| `description` | 文档用途说明 |
| `referenceType` | `PROJECT_DOCUMENT/EXTERNAL_REFERENCE` |
| `required` | 是否必需 |
| `minCount/maxCount` | 数量约束 |
| `acceptedDocTypes` | 允许的 `Document.docType` |
| `generationMode` | `NONE/USER_PROVIDED/AI_ASSISTED` |
| `actionKey` | AI 生成项目文档使用的动作 |
| `triggerPolicy` | `MANUAL/AFTER_MATERIALIZATION` |
| `confirmationPolicy` | 新版本是否需要确认采用 |
| `orderNo` | 显示和上下文排序 |
| `contextPolicy` | `FULL_TEXT/SUMMARY/RAG/AUTO/NONE` 等组装策略 |

### 推荐通用文档角色

`fieldKey` 表达 Blueprint 中的具体文档身份，`role` 只表达跨 Blueprint 的通用语义类别。

| role | 通用含义 | 示例 fieldKey |
|---|---|---|
| `brief` | 全项目简报 | `brief` |
| `specification` | 项目或内容规格 | `projectSpecification` |
| `requirement` | 需求来源 | `customerRequirements` |
| `reference` | 一般参考资料 | `productManual` |
| `source` | 内容创作源材料 | `interviewTranscript` |
| `evidence` | 事实和证据来源 | `marketEvidence` |
| `guideline` | 风格、品牌或操作指南 | `brandGuide` |
| `policy` | 政策、制度和强制规则 | `publicationPolicy` |
| `compliance` | 合规、安全和法律要求 | `complianceRules` |
| `research` | 调研、分析和研究资料 | `marketResearch` |
| `template` | 输出结构或格式模板 | `articleTemplate` |
| `example` | 范例、历史优秀内容 | `referenceExamples` |
| `terminology` | 术语、命名和词汇表 | `terminology` |
| `data` | 结构化或半结构化数据来源 | `productData` |
| `other` | 无法归类的文档 | `attachments` |

避免把 `product_manual`、`brand_guideline`、`competitor_report` 等行业名称定义为系统级 role。应写成：

```yaml
key: productManual
role: reference
label: 产品说明
```

```yaml
key: brandGuide
role: guideline
label: 品牌规范
```

Action 依赖具体文档时使用 `fieldKey`：

```yaml
requiredContextKeys:
  - productManual
```

不使用宽泛 role 作为唯一依赖键，否则无法区分同一角色下的多份文档。

### DocumentVersionSemanticMetadata

摘要和主题应按 `documentVersionId` 建立独立的版本语义元数据，不写入 `AigcProjectDocumentRef`，也不修改不可变 DocumentVersion 正文。

目标模型：

```text
DocumentVersionSemanticMetadata
├─ documentVersionId
├─ summary
├─ topics
├─ keywords
├─ language
├─ generationStatus
├─ sourceType
├─ modelRef
├─ manualOverride
├─ generatedTime
└─ version/audit fields
```

推荐字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `documentVersionId` | `BIGINT UNIQUE` | 对应不可变正文版本 |
| `summary` | `TEXT` | 当前版本摘要 |
| `topics` | `JSONB` | 主题列表 |
| `keywords` | `JSONB` | 关键词列表 |
| `language` | `VARCHAR` | 检测或指定的语言 |
| `generationStatus` | `VARCHAR` | `PENDING/READY/FAILED` |
| `sourceType` | `VARCHAR` | `AI/MANUAL/HYBRID` |
| `modelRef` | `VARCHAR` | 使用的模型或算法版本 |
| `manualOverride` | `BOOLEAN` | 是否存在人工覆盖 |
| `generatedTime` | `TIMESTAMP` | 最近生成完成时间 |
| `version` | `INTEGER` | 乐观锁和并发修改控制 |

主题记录建议包含标准化名称和来源：

```json
[
  {
    "name": "降噪耳机",
    "normalizedName": "noise-cancelling-headphones",
    "confidence": 0.96,
    "source": "AI"
  },
  {
    "name": "通勤场景",
    "normalizedName": "commuting",
    "source": "MANUAL"
  }
]
```

### 自动生成生命周期

每个新 DocumentVersion 创建后异步建立语义元数据：

```text
DocumentVersion 创建成功
    ↓ AFTER_COMMIT
创建 SemanticMetadata(status=PENDING)
    ↓
提交摘要/主题提取任务
    ↓
AI 生成 summary/topics/keywords/language
    ↓
SemanticMetadata(status=READY)
```

适用于：

- AI 生成的项目文档；
- 用户上传并转换的外部文档；
- 用户手工创建的文档；
- 文档追加的新版本。

不得在 DocumentVersion 创建事务中同步等待模型响应。每个 DocumentVersion 独立拥有元数据，生成新正文版本时不覆盖旧版本元数据。

如果文档生成模型已经返回摘要和主题，可以在 DocumentVersion 落库后通过同一个语义元数据接口写入，不建立项目专用旁路。

### 人工修改和重新生成

语义元数据支持人工修改，但不修改 DocumentVersion：

```text
PATCH SemanticMetadata
├─ expectedMetadataVersion
├─ summary
├─ topics
└─ keywords
```

人工修改后：

```text
sourceType = MANUAL 或 HYBRID
manualOverride = true
```

后续自动任务遵守：

- 默认不得覆盖人工修改内容；
- 可以补齐仍为空的字段；
- 只有用户明确执行“重新生成摘要/主题”时才允许覆盖；
- 修改和重新生成必须保留操作者、时间和模型审计。

若需要完整历史，可增加 `DocumentVersionSemanticMetadataRevision`；首期至少使用乐观锁和操作审计避免静默覆盖。

### ProjectContext 使用策略

根据 `projectInitialization.documents[].contextPolicy.mode` 选择组装方式：

| mode | 行为 |
|---|---|
| `FULL_TEXT` | 使用选定 DocumentVersion 完整正文 |
| `SUMMARY` | 使用摘要、主题和关键词 |
| `RAG` | 只注入与当前任务相关的检索片段 |
| `AUTO` | 根据正文长度、任务和预算自动选择 |
| `NONE` | 不进入 AI 上下文，只保留项目记录 |

读取路径：

```text
ProjectDocumentRef.selectedDocumentVersionId
    ↓
DocumentVersion
    ├─ title/content/checksum
    ├─ Document.docType
    └─ DocumentVersionSemanticMetadata
       ├─ summary
       ├─ topics
       └─ keywords
```

元数据缺失或生成失败时：

- 不允许回退读取其他 DocumentVersion 的摘要或主题；
- `FULL_TEXT/RAG` 可继续基于当前选定正文工作；
- `SUMMARY` 是否等待或受控降级由 Blueprint contextPolicy 决定；
- 语义元数据失败不得改变项目选定的文档版本。

### 首期建议范围

首期字段目录建议至少提供：

```text
goal
background
audience
requirements
constraints
keyFacts
styleGuidance
successCriteria
additionalContext
```

首期文档角色建议至少提供：

```text
brief
specification
requirement
reference
source
evidence
guideline
policy
template
example
research
data
other
```

首期版本语义元数据建议至少提供：

```text
documentVersionId
summary
topics
language
generationStatus
sourceType
manualOverride
generatedTime
```

`keywords/modelRef` 可在检索和生成审计实际接入时同步启用，但目标模型应预留其语义，不通过 DocumentRef 临时承载。

### 补充验收标准

- Blueprint 可以用统一 Schema 声明通用文本字段和文档字段；
- 行业字段通过自定义 `key/label` 映射通用 semanticRole/role；
- binding、semanticRole 和 role 均由服务端目录校验；
- Action 使用 `fieldKey` 声明精确上下文依赖；
- 当前 DocumentVersion API/实体不再被误认为已有 summary/topics；
- 每个新 DocumentVersion 提交后异步创建版本语义元数据；
- 摘要和主题始终与精确 documentVersionId 对齐；
- 人工修改语义元数据后自动任务不得静默覆盖；
- ProjectContext 按 FULL_TEXT/SUMMARY/RAG/AUTO/NONE 策略组装；
- 元数据失败不改变文档正文、项目选定版本或 ProjectGraph；
- DocumentRef 不复制 summary/topics/docType/roleDescription；
- 所有文档正文和派生语义元数据继续按不可信项目上下文处理。


## 统一 AI 生成内循环与 Prompt 持久化决策

### 完整流程结论

物化前初始化字段生成、物化后项目文档生成和 ProjectObject 内容生成共享同一套 AI 协作内循环：

```text
生成计划和目标指令
    ↓
确认执行范围
    ↓
执行并产生 candidate，随后调整或采用
```

但整个项目生命周期不是只有三步，而是：

```text
Blueprint 初始化 Draft
    ↓
物化前 AI 辅助生成并确认初始化字段
    ↓
确定性物化 Project/Snapshot/空 ProjectGraph
    ↓ COMMIT
物化后 AI 辅助生成项目文档和 ProjectObject 内容
```

物化阶段与内容生成阶段必须分开。`AigcProjectMaterializer` 继续保持无 AI、单事务和确定性；项目文档与对象内容在事务提交后通过 canonical Assistant Task/TaskPlan 异步生成。

### 三步内循环的准确含义

#### 生成计划和目标指令

系统生成的是：

- 本轮生成目标集合；
- 每个目标的 `actionKey`；
- 目标间依赖；
- 必需和可选上下文键；
- 每个目标专属的 `generationInstruction`；
- 预计成本；
- 执行和采用确认策略。

系统不在此阶段为每个目标复制一份完整最终 Prompt。

#### 确认执行范围

用户确认的是：

- 生成哪些目标；
- 使用哪些精确版本资料；
- 每个目标的专属生成要求；
- 预计成本和模型选择；
- 结果是只生成 candidate 还是允许按策略自动采用。

用户不需要审批内部完整 System Prompt。

#### 执行、调整和采用

确认后即时编译完整 Prompt，创建 canonical TaskPlan 并执行。每个目标分别产生 candidate：

| targetType | candidate 落点 | 采用结果 |
|---|---|---|
| `INITIALIZATION_FIELD` | Initialization Draft field candidate | 写入确认后的 ProjectSpecification |
| `PROJECT_DOCUMENT` | DocumentVersion candidate | 更新 `selectedDocumentVersionId` |
| `PROJECT_OBJECT` | ObjectVersion candidate | 更新 `adoptedVersionId` |
| `PROJECT_COVER` | MediaVersion candidate | 更新封面采用指针 |

生成后可以编辑、重新生成、否决或采用；不得原地覆盖历史 candidate。

### 通用 Prompt 与目标指令分离

每个目标只持有自己的增量指令：

```text
audience：
根据项目目标和产品资料归纳主要受众及典型使用场景。

brief：
形成覆盖目标、受众、核心信息、风格和交付约束的项目 Brief。

copy.01：
为小红书生成偏生活化的推广文案，突出通勤降噪。
```

执行时由 PromptCompiler 组装：

```text
可信系统规则和 Blueprint 指令
+ Action 输入输出合同
+ 当前 ProjectContextSnapshot
+ 当前目标 generationInstruction
+ 用户本轮反馈
+ 模型/Provider 适配层
= 本次物理调用最终 Prompt/Messages
```

项目通用规则、Brief、品牌、渠道和外部资料不复制到每个计划项的 `generationInstruction`。

### 单项目标指令调整

用户可以在执行前调整单个字段、项目文档或对象的 `generationInstruction`，不影响其他计划项。

任何指令修改都必须生成新的：

```text
itemVersion
itemHash
proposalVersion
proposalHash
```

处理规则：

- 尚未确认：直接形成新提案版本；
- 已确认但尚未执行：修改项重新确认，旧确认失效；
- 已执行：创建基于反馈的新执行和新 candidate；
- 不允许修改已冻结指令后继续复用旧确认或旧 requestDigest。

### 手动与自动模式

手动模式默认：

```text
批量确认执行
→ 生成 candidate
→ 字段可批量采用
→ Brief 等重要项目文档逐项采用
```

自动模式默认：

```text
在已授权范围内自动生成 candidate
→ 用户后续查看、调整和采用
```

自动模式不等于自动采用。只有存在独立、明确的采用授权时，低风险字段才可自动采用。自动模式不得扩大目标范围、修改结构合同、更换引用版本或覆盖人工修改。

### 表单与对话共用同一合同

表单按钮和对话确认只是不同交互入口：

```text
表单确认
┐
├→ 同一 Proposal → 同一 Approval → 同一 TaskPlan → 同一 Candidate/Adoption
┘
对话确认
```

对话中的“确认全部”“只生成 Brief”等自然语言必须解析为结构化命令：

```text
proposalId
proposalVersion
proposalHash
approvedTargetKeys
itemInstructionOverrides
```

不得仅凭一段“确认”文本直接触发副作用。按钮和自然语言确认最终调用同一审批命令。

### 生成提案持久化

为了支持表单和对话跨会话确认、局部修改和过期校验，生成提案及其修订需要持久化，但不建立第二套执行调度状态机。

建议模型：

```text
ProjectGenerationProposal
├─ proposalId
├─ projectId 或 draftId
├─ proposalVersion
├─ proposalHash
├─ contextHash
├─ status
├─ estimatedCredits
├─ confirmationPolicy
├─ approvedBy
├─ approvedTime
├─ linkedTaskId
├─ expiresAt
└─ items

GenerationProposalItem
├─ targetType
├─ targetKey/fieldKey
├─ actionKey
├─ generationInstruction
├─ dependencyKeys
├─ requiredContextKeys
├─ modelPolicy
├─ estimatedCredits
├─ itemVersion
└─ itemHash
```

Proposal 只负责“提议和确认什么”，确认后执行状态由 canonical Task/TaskPlan 唯一管理。

### 目标指令必须长期持久化

用户确认的 `generationInstruction` 是业务审计事实，必须版本化长期保存：

```text
GenerationProposalItem.generationInstruction
    ↓ 用户确认
TaskPlan.TaskNode.generationInstruction
    ↓ 执行物化
AigcExecutionRun.promptText 或 effectiveInput.targetInstruction
```

`TaskPlan.TaskNode` 当前只有 `description`，目标状态应新增显式：

```text
generationInstruction
proposalItemRef
instructionHash
```

不能复用 `description` 承载执行指令，因为 description 是人类可读任务说明，generationInstruction 是经确认的模型目标输入。

`AigcExecutionRun.promptText` 的语义固定为：

- Assistant/文本动作：本项目标指令或用户补充指令快照；
- 图像、视频等专业 Provider：最终发送给 Provider 的专业媒体 Prompt。

不得同时把完整 Assistant System Prompt 塞入 `promptText`。

### 当前已有 Prompt 持久化事实

当前代码已经存在以下能力：

| 位置 | 当前保存内容 |
|---|---|
| `AigcExecutionRun.promptText` | 动作提交 Prompt |
| `AigcExecutionRun.contextSnapshot` | 运行上下文快照 JSON |
| `AigcExecutionRun.effectiveInput` | actionKey、项目/图修订、Snapshot hash、输入版本、Prompt、参数等 |
| `AigcExecutionSubmission.requestPayload` | 原始规范化提交请求 |
| `AigcTask.prompt/systemPrompt` | 专业任务 Prompt 和技能系统提示词 |
| `CompiledSystemPrompt` | 内存中的 compilerVersion、完整 layers、content、sha256 |
| `AiLlmCallLog.systemPrompt/messages/response` | 每次 LLM 调用的完整请求和响应内容 |
| `ToolInvocationReceiptEntity` | requestDigest、身份、actionKey、状态和结果，不含 Prompt |

当前问题不是完全没有保存，而是目标指令、最终 Prompt、上下文引用和日志内容分散，尚未形成统一语义和敏感数据保留策略。

### 有效输入与上下文引用

每次执行永久保存可重放和审计所需的精确引用，不在多个业务表复制正文：

```text
AigcExecutionRun.effectiveInput/contextSnapshot
├─ proposalId/proposalVersion/proposalHash
├─ proposalItemKey/itemVersion/itemHash
├─ generationInstructionHash
├─ actionKey
├─ projectVersion
├─ configurationSnapshotId/snapshotHash
├─ graphRevision
├─ selectedDocumentVersionIds
├─ inputObjectVersionIds
├─ brand/channel version refs
├─ attachment/media version refs
├─ requestedModel/modelPolicy
├─ actionArguments
├─ idempotencyKey
└─ confirmation receipt ref
```

这些引用和哈希长期保存。文档正文、Brief 正文和上游对象正文继续由各自版本实体持有，不复制进 `effectiveInput`。

### 最终 PromptManifest 永久保存

每次物理模型调用必须保存 PromptManifest，即使完整 Prompt 内容因保留策略被删除，仍可证明调用使用了哪些来源和编译结果。

建议结构：

```text
PromptManifest
├─ compilerVersion
├─ executionId/executionRunId/llmCallLogId
├─ actionKey
├─ proposalItemRef
├─ systemPromptHash
├─ messagesHash
├─ finalRequestHash
├─ layerRefs
│  ├─ layerKind
│  ├─ sourceKind
│  ├─ sourceKey
│  ├─ sourceVersion
│  └─ sourceSha256
├─ contextRefs
│  ├─ resourceType
│  ├─ resourceId
│  ├─ versionId
│  └─ contentHash
├─ model/provider/modelParameters
├─ compressionPolicy/version
├─ redactionPolicy/version
└─ createdTime
```

PromptManifest 是长期审计事实，禁止原地修改。

### 最终完整 Prompt 的保存策略

完整最终 Prompt/Messages 可能包含：

- 用户输入；
- Brief 和项目自产文档；
- 外部参考文档片段；
- 品牌资料；
- 个人信息和商业敏感内容；
- 内部系统规则。

因此完整内容不作为项目业务真理源，也不默认永久复制到多个表。

持久化策略：

```text
始终长期保存：
PromptManifest + hashes + version refs + generationInstruction

按审计级别保存：
加密的完整 System Prompt/Messages/Response

到期删除后仍保留：
PromptManifest 和 hashes
```

建议保留级别：

| 级别 | 完整 Prompt 内容 | 适用场景 |
|---|---|---|
| `HASH_ONLY` | 不长期保存，仅保存 Manifest/hash | 普通生产调用默认 |
| `ENCRYPTED_SHORT_TERM` | 加密限期保存 | 调试、质量分析、争议排查 |
| `ENCRYPTED_AUDIT` | 加密并按审计策略保存 | 高风险、合规要求场景 |
| `LEGAL_HOLD` | 受控延长保存 | 明确法律保全流程 |

具体时长由敏感数据和审计策略配置，不写死在 Blueprint。

### AiLlmCallLog 的目标职责

现有 `AiLlmCallLog` 已保存完整 `systemPrompt/messages/response`，可以承载每次物理 LLM 调用快照，但需要补充：

```text
compilerVersion
systemPromptHash
messagesHash
finalRequestHash
promptManifest
retentionClass
redactionStatus
encryptedPayloadRef 或加密列
expiresAt
```

并明确：

- 它是受控的调用审计/观测记录，不是项目内容真理源；
- 完整内容需要加密、权限隔离、访问审计和到期清理；
- 业务 API 默认不返回完整 System Prompt 和消息；
- 同一完整 Prompt 不再复制到 ProjectObject payload、DocumentRef 或 Snapshot。

专业媒体 Provider 的最终 Prompt 继续由 `AigcExecutionRun.promptText` 保存，并应用同样的敏感数据和保留分级；其 `effectiveInput` 保存生成来源引用和 Prompt hash。

### Tool InvocationReceipt 的边界

`ToolInvocationReceipt` 继续负责副作用幂等和完成证据：

```text
requestDigest
任务/执行/fence 身份
toolId/actionKey
status/result
```

它只需引用 `PromptManifest` 或 execution identity，不复制完整 Prompt。Prompt 快照属于模型调用记录，Tool Receipt 属于副作用治理，两者不能混为同一实体。

### 精确重放与可解释性

需要重放或解释某次调用时：

```text
PromptManifest.compilerVersion
+ layer source versions
+ BlueprintSnapshot hash
+ selected DocumentVersion IDs
+ input ObjectVersion IDs
+ generationInstruction
+ context/compression policy
+ model parameters
```

重新编译后校验：

```text
recompiled system/messages hash
== stored system/messages hash
```

如果动态压缩、检索片段或工具结果无法只靠来源版本确定，必须在 contextSnapshot 中保存选中片段引用、顺序和内容 hash；必要时按审计级别保存加密片段快照。

### Prompt 安全边界

- 只有已发布 Blueprint、Action 合同和系统策略可以贡献 System/Developer 层；
- 项目字段、Brief、自产文档、外部文档、摘要、主题和 contextNote 只能进入不可信上下文层；
- Prompt 日志输出前执行敏感字段识别和脱敏；
- 完整 Prompt 查看权限与普通项目查看权限分离；
- 所有完整 Prompt 访问写审计日志；
- 删除项目时按数据保留策略处理 Prompt 内容，不能只依赖软删除隐藏。

### 实施调整

- 增加版本化 `ProjectGenerationProposal/GenerationProposalItem`；
- `TaskPlan.TaskNode` 新增 `generationInstruction/proposalItemRef/instructionHash`；
- 表单和对话确认共用 Proposal Approval 命令；
- `AigcExecutionRun.promptText` 收敛为目标指令或专业 Provider Prompt；
- 扩展 `effectiveInput/contextSnapshot` 保存 Proposal、版本引用和 hash；
- 每次物理模型调用生成不可变 PromptManifest；
- 扩展 `AiLlmCallLog` 的 hash、manifest、加密、脱敏和保留字段；
- ToolInvocationReceipt 只引用执行/Manifest，不保存完整 Prompt；
- 建立完整 Prompt 到期清理和访问审计；
- 不在 Project、ProjectObject、DocumentRef 或 BlueprintSnapshot 重复保存最终完整 Prompt。

### 补充验收标准

- 物化前和物化后生成共享 Proposal→Approval→TaskPlan→Candidate 内循环；
- 物化事务中不存在模型调用；
- 每个目标具有可独立编辑和版本化的 generationInstruction；
- 修改目标指令后旧确认和旧 hash 自动失效；
- 自动模式只在授权范围内跳过执行确认，默认仍生成 candidate；
- 表单和对话使用同一结构化确认命令；
- 已确认 generationInstruction 可从 Proposal、TaskPlan 和 Execution 追溯；
- 最终 Prompt 不复制到 ProjectObject payload、DocumentRef 或 Snapshot；
- 每次物理模型调用都有不可变 PromptManifest 和内容 hash；
- 完整 Prompt 删除后仍可通过 Manifest 证明来源、版本和编译结果；
- `AiLlmCallLog` 的完整内容受加密、权限、脱敏和保留策略治理；
- ToolInvocationReceipt 不承担 Prompt 内容存储；
- 项目文档和外部文档正文只通过精确版本引用进入上下文；
- 对动态检索/压缩结果保存片段引用、顺序和 hash；
- 目标指令长期保留，完整 Prompt 按敏感数据策略限期保留。


## 无目录 Blueprint 的 Assistant 生成与统一物化决策

### 核心定义

“无蓝图创建”只表示没有目录中的 `AigcProjectBlueprint`，不表示项目可以没有 BlueprintSnapshot。

每个项目在 ProjectGraph 物化前都必须拥有一份经过系统校验、确认并冻结的项目级 BlueprintSnapshot：

```text
CATALOG
AigcProjectBlueprint
    ↓ 系统解析
BlueprintSnapshot 提案
    ┐
    ├→ 系统校验 → 确认/授权 → 冻结 Snapshot → 确定性物化
    ┘
ASSISTANT_GENERATED
Assistant 根据当前项目需求生成
BlueprintSnapshot 提案
```

两种来源只在 Snapshot 如何产生上不同，从 Snapshot 校验开始共用全部下游合同。

### AI 与系统职责边界

Assistant 不能直接创建项目实体、对象节点或关系行。准确职责为：

```text
Assistant
→ 生成结构化 BlueprintSnapshot 提案

系统
→ 校验、规范化、解析执行绑定、计算 hash

用户或既有授权
→ 确认结构

Materializer
→ 确定性物化已确认 Snapshot
```

禁止 AI：

- 直接调用 Repository 创建 ProjectObject/Relation；
- 绕过系统 Validator；
- 提交未确认且未授权的结构；
- 生成或指定数据库 ID；
- 自行选择任意 executionBindingVersionId；
- 在 LLM 调用事务内物化 ProjectGraph；
- 将项目级 Snapshot 自动发布为目录 Blueprint。

Assistant 只能调用受控 Tool，请求系统处理已持久化的提案。

### 对话式无目录创建流程

```text
用户在对话中表达项目目标
    ↓
Assistant 收集缺失信息并形成 ProjectSpecification candidate
    ↓
用户确认或补充 ProjectSpecification
    ↓
Assistant 生成 BlueprintSnapshot 提案
    ↓
系统规范化并校验
    ↓
对话展示结构、成本和确认卡
    ↓
用户确认或系统核对既有结构自主授权
    ↓
冻结 ASSISTANT_GENERATED Snapshot
    ↓
共用 AigcProjectMaterializer
    ↓ COMMIT
共用项目文档和 ProjectObject 内容生成流程
```

表单和对话仍然共用相同 Proposal、Approval、Validator 和 Materializer，不能建立 Chat 专用物化路径。

### ProjectSpecification 收敛

Assistant 在生成结构前先形成项目规格，至少包括：

- 项目名称和简介；
- 项目类型；
- 目标和受众；
- 需求、事实和约束；
- 渠道；
- 交付要求；
- 品牌版本；
- 外部文档精确版本；
- 成功标准；
- 用户补充要求。

会影响对象数量、类型、关系、outputContract、Action、DeliverableSet 或流程策略的字段，必须在 Snapshot 生成前确认，或存在明确且仍有效的结构自主授权。

### Assistant 允许输出的 Snapshot 内容

Assistant 只输出逻辑键和受控类型，不输出数据库身份：

```text
sourceType = ASSISTANT_GENERATED
projectSpecification
projectInitialization
objectSpecs
relationSpecs
actions
deliverableSets
processPolicy
snapshotVersion
```

Assistant 不提供或其输入不被信任的内容包括：

- tenant/user/org/workspace ID；
- 数据库主键；
- executionBindingVersionId；
- canonical relation layer；
- 关系方向和是否影响 readiness 的最终语义；
- 权限判定结果；
- 预算结算结果；
- Snapshot hash；
- 确认和授权回执。

这些值由服务端查询、推导或计算。

### 结构化生成方式

Assistant 必须通过类型化 Tool/Function Calling 生成提案，不允许依赖自由文本 JSON 的宽松解析。

建议 Tool：

```text
propose_project_blueprint
├─ projectSpecificationDraft
├─ projectInitializationDraft
├─ objectSpecs
├─ relationSpecs
├─ actions
├─ deliverableSets
└─ processPolicy
```

Tool 只创建 Proposal，不触发物化副作用。

### 系统校验

#### 项目规格校验

- projectType 已发布；
- 渠道版本有效；
- 品牌和外部文档版本可访问；
- 初始化字段符合 `projectInitialization` Schema；
- 文档字段的 role、referenceType 和数量合法；
- 用户权限、工作空间和预算范围有效。

#### 对象图校验

- localKey/stableKey 非空且唯一；
- objectType 在当前 ProjectSemanticCatalog 中存在；
- semanticRole/outputContract 合法；
- parentKey 引用存在；
- 父子层级单父且无环；
- 对象数、层级深度和关系数不超过系统上限；
- 必需 DeliverableSet 存在；
- 项目上下文文档未被错误建成纯上下文 ProjectObject。

#### 关系校验

- source/target 存在；
- 不允许自环和重复边；
- relationType 已注册且允许用于配置；
- 端点 semanticRole 符合关系语义；
- 需要无环的关系通过拓扑校验；
- canonical layer、方向、acyclic 和 affectsReadiness 由服务端语义目录计算，不采用 AI 自报值。

#### Action 校验

- actionKey 已发布；
- targetKey/fieldKey 存在；
- 每个对象目标最多一个显式默认动作；
- dependencyTemplateKeys 指向有效生产对象；
- requiredContextKeys 指向有效初始化字段或项目文档；
- 每个 Action 存在已发布执行绑定；
- confirmationPolicy 合法；
- Action 不得扩大用户授权和预算范围。

#### 项目文档校验

- fieldKey 存在于 `projectInitialization.documents`；
- referenceType、role、minCount/maxCount 合法；
- EXTERNAL_REFERENCE 使用精确 DocumentVersion；
- AI_ASSISTED PROJECT_DOCUMENT 具有合法 actionKey；
- acceptedDocTypes 与实际 `Document.docType` 兼容；
- 必需项目上下文的生成和采用门明确。

### 服务端规范化

系统校验后执行规范化：

- 统一 stable/local key 格式；
- 补全 canonical relation semantics；
- 解析并冻结 executionBindingVersionIds；
- 解析品牌、渠道和外部文档版本；
- 补全默认策略；
- 排序对象、关系和文档字段；
- 生成 canonical JSON；
- 计算 proposalHash 和 snapshotHash；
- 记录 validatorVersion、semanticCatalogVersion 和 compilerVersion。

Assistant 原始输出只用于审计，规范化 Snapshot 才进入确认和物化。

### 结构确认

无目录 Blueprint 默认需要确认，因为它定义新的对象范围、依赖和交付合同。

确认界面至少展示：

- ProjectSpecification 摘要；
- 项目上下文文档；
- 对象树和非层级关系；
- 每个对象的 outputContract 和默认 Action；
- DeliverableSet；
- 必需确认门；
- 预计生成目标和成本；
- 校验警告。

用户可以：

- 确认全部；
- 删除或新增允许范围内的对象；
- 修改数量；
- 调整关系；
- 修改项目文档；
- 修改单项目标指令；
- 取消创建。

任何结构调整产生新 proposalVersion/proposalHash，旧确认失效。

### 自动结构授权

只有同时满足以下条件才允许跳过即时人工确认：

- 项目处于 auto 模式；
- 用户已明确授权 Assistant 生成项目结构；
- 对象类型、数量、交付范围在授权边界内；
- 没有高风险 Action；
- 不增加已确认预算；
- 不引入未授权发布或外部副作用；
- 不绕过系统强制确认门；
- Proposal 和授权均未过期。

自动结构仍必须持久化 Proposal、ValidationResult、authorizationReceipt 和 hash，不能因为无人即时点击而跳过审计。

### Proposal 持久化

建议新增版本化提案：

```text
AssistantBlueprintProposal
├─ proposalId
├─ draftProjectId 或 conversationId
├─ proposalVersion
├─ proposalHash
├─ sourceTaskId/sourceExecutionId
├─ rawProposal
├─ normalizedSnapshot
├─ validationResult
├─ validatorVersion
├─ semanticCatalogVersion
├─ status
├─ estimatedCredits
├─ confirmedBy
├─ confirmedTime
├─ authorizationReceiptRef
├─ expiresAt
└─ createTime
```

状态只表达提案生命周期：

```text
DRAFT
VALIDATING
INVALID
AWAITING_CONFIRMATION
CONFIRMED
MATERIALIZED
SUPERSEDED
CANCELED
EXPIRED
```

它不承担 Task/Execution 调度，物化后的智能执行仍由 canonical TaskPlan 管理。

### Materializer 输入边界

客户端不能把任意 Snapshot JSON 直接提交给 Materializer。

ASSISTANT_GENERATED 来源的 MaterializeCommand 只提交：

```text
proposalId
proposalVersion
proposalHash
expectedDraftVersion
idempotencyKey
```

服务端重新加载 Proposal，并在物化事务前重新校验：

- Proposal 状态；
- proposalVersion/hash；
- 确认或授权回执；
- 用户和工作空间权限；
- 预算；
- 引用资源可访问性；
- 语义目录和执行绑定仍然有效；
- Snapshot 兼容版本。

### Snapshot 冻结

确认后写入：

```text
AigcProjectConfigSnapshot
├─ sourceType = ASSISTANT_GENERATED
├─ sourceRef = assistant-proposal:{proposalId}@{proposalVersion}
├─ snapshotHash
├─ projectSpecification
├─ projectInitialization
├─ objectSpecs
├─ relationSpecs
├─ actions
├─ deliverableSets
├─ processPolicy
├─ executionBindingVersions
├─ validatorVersion
└─ snapshotVersion
```

同时记录 Proposal、Task、Execution、确认人和授权回执引用，确保结构来源可追溯。

### 两种来源适配器

建议拆分来源产生方式：

```text
CatalogBlueprintSnapshotProvider
    ↓
AigcProjectBlueprint + MaterializeCommand
    ↓
BlueprintSnapshot

AssistantBlueprintSnapshotProvider
    ↓
已确认 AssistantBlueprintProposal
    ↓
BlueprintSnapshot
```

之后统一进入：

```text
BlueprintSnapshotValidator
→ BlueprintSnapshotNormalizer
→ BlueprintSnapshotFreezer
→ AigcProjectMaterializer
```

Materializer 不查询目录 Blueprint，也不调用 Assistant，只消费已验证和冻结的 Snapshot。

### 物化后统一流程

物化提交后两种来源完全一致：

```text
ProjectMaterializedEvent
    ↓ AFTER_COMMIT
生成项目文档 Proposal
    ↓
生成和采用 Brief 等项目上下文
    ↓
计算 ObjectReadiness
    ↓
生成 ProjectObject 内容 Proposal/TaskPlan
    ↓
DocumentVersion/ObjectVersion/MediaVersion candidate
```

后续生成不再按 CATALOG/ASSISTANT_GENERATED 分叉。

### 不自动发布目录 Blueprint

ASSISTANT_GENERATED Snapshot 默认只属于当前项目：

```text
AssistantBlueprintProposal
→ ProjectConfigSnapshot
→ 当前项目
```

如果用户希望复用，必须走独立提升流程：

```text
从项目 Snapshot 创建 Catalog Blueprint Draft
    ↓
抽取并重新设计动态数量、激活条件和可配置规则
    ↓
人工编辑、编译和发布校验
    ↓
成为新的 AigcProjectBlueprint 版本
```

不能将某个项目的具体对象图自动发布为全局目录模板。

### 当前实现缺口

当前真实代码尚未支持该路径：

- `AigcProjectMaterializer` 拒绝非 `CATALOG` sourceType；
- `AigcConfigurationService.resolve()` 只查询已发布 `AigcProjectBlueprint`；
- CATALOG sourceRef 必须匹配 `blueprint:{id}@{version}`；
- `AigcProjectConfigSnapshot` 数据库约束只允许 `source_type='CATALOG'`；
- MaterializeCommand 必须提供 blueprintVersionId；
- 尚无 AssistantBlueprintProposal 的版本、hash、验证和确认实体；
- 尚无统一 Snapshot Validator/Normalizer 来源边界。

因此这是已确认目标设计，不是当前代码已经具备的能力。

### 实施调整

- 放开 Snapshot sourceType 为 `CATALOG/ASSISTANT_GENERATED`；
- 新增 `AssistantBlueprintProposal` 及版本/hash/状态/确认模型；
- 增加类型化 `propose_project_blueprint` Tool；
- 增加 Proposal validate/confirm/cancel API；
- MaterializeCommand 使用 source-specific reference，不接受任意 Snapshot JSON；
- 拆分 Catalog Resolver 与 Assistant Proposal Provider；
- 抽取统一 BlueprintSnapshotValidator/Normalizer/Freezer；
- Materializer 改为只消费 validated Snapshot；
- 数据库约束允许两种 sourceType；
- 对话和表单使用同一 Proposal Confirmation UI/Interrupt；
- 物化后统一进入项目文档和对象生成流程；
- 增加 Proposal 审计、过期、幂等和授权回执；
- 增加无目录 Blueprint 的单元、集成和验收测试。

### 补充验收标准

- 无目录 Blueprint 项目在物化前仍有合法 BlueprintSnapshot；
- Assistant 只能创建 Proposal，不能直接写 Project/Object/Relation；
- Assistant 输出通过类型化 Tool 进入系统；
- 系统拒绝未知 objectType/relationType/actionKey/outputContract；
- 所有关系 canonical semantics 由服务端计算；
- 所有执行绑定由服务端解析和冻结；
- 未确认且无有效授权的 Proposal 不能物化；
- Proposal 修改后旧确认和旧 hash 失效；
- Materializer 不接受客户端任意 Snapshot JSON；
- CATALOG 与 ASSISTANT_GENERATED 共用 Validator、Freezer 和 Materializer；
- Snapshot sourceRef 可追溯到目录版本或 Assistant Proposal 版本；
- 物化事务内不存在模型调用；
- 物化后生成流程不按 Snapshot 来源分叉；
- ASSISTANT_GENERATED Snapshot 不自动发布为目录 Blueprint；
- 当前仅支持 CATALOG 的代码和数据库约束被明确替换；
- 对话模式可完成提案展示、修改、确认、物化和跳转到统一项目工作台。
