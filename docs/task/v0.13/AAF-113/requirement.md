---
level: Practice
layer: Product
purpose: 定义 AAF-113 基于 Assistant、项目制品图、canonical TaskPlan 和一次性 AI 定时触发的项目创作闭环
status: active
version: 2.4.0
date: 2026-09-17
author: AaronZZH & Kiro
tags:
  - AAF-113
  - Assistant
  - Content Studio
  - TaskPlan
  - BlueprintSnapshot
  - ProjectSchedule
  - ProjectGraph
related:
  - aigc-project-capability-gaps.md
  - design.md
  - ui-design.md
  - tasks.md
  - skill.md
  - ../AAF-112/requirement.md
  - ../AAF-115/requirement.md
  - ../../../design/apps/content-studio/content-studio-design.md
  - ../../../design/apps/content-studio/content-studio-tech.md
---

# AAF-113 Assistant 项目创作闭环

## AIGC 项目基本需求与目标

1. **统一项目载体**：AIGC 项目统一承载用户目标、项目结构、创作对象、素材、执行任务、候选版本、最终产出物和交付记录，界面与对话操作必须落到同一项目事实。
2. **支持有蓝图创建**：用户可在界面选择已发布 Blueprint 创建项目，也可在对话中让 Assistant 解释、推荐并基于选定 Blueprint 协作；系统冻结项目级 BlueprintSnapshot 后再物化对象结构。
3. **支持无蓝图创建**：没有适用 Blueprint 时，界面不得阻断创建。Assistant 根据用户目标、交付物、约束和参考信息生成项目级 BlueprintSnapshot，经用户确认或既有明确授权后冻结。
4. **统一有蓝图与无蓝图链路**：两种来源只在结构产生方式上不同，后续统一进入 BlueprintSnapshot 校验、ProjectGraph、ObjectReadiness、canonical TaskPlan、candidate、adopted 和 delivery 链，不形成两套项目模式。
5. **支持界面局部微调**：用户可在项目结构、图谱、对象详情或画布中直接执行生成图像、重新生成、重新编辑、添加内容、添加下级、调整合同、采用或否决候选等明确操作；结果默认形成新 candidate，不覆盖已采用版本。
6. **支持对话式项目协作**：用户可在现有 Assistant 对话中描述项目目标、补充要求、修改单个对象或要求跨对象处理；对话与界面共享项目上下文、对象版本、任务和执行结果。
7. **支持批量自主与交互式生成**：Assistant 可按用户选择的 `manual | auto` 模式，对全部或明确范围内对象进行批量生成。manual 按用户发起或确认的范围推进；auto 只在已授权目标、范围、预算和质量门内自主连续推进。
8. **支持单点智能修改**：除界面确定性微调外，用户也可通过 Assistant 对单个对象提出自然语言修改要求，由 Assistant 理解上下文、生成文本或组织专业 Tool 参数，并形成可比较的新 candidate。
9. **按项目流程有序生成**：系统根据 BlueprintSnapshot 和 ProjectGraph 中的依赖、对象合同及当前版本计算 ObjectReadiness；Assistant 只推进已就绪对象，不重复生成 active Run、待处理候选或默认已满足对象。
10. **统一多模态产出**：Assistant 自身生成 Brief、提纲、文本和文档正文；图像、音频、视频由专业 Tool 执行。各类结果最终保存为 ObjectVersion、DocumentVersion、MediaVersion 或组合 manifest，并关联到项目对象。
11. **保留版本与候选治理**：生成、重新生成和重新编辑均保留来源、输入版本、执行引用和 candidate；用户可比较、采用或否决，系统不得把生成成功直接等同于采用、审核通过或交付完成。
12. **使用唯一智能任务模型**：所有需要理解、创作或跨对象编排的操作都进入 canonical Task/TaskPlan；项目图、专业 Run、媒体任务和积分事实不复制为第二套计划或状态源。
13. **支持异步执行与恢复**：专业媒体生成可进入外部等待；页面关闭、网络断开、服务重启或事件重复时，系统仍可根据持久 Task、Run 和资源事实恢复，不依赖原浏览器连接保持执行。
14. **支持一次性 AI 定时提示词**：用户可为项目设置一个或多个一次性时间点和提示词；到点后系统创建现有 canonical Assistant Task。项目忙时延后到空闲，项目页与绑定对话显示同一任务状态。
15. **提供统一进度与成本视图**：项目界面和对话可查看当前 Task、对象 readiness、专业 Run、候选、待确认事项、失败原因和实际积分；实际成本以积分账本结算与退款事实为准。
16. **形成可审计最终交付**：必需对象采用并验证后，系统冻结精确对象版本、文档/媒体引用、graphRevision 和 evidenceHash，形成可重现的最终产出物清单。
17. **全过程受治理约束**：界面、对话、自动模式和定时触发都必须在执行时重新校验权限、项目版本、生命周期、预算、内容安全和强制确认；不得自动采用、送审、发布、完成或归档。

上述“统一”是指有蓝图与无蓝图项目共享同一业务链；`manual | auto` 仍只表示用户与 Assistant 的协作控制方式，不增加第三种项目模式。

## 决策基线

本需求以 [AAF-113 AIGC 项目能力与架构决策基线](aigc-project-capability-gaps.md) v2.2.0 为唯一决策来源。本文只定义最终需求，不保留被替代的入口、模式或编排方案。

## 背景

AAF-112 已提供 AIGC Project、ProjectGraph、ObjectVersion、DocumentVersion、MediaVersion、ExecutionRun、候选采用与交付链。AAF-115 已提供 canonical Task、TaskPlan、TaskNode、Execution 和持久调度。AAF-113 将两者连接为一个 AI 原生项目创作闭环：Assistant 理解项目目标、生成文本、编排专业媒体 Tool，并在异步资源持久化后恢复同一 TaskPlan；项目服务负责结构、版本、readiness、权限、候选、交付证据和实际积分事实。用户还可设置一次性时间点和项目提示词，到点后由现有 Assistant 创建同一 canonical Task，并在项目页与绑定对话中查看状态。

## Epic

**目标**：用户可从目录 Blueprint 或当前项目需求创建不可变 BlueprintSnapshot，通过同一个 Assistant 完成项目创作闭环，并可把一个明确项目提示词设置到一次性时间点自动提交给该 Assistant。

**范围**：

- 目录来源和 Assistant 生成来源的统一 BlueprintSnapshot；
- AIGC 专业 ProjectGraph 的结构与制品语义硬化；
- Assistant 与 UI 双入口、`manual | auto` 协作策略和 canonical TaskPlan；
- 服务端 ObjectReadiness、冻结输入版本和受控项目 Tool；
- 文本、文档、图像的真实端到端完成链，以及音频、视频的统一底层合同；
- 持久外部等待、候选决策、ProjectProgressSnapshot、积分归集与断线恢复；
- 旧批量生成入口的一次性退役；
- 一次性项目 AI 定时提示词、到点触发和项目页/Assistant 对话状态查看。

## 用户故事

### 从项目需求建立结构基线

**作为**没有适用目录模板的内容创作者，**我希望** Assistant 根据已澄清的当前项目需求生成项目级 BlueprintSnapshot，**以便**项目仍能获得合法、可审计、可物化的对象结构、依赖和交付合同。

**完成条件**：Assistant 生成来源与目录来源经过同一校验、持久化和 ProjectGraph 物化链；首次快照成为不可变项目结构基线。

### 通过 Assistant 创作和编排

**作为**项目创作者，**我希望** Assistant 自身完成 Brief、创意和文案创作，并根据项目依赖调用专业媒体 Tool，**以便**一个 canonical TaskPlan 能持续表达目标、步骤、等待和恢复，而不复制项目图或专业执行状态。

**完成条件**：文本形成受控 candidate；专业媒体执行形成可追踪 Run 和资源版本；异步等待不占用 Agent 线程或依赖长连接。

### 受控手动或自动推进

**作为**需要不同控制力度的用户，**我希望**项目在 manual 下按确认范围推进，在 auto 下于已授权范围内连续推进，**以便**自动化不扩大目标，也不绕过权限、预算、质量和不可逆确认门。

**完成条件**：`AigcProject.generationMode` 是唯一协作模式真理源；模式切换只影响尚未提交的动作，不隐式修改活跃 Task 或已提交 Run。

### 通过 UI 和对话操作同一项目

**作为**使用项目页面的用户，**我希望**智能创作进入 Assistant Task，而已完全确定的结构、候选和 Run 操作直接调用类型化 API，**以便**两种入口共享同一项目、候选、执行和计费事实。

**完成条件**：入口按是否需要智能理解、创作或编排判定，不按对象数量判定；UI 不复制服务端状态机。

### 查看可恢复的结果与实际成本

**作为**可能关闭页面或跨会话继续工作的用户，**我希望**查看 TaskPlan、readiness、Run、候选、待确认事项和结算后实际成本，**以便**理解项目当前进展，并在断线后恢复同一业务事实。

**完成条件**：SSE 仅触发刷新；数据库资源、InvocationReceipt、Task fence 和积分账本决定完成与成本。

### 查看单个对象与项目累计的实际积分消耗

**作为**在项目中持续生成文案、图像、视频等内容的用户，**我希望**在每个项目对象旁看到该对象已实际消耗的积分，并在项目层面看到累计实际消耗，且这两个数字在任意对象完成新一轮生成或采纳后及时刷新，**以便**清楚知道每一次生成的真实代价和项目整体花费，而不必等到交付阶段才了解成本。

**完成条件**：
- 单对象消耗以该对象关联的 `ExecutionRun`/`AigcTask` 结算流水（`CreditTransaction.type=SPEND` 且已扣除对应退款）聚合得出，不使用预计或预留金额冒充实际消费；
- 项目累计消耗是项目内全部对象单项实际消耗之和，与既有 `ActualCreditSummary`（`estimated`/`reserved`/`settled`/`refunded`）的 `settled - refunded` 净额一致，不新增第二套计算口径；
- 任务失败自动退款（`AigcTaskService.failTask` 已有的 `creditGuard.refund` 机制）后，两个数字必须体现退款结果，不得停留在扣费时的旧值；
- 刷新依赖全局事件 SSE（`GET /api/events/stream`，AIGC 事件类型前缀 `aigc.`）的"变化通知即失效重查"机制，不新增第二条推送通道，不在 SSE payload 中直接携带积分数值；
- 不修改或依赖 `AigcProject.costUsed` 字段（该字段当前无写入，属已知技术债，不作为本需求的数据来源）。

### 定时提交项目提示词

**作为**项目创作者，**我希望**为项目设置一个或多个一次性执行时间点和明确提示词，**以便**系统到点后自动把该提示词交给现有 Assistant，并让我在项目页和绑定对话中查看同一任务状态。

**完成条件**：每个时间点独立保存；到点只创建 canonical Assistant Task；项目忙时延后到空闲；所有权限、预算、确认和安全门在触发与 Tool 执行时重新校验。

## 需求规格

### 项目边界与真理源

- 保留现有 AIGC Project 专业聚合，不建立万能 `ProjectObject`，不在 AAF-113 中物理迁移到通用 Project。
- `AigcProjectObject` 表达 AIGC 内容对象的稳定身份与结构位置；TaskNode、ExecutionRun、AigcTask、Review、Publication 和积分交易保留各自类型化真理源。
- canonical TaskPlan 是 Assistant 智能编排的唯一计划真理源；ProjectGraph 是项目对象、结构和关系真理源，二者不得相互复制。
- Assistant 是项目文本创作和智能编排主体；Brief、创意方向、提纲、文案等文本不得再经独立文案智能体二次生成。
- 图像、音频、视频等媒体由专业 Tool 执行；Assistant 负责基于项目上下文组织目标、Prompt、参数和后续步骤。

### BlueprintSnapshot 与项目基线

BlueprintSnapshot 只允许两种来源：

- `CATALOG`：从已发布目录 Blueprint 和项目配置解析；
- `ASSISTANT_GENERATED`：Assistant 根据当前项目的 ProjectSpecification 直接生成。

两种来源必须共享以下合同：

```text
ProjectSpecification
→ BlueprintSnapshot 校验
→ 不可变快照持久化
→ ProjectGraph 原子物化
→ ObjectReadiness
→ canonical TaskPlan
→ candidate / adopted / delivery
```

- 复用并扩展现有 `AigcProjectConfigSnapshot`，不新建第二套 BlueprintSnapshot 表。
- 快照至少持久化 `sourceType/sourceRef`、`projectSpecification`、`objectSpecs`、`relationSpecs`、`outputContracts`、`snapshotVersion` 和 `snapshotHash`。
- `projectSpecification` 必须使用类型化合同，只保存项目类型、生产模式、初始化值、渠道、执行绑定引用和数量覆盖等项目级输入事实；不得再嵌入原始 Blueprint 定义或与 payload 顶层 `objectSpecs/relationSpecs/actions/deliverableSets/processPolicy` 重复的副本。
- BlueprintSnapshot 不可变；首次快照是项目结构基线。后续修改形成 ProjectRevision；确需改变结构合同时创建新快照版本，不覆盖历史。
- Assistant 生成的提案只有在用户确认或已存在明确结构自主授权后才能冻结；超出授权范围必须重新确认。
- 项目级快照默认只属于当前项目，不自动发布为目录模板。
- Brief 正文的唯一读取路径是项目级 `AigcProjectDocumentRef.selectedDocumentVersionId → DocumentVersion`；不保留 `AigcProject.brief`，也不把 Brief 建模为 ProjectObject。

### ProjectGraph 语义

- `parentId` 是层级树唯一写源；`contains` 由 `parentId` 派生，不允许独立写入。
- 所有父子对象必须属于同一项目，层级必须单父且无环。
- BlueprintSnapshot 关系只提交 `relationType`、source 和 target；canonical layer、方向约束、端点约束和是否影响 readiness 由服务端语义目录计算。
- 关系方向固定为 source=上游/来源、target=下游/结果。
- relationType 与 layer 的核心映射为：domain、reference、story_order、execution 四层；图查询必须暴露计算后的 layer。
- ObjectSpec 必须包含并由 ProjectGraph 暴露：
  - `semanticRole`：STRUCTURE、WORKING_ARTIFACT、DELIVERABLE、EVIDENCE、REFERENCE；
  - `outputContract`：INLINE_OBJECT_VERSION、DOCUMENT_VERSION、MEDIA_VERSION、COMPOSITE_MANIFEST、REFERENCE_ONLY。
- START、DESIGN、BUILD、DELIVER、RUN 只作为 Skill/TaskPlan 的软工作坐标，不进入 ObjectSpec、ProjectObject 或图查询合同。
- ProjectObject 内容成熟度只暴露 `EMPTY | CANDIDATE_AVAILABLE | ADOPTED`。blocked、ready、review、done、excluded 分别由 readiness、Review、交付事实和 contractRole 表达。

### 项目阶段与进度

- AAF-113 不新增持久化 phase 字段或阶段状态机。
- START、DESIGN、BUILD、DELIVER、RUN 作为通用工作坐标；现有 lifecycle 只承担粗粒度治理和写操作安全门。
- UI 使用服务端派生的 ProjectProgressSnapshot 分层展示项目目标与范围、TaskPlan、ObjectReadiness、ExecutionRun/AigcTask、候选与采用、审核交付和实际积分。
- ProjectProgressSnapshot 是只读投影，不取得任何底层事实所有权。

### Assistant 上下文与语义

每次项目 Assistant 执行自动装配紧凑 ProjectContextSnapshot，至少包含：

```text
projectId / projectVersion / graphRevision
lifecycle / generationMode / semanticCatalogVersion
ProjectSpecification 与 Brief 摘要
focusedObject
当前对象语义子集
缺失对象与 ObjectReadiness
活动 Run / 待决候选 / 待确认事项
实际成本摘要 / allowed actions
```

- 完整图谱、版本正文、候选、Run、素材和低频语义按需调用只读 Tool，不一次性注入 Prompt。
- `ProjectSemanticCatalog` 不建立人工维护的第二语义数据库；它从固定核心枚举注册表、已发布 ProjectType、BlueprintSnapshot、relationType 目录和 Tool/Action metadata 编译，并生成 `semanticCatalogVersion`。行业软性参考通过项目知识库或精确版本参考文档进入上下文，不恢复 DomainExtension。
- `inspect_project_semantics` 是 Resource Catalog 内置只读 Tool；是否可见由 SkillVersion requirement、Assistant policy、Role、项目上下文和用户权限的交集决定。
- projectId、workspace、domain scope 由服务端绑定；Tool 不授予写权限。未知、废弃或版本不一致的语义值必须拒绝，Assistant 不得猜测。

### UI 与对话双入口

- 对话入口通过现有 Assistant 处理自然语言项目目标；不新增项目专属 Assistant、Agent、Team 或对话端点。
- UI 中需要理解上下文、创作文本、组织 Prompt、跨对象规划或决定后续步骤的操作，创建结构化 Assistant Execution 或 Task。
- 项目级批量生成必须提交结构化 Assistant Task；同项目 current Task 未终结时批量按钮禁用，并提供查看、确认、恢复、修订或取消原 Task 的入口。
- UI 中目标、输入和处理方式均已完整确定的生成图像、重新生成、重新编辑、添加内容、结构编辑、手工文本保存、候选采用/否决、明确 Run 取消/重试等操作，直接调用类型化领域 API。
- 对象级生成和重新编辑默认形成新 candidate，不自动覆盖 adopted；按钮可用性由服务端 ObjectReadiness 与 allowedActions 决定。
- 两类入口共享 ProjectGraph、版本资源、candidate、adopted 指针、专业 Tool、Activity 和积分账本。
- 服务端状态由 TanStack Query 获取；Zustand 只保存本地焦点、面板开关等 UI 状态。

### generationMode 与确认

- `AigcProject.generationMode` 只允许 `manual | auto`，默认 manual，是项目协作模式唯一真理源。
- manual 下，任何新的执行范围在首次副作用前必须由用户发起或确认。
- auto 下，Assistant 可在已确认的项目目标、交付范围、预算上限、质量门和普通动作范围内连续推进。
- auto 不得扩大对象数量、OPTIONAL 范围或交付物，不得绕过权限、预算、内容安全、强制确认、采用、审核、发布、完成和归档门。
- 模式切换使用专用类型化命令，只包含 projectId、mode、expectedProjectVersion 和 idempotencyKey。
- 模式切换只影响尚未提交的新动作；已提交 Run 按创建时快照完成。活跃 Task 只能通过显式 Task 控制操作暂停、修订、恢复或取消。
- 用户本次明确指定的目标和范围优先于项目默认模式。

### canonical TaskPlan 与 readiness

- 只读问答可使用直接 Execution；写入、后台生成、结构化澄清或多步骤目标在首次副作用前进入 canonical Task。
- TaskPlan 只表达 Assistant 工作步骤及其依赖，不为每个 ProjectObject、AigcTask 或专业 Run 创建镜像节点。
- 少量同能力强顺序步骤由一个 ExecutorPlan 处理；独立工作包、跨模态能力或不同 Skill 才拆分 TaskNode。
- 有效并行度取 TaskPlan 上限、项目策略、预算窗口、供应商限流和 ready 工作包数量的最小值，且保持既有 1..8 合同。
- BlueprintSnapshot 定义对象依赖并物化为对象级依赖；服务端根据当前图谱、actionKey、版本、活动 Run、权限、策略和本次范围派生 ObjectReadiness。
- ObjectReadiness 至少区分：`SATISFIED`、`READY`、`BLOCKED_BY_DEPENDENCY`、`BLOCKED_BY_POLICY`、`ACTIVE_RUN`、`PENDING_CANDIDATE`、`AWAITING_CONFIRMATION`。
- 下游输入优先使用 adopted 版本；同一已授权 TaskPlan 中唯一 candidate 可作为中间输入，但不自动 adopted；多候选必须暂停选择。
- OPTIONAL 对象只在明确确认范围或已授权 TaskPlan 范围内生成；EXCLUDED 对象不进入范围。
- active Run 或 pending candidate 默认不重复提交；已有 adopted 版本只有在用户明确要求新候选时才重新生成。
- 每次专业调用必须冻结实际 inputVersionIds、目标、完成标准和授权证据；运行中图变化只影响后续新计划。

### 结果、候选与完成合同

完成语义分层如下：

| 层次 | 完成条件 |
|---|---|
| Tool 调用 | 副作用已幂等持久化并返回稳定引用 |
| 对象步骤 | outputContract 对应的权威资源和 candidate 已持久化 |
| Assistant Task | 冻结的 CompletionCriteria 与失败策略已满足 |
| Project 交付 | 必需对象 adopted、manifest 与 evidenceHash 冻结，审核和发布策略满足 |

outputContract 对应的最小证据：

| outputContract | 完成证据 |
|---|---|
| INLINE_OBJECT_VERSION | 仅结构化 JSON 的 ObjectVersion candidate |
| DOCUMENT_VERSION | 自由文本 DocumentVersion + ObjectVersion candidate |
| MEDIA_VERSION | MediaVersion + ObjectVersion/ref |
| COMPOSITE_MANIFEST | manifest ObjectVersion + evidenceHash |
| REFERENCE_ONLY | 已校验的类型化资源引用 |

- CompletionCriteria 在调用时冻结；Run 成功但预期资源尚未持久化时，对象步骤不得完成。
- 默认只形成 candidate，不自动替换 adopted。采用、替换和否决必须保留版本、原因、操作者和并发检查。
- manifest 必须引用精确 ObjectVersion，并可追溯 DocumentVersion/MediaVersion；adopted 变化后历史 Review/Publication 保持快照或明确 stale。

### 多模态范围

- 底层合同覆盖文本、文档、图像、音频和视频。
- AAF-113 必须真实跑通：
  - 文本：Assistant 生成并登记 DocumentVersion 与 ObjectVersion candidate；
  - 结构化数据：登记不含自由文本的 INLINE_OBJECT_VERSION candidate；
  - 文档：DocumentVersion 与 ObjectVersion candidate；
  - 图像：专业 Tool 生成 MediaVersion 与 ObjectVersion/ref。
- 音频和视频在现有专业 Tool 可用时接入同一异步链；若 Tool 尚不可用，仅后置适配器，不改变 ProjectGraph、TaskPlan、candidate 或完成合同。
- 消息只保存稳定 project/object/run/artifact/version 引用和安全摘要，不复制正文、二进制、图谱或 Run 状态。

### 持久外部等待与恢复

- 专业 Tool 返回 executionRunId 只表示 durable accepted。用户目标要求资源结果时，TaskNode 和 Execution 必须进入 `AWAITING_EXTERNAL`。
- InvocationReceipt 保存 projectId、Task/Execution/toolCall、executionRunIds、期望对象和冻结 CompletionCriteria。
- 进入外部等待后结束当前 Dispatch，释放 Agent 线程和 lease，不通过 sleep、长轮询或 AG-UI/SSE 长连接等待。
- 专业 terminal/candidate 内部事件触发快速恢复；后台扫描补偿事件丢失、服务重启和消费者中断。
- 恢复必须校验 current plan/revision/execution/fence，并查询权威 Run 与版本资源；迟到或重复事件不得复活旧计划或重放 Tool 副作用。
- SSE 只通知 UI 重新查询，不直接推进 Task。

### 实际积分与监控

- 积分账本是实际消费唯一真理源；Binding 预估值只用于执行前提示，reservation 只表示预留。
- 必须区分 `estimated`、`reserved`、`settled` 和 `refunded`；用户实际成本按 settled 扣除 refunded 后展示。
- `costUsed` 只作为可从账本重建的投影，不得成为独立消费真理源。
- 必须建立 `project → Task → toolCall → Run/AigcTask → candidate → creditTx` 的可追踪关联链。
- ProjectProgressSnapshot 必须展示 TaskPlan、readiness、Run、candidate、待确认事项、失败重试和结算后实际成本。

### 旧入口退役

- 新 Assistant TaskPlan 路径达到上线门禁后，UI、Assistant Tool 和动作权限一次性切换到新入口，并停止接受旧批量生成请求。
- 已受理的在途 root/child 执行必须选择完成或取消，并结清 reservation、settled 和 refunded 交易。
- 删除旧批量生成的服务特判、seed、binding、调用方和测试，不保留长期双路径、fallback 或兼容适配器。
- 历史执行引用仅保留只读审计，不转换或复制为 TaskPlan。

### 项目 AI 定时任务

- 首期只支持一次性 `RUN_ONCE` 时间点，不向用户暴露 cron、固定频率或周期规则。
- 一条 `ProjectSchedule` 对应一个时间点。用户一次提交多个时间点时，系统在同一命令中创建多条独立记录；每条记录可单独暂停、恢复、编辑未触发内容或取消。
- 创建命令只接收 `projectId`、`prompt`、一个或多个 `executeAt`、IANA `timezone`、绑定的 `assistantId/conversationId`、`expectedProjectVersion` 和 `idempotencyKey`。服务端从当前身份写入 owner/org/workspace，不接受客户端伪造 scope。
- 服务端以 `Instant` 保存 executeAt，并保留 IANA timezone 用于原始意图和界面展示。过去时间、无效时区、空提示词和无项目权限必须拒绝。
- schedule 状态只表达 `SCHEDULED | PAUSED | DEFERRED | DISPATCHING | TRIGGERED | FAILED | CANCELED`；触发后保存 canonical `linkedTaskId`，Task 的规划、执行、等待确认、外部等待、完成和失败仍由 canonical Task 真理源表达。
- 用户可在 `SCHEDULED/PAUSED/DEFERRED` 状态编辑提示词或未来时间并使用版本 CAS；`TRIGGERED/FAILED/CANCELED` 保留只读审计。删除语义为取消，不物理删除已产生的 Task 或执行记录。
- 统一 scheduler 只运行一个 due scanner。scanner 原子认领 `executeAt <= now` 的记录并以 `scheduleId + executeAt` 创建幂等 Assistant Task；服务重启后仍对到期未触发记录执行一次。
- 到点时若同项目已有未终结 current Task，schedule 进入 `DEFERRED`，并在项目空闲后自动重试；首期不允许并行创建第二 Task，也不提供 SKIP/REPLACE 策略。
- 触发前重新读取并校验 schedule 归属者权限、项目 lifecycle、当前 projectVersion/graphRevision、current Task、Assistant 可执行性、预算、内容安全和强制确认策略。schedule 不保存或信任创建时 inputVersionIds、ObjectReadiness 或 allowedActions。
- 触发成功后，Assistant 以原 prompt 作为结构化 objective，在绑定的项目对话中显示“定时触发”来源和稳定 Task 引用，并复用 canonical TaskPlan、专业 Tool、candidate、积分、AWAITING_EXTERNAL 与恢复链。
- 定时触发不授予额外权限，也不绕过确认。提示词后续需要采用、送审、发布、完成、归档或其他强制确认时，Task 进入现有 `AWAITING_CONFIRMATION`，等待用户在项目页或对话中处理。
- 项目页 schedule 列表与 Assistant 对话查询同一 `ProjectSchedule + linkedTaskId`。SSE 只使 schedule、ProjectProgressSnapshot 和 Task Query 失效重取，不保存第二份状态。
- 首期不支持定时调用类型化 Project API、手动立即执行、Assistant 通过 Tool 创建/修改 schedule、节假日日历或 recurrence。

### 数据概念登记

| 产品概念 | 数据实体/资源 | 说明 |
|---|---|---|
| AIGC 项目 | AigcProject | 项目身份、generationMode 和粗粒度治理状态 |
| 项目结构基线 | AigcProjectConfigSnapshot | 两种来源共享的不可变 BlueprintSnapshot |
| 项目修订 | ProjectRevision | 记录基线之后的项目变更 |
| 项目对象 | AigcProjectObject | AIGC 内容对象的稳定身份、层级和采用指针 |
| 项目关系 | AigcProjectRelation | 非层级图关系及 canonical layer |
| 对象内容版本 | AigcObjectVersion | candidate 治理、结构化内容和精确资源引用事实 |
| 文档版本 | DocumentVersion | 所有自由文本的权威不可变内容版本 |
| 媒体版本 | MediaVersion | 图像、音频和视频权威资源版本 |
| Assistant 工作 | Task/TaskPlan/TaskNode/Execution | 目标、智能计划、步骤、控制和等待 |
| 专业执行 | ExecutionRun/AigcTask | 专业动作、供应商执行、重试、终态和退款 |
| 外部等待凭据 | InvocationReceipt | Tool 副作用引用和冻结完成标准 |
| 交付快照 | manifest ObjectVersion | 精确版本集合与 evidenceHash |
| 实际积分 | CreditTransaction | 预留、结算和退款账本事实 |
| 项目 AI 定时任务 | ProjectSchedule | 一个一次性时间点、提示词、触发状态和 `linkedTaskId` |

### 约束与边界

- 所有项目读写必须按 org/workspace/user/project 权限隔离；客户端不得提供可越权的 workspace、binding 或内部 scope。
- 所有写操作重新校验 projectVersion、graphRevision、lifecycle、generationMode、预算、权限、adoptedVersionId 和 actionKey。
- 同 idempotencyKey 同参数返回原结果；同 key 不同参数返回冲突。
- 不新增项目专属 Assistant、第二文本生成链、项目任务表、万能写 Tool、通用 Project 数据表或持久化 phase。
- 不把生成 Task 完成等同于 Project 交付完成。
- ProjectSchedule 不保存 TaskPlan、readiness 或 Tool 输入；到点后只通过现有 Assistant Task API 触发。
- scheduleId 与 executeAt 共同形成稳定触发幂等键；同项目未终结 current Task 时只能延后。
- 不在 AAF-113 扩建复杂预算、阶段预算、完整阶段引擎、模板市场或自动发布治理。

## 验收标准

```gherkin
Scenario: AC-01 目录来源形成不可变项目基线
Given 用户选择已发布的目录 Blueprint 并提交项目配置
When 系统创建项目
Then 系统将解析结果写入 AigcProjectConfigSnapshot
And 快照包含 sourceType、sourceRef、projectSpecification、objectSpecs、relationSpecs、outputContracts 和 snapshotHash
And ProjectGraph 在同一创建流程中原子物化
```

```gherkin
Scenario: AC-02 Assistant 根据项目需求生成结构基线
Given 当前没有适用的目录 Blueprint
And Assistant 已澄清项目目标、交付物、结构和依赖
When 用户确认结构提案或存在覆盖该范围的明确结构自主授权
Then 系统校验并持久化 ASSISTANT_GENERATED BlueprintSnapshot
And 它与目录来源使用同一 ProjectGraph、readiness、TaskPlan、candidate 和交付链
And 未注册语义或越权动作被拒绝
```

```gherkin
Scenario: AC-03 快照修订和 Brief 保持单一真理源
Given 项目已有首次 BlueprintSnapshot 和项目级 Brief 文档选定版本
When 用户修改项目结构或生成新的 Brief 候选版本
Then 系统记录 ProjectRevision 或项目文档决策记录
And 结构合同变化时创建新快照版本而不覆盖旧快照
And Brief 正文只写入 DocumentVersion
And 只有用户采用后才更新 Brief DocumentRef.selectedDocumentVersionId
And AigcProject 不保存 Brief 正文或摘要副本
```

```gherkin
Scenario: AC-04 ProjectGraph 层级、关系和制品语义一致
Given BlueprintSnapshot 包含父子对象和多种 relationType
When 系统物化并查询 ProjectGraph
Then parentId 是层级唯一写源且 contains 为派生关系
And 系统按 relationType 计算并返回 canonical layer
And 关系方向为 source 上游到 target 下游
And 每个对象返回 semanticRole 和 outputContract
And ProjectObject 内容成熟度只可能是 EMPTY、CANDIDATE_AVAILABLE 或 ADOPTED
```

```gherkin
Scenario: AC-05 服务端 readiness 阻止重复和越序生成
Given 目标范围包含 adopted 对象、pending candidate、active Run、OPTIONAL 对象和依赖未满足对象
When Assistant 或 UI 查询 ObjectReadiness
Then adopted 对象默认 SATISFIED
And pending candidate 与 active Run 不重复提交
And 未纳入授权范围的 OPTIONAL 对象不生成
And 依赖未满足对象保持 BLOCKED_BY_DEPENDENCY
And 下游只有在取得已冻结的可用 inputVersionIds 后才能提交
```

```gherkin
Scenario: AC-06 manual 与 auto 共享 canonical TaskPlan
Given 项目存在多个需要智能创作或专业 Tool 的步骤
When manual 用户确认一个明确范围
Then Assistant 只在该范围内创建或推进 canonical TaskPlan
When auto 用户已确认目标、交付范围、预算上限和质量门
Then Assistant 可在该授权范围内连续推进 ready 步骤
And auto 不扩大范围或绕过强制确认、采用、审核和发布门
And 模式切换不修改活跃 Task 或已提交 Run
```

```gherkin
Scenario: AC-07 UI 与对话进入同一业务事实
Given 用户在项目页面操作同一项目
When 用户请求生成文案、组织图片 Prompt、跨对象继续生成或批量生成
Then UI 创建结构化 Assistant Execution 或 Task
And 同项目 current Task 未终结时批量生成按钮禁用并显示可见原因
When 用户生成明确图像、重新编辑、添加内容、保存手工文本、采用明确候选或取消明确 Run
Then UI 直接调用类型化领域 API
And 新生成或编辑结果形成 candidate 而不自动替换 adopted
And 两类入口查询到同一 ProjectGraph、candidate、Run 和积分事实
```

```gherkin
Scenario: AC-08 结构化数据、文本和图像按输出合同完成
Given TaskPlan 分别要求 INLINE_OBJECT_VERSION、DOCUMENT_VERSION 和 MEDIA_VERSION 结果
When Assistant 完成结构化数据与文本且专业 Tool 完成图像资源
Then 结构化数据步骤持久化不含自由文本的 ObjectVersion candidate
And 文本步骤持久化 DocumentVersion 与 ObjectVersion candidate
And 图像步骤持久化 MediaVersion 与 ObjectVersion 或类型化引用
And 任一预期资源未持久化时对应步骤不得完成
```

```gherkin
Scenario: AC-09 音频和视频沿用统一多模态合同
Given 当前环境存在可用的音频或视频专业 Tool
When Assistant 在授权范围内调用该 Tool
Then 结果通过 MEDIA_VERSION 合同进入同一等待、candidate 和积分链
Given 当前环境不存在对应专业 Tool
When 用户请求该媒体类型
Then 系统返回能力不可用的稳定结果
And 不创建另一套项目模型或完成状态
```

```gherkin
Scenario: AC-10 durable accepted 后持久等待并恢复
Given 专业 Tool 已返回 executionRunId
And 用户目标要求取得最终资源
When 资源仍未持久化
Then InvocationReceipt 保存冻结 CompletionCriteria
And TaskNode 与 Execution 进入 AWAITING_EXTERNAL
And 当前 Dispatch 结束并释放执行资源
When terminal 事件到达或恢复扫描发现结果
Then 系统校验 current plan、execution 和 fence
And 查询权威 Run 与资源后恢复同一节点
And 断线、重复事件或服务重启不会重放旧 Tool 副作用
```

```gherkin
Scenario: AC-11 候选输入与正式采用保持分离
Given 同一已授权 TaskPlan 的上游对象形成唯一 candidate
When 下游步骤需要该版本
Then 系统可以冻结该 candidate 作为 inputVersionId
And adoptedVersionId 不自动变化
Given 上游存在多个候选
When 下游准备执行
Then ObjectReadiness 要求用户或 Assistant 暂停并完成候选选择
```

```gherkin
Scenario: AC-12 项目语义按权限和版本受控读取
Given 项目上下文包含 semanticCatalogVersion
When Assistant 请求当前步骤需要的低频语义定义
Then inspect_project_semantics 只返回服务端绑定 scope 内已发布的定义
And Tool 可见性取 SkillVersion、Assistant policy、Role、项目上下文和用户权限交集
And 未知、废弃或版本不一致的值返回稳定错误而不允许猜测
```

```gherkin
Scenario: AC-13 项目进度展示实际积分
Given Assistant 文本执行和专业媒体执行已产生积分交易
When 用户打开项目进度
Then ProjectProgressSnapshot 展示 TaskPlan、readiness、Run、candidate 和待确认事项
And 实际成本来自 settled 减 refunded 的账本交易
And estimated、reserved、settled、refunded 分别展示
And costUsed 可由账本重建且不作为独立真理源
```

```gherkin
Scenario: AC-14 旧批量入口一次性退役
Given 新 Assistant TaskPlan 路径已通过上线门禁
When 系统切换 UI、Tool 和动作权限入口
Then 新请求只能进入新路径
And 在途旧执行被完成或取消并结清预留、结算和退款
And 旧服务特判、seed、binding、调用方和测试被删除
And 系统不存在长期双路径或 fallback
```

```gherkin
Scenario: AC-15 精确版本形成可重现交付证据
Given 所有必需对象已有 adopted 版本且验证通过
When 用户冻结交付 manifest
Then manifest 引用精确 ObjectVersion 及其 DocumentVersion 或 MediaVersion
And graphRevision、includedObjectIds、验证结果和 evidenceHash 被冻结
And 后续 adopted 变化不会改写历史审核或发布事实
```

```gherkin
Scenario: AC-16 一次性时间点触发现有 Assistant Task 并共享状态
Given 用户在可写项目中设置同一提示词的一个或多个未来时间点和 IANA 时区
When 系统保存设置
Then 每个时间点形成一条独立 ProjectSchedule
And 用户可在触发前编辑、暂停、恢复或取消记录
When 某条记录到期且项目没有未终结 current Task
Then 统一 scheduler 以 scheduleId 和 executeAt 幂等创建一个 canonical Assistant Task
And Task 在绑定项目对话中标记为定时触发并使用用户保存的原提示词
And 项目页与对话显示同一 schedule、taskId 和 canonical Task 状态
Given 到点时项目已有未终结 current Task
When due scanner 处理该记录
Then schedule 进入 DEFERRED 且不创建竞争 Task
And 项目空闲后系统重新校验权限、项目版本、lifecycle、预算、确认和安全策略再触发
And 服务重启、重复扫描或网络重试不会创建第二个 Task
And 定时触发不会自动采用、送审、发布、完成或归档
```

## 测试要点

### developer 验证

- BlueprintSnapshot 两种来源的合同、哈希、不可变性与原子物化单元/集成测试；
- parentId、relationType→layer、semanticRole/outputContract 和 ProjectObject 成熟度测试；
- generationMode、ObjectReadiness、candidate 输入选择、OPTIONAL 范围和幂等冲突测试；
- 文本、文档、图像输出合同和 AWAITING_EXTERNAL 恢复测试；
- Tool 权限交集、scope 绑定、语义版本错误和积分关联测试；
- 前端入口路由、ProjectProgressSnapshot 和断线刷新测试；
- ProjectSchedule 多时间点创建、时区、CAS、due claim、幂等触发、忙时延后、重启补跑和绑定对话状态测试。

### tester 验收

- 每条 AC 至少有一个对应 `*AcceptanceTest.java`、`*.accept.test.ts(x)` 或 Playwright 场景；
- 验收报告必须提供 AC-01 至 AC-16 覆盖矩阵和证据；
- 重点覆盖权限隔离、并发版本冲突、重复事件、服务重启、部分失败、退款、旧入口不可达和定时 Task 不重复。

## 非目标

- 通用 Project、Goal、WorkItem、Evidence 等通用治理表的物理落地；
- 完整持久阶段引擎、复杂 Gate 和阶段预算；
- 任意 JSON 项目、万能项目对象或万能写 Tool；
- 可视化 Blueprint 编辑器、模板市场和自动将项目快照发布为模板；
- 自动采用、自动送审、自动发布、自动完成或自动归档；
- 在专业 Tool 缺失时自行实现新的音频或视频供应商适配器；
- 项目周期计划、任意 cron、定时确定性动作、手动立即执行和可配置并发策略。

## 相关设计

- [AAF-113 技术设计](design.md)
- [AAF-113 UI 设计](ui-design.md)
- [AAF-113 技术任务](tasks.md)
- [AAF-113 最终决策基线](aigc-project-capability-gaps.md)
- [AAF-113 Assistant Skill](skill.md)
- [AAF-112 项目先行需求](../AAF-112/requirement.md)
- [AAF-115 canonical Task 需求](../AAF-115/requirement.md)
- [Content Studio 产品设计](../../../design/apps/content-studio/content-studio-design.md)
- [Content Studio 技术设计](../../../design/apps/content-studio/content-studio-tech.md)

## 变更记录

| 日期 | 变更内容 | 原因 | 影响评估 |
|---|---|---|---|
| 2026-09-14 | 按 v2.0.0 最终决策基线重写需求 | 冻结 Assistant 主导、TaskPlan 单一编排、统一快照、制品图、完成合同和积分监控 | 替换此前全部需求描述；design、tasks 和验收测试必须按 AC-01 至 AC-15 对齐 |
| 2026-09-14 | 补充现有项目工作台的 UI 交互合同 | 明确局部微调、批量生成调用 Assistant 和 current Task 禁用规则 | design、ui-design、tasks 和 UI 验收测试同步到 v2.1.0 |
| 2026-09-14 | 增加一次性项目 AI 定时提示词 | 先复用现有 scheduler 与 canonical Assistant Task 实现最小闭环 | 新增 AC-16；design、ui-design 和 tasks 同步更新 |
| 2026-09-14 | 在文档开头增加 AIGC 项目业务总纲 | 先呈现有/无蓝图、界面/对话、局部/批量、多模态和交付等核心用户能力 | 不改变 AC；需求版本升级为 v2.2.1，design 同步引用 |

| 2026-09-17 | 采纳 Blueprint 物化最终决策 | 消除 `projectSpecification` 原始 Blueprint 副本、Brief 双真理源和已删除 DomainExtension 残留 | `projectSpecification` 改为类型化目标；Brief 固定为项目级 DocumentRef 选定版本；AC-03 与语义目录来源同步修正 |