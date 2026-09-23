# AAF-113 场景示例实现验证

**验证日期**: 2026-09-22  
**验证人**: Kiro

## 场景来源

design.md 第192-280行描述了完整的贯穿示例：

> **用户小李通过 UI 选择"图文推广套餐" CATALOG Blueprint 创建一个项目**

该示例涵盖了从 CONFIGURING 到 DELIVERING 的完整六阶段流程。

---

## 场景详细步骤与实现验证

### 阶段一：CONFIGURING（配置中）

**场景描述**（design.md:214）:
> 小李填写项目名"618大促推广"和简介"面向年轻女性用户，突出五折力度和限时紧迫感"，选择"图文推广套餐"、渠道、品牌档案及带角色的产品说明文档。

**实现验证**: ✅ **可实现**

**证据**:
- `AigcProject` 实体支持 name、description 字段（v7__aigc_schema.sql）
- `AigcProjectChannelRef`、`AigcProjectProfileRef`、`AigcProjectDocumentRef` 关联表已存在
- `blueprintCode`、`blueprintVersion` 字段已定义
- 前端项目创建表单已实现（apps/webui/src/features/aigc/components/project）

**已完成任务**: #11301（BlueprintSnapshot v2 数据合同）、#11326（CATALOG Blueprint 初始化）

---

### 阶段二：MATERIALIZING（生成结构中）

**场景描述**（design.md:232）:
> 系统从"图文推广套餐"读取 `projectInitialization`（项目简介、产品说明、Brief 项目文档策略）以及 `concept`/`package`/`image`×3/`video`×1/`copy` 生产槽位；渠道约束决定视频激活和媒体规格，Action 现场解析已发布执行绑定。

**实现验证**: ✅ **可实现**

**证据**:
- `AigcConfigurationService.resolve()` 方法已实现（#11325）
- `BlueprintSnapshotValidator` 校验器已完整实现（#11302）
- `AigcProjectBlueprintSnapshotPayload` 类型化载荷已定义
- `projectSpecification`、`slotTemplates`、`relationSpecs`、`actions`、`deliverableSets`、`processPolicy` 字段结构完整
- 渠道约束解析、执行绑定查询逻辑已实现

**已完成任务**: #11302（校验与不可变基线）、#11325（类型化 BlueprintDefinition）

---

### 阶段三：MATERIALIZED（结构已就位）

**场景描述**（design.md:252）:
> 事务创建 `concept`、`package`、`image.01/02/03`、按条件激活的 `video.01` 和 `copy` 等生产对象，全部初始为 EMPTY；同时写入品牌、渠道、外部资料等 Ref。

**实现验证**: ✅ **可实现**

**证据**:
- `AigcProjectMaterializer.materialize()` 方法已实现（#11326）
- 原子物化事务：冻结快照 → 创建 AigcProject → 批量生成 AigcProjectObject → 写入关联 Ref
- `AigcProjectObject` 字段完整：objectType、stableKey、blueprintTemplateKey、instanceNo、contractRole、semanticRole、outputContract、parentId、status、defaultActionKey、payload
- 初始状态为 EMPTY 的逻辑已实现
- `AigcProjectObjectMaturityProjector` 状态投影器已实现（#11303）

**已完成任务**: #11303（层级、关系、对象成熟度）、#11326（原子物化）

---

### 阶段四：CREATING（创作中）

**场景描述**（design.md:268）:
> 物化提交后，系统按已确认计划生成 Brief DocumentVersion candidate；小李确认后，Brief DocumentRef 的 `selectedDocumentVersionId` 指向该版本。随后 `concept`、`image.*`、`video.01` 和 `copy` 按 readiness 生成各自 candidate；媒体继续形成 ExecutionRun + MediaVersion + ObjectVersion，重新生成产生新 candidate，不覆盖已采用版本。

**实现验证**: ✅ **可实现**

**证据**:

#### 4.1 Brief 文档生成
- `AigcProjectDocumentRef` + `selectedDocumentVersionId` 字段已存在
- `RecordProjectDocumentCandidateToolHandler` 已实现（#11307）
- DocumentVersion 生成与 candidate 治理已完整

#### 4.2 ObjectReadiness 计算
- `AigcProjectReadinessService` 已实现 7 态 readiness（#11306）:
  - SATISFIED（已采用对象）
  - READY（准备就绪）
  - BLOCKED_BY_DEPENDENCY（依赖未满足）
  - BLOCKED_BY_POLICY（策略阻塞）
  - ACTIVE_RUN（运行中）
  - PENDING_CANDIDATE（待决候选）
  - AWAITING_CONFIRMATION（待确认）
- 依赖计算基于 `AigcProjectRelation` 的 `dependencyObjectIds`

#### 4.3 文本 candidate
- `RecordProjectTextCandidateToolHandler` 已实现（#11307）
- `AigcObjectVersion` candidate 治理已完整
- 不自动覆盖 adoptedVersionId 的逻辑已实现

#### 4.4 媒体 candidate
- `AigcProjectMediaExecutionService` 已实现类型化媒体执行（#11309）
- ExecutionRun + MediaVersion + ObjectVersion 闭环完整
- 重新生成产生新 candidate 的逻辑已实现
- 图像执行绑定与 Tool 适配已完成

**已完成任务**: #11306（ObjectReadiness）、#11307（文本与文档 candidate Tool）、#11309（媒体执行与 candidate 闭环）

---

### 阶段五：REVIEWING（审核中）

**场景描述**（design.md:274）:
> 小李确认必需项目上下文和全部必需生产对象后点击"提交审核"，系统创建 review 记录并冻结送审范围；审核角色查看精确版本后通过。

**实现验证**: ✅ **可实现**（既有能力）

**证据**:
- `AigcWork`、`Review` 实体已存在（AAF-112 既有能力）
- 精确版本引用机制已实现
- 审核流程与权限控制已完整

**相关任务**: 本阶段复用 AAF-112 既有审核链，AAF-113 无需新增实现

---

### 阶段六：DELIVERING（交付中）

**场景描述**（design.md:280）:
> 系统按 `package` 节点（交付内容包）精确引用其下 `image.01/02/03`、`video.01`、`copy` 当前 adopted 的 `ObjectVersion` ID，冻结成一份不可变 `manifest`，计算 `evidenceHash`。项目状态变为 `DELIVERING`。小李在项目头部看到"本项目实际消耗 128 积分"（由 `AigcProjectProgressEvidenceAdapter` 按全部关联 `CreditTransaction` 结算流水聚合得出，已扣除 `image.02` 第一次生成失败的自动退款）。

**实现验证**: ✅ **可实现**

**证据**:

#### 6.1 交付 manifest 与 evidenceHash
- `AigcWorkPublication`、`AigcWork` 实体已存在
- manifest 精确版本引用机制已实现（#11312）
- `evidenceHash` 计算逻辑已实现
- 历史快照不可变性已保证

#### 6.2 实际积分展示
- `AigcProjectProgressSnapshot` 投影已实现（#11311）
- `AigcProjectProgressEvidencePort` 接口已定义
- `AigcProjectProgressEvidenceAdapter` 适配器已实现
- 积分聚合逻辑：
  ```java
  // 按全部关联 CreditTransaction 聚合
  settled - refunded = 实际消耗
  ```
- 自动退款机制已存在（`AigcTaskService.failTask` 的 `creditGuard.refund`）
- 前端项目头部积分展示已实现（#11324）

**已完成任务**: #11311（ProjectProgressSnapshot 与实际积分关联）、#11312（交付 manifest 与 evidenceHash）、#11324（对象与项目累计实际积分实时展示）

---

## 验收标准（requirement.md AC）映射

| 场景阶段 | 对应 AC | 实现状态 |
|---------|---------|---------|
| CONFIGURING → MATERIALIZING | AC-01 目录来源形成不可变项目基线 | ✅ #11301, #11302, #11325, #11326 |
| MATERIALIZED | AC-04 ProjectGraph 层级、关系和制品语义一致 | ✅ #11303 |
| CREATING（readiness） | AC-05 服务端 readiness 阻止重复和越序生成 | ✅ #11306 |
| CREATING（文本/文档） | AC-08 项目文档、对象和媒体 candidate | ✅ #11307, #11327 |
| CREATING（媒体） | AC-08, AC-09 图像/音视频统一底层合同 | ✅ #11309 |
| DELIVERING（积分） | AC-13 项目进度展示实际积分 | ✅ #11311, #11324 |
| DELIVERING（manifest） | AC-15 精确版本形成可重现交付证据 | ✅ #11312 |

---

## 扩展场景：Assistant 生成来源

requirement.md 还定义了无目录 Blueprint 的场景（AC-02）:

**场景描述**（requirement.md:353-359）:
> Given 当前没有适用的目录 Blueprint  
> And Assistant 已澄清项目目标、交付物、结构和依赖  
> When 用户确认结构提案或存在覆盖该范围的明确结构自主授权  
> Then 系统校验并持久化 ASSISTANT_GENERATED BlueprintSnapshot  
> And 它与目录来源使用同一 ProjectGraph、readiness、TaskPlan、candidate 和交付链

**实现验证**: ✅ **可实现**

**证据**:
- `AssistantBlueprintProposal` 实体已完整实现（#11321）
- `SubmitAssistantBlueprintProposalToolHandler` Tool 已实现
- `BlueprintSnapshotNormalizer` 归一化器已实现
- ASSISTANT_GENERATED 与 CATALOG 共享同一 `AigcProjectMaterializer.materialize()` 物化链
- 统一校验器 `BlueprintSnapshotValidator` 处理两种来源
- 用户确认与授权机制已完整

**已完成任务**: #11321（ASSISTANT_GENERATED BlueprintSnapshot Proposal）、#11302（统一校验）

---

## 扩展场景：定时触发

requirement.md AC-16 定义的定时任务场景:

**场景描述**（requirement.md:503-517）:
> Given 用户在可写项目中设置同一提示词的一个或多个未来时间点和 IANA 时区  
> When 系统保存设置  
> Then 每个时间点形成一条独立 ProjectSchedule  
> ...  
> When 某条记录到期且项目没有未终结 current Task  
> Then 统一 scheduler 以 scheduleId 和 executeAt 幂等创建一个 canonical Assistant Task

**实现验证**: ✅ **可实现**

**证据**:
- `aigc_project_schedule` 表已定义（v7__aigc_schema.sql）
- `AigcProjectSchedule` 实体已完整实现（#11319）
- `ProjectScheduleScanActionExecutor` 调度器已实现
- `FOR UPDATE SKIP LOCKED` 原子认领逻辑已实现
- 幂等创建 canonical Task 逻辑已实现
- 忙时延后到 DEFERRED 状态的逻辑已实现
- 前端 `ProjectScheduleSheet` UI 已实现（#11320）
- 项目页与对话状态同步机制已实现

**已完成任务**: #11319（ProjectSchedule 与统一 scheduler 触发）、#11320（定时任务 Sheet 与绑定对话状态）

---

## 验证结论

### ✅ 场景完整性

**主场景（小李的图文推广项目）**: 100% 可实现

- 阶段一至阶段六全部关键步骤均有对应实现
- 23个源码完成任务覆盖了完整的 CATALOG Blueprint 创作闭环
- 从项目创建、结构物化、内容生成、候选治理到交付证据的完整链路已打通

**扩展场景**: 100% 可实现

- ASSISTANT_GENERATED 来源已实现（#11321）
- 一次性定时触发已实现（#11319, #11320）
- manual/auto 协作模式已实现（#11328）

### 🚧 待验证项（受人类约束阻塞）

tasks.md 第35行约束：
> 本轮按人类明确指令只实现阶段一源码，测试仅调整因签名/依赖变更冲突的现有测试，不新增测试文件，不运行 test/check/lint/build

**影响**:
1. **质量门禁未执行**：#11313（阶段一门禁）、#11323（阶段三门禁）
2. **集成测试未运行**：端到端真实跑通未验证
3. **验收测试未启动**：#11317（tester 验收）

**当前状态**: 源码静态分析显示场景可实现，但缺少动态验证证据

### 🔍 已知设计缺口

#### 缺口A: Publication stale 标记（#11312）

**场景影响**: 阶段六 DELIVERING

**问题**: `AigcWorkPublication` 实体没有 stale 状态字段，采用变化后依赖旧 manifest 的已发布 Publication 无法被标记为过期

**当前方案**: tasks.md 记录为已知限制，等待架构决策

#### 缺口B: ProjectContext 注入链路（#11304）

**场景影响**: Assistant 对话协作时的项目上下文

**问题**: `ContentStudioProjectContextProvider.load()` 当前无任何调用方，项目事实快照无法注入实际 Assistant 对话

**外部依赖**: 等待 AAF-115 新增 `DOMAIN_CONTEXT` Prompt 层

**当前方案**: Provider 已实现，等待 AAF-115 接线

### 📋 未覆盖场景（非目标）

requirement.md 第538-546行明确排除：
- 通用 Project 物理落地
- 复杂 Gate 和阶段预算
- 可视化 Blueprint 编辑器（后置 #11329）
- 自动采用、自动送审、自动发布
- 任意 cron、定时确定性动作

---

## 推荐验证路径

### 路径一: 解除约束后全链路验证（推荐）

**步骤**:
1. 解除"不运行门禁"约束
2. 执行 #11313 阶段一门禁
3. 执行 #11323 阶段三门禁
4. 运行端到端集成测试
5. 执行 #11317 验收测试

**预期结果**: 小李的图文推广项目完整场景真实跑通

### 路径二: 部分验证（当前可行）

**步骤**:
1. 手动创建图文推广项目（CONFIGURING → MATERIALIZING → MATERIALIZED）
2. 查询数据库验证：
   - `aigc_project_config_snapshot` 快照已冻结
   - `aigc_project_object` 对象节点已创建（concept/package/image×3/video/copy）
   - `aigc_project_relation` 关系已物化
   - parentId 层级树正确
3. 手动调用 readiness API 验证 7 态计算
4. 手动调用 candidate Tool 生成文本/文档/图像
5. 查询积分聚合投影验证实际消耗计算

**局限性**: 缺少自动化测试覆盖，无法验证断线恢复、并发冲突、重复事件等边界场景

---

## 附录：场景对应源码文件

### 核心服务
- **配置解析**: `AigcConfigurationService.java` (resolve/blueprintSnapshot/channelSnapshot)
- **校验器**: `BlueprintSnapshotValidator.java` (validate/canonicalHash)
- **物化器**: `AigcProjectMaterializer.java` (materialize/freezeSnapshot)
- **Readiness**: `AigcProjectReadinessService.java` (7态计算)
- **媒体执行**: `AigcProjectMediaExecutionService.java` (图像/音视频)
- **积分聚合**: `AigcProjectProgressEvidenceAdapter.java` (settled - refunded)

### Assistant Tools
- **Blueprint Proposal**: `SubmitAssistantBlueprintProposalToolHandler.java`
- **文本 candidate**: `RecordProjectTextCandidateToolHandler.java`
- **文档 candidate**: `RecordProjectDocumentCandidateToolHandler.java`
- **语义检查**: `InspectProjectSemanticsToolHandler.java`
- **生成动作**: `SubmitApprovedGenerationActionToolHandler.java`

### 前端组件
- **项目创建**: `apps/webui/src/features/aigc/components/project/ProjectCreationForm.tsx`
- **项目详情**: `apps/webui/src/app/studio/projects/[id]/page.tsx`
- **进度展示**: `apps/webui/src/features/aigc/components/project/ProjectProgressPanel.tsx`
- **定时任务**: `apps/webui/src/features/aigc/components/project/ProjectScheduleSheet.tsx`

---

**验证完成日期**: 2026-09-22  
**下次更新**: 门禁执行后补充动态验证结果
