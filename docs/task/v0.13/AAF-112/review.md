---
level: Practice
layer: Product
purpose: 记录 AAF-112 #11207–#11217 的静态架构与代码审查结论
status: draft
version: 1.0.0
date: 2026-10-14
author: architect
---

# AAF-112 静态架构与代码审查

## 审查范围与方法

- 审查任务：#11207–#11217，作为 #11206 的静态审查部分。
- 后端范围：`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/` 下 configuration、project、execution、task、work、event 等实现，并静态核对 `db/migration/v7__aigc_schema.sql` 的相关约束。
- 前端范围：`apps/webui/src/features/studio/`、`apps/webui/src/app/studio/projects/`，并按调用关系只读核对 `apps/webui/src/lib/api/rest/ai/aigc/` 的类型与 Query mutation 接线。
- 对照依据：`requirement.md`、`tasks.md`、`dev-log.md`、`content-studio-tech.md`、`content-studio-design.md`、`architecture-constraints.md`、`code-review-standard.md` 与 coding-standards skill。
- 方法：仅阅读、符号检索和 AST 静态搜索；**未运行任何 test、lint、typecheck、build、check、mvn 或 pnpm 命令**。`tasks.md` 记录的人工全绿结论未在本次审查中复验。
- 前置状态：`dev-log.md` 已记录 #11207–#11217；因本次被明确限制为静态审查，运行时行为和测试结果均不据此判定为通过。

## 审查结论

当前实现存在会破坏配置复现、强幂等、reservation 单向状态机、条件首次采用及 manifest 审计一致性的 blocker，不满足 AAF-112 完成定义，结论为**不通过，需修复后重审**。

## Blocker 问题

| ID | 文件与行号 | 问题 | 依据与影响 | 修复要求 | 状态 |
|---|---|---|---|---|---|
| B-01 | `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/configuration/api/AigcConfigurationResolveCommand.java:6-18`；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/configuration/api/AigcResolvedConfiguration.java:6-35`；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/project/api/AigcProjectMaterializeCommand.java:19-72`；`apps/webui/src/features/studio/content/new-project-schema.ts:14-61`；`apps/webui/src/features/studio/content/NewProjectLauncher.tsx:1245-1349` | 动态创建契约未落地 `budgetTier`、`qualityTier`；创建页没有预算档、质量档和槽位数量输入，并在提交时固定 `slotOverrides: []`。`NewProjectLauncher.tsx:349-365` 还把 `actionSpec` 当 `Record<string, unknown>` 防御性解析，未形成端到端强类型配置契约。 | `requirement.md`“项目与交付合同”及首条 AC 要求选择并固化预算质量档和槽位数量；`tasks.md` #11207/#11208 明确声明这些输入已接入；`content-studio-tech.md` 要求按 `defaultCount → productionMode/budgetTier/qualityTier → slotOverrides → ChannelSpec` 解析并写入快照。当前接口签名无法表达这些输入，核心 AC 必然缺失。 | 一次性补齐后端命令、解析结果、快照、前端类型和表单；移除原始 Map 配置读取路径，确保同一规范化创建请求可复现。 | 未修复 |
| B-02 | `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/execution/service/AigcExecutionSubmissionService.java:44-82,261-282` | Submission 的 `requestHash` 未包含 `command.approvedTarget()`；该值只在 `normalize()` 中写入 `approvedGenerationTarget` payload。同一 `(projectId,idempotencyKey)` 若仅变更已批准目标，哈希仍相同，`ON CONFLICT` 会把它当作合法重放并继续使用首个 payload，而不是返回同键异参冲突。 | `requirement.md`“同键异参必须冲突”；`content-studio-tech.md` Execution API 要求 Submission 保存完整规范化请求哈希，避免重复或越权副作用。已批准目标包含冻结授权与输入证据，遗漏会破坏幂等和授权完整性，属于安全/业务一致性 blocker。 | 将完整规范化业务请求（含 approved target 证据）纳入唯一哈希，并保持 record/recovery 使用同一规范化函数。 | 未修复 |
| B-03 | `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectService.java:1682-1735`（尤其 `1709-1716`） | `bindExecution` 允许把已 `RELEASED + ROOT_TERMINAL` 的 reservation 原地恢复成 `BOUND`。这使终态释放不是单向事实，并让同一 submission/root 在终态后重新获得活动占用。 | `content-studio-tech.md` Project API 明确规定只有 `PREPARED + same submission + root IS NULL` 可 CAS 到 BOUND；RELEASED 的同参数重放必须返回既有 RELEASED，其他 bind 均冲突。代码审查规范“资源申请 vs 释放”要求对称，终态资源不得被无新 reservation 地复活。 | 删除 RELEASED→BOUND 逆迁移；局部重试应按设计建立新的可追溯执行事实或修订设计并先审核，不得复活终态 reservation。 | 未修复 |
| B-04 | `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectDeliveryService.java:103-190`；全后端静态搜索未发现 `AUTO_ADOPT_IF_EMPTY` 分支 | 执行候选登记后只创建 candidate、更新对象成熟度并进入 ADOPTING；没有从不可变项目快照读取 `AUTO_ADOPT_IF_EMPTY`，也没有 `adopted_version_id IS NULL` 的独立 CAS 首次采用实现。 | `requirement.md`“AUTO_ADOPT_IF_EMPTY 仅能从不可变项目快照读取”；`tasks.md` #11212 声明已完成条件首次采用；`content-studio-tech.md`“候选采用与确认替换”要求并发候选仅一个条件采用成功。当前实现缺失该核心领域事实。 | 在 Project 聚合内读取固定快照策略，登记 candidate 后执行仅空指针可成功的 CAS，并单独记录 revision/event；失败只保留 candidate。 | 未修复 |
| B-05 | `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectDeliveryService.java:439-509`（尤其 `472-477`）；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectDeliveryService.java:1092-1102` | freeze 先用当前 revision 验证客户端的 `expectedEvidenceHash`，随后以 `currentGraphRevision + 1` 再算 `finalEvaluation`，并把新的 revision/hash 写入 manifest。由于 hash 明确包含 graphRevision，manifest 固化的证据哈希不是用户刚确认并提交的 evidenceHash。 | `requirement.md` 送审 AC 与 `content-studio-tech.md` 要求 freeze 以“同一 optional scope、graph revision、evidenceHash、project version”重算并冻结；当前实现破坏确认值与不可变 manifest 的审计同一性。 | manifest 必须冻结客户端确认且事务内复核的同一 revision/hash；freeze 自身的 revision 记录不得改写被审核内容的证据身份。 | 未修复 |
| B-06 | `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectMaterializer.java:68-73`；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectService.java:175-181`；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectDeliveryService.java:82-88`；`apps/service/aaf-api/src/main/resources/db/migration/v7__aigc_schema.sql:520-560` | 明确保留 `LEGACY_PLANNED_PHASE_PLACEHOLDER`，以固定 `BUILD/DELIVER` 填充已声明“业务语义上移除”的 `planned_phase NOT NULL` 列。这是旧 schema + 固定占位写入的临时 shim，并形成无业务含义的持续双写负担。 | coding-standards“硬约束 5 不加兼容层”禁止 fallback/legacy adapter/temporary shim/TODO 占位；`content-studio-tech.md` 要求一次性迁移且不保留旧新双路径。硬约束违反即 blocker。 | 通过经审核的一次性 schema/Entity 收敛删除该列和全部占位写入，或先修订权威设计；不得继续保留 `LEGACY_*` 写路径。 | 未修复 |

## Major 问题

| ID | 文件与行号 | 问题 | 依据与影响 | 修复要求 | 状态 |
|---|---|---|---|---|---|
| M-01 | `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/project/domain/AigcProjectObject.java:34-38`；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectService.java:1443-1474`；`apps/service/aaf-api/src/main/resources/db/migration/v7__aigc_schema.sql:520-560`；`apps/webui/src/lib/api/rest/ai/aigc/project.ts:135-160` | 自定义对象也被分配递增 `instanceNo`，Entity/SQL/TS 均要求非空；技术设计要求非模板自定义对象保存 `blueprintTemplateKey=null、instanceNo=null`。 | `content-studio-tech.md` Project API 的 appendObject 契约及“父内容包、子交付槽位”明确区分模板实例号与自定义 stableKey。当前模型把模板实例身份语义扩散到自定义对象，接口与数据模型不一致。 | 将 `instance_no` 改为仅模板实例必填，并用约束保证 `blueprint_template_key` 与 `instance_no` 同空/同非空；自定义 stableKey 使用独立不可复用序列策略。 | 未修复 |
| M-02 | `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/configuration/service/AigcConfigurationService.java:31,393-411`；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectService.java:46`；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/project/service/AigcProjectDeliveryService.java:30`；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/work/service/AigcWorkService.java:28` | `configuration` 直接注入 `execution.repository.AigcExecutionBindingRepository` 并读取 execution Entity；project/work/task 又直接依赖顶层 `event.service.AigcActivityEventService`。同时代码新增了设计八子模块之外的顶层 `event` 业务子模块。 | `architecture-constraints.md` 禁止业务包直接访问他包 entity/repository；`content-studio-tech.md`“目标包与职责/依赖方向”要求八个核心子模块、同步依赖只指向对方 `.api`，且 configuration 无 AIGC 子模块依赖。属于明确分层/边界 major。 | 由 execution 暴露只读 binding API；Activity 能力应按经审核的归属提供 `.api` 端口，禁止跨子模块注入 service/repository。 | 未修复 |
| M-03 | `apps/webui/src/app/studio/projects/[id]/page.tsx:20-66,238-255`；`apps/webui/src/features/studio/content/ProjectAssistantControl.tsx:38-189`；`apps/webui/src/features/studio/content/ObjectDetailPanel.tsx:143-157`；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/project/controller/AigcProjectController.java:176-194` | AAF-112 工作台已查询/绑定项目 Assistant、打开 Chatter，并提供“在对话中修改”入口；后端同时暴露 conversation-context 绑定接口。 | `requirement.md` 非目标明确“不实现 AAF-113 的项目对话”；`tasks.md` #11217 明确“AAF-113 阶段门继续生效，不在本任务接入项目对话”。这是任务外能力和阶段门越界，扩大了写入与权限审查面。 | 从 AAF-112 交付面移除或以未启用阶段门完全隔离这些入口；待 AAF-113 依其需求、设计与验收独立交付。 | 未修复 |
| M-04 | `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/execution/event/AigcExecutionRunDispatchListener.java:20-29`；`apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/execution/event/AigcProjectArchivedListener.java:27-40`；同范围另有多处 `fallbackExecution = true` | 关键 after-commit 监听器启用 `fallbackExecution=true`。当发布点没有事务时，Spring 会立即执行监听器；Run 派发、归档取消、候选/Task 终态传播因此存在绕过“事务提交后再产生副作用”的 fail-open 路径。 | `content-studio-tech.md` 要求 Run 落库且 reservation BOUND 后才派发，事件状态变更与通知必须对称；代码审查规范“事务提交/回滚”“成功/错误路径”要求一致。代码自身 `AigcProjectMaterializer` 注释也承认无事务 fallback 会破坏该不变量。 | 对关键副作用取消 fallbackExecution，强制所有发布点位于事务内；需要无事务入口时显式开启事务或写 durable outbox，不得静默立即执行。 | 未修复 |

## Minor 问题

未发现独立 minor；可读性问题未从 blocker/major 中拆分重复计数。

## 对称性检查

| 检查项 | 静态结论 |
|---|---|
| 生产者 vs 消费者 | Activity SSE 有持久事件表、游标和前端重连；域事件跨进程崩溃窗口是否全部可恢复，未能静态确认。 |
| 创建 vs 删除 | ProjectObject 删除前检查子对象、版本、关系与 reservation 历史；自定义实例身份契约见 M-01。 |
| 加密 vs 解密 | 本范围无成对加解密逻辑，不适用。 |
| 事务开启 vs 提交/回滚 | 核心写服务使用事务；`fallbackExecution=true` 的 fail-open 路径见 M-04。 |
| 监听器注册 vs 注销 | Spring 托管监听器不需手工注销；前端 SSE 在 effect cleanup 中 `abort()`。 |
| 资源申请 vs 释放 | PREPARED/BOUND 有终态释放，但 RELEASED 可逆向重绑，见 B-03。 |
| 状态变更 vs 通知 | 主要 Project/Review/Work/Publication 状态均发布 Activity 或领域事件；跨崩溃窗口的完整性未能静态确认。 |
| 认证 vs 鉴权 | REST authority、BaseCrud scope、owner/org/workspace/project 校验在抽查入口存在；无法仅靠静态抽查证明所有间接内部调用 100% 对称，标记为未能静态确认。 |
| 成功路径 vs 错误路径 | Submission recovery、派发失败终态化和 reservation 释放存在；供应商已经受理后的 exactly-once/退款补偿未能静态确认。 |
| 入队 vs 出队 | Submission 与 Task 均有持久恢复结构；真实队列、供应商回调和进程崩溃组合未执行验证，未能静态确认。 |
| 缓存写入 vs 失效 | 前端 Activity 和 mutation 对 project/graph/versions/reviews/work/execution 等 Query 进行失效；未发现把这些服务端事实复制进 Zustand。 |
| 前端乐观更新 vs 回滚 | 审查范围未使用服务端事实的 Zustand/乐观副本；采用/审核等 mutation 在 settled/error 后重新失效权威 Query。 |

## 静态确认与限制

- 已静态确认：ProjectObject stableKey 与模板实例组合唯一索引存在；candidate/adopt、Review、Work、Publication 是分离事实；采用命令携带 expectedAdoptedVersionId；Work 固定 manifest；完成策略从当前项目配置快照读取；归档后主要写入口拒绝；Studio 的 ProjectGraph/Run/Review/Work 等服务端状态使用 TanStack Query，项目 Zustand 仅保存 view/focus/viewport/collapse/layer/zoom；审查范围未发现 TypeScript `any` 或 `@ts-ignore`；未发现 `/api/content/**` 路径和 `uiStage` 第二状态。
- 未能静态确认：数据库真实部署状态是否与当前 SQL 一致；测试覆盖与测试结果；并发事务隔离下的全部竞态；供应商侧 exactly-once、积分扣费/退款；域事件在提交后进程立即崩溃时的恢复完整性；所有特殊命令的动态权限策略结果。

## 汇总

初审：`blocker=6 major=4 minor=0`。

复核更新（2026-10-14）：对照 AAF-113 的设计调整与代码重新核实后，修正如下。

| 编号 | 原级别 | 复核结论 | 新级别 | 处理 |
|---|---|---|---|---|
| B-01 | blocker | 撤销。`budgetTier/qualityTier` 已被 AAF-113 删除；`slotOverrides` 为已知未启用能力（AAF-113 design.md:294-295） | minor（文档滞后） | 已改 `requirement.md`、`tasks.md` #11207；`content-studio-tech.md` 的 `budgetTier` 描述待同步 |
| B-02 | blocker | 成立：`requestHash` 未含 `approvedTarget`；暴露面取决于调用方幂等键生成，未核实 | major | 并入哈希 |
| B-03 | blocker | 成立：`bindExecution` 的 `RELEASED→BOUND` 分支不在设计中 | blocker | 删除该分支 |
| B-04 | blocker | 成立：代码无实现，AAF-113 也未取代 | blocker（待决策） | 删规格则归零，补实现则修复 |
| B-05 | blocker | 实现自洽（冻结会推进 `graphRevision`，哈希含 revision，故按冻结后 revision 计算）；与规格措辞不一致 | minor | 改规格措辞 |
| B-06 | blocker | 有记录的例外（AAF-113 design.md:905），但违反"未发布、SQL 直接修改" | blocker（待决策） | 删列则归零；接受例外则降 major |
| M-01 | major | 复核：代码借用 `instance_no` 作为自定义对象的永不复用序号以生成 `custom.<type>.NN` 稳定键，功能无误，仅与规格字段含义不一致；前端仅在有 `blueprintTemplateKey` 时显示实例号。改规格不改代码（`content-studio-tech.md`、`content-studio-design.md` 已同步） | minor（规格措辞） | 已改规格 |
| M-02 | major | 拆为两处。第一处（`configuration` 直接注入 execution 的 repository）已改：execution 新增 `AigcExecutionBindingApi`（`.api` 只读查询）及实现 `AigcExecutionBindingQueryService`，`AigcConfigurationService` 改依赖该接口，`AigcConfigurationServiceTest` 同步改为 mock 该接口。第二处（多个子模块直接依赖顶层 `event.service.AigcActivityEventService`）由 AAF-119 #11905 解决，在此之前仍记 major | major（剩第二处） | 第一处已改，未编译 |
| M-03 | major | 撤销。项目对话入口是 AAF-113 交付（AAF-113 tasks.md:326），归属问题 | minor（文档措辞） | 改 AAF-112 requirement 非目标与 #11217 描述 |
| M-04 | major | 保留 | major | 未复核 |

复核后计数：`blocker=3 major=4 minor=3`（B-03、B-04、B-06 为 blocker；B-02、M-01、M-02、M-04 为 major；B-01、B-05、M-03 为 minor）。质量门仍为不通过。

通过路径：B-03 修复；B-04、B-06 决策后归零；major 需降到 2 个以内（建议修 B-02 与 M-04，M-01、M-02 视为边界整改项登记后续）。

## 整改记录（2026-10-14，未编译、未运行测试）

| 编号 | 处理 | 位置 |
|---|---|---|
| B-03 | 已改：删除 `RELEASED→BOUND` 分支，只有 `PREPARED` 且未绑定 root 的 reservation 可绑定，其余抛"执行 reservation 不能绑定" | `AigcProjectService.bindExecution` |
| B-02 | 已改：`requestHash` 的业务 Map 加入 `approvedGenerationTarget`（缺省为空 Map） | `AigcExecutionSubmissionService.record` |
| M-04 | 部分已改：去掉 `AigcExecutionRunDispatchListener`、`AigcProjectArchivedListener` 的 `fallbackExecution`（发布点均在 `@Transactional` 入口内，dispatch 另有恢复扫描兜底）。其余 7 个监听器类（10 个注解）经核对发布点均在事务内（`AigcTaskExecutor` 全部位于 `resumeIntent` 的 `@Async + REQUIRES_NEW` 之下；`AigcTaskService` 完成/失败为 `REQUIRES_NEW`；候选相关发布方在 `REQUIRES_NEW` 或 `TransactionTemplate` 内；`AigcToolActionExecutor` 依赖调用方 `dispatchDeferred` 的事务，`AigcWorkService` 三处发布点的事务标注未读取），保留 `fallbackExecution`：它们承载异步生成主链，去掉后一旦出现无事务发布点，事件会被静默丢弃且无恢复扫描兜底。改为新增 `AigcEventFallbackGuardAspect`：监听器入口检测无事务时记录 ERROR，不改变执行行为 | `execution/event/`、`config/AigcEventFallbackGuardAspect` |
| B-04 | 已改：从 AAF-112 requirement、tasks、`content-studio-design.md`、`content-studio-tech.md` 删除 `AUTO_ADOPT_IF_EMPTY`，规格改为采用只由用户明确选择 | docs |
| B-06 | 已改：`v7__aigc_schema.sql` 删除 `planned_phase` 列与检查约束，删除 `AigcProjectObject.plannedPhase` 字段及三处占位常量和写入；AAF-113 design.md:905 同步 | 见左 |
| B-01、B-05、M-03 | 文档：B-01 已改 requirement、tasks；B-05、M-03 的规格措辞与 `content-studio-tech.md` 的 `budgetTier` 描述待同步 | docs |
| M-01、M-02 | 登记后续 | — |

整改后预期计数：`blocker=0 major=1（M-02）minor=5（含 M-01、M-04 剩余部分降级）`。满足 blocker=0 且 major≤2 的门槛，需 architect 复审确认；M-04 的守卫切面与其余整改均未编译、未运行测试。

注意：修改了已应用的 v7 迁移，已执行过该迁移的本地开发库需重建（或执行 Flyway repair），否则启动校验和不一致。
