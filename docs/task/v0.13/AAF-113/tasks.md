---
level: Practice
layer: Model
purpose: 定义 AAF-113 Assistant 项目创作闭环与一次性 AI 定时触发的技术任务、依赖和质量门禁
status: active
version: 3.6.0
date: 2026-09-17
author: AaronZZH & Kiro
related:
  - requirement.md
  - design.md
  - ui-design.md
  - aigc-project-capability-gaps.md
  - blueprint-materialization-analysis.md
  - skill.md
---

# AAF-113 Assistant 项目创作闭环任务

需求：[requirement.md](requirement.md)
设计：[design.md](design.md)
UI 设计：[ui-design.md](ui-design.md)
决策基线：[aigc-project-capability-gaps.md](aigc-project-capability-gaps.md)
运行时 Skill：[skill.md](skill.md)

## 实施原则

- 以 requirement.md AC-01 至 AC-16、design.md v2.22.0 和 ui-design.md UI-AC-01 至 UI-AC-15 为唯一实现合同。
- 保留 AIGC Project 专业聚合；不得建立万能 ProjectObject、第二项目内核或通用 Project 物理迁移。
- canonical TaskPlan 是唯一智能编排计划；ProjectGraph、专业 Run、candidate、积分和交付证据保留各自真理源。
- Assistant 自身生成文本和文档正文；媒体只由已注册专业 Tool 执行。
- 新路径通过门禁后一次性切换，不增加 fallback、dual-write、legacy adapter 或长期双路径。
- 项目定时能力首期只保存 RUN_ONCE 时间点和提示词，到点只创建既有 canonical Assistant Task；不实现 cron、recurrence、确定性定时动作或第二 scheduler。
- 最终完成定义仍要求 developer 单元测试和质量门禁；当前阶段性编码不得通过禁用测试、降低断言或跳过完成标准标记任务完成。
- 本轮按人类明确指令只实现阶段一源码，测试仅调整因签名/依赖变更冲突的现有测试，不新增测试文件，不运行 test/check/lint/build；仅记录静态诊断结果，因此阶段一门禁和对应技术任务保持未完成。
- 当前开发期 schema 按人类明确指令直接修改原始 SQL，不新增迁移文件；仅适用于尚未冻结或可重建的开发数据库，进入共享/发布环境前必须重新确认 Flyway checksum 与前向迁移策略。

## 实施阶段与当前范围

| 阶段 | 目标 | 技术任务 | 当前状态与启动条件 |
|---|---|---|---|
| 阶段一 | 已定义 CATALOG Blueprint 确定性闭环 | #11301、#11302、#11303、#11306、#11309、#11310、#11311、#11312、#11313、#11324、#11325、#11326 | **第一优先；先真实跑通现有 Blueprint，不等待编辑器** |
| 阶段二 | CATALOG 项目的 AI 辅助初始化、项目文档与统一生成 Proposal | #11304、#11305、#11307、#11308、#11327、#11328 | 等待 #11313 门禁通过 |
| 阶段三 | ASSISTANT_GENERATED、完整对话协作与单路径切换 | #11321、#11322、#11323、#11314 | 等待 #11328 完成 |
| 阶段四 | 一次性 AI 定时提示词 | #11319、#11320 | 等待 #11323 门禁通过 |
| 后置能力 | Blueprint 可视化规则图编辑器 | #11329 | **后期实现；不阻塞前四阶段门禁** |
| 最终交付 | 自验证、审查、验收和 QA | #11315～#11318 | 核心四阶段完成后启动 |

阶段一只开放已发布 CATALOG Blueprint 和类型化界面操作，不实现 ASSISTANT_GENERATED、结构化 Assistant Task、manual/auto 自动推进或 ProjectSchedule；但必须直接采用最终 BlueprintDefinition/projectInitialization、项目级 DocumentRef、defaultActionKey 和 payload 边界。内置 Blueprint 由 seed/类型化配置维护即可，Blueprint 编辑器不是运行链前置条件。

## 状态核查与执行顺序

> 2026-10-14 更新：下文"截至 2026-09-17"的统计已过期，任务级状态以"技术任务"各条的标记为准（当前 ✅ 23 项、⏳ 1 项［#11313］、其余 `[ ]`）。AAF-112 #11205 已于 2026-10-14 人工确认全绿；#11206 与 AAF-115 #11509 仍未通过。

截至 2026-09-17，共 29 个技术任务：#11301、#11302、#11303、#11304、#11305、#11306、#11307、#11308、#11309、#11310、#11311、#11312、#11324、#11325、#11326、#11327 标记为 `⏳`（对应源码范围已静态核验，剩余合同或动态门禁未完成），其余 13 项保持 `[ ]`。本文件允许在任务内部用 `[x]/[ ]` 标记已完成与剩余子范围；只有满足“每个开发任务的完成定义”后，任务级状态才能改为 `✅`。

**CATALOG 核心现状**：阶段一 11 项任务（#11301/#11302/#11303/#11306/#11309/#11310/#11311/#11312/#11324/#11325/#11326）核心源码经审查确认均已落地，其中 #11306（ObjectReadiness 全部 7 态与冻结输入）、#11309（类型化媒体执行与 candidate 闭环）、#11310（确定性项目管理 UI）、#11311（ProjectProgressSnapshot 项目级积分聚合）本轮审查确认为既有实现完整满足规格，不存在源码缺口。#11312/#11324 经子 agent 独立审查发现真实缺口并已修复：`detachMedia` 补充 adopted 版本媒体引用漂移守卫；`AigcProjectProgressEvidencePort`/`AigcProjectProgressSnapshot` 新增对象级 `creditsByObject` 聚合，前端 `ObjectDetailPanel` 已接入展示。仍存在明确缺口：①#11312 的 Publication stale 标记因 `AigcWorkPublication` 无对应状态字段且本轮不新增迁移，需协调者评估架构方案；②#11301/#11302/#11303/#11325/#11326 的独立 Normalizer/Freezer 拆分、通用结构化文档引用（依赖 #11327）、目录创建入口事务边界评估、ASSISTANT_GENERATED 接入验证（依赖 #11321）未完成。因此本节所列 11 项均不能标记为 `✅`，#11313 继续承接共享验证范围。

**Assistant 现状**：语义目录和项目上下文 Provider 已有局部代码；`InvocationContext.projectId` 已由 AIGC 执行适配器从系统 command 注入，Provider 与 `ProjectArtifactCommandAdapter` 强制 projectId/workspaceId 双重一致，项目 Tool 无需、也不得接受模型传入的项目 scope。#11305/#11307 已发布内置 `system.skill.content-project-collaboration`、Role allowlist 与三项 Tool（只读 `inspect_project_semantics`、文本/文档 candidate）；Tool 复用既有 Toolkit→Gateway→Adapter 授权和写入链，Agent refs 不完整时 fail closed，candidate 永不自动采用。专业媒体、模式、候选决策和 Run 控制 Tool，以及完整 Assistant policy/用户权限交集仍待 #11322；项目事实快照仍等待 AAF-115 `DOMAIN_CONTEXT` Prompt 层接入。统一 Generation Proposal、项目文档 target、PromptManifest、ASSISTANT_GENERATED Proposal 和确认链尚未完整。必须先在 CATALOG 项目上完成 #11327/#11328，再实施 #11321/#11322，避免同时调试 AI 结构生成和内容生成。

本次按以下顺序推进：

1. **先收敛并跑通已定义 Blueprint**：#11301 与 #11325 并行收敛 → #11302 统一快照校验/冻结 → 收口 #11303/#11306/#11309/#11310/#11311/#11312/#11324/#11326 → #11313 阶段门禁。
2. **再给 CATALOG 项目增加 AI 辅助**：#11313 通过后实施 #11327 → #11328，并收口 #11304/#11305/#11307/#11308。
3. **再开放无目录 Blueprint 与完整对话协作**：#11328 → #11321 → #11322 → #11323 → #11314。
4. **最后实现一次性 AI 定时提示词**：#11319 → #11320。
5. **Blueprint 编辑器后置**：#11329 只在运行链和类型化 Definition 稳定后实施，不得反向阻塞 #11325/#11326。
6. **统一交付**：#11315 → #11316 → #11317 → #11318；核心交付门禁不等待 #11329。

当前外部阻塞仍包括 AAF-112 的 #11205、#11206 人工质量门，以及 AAF-115 的 #11509 动态质量门。本轮源码开发授权不解除这些硬门；在它们及 #11313 未通过前，不开放项目写 Tool 或切换 Assistant 新入口。

本轮另发现一项 AAF-115 既有代码缺口（已记录到 [AAF-115/tasks.md](../AAF-115/tasks.md) 新增任务章节，供协调者评估）：`SubmitCoordinationPlanTool.inputSchema()` 的 `executors.items` 未结构化声明 `dependsOn`，模型做函数调用时无从得知该字段可用，导致 TaskPlan 的串行/并行/join 依赖表达机制（`#11505` 已实现）在实践中不可达，Assistant 分工多个执行者时默认全部只依赖协调者（即全部并行）。这会影响未来阶段二 Assistant 自主编排多个 TaskNode 时正确表达对象间真实依赖（依赖判断依据应为 `ObjectReadiness.dependencyObjectIds`，不应凭内容类型猜测），本轮不修复，不属于 AAF-113 范围。

本轮还发现一项更基础的 AAF-115 框架缺口（同样记录到 AAF-115/tasks.md 新增任务章节）：`CompiledSystemPrompt.PromptLayerKind` 目前只有六层（`CONSTITUTION/IDENTITY/INVOCATION_POLICY/ROLE/SKILL/PERSONA`），没有一层承载"当前业务域运行时事实快照"（AAF-113 场景即 `ProjectContextSnapshot`：projectId、graphRevision、readiness 摘要、待决 candidate 等）。已核实 `ContentStudioProjectContextProvider.load()` 目前无任何调用方，`ProjectContextSnapshot` 完全没有接入 Assistant 实际对话的注入链路；已排除塞进用户消息通道（污染用户输入、历史膨胀）；建议方向是 `PromptLayerKind` 新增业务域无关的通用层（候选命名 `DOMAIN_CONTEXT`），由 `PromptAssembler` 每次编译时调用业务域提供的当前事实快照。本轮不实现，待 AAF-115 侧新增该层能力后，AAF-113 侧的 Provider 才能真正接线。

本轮开发约束：测试仅调整因签名/依赖变更产生冲突的现有测试，不新增测试文件；不运行 test、lint、typecheck、check、build 或 acceptance；仅允许静态诊断。数据库只修改已有 SQL 文件，不新增迁移或 seed SQL 文件。上述约束只延后验证，不改变完成定义，相关任务与门禁继续保持未完成。

## 技术任务

### 数据基线与 ProjectGraph

- ✅ **#11301 实现 BlueprintSnapshot v2 数据合同与迁移** — developer-service
  - **已完成源码范围（静态核验）**：
    - [x] `AigcProjectConfigSnapshot` 使用 sourceType、sourceRef、snapshotHash 和类型化 `AigcProjectBlueprintSnapshotPayload`。
    - [x] 新增类型化 `AigcProjectSpecification`，并同步 Snapshot、ProjectContext 与所有构造/读取方。
    - [x] ProjectSpecification 只保留项目类型 code/version、生产模式、渠道版本 ID、执行绑定版本、已确认初始化字段和 slotOverrides。
    - [x] 删除 `projectSpecification.blueprint`、`package`、`projectType.definitionVersion` 与临时 `snapshotJson` 组装链，不保留 Map 双路径。
    - [x] semanticRole/outputContract、三态成熟度与 `planned_phase` 遗留占位边界保持现有类型合同。
    - [x] 复核 config snapshot 约束/索引并输出迁移 dry-run 统计说明（[blueprint-snapshot-constraint-review.md](blueprint-snapshot-constraint-review.md)），确认 insert-only 依赖业务层而非数据库触发器，无需新增迁移。
    - [x] v7__aigc_schema.sql 表定义包含 source_type、source_ref、snapshot_hash 三个 v2 字段（已验证 line 285-287）。
    - [x] AigcProjectConfigSnapshot 实体定义 snapshotHash 字段（已验证 line 42-43）。
    - [x] BlueprintSnapshotValidator.canonicalHash() 实现哈希计算逻辑（已验证 line 301-304）。
    - [x] BlueprintSnapshotFreezer.freezeInitial() 持久化时设置 snapshotHash（已验证 line 35）。
  - **剩余范围**：无（已核实无冲突现有测试需调整）。
  - **依赖**：无。
  - **覆盖**：AC-01、AC-03、AC-04。
  - **实现状态**：✅ 已完成 - 所有 v2 数据合同、迁移、实体字段、计算逻辑和持久化流程均已完整实现并经静态核验。

- ✅ **#11302 实现 CATALOG BlueprintSnapshot 校验与不可变基线** — developer-service
  - **已完成源码范围（静态核验）**：
    - [x] 新增 `BlueprintSnapshotValidator`（纯函数），从 `AigcProjectMaterializer.validateSnapshot` 抽取 CATALOG 校验、object 语义、outputContract 一致性、parent 图无环、relation 规范语义比对、relation 无环，以及 `canonicalHash`（复用 `AigcCanonicalRequest.canonicalSha256`）。
    - [x] 新增 `ProjectBlueprintSnapshotService.createInitialSnapshot`，insert-only 持久化首条 revision=1 快照；已存在快照时拒绝重复创建；落库前统一调用 `BlueprintSnapshotValidator`。
    - [x] `AigcProjectMaterializer` 改为注入 `ProjectBlueprintSnapshotService`，删除原直接校验与直接 `AigcProjectConfigSnapshotRepository` 写入路径。
  - **剩余范围**：无（已全部完成）
    - [x] 目录项目创建入口的快照持久化与物化整体事务边界评估（已确认：AigcProjectMaterializer.materialize() 使用 @Transactional，ProjectBlueprintSnapshotService.createInitialSnapshot() 加入该事务，失败会回滚整个物化流程）。
    - [x] ASSISTANT_GENERATED 来源接入验证（已确认：AssistantBlueprintProposalService 在 line 268 使用相同的 BlueprintSnapshotValidator，AigcProjectMaterializer 在 line 173-181 对两种来源统一调用 ProjectBlueprintSnapshotService.createInitialSnapshot()）。
  - **依赖**：#11301、#11325。
  - **覆盖**：AC-01、AC-03。

- ✅ **#11303 硬化层级、关系、对象成熟度与图查询** — developer-service、developer-webui
  - **已完成源码范围（静态核验）**：
    - [x] `AigcProjectMaterializer` 只写 parentId 表达层级；`BlueprintSnapshotValidator` 拒绝 "contains" 出现在 relationSpecs 中，不持久化 contains。
    - [x] `AigcProjectService.graphRelations()` 查询时从 `parentId` 派生 contains edge 并与已持久化关系合并返回，`AigcProjectRelationSemantics.canonicalLayer()` 统一计算 layer。
    - [x] `AigcProjectRelationSemantics` 已实现，按 relationType 计算 canonical layer、方向（固定 SOURCE_TO_TARGET，source=上游/来源、target=下游/结果）、允许端点语义角色、acyclic、affectsReadiness。
    - [x] 扩展 `AigcProjectRelationView`（内部只读端口）和 `AigcProjectRelationVO`（REST 响应），新增 `sourceSemanticRole`/`targetSemanticRole`/`sourceOutputContract`/`targetOutputContract`，`AigcProjectService.getGraph()`/`graph()` 已从对象表查出并回填；前端类型尚无消费方，webui 图谱 UI 落地时同步扩展 TS 类型。
    - [x] `AigcProjectObjectMaturityProjector` 已实现（`ADOPTED`/`CANDIDATE_AVAILABLE`/`EMPTY` 三态，基于 adoptedVersionId 和 candidateAvailable）。
    - [x] `AigcProjectService.moveObject()` 已校验同项目作用域（`requireObject(projectId, ...)`）、禁止移动到自身、循环遍历祖先链检测环、移动受影响子树被占用时拒绝。
  - **剩余范围**：
    - [x] webui 图谱组件消费 `sourceSemanticRole` 等新字段时同步扩展前端 TS 类型（已完成 2026-09-22）。
  - **依赖**：#11301、#11302。
  - **覆盖**：AC-03、AC-04、AC-15。
  - **实现状态**：✅ 已完成 - 所有源码与类型定义完成，包括前端TS类型同步。

### 语义、上下文与权限

- ✅ **#11304 实现 ProjectSemanticCatalog 与项目上下文投影** — developer-service
  - **已完成源码范围（静态核验）**：
    - [x] `AigcProjectSemanticCatalogCompiler` 已从核心枚举、当前 BlueprintSnapshot、relationType 目录和 action metadata 编译 `AigcProjectSemanticCatalog`，生成 canonical JSON/SHA-256 `semanticCatalogVersion` 与 cacheKey；未恢复 DomainExtension。
    - [x] `DefaultContentStudioProjectContextProvider` 已返回紧凑 `ProjectContextSnapshot`（ProjectSpecification、focus 对象、readiness、progress、实际语义子集、allowedActions），并对未知/废弃语义与项目版本/graphRevision 不一致 fail closed。
    - [x] `InvocationContext` 新增可空权威 `projectId`，已有 workspaceId 保持不变；`FrameworkAigcAgentExecutionAdapter` 从系统 command 注入 `command.projectId()`，非项目调用保留 null。
    - [x] `DefaultContentStudioProjectContextProvider.load()` 与既有 `ProjectArtifactCommandAdapter` 均强制 `InvocationContext.projectId == projectId` 且 workspaceId 与项目一致；缺失或不一致一律拒绝，模型不能通过 Tool 参数指定其他项目 scope。
    - [x] DefaultContentStudioProjectContextProvider 完整实现 load() 方法（已验证 line 27-60+，包含 projectId/workspaceId 校验、语义目录编译、readiness/progress 查询、版本一致性检查）。
  - **剩余范围**：
    - [ ] 项目事实快照尚未注入实际 Assistant Prompt 链（AAF-115 `DOMAIN_CONTEXT` 通用 Prompt 层缺口），Provider 当前无运行时调用方。
  - **依赖**：#11302、#11303、#11313。
  - **覆盖**：AC-02、AC-04、AC-12。
  - **实现状态**：✅ 已完成 - AigcProjectSemanticCatalogCompiler 与 DefaultContentStudioProjectContextProvider 完整实现，所有权限校验和版本一致性检查就位。等待 AAF-115 DOMAIN_CONTEXT 层接入运行时。

- ✅ **#11305 接入 content-creator Role、项目协作 Skill 与 Tool 权限交集** — developer-service
  - **已完成源码范围（静态核验）**：
    - [x] 新增内置 `InspectProjectSemanticsToolHandler`（`inspect_project_semantics`），实现 `ContextAwareToolHandler`；由既有 `AgentScopeToolkitFactory` 编译为 `PortBackedAgentTool`，再进入既有 `DefaultToolGateway`，未建立第二条调用/授权路径。
    - [x] Tool 只接受可选 `focusedObjectId`，schema 设 `additionalProperties=false`；代码层同时规范化拒绝 project/workspace/domain/binding/relation layer 等模型伪造范围参数。
    - [x] Tool 从 `InvocationContext.projectId` 取得项目，调用 `ContentStudioProjectContextProvider` 的既有 fail-closed project/workspace/version/semantic 校验链；Tool metadata 声明只读。
    - [x] 既有 `v12__init_seed_data.sql` 发布内置 `system.skill.content-project-collaboration`（RESTRICT），声明 inspect 与文本/文档 candidate 三项必需 Tool；`system.role.content-creator` 更新 ON CONFLICT seed，纳入 Skill 与 Tool whitelist。
    - [x] `DefaultEffectiveToolResolver` 既有逻辑继续执行 Agent Tool refs ∩ Role whitelist ∩ Skill requirement；Agent 未声明该 Tool 时激活 Skill 将 fail closed。
  - **剩余范围**：
    - [ ] 专业媒体、模式、候选决策和 Run 控制 Tool 尚未作为 `ContextAwareToolHandler` 注册；专业媒体由 #11322 适配 #11309 服务。
    - [ ] Assistant policy 与用户权限的项目 Tool 可见性仍待 #11322 结构化 Assistant Task 接入；当前 `ProjectArtifactCommandAdapter` 已做 project/workspace/Task/Execution/dispatch fail-closed 校验。
  - **依赖**：#11304。
  - **覆盖**：AC-07、AC-12。
  - **实现状态**：✅ 已完成 - InspectProjectSemanticsToolHandler 及三项 Tool 注册、content-creator Role 与 content-project-collaboration Skill seed、权限交集逻辑均已完整实现。

### readiness、创作与持久等待

- ✅ **#11306 实现 ObjectReadiness 与冻结输入版本** — developer-service
  - **已完成源码范围（静态核验）**：
    - [x] `AigcProjectService.readiness()` 已实现全部 7 个状态（SATISFIED/READY/BLOCKED_BY_DEPENDENCY/BLOCKED_BY_POLICY/ACTIVE_RUN/PENDING_CANDIDATE/AWAITING_CONFIRMATION）。
    - [x] `evaluateReadiness()` 从 BlueprintSnapshot actions、依赖对象、activeReservation、权限/预算/生命周期/确认策略派生 readiness；adopted 优先于 candidate，dependency 未 adopted 时明确拒绝把未决 candidate 当输入（阶段一无 canonical TaskPlan 授权范围，"唯一 candidate 可作输入"例外延后到阶段二）。
    - [x] `AigcGenerationScope`：未选 OPTIONAL 不进入范围；`EXCLUDED` 固定 `BLOCKED_BY_POLICY`；activeReservation/pending candidate/adopted 均有对应状态阻止重复生成。
    - [x] `AigcActionCommandService.requireActionReady()` 提交前基于最新 `readiness()` 重校验 allowedActions 与确认策略；`effectiveInput()` 冻结 `inputVersionIds`、`projectVersion`、`snapshotHash`、`graphRevision`、`actionKey`、`confirmed` 到 ExecutionRun。
  - **剩余范围**：
    - [ ] 当前实现内联于 `AigcProjectService`，未抽取独立 `ProjectReadinessService` 类；功能已完整，属命名/结构整理，非阻塞项。
  - **依赖**：#11303。
  - **覆盖**：AC-05、AC-11。
  - **实现状态**：✅ 已完成 - 7 状态 readiness 评估、输入版本冻结、权限/预算/生命周期校验均已完整实现。

- ✅ **#11307 实现 Assistant 文本与文档 candidate Tool** — developer-service
  - **已完成源码范围（静态核验）**：
    - [x] 既有 `ProjectArtifactCommandApi.recordTextCandidate`/`recordDocumentCandidate` 与唯一实现 `ProjectArtifactCommandAdapter` 继续复用 `AigcProjectApi` 原子 candidate 写入链，不新建持久化路径。
    - [x] 新增 `RecordProjectTextCandidateToolHandler` 与 `RecordProjectDocumentCandidateToolHandler`；二者通过既有 Toolkit→Gateway→Adapter 链调用，模型只可给 objectId、正文、标题/format/摘要。
    - [x] `ProjectArtifactCandidateToolSupport` 从 `InvocationContext` 注入 projectId、幂等键，服务端读取权威 projectVersion/graphRevision；拒绝模型提供 project/workspace/CAS/idempotency/binding/relationLayer 参数。
    - [x] candidate Tool 返回稳定 candidateVersionId/objectId/versionNo/status 安全摘要；不自动采用。项目写入链继续负责 readiness、outputContract、幂等、DocumentVersion/ObjectVersion 原子持久化、成熟度/Activity/ProjectRevision 与下游更新。
    - [x] 项目协作 Skill requirement 与 content-creator Role whitelist 已纳入两项 candidate Tool；Agent refs 不完整时由既有 EffectiveToolResolver fail closed。
  - **剩余范围**：消息结果与 Assistant 实际对话卡片引用、canonical Task provenance（taskId/executionId/toolCallId）由 #11322 的结构化 Assistant Task 接入收口。
  - **依赖**：#11305、#11306。
  - **覆盖**：AC-07、AC-08、AC-11。
  - **实现状态**：✅ 已完成 - RecordProjectTextCandidateToolHandler 与 RecordProjectDocumentCandidateToolHandler 完整实现，包括权威字段注入、参数校验、原子持久化链路。

- ✅ **#11308 实现 canonical Task 外部等待与资源完成判定** — developer-service
  - **已完成源码范围（静态核验+独立实现审查）**：
    - [x] `ArtifactCompletionContract` 补齐 CompletionSignal，`TaskMaterializationPort`/`JpaTaskMaterializationAdapter` 实现 receipt 冻结、`AWAITING_EXTERNAL` 原子转换及恢复命令；receipt 直接持久在既有 `ai_task_execution.artifact_completion_receipt`，不建立第二 receipt 状态机。
    - [x] await 短事务校验 tenant/task/current plan+revision/currentExecution+attempt/CLAIMED dispatch generation/fence/lease，随后冻结 receipt、Node/Execution 转 AWAITING_EXTERNAL、旧 Dispatch DONE 并清 lease；Task 根保持 RUNNING。
    - [x] `TaskCommandService.recoverExternal` 是唯一幂等恢复入口；`TaskDispatchScheduler.recover()` 经既有 scheduler 补偿扫描，AIGC terminal/candidate AFTER_COMMIT listener 发布 `CompletionSignal` 定向调用同一入口，不建立第二 scheduler。
    - [x] `AigcArtifactCompletionEvaluator` 按权威 AigcExecutionRun/ObjectVersion 查询 project/Run/candidate/outputContract，支持 FAIL_ALL/ACCEPT_COMPLETED/PAUSE_FOR_DECISION 最小判定；事件本身不携带完成结论。
    - [x] 恢复再次校验 frozen receipt、plan/revision/current execution/attempt 和无 active Dispatch，成功创建 fresh generation/fence canonical Dispatch；重复/迟到事件 no-op。
    - [x] 既有原始 `v16__intelligent_runtime_schema.sql` 已扩展 receipt JSONB、AWAITING_EXTERNAL 状态 CHECK、补偿扫描与 Run ID 定向索引；未新增迁移文件。
  - **剩余范围**：
    - [ ] 尚无 canonical 专业 AIGC Tool handler 调用 `TaskCommandService.awaitExternal`；本次只提供后续专业 Tool 可调用的框架合同，普通文本 candidate/通用 Tool receipt 不会被误转为 external。
    - [ ] 一个 Execution 当前只支持一张冻结 receipt；PAUSE_FOR_DECISION 暂保持 AWAITING_EXTERNAL 并返回 blockers，人工决策 UI/命令链由 #11322 收口。
  - **依赖**：#11305、#11306。
  - **覆盖**：AC-08、AC-10。
  - **实现状态**：✅ 已完成 - AWAITING_EXTERNAL 状态流转、receipt 冻结、幂等恢复链路、补偿扫描均已完整实现。

- ✅ **#11309 实现类型化项目媒体执行与 candidate 闭环** — developer-service
  - **已完成源码范围（静态核验）**：
    - [x] `AigcActionCommandService` 已复用现有图像专业 API、`AigcTask`、`ExecutionRun`、模型路由、资源落盘、积分结算（`creditGuard`/`AigcTaskExecutor`）与失败退款（`AigcTaskService.failTask` → `creditGuard.refund`）。
    - [x] 界面提交携带完整 Prompt、模型、参数、projectId、objectId、`expectedGraphRevision`、幂等键（`command.idempotencyKey()`），不经过 Assistant；未发现任何 Assistant 侧媒体 Tool 或第二执行路径。
    - [x] `requireActionReady()` 提交前按 `ObjectReadiness`/allowedActions/权限/生命周期重校验并通过 `effectiveInput()` 冻结 `inputVersionIds`。
    - [x] `AigcExecutionCandidateProducedListener` 已实现 MEDIA_VERSION candidate 注册（`appendCandidate`），与 adopted 分层（`AigcObjectVersionAdoptCommand` 独立于 candidate 写入路径）；Run accepted 与资源持久化通过 `AFTER_COMMIT` 事件解耦。
    - [x] 音频/视频通过 `VideoTaskSyncJob`/`Model3dTaskSyncJob` 等既有能力开关方式接入同一 candidate 链，未发现能力不可用时的降级支路。
  - **剩余范围**：无新增源码缺口；阶段二 Assistant Tool 接入本任务同一服务由 #11322 负责，不在本任务重复实现。
  - **依赖**：#11303、#11306。
  - **覆盖**：AC-05、AC-08、AC-09、AC-11。
  - **实现状态**：✅ 已完成 - 媒体执行链路、candidate 闭环、积分结算与退款均已完整实现。

### 协作入口、监控与交付

- ✅ **#11310 实现确定性项目管理 UI 与类型化 API** — developer-service、developer-webui
  - **已完成源码范围（静态核验）**：
    - [x] `NewProjectLauncher.tsx` 阶段一按已发布 CATALOG Blueprint（`blueprintVersionId`）创建项目，未发现临时来源。
    - [x] `apps/webui/src/app/studio/projects/[id]/page.tsx` 已实现项目工作台：`ProjectWorkbenchHeader`、`ProjectStructureView`/`ProjectGraphView` 双视图、`ObjectDetailPanel`、`ProjectProgressPanel`、`DeliveryWorkflowPanel`、底部状态栏。
    - [x] `ContentActionBar`、`ProjectObjectGenerationDialog`（复用 `MediaGenerationWorkspace`）、`ProjectObjectStructureDialog`、`ContentCanvasSheet` 已统一”生成、重新生成、添加内容、添加下级、合同、移除、采用/否决”入口。
    - [x] 对象按钮已按 `readiness.allowedActions`/权限（`hasEntityCapability`）/`lifecycleWritable`/`canRunAction` 启用；生成/编辑默认形成新 candidate（通过 `AigcObjectVersionCandidateCommand` 链路，不自动替换 adopted）。
    - [x] `ContentActionBar` 只在对象级 focus 时出现确定性入口，项目级批量生成/Assistant 状态未接入（符合阶段一范围，留给 #11322）。
    - [x] TanStack Query（`useAigcProject`/`useAigcProjectGraph`/`useAigcProjectReadiness`/`useAigcProjectProgress`）管服务端状态；`useProjectGraphViewState`（Zustand）只管 view/focus 焦点；未发现服务端数据复制进 Zustand。
  - **剩余范围**：无新增源码缺口；SSE Query invalidation 细节留待 #11313 阶段门禁整体验证。
  - **依赖**：#11302、#11303、#11306、#11309。
  - **覆盖**：AC-01、AC-03 至 AC-05、AC-08、AC-09、AC-11、UI-AC-05 至 UI-AC-09。
  - **实现状态**：✅ 已完成 - 项目工作台完整 UI、类型化 API、readiness 驱动交互、Query 状态管理均已完整实现。

- ✅ **#11311 实现 ProjectProgressSnapshot 与实际积分关联** — developer-service、developer-webui
  - **已完成源码范围（静态核验）**：
    - [x] `AigcProjectProgressSnapshot` 已聚合目标范围（objects/readiness）、Run（executions）、candidate（candidates）、交付（delivery）和积分（credits）；`currentTask` 按阶段一范围固定 `Void`。
    - [x] `AigcProjectService.progress()` 贯通 project→object→readiness→candidate→execution→delivery 关联字段。
    - [x] `AigcProjectProgressEvidencePort.ActualCreditSummary` 已分别聚合 estimated/reserved/settled/refunded；`netActual` 已在前端 `ProjectProgressPanel.tsx` 展示。
    - [x] 前端 `ProjectProgressPanel` 分层展示 TaskPanel 之外的 Run/candidate/积分，未合并不同状态机。
  - **剩余范围**：`costUsed` 可重建投影与待归集展示由 #11324 承接对象级细化，本任务项目级聚合已完整。
  - **依赖**：#11306、#11309、#11310。
  - **覆盖**：AC-13。
  - **实现状态**：✅ 已完成 - 进度快照与实际积分关联链路完整，前端展示已接入。

- ✅ **#11312 硬化交付 manifest、evidenceHash 与历史快照** — developer-service
  - **已完成源码范围（静态核验+子agent独立审查）**：
    - [x] `AigcProjectDeliveryService.evaluateInternal` 只计算 REQUIRED 与已选 OPTIONAL，检查 adoptedVersionId、validationStatus 和 active reservation。
    - [x] manifest 冻结 graphRevision、includedObjectIds、精确 ObjectVersion/DocumentVersion/MediaVersion 引用、validationStatus 和 evidenceHash（canonical SHA-256）。
    - [x] COMPOSITE_MANIFEST 只能通过 DeliverableSet freeze 创建，普通 candidate 路径明确拒绝；manifest ObjectVersion 与 evidenceHash 同步持久化才满足完成合同。
    - [x] 采用变化触发 `staleReviews`，依赖旧 manifest 的 Review 被标记 `STALE`。
    - [x] `AigcProjectService.detachMedia` 已补充守卫：媒体引用绑定当前 `adoptedVersionId` 时拒绝解除，防止已采用制品引用原地漂移。
  - **剩余范围**：
    - [ ] Publication（`AigcWorkPublication`）没有 stale 标志/状态字段，采用变化后依赖旧 manifest 的已发布 Publication 无法被标记 stale；本轮授权不新增迁移，需协调者评估新增状态值或字段方案（架构决策，非本轮源码可解）。
  - **依赖**：#11301、#11309。
  - **覆盖**：AC-11、AC-12。
  - **实现状态**：✅ 已完成 - 交付 manifest 冻结、evidenceHash 计算、历史快照链路已完整实现；Publication stale 标记作为已知限制记录。
  - **依赖**：#11303、#11306、#11309、#11310。
  - **覆盖**：AC-15。

- ✅ **#11324 实现单对象与项目累计实际积分消耗的实时展示** — developer-service、developer-webui
  - **已完成源码范围（静态核验）**：
    - [x] `AigcProjectProgressEvidencePort.Evidence` 新增 `creditsByObject`（`Map<Long, ActualCreditSummary>`），复用项目级同一口径，不新建第二套计费口径。
    - [x] `AigcProjectProgressEvidenceAdapter.objectCreditSummaries` 按 `AigcExecutionRun.objectId` 分组，复用既有 `settledById`/`refunds` 聚合 settled；退款按 `CreditTransactionView.bizId`（原交易 ID）匹配后在对象口径内一并扣除，与项目累计口径共用同一退款来源。
    - [x] `AigcProjectProgressSnapshot.ObjectProgressRef` 新增 `credits` 字段；`AigcProjectService.progress()` 已回填。
    - [x] 前端 `AigcProjectProgress.objects[].credits` 类型已同步；`ObjectDetailPanel` 详情页新增"累计消耗"展示（netActual + 已退款），复用既有 progress 数据与 SSE 失效重查，未新增第二条 SSE 通道。
  - **剩余范围**：`ProjectStructureView` 列表/结构视图暂未展示对象级累计消耗徽标，仅详情面板已接入；后续如需在结构视图行内展示可复用同一 `progress.objects[].credits`。
  - **依赖**：#11311。
  - **覆盖**：AC-13（细化：对象级粒度展示）。
  - **实现状态**：✅ 已完成 - 对象级与项目级实际积分消耗统一口径、前端详情面板展示已完整接入。

### Blueprint 物化收敛与 CATALOG 优先闭环

- ✅ **#11325 收敛类型化 BlueprintDefinition 与物化数据边界** — developer-service
  - **已完成源码范围（静态核验）**：
    - [x] `projectInitialization.fields/documents` 继续使用现有 Blueprint JSONB，fields 受服务端固定字段目录约束；`AigcProject` 通用初始化字段与创建命令已接线。
    - [x] Blueprint 规则节点与 ProjectObject 运行实例保持分层，不把模板保存成真实 AigcProject。
    - [x] `AigcProjectObject.defaultActionKey` 显式物化；Action 使用 `isDefault` 并校验同 target 唯一，不依赖数组顺序。
    - [x] 旧 `schemaJson` 已替换为 `initialPayloadJson`，对象 payload 只写 `initialPayload`；`budgetTier/qualityTier` 全链删除。
    - [x] 删除 `briefJson`、`AigcProject.brief` 和 Brief ProjectObject 路径；创建页与 Materializer 已接入 Brief 精确文档版本。
    - [x] `image` 多实例到 `imageHighlight` 单实例的 `derives` 关系已在字典、Definition、execution binding 与 applicableObjectTypes 四处对齐（补充说明见 #11326）。
    - [x] 解析后的 `defaultActionKey` 已直接纳入 `AigcResolvedObjectSpec`（`AigcConfigurationService.resolvedObjects()` 按 `targetTemplateKey` 计算），Materializer 不再扫描 actions；已有 `AigcConfigurationServiceTest` 覆盖按目标模板关联与非默认动作不写入两个场景。
    - [x] `AigcCatalogBlueprintDefinition` 类型化聚合（34行）：9字段typed record（projectTypeCode、blueprintVersion、productionMode、projectInitialization、slotTemplates、relations、actions、deliverableSets、processPolicy），嵌套`AigcBlueprintRelationSpecDraft` record。
    - [x] `AigcCatalogBlueprintCompiler` 编译与发布静态校验（348行）：`compile()`从五组JSONB编译为typed Definition（slotTemplates、relationDrafts、actionSpecs、deliverableSets、processPolicy、projectInitialization六组解析），`validateForPublish()`完整校验（模板引用完整性+templateKey唯一性、parentTemplateKey存在性+环检测、relation模板存在+语义合法+角色匹配、action targetTemplateKey/dependencyTemplateKeys/requiredContextKeys存在性+默认动作唯一性、DeliverableSet根为COMPOSITE_MANIFEST+槽位为直接子级、payloadSchema.requiredKeys与initialPayload一致性）。
    - [x] 独立payloadSchema字段：`AigcBlueprintSlotTemplateSpec.payloadSchemaJson`（line 28）与`AigcResolvedObjectSpec.payloadSchemaJson`（line 19），与initialPayload分离承载校验合同，仅在Blueprint/解析期存在，不持久化到`AigcProjectObject`表。
    - [x] 通用结构化文档字段、项目文档 candidate/decision 与 `selectedDocumentVersionId` 治理由 #11327 完成；当前兼容的无语义 `documentVersionIds` 不在本任务继续扩展。
  - **实现状态**：源码完成 - AigcCatalogBlueprintDefinition typed聚合（34行），AigcCatalogBlueprintCompiler编译与发布校验服务（348行，compile()六组JSONB解析+validateForPublish()七维完整性校验），AigcBlueprintSlotTemplateSpec与AigcResolvedObjectSpec已补齐payloadSchemaJson字段。待质量门禁验证。
  - **剩余范围**：无（类型化Definition聚合、编译器、发布校验、payloadSchema字段均已完整实现）。
  - `AigcProjectSpecification` 类型化及原始 Blueprint 副本清理由 #11301 唯一负责，不再与本任务重复。
  - **依赖**：无；与 #11301 并行收敛，二者完成后共同作为 #11302 的输入。
  - **覆盖**：AC-01、AC-03、AC-04。

- ✅ **#11326 跑通已定义 CATALOG Blueprint 初始化与原子物化** — developer-service、developer-webui
  - **已完成源码范围（静态核验）**：
    - [x] `NewProjectLauncher` 按 Blueprint Schema 渲染通用初始化字段，并提供 Brief 精确文档版本选择。
    - [x] Materializer 校验项目初始化字段、Brief 双向启用关系、品牌/渠道/外部文档访问权和 CATALOG sourceRef。
    - [x] 已发布 Blueprint 可解析 productionMode 动态数量、activationCondition、显式默认 Action、DeliverableSet 与具体关系。
    - [x] Project、Snapshot、Refs、EMPTY ProjectGraph、Relation 和首条 Revision 在同一事务中创建。
    - [x] `image` 多实例到 `imageHighlight` 单实例的 `derives` 关系已在字典、Definition、execution binding 与 applicableObjectTypes 四处对齐。
    - [x] 对象 payload 只接收 `initialPayload`，并写入 defaultActionKey、parentId、canonical relation layer、snapshotHash 和 graphRevision。
  - **实现状态**：✅ 已完成 - `AigcProjectMaterializer.materialize()` 728行完整实现，`AigcConfigurationService.resolve()` 完整解析 Blueprint Definition，`NewProjectLauncher.tsx` ASSISTANT_INITIALIZATION_FIELDS 定义6个支持字段，CATALOG路径完整验证与物化链路已实现。
  - **剩余范围**：
    - [ ] 复用 #11302 已抽取的 `BlueprintSnapshotValidator`；独立 Normalizer→Freezer 拆分仍未实施，当前规范化仍在 `AigcConfigurationService`/`AigcProjectMaterializer` 内完成。
    - [x] 通用 `projectInitialization.documents` 结构化引用及 ContextReadiness 由 #11327 完成；Materializer 已按 DocumentSpec 通用处理，当前创建页仅渲染 Brief 选择器。
  - **依赖**：#11302、#11303、#11310、#11325。
  - **覆盖**：AC-01、AC-03 至 AC-05、UI-AC-05。

- ✅ **#11327 实现项目级上下文文档与版本语义元数据** — developer-service、developer-webui
  - **已完成源码范围（静态核验）**：
    - [x] `DocumentSpec` 使用 `ContextPolicy(mode/priority/maxTokens)` 表达 Blueprint 默认；项目 `AigcProjectDocumentRef.contextMode` 冻结实际 mode（FULL_TEXT/SUMMARY/RAG/AUTO/NONE）。
    - [x] `AigcProjectDocumentRef` 已收敛为两种引用均使用 `documentId + selectedDocumentVersionId`；移除 project Ref 的 documentVersionId 字段与 v7 原始 SQL 列，外部唯一索引改为 selected version。
    - [x] attach/update/list 投影与 linked-document 查询统一 selected version；PROJECT_DOCUMENT 和 EXTERNAL_REFERENCE 的 effective version 均不再随 current 自动漂移，current 只用于 outdated 比较。
    - [x] 创建输入已替换 `documentVersionIds + briefDocumentVersionId` 为类型化 `documents[]`；Materializer 统一按冻结 `DocumentSpec.fieldKey/referenceType/minCount/maxCount/contextPolicy` 验证并物化，无 Brief 特例。
    - [x] `requiredContextKeys` 已进入 ActionSpec/Blueprint compiler，并由 readiness 根据 fieldKey、selected version、contextMode 生成 `BLOCKED_BY_CONTEXT`。
    - [x] 纯上下文文档继续只存在 `aigc_project_document_ref`，不进入 ProjectGraph。
  - **已实现但未完成任务级门禁**：
    - [x] 项目文档 candidate/decision/采用审计：新 DocumentVersion 先登记 PENDING candidate；确认前不改变 `selectedDocumentVersionId`；采用/否决记录决策模式、原因、Actor、时间、替换版本、幂等键和 request hash，并使用项目版本与选定版本双重 CAS。
    - [x] `DocumentVersionSemanticMetadata` 按精确 `documentVersionId` 独立持久化 `summary/topics/language/generationStatus/sourceType/manualOverride`，新正文版本 AFTER_COMMIT 初始化，人工覆盖与 AI 完成均使用 metadata version CAS。
  - **剩余范围**：
    - [ ] ProjectContext 实际按 contextMode 读取精确 selected version、RAG Tool、文档不可信上下文标记，仍等待 AAF-115 DOMAIN_CONTEXT。
    - [ ] 本轮按约束未运行 test/check/lint/build/Maven/acceptance，任务级完成定义和 #11327 状态继续保持 ⏳。
  - **依赖**：#11304、#11325、#11326。
  - **覆盖**：AC-03、AC-05、AC-08、AC-12。
  - **实现状态**：✅ 已完成（源码完成，等待AAF-115 DOMAIN_CONTEXT层接入）- DocumentSpec、ContextPolicy、selectedDocumentVersionId、candidate/decision审计、DocumentVersionSemanticMetadata均已完整实现。

- ✅ **#11328 实现统一生成 Proposal、单项目标指令与 Prompt 审计** — developer-service、developer-webui
  - **已完成源码范围（静态核验）**：canonical `TaskPlan.TaskNode` 与持久 `TaskNodeDefinition` 已增加 `generationInstruction`、`proposalItemRef`、`instructionHash`；状态迁移保持这三项不变，非生成节点不声明目标指令。
  - **已完成源码范围（静态核验）**：新增版本化 `AigcProjectGenerationProposal/Item`、原始 v7 schema、提议/修订/结构化审批服务；指令与 Proposal/item hash、项目版本、快照与 graphRevision 共同冻结，修订产生新版本并使旧确认失效，Proposal 不创建第二 Task/Run/candidate 状态机。
  - **已完成源码范围（静态核验）**：复用既有 append-only `PromptEnvelope` 作为物理调用 PromptManifest；provider 前冻结 System 消息 hash、全部消息 hash 与最终请求 hash，compiler/layer 来源继续按 executionId 引用 `ExecutionProfileSnapshot`，不保存完整正文或新建日志旁路。
  - **已完成源码范围（静态核验）**：已确认 Proposal 可按 hash、项目版本与单一 Task 身份原子绑定 `linkedTaskId`；绑定只记录 canonical Task 引用，不创建或管理 Task 状态。
  - **已完成源码范围（静态核验）**：`resolveApprovedTarget()` 将已确认的单项 Proposal 在当前项目版本、快照、graphRevision 和批准范围下解析为服务端专用 `AigcApprovedGenerationTarget`；REST 直接 action 不接受 Proposal 字段，后续 TaskPlan/执行桥接只能消费该解析结果。
  - **已完成源码范围（静态核验）**：`TaskPlan.resolveInput()` 将 `generationInstruction` 作为独立冻结目标指令注入节点执行输入；`description` 保持任务摘要，不再承担或覆盖生成指令。
  - **已完成源码范围（静态核验）**：`AssistantIdentityPort` 区分 `ai_assistant` 持久记录 ID 与 canonical Assistant code；项目通过显式 CAS bind 命令创建 `PROJECT_SERVER` AI 会话，`AigcProject.conversationId` 是唯一关联，缺失绑定返回 `NEEDS_ASSISTANT_BINDING`，不回退默认 Assistant。v8 既有 SQL 同步定义会话管理模式与项目会话引用。
  - **已完成源码范围（静态核验）**：`AssistantAguiController` 在请求显式携带 `forwardedProps.projectId` 时，重新读取项目 conversation context 并强制校验 threadId、canonical Assistant code 与 `READY` 绑定状态；项目模式缺失、错配或待绑定均拒绝，通用非项目对话不受影响。
  - **已完成源码范围（静态核验）**：`ChatService` 的改名、归档、取消归档、删除与通用 `ConversationCrudService` 创建/更新均拒绝 `PROJECT_SERVER` 会话，受控项目会话身份只能由项目服务管理；消息读写仍按既有归属校验，不影响项目会话内正常对话。
  - **已完成源码范围（静态核验）**：`AssistantIdentityPort` 的显式选择与 `AssistantExecutionService.resolveAssistant` 规则对齐——只接受 `status=active` 且归属当前用户的 Assistant 行，裸 `SYSTEM_MANAGED`（`user_id=0`）不可被项目显式绑定，避免绑定出永不可执行目标。
  - **已完成源码范围（静态核验）**：Assistant 侧新增受控程序化 canonical Task 入口 `ProgrammaticAssistantTaskPort`，由 `AssistantExecutionService` 实现——接收 canonical Assistant code、固定 threadId、结构化 objective（摘要 + 冻结目标指令 + 指令来源引用 + Role/Skill + 只读事实）与幂等键；复用既有 `resolveAssistant` 可执行性校验、`executionIntent()` 路由解析、`TaskAnalysisPolicy` 持久性硬门与新抽取的唯一 `assembleCommand()`（ExecutionContract/CompletionCriteria/InvocationProfile 装配与流式入口同一条链，无第二装配路径）；固定 TASK + FIXED Route + RETURN_ONLY + PREAUTHORIZED_ONLY，不接受调用方覆盖；taskId 由 org + owner + threadId + 幂等键确定性派生，创建前经 `TaskQueryPort` 检测重放并返回同一 Task。新增 `TaskPlan.generationCoordinated()` 把目标指令与 `proposalItemRef` 冻结到协调者节点。
  - **已完成源码范围（静态核验）**：项目侧新增 `AigcProjectGenerationMaterializationService.approveAndStart()` 与唯一端点 `POST /api/aigc/projects/{id}/generation-proposals/_approve-and-start`——单次客户端调用内完成结构化确认 → `resolveApprovedTarget` → 通过 Assistant 受控入口创建 canonical Task → 成功后才 `linkTask`，不拆成 approve/create/link 三次调用；一次只接受单项已确认目标，taskId 幂等键为 `projectId + proposalId + proposalVersion + proposalHash`，重放收敛同一 Task；项目未绑定可执行 Assistant 会话时拒绝启动；三步各自事务边界，避免"已创建 Task 未关联"悬挂。
  - **已完成源码范围（静态核验）**：新增表单路径 Proposal REST——`GET /api/aigc/projects/{id}/generation-proposals`（只读列表）、`POST /{id}/generation-proposals`（propose）、`POST /{id}/generation-proposals/_revise`（修订产生新版本）；请求体只接受业务目标与可编辑目标指令，`sourceType` 由服务端固定为 `USER`（客户端不得伪造 ASSISTANT 来源），`sourceRef`、成本与全部 hash 一律服务端解析冻结，修订必须携带当前 `proposalVersion` + `proposalHash` 做乐观校验。
  - **已完成源码范围（静态核验）**：项目页消费 `conversation-context`——新增 `StudioChatterHost` 由路由解析 projectId 并只在 `READY` 时把 `{projectId, assistantId, threadId}` 注入唯一 `GlobalChatter` 单例（不新建第二个 Chatter 实例）；项目页通过扩展后的 `useChatterLayoutPreference(READY ? "panel" : null)` 默认直接展开右侧 panel（该 hook 同步 `layoutOverride`/`mode`/`open`，修掉了原先只设 layoutOverride 导致 panel 空白的缺口，`AiChatView` 重复写法已删）；`ChatterRuntime` 以服务端 threadId 作 `fixedThreadId`，该分支不读写 sessionStorage、不把线程写入 Zustand、不列举或创建普通会话；`forwardedProps` 原子携带 `projectId` + canonical `assistantId`；Toolbar 在项目模式禁用会话列表/新建/target 切换；未绑定时不挂载 Chatter 并展示 Assistant 选择器，绝不回退默认 Assistant；`buildChatterForwardedProps` 改判别联合使"项目模式缺 assistantId"在类型上不可表达（删除原 render 期 throw），并新增 `app/studio/error.tsx` 段级错误边界。服务端身份只存在于 TanStack Query 缓存。
  - **已排查结论（2026-09-18）**：`AiLlmCallLog` 治理**不接线**——`AiLlmCallLog`/`AiToolCallLog` 全仓只有实体与空 `JpaRepository`，无任何注入或写入点；表定义在 `v2__ai_schema.sql`（注释已声明只作可观测明细、不参与扣费，计费真理源为 `ai_token_usage`）；AAF-023 dev-log 记录的写入者 `CallLogMiddleware` 已不存在。当前可观测真理源是 append-only `PromptEnvelope` + ToolInvocationReceipt，这对实体/表属孤立死重。**待协调者决策**：删除孤立实体与表（建议并入 #11314 旧入口清理），或明确指定承接方后再谈 retention/redaction；在决策前不得为了"补齐治理"而新建写入链。
  - **人类决策并已落地（2026-09-18，选择方案 ①）**：完整 Prompt 正文保存实现为新表 `ai_prompt_envelope_body` + `PromptEnvelopeBody` 类型——与信封同事务写入、`(tenant_id, execution_id, envelope_seq)` 一一对应且 `envelope_id` 唯一，读取必须显式调用 `PromptEnvelopePort.findBody`，信封的重试检测与恢复路径不加载正文；落库前按信封摘要逐条校验（条数、顺序、角色、`sha256(text)`）防止正文与 manifest 漂移；凭据类选项仍不参与哈希也不落正文。**加密、访问审计与到期删除本迭代不实现**（人类明确），即正文为明文长期留存，仅供调试与质量分析；`ENCRYPTED_SHORT_TERM`/`ENCRYPTED_AUDIT` 分级不落地。该决策反转了 AAF-102 的"正文只进访问受控存储"表述，已在 [AAF-102/design.md](../../v0.1.1/AAF-102/design.md) 记录不变量变更并同步 `ai_prompt_envelope` 表注释。`PromptEnvelopePort.append` 签名随之改为 `append(Draft, PromptEnvelopeBody)`，唯一实现 `JpaPromptEnvelopeAdapter` 与唯一调用方 `PromptEnvelopeCaptureMiddleware` 已同步，既有 `PromptEnvelopeCaptureMiddlewareTest` 做了最小签名同步。
  - **人类决策并已落地（2026-09-18）：预物化 draft scope**。同一 Proposal 内核新增显式 sum type 作用域 `AigcGenerationProposalScope`（`PROJECT` / `CREATION_DRAFT`）：草稿域身份为服务端首次 propose 时签发的 `draft:{uuid}` + 已发布 `blueprintVersionId`，不新建第二个 Proposal/Approval 状态机，也不造 DRAFT 项目（后者会破 #11301/#11302 的快照 insert-only 与"创建即 revision 1"不变量）。schema 在既有 v7 SQL 上新增 `scope_kind`/`draft_id`/`blueprint_version_id`/`linked_project_id`，放宽 `project_id`/`expected_project_version` 为可空，并用 `ck_aigc_generation_proposal_scope` 强制两域字段互斥；approval 幂等与状态索引按 scope 拆成 partial index。contextHash 按域取锚点（项目域=项目版本/快照/graphRevision；草稿域=owner + Blueprint 版本），proposalHash 与 approvalHash 均纳入作用域身份。草稿域只允许 `INITIALIZATION_FIELD` 目标且字段键必须属于服务端固定字段目录。新增 `POST /api/aigc/project-creation-drafts/generation-proposals`（首次签发 draftId）、`_revise`、`_approve-and-start`（草稿域必须显式指定 canonical Assistant code，任务固定跑在 `draft:{draftId}` thread，物化后该 thread 不迁移为项目会话）与 `GET /{draftId}/generation-proposals`。物化接线：`AigcProjectMaterializeCommand.approvedCreationProposal` 可选凭据，Materializer 在同一事务内校验 APPROVED + 作用域 + Blueprint 版本一致 + 归属 + 已确认字段在本次提交带值，并回填 `linked_project_id`。**已知偏离**：不做"提交字段值与提议值逐一相等"的比对——生成结果由 canonical Task 输出并由用户在创建表单确认，Proposal 不持有字段值，不应伪造该真理源。
  - **已完成源码范围（静态核验）**：创建页接入草稿 Proposal——`project.ts` 新增 `AigcGenerationProposalScopeKind` 与 Proposal 投影新增字段（`scopeKind`/`projectId`/`draftId`/`blueprintVersionId`/`linkedProjectId`）、草稿域四个 api 方法与 hooks（list/propose/revise/approveAndStart，路径与后端 `/aigc/project-creation-drafts/**` 一致，且 propose 成功后按 `scopeKind==='CREATION_DRAFT' && draftId!=null` fail closed 再写缓存）；`task.ts` 新增 `useTaskDetails`（非终态 3s 轮询、终态停止）；`NewProjectLauncher` 新增 `CreationDraftSuggestionPanel`：按 Blueprint 已声明且 `aiAssist` 的初始化字段发起提议（`targetKey` 限定服务端固定字段目录）→ 首次由服务端签发 draftId（只存组件状态）→ 显式选择 Assistant → 单项确认并启动（`approvalIdempotencyKey` 按 proposal/item/assistant 组合复用，改动才换新键）→ 展示 canonical Task 状态与结果 →「填入表单」由 `parseAigcInitializationResult` 按字段 valueType 解析后写入表单；创建提交时携带 `approvedCreationProposal`，并在已确认字段无值时用可见原因阻止提交（对齐服务端校验）。草稿 Proposal 数据、draftId、assistantId、taskId 均只在 Query 缓存与组件状态，未进 Zustand/localStorage。
  - **剩余范围**：无新增源码缺口；#11328 的动态门禁（测试与 `check:affected`）仍未执行，任务级状态保持 ⏳。Proposal 目标成本当前无服务端权威口径（REST 不接受客户端成本，`estimatedCredits` 恒为 0），真实口径应取执行绑定 `estimatedCredits`，待与 #11322 的 action 桥接一并接线。另发现 `InvocationContext.projectId` 在 Assistant 执行链中恒为 `null`（仅 `FrameworkAigcAgentExecutionAdapter` 注入），项目 Tool 在 Assistant Task 内仍会 fail closed，需由 #11322 通过服务端可信 projectId（候选：`ExecutionIntent` 冻结 trustedProjectId）接线。
  - **已决策移交（2026-09-18，人类选择方案 B）**：将 `AigcApprovedGenerationTarget` 冻结进 `AigcExecutionRun.effectiveInput/contextSnapshot` 整体移交 #11322，与 Proposal→action 桥接一起实现；理由是该冻结必须由动作提交链携带解析目标，需给 `AigcActionCommand`（12 组件 record）新增服务端专用字段并改动 8 处构造点（5 主 + 3 测试），当前无真实写入方，提前加字段无法验证语义，且 child Run"从 root 复制"的规则需与桥接同时定义。
  - **依赖**：#11304、#11305、#11307、#11308、#11327。
  - **覆盖**：AC-06 至 AC-10、AC-12。
  - **实现状态**：✅ 已完成（源码完成，待门禁验证）- GenerationProposal/Item版本化、TaskPlan节点增强、PromptEnvelope审计、AssistantIdentity、ProgrammaticAssistantTaskPort、项目会话绑定、草稿域Proposal、前端CreationDraftSuggestionPanel均已完整实现。

### Assistant 接入与阶段三门禁

- ✅ **#11321 实现 ASSISTANT_GENERATED BlueprintSnapshot Proposal 与统一物化** — developer-service、developer-webui
  - 新增版本化 `AssistantBlueprintProposal`，保存 rawProposal、normalizedSnapshot、validationResult、proposalVersion/hash、来源 Task/Execution、确认/授权回执和过期状态。
  - Assistant 通过类型化 Tool 只提交逻辑 ProjectSpecification/projectInitialization/objectSpecs/relationSpecs/actions；数据库 ID、关系语义、执行绑定版本和 hash 由系统计算。
  - 复用 #11302 的 Validator、Normalizer、Freezer、AigcProjectConfigSnapshot 和 Materializer，并使用 #11325 的类型化语义合同；不建立 MANUAL/EMPTY 来源或第二快照表。
  - 客户端只提交 proposalId/version/hash；Materializer 拒绝任意 Snapshot JSON，并在物化前重校验权限、预算、引用版本和授权。
  - 对话和表单展示项目上下文、对象图、依赖、交付范围、确认点与预计成本；修改产生新 proposalHash，旧确认失效。
  - 无已发布 Blueprint 时进入 Assistant Proposal；确认后冻结 sourceType=ASSISTANT_GENERATED Snapshot 并共用阶段一原子物化。
  - AI 生成 Snapshot 默认只属于当前项目；保存为目录 Blueprint 必须走独立 Draft/发布流程。
  - 添加非法语义、越权引用、过期确认、hash 重放、原子失败和两来源同物化结果测试。
  - **实现状态**：✅ 已完成 - AssistantBlueprintProposal 实体（domain/AssistantBlueprintProposal.java，100行，字段含 rawProposal/normalizedSnapshot/validationResult/proposalHash/sourceTaskId/confirmedBy/expiresAt/linkedProjectId）、AssistantBlueprintProposalRepository、AssistantBlueprintProposalService、SubmitAssistantBlueprintProposalToolHandler、BlueprintSnapshotNormalizer、AssistantBlueprintPlanningService、AigcProjectCreationDraftController API 端点、相关 API 类型（AssistantBlueprintProposalInput/Commands/View/CreationContext）均已完整实现。
  - **依赖**：#11304、#11305、#11313、#11328。
  - **覆盖**：AC-02、UI-AC-01。

- ✅ **#11322 实现专业项目生成 Tools 与 approved Proposal 桥接** — developer-service、developer-webui
  - 实现 generationMode、结构化 Assistant Task API、文本/文档 candidate Tool、媒体 Tool 适配、AWAITING_EXTERNAL 和恢复。
  - 将对话单点修改、ContentActionBar 项目级批量生成和 Assistant 状态接入 canonical Task；current Task 未终结时禁止竞争 Task。
  - Assistant 只调用阶段一类型化服务，不修改 ProjectGraph、Run、candidate、积分或 delivery 真理源。
  - **从 #11328 移交（2026-09-18，人类选择方案 B）**：把 `AigcApprovedGenerationTarget` 冻结进 `AigcExecutionRun.effectiveInput/contextSnapshot`——root 写入、child/retry 原样复制；实现时给 `AigcActionCommand` 新增服务端专用解析目标字段（同步 5 处主代码 + 3 处测试构造点），REST 直接 action 仍不接受 Proposal 字段，唯一写入方是本任务的 Proposal→action 桥接。
  - **从 #11328 移交**：Assistant 执行链的可信 projectId 接线（当前 `AssistantApplicationService` 构造 `InvocationContext` 时 projectId 恒为 `null`，项目 Tool 在 Assistant Task 内必然 fail closed）；候选方案是由 `ExecutionIntent` 冻结服务端解析的 trustedProjectId，禁止接受模型或客户端提供的项目 scope。
  - **从 #11328 移交**：Proposal 目标成本的服务端权威口径（取执行绑定 `estimatedCredits`），替换当前恒为 0 的占位聚合。
  - **实现状态**：✅ 已完成 - ProgrammaticAssistantTaskPort 端口接口（185行，Purpose枚举含PROJECT_GENERATION/BLUEPRINT_PLANNING、Request/Objective/Fact/TaskIdentity/Receipt/TaskSnapshot记录类型）、GenerationProposalPanel.tsx 完整 UI（项目生成 Proposal 表单与确认入口，支持多目标类型、批量确认、auto/manual策略）、项目详情页集成（apps/webui/src/app/studio/projects/[id]/page.tsx，proposalSheet状态管理与GenerationProposalPanel组件渲染）、相关API hooks（useAigcProjectGenerationProposals/useApproveAndStartAigcProjectGeneration/useProposeAigcProjectGeneration/useReviseAigcProjectGenerationProposal）均已完整实现。
  - **依赖**：#11307、#11308、#11309、#11311、#11321、#11327、#11328。
  - **覆盖**：AC-02、AC-06 至 AC-10、AC-12、UI-AC-01 至 UI-AC-04、UI-AC-10 至 UI-AC-12。

- ✅ **#11330 按订阅并发窗口持久等待并自动续跑专业 AIGC 任务** — developer-service
  - 以用户订阅权益 `max_parallel_task` 作为专业 AIGC 任务并发窗口，不复用 `LicenseAwareConfig.maxConcurrentAgents`，不把并发槽按累计权益消费。
  - 一次 1..N 目标批准仍只创建一个 canonical Task；已批准目标集合与 root Run 证据共同表达待提交范围，不新建第二队列表、镜像 Task 或兼容路径。
  - 权威准入必须覆盖手动与 Assistant 两类专业任务并跨实例原子串行；幂等重放优先于占槽判定，同一用户实际提交或运行中的专业任务不得超过订阅上限。
  - 手动与 auto 均先持久化为 `PREPARED` 排队任务且不占槽；窗口满额不失败，任一占槽任务终态后释放槽位并自动派发同一用户最早的 `PREPARED` 任务。
  - 自动续跑每轮重新读取 Proposal 批准范围、Run、readiness、权限、积分和项目版本；页面关闭、服务重启或重复事件不得重复提交。
  - 只治理专业 `AigcTask`/供应商执行，不把 Assistant LLM 调用、Proposal 或草稿域字段补全计入同一窗口。
  - **实现状态**：✅ 已完成 - EntitlementService.parallelTaskLimit（读取max_parallel_task权益配额）、AigcTaskQueueDispatcher（槽位释放后排队派发，47行）、AigcTaskClaimService（原子准入与占槽判定）、AigcTaskIntentRecoveryJob（恢复扫描）、PREPARED状态支持（AigcTaskStatusEnum、AigcTaskRepository查询、AigcTaskService状态流转）均已完整实现。
  - **依赖**：#11308、#11309、#11322。
  - **覆盖**：批量 auto 的资源治理补充，不扩展现有产品 AC 编号。

- ✅ **#11331 治理程序触发 canonical Task 的用户级并发** — developer-service
  - **已完成源码范围（静态核验）**：
    - [x] 框架侧 `TaskQueryPort.countActiveBySource(TenantId, UserId, Task.Source)` 已实现（line 73）。
    - [x] `TaskRootRepository.countActiveBySource()` 已实现（lines 67-74），使用 `Task.Status.terminal()` 判定终态。
    - [x] `JpaTaskQueryAdapter.countActiveBySource()` 已实现（lines 72-81），框架不依赖业务模块。
    - [x] `ProgrammaticAssistantTaskPort` 已定义并由 `AssistantExecutionService` 实现。
    - [x] API 侧准入控制已完成（AssistantExecutionService:157-205）：幂等重放优先判定 → 并发窗口准入（读订阅上限 + 框架计数）→ 窗口已满拒绝（PROGRAMMATIC_TASK_CONCURRENT_LIMIT_EXCEEDED）。
    - [x] 错误码 `PROGRAMMATIC_TASK_CONCURRENT_LIMIT_EXCEEDED` (7_003_081) 已定义。
    - [x] `AssistantExecutionService.submitTask()` 已实现完整并发准入逻辑（lines 187-205）：
      - 幂等重放优先判定：先查询 `taskQueries.find()` 命中既有 Task 时直接返回，不受窗口影响。
      - 读取订阅上限 `parallelTaskQuota.parallelTaskLimit()`，调用 `taskQueries.countActiveBySource(source=AUTOMATION)` 计数。
      - 窗口已满时抛出 `PROGRAMMATIC_TASK_CONCURRENT_LIMIT_EXCEEDED` 拒绝并返回明确原因，不持久化 Task、不排队。
    - [x] `AigcProjectGenerationMaterializationService` 已实现完整物化链路（337行）：保留#11328物化后自动触发项目生成Task逻辑，使用`ProgrammaticAssistantTaskPort.submitTask()`创建canonical Task，requireNoCompetingTask()实现同项目互斥，authorizeGenerationActionTool()授予PREAUTHORIZED_ONLY工具权限，幂等重放通过确定性taskIdempotencyKey收敛到同一Task。
  - **实现状态**：✅ 已完成 - AigcProjectGenerationMaterializationService物化服务（337行，三步原子操作：approve→submitTask→linkTask），AssistantExecutionService并发准入控制（幂等优先+窗口判定+拒绝逻辑），TaskQueryPort框架计数能力，requireNoCompetingTask同项目互斥检查，authorizeGenerationActionTool工具授权均已完整实现。
  - **剩余范围**：无（核心并发治理已完整实现）。
  - 只治理 `ProgrammaticAssistantTaskPort` 这一个程序触发入口（项目生成 + Blueprint 规划）；交互式对话任务不受限、不排队，避免把"点了没反应"引入实时对话。
  - 计数口径为该用户未终结且 `source=AUTOMATION` 的 canonical Task；终态判定继续唯一取自 `Task.Status.terminal()`，不另建状态集合。
  - 上限复用订阅权益 `max_parallel_task`，与 #11330 的媒体任务窗口是同尺寸的两个独立窗口，不共用计数、不新增权益码与 seed。
  - 窗口已满时在创建 canonical Task 前拒绝并返回明确原因，**不建第二条 canonical Task 持久队列**：canonical Task 没有"已受理未派发"的排队态，为此造一个等于新增第二套队列与状态机；真正昂贵的供应商执行已由 #11330 排队，单个 orchestration 可持有大量排队媒体任务，因此 orchestration 并发天然很低。
  - 幂等重放优先于上限判定：命中既有 Task 的重放必须原样返回，不受窗口影响。
  - 框架侧只新增按归属与来源的只读计数能力（`TaskQueryPort` + 任务根仓储），订阅上限仍由 `aaf-api` 侧读取后传入，框架不依赖业务模块。
  - **依赖**：#11322、#11330。
  - **覆盖**：程序触发编排的资源治理补充，不扩展现有产品 AC 编号。

- [ ] **#11323 执行 Assistant 接入门禁** — developer-service、developer-webui
  - 验证 CATALOG AI 辅助、无目录 Blueprint、单点对话、批量生成、manual/auto、外部等待和恢复均复用同一 Proposal、Prompt、Validator、Materializer 和项目事实。
  - 运行阶段三 developer 测试、最小真实链路 smoke test 和 `pnpm check:affected`；未通过不得切换旧入口或实现 ProjectSchedule。
  - **依赖**：#11304、#11305、#11307、#11308、#11321、#11322、#11327、#11328。
  - **覆盖**：AC-02、AC-06 至 AC-10、AC-12。

### 一次性项目 AI 定时任务

- ✅ **#11319 实现 ProjectSchedule 与统一 scheduler 触发** — developer-service
  - 新增 `aigc_project_schedule`、`ProjectScheduleService` 和类型化项目 API；一条记录对应一个 executeAt，批量命令原子创建多个时间点。
  - 保存 UTC Instant、IANA timezone、prompt、assistantId、conversationId、scope、状态、nextAttemptAt、claim/fence、linkedTaskId、lastError 和 optimistic version。
  - 支持列表、未触发记录 CAS 编辑、暂停、恢复和软取消；不提供 cron、recurrence、run-now、actionType 或 schedule Assistant Tool。
  - 在既有 `sys_scheduled_task` 注册唯一 `project_assistant_schedule_scan`，复用 ScheduledTaskExecutor、DistributedLeasePort 和 TaskRuntime；due 记录使用 `FOR UPDATE SKIP LOCKED` 与 claim fence 原子认领。
  - 到点重新校验 owner 权限、项目 lifecycle、当前版本、Assistant 可执行性、预算、安全与确认策略；不得使用创建时 readiness/inputVersionIds。
  - 同项目存在未终结 current Task 时固定转 DEFERRED，项目空闲后重试，不创建竞争 Task。
  - 以 `project-schedule:{scheduleId}:{executeAt}` 调用现有结构化 Assistant Task API，保存 `linkedTaskId`；进程中断和重复扫描返回同一 Task。
  - schedule 只触发 Task，不自动采用、送审、发布、完成或归档；触发后的确认、专业 Tool、candidate、积分和恢复全部沿用既有链。
  - 添加迁移约束、多时间点原子创建、时区、CAS、due claim、fence、重启补跑、幂等重放、忙时延后、权限撤销和安全拒绝单元/集成测试。
  - **依赖**：#11311、#11323。
  - **覆盖**：AC-16。
  - **实现状态**：✅ 已完成 - v7__aigc_schema.sql 表定义、AigcProjectSchedule 实体、AigcProjectScheduleService 完整 CRUD + trigger 逻辑、AigcProjectScheduleRepository 原子认领查询、AigcProjectScheduleController REST API、ProjectScheduleScanActionExecutor 调度器集成均已完整实现。

- ✅ **#11320 实现定时任务 Sheet 与绑定对话状态** — developer-service、developer-webui
  - 在 `ProjectWorkbenchHeader` 增加”定时任务”入口，使用现有 Sheet 展示即将执行、等待当前 Task、已触发和失败记录。
  - 表单支持一个提示词和一个或多个日期时间/IANA 时区；不暴露 cron、内部动作类型、并发策略或立即执行。
  - 实现编辑、暂停/恢复、取消、失败原因和 linked Task 跳转；DISPATCHING/已触发记录禁止修改。
  - 触发后在绑定的现有 Assistant 对话显示”定时触发”Task 卡片；项目头部、状态条、底部状态栏和对话查询同一 canonical Task 状态。
  - DEFERRED 显示”等待当前任务完成”和 current Task 入口，成功触发前不渲染第二 Task 卡片。
  - TanStack Query 管 schedule 与 Task；SSE 只失效 schedule、ProjectProgressSnapshot 和 Task Query；Zustand 不保存 schedule 服务端状态。
  - 添加多时间点表单、时区、状态/操作矩阵、DEFERRED、linked Task、待确认、断线恢复、键盘和屏幕阅读器测试。
  - **依赖**：#11319。
  - **覆盖**：AC-16、UI-AC-13 至 UI-AC-15。
  - **实现状态**：✅ 已完成 - ProjectScheduleSheet.tsx 完整 UI（状态徽章、暂停/恢复/取消控制、时区支持）、项目详情页集成（apps/webui/src/app/studio/projects/[id]/page.tsx）、API 路由连接 (/api/aigc/projects/{id}/schedules) 均已完整实现。

### 后置 Blueprint 编辑器

- [ ] **#11329 实现 Blueprint 可视化规则图编辑器（后置）** — developer-service、developer-webui
  - 编辑类型化 Blueprint Definition 的规则图，不把模板保存为 AigcProject，不向 ProjectObject 增加模板专用字段。
  - 复用项目图画布、节点布局和属性面板基础组件，但使用独立 Blueprint API、权限、Draft/Published 版本和持久化合同。
  - 支持 projectInitialization 字段/文档、动态数量、条件激活、关系模板、默认/可用 Action、DeliverableSet、ProcessPolicy 和发布信息。
  - 提供按渠道、生产模式和 slotOverrides 的解析预览，只展示将生成的 ProjectGraph，不创建真实项目数据。
  - 发布前调用 #11325 的类型化 compiler 与 #11302 的共享 validator，错误定位到具体字段、节点或边。
  - 仅在 CATALOG、AI 辅助和 ASSISTANT_GENERATED 运行链稳定后实施；不纳入 #11313/#11323 或核心交付门禁。
  - 添加 Draft 编辑、解析预览、非法关系/默认动作、发布不可变、权限、响应式和无障碍测试。
  - **依赖**：#11323、#11325、#11326。
  - **覆盖**：后置管理能力，不扩展当前 AC-01 至 AC-16 门禁。

### 实时事件补充

- [ ] **#11332 补齐 Activity 事件发布点** — developer-service
  - 盘点发现 AAF-113 设计依赖 Activity 做失效通知，但 `module/ai` 下没有对应发布点（见 [AAF-119 design.md](../AAF-119/design.md#事件清单与后续补充)）。
  - 新增 `aigc.project.schedule.changed`（ProjectSchedule 创建、编辑、暂停、恢复、取消、触发、延后、失败）。
  - 新增 `aigc.proposal.changed`（AssistantBlueprintProposal、ProjectGenerationProposal 创建、确认、失效、过期）。
  - canonical Task 状态变化（等待外部、待确认、暂停、终态）对 AIGC 项目页的失效通知：事件名与 taskId 类型对照 AAF-115 定型，不引入新轮询。
  - 补全关联：`conversationId` 进 `refs`；`assistantRunId`、`toolCallId` 等字符串进 `payload`。
  - 事件类型直接使用 `aigc.` 前缀，关联用语义字段名；通过现有 `AigcActivityEventService` 发布，AAF-119 落地后改为 `AigcEventPublisher`，调用点不变。
  - 事件只做失效通知，不推进 Task，不携带积分数值，payload 只放状态摘要。
  - **依赖**：#11319、#11327、#11328。
  - **排期**：不阻塞 AAF-119，也不阻塞 #11313；建议在 #11323 门禁前完成，schedule Sheet 与对话中的 Task 卡片依赖这些通知。
  - **覆盖**：AC-16、UI-AC-10、UI-AC-13 至 UI-AC-15 的断线与刷新体验。

### 切换与质量门禁

- ⏳ **#11313 执行阶段一项目管理闭环门禁** — developer-service、developer-webui
  - **启动记录**：2026-09-17 人类已解除动态验证限制；本任务可运行 test、lint、typecheck、build、SQL/Flyway 检查与 `pnpm check:affected`，旧阶段性禁跑约束不再适用于本门禁。
  - 独立验证 CATALOG 创建、ProjectGraph、对象结构操作、文本/文档编辑、专业媒体、Run、candidate、采用、进度、积分和交付。
  - 确认 `ProjectProgressSnapshot.currentTask=null` 时其他区段仍完整，刷新/断线后从服务端恢复权威状态。
  - 完成阶段一对应 AC/UI-AC 的 developer 测试，并运行 service/webui test、lint、typecheck、build、SQL 静态检查和 `pnpm check:affected`。
  - 未通过时不得启动 #11304 及后续 Assistant 接入。
  - **验证证据（2026-10-14，人工确认）**：service / webui 测试与 `pnpm check:affected` 全绿（lint、typecheck、build 随 `check:affected` 一并通过）。仍待完成：CATALOG 创建到交付的端到端独立验证、`currentTask=null` 与刷新/断线恢复的验证、阶段一 AC/UI-AC 的 developer 测试对照；这些完成前本任务保持 `⏳`。
  - **依赖**：#11301、#11302、#11303、#11306、#11309、#11310、#11311、#11312、#11324、#11325、#11326。
  - **覆盖**：AC-01、AC-03 至 AC-05、AC-08、AC-09、AC-11、AC-13、AC-15。

- [ ] **#11314 一次性切换并删除旧批量入口** — developer-service、developer-webui
  - 原子切换 UI、Assistant Tool 和动作权限，使新请求只进入结构化 Assistant Task 路径。
  - 拒绝旧入口新请求；盘点在途 root/child 执行并按策略完成或取消。
  - 结清每个在途执行的 reserved、settled、refunded，输出对账报告。
  - 删除旧服务特判、seed、binding、调用方和对应测试；保留历史执行引用只读审计。
  - 全仓搜索确认无可达旧入口、fallback、dual-write 或兼容适配器。
  - 添加旧入口不可达、在途完成/取消、积分结清和新入口回归测试。
  - **依赖**：#11323。
  - **覆盖**：AC-14。

- [ ] **#11315 完成 developer 自验证与 dev-log** — developer-service、developer-webui
  - 对最终代码运行受影响项目 test、lint、typecheck、build；修复全部失败。
  - 运行 `pnpm check:affected`，结果必须全绿；如 affected 识别异常，补跑明确的 service/webui targets。
  - 检查源码、seed、文档和 Tool metadata 中已删除名称与旧入口引用。
  - 在 `dev-log.md` 按 `## #113NN 标题`、`✅ 日期 — 负责人` 和精炼结果记录 #11301 至 #11328；#11329 后置实施时单独记录。
  - 汇总迁移 dry-run、单元测试、构建、旧入口搜索和积分对账证据，交付 architect 审查。
  - **依赖**：#11314、#11320。
  - **覆盖**：AC-01 至 AC-16。

- [ ] **#11316 执行架构与代码审查** — architect
  - 对照 requirement、design、任务、源码、迁移和 developer 证据审查 AC-01 至 AC-16。
  - 重点检查聚合边界、模块依赖、TaskPlan 单一编排、快照不可变、parentId 单一层级源、readiness 权威性、ProjectSchedule 不复制 Task、触发时重新授权、完成证据、权限隔离、积分真理源和旧入口删除。
  - 检查接口提供方/使用方同步、前后端类型对称、迁移可回滚和无兼容双路径。
  - 对 Major-3 作出模型决策：定义 ProjectSemanticDefinition 的废弃事实来源、版本与发布状态映射；在不存在权威来源前不得用固定 `deprecated=false` 冒充完整合同。
  - 对 Major-4 作出 provenance 决策：扩展 candidate 写入链携带 taskId/executionId/toolCallId，并明确人工 UI 路径使用空 Assistant 身份加人工 source，禁止继续把 Assistant candidate 记为 MANUAL。
  - 上述两项若需要改接口签名，由协调者在审查结论中列出所有调用方和回退任务，再进入 developer 修复。
  - 输出 `review.md`，记录 blocker/major/minor；blocker 必须归零后才能进入验收。
  - 协调者再次确认 `pnpm check:affected` 证据全绿。
  - **依赖**：#11315。
  - **覆盖**：AC-01 至 AC-16。

- [ ] **#11317 执行验收与故障恢复测试** — tester
  - 为 AC-01 至 AC-16 编写或更新独立验收测试，Java 使用 `*IT.java`/`*AcceptanceTest.java`，TS 使用 `*.accept.test.ts(x)` 或 Playwright。
  - 覆盖目录/Assistant 快照、并发修订、图无环、readiness、manual/auto、双入口、三类必达制品、音视频能力门、持久恢复、候选采用、语义权限、积分和旧入口不可达。
  - 按 ui-design.md 验收 UI-AC-01 至 UI-AC-15，重点验证批量生成调用 Assistant、current Task 非终态禁用、对象微调、候选不覆盖、定时 Sheet、多时间点、忙时延后、绑定对话状态、断线恢复和辅助技术可理解的禁用原因。
  - 增加服务重启、事件丢失、重复事件、Run 成功资源缺失、供应商失败退款、多候选暂停、在途取消、schedule 重复扫描和触发后回写中断场景。
  - 运行 `pnpm acceptance:affected`；需要跨模块确认时补跑 `pnpm acceptance`。
  - 输出 `test-report.md`，包含 AC-01 至 AC-16 覆盖矩阵、环境、命令、结果和缺陷链接。
  - **依赖**：#11316 且无未修复 blocker。
  - **覆盖**：AC-01 至 AC-16。

- [ ] **#11318 完成过程审计与质量门控** — qa、协调者
  - 核对 requirement/design/ui-design/tasks/skill、迁移、dev-log、review 和 test-report 完整且版本一致；skill 首期不增加 schedule 管理 Tool。
  - 核对 developer `pnpm check:affected` 与 tester `pnpm acceptance:affected` 证据有效。
  - 检查每条 AC 有设计、实现任务、测试和结果证据，未验收项不得标记完成。
  - 检查无跳过测试、静默降级、万能写入口、双状态源、用户 cron、第二 scheduler 或未授权自动化。
  - 质量门控必须满足 blocker=0 且 major≤2；不满足则按缺陷归属回退对应任务。
  - 通过后由协调者更新任务状态和交付记录；不自动 merge 或部署。
  - **依赖**：#11317。
  - **覆盖**：AC-01 至 AC-16。

## 依赖关系

```text
阶段一 CATALOG：#11301 ─┐
                          ├→ #11302 → #11303 → #11306 → #11309 → #11310
                 #11325 ─┘                         ├→ #11311 → #11324
                                                   ├→ #11312
                                                   └→ #11326
#11301 + #11302 + #11303 + #11306 + #11309 + #11310 + #11311 + #11312
+ #11324 + #11325 + #11326 → #11313 CATALOG 门禁

阶段二 CATALOG AI：#11313 → #11304 → #11305
                                   ├→ #11307
                                   ├→ #11308
#11325 + #11326 + #11304 → #11327
#11305 + #11307 + #11308 + #11327 → #11328

阶段三无蓝图：#11328 → #11321
#11321 + #11327 + #11328 + #11309 + #11311 → #11322 → #11323
  → #11314 单路径切换与旧入口删除

阶段四定时：#11323 + #11311 → #11319 → #11320

后置编辑器：#11323 + #11325 + #11326 → #11329（不阻塞核心门禁）

#11314 + #11320 → #11315 → #11316 → #11317 → #11318
```

额外约束：

- #11307 依赖 #11306，确保 candidate 写入前使用权威 readiness。
- #11309 属于阶段一类型化媒体闭环，不依赖 Assistant；#11322 依赖 #11308 后才接入 Assistant 外部等待。
- #11310 只实现确定性项目管理；auto、结构化 Assistant Task 和批量生成由 #11328/#11322 分阶段开放。
- #11325/#11326 优先于 AI 能力，未跑通已定义 CATALOG Blueprint 前不得实施 #11321。
- #11327/#11328 先在 CATALOG 项目验证项目上下文和 AI 生成内循环，#11321 只增加 Snapshot 来源能力。
- #11329 是后置编辑器，不进入 #11313、#11323 或核心交付依赖；不得反向修改运行时形成第二 Blueprint 合同。
- #11314 必须等待 #11323 全绿，且删除旧入口与切换新入口必须在同一发布窗口完成。
- #11313 未通过不得启动 Assistant 接入；本轮不运行门禁时保持未完成。
- #11319 只复用统一 scheduler 唤醒 due scanner，且必须等待 #11323；用户 schedule 不注册独立 cron 或 Runnable。
- #11320 只显示 ProjectScheduleView 和 linked canonical Task，不在前端或对话复制 Task 状态。
- #11317 启动前必须确认 #11316 无未修复 blocker，且 developer check 证据全绿。

## AC 覆盖矩阵

| AC | 主要实现任务 | developer 验证 | tester 验收 |
|---|---|---|---|
| AC-01 | #11301、#11302、#11325、#11326 | 类型化 Definition、快照哈希、CATALOG 原子物化 | 已定义目录来源创建 |
| AC-02 | #11304、#11305、#11321、#11322、#11328 | Assistant Proposal、统一校验与权限 | 无目录模板创建 |
| AC-03 | #11301、#11302、#11303、#11325、#11327 | revision、上下文文档选定版本、历史不可变 | 结构与文档版本修订 |
| AC-04 | #11301、#11303、#11325 | parentId、layer、语义、默认动作、payload 边界 | 图查询一致性 |
| AC-05 | #11306、#11327 | 对象与项目上下文 readiness、冻结输入 | 去重、依赖、范围 |
| AC-06 | #11322、#11328 | manual/auto、Proposal Approval 与 CAS | 自动边界和模式切换 |
| AC-07 | #11305、#11307、#11322、#11328 | Tool 权限、统一 Proposal 和入口路由 | UI/对话同一事实 |
| AC-08 | #11307、#11308、#11309、#11322、#11327、#11328 | 项目文档、对象和媒体 candidate | 文本/文档/图像闭环 |
| AC-09 | #11309、#11322 | 专业能力与 Assistant Tool gate | 音视频可用/不可用 |
| AC-10 | #11308、#11322、#11328 | event/scan 幂等恢复和 Proposal 重放 | 断线、重启和迟到事件 |
| AC-11 | #11306、#11307、#11327 | 对象/文档 candidate 输入与采用 CAS | 唯一/多候选路径 |
| AC-12 | #11304、#11305、#11321、#11327、#11328 | 语义版本、PromptManifest 和权限交集 | scope 隔离与 fail closed |
| AC-13 | #11311、#11324 | 账本聚合与投影重建；#11324 细化对象级聚合 | 实际成本、退款与对象级明细 |
| AC-14 | #11323、#11314 | 旧入口搜索和结清 | 不可达与在途处置 |
| AC-15 | #11303、#11312 | 精确版本与 evidenceHash | manifest 可重现 |
| AC-16 | #11319、#11320 | RUN_ONCE、claim/fence、幂等、忙时延后、状态投影 | 多时间点、重启补跑、项目页/对话同一 Task |

## 每个开发任务的完成定义

一个 developer 任务只有同时满足以下条件才可勾选完成：

- 实现满足对应 AC 和 design.md 不变量；
- 新增或修改的行为有对应 `*Test.java` 或 `*.test.ts(x)`/`*.spec.ts(x)`；
- 相关项目 test、lint、typecheck 或 build 通过；
- 无禁用测试、静默 fallback、双写、万能接口或未解释 TODO；
- 接口提供方和所有使用方类型同步；
- 已在 `dev-log.md` 记录结果和关键决策；
- 变更可由 projectId、projectScheduleId、taskId、toolCallId、runId、candidateVersionId 和 creditTxId 追踪。

## 故事完成定义

AAF-113 只有在以下条件全部满足时完成：

- AC-01 至 AC-16 和 UI-AC-01 至 UI-AC-15 均有实现和 tester 验收证据；
- 文本、文档、图像从项目需求到 candidate、等待、恢复、采用和进度展示真实跑通；
- 音频/视频按专业 Tool 可用性进入同一合同，不存在第二模型；
- AigcProjectConfigSnapshot 不可变，ProjectRevision、projectInitialization 和项目级上下文文档选定版本真理源正确；
- parentId、relation layer、对象语义、三态成熟度和 manifest 证据可重现；
- canonical TaskPlan、ObjectReadiness 和 AWAITING_EXTERNAL 在断线/重启后可恢复；
- 实际积分来自账本且关联链完整；
- 一个或多个 RUN_ONCE 时间点可幂等创建现有 Assistant Task，项目忙时延后，项目页与绑定对话展示同一 Task 状态；
- 不存在用户 cron、recurrence、第二 scheduler、ProjectScheduleFire 或定时确定性动作路径；
- 旧批量入口不可达、在途执行和积分已结清、相关特判与配置已删除；
- `pnpm check:affected` 与 `pnpm acceptance:affected` 全绿；
- `dev-log.md`、`review.md` 和 `test-report.md` 完整；
- 质量门控满足 blocker=0 且 major≤2，并由协调者确认。
