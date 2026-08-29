---
level: 2
version: 0.1.0
date: 2026-05-06
purpose: 记录协作过程中发现的改进点，按主题分类，由协调者定期审阅
---

# 改进意见

AI 智能体和人类用户协作中发现的改进点，协调者定期审阅后纳入规范或排入待办。

- **格式**：`日期 | 条目 | 状态`（提出者默认协调者）
- **状态**：`待评估` / `已采纳`（可附实施状态） / `已完成` / `已拒绝`

## 已完成

- 2026-04-28 | **AGENTS.md 补充"基本原则"和"AI 协作宣言"** | 已完成
- 2026-05-05 | **.nxignore 排除 `docs/design/auto-dev/multica` 和 `gstack` 参考资料目录**；后续新增参考目录需同步追加 | 已完成
- 2026-05-05 | **AAF-024 #10 文档真理源归一**：steering 红线化（84 行）+ AGENTS.md 指针化 + 7 个 agent 的 resources 精确配置 | 已完成
- 2026-05-05 | **AAF-024 #11 Agent 派发触发条件规则**：🟢 低风险 kiro_default 兼任不派发 / 🟡 中风险派发 developer+tester / 🔴 高风险完整派发 product→architect→developer→tester→qa。反思见 [AAF-024 dev-log #2](../task/v0.1.0/AAF-024/dev-log.md#2-过度工程化判断的反思记录) | 已完成
- 2026-05-06 | **Agent 产出物契约机制化（gstack 借鉴）**：把下游 agent 对上游产出物的依赖从"协调者 prompt 转述"升级为"结构化契约+启动前校验"。gstack 设计"下游 skill 只读上游产物"（office-hours → design doc → plan-ceo-review；plan-eng-review → test plan → qa），产物不全下游不启动。AAF 现有 product→architect→developer→tester→qa 链条，但产出物靠 prompt 传递易丢失上下文。建议在 `collaboration-standard.md` 新增"派发产出物契约表"：product 必产 `docs/prd/AAF-xxx.md` + 验收标准清单；architect 必产设计文档 + 测试矩阵；tester 必读测试矩阵生成验收脚本；下游 agent 启动时校验上游产物存在且完整，不满足则拒绝执行并回退到上游 | 已完成

## 已采纳

- 2026-05-05 | **缺失关键流程**：hotfix / 技术债 / 依赖升级 / 回滚 / 超时无规范。遇到首次真实案例时各建 20-30 行规范，不预先写 | 已采纳
- 2026-05-06 | **前端状态管理规范细化（4 个子规则）**：补充已登记条目：① **Zustand 选择器必须返回稳定引用**——`s => ({ a: s.a, b: s.b })` 或 `s => s.items.map(...)` 会触发无限重渲，用分开选 primitive 或 shallow 比较；② **Query 缓存键必须带 workspace/租户 id**（如 `['issues', wsId]`），让切换空间自动换数据无需手动 invalidate；③ **Mutations 默认 optimistic**：本地先改 → 发请求 → 失败回滚 → settle 后 invalidate；④ **Persist 硬规则**：用户偏好/草稿/tab 布局可持久化，模态框/临时选择/服务端数据绝不持久化。参考 multica CLAUDE.md `State Management` 节 | 已采纳
- 2026-05-05 | **Vitest + Playwright + @nx/vite 技术选型**：不引入 Jest/@nx/jest。 | 已采纳
- 2026-05-05 | **后端测试环境走本地真实 DB + CI service container，不引入 Testcontainers**： | 已采纳
- 2026-05-05 | **Cucumber 移除**：6 处规范宣称但 pom 零依赖零 `.feature`；Cucumber BDD 与"`docs/prd/**` 是唯一真理"冲突。Gherkin 仅保留在需求文档 + 测试 `@DisplayName`。 | 已采纳
- 2026-05-05 | **Playwright E2E 引入**：登记为 [AAF-023 #6](../task/v0.1.0/AAF-023/tasks.md) | 已采纳（未实施）

## 协作流程与智能体

- 2026-05-06 | **指定任务类型 LLM**：为不同任务（代码生成/文档/测试/架构）配置专门 LLM，优化效果和成本（参考 multica 支持 8 种 Agent CLI） | 远期
- 2026-05-06 | **Sprint 流水线模板固化（gstack "流程 > 角色"理念）**：把典型迭代派发顺序从协调者现场组装升级为可复用 skill 模板。gstack 原话 "without a process, ten agents is ten sources of chaos"——流程结构化是并行的前提。AAF 已有完整角色集（product/architect/developer-*/qa/tester）和 🟢🟡🔴 风险分级（AAF-024 #11），但 subagent 派发的流水线编排仍靠协调者临场组装。建议新增 `.kiro/skills/sprint-orchestration/SKILL.md`，封装三级派发模板（Think→Plan→Build→Review→Test→Ship→Reflect），与风险分级共同构成"触发条件 + 流程模板"二维框架 | 待评估
- 2026-05-06 | **/autoplan 式一键评审 skill（gstack 借鉴）**：把 CEO→design→eng 三轮评审封装为一键 skill，只把"品味决策"（taste decision）暴露给用户。gstack `/autoplan` 在每轮编码"决策原则"，可机械判断的都自动处理，只在不可机械化的选型点才 AskUserQuestion。AAF kiro_planner/协调者现在派发 subagent 需反复转述需求。建议：作为 `sprint-orchestration` skill 的高层包装，内部按 🔴 级别自动跑 product→architect→developer 规划阶段，仅在架构选型/接口删除/权限变更点向用户发问，降低协调者转述摩擦 | 待评估

- 2026-05-06 | **product agent 六问挑战模板（gstack `/office-hours` 借鉴）**：把"推翻用户自述需求"从隐含职责升级为结构化必过检查。gstack 例子中用户说"我要日报应用"，agent 识别出"实际在描述 chief of staff AI"，抽取 5 个隐含能力 + 挑战 4 个前提 + 给 3 个实现方案。AAF product agent 职责含"需求分析"但无强制挑战前提的 prompt 结构。建议在 product agent prompt 中加六问必过项：① 描述的是需求还是方案？② 真正痛点是什么？③ 有哪些隐含前提？④ 最窄 MVP 是什么？⑤ 完整愿景成本多少？⑥ 推荐哪个、为什么？没过六问不进架构阶段 | 待评估
- 2026-05-06 | **迭代 retro 产出机制化（gstack `/retro` 借鉴）**：把迭代回顾从"随意总结"升级为结构化产出物。gstack `/retro` 周度回顾含按人分解贡献 / 发货连续性 / 测试健康趋势 / 成长机会。AAF qa agent 负责度量分析和质量门控，但无定期 retro 产出物。建议在 `iteration-management` skill 加 retro 步骤：迭代归档前必产 `docs/task/v0.x.0/retro.md`，含按角色分解贡献 / 计划 vs 实际对比 / 测试覆盖趋势 / 质量门控通过情况 / 下迭代改进点；作为 qa agent 的标准交付物之一 | 待评估
- 2026-05-06 | **Tests follow the code, not the app（测试放置硬规则）**：参考 multica CLAUDE.md 的分层——共享业务逻辑测试 → `packages/*.test.ts`（pure logic）；共享 UI 组件测试 → `packages/views/*.test.tsx`（jsdom，不 mock 框架）；平台特定接线测试 → `apps/web/*.test.tsx` / `apps/desktop/`（需要 mock 框架）；E2E → `e2e/*.spec.ts`。**硬规则：如果测试需要 mock `next/navigation` 或 `react-router` 来测共享组件，测试放错了位置，必须移到 `packages/`**。依赖 P2.3 packages/ 落地 | 待评估
- 2026-05-06 | **修 bug 必加回归测试（gstack `/qa` 借鉴）**：让"测试完备性"从纪律变成机制。gstack 每次修 bug 都自动生成对应回归测试。AAF 测试规范已分层明确（Surefire/Failsafe、`*.test.ts`/`*.accept.test.ts`），但"修一个 bug 必须加一个测试"未机制化。建议在 `acceptance-test-standard.md` 和 tester agent SKILL.md 硬编码：每修一个 bug 必须在对应层级新增一个 failing-then-passing 的测试用例；在 dev-log 记录 bug 链接 + 新增测试文件路径 + 复现步骤；缺失即 blocker | 待评估
- 2026-05-06 | **视觉审计原子提交规范（gstack `/design-review` 借鉴）**：前端样式修复每个 finding 一个 commit，格式 `style(design): FINDING-NNN`，可 bisect 回退。gstack 80 项视觉审计后逐项修复，CSS-only 变更免风险预算，JSX 变更计入风险。AAF 提交规范有 `style` type 但无"一修一提交"约束。建议：① 在 `commit-standard.md` 补充"视觉/样式修复场景"——每个独立 finding 一个 commit，scope 为组件名或页面名；② designer agent 或 tester agent 做视觉审计时遵循此规范；③ 纯 CSS/Tailwind 变更标记为低风险可批量提交，涉及组件结构变更需逐个提交。v0.2 webui 有实际页面后生效 | 待评估
- 2026-05-06 | **Polymorphic Actor 落地路径**：① 新建 `docs/design/framework/actor.md` 定义 Actor 抽象、类型枚举、操作接口、审计协议；② 更新 `requirement-standard.md` 强制"谁做了什么"字段用 Actor；③ 更新 ~~`permission-system.md`~~→`access-control.md` 把"Agent 作为权限主体"从 Principal 层上升为 Actor 层；④ `packages/types/actor.ts` + `com.xuejiai.aaf.common.actor.Actor` 作为首个共享类型（同时验证 P2.3）；⑤ ArchUnit/ESLint 约束 `createdBy`/`assignee`/`owner` 必须为 Actor 类型 | "已实施（设计+规范层面），代码层面待首个业务实体开发时落地

## 五层智能架构代码-文档一致性审计（2026-08-26）

来源：`docs/design/framework/intelligent/` 全量文档与 `apps/service/` 代码的双向核实（虚标降级 + 过度保守升级）。缺口本身已记录在各文档的「实现态」表，本节只保留**优先级排序**与**是否需人类确认设计变更**。

排序依据：先真理源矛盾与安全边界，再能力缺口，最后优化。

### 本次会话已完成

- 2026-08-26 | **置信度门控三段阈值**：`DefaultConfidenceGate` 原仅单一 `0.7` 阈值，低置信+可验证会越级自动执行；已改为 `>0.9 / 0.7..0.9 / <0.7`，删除被设计否决的 `EXECUTE_AND_VERIFY` 分支，不可逆动作强制确认。两个调用点（`AiBusinessActionExecutor`、`ToolCallDispatcher`）原都只拦 `PAUSE_FOR_HUMAN`，已一并修正 | 已完成
- 2026-08-26 | **`AiOutput` 横向越权**：详情/调整/回退原按裸 `id` 查询；已改为按归属主体读取，越权与不存在统一按不存在处理 | 已完成
- 2026-08-26 | **`TaskAnalysis` 驱动调度 + 解除 copywriting 闸门**：编排形态由复杂度判定决定，不再按 `interactionMode` 硬绑定；任务式接受任意已发布 Skill | 已完成
- 2026-08-26 | **协调计划授权衰减**：衰减基准改为协调者自身已冻结 Role/Skill（原为根路由相等），`CHAT` 的 AUTO 路由因此可进 `coordinated`；越界 fail-closed，边界用例见 `DelegatedTaskCoordinatorAuthorizationTest` | 已完成
- 2026-08-26 | **短期会话上下文接入 L1**：作为记忆读管道的一个通道（非独立 scope），读写双向补齐——原 `ShortTermMemoryService.append` 零调用方，读到永远为空 | 已完成
- 2026-08-27 | **`NO_PROGRESS` 无新进展停止**：`IterationEvaluation.Decision` 只有 CONTINUE/COMPLETE/BLOCKED，`TaskBoard.IterationStopReason` 无 `NO_PROGRESS`，Agent 卡在原地重复无效动作时无法提前止损，只能靠 `maxIterations` 数字上限硬顶。已实现：`TaskBoard.IterationStopReason` 新增 `NO_PROGRESS`；`IterationState` 新增 `lastIterationResults`/`unchangedStreak` 字段；`evaluateIteration` 按结构化成员结果比对（不依赖 evaluator 模型自报，符合 agent.md"不使用思维链文本相似度"要求）——连续两轮成员结果与上一轮完全一致即停止并升级。范围收窄：只做了 `IterationGroup` 层面的"无新证据"检测；"同一工具+等价参数+Observation 无变化禁止第三次盲重试"（单 SubTask 级别的重复调用检测）未做，需要新增逐次尝试的 observation 历史记录，数据模型改动更大，本次未处理。`TaskLoopContract` 统一类型按用户决定不做（"各自生效也行"），5 个现有类各自职责保持不变 | 已完成（部分）
- 2026-08-26 | **父任务 AGGREGATOR 完成绕过业务证据门禁**：`AGGREGATOR_REDUCE` 聚合模式下父任务完成证据只指向 `AGGREGATOR` 子任务，但完成判定分支此前只对 `EXECUTOR` 校验 `completionEvidenceSatisfied`，`AGGREGATOR` 无条件 `completeSubTask`；`PASS_THROUGH`/`ORDERED_CONCAT` 模式因证据只指向已受检的 `EXECUTOR` 未受影响。已将 `AGGREGATOR` 纳入同一证据校验（`DelegatedTaskCoordinator.java:490-496`），不满足则按现有重试/失败路径处理，重试耗尽后走 `hasTerminalFailure` → `notifyFailure`；回归测试见 `DelegatedTaskCoordinatorAggregatorCompletionTest`（已验证回退修复会令 `should_fail_aggregator_when_completion_evidence_missing` 失败） | 已完成
- 2026-08-27 | **`AGGREGATOR_REDUCE` 命名收敛**：枚举旧名 `COORDINATOR_REDUCE` 已整体替换为 `AGGREGATOR_REDUCE`（`CoordinationPlan.java:176-180`、`TaskBoard.java:227,391`），协调者提示词允许值同步（`InvocationPolicy.java:13-15`），不保留双语义。序列化影响已核实：枚举名落在 `ai_task_board.board_payload` JSONB（`TaskBoardEntity` 整体 `@JdbcTypeCode(SqlTypes.JSON)`），按项目既有约定（`v7__aigc_schema.sql:2`「所有环境必须清空后按 Flyway 顺序重建」+ dev `aaf.flyway.clean-on-start`）不新增数据迁移文件。`coordination.md` 聚合契约节与实现态表已同步 | 已完成
- 2026-08-28 | **记忆检索门面收敛 + 情景/程序化三通道接入主链**：`MemoryRetrievalService` 与 `UnifiedRetrievalService` 是两套未接主链的孤立门面（前者顺序组合短期/原子/bundle/程序化/图谱，后者并行组合原子/bundle/知识），与生产在跑的 `DefaultMemoryContextCollaborator`+`HybridSearchService` 分别调用形成三套并行实现，违反禁并行抽象。已收敛为两层门面：新建 `MemoryRetrievalPort`（记忆自有检索入口，`DefaultMemoryRetrievalPort` 虚拟线程并行原子/情景 bundle/程序化三通道，单通道失败降级空结果不影响其他通道）+ `UnifiedRetrievalPort`（跨源编排门面，`DefaultUnifiedRetrievalPort` 迁移意图分类规则与预算分配公式、并行调用 `MemoryRetrievalPort`+`HybridSearchService`、RRF 融合四通道、融合后调用 `MemoryRerankerService`（新增 `rerankContents` 通用重排方法）重排）；`DefaultL1ContextCollaborator` 改为统一调用 `UnifiedRetrievalPort`，短期会话与任务材料前置注入逻辑不变；`MemoryRetrievalService`/`UnifiedRetrievalService` 两个旧门面整体删除（零生产调用方，无需迁移旧测试）。同时修正两处已知缺陷：知识检索改用调用方已解析授权主体，不再使用 `AuthorizationSubject.unresolved()`；重排调用时机改为融合之后作用于最终跨源候选。图谱通道非本轮范围，仍需接入统一门面，单独排期。新增 14 个单测（`DefaultMemoryRetrievalPortTest`/`DefaultUnifiedRetrievalPortTest`/`DefaultL1ContextCollaboratorTest`），`pnpm nx test service` 全绿（357 测试）。`memory.md`/`retrieval.md`/`cognition.md` 实现态表与验收基线已同步为已实现 | 已完成

### 待办（🔴 高优先，安全边界与真理源矛盾）

- 2026-08-26 | **工具空集语义相反（安全）**：`DefaultEffectiveToolResolver.resolve()` 把 Skill 空需求当"未限制"（放行 Role/Agent 交集全部工具），`resolveAssistant()` 把同一空集合当"全拒绝"（直接返回空列表），同一语义两种解释。已统一为 RESTRICT+空集合恒拒绝业务工具（`DefaultEffectiveToolResolver.java:14-52`），不再随 Role 白名单放宽；生产唯一调用方 `AssistantApplicationService` 在 Role 白名单为空时已提前把 Agent 工具清空传入，故该修复不影响其现有调用路径。已翻转 `should_return_agent_tools_when_role_whitelist_empty` 预期并重写测试套件，含新增 `resolveAssistant` 覆盖（`DefaultEffectiveToolResolverTest`）。2026-08-29 补齐剩余缺口：新建 `BaseToolProfile`（4 个 `ai_tool_catalog` 中 `risk_level=LOW`+`read_only=TRUE` 工具：`listBusinessActions`/`list_workflows`/`recognizeOcr`/`queryWeather`），恒定并入候选集但仍受 Role/Assistant 白名单交集约束不绕过业务权限边界（对比 Kiro CLI subagent 信任不隐式继承、AgentScope `ToolGroupManager` 未分组默认放行两种参照设计后确认此约束）；`toolAccessMode` 简化为布尔开关 `ActivatedSkill.inheritRoleTools()`（不新增枚举类型，`RESTRICT`=false 维持原交集逻辑，`INHERIT`=true 跳过 Skill 必需工具限制放行 Role/Assistant 与 Agent 交集），贯通 `SkillVersion.toolAccessMode`→`SkillStore.SkillRecord`→`SkillDef`→`ActivatedSkill`→`EffectiveToolResolver` 全链路。`DefaultEffectiveToolResolverTest` 扩至 18 个测试覆盖三态与 `BaseToolProfile` 场景，`pnpm nx test service` 全绿 | 已完成
- 2026-08-26 | **`SYSTEM_MANAGED` 绕过 owner 校验（安全）**：`AssistantExecutionService.requireExecutableBy` 对系统托管定义直接 return，任何认证用户可执行任意系统助理定义。已按方向 B 修复：`resolveAssistant` 增加显式 target 命中 `SYSTEM_MANAGED` 即拒绝（`AssistantExecutionService.java:774-782`），只允许隐式默认路径（解析为调用者自己副本）触达；`AssistantApplicationService` 同名重复逻辑未动，留作已知残余（`ExecutionAudience`/`ChannelBindingPolicy` 完整方案未来再评估）。2026-08-27 复核结论：该残余**不可局部补同样的检查**。`AssistantApplicationService.requireExecutableBy` 位于框架内部端口，`AssistantCommandPort` 的四个调用方全是已授权命令的内部派生——渠道适配器（绑定配置驱动，主体为 `VISITOR`）、`DelegatedTaskCoordinator` 子命令、`ApprovalRecoveryDispatcher` 重放、以及经 `resolveAssistant` 已把关的 HTTP 入口。且 Team 成员允许是 `SYSTEM_MANAGED`（`AssistantExecutionService.requireExecutableBy:855-861` 放行、`resolveTeamMember:829` 调用），子 `SUBTASK` 命令沿用父 `memorySubject` 并携带成员 assistantId，因此"仅 `SubjectKind.VISITOR` 才放行 SYSTEM_MANAGED"这类规则会直接打断带系统成员的 Team 执行。正解仍是命令上显式带 `ExecutionAudience`（OWNER / CHANNEL_VISITOR / DELEGATED_CHILD），需新增字段并落到 `ai_delegated_task` 命令 JSON，属独立排期项。状态从"安全残余"降级为"设计缺口"，不再当作可局部修复的漏洞记账。2026-08-29 三次复核确认：① `TeamDefinition` 无 `ownerId` 字段，但 `resolveTeamMember` 组建阶段已对每个 Member（Leader/Worker 均适用）调用 `requireExecutableBy(identity, definition)`，跨用户组队（拉别人私有 Assistant 入队）在组建阶段即被拒绝（`EXECUTION_TEAM_MEMBER_ASSISTANT_NOT_FOUND`），已与既有条目"Team 成员只能是调用者自己的 Assistant 或系统助理，无跨用户组队"结论一致，非新发现；② `AssistantApplicationService.requireExecutableBy` 的四个调用方 `assistantId` 均来自上游已校验通过的内部派生命令，不存在用户可控的裸系统模板越权输入面；③ 引入 `ExecutionAudience` 的成本（新枚举+改 `AssistantCommand` 字段+四个调用方分类+`ai_delegated_task` JSON 兼容）与消除的实际风险不成比例。**最终确认维持现状不处理**；唯一有长期价值但非必要的动作是消除 `aaf-framework`/`aaf-api` 两处重复实现，受包边界限制（`aaf-framework` 禁依赖业务模块）无法直接复用，需下沉公共逻辑，暂不排期 | 已确认不处理
- 2026-08-27 | **渠道绑定配置无归属校验（安全，本轮新发现）**：`WecomKfBindingController` 仅有 `@PreAuthorize("isAuthenticated()")`，三个写读接口都不校验归属——`list()` 直接 `bindingRepo.findAll()`；`save()` 按 `openKfId` upsert，命中他人已有行即改写其 `assistantId`；`delete(id)` 直接 `deleteById`。危害链：`OperatorEntityListener` 只在 `@PrePersist` 回填 `ownerId`/`orgId`，`@PreUpdate` 不回填，所以被改写的绑定仍保留原属主，而 `WecomKfMessageHandler.request()` 正是用 `binding.getOrgId()`/`getOwnerId()` 作为执行租户与主体（`DefaultChannelAssistantExecutionAdapter` 以 `SubjectKind.VISITOR` 落记忆）——等于同组织内任一认证用户可把他人客服账号重定向到自己指定的 Assistant，并以原属主身份执行。`assistantId` 亦无存在性/生命周期/可执行性校验。跨租户方向由 `OrgFilterAspect`（对任意 `JpaRepository` 生效、orgId 缺失 fail-closed）拦住，实际暴露面是**同组织内跨用户**。与本轮已修的 `AiOutput` 横向越权同类。修复方向需确认：① 三个接口一律按 `ownerId` 归属过滤，`save()` 命中他人行即拒绝；② 绑定保存时校验 `assistantId` 存在、已发布且对绑定属主可执行（复用 `AssistantExecutionService.resolveAssistant` 同一规则）；③ 是否为组织管理员保留跨用户管理能力（涉及角色模型，需人类定）。已按人类确认的**方案 A（绑定为个人资源）**修复：`list` 改 `findByOwnerIdOrderByIdAsc`、`delete` 改 `findByIdAndOwnerId` 后按不存在处理、`save` 命中他人 `openKfId` 直接 `FORBIDDEN`（该列唯一约束，无法退化为"新建自己的一条"）并显式写入 `ownerId`；`assistantId` 校验通过新增的 `AssistantExecutionService.requireExplicitlyExecutable` 复用执行入口同一规则（存在性 + 已发布 + 非裸系统模板 + 归属），不另建第二套判断。管理员跨用户接管能力按最小权限暂不实现，等出现真实运维交接需求时再评估（届时需一并定 `ownerId` 转移与审计）。回归测试见 `WecomKfBindingControllerTest`。`BotBinding`（钉钉/飞书）当前只有读取路径、无写入接口，无同类缺口 | 已完成
- 2026-08-26 | **外部动作 receipt 非原子**：动作成功后 receipt 落库前崩溃会重复副作用。已按"只加日志观察"处理：`JpaInvocationReceiptAdapter.claim()` 在 PENDING+fencingToken 提升允许重新领取时新增 `log.warn`，不改行为；fail-closed 转人工的策略切换留待观察后再定 | 已确认（仅日志）
- 2026-08-26 | **记忆可绕过候选门禁**：自动 `learn` 不经通用学习候选；旧 `MemoryExtractionService.extractAndStore` 是可注入的直接写入并行路径；`MemoryDeduplicationService` 在模型异常与解析失败时默认 `ADD`（fail-open）。已完成：`MemoryExtractionService`/`MemoryDeduplicationService` 零生产调用方、零测试覆盖、与 `RuleBasedMemoryGovernanceAdapter` 并行重复，已整体删除（移除并行写入口，fail-open 缺陷随之消失）。未完成：自动 `learn` 仍直接写库不经 `learning.md` 定义的通用候选信封——这需要新增 `targetType`/`payloadRef`/`gateResults` 类型化记忆候选字段与分流路由，`learning.md` 明确标注为 🎯 目标态未开工的架构级功能，非局部修复，本次未处理 | 已完成（部分）
- 2026-08-26 | **Workflow 第二正文通路**：`WorkflowAgUiController` 自建正文 SSE 与事件投影，违反"AG-UI 是唯一正文通道"。2026-08-27 复核修正了判定：该端点前端唯一调用点是 `flow-editor-view.tsx:76` 的 `handleDebug()`，`debug` 恒为 true，实际承担的是编排画布试跑；而 `requireRunnableFlow` 里的非 debug 生产分支（要求 flow `PUBLISHED`）**零调用方**，那才是真正违反唯一正文通道的部分。已按人类确认拆两步：**第一步已完成**——`/api/workflow/run` 收窄为仅调试（`debug` 非真直接拒绝、仅创建者），删除非 debug 生产分支（禁双路径），前端同步去掉 `startWorkflow` 的 `debug` 形参恒传 `true`；`runtime.md` 新增两通道契约对照表（入口/调用方/准入/正文语义/事件与状态/人工节点六维），"唯一正文通道"与"正文投影"实现态改为已实现。**第二步待立项**——真正的 `processMode=PREDEFINED_WORKFLOW` 执行分支：该值当前仅有判定产出（`DefaultTaskComplexityAnalyzer.java:26`）与一行日志消费（`AssistantExecutionService.java:910`），无执行分支；需绑定真实 Task/Conversation/Lease、Flowable 状态转 `ExecutionEvent`、`UserTask` 并入 `PersistentHitlCoordinator`（现为独立 `submitInput`）、正文取自完成门禁认可产物。三个待人类确认的设计点：工作流节点是否映射为 SubTask（还是整条工作流作为一个 SubTask 的执行体）、`LlmNode` 直连 `ResilientChatService` 是否收进统一链（当前工作流内部两套模型调用：`AgentNode:133` 走统一链落事件、`LlmNode` 不落）、调试临时部署在统一运行时如何表达 | 已完成（第一步）· 第二步待立项
- 2026-08-26 | **模型运行配置双源**：`ai_model` 是登记真理源，但直连 ANTHROPIC/OLLAMA 与 Harness key 仍可取 Spring 配置兜底。已确认保留现状（`ModelManagementService.resolveApiKey` 兜底为有意设计，非误用），不改代码；`model-router.md` 已如实记录为部分实现，不重复登记 | 已确认保留
- 2026-08-26 | **Assistant 历史 revision 不可读**：Team 冻结 `assistantRevision`，但适配器只读当前行，历史版本无法恢复，影响回放与回滚。2026-08-27 复核后由人类确认为**设计如此，当前不处理**：Assistant 不做版本化，`version` 是变更计数器——记下数字 + 随执行画像进快照，用于"配置调整后能简单比对"，不用于按版本取历史定义。三处证据支持该定性：① 两处 schema 注释早已写死（`v2__ai_schema.sql` 的 `ai_assistant` 表注释"不支持运行时版本切换"、`code` 列注释"运行时只按当前行读取"；`v16__intelligent_runtime_schema.sql:27` 的 `assistant_revision` 列注释"仅供审计，不支持请求选择"）；② 查定义入口只有 `AssistantRepository.findByCodeAndStatus`，无按版本查询；③ Assistant 副本是 per-user 私有定义，非跨用户共享资产，与真正做了版本递增的 `SkillService:472,491`、`AgentDefinitionService:56,105` 性质不同。`assistant.md` 稳定身份行原写"执行引用精确 revision"属虚标，已改为按当前行读取 + `version` 作变更计数器的表述。**遗留条件**：`version` 全仓无递增代码（`setVersion` 51 处调用无一针对 `AssistantEntity`，且 `BaseEntity.version` 无 `@Version` 注解），当前无 Assistant 编辑接口所以不构成问题；一旦新增任何 Assistant 写路径，必须同步推进 `version`，否则 `DefinitionLifecycleService:156`、`AssistantExecutionService:840`、`AssistantApplicationService:1096,1155` 这四处相等断言将永久退化为空断言 | 已确认保留（设计如此）
- 2026-08-27 | **Team 并行度不覆盖 roster**：`TaskBoard.applyCoordinationPlan` 原样采用 Leader 计划的 `maxParallelism`，`DecompositionBudget.requireWithin` 只校验上界，因此 8 人 Team 的 Leader 可以提 `maxParallelism=1` 把固定 Team 静默串行化，违反 `team.md` 的 `TaskBoard.maxParallelism = R` 合同。已在 `TaskBoard.applyCoordinationPlan` 的 teamBoard 分支断言 `plan.maxParallelism() == teamTargets.size()`，不等即拒（与既有"计划非法"路径同为 `IllegalArgumentException`）；不静默改写计划值，容量不足仍由 `running < maxParallelism` 的领取排队解决。协调者提示词同步改为"固定 Team 的 maxParallelism 必须等于 roster 数"（原措辞"可覆盖该 roster"允许收窄）。用例见 `CoordinationPlanTest` 两条（低于 roster 拒绝 / 等于 roster 冻结成功并保留槽位）。`team.md` 该行实现态改为已实现 | 已完成
- 2026-08-26 | **Team 成员级预算账本**：无 `MemberExecutionBudget` 类型，成员级 `tokenLimit`/`deadline` 未在调度前预留，也无预算转移审计。2026-08-27 人类决策**远期不动**，理由是当前身份与计费模型下价值不足：① Team 成员只能是调用者自己的 Assistant 或系统助理（`AssistantExecutionService.resolveTeamMember:829` → `requireExecutableBy:855-861`），无跨用户组队；② 积分是 per-user 钱包（`CreditAccount.userId`），无团队池，全部花费结算到发起者（`JpaTokenMeteringAdapter.record` 取 `context.userId()`）；③ 任务级预算账本已完整——`ExecutionContract.BudgetLimit(modelTokens, toolUnits, credits)` 为上限、`DelegatedTask.BudgetUsage` 为累计量、`reserveModelCall`/`recordModelUsage`/`reserveToolCall` 为记账入口、越界经 `pauseAndThrow` 置 `PAUSED` + `checkpoint.budgetPause` 转人工；④ 有无成员切分的差别仅在"撞上限时手上有多少半成品"，因聚合要求全部 Worker 结果，两种情况最终都转人工。连"只做成员归账"的轻量方案也一并不做。`team.md` 成员级预算节已标注为目标态 + 远期不动 + 重新评估触发条件（出现跨用户 Team、团队积分池或按成员计费/配额时），实现态行同步。附带确认：钱包 `CreditAccount.frozen` 字段在 AI 链路未使用（只有 `precheck` + 事后 `settleIdempotently`），同一用户多会话并发下可扣穿为负——人类确认可接受，不做预冻结，不单独立项 | 已确认远期不动
- 2026-08-26 | **Skill 运行时加载与发布门禁**：references/知识绑定仅有表未接生产链；内置与 Role 两层合并未接线；用户 Skill 仍可显式提交 `INHERIT`（应仅限已审核系统 Skill）。已完成第三项：`SkillService.validatePublish` 新增 `requireInheritOnlyForBuiltIn`，发布时 `toolAccessMode=INHERIT` 且非 `builtIn` Skill 直接拒绝（复用既有 `requireApprovedVersion` 保证已人工审核），测试见 `SkillServiceTest`。前两项（references/知识绑定接生产链、内置与 Role 两层合并接线）未处理，涉及 `DefaultEffectiveSkillResolver`/`SkillCatalogPort` 生产调用链改造，范围更大 | 已完成（部分）

### 待办（🟡 能力缺口与优化）

- 2026-08-26 | **三层恢复闭环**：通用步骤级、会话重建、目标级恢复及恢复后授权复核未闭合。**需人类确认事务与恢复架构** | 待评估
- 2026-08-27 | **每次物理调用冻结 `PromptEnvelope`**：当前无该类型，P1–P7 仅以六类编译片段局部冻结；配套 `TaskLoopContract` 版本化与 `NO_PROGRESS` 停止条件亦缺失。已立项为 AAF-102（设计见 `docs/task/v0.1.1/AAF-102/design.md`），第一步（结构拆分）已完成：新增 `PromptEnvelope`/`PromptEnvelopePort`/`JpaPromptEnvelopeAdapter`/`ai_prompt_envelope` 表，用于审计捕获（只存 hash/长度投影，不参与恢复）；`PromptEnvelopeCaptureMiddleware` 注册进 `AgentScopeSpecCompiler` 的 `onModelCall`，provider 发送前落一条信封；`ExecutionProfileSnapshot` 拆出 `PerCallProfile` 内部记录承载 `skillExecutionProfile`/`compiledSystemPrompt`/`toolAuthorizationRules`/`contextCompression`，外层保留同名委托 accessor 使既有 24 处读取点零改动；`ExecutionProfileSnapshotPort` 改为追加式（`freeze` 不再做相同性校验，`find` 取最新一条），`ai_execution_profile_snapshot` 去唯一约束；`resolveExecutionProfile` 删除三条已失效的可变量恢复断言（`compiledSystemPrompt` 完全相等、`contextCompression` 必须存在、`skillExecutionProfile` 激活集必须与请求路由一致），保留不变量断言（`taskId`/`assistantId`/`invocationPolicy`）。实施与原设计有偏离：未把 `PerCallProfile` 迁入 `PromptEnvelope`（后者只存 hash 投影不适合承载续跑所需完整对象），改为职责分离，偏离记录见设计文档。**第二步已按不同技术路线落地，非设计文档原方案**：设计规划的 `skill.load`/`skill.reference.load` 两个工具经 `ToolSuspendException` 挂起、`ProfileUpgradeResumeDispatcher` 续跑（`SkillLoadToolHandler`/`ProfileUpgradeResumeDispatcher`/`ProfileUpgradeRequest` 三个类均未创建）；实际实现为单一 `ContextLoadTool`（工具名 `context.load`，`kind=SKILL`/`SKILL_REFERENCE` 二态），同步返回工具结果不挂起、不续跑，已注册进 `AssistantInfrastructureAutoConfiguration` 接入工具链——代码注释明确记录这是有意简化：技能正文与参考文档是只读知识性文本，非有副作用业务动作，不需要为此重启 Harness 续跑（对比 `SupportHandoffTool` 那类必须转人工的动作）。`KNOWLEDGE_BINDING` 枚举值先行预留、命中即报错，依赖 L1 读管道三通道接入后单独排期。设计文档与 `skill-tool-resolution.md` 尚未同步这次架构偏离。**零单元测试覆盖**（无 `ContextLoadToolTest`），需补齐后才能确认端到端可用性 | 进行中（第一步已完成；第二步已用不同架构落地，待补测试与文档同步）
- 2026-08-26 | **产物四工具与 `ARTIFACT_*` 事件**：仅 `content.draft.upsert` 已实现，`artifact.reserve/checkpoint/commit/fail` 及对应事件缺失，长产物无法断点续跑。**需人类确认产物状态机细节** | 待评估
- 2026-08-26 | **执行期输入分类不信任客户端**：当前信任客户端 kind，`MODIFY/SUPPLEMENT` 仅支持澄清补参。须显式取消走确定性识别、其余用非自主分类，`MODIFY` 生成新 Board version。**需人类确认修改语义与版本策略** | 待评估
- 2026-08-26 | **短期会话上下文压缩**：`memory.md` 契约要求短期记忆为"压缩摘要"，当前实现为原文按预算裁剪注入，压缩这步未做 | 待评估
- 2026-08-26 | **默认副本唯一约束**：审计误判——DB 层已有 `uk_ai_assistant_user_default`/`uk_ai_assistant_user_source` 唯一索引（`v2__ai_schema.sql:471-478`），数据完整性无风险，已修正 `assistant.md` 表述；剩余仅"极端竞态下第二请求收到约束冲突错误而非静默幂等成功"这一体验细节，非数据风险，暂不处理 | 已确认无风险

### 顺带修复的既有问题

`PromptTemplateAssetServiceTest` 的 DTO 构造器参数缺失长期挡住整个 `aaf-api` 测试套件编译，导致 218 个测试从未运行；修复后暴露并一并修正三处既有失败：`TrustedKnowledgeStoreRunTest` 列索引陈旧（生产代码正确）、`AuthServiceTest` 缺 `AssistantProvisioningPort` mock、`TodoServiceTest` 断言取错字段（`code` 实为 `1006007`，`409` 是 httpStatus）。

## AAF 框架业务能力

- 2026-05-06 | **WebSocket 实时事件层分房间规范**：按 workspace 分房间 + 事件类型枚举 + "WS 只 invalidate query 不直接写 store"。前端状态管理条目已覆盖后半条，分房间/事件类型协议未记录 | 待评估
- 2026-05-05 | **Agent Runtime / Daemon 架构 P3.2**：AAF 作为后端本身不需要 Daemon；若要作为"被调度 Agent 框架"被 multica 式平台管理，参考 multica Daemon 机制设计 Agent Sandbox：探测 → 注册 → 轮询认领 → 心跳 → 隔离执行 → 反注册。v0.2+ Agent Sandbox 模块设计时评估 | 待评估
- 2026-05-06 | **后台巡检任务三件套规范**：借鉴 multica Server 启动的三个 goroutine——① **Runtime Sweeper**（每 30s）：标记离线 runtime / 回收孤儿任务 / GC 长期离线 ② **Autopilot Scheduler**（每 30s）：扫 cron 触发器到点 dispatch ③ **DB Stats Logger**（周期性）：打印连接池状态。AAF 编排引擎/协作控制台 Phase 3 Autopilot 落地前需定义后台任务规范，放在 `docs/design/framework/background-tasks.md` | 待评估
- 2026-05-06 | **WebSocket 心跳规范 + 事件分类处理**：补充已登记的"WebSocket 分房间规范"。① **心跳**：server 每 54s ping，client 60s 内必须 pong，否则断连；② **事件分类处理**：即时更新事件（issue/comment/task 这类需要高响应的）前端**直接 patch 本地缓存**，其他 less-critical 事件触发 **query invalidate 重拉**——不是所有事件都走 invalidate 一种模式。参考 multica 实时层设计 | 待评估
- 2026-05-06 | **Autopilot 细化规范**：补充已登记的"Autopilot 多触发机制"。① **Run 状态机**：`pending → issue_created → running → completed/failed/skipped`；② **每 Autopilot 实例独立 `concurrency_policy`**（skip/queue/replace），与任务生命周期的并发策略语义不同（一个管实例级，一个管任务级）；③ **内置模板样板**：daily news digest / PR review reminder / bug triage / weekly progress report / dependency audit / security scan 等 6 种，作为产品启动样板（用户一键创建而非从零写 cron） | 待评估
- 2026-05-06 | **Task 实体字段与关系规范化**：补充已登记的"任务生命周期状态机"。① **Task 核心字段**：多态 `assignee_type/id` + `creator_type/id`、`parent_task_id`（子任务）、`project_id`/`epic_id`（归属）、`origin_type/id`（追溯来源如 autopilot run）、`acceptance_criteria` JSONB、`due_date`、`position`（手动排序）；② **依赖关系类型**：`blocks` / `blocked_by` / `related` 三种；③ **Comment 分类**：`comment` / `status_change` / `progress_update` / `system` 四种类型，时间线混合展示；④ **订阅自动来源**：creator / assignee / commenter / mentioned / manual 五种，自动订阅规则明确；⑤ **Inbox 严重性分级**：`action_required` / `attention` / `info` 三级，决定通知强度 | 待评估
- 2026-05-05 | **packages/ 首个共享包 + 包边界 ArchUnit P2.3**：v0.1 末期或 v0.2 启动时，出现第一个可提取共享模块（如 types/common-utils）时建立首包；配套 ArchUnit（Java）+ ESLint `no-restricted-imports` / `import/no-restricted-paths`（TS）。参考 multica `packages/core` 零 react-dom、`packages/ui` 零业务包导入 | 已采纳（待首个跨 app 共享需求出现时触发）
- 2026-05-05 | **ESLint 配置 + webui lint target**：`apps/webui/project.json` 的 `check` 缺 lint（Next.js 16 linter 被 off）。引入 ESLint + eslint-config-next 最小配置，`webui:lint` 加入 `webui:check` 的 `dependsOn` | 待评估
- 2026-05-05 | **首次全仓 prettier 对齐**：`pnpm format:check` 发现 80+ 文件不合规（历史遗留）。启用 CI format-check 前先跑一次 `pnpm format`，在 AAF-023 收尾单独提交 | 待评估


## developer agent 跨任务越界提交问题

**发现时间**：2026-05-30，webui 代码审查分批修复过程中

**现象**：
developer-webui agent 在执行本任务期间多次带进后端代码：
- 批次 1 commit 多带 `apps/service/aaf-api/src/main/resources/db/migration/v1__system_schema.sql`。
- 批次 2 执行期间误提交 `9c1db9e feat(profile)` 后端用户画像模块（14 个文件 +714 行）。

**原因**：
- agent 未限制 `git add` 路径，误将工作区中其他未提交文件一并提交。
- 任务 prompt 有说明仅递 webui + docs 路径，但未被严格遵守。

**建议**：
1. 所有 developer agent 提交前必须运行 `git status` 检查，只 `git add <任务明确指定的路径>`，严禁 `git add .` 或 `git add -A`。
2. 可考虑在 commit hook 中加路径过滤，跨项目提交强制警告。
3. 在 `.kiro/agents/developer-webui` 的提示词中补充：跨路径提交视为 blocker。


## NexusKBEngine 增加 Agentic GraphRAG 三层检索分层

**发现时间**：2026-06-06，调研 Neo4j Aura Agent 后提出
**参考**：[调研笔记](../learn/graphrag-neo4j-aura-agent.md)

**现状**：`NexusKBEngine` 已有向量/关键词/图谱/混合四种检索接口，但检索路径由代码固定，Agent 无法根据问题类型自主选择检索工具。

**建议**：将检索工具按三层暴露给 Agent：
1. **精确层**：预定义 Cypher 模板（高频/已知问题，低风险）
2. **语义层**：向量相似度检索（模糊匹配，中风险）
3. **动态层**：NL→Cypher（聚合/即席查询，高风险，需置信度门控）

同时支持**工具链式调用**：向量检索找到入口节点后，Agent 可自动触发图遍历补全关联上下文（`searchByVector` 结果 → `searchByGraph`），这是 GraphRAG 相比普通 RAG 的核心优势。

**影响范围**：`NexusKBEngine` 接口 + Agent 工具调度层
**优先级**：P2，v0.3+ 知识引擎增强时考虑 | 待评估

---

## AG-UI 协议补充 THINKING 事件（工具调用可见性）

**发现时间**：2026-06-06，调研 Neo4j Aura Agent 后提出
**参考**：[调研笔记](../learn/graphrag-neo4j-aura-agent.md)

**现状**：`ag-ui-protocol.md` 已有 `TOOL_CALL_START / TOOL_CALL_END / TOOL_CALL_RESULT` 事件，协议层工具调用可见性基本完备，但缺少 `THINKING` 事件——即 Agent 在决定调用工具前的内部推理文本。

**建议**：在 AG-UI 协议中新增 `THINKING` 事件类型：
```
event: THINKING
data: {"messageId":"...", "delta":"用户在问合同条款，应该先用向量检索找相关片段..."}
```
前端可渲染为可折叠的"推理过程"面板，满足法律、医疗、合规等垂直场景的可解释性需求。

**影响范围**：`ag-ui-protocol.md` + 前端 `assistant-ui` Tool Call UI 渲染
**优先级**：P2，v0.2+ 对话体验增强时考虑 | 待评估

---

## uniapp mp-weixin 构建失败（@vueuse/core 兼容性）

**发现时间**：2026-06-06，本地预验证 CI 流程时发现

**现象**：
`pnpm nx run uniapp:build:mp-weixin` 失败，报错：
```
"TransitionGroup" is not exported by "vue-demi/lib/index.mjs"
```
`@vueuse/core@11.x` 依赖 `TransitionGroup`，但 UniApp 小程序环境的 vue 精简包未导出该 API。

**临时处理**：
CI workflow 中注释掉 `Build uniapp (mp-weixin)` 步骤，H5 构建正常。

**修复方向（待评估）**：
1. 降级 `@vueuse/core` 到 `^10.x`（需验证无破坏性变更）
2. 换用 `@uni-helper/vueuse` 或 UniApp 社区适配版
3. 在 vite 配置中针对 mp-weixin 平台 externalize 不兼容 API

**优先级**：P2，不阻塞当前迭代
