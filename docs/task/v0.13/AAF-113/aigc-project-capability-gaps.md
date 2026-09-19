---
level: Practice
layer: Model
purpose: 冻结 AAF-113 AIGC 项目创作与一次性 AI 定时触发的最终架构决策
status: active
version: 2.4.0
date: 2026-09-15
author: AaronZZH & Kiro
---

# AAF-113 AIGC 项目最终架构决策基线

## 最终黄金路径

```text
UI 结构化目标、用户对话或一次性项目定时提示词
→ 定时提示词到点时创建同一项目的 canonical Assistant Task
→ 选择目录 Blueprint，或 Assistant 按当前项目需求生成 BlueprintSnapshot
→ 校验并冻结不可变项目结构基线
→ 原子物化 ProjectGraph
→ 装配 ProjectContextSnapshot 与 ObjectReadiness
→ 创建或推进 canonical TaskPlan
→ Assistant 直接生成文本/文档正文并登记 candidate
→ Assistant 调用专业媒体 Tool
→ 持久外部等待与恢复
→ candidate 比较、采用或继续生成
→ 冻结 manifest 与 evidenceHash
→ 展示项目进度和实际积分
```

两种 BlueprintSnapshot 来源从校验开始共享同一 ProjectGraph、readiness、TaskPlan、candidate、Review 和 delivery 链。

## 最终决策索引

| 决策 | 状态 | 最终结论 |
|---|---|---|
| DEC-01 专业边界与真理源 | 已确认 | 保留 AIGC Project 专业聚合，各类事实继续由类型化资源拥有 |
| DEC-02 项目结构与不可变基线 | 已确认 | 目录和 Assistant 来源统一持久化到 AigcProjectConfigSnapshot |
| DEC-03 阶段、状态与进度 | 已确认 | AigcProjectLifecycle 即阶段字段，需拆分治理维度；四阶段叙述为阶段值分组视图 |
| DEC-04 Assistant 编排与操作入口 | 已确认 | canonical TaskPlan 是唯一智能计划，UI 智能与确定性操作分流 |
| DEC-05 依赖、输入版本与 readiness | 已确认 | 服务端派生 ObjectReadiness 并冻结实际输入版本 |
| DEC-06 Candidate、完成合同与持久等待 | 已确认 | 资源实际持久化才完成；文本/文档/图像真实跑通 |
| DEC-07 积分、监控与交付证据 | 已确认 | 积分账本是实际成本真理源，manifest 精确冻结交付证据 |
| DEC-08 项目 AI 定时触发 | 已确认 | 一次性时间点只负责创建既有 Assistant Task，状态在项目页与原对话共享 |

## DEC-01 专业边界与真理源

### AIGC Project 聚合

- 保留现有 `AigcProject`、`AigcProjectConfigSnapshot`、`AigcProjectObject`、`AigcProjectRelation`、ObjectVersion、DocumentVersion、MediaVersion、DeliverableSet、Review、AigcWork 和 Publication 主链。
- AAF-113 不建立万能 `ProjectObject`，不把 AIGC Project 物理迁移到通用 Project。
- `AigcProjectObject` 只表达 AIGC 内容对象的稳定身份、层级位置、合同角色、对象语义和采用指针，不承载 TaskNode、ExecutionRun、AigcTask 或通用 WorkItem。
- 通用 Project 后续通过 DomainBinding、ArtifactReference 和 ExecutionReference 引用 AIGC 专业事实，不复制正文、媒体、candidate、Run 或积分状态。

### 真理源分工

| 事实 | 唯一真理源 |
|---|---|
| 项目身份、专业配置、generationMode、粗粒度治理 | `AigcProject` |
| 不可变项目结构基线 | `AigcProjectConfigSnapshot` |
| 基线后的项目修改 | ProjectRevision |
| AIGC 内容树与非层级图关系 | `AigcProjectObject` / `AigcProjectRelation` |
| 结构化内容 | ObjectVersion |
| 所有自由文本正文 | DocumentVersion + ObjectVersion candidate |
| 图像、音频和视频资源 | MediaVersion + ObjectVersion/ref |
| 当前采用结果 | `adoptedVersionId` 与候选决策记录 |
| Assistant 目标、计划、等待和恢复 | canonical Task / TaskPlan / TaskNode / Execution |
| 专业调用、重试、退款和终态 | ExecutionRun / AigcTask |
| Tool 副作用与冻结完成标准 | InvocationReceipt |
| 实际积分 | Agent token metering 与积分账本交易 |
| 审核、交付和发布 | manifest / Review / AigcWork / Publication |

### Assistant 与专业 Tool 分工

- Assistant 是项目文本创作和智能编排主体。Brief、创意方向、提纲、文案和文档正文由当前 Assistant 在自身模型执行中生成。
- Assistant 通过受控 Tool 将所有自由文本写入 DocumentVersion，并登记只含版本元数据的 ObjectVersion candidate；结构化 JSON 才直接写入 INLINE ObjectVersion candidate；持久化 Tool 不再次调用模型生成正文。
- 图像、音频和视频由已注册专业 Tool 执行。Assistant 负责根据项目上下文组织目标、Prompt、参考资源、参数和后续步骤。
- 专业 Tool 继续复用 AigcTask、模型路由、ExecutionRun、MediaVersion、积分结算和失败退款链。

## DEC-02 项目结构与不可变基线

### BlueprintSnapshot 来源

BlueprintSnapshot 只允许以下来源：

| sourceType | 来源合同 |
|---|---|
| `CATALOG` | 从已发布 ProjectBlueprint 及项目配置解析 |
| `ASSISTANT_GENERATED` | Assistant 根据已澄清的当前 ProjectSpecification 直接生成 |

- `sourceType` 只表达结构来源，不是协作模式。
- Assistant 生成的项目级快照只属于当前项目，不自动发布为可复用 Blueprint。
- manual 下冻结结构前必须确认；auto 下只有明确授权覆盖目标、交付范围和结构自主权时才能冻结，超出授权范围必须确认。

### 持久化与修订

- 复用并扩展现有 `AigcProjectConfigSnapshot`，不新建第二套 BlueprintSnapshot 表。
- 快照至少持久化：

```text
sourceType / sourceRef
projectSpecification
objectSpecs
relationSpecs
outputContracts
processPolicy / confirmationGates / reviewRequired
snapshotVersion / snapshotHash
```

- BlueprintSnapshot 不可变；首次快照是项目结构基线。
- 后续项目修改形成 ProjectRevision；结构合同发生变化时创建新快照版本，不覆盖历史。
- 快照与 projectVersion、graphRevision、确认凭据和来源 Task/Execution 关联。
- 目录和 Assistant 来源使用同一个结构校验器、哈希算法和 `AigcProjectMaterializer` 原子物化事务。
- 物化失败不得留下半个 ProjectGraph。

### Brief 真理源

- Brief 的 DocumentVersion 是 Brief 正文唯一真理源。
- `AigcProject.brief` 只保留创建输入或可重建摘要投影，不作为独立可编辑正文。
- Brief 修改产生新资源版本和 ProjectRevision，不原地覆盖历史正文。

### ProjectGraph 层级与关系

- `parentId` 是 ownership/outline tree 的唯一写源；父子对象必须同项目、单父且无环。
- `contains` 由 `parentId` 在查询时派生，不允许独立编辑或持久化为第二层级真理源。
- `AigcProjectRelation` 保存非层级关系。
- BlueprintSnapshot 只提交 `relationType`、source 和 target；canonical layer、方向、允许端点、无环约束和 readiness 影响由服务端关系语义目录计算。
- 关系方向固定为 source=上游/来源、target=下游/结果。

核心 relationType 与 layer 映射：

```text
contains / derives / constrains / composes / variant → domain
references                                         → reference
orders                                             → story_order
execution_depends                                  → execution
```

图查询必须暴露计算后的 layer。

### 对象制品语义

ObjectSpec 必须冻结并由 ProjectGraph 暴露：

| 字段 | 最终值 | 责任 |
|---|---|---|
| `semanticRole` | STRUCTURE、WORKING_ARTIFACT、DELIVERABLE、EVIDENCE、REFERENCE | 区分结构、过程制品、交付物、证据和参考输入 |
| `plannedPhase` | START、DESIGN、BUILD、DELIVER、RUN | 标记主要形成阶段，不表示当前项目 phase 或对象状态 |
| `outputContract` | INLINE_OBJECT_VERSION、DOCUMENT_VERSION、MEDIA_VERSION、COMPOSITE_MANIFEST、REFERENCE_ONLY | 冻结对象完成时必须形成的权威资源 |

`semanticRole` 是 ProjectGraph 一等字段；`plannedPhase` 和 `outputContract` 来自不可变 ObjectSpec，并作为只读投影暴露给图查询、readiness 和结果校验。

## DEC-03 阶段、状态与进度

### 阶段合同

- `AigcProjectLifecycle` 本身即为持久化的阶段字段，不需要另建"第二个"phase 字段或独立 AIGC 阶段状态机；`AigcProjectLifecycle` 当前混装了阶段语义（现在在做哪类工程活动）与治理语义（项目治理生命周期是否仍然有效），需拆分为两个正交字段：阶段字段收窄为 `CONFIGURING/MATERIALIZING/MATERIALIZED/CREATING/REVIEWING/DELIVERING`；新增独立治理字段 `ACTIVE/SUSPENDED/CANCELLED/COMPLETED/ARCHIVED`（当前完全缺失中途暂停/取消的表达能力，是真实功能缺口，不只是命名洁癖）。此拆分涉及数据库迁移、后端多处状态校验和前端状态判断点（≥5 文件跨模块），按硬约束需协调者评估后实施，本决策基线记录目标形态，暂不视为立即变更。
- START、DESIGN、BUILD、DELIVER、RUN 是通用五阶段迭代循环的坐标术语，仅用于架构对照，验证 `AigcProjectLifecycle` 六值是否完整覆盖前四阶段；不作为独立于 `AigcProjectLifecycle` 的用户可见概念，不建立"4阶段"展示层。RUN 阶段依赖"已发布可运行能力+持续运行观测"前提，AIGC 项目产出静态内容交付物，此前提不成立，不需要凑成五阶段。
- 面向用户的阶段展示直接使用 `AigcProjectLifecycle` 六值（`CONFIGURING/MATERIALIZING/MATERIALIZED/CREATING/REVIEWING/DELIVERING`）及各自中文 label，粒度已足够直接可读，不需要先归并成更粗的分组再展示；现有 `page.tsx` 筛选 tab 已是这一模式的既有先例。
- `plannedPhase` 沿用同一套坐标术语，仅用于对象级规划提示，不建立第二套阶段真理源。
- 拆分后的阶段字段只承担写操作安全门（对应目标形态的 `MUTABLE_STATUSES` 等价集合）和粗粒度叙述分组，不用于推导 Assistant 下一步；细粒度进度推导继续由 `ObjectReadiness` 承担。
- Task 等待、专业执行、candidate、Review 和 Publication 状态分别保存在其类型化真理源中。

### ProjectObject 内容成熟度

ProjectObject 只暴露以下派生内容成熟度：

```text
EMPTY
CANDIDATE_AVAILABLE
ADOPTED
```

- `contractRole` 表达 REQUIRED、OPTIONAL、EXCLUDED。
- ObjectReadiness 表达 ready、blocked、active 和待确认。
- DeliverableSet completion/manifest 表达交付完成。
- Review 和 Publication 分别表达审核与发布状态。
- ProjectObject status 不承担确认、依赖、执行、审核、发布或项目生命周期语义。

### ProjectProgressSnapshot

UI 使用服务端派生的只读投影：

```text
ProjectProgressSnapshot
├─ objectiveAndScope
├─ taskPlanProgress
├─ currentAndWaitingNodes
├─ deliverableReadiness
├─ activeExecutionRefs
├─ pendingCandidatesAndDecisions
├─ reviewAndDeliverySummary
└─ actualCreditSummary
```

该投影聚合读取，不拥有任何底层状态，也不持久化为第二状态源。

## DEC-04 Assistant 编排与操作入口

### canonical TaskPlan

- canonical Task、TaskPlan、TaskNode 和 Execution 是 Assistant 智能编排的唯一计划真理源。
- ProjectGraph 只保存专业对象事实；TaskPlan 不复制 ProjectObject、AigcTask、专业 Run 或媒体子任务。
- 文本和文档步骤由 Assistant 自身完成；媒体步骤调用专业 Tool。
- 少量同能力、强顺序步骤由一个 ExecutorPlan 分步处理；只有独立工作包、跨模态能力或不同 Skill 才拆分 TaskNode。
- 有效并行度取 TaskPlan 上限、项目策略、预算窗口、供应商限流和 ready 工作包数量的最小值，并保持既有 1..8 合同。

### generationMode

- `AigcProject.generationMode` 只允许 `manual | auto`，是项目协作模式唯一真理源。
- manual 下，新的执行范围在首次副作用前由用户发起或确认。
- auto 下，Assistant 可在已确认的目标、交付范围、预算上限、质量门和普通动作范围内连续推进。
- auto 不得扩大目标、对象数量、OPTIONAL 范围或交付物，不得绕过权限、内容安全、强制确认、采用、审核、发布、完成和归档门。
- 模式切换使用专用类型化命令，只影响尚未提交的新动作。
- 已提交 Run 按创建时快照完成；活跃 Task 只能通过显式 Task 控制操作暂停、修订、恢复或取消。

### UI 与对话双入口

入口按“是否需要智能理解、创作或编排”判定，不按对象数量判定：

| 操作 | 最终入口 |
|---|---|
| 文本创作、结合项目组织媒体 Prompt、跨对象生成、自动或继续生成 | 结构化 Assistant Execution/Task |
| 已完整确定 Prompt、模型和参数的单项媒体生成 | 专业媒体 API |
| 手工文本保存、结构编辑、合同角色修改 | 类型化 Project API |
| 采用/否决明确 candidate | 类型化 Project API |
| 取消/重试明确 Run | 类型化 Execution API |

- UI 智能目标直接提交结构化 objective、projectId、focusedObjectId 和 explicitScope，不伪造聊天文本。
- 对话继续使用现有 Assistant 和统一 AG-UI，不新增项目专属 Assistant、Agent、Team 或对话端点。
- 两类入口共享 ProjectGraph、版本资源、candidate、adopted 指针、专业 Tool、Activity 和积分账本。
- TanStack Query 管理服务端状态；Zustand 只管理本地焦点、面板和未提交表单。

### 能力维度与来源、模态正交

- 决定入口是类型化 API 还是结构化 Assistant Task 的唯一标准是该动作是否需要智能理解、创作或编排；不因触发界面是 UI 按钮还是对话消息而改变判定，也不因项目结构来源是 CATALOG 还是 ASSISTANT_GENERATED 而改变判定。
- BlueprintSnapshot 的来源只表达项目结构最初如何产生，不限定该项目此后能否使用 Assistant 生成正文、媒体或结构调整；CATALOG 与 ASSISTANT_GENERATED 项目物化完成后共享同一组 Assistant Tool 可见性规则。
- UI 与对话是同一 Assistant 能力的两种触发界面，不是两个能力档次。UI 上的生成简报、重新生成等操作在 Assistant Task 基础设施就位后同样经由 canonical Task 执行，只是结果原位展示在页面而非对话气泡中。
- 工程任务顺序（tasks.md 阶段一、阶段二）表达搭建先后：先建立确定性 ProjectGraph、readiness、candidate 下游机制，再叠加 Assistant 生成能力；不表达两个阶段对应两种最终产品体验。阶段一期间 CATALOG 项目的手工录入（如手写简报）是 Assistant Tool 基础设施尚未就位前的过渡状态，不是该来源项目的永久上限。

### 单路径切换

- 新文本、文档、图像和恢复闭环通过上线门禁后，UI、Assistant Tool 和动作权限一次性切换到 canonical TaskPlan 路径。
- 切换后停止接受被替换批量入口的新请求。
- 在途 root/child 执行必须完成或取消，并结清 reserved、settled 和 refunded 交易。
- 删除被替换入口的服务特判、seed、binding、调用方和测试。
- 历史执行引用只读保留，不转换或复制为 TaskPlan。
- 系统不保留 fallback、dual-write、兼容适配器或长期并行入口。

## DEC-05 依赖、输入版本与 readiness

### 项目上下文

每次项目 Assistant 执行自动装配紧凑 ProjectContextSnapshot：

```text
projectId / projectVersion / graphRevision
lifecycle / generationMode / semanticCatalogVersion
ProjectSpecification / Brief 摘要 / focusedObject
当前对象语义子集
缺失对象与 ObjectReadiness
活动 Run / 待决 candidate / 待确认事项
实际成本摘要 / allowed actions
```

完整 ProjectGraph、版本正文、candidate、Run、素材和低频语义按需查询，不一次性注入 Prompt。用户描述和聊天历史只表达意图，不替代权威项目查询。

### ObjectReadiness

BlueprintSnapshot 定义依赖并物化为对象级依赖。服务端根据当前图谱、actionKey、版本、活动 Run、权限、策略、确认和本次范围派生 ObjectReadiness：

```text
SATISFIED
READY
BLOCKED_BY_DEPENDENCY
BLOCKED_BY_POLICY
ACTIVE_RUN
PENDING_CANDIDATE
AWAITING_CONFIRMATION
```

判定规则：

- EXCLUDED 和未选 OPTIONAL 不进入执行范围。
- adopted 对象默认 SATISFIED；只有用户明确要求新 candidate 时才重新生成。
- active Run 或 pending candidate 默认不重复提交。
- 下游输入优先使用 adopted 版本。
- 同一已授权 current TaskPlan 中唯一有效 candidate 可作为中间输入，但不自动 adopted。
- 多个 candidate 必须暂停选择。
- 有依赖对象只有在所有必需上游存在可用版本后才 READY。
- 每次专业调用冻结实际 inputVersionIds、projectVersion、graphRevision、actionKey、确认凭据和完成标准。
- 运行中图变化只影响后续新计划，不改写已冻结调用。
- readiness 是查询投影，不写入 ProjectObject，也不由 Assistant 或浏览器自行计算。

首期依赖采用明确 ALL 语义，不扩展复杂依赖基数。

### 项目语义三层机制

| 层级 | 保存内容 |
|---|---|
| Skill | 跨项目稳定原则和少量核心值 |
| ProjectContextSnapshot | 当前项目实际使用的精简语义子集 |
| `inspect_project_semantics` | 完整、低频、版本化定义及完成证据 |

- `ProjectSemanticCatalog` 不建立人工维护的第二语义数据库。
- 语义目录从固定核心枚举注册表、已发布 ProjectType、当前 BlueprintSnapshot、DomainExtension、relationType 目录和 Tool/Action metadata 编译。
- 编译结果生成 `semanticCatalogVersion`；项目上下文只注入当前图谱和动作涉及的语义。
- `inspect_project_semantics` 由 Resource Catalog 注册为系统内置只读 Tool，不属于单一 Skill，可供不同专业项目 Skill 复用。
- Tool 可见性取 Resource Catalog、Assistant policy、Role allowlist、SkillVersion requirement、项目上下文和用户权限的交集。
- projectId、workspace 和 domain scope 由服务端 InvocationContext 绑定；Tool 不授予写权限。
- 未知、废弃、未发布或版本不一致的语义值返回稳定错误，Assistant 不得猜测。

## DEC-06 Candidate、完成合同与持久等待

### 完成语义分层

| 层次 | 完成条件 |
|---|---|
| Tool 调用 | 副作用已幂等持久化并返回稳定引用 |
| 对象步骤 | outputContract 对应的权威资源与 candidate 已持久化 |
| Assistant Task | 冻结的 CompletionCriteria 与失败策略已满足 |
| Project 交付 | 必需对象 adopted，manifest/evidenceHash 冻结，审核与发布策略满足 |

Tool 返回 executionRunId 只表示 durable accepted，不表示对象资源、Assistant Task 或项目交付已完成。

### 输出合同

| outputContract | 必须持久化的完成证据 |
|---|---|
| `INLINE_OBJECT_VERSION` | 仅结构化 JSON 的 ObjectVersion candidate |
| `DOCUMENT_VERSION` | 自由文本 DocumentVersion + ObjectVersion candidate |
| `MEDIA_VERSION` | MediaVersion + ObjectVersion/ref |
| `COMPOSITE_MANIFEST` | manifest ObjectVersion + evidenceHash |
| `REFERENCE_ONLY` | 已校验的类型化资源引用 |

- CompletionCriteria 在调用时由系统根据 action/result contract 冻结到 InvocationReceipt。
- Run 成功但预期资源缺失时，对象步骤不得完成。
- 资源已存在但专业 Run 未终态时继续等待。
- 部分成功按冻结失败策略完成、重试或暂停。
- candidate 默认不自动 adopted；采用、替换和否决保留版本、原因、操作者和并发检查。

### 多模态范围

- 底层合同覆盖文本、文档、图像、音频和视频。
- AAF-113 必须真实跑通：
  - 文本：Assistant 生成并登记 DocumentVersion 与 ObjectVersion candidate；
  - 结构化数据：登记不含自由文本的 INLINE_OBJECT_VERSION candidate；
  - 文档：Assistant 生成正文并登记 DocumentVersion 与 ObjectVersion candidate；
  - 图像：专业 Tool 生成 MediaVersion 与 ObjectVersion/ref。
- 音频和视频在现有专业 Tool 可用时接入同一 MEDIA_VERSION、等待、candidate 和积分链。
- 对应专业 Tool 不可用时返回稳定 capability unavailable，只后置适配器，不改变 ProjectGraph、TaskPlan、candidate 或完成模型。
- 消息只保存稳定 project/object/run/artifact/version 引用和安全摘要，不复制正文、二进制、图谱或 Run 状态。

### 持久外部等待

```text
专业 Tool durable accepted
→ InvocationReceipt 冻结关联与 CompletionCriteria
→ TaskNode + Execution = AWAITING_EXTERNAL
→ 当前 Dispatch 结束并释放 Agent 线程与 lease
→ terminal/candidate after-commit 事件或恢复扫描
→ 校验 current TaskPlan / Execution / fence / run ownership
→ 创建新 Dispatch
→ 查询权威 Run 与资源版本
→ 完成、重试、失败或暂停
```

- ReAct 只承担当前活跃 Dispatch 内的短周期读取、推理和 Tool 调用，不通过 sleep 或持续轮询等待外部任务。
- after-commit 内部事件是快速路径；扫描补偿事件丢失、服务重启和消费者中断。
- 重复、迟到、旧 plan 或已取消 Task 的事件不得复活旧执行或重放 Tool 副作用。
- Activity SSE 只通知 UI 重新查询，不推进 Task，也不作为完成真理源。

## DEC-07 积分、监控与交付证据

### 实际积分

- 积分账本是实际消费唯一真理源。
- Binding 或模型预估值只用于执行前提示；reservation 只表示预留。
- 必须区分 `estimated`、`reserved`、`settled` 和 `refunded`；实际净成本为 settled 减 refunded。
- `AigcProject.costUsed` 只作为可从账本重建的投影，不参与结算真理判断。
- 必须建立以下可追踪关联：

```text
project
→ canonical Task
→ Assistant Execution / toolCall / InvocationReceipt
→ ExecutionRun / AigcTask
→ candidate / resource version
→ creditTx
```

- 无法归属的交易显示“待归集”，不得按零成本处理，也不得以固定预估替代实际消费。

### 监控投影

ProjectProgressSnapshot 必须展示：

- 项目目标与本次范围；
- TaskPlan 进度、当前节点和外部等待；
- ObjectReadiness 与阻塞原因；
- 活动 ExecutionRun/AigcTask、失败和重试；
- candidate、adopted 和待确认事项；
- Review、manifest 和发布摘要；
- estimated、reserved、settled、refunded 和实际净成本。

### 可重现交付证据

- DeliverableSet completion 只计算 REQUIRED 和已选 OPTIONAL。
- 纳入对象必须有 adoptedVersionId、有效 validationStatus，且不存在活动 reservation。
- manifest 冻结 graphRevision、includedObjectIds、精确 ObjectVersion、DocumentVersion/MediaVersion 引用、validationStatus 和 evidenceHash。
- adopted ObjectVersion 及其制品引用不可原地漂移，或必须由可验证内容哈希覆盖。
- adopted 指针变化后，依赖旧 manifest 的 Review/Publication 保持历史快照或明确 stale，不改写历史事实。
- 生成成功、candidate、adopted、审核通过和发布完成保持不同层级。

## DEC-08 项目 AI 定时触发

### 首期合同

- 首期只支持一次性 `RUN_ONCE` 时间点。每个 `ProjectSchedule` 保存一个 `executeAt`、IANA `timezone` 和用户明确填写的 `prompt`；多个时间点保存为多条独立记录。
- 定时能力只决定“何时把提示词交给 Assistant”，不预生成 TaskPlan，不保存 inputVersionIds/readiness，不执行内部 handler，也不成为第二智能计划。
- 到点后通过既有结构化 Assistant Task API 提交 `projectId + prompt + assistantId + conversationId + scheduleId`，复用同一个 Assistant、canonical TaskPlan、专业 Tool、candidate、积分、确认和恢复链。
- 到点时若项目存在未终结 current Task，固定进入 `DEFERRED`，待项目空闲后再触发；首期不提供 SKIP/REPLACE/并行等用户可配置冲突策略。
- 每次触发重新校验 schedule 归属者、项目访问权、项目 lifecycle、当前 projectVersion/graphRevision、current Task、预算、内容安全和强制确认策略。真正调用 Tool 时继续使用既有 ObjectReadiness、allowedActions 和版本冻结；定时配置不得信任创建时快照。
- schedule 本身不得绕过确认。提示词涉及采用、送审、发布、完成、归档或其他强制确认动作时，canonical Task 进入既有等待确认状态。
- 复用统一任务调度框架运行一个项目 schedule due scanner；通过原子 claim、fence 和 `scheduleId + executeAt` 幂等键确保同一时间点至多创建一个 canonical Task。服务重启后对到期未触发记录补跑一次。
- `ProjectSchedule` 保存关联的 canonical `linkedTaskId`。项目页和绑定的 Assistant 对话都查询同一 schedule/Task 权威状态；SSE 只触发刷新，不复制状态机。

### 首期边界

- 用户可创建、编辑未触发记录、暂停、恢复和取消 schedule；已触发记录保留只读审计，不物理删除关联历史。
- 首期不支持 recurrence、任意 cron、固定频率、节假日日历、定时调用类型化 Project API、手动立即执行、并行创建第二 current Task，或 Assistant 通过 Tool 管理 schedule。
- 首期不新增 `ProjectScheduleFire`、第二 scheduler、第二项目执行器、第二对话端点或第二 Task 状态源；一条 RUN_ONCE schedule 即一次触发事实。

## AAF-113 实施范围

必须交付：

- CATALOG 与 ASSISTANT_GENERATED 两种 BlueprintSnapshot 来源的统一持久化、校验、哈希和原子物化；
- `AigcProjectConfigSnapshot` v2、ProjectRevision 和 Brief 正文真理源；
- parentId 单一层级源、relationType→layer、对象语义和 ProjectObject 三态成熟度；
- ProjectContextSnapshot、ObjectReadiness、ProjectSemanticCatalog 和受控语义 Tool；
- content-creator Role、项目协作 Skill、Assistant policy 和项目 Tool 权限交集；
- canonical TaskPlan、文本/文档 candidate Tool、专业图像 Tool 和冻结输入版本；
- AWAITING_EXTERNAL、InvocationReceipt、资源完成判定、事件恢复和补偿扫描；
- UI 智能/确定性双入口、manual/auto 边界和模式切换；
- ProjectProgressSnapshot、全链关联、实际积分和失败退款；
- manifest 精确版本与 evidenceHash；
- 一次性项目 AI 定时提示词、统一 scheduler 扫描、canonical Task 触发和项目页/对话状态查看；
- 新路径上线门禁、单路径切换、在途执行处置和被替换入口删除。

## 非目标

- 通用 Project、Goal、Deliverable、ArtifactReference、Evidence 和 WorkItem 的物理表；
- AIGC→通用 Project 数据迁移或双写同步层；
- 完整持久阶段引擎、阶段 baseline、复杂 Gate 和阶段预算；
- 任意 JSON 项目、万能项目对象、万能写 Tool 或任意关系 DSL；
- 可视化 Blueprint 编辑器、模板市场和自动将项目快照发布为模板；
- 复杂依赖基数、大型全图 DAG 引擎和跨项目 Artifact 仓库；
- 自动采用、自动送审、自动发布、自动完成或自动归档；
- 在专业 Tool 缺失时于本故事内实现新的音频或视频供应商适配器；
- 复杂预算预留、成本优化和运行价值分析；
- 周期计划、任意 cron、定时确定性 Project API 动作和可配置并发策略。

## 强制不变量

- AIGC Project 聚合保留，不建立万能 ProjectObject。
- `AigcProjectConfigSnapshot` 是两种 Blueprint 来源共同的不可变基线存储。
- Brief 的 DocumentVersion 是正文真理源。
- parentId 是层级唯一写源；relation layer 只能由 relationType 目录计算。
- semanticRole、plannedPhase 和 outputContract 来自冻结 ObjectSpec。
- ProjectObject status 只表达 EMPTY、CANDIDATE_AVAILABLE、ADOPTED。
- canonical TaskPlan 是唯一智能编排计划；ProjectGraph 与专业执行不复制为 TaskNode。
- readiness 由服务端派生；Assistant 和 UI 不自行计算。
- Tool accepted、对象资源完成、Task 完成和项目交付完成彼此独立。
- AWAITING_EXTERNAL 不持有 Agent 线程、Dispatch lease 或前端长连接。
- candidate 默认不自动 adopted；auto 不扩大范围或绕过治理门。
- 积分账本是实际消费唯一真理源；costUsed 可重建。
- manifest 必须精确引用可重现的版本资源和 evidenceHash。
- 新路径上线后执行单路径切换，不保留 fallback、dual-write 或兼容层。
- ProjectSchedule 只保存一次性时间点和提示词；到点只创建既有 canonical Task。
- 同项目 current Task 未终结时 schedule 固定延后，不创建竞争 Task。
- 项目页与 Assistant 对话展示同一 schedule/linkedTaskId 的权威状态。

## 下游文档映射

| 决策 | requirement.md | design.md | tasks.md |
|---|---|---|---|
| DEC-01 | 项目边界与真理源 | 真理源与聚合边界 | #11301～#11305 |
| DEC-02 | BlueprintSnapshot 与项目基线、ProjectGraph 语义 | BlueprintSnapshot、ProjectGraph | #11301～#11303 |
| DEC-03 | 项目阶段与进度 | 阶段、成熟度、ProjectProgressSnapshot | #11303、#11311 |
| DEC-04 | UI 与对话、generationMode、canonical TaskPlan | Assistant 编排、双入口、单路径切换 | #11305、#11310、#11314 |
| DEC-05 | Assistant 上下文与 readiness | ProjectSemanticCatalog、ObjectReadiness | #11304～#11306 |
| DEC-06 | 结果、候选、多模态与持久等待 | 完成合同、专业 Tool、外部恢复 | #11307～#11309 |
| DEC-07 | 实际积分、监控与交付 | 进度、账本关联、manifest | #11311、#11312 |
| DEC-08 | 项目 AI 定时任务 | ProjectSchedule、due scanner、Assistant Task 触发与状态投影 | #11319、#11320 |

## 相关文档

- [最终需求与验收标准](requirement.md)
- [最终技术设计](design.md)
- [最终实施任务](tasks.md)
- [Assistant 项目协作 Skill](skill.md)
