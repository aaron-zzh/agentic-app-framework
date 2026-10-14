---
level: Practice
layer: Model
purpose: AAF-113 开发记录，按任务追加静态收口结果与依赖阻塞
status: active
version: 1.0.0
date: 2026-09-15
author: AaronZZH & Kiro
---

# AAF-113 开发记录

> 本轮全程按人类明确指令：不新增测试、不运行 test/lint/typecheck/check/build/acceptance，数据库只修改已有 SQL；两端 LSP（jdtls、typescript-language-server）均未安装，凡涉及 diagnostics 的结论均以人工符号交叉核实结果为准，工具返回的 "No diagnostics" 已标注为不可信证据。

## #11301～#11303 BlueprintSnapshot v2、ProjectGraph 与关系语义

✅ 2026-09-15 — developer-service（源码完成，动态门禁未跑）

- CATALOG BlueprintSnapshot 类型化 payload、canonical hash 与原子物化收口
- parentId 单一层级源，contains 仅查询派生
- relationType 语义目录驱动 layer/direction/acyclic/readiness
- 三态成熟度重建落回 AigcProjectObjectMaturityProjector
- 未实现 ASSISTANT_GENERATED 创建链，留给 #11321

## #11306、#11309、#11311、#11312 readiness、类型化媒体、进度积分与交付

✅ 2026-09-15 — developer-service（源码完成，动态门禁未跑）

- ObjectReadiness 七态判定与冻结输入版本收口
- 类型化媒体执行复用现有 AigcTask/ExecutionRun 链，未新建执行路径
- ProjectProgressSnapshot 贯通 Run→Task→candidate→creditTx，五项积分聚合
- manifest/evidenceHash 固定 ObjectVersion 引用，禁止原地漂移

## #11303、#11310、#11311 项目管理 UI 与类型化 API 接线

✅ 2026-09-15 — developer-webui（源码完成，动态门禁未跑）

- 新增 ProjectProgressPanel 分层展示 Task/Run/candidate/交付/积分
- 对象操作按 ObjectReadiness.allowedActions 门控，不自动替换 adopted
- NewProjectLauncher 阶段一仅走 CATALOG，SSE 只触发 Query invalidation
- 三态枚举、冻结输入引用等前端类型与后端 record 逐一交叉核实一致

> **问题**：两端 LSP 均未装（jdtls、typescript-language-server），"No diagnostics" 不可信。
> **沉淀**：已用符号定义/引用的人工交叉核实替代，覆盖字段名、方法签名、调用点参数类型；后续应尽快安装两端 LSP 减少人工核验成本。

## #11304、#11307 项目语义目录与 Assistant candidate Tool

✅ 2026-09-15 — developer-service（部分完成，动态门禁未跑）

- 编译 AigcProjectSemanticCatalog 与紧凑 ContentStudioProjectContextProvider
- ProjectArtifactCommandAdapter 直接复用阶段一 recordTextCandidate/recordDocumentCandidate
- InvocationContext 绑定 task/execution/dispatch/workspace 身份完整校验
- 未注册为 Resource Catalog Tool，未完成 Role/Skill 权限交集，不可对模型开放

## #11305、#11308、#11321 权限交集、外部等待与 Assistant Blueprint

🟡 2026-09-15 — developer-service（未完整完成，如实标记阻塞）

- #11305：Resource Catalog、Assistant policy、Role allowlist、SkillVersion 均未接线
- #11308：仅在 Execution/TaskPlan 新增 AWAITING_EXTERNAL 枚举值，未做持久化、receipt 存储、after-commit 信号与补偿扫描；已核实枚举扩展未引发无 default 穷举 switch 编译风险
- #11321：AigcBlueprintSourceType 已加入 ASSISTANT_GENERATED，但 AigcProjectMaterializer 仍只接受 CATALOG，创建链未接通
- 三者均需 AAF-115 canonical Task 状态机深度接入，本轮未强行重构

## #11321/#11322/#11314 助理对话协作前端

✅ 2026-09-15 — developer-webui（诚实受限收口，动态门禁未跑）

- 识别后端阶段二未闭环，未创建假交互、假数据或第二对话 UI
- 仅在 ProjectWorkbenchHeader 新增禁用态占位 Chip，标注依赖 #11305/#11308/#11321/#11322
- 前端类型与后端 ArtifactCompletionContract/AigcProjectSemanticCatalog 逐项核对一致

> **问题**：现象——后端阶段二权限交集与外部等待未闭环 → 根因：#11305/#11308 需要跨治理资源接线与 AAF-115 深度状态机改动，超出本轮授权范围 → 解法：前端仅预留标注依赖的禁用入口，不伪造可用性。

## 独立静态审查（architect）

🔴 2026-09-15 — architect（blocker=1、major=5，均未在本轮修复，需下一轮处理）

- **blocker**：`ProjectObjectStructureDialog.tsx` 是本轮未改动的遗留组件，提交 `appendObject` 时缺少 `#11301` 新增的必填字段 `expectedGraphRevision/semanticRole/plannedPhase/outputContract`；后端 `AigcProjectService.appendObject` 已对 `expectedGraphRevision` 做 `requireGraphRevision` 强校验，人工核实确认此路径会在实际运行时被拒绝。对象类型选项仍来自前端硬编码 `OBJECT_TYPE_CONFIG`，未读取当前快照白名单。
- **major-1**：`AigcProjectMaterializer` 将 Brief 正文直接写入 `AigcProject.brief`，未经过 DocumentVersion/ObjectVersion，违反 AC-03 正文唯一真理源。
- **major-2**：`evaluateReadiness` 冻结任意唯一 pending candidate 为下游输入，未限定"当前已授权 TaskPlan"范围，阶段一 `currentTask=null` 时存在冻结未 adopted candidate 的风险。
- **major-3**：`AigcProjectSemanticCatalogCompiler` 所有语义定义固定 `deprecated=false`，未接入真实发布/废弃判定；`ContentStudioProjectContextProvider` 未显式核对 InvocationContext user/tenant，fail-closed 尚不完整。
- **major-4**：`ProjectArtifactCommandAdapter` 复用阶段一 candidate 写入链正确，但未传递 Assistant provenance（记账仍为 `MANUAL_CANDIDATE`/`source=MANUAL`），Task/Execution/toolCall 与追踪链存在缺失。
- **major-5**：项目页同时读取 `AigcProjectProgress` 与旧 `AigcProjectSummary` 并混合回退，违反 ProjectProgressSnapshot 单一只读投影原则。
- architect 在审查中修复了自己新增文件 `DefaultContentStudioProjectContextProvider.java` 的 revision 一致性问题（跨 revision 混读窗口），属于本轮新增代码的内部完善，非既有代码回退。
- 未执行任何动态门禁（test/lint/typecheck/check/build/acceptance）；两端 LSP 仍不可信。

> **沉淀**：blocker 涉及遗留组件（`ProjectObjectStructureDialog.tsx`）与本轮新增后端合同不同步，超出本轮"收口阶段一/阶段二源码"任务范围，按"不做任务外重构"原则未擅自扩大修复，留给下一轮 developer-webui 任务处理。5 项 major 均记录在案，作为后续任务的输入。

## Major-1 补充核实：Brief 与文案版本化现状

✅ 2026-09-15 — 核实结论（标记后期优化，非本轮修复）

- 已核实：正式文案/文档对象（`outputContract=DOCUMENT_VERSION` 的 `AigcProjectObject`）**已经**通过 `recordTextCandidate`/`recordDocumentCandidate` → `recordManualCandidate` → `persistDocumentVersion` 统一走版本化路径，幂等、candidate/adopted 分离、并发版本冲突检测齐全；前端 `ObjectDetailPanel.tsx` 已接入。阶段二 `ProjectArtifactCommandAdapter` 直接复用这条既有链路，不存在双路径。
- 唯一未版本化的是 `AigcProject.brief`——它挂在项目实体本身而非独立 `AigcProjectObject`，走的是 `project.setBrief(...)` 直接覆盖字符串，无历史、无版本。
- **触发条件明确**：只要阶段二不新增"Assistant 编辑 Brief"功能，现状是安全的（人工 UI 直接读写字符串，内部自洽）。一旦要做"Assistant 写 Brief"，必须先完成 Brief 的 DocumentVersion 化，否则会出现人工路径（直接覆盖）与 Assistant 路径（若follow 既有模式会走 DocumentVersion）并存的双真理源问题。
- 当前 `ContentStudioProjectContextProvider` 只读 Brief（`specification.put("brief", project.brief())`），未实现写入，风险未触发。

## Major-3 补充修复：ProjectSpecification 未按 DEC-05 装配

✅ 2026-09-15 — 已修复

- 核实发现：`aigc-project-capability-gaps.md` DEC-05"项目上下文"明确要求 `ProjectContextSnapshot` 每次都装配 `ProjectSpecification`（渠道、生产模式、Blueprint 等结构化配置事实），但 `DefaultContentStudioProjectContextProvider.load()` 此前只从 `AigcProjectView` 拼了 `name/description/brief` 三个基础字段冒充 specification，从未读取真正持久化在 `AigcProjectConfigSnapshot.snapshot.projectSpecification()` 里的配置数据。
- 已修复：改为通过 `project.configurationSnapshotId()` 查询快照（复用 `AigcProjectService.readiness()/completion()` 已有的查询模式），从 `configSnapshot.getSnapshot().projectSpecification()` 取出真正的结构化配置（package/projectType/blueprint/domainExtension/channels/executionBindings/productionMode/budgetTier/qualityTier/slotOverrides），叠加 `briefSummary` 字段承载创作简报摘要。
- 撤回此前"projectSpecification 存档后无人读取"的结论——那是描述当轮实现缺口，不是设计意图；规范要求它必须被读取喂给 Assistant。

## 设计文档整理：结构与流程（design.md v2.4.0）

✅ 2026-09-15 — 讨论结论已落地文档，未改动源码

- 核实结论：AAF-113 采用的是自己的领域模型（BlueprintSnapshot/ProjectGraph/TaskPlan/candidate），不是 `general-project-management-model.md` 里的通用模型（Goal/Deliverable/WorkItem），两者独立，后者未被采纳整合，该文档自身也注明"是否调整属于后续领域集成决策"。
- 核实结论：TaskNode 已有真实的 `dependsOn`（DAG 依赖）和 `AWAITING_CLARIFICATION`/`ClarificationRequest`（结构化确认）机制（AAF-115 已实现），"确认门"不需要新造概念，是硬机制；`skill.md` 的 START/DESIGN/BUILD/DELIVER/RUN 坐标是软指导，明确"不写入 lifecycle"，不是持久状态机——这个软硬分工不需要调整，本次只是把"具体怎么拆 TaskNode"细化进 design.md。
- design.md 补充两处：①"Brief 真理源"章节标注当前实现差距（`AigcProjectMaterializer` 仍直写字段，未按既有设计的 BriefContentService 顺序创建 Brief 对象首条 DocumentVersion）并明确复用 `objectType=brief` 槽位的存在性判断规则（Blueprint 有配置该槽位才能接管，否则字段继续作为唯一存储）；②"TaskPlan 生成规则"后新增"ASSISTANT_GENERATED 创建流程的 TaskNode 结构"，用 `dependsOn`+`AWAITING_CLARIFICATION` 描述两个确认节点（规范+简报草案 → 对象树草案），明确是当次 Task 内部临时步骤、不残留状态、不违反"不新增持久化阶段引擎"非目标。
- 同步更新 DEC-04"能力维度与来源、模态正交"在 design.md 里的落地引用——CATALOG 项目物化后同样可用文本/文档 candidate Tool 迭代 Brief，不需要走 ASSISTANT_GENERATED 才有 AI 生成能力。
- 未改动任何源码；已识别的实现差距（Brief 版本化、ASSISTANT_GENERATED 创建流程）留给对应任务号（`#11302`/`#11307`/`#11321`）后续实现时参照本次设计。

## design.md 与 aigc-project-capability-gaps.md 一致性核对（v2.4.0 → 补丁）

✅ 2026-09-15 — 已修正 3 处不一致，未新增决策

- **不一致 1**：`ObjectReadiness` 判定顺序里"同一 current TaskPlan 中唯一有效 candidate 次之"的表述，此前本轮已把源码 `evaluateReadiness()` 修复为阶段一不允许唯一 candidate 自动冻结（major-2），但 design.md 文字未同步。修正：保留 DEC-05 的最终目标规则（有 TaskPlan 时唯一 candidate 可用），补充说明阶段一因无 TaskPlan 该例外天然不成立，属于规则的自然结果，不是偏离——避免用阶段一细节覆盖掉目标架构描述。
- **不一致 2**：阶段一描述"不依赖 Assistant 生成"容易让人误读成 CATALOG 项目永久排斥 Assistant，与新写入 DEC-04 的"能力维度与来源、模态正交"矛盾。修正为"先不要求 canonical Task/TaskPlan 或 Assistant Tool 基础设施就位"，并显式引用 DEC-04 说明这些能力对 CATALOG 项目同样适用，只是要等 `#11304`~`#11308` 落地。
- **不一致 3**：`BriefContentService` 类名在正文和 AC-03 映射表两处出现，但机制细化部分已明确"复用现有 `AigcProjectDeliveryService.persistDocumentVersion`，不新建服务类"，前后自相矛盾。统一改为准确描述写入顺序，删除虚构类名。
- 核查确认阶段一/阶段二关于"无 Blueprint 创建""批量生成""auto 连续推进"仍属阶段二专属能力的描述不受影响——这些是结构来源和编排范围维度，跟"内容生成能力不分来源"是两个不同维度，不冲突，未改动。

## 代码与设计一致性核查 + 字段盘点补写（design.md v2.5.0）

✅ 2026-09-15 — 已发现问题并部分落地文档

- 核查当前工作区未提交代码与 design.md 的一致性，发现 4 处不一致（详见对话记录，未在本轮下结论修复）：①`AigcProjectBlueprintSnapshotPayload.projectSpecification` 是 `Map<String,Object>`，design.md 写的是类型化 `ProjectSpecificationSnapshot`；②`ArtifactCompletionContract.CompletionCriteria` 是单一 record，design.md 写的是含 `SubmissionAccepted`+`ArtifactSet` 的 sealed interface；③`ProjectArtifactCommandApi` 真实签名（返回 `AigcObjectVersionView`、命令类缺 `actionKey`/`criteria`、多一个 `InvocationContext` 参数）与 design.md 伪代码（返回 `CandidateRef`、命令类含 `actionKey`/`criteria`）有偏差；④`ContentStudioProjectContextProvider.load` 参数命名 design.md 写 `AssistantInvocationContext`，代码用已有的 `InvocationContext`，判断只是命名占位不算冲突。
- 深入核查 ①：发现 `projectSpecification.blueprint` 子对象与 payload 顶层已解析的 `slotTemplates`/`relationSpecs`/`actions`/`deliverableSets`/`processPolicy` 是同一份 Blueprint 事实的重复存储（真冗余）；`package`/`domainExtension`/`projectType.definitionVersion` 子对象全仓搜索确认当前无代码读取（死数据）；`channels`/`productionMode`/`budgetTier`/`qualityTier`/`slotOverrides`/`executionBindings` 是真正必要且非冗余的字段。
- **遗漏修正**：上一轮按阶段的完整字段盘点（初始配置/物化解析/对象节点生成/交付物生成）只呈现在对话中，未落到文档，用户指出后已补写进 design.md 新增"字段来源与阶段总览"章节，包含冗余与死数据发现的书面记录。
- 本次只记录发现，均未执行代码修复；不一致项 1-3 和冗余/死数据清理方案待专项讨论确认后再实施。

## 设计文档开篇总纲（design.md v2.6.0）

✅ 2026-09-15 — 已落地

- 在"设计基线"之后新增两个总纲章节，放在"设计目标"之前，让读者先建立整体框架再看细节：
  - "数据结构总览"：只讲聚合关系不含具体字段，三条主线（内容树 Project→Snapshot→Object→Version；智能计划 Task→TaskPlan→Execution；交付证据 manifest→Review→Publication）及其独立性，依据 `aigc-project-capability-gaps.md` DEC-01/DEC-03/DEC-07。
  - "阶段流程总览"：直接引用决策基线"最终黄金路径"原文，再展开为四个粗粒度阶段（初始配置/规范与结构生成/交付物生成/交付与观测）的目标、产出方、是否所有项目都经过；并重申 `tasks.md` 阶段一/二/三是工程搭建顺序不是产品体验分叉（呼应 DEC-04 正交原则）。
  - 两章节均标注不新增持久化 phase 字段，只是叙述划分，不违反 DEC-03 非目标。


## 任务复杂度判断与依赖表达——两层提示词优化（2026-09-15）

✅ 2026-09-15 — Kiro

- 优化底层通用 Skill `builtin-task-decomposition`（`v12__init_seed_data.sql`，修改既有 seed，未新增文件）：在"需要多个执行者协作"段补充依赖判断原则——只有真实产出-输入依赖存在时才声明先后关系，禁止凭内容类型或常见创作顺序臆断（如"文本先于配图"）。不引入 `dependsOn` 字段名，判断方法与 Tool 参数机制解耦。
- 补充 AAF-113 `skill.md`"canonical TaskPlan"段：任务复杂度判断映射到项目场景（同对象创作/修改走内部步骤；跨对象独立工作可拆多 TaskNode；跨模态/不同专业能力必须拆多 TaskNode）；依赖判断落到本项目权威数据源——只有目标对象 readiness 为 `BLOCKED_BY_DEPENDENCY` 且依赖对象是本次计划内另一 TaskNode 产出目标时才声明依赖，不确定时先查 readiness 不臆断。
- 讨论过程中发现真实代码缺口并已记录（未修复，非 AAF-113 范围）：`SubmitCoordinationPlanTool.inputSchema()` 的 `executors.items` 未用 `properties` 结构化声明 `dependsOn`/`inputBindings`/`maxAttempts`，模型函数调用时无信号可用，导致已实现的 DAG 依赖机制（`#11505`）实际不可达（executor 默认全部只依赖协调者即全部并行）。已记录到 [AAF-115/tasks.md](../AAF-115/tasks.md) 新增任务章节供协调者评估，并在本文档 `tasks.md` 外部阻塞段落追加引用。


## #11313 静态物化复核与 #11328 启动

⏳ 2026-09-18 — developer-service

- Compiler 成为 Blueprint JSONB 唯一读取边界
- 发布拒绝非法父级、关系、动作与交付定义
- 运行期只消费类型化 Definition，关系元数据不复制模板规则
- TaskNode/Proposal/PromptEnvelope 冻结指令、范围、hash 与服务端目标
- 未新增测试，未运行任何动态验证；#11313 保持 ⏳

## #11332 schedule 与 Proposal 事件发布点

✅ 2026-10-14 — developer-service（源码完成，未编译、未运行测试、未新增 SQL）

- `AigcEventPublisher` 新增 `publish(userId, type, refs, payload)` 重载，供自定义关联键使用
- `aigc.project.schedule.changed`：`AigcProjectScheduleService` 覆盖创建、取消、暂停、恢复、触发、延后、失败；refs 为 projectId、scheduleId；触发与失败发生在调度线程，无组织上下文，事件为用户级
- `aigc.proposal.changed`：生成 Proposal 与 Assistant Blueprint Proposal 两个服务共用事件类型，用 payload 的 `kind` 区分；字符串 proposalId 放 payload，refs 仅在已知时带 projectId
- 生成 Proposal approve 的过期分支抛异常回滚，不发布过期事件；schedule 暂无编辑方法，编辑事件待能力落地后补
- canonical Task 状态事件拆为 #11333，不加 `aigc.` 前缀（见下一节）

## #11333 canonical Task 状态事件

✅ 2026-10-14 — developer-service + developer-webui（源码完成，未编译、未运行测试、未新增 SQL）

- 后端：`TaskStatusEventPublisher` 经 `SynchronousExecutionEventWriter` 的单一落库点接入，AAF-115 的各 Task 状态写入路径无需逐个改；`SynchronousExecutionEventWriter` 构造器新增该依赖，自动配置同步接线
- 事件 `task.status.changed`：ID 类字段全放 payload（字符串），接收用户取事件 `userId`
- 前端：`useTaskList` 订阅 `task.` 前缀并按 conversationId 失效；轮询放宽为 3 秒与 10 秒兜底
- 风险：改动位于 AAF-115 的事件落库热点（框架层），所有执行事件落库都会经过过滤判断，且命中状态类事件时多一次 `sys_event` 写入，建议动态验证时观察落库耗时
