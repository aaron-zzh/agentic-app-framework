# 开发记录：AAF-117 任务式调用执行形态与全环节流式可视化

执行者：AI/developer-service + developer-webui

> 约束：本轮实现不编译、不运行任何测试与 lint，下列条目均为"已实施、未运行验证"，由人类或后续 check 阶段验证。

## 完成记录

- ✅ 前端审查 — 复核 assistant-agui.ts / use-copywriting.ts / copy page：修正节点事件解析（started/completed 不带 delta、roleKey 允许空串）、节点状态仅跟随生命周期事件（2026-10-12）
- ✅ #11713 控制工具重命名 — 四个工具名常量、异常文案、InvocationPolicy 与注释一并改为新名，不保留旧名（2026-10-12）
- ✅ #11714 内置技能拆分 — v12 种子中 builtin-task-decomposition 拆为 triage / step-planning / agent-delegation 三个技能并各自绑定 ALWAYS（2026-10-12）
- ✅ #11715 按智能体类型绑定 — 绑定表无智能体类型维度，新增 BuiltinSkillScope 在 resolveExecutionProfile 中按智能体类型与任务态过滤，不改表结构（2026-10-12）
- ✅ #11707 协调者交付标志 — 协调者是计划唯一完成证据时 delivery=true；委派成功后由 AG-UI 投影把交付权移交聚合者，RUN_STARTED 只发一次（2026-10-12）
- ✅ #11708 并入 PRIMARY — AgentKind / InvocationPolicy / 披露策略并入 COORDINATOR，InvocationProfile.primary 改名 coordinator，协调者恢复 Persona（2026-10-12）
- ✅ #11709 升级继承技能 — promoteDirect 继承轻量态 FIXED 路由的 Skill（2026-10-12）
- ✅ #11710 协调者工具与上下文边界 — 协调者保留 Role/Skill 业务工具并补控制工具，披露放宽为内容级，聚合者仅 request_clarification（2026-10-12）
- ✅ #11711 统一协调者指令 — COORDINATOR 指令允许直答，合同版本升为 3（2026-10-12）
- ✅ #11716 分解策略 — execution.decomposition 冻结在 ExecutionIntent，NONE 移除 delegate_agents，REQUIRED 仅保留 delegate_agents 与 request_clarification（2026-10-12）
- ✅ #11717 取消 L0 选择并放宽委派 — 任务态节点用冻结的 Role/Skill；executors 的 roleKey/skillKey 可选，指定时须在候选目录且 Role 工具集不超出协调者（2026-10-12）
- ✅ #11705 SSE 结束条件 — 移除节点 doFinally 的无条件 complete，新增 RunDeliveryTracker：任务流在交付节点完成或任一节点失败/取消/暂停/等待授权/澄清时收口，恢复流跟随整个任务（2026-10-12 缺口调整后，原"按单次执行收口"已取代）（2026-10-12）
- ✅ #11706 缺口记录 — 新建 AAF-115 dev-log 记录 TASK 路径 SSE 立即闭合缺陷并指向 AAF-117（2026-10-12）
- ✅ #11718 聚合要求 — AggregationContract 增 requirements，AGGREGATOR_REDUCE 必填，上限取请求参数/AiProperties（默认 8000）并受 32000 硬顶，jsonb 持久化无需改表（2026-10-12）
- ✅ #11719 聚合者输入契约 — AGGREGATOR_REDUCE 的聚合者输入含用户原始请求、协调目标、聚合要求与执行者清单，PASS_THROUGH 不变（2026-10-12）
- ✅ #11721 节点过程事件 — 内部节点 aaf.node.message 的 data 补回 delta，仅走实时通道，不进入持久公共事件（2026-10-12）
- ✅ #11722 前端节点分组展示 — 节点输出按 nodeId 分组默认折叠，正文区只消费交付节点文本；执行者草稿产物已由缺口调整补上识别（见下）（2026-10-12）
- ✅ #11723 文档同步（开发侧）— 冲突与待决策项登记到 improvements.md，设计文档与 ADR 状态待协调者处理（2026-10-12）
- ✅ #11724 拒绝授权反馈并续接 — 拒绝回传节点工具错误并恢复执行，二次拒绝失败收口（2026-10-12）
- 🚫 #11712 轻量态自动升级 — 修正：网关在创建审批前就拒绝，无需抑制 interrupt；按"先简单实现"让拒绝错误指明 promote_to_task，由模型同循环升级，未做 harness 自动触发（2026-10-12）
- 🚫 #11720 harness 驱动产物保存 — 决策：维持现状（方案 c），模型经 Skill 声明的保存工具保存（2026-10-12）

## 实现决策

- 工具类名（`SubmitExecutorPlanTool` 等）未随工具名改动：ADR-008 只要求模型可见工具名替换，类名改动属于任务外重构，留待后续评估
- `builtin-step-planning` 未对聚合者开放：聚合者没有 `plan_steps` / `report_step`（#11710 保持其无业务工具），技能正文提到的工具必须与可见工具一致；与 ADR 表格中"所有智能体"的字面表述有差异
- `builtin-step-planning` 与 `builtin-agent-delegation` 只在任务态（`taskId != null`）内激活，对应 ADR "轻量态不支持 plan_steps"
- 前端节点事件解析不再对 `aaf.node.message` 强制要求 `delta`：后端在 #11721 之前只下发 `contentLength`，强制校验会让任何含内部节点的任务整条流中断
- `delegate_agents` 入参模式里只写默认上限 8000，实际生效上限（请求参数覆盖后）在超限错误里返回：工具是单例，`inputSchema()` 拿不到单次请求的上下文，与 ADR "实际上限写入字段说明"有差异
- 实时流收口：任务流用 `RunDeliveryTracker.endsStream`（交付节点完成或任一节点失败/取消/暂停/等待授权/澄清），恢复流（初版按 `endsExecution` 收口，后被缺口调整改为跟随整个任务，复用 `endsStream`）。初版曾记"子节点的授权等待只投影为 CUSTOM"，已被缺口调整取代：任一节点的授权/澄清等待现投影为标准 interrupt；聚合者未被派发时由原有的空闲超时（10 分钟 / 5 分钟）兜底

## 注意事项

- `builtin-agent-delegation` 正文已同步 `requirements` 与可选 `roleKey`/`skillKey`（v12 种子，2026-10-12 静态核对确认）；`delegate_agents` 之前协调者输出的文本已作为正文发出（交付权在委派成功后才移交），靠 `builtin-agent-delegation` 技能与协调者指令约束"委派前不输出正文"，前端兜底尚未实现；`decomposition=REQUIRED` 同样只靠工具可见性与未委派按节点失败收口
- `ExecutionIntent` 新增 `decomposition` 组件、`AssistantExecutionRequest.ExecutionOptions` 新增 `decomposition`，已同步 `ExecutionIntentTest`、`AssistantExecutionServiceTest` 的构造调用；`AssistantCommand.forNode` 原未使用的 `aggregationKind` 参数改为 `TaskPlan plan`，用于判定协调者是否同时是交付节点
- #11717 原阻塞点已解除：人工确认放宽后规则为"候选目录 + Role 工具集为协调者子集"，见 tasks.md；随后决策执行者暂默认继承协调者，可选指定入口保留
- 待核实结论：TASK 路径（意图恒为 FIXED）本身不触发 L0 选择；会触发 L0 的只有轻量态 AUTO 与"AUTO 轻量态升级后的任务节点"，后者已改为沿用节点冻结的 Role/Skill
- 历史文档（`docs/task/v0.12/**`、`docs/design/framework/intelligent/runtime.md` 等）仍引用旧工具名与旧技能名，由 #11723 处理，不在本阶段改

## 缺口调整（2026-10-12，人工确认后）

- ✅ 保存意图按交付角色推导 — 轻量态直答、唯一完成证据所在的协调者、聚合者才要求并持有产物保存工具；聚合者仅放行保存工具，执行者不可见保存工具（AssistantApplicationService.resolveExecutionProfile）
- ✅ REQUIRED 协调者从启动起即内部节点 — AssistantCommand.nodeIdentityOf 按分解策略计算交付标志，输出走 aaf.node.message 过程事件可监控
- ✅ REQUIRED 未委派按节点失败收口 — TaskCommandService.executeClaimed 检测 delegate_agents 未成功，走既有重试（受 maxAttempts 约束），耗尽后按 PAUSE_TASK 暂停
- ✅ 子节点授权/澄清等待投影为标准 interrupt — AafAguiConverterRegistry 对任一节点的等待事件走生命周期投影；恢复流改为跟随整个任务（AssistantApprovalEventService 去掉按执行过滤，复用 RunDeliveryTracker.endsStream）
- ✅ 内部节点草稿产物前端可识别 — 节点事件前端解析带脱敏 data，按节点记录 artifacts，不覆盖页面当前文档

注意：
- 保存工具要求不再落在执行者上；任务级 DRAFT_COMMITTED 证据目前未在节点收口处校验（只在 DIRECT 的 finalizeDirect 校验），未发现需要同步调整之处，但未运行验证
- 拒绝反馈复用 `HumanApproval.decisionReason` 的 JSONB 持久化，不新增列；后端按 Unicode 字符计数，超过 1000 直接拒绝请求（不截断），前端输入同步限制 1000
- 拒绝授权不再把 Task 转 PAUSED：当前节点恢复并收到 `AuthorizationDeniedException`；同 nodeId 同 action 累计拒绝两次后抛 `IllegalStateException`，交给既有重试与 PAUSE_TASK 收口
- 同任务兄弟节点在授权等待期间的调度行为沿用现状（Task 进入 AWAITING_AUTHORIZATION，已在跑的节点自然收口），未改调度
- ✅ 任务计划依赖图 — TaskPanel 计划区默认文字列表，可切换到 React Flow 依赖图（TaskPlanGraph，按依赖深度分层、状态着色），点击节点显示该节点详情；实时输出详情仍在文案页节点面板（SSE），任务面板为轮询投影不含实时文本
- 调度结论：Task 进入 AWAITING_AUTHORIZATION 时 JpaTaskMaterializationAdapter.claim 不再领取新节点（仅 READY/RUNNING/VERIFYING 可领取），即现状已是"整体暂停调度、在跑节点自然收口"，无需改调度
- 决策（2026-10-12）：执行者 Role/Skill 暂不由协调者指定，默认继承协调者；不做候选目录只读工具，也不做代码选择。delegate_agents 仍保留可选 roleKey/skillKey 及 requireDelegable 校验，作为后续按需启用的入口

## 静态核对（2026-10-12）

> 只做代码与 tasks.md 的静态阅读与检索，未编译、未运行任何测试、lint、check、pnpm/nx/mvn；以下结论均未经运行验证。结论取值：已实现 / 部分实现 / 未实现 / 不做。

- 已实现 #11701 MESSAGE_DELTA 不落库 — `JpaExecutionEventStoreAdapter.append` 直接返回，`MESSAGE_COMPLETED` 仍落库
- 部分实现 #11702 实时事件端口 — `ExecutionLiveEventPort`、`InMemoryExecutionLiveEvents.publish/open/complete` 已有；缺口：孤儿计时器订阅建立后不取消，活跃订阅满 15 分钟被强制 complete；通道无所有者校验
- 已实现 #11703 入口先订阅后提交 — `AssistantExecutionService.execute` 先 open 再 submit；`AssistantApprovalEventService.open`；澄清恢复先登记订阅（审批恢复的顺序缺口见 #11724）
- 已实现 #11704 空 delta 过滤 — `TextMessageEventConverter.convert/requiredDelta`
- 已实现 #11705 SSE 结束条件 — `RunDeliveryTracker.endsStream`、`AssistantExecutionService.execute`、`AssistantApprovalEventService.open`；`AssistantApplicationService.invoke` 的 `doFinally` 仅记录日志
- 已实现 #11706 缺口记录 — `docs/task/v0.13/AAF-115/dev-log.md` 第 5 行
- 已实现 #11707 协调者交付标志 — `AssistantCommand.nodeIdentityOf`、`AafAguiConverterRegistry.convert`、`AafAguiStreamContext.runStarted`
- 已实现 #11708 并入 PRIMARY — `InvocationProfile.coordinator`、`TaskCommandService.promoteDirect`、`PromptAssembler`；apps/ 下无 `AgentKind.PRIMARY` / `InvocationPolicy.PRIMARY` / `primaryMinimal` / `AgentPurpose.COORDINATION` / `Audience.PRIMARY`
- 已实现 #11709 升级继承技能 — `TaskCommandService.promoteDirect` 取冻结画像 `resolvedRoute.skillKey`
- 已实现 #11710 协调者工具与上下文边界 — `AssistantApplicationService.builtinControlTools/resolveExecutionProfile/executionContext`；tasks.md 原写"聚合者有效工具仅 request_clarification"，已改为"聚合者控制工具仅 request_clarification，AUTO_SAVE_DRAFT 下另放行保存工具"
- 已实现 #11711 统一协调者指令 — `InvocationPolicy.COORDINATOR` 合同版本 3；指令仍简述三档规则，REQUIRED 策略未披露给协调者（已登记 improvements）
- 不做 #11712 轻量态自动升级 — 仅做指明 `promote_to_task` 的拒绝错误（`DefaultToolGateway.invoke`、`InvocationPolicy.COORDINATOR`、`builtin-task-triage`），harness 自动触发未实现，与 tasks.md 的 🚫 结论一致
- 已实现 #11713 控制工具重命名 — 四个 `TOOL_NAME` 常量；apps/ 下旧名仅剩 v12 种子中两处谱系注释（`builtin-task-decomposition`），Java/TS/测试无旧名；全仓库 docs 下旧名由 #11723 处理，verify 口径已限定为 apps/
- 已实现 #11714 内置技能拆分 — `v12__init_seed_data.sql` 三个技能摘要与正文、三条 ALWAYS 绑定、INHERIT 的 CASE 一致，正文含 `requirements` 与可选 `roleKey`/`skillKey`；tasks.md 原写"正文待 #11717、#11718 后同步"已过时并修正
- 已实现 #11715 按智能体类型绑定 — `BuiltinSkillScope.filter/applicable`；缺口：过滤不看分解策略，NONE 下 delegation 技能、REQUIRED 下 step-planning 技能仍激活但对应工具不可见
- 已实现 #11716 分解策略 — `ExecutionIntent.decomposition`、`AssistantExecutionRequest.ExecutionOptions`、`AssistantApplicationService.builtinControlTools`、`AssistantCommand.nodeIdentityOf`、`TaskCommandService.executeClaimed`；前端 `copywritingExecutionRequest` 传 `NONE`；tasks.md 补充 REQUIRED 内部节点与失败收口
- 已实现 #11717 取消 L0 选择并放宽委派 — `SubmitCoordinationPlanTool.submit/requireDelegable`、`AssistantApplicationService.effectiveRoute`；缺口：工具集越权错误未附候选目录；tasks.md 措辞改为"可选指定、默认继承"，并移除已失效的 AUTO_SAVE_DRAFT 执行者限制
- 已实现 #11718 聚合要求 — `SubmitCoordinationPlanTool.requirementsLimit/requireAggregationRequirements`、`TaskPlanDraft.AggregationContract`；缺口：`inputSchema` 未声明嵌套 property，必填规则只在工具层
- 已实现 #11719 聚合者输入契约 — `TaskPlan.applyTaskPlanDraft/resolveInput/appendAggregationContext`
- 不做 #11720 harness 驱动产物保存 — 无残留的 harness 保存调用；保存仍经 Skill 声明的 `content.draft.upsert`（`AssistantApplicationService.resolveExecutionProfile`）
- 部分实现 #11721 节点过程事件 — `InternalNodeEventConverter.appendLiveDelta`、`ExecutionEventPublicMapper.safeData`；缺口：披露仅限任务所有者未在实时端口层强制，taskId 取自客户端 runId；tasks.md verify 改为"增量文本不落库"
- 已实现 #11722 前端节点分组折叠 — `use-copywriting.ts` `recordEvent/assistantDraftArtifact`、`copy/page.tsx` 节点面板、`TaskPanel.tsx`、`TaskPlanGraph.tsx`；tasks.md 原写"执行者草稿产物暂识别不到"已过时并修正，补充依赖图与 interrupt 说明
- 已实现 #11723 设计文档同步（开发侧）— `docs/prd/improvements.md` 已登记；`runtime.md`、`coordination.md`、ADR-008 状态由协调者修改
- 部分实现 #11724 拒绝授权反馈并续接 — `DefaultToolGateway.priorRejection`、`JpaHitlTransitionAdapter.decideAuthorization`、`PersistentHitlCoordinator.decideDelegated`、`AssistantAguiController.resumeApproval`；缺口：`resumeApproval` 先 decide（内部已物化并 signal 调度）后 open 订阅，与方法注释不一致；前端 1000 限制按 UTF-16 计数

### 静态检查结论

- 旧名检索：apps/ 下四个旧工具名、`AgentKind.PRIMARY`、`InvocationPolicy.PRIMARY`、`primaryMinimal`、`AgentPurpose.COORDINATION`、`builtin-task-decomposition` 均无残留（后者仅 v12 种子两处谱系注释）
- 构造器调用点：`ExecutionIntent`（10 组件，7 处）、`ExecutionOptions`（8 组件，4 处）、`AggregationContract`（4 组件）、`SubmitCoordinationPlanTool`（5 参）、`DefaultToolGateway`（9 参）、`InvocationProfile.coordinator`（4/5 参）、`NodeIdentity`（5 参）、`AssistantApplicationService` 自动配置（26 参）调用点参数个数与定义一致，含测试
- switch：`AgentKind`、`DecompositionStrategy` 的 switch 穷尽；`ExecutionEventType` 的 switch 均有 default 或已穷尽当前枚举
- import 与 Spring Bean：两个自动配置类的 `@Bean` 参数与构造器一致；未发现缺失或明显未使用的显式 import（纯文本核对）
- 前端：`assistant-agui.ts`、`use-copywriting.ts`、`copy/page.tsx`、`TaskPanel.tsx`、`TaskPlanGraph.tsx` 的 import 可解析，`@xyflow/react` 已声明；无 `any`、`@ts-ignore`、`asChild`，无 Button 渲染非 button 的违规
- 种子 v12：三个技能的 `$skill$` 引号包裹、skill_key、CASE、绑定 WHERE 自洽
- 失效测试：无因行为变化而失效的用例；仅有语义陈旧项，待复核——`SubmitCoordinationPlanToolTest.should_accept_plan_within_frozen_baseline`（fixture 的 AUTO 协调者 `delivery=false`，不再是规范身份）、`AgUiProjectorTest.should_not_emit_step_for_delivery_node_execution_events`（名称与注释指向聚合者交付节点，实际调用的是 `nodeIdentity=null` 的根事件）；未新增也未删除任何测试

### 本次直接修正

- `RunLifecycleEventConverter` 类注释：删去"子任务授权等待降级为 CUSTOM"的过期描述，改为任一节点等待均投影为 interrupt
- `TaskPlan.coordinated` 注释：去掉"无业务工具的协调者"
- tasks.md：#11702、#11705、#11710、#11711、#11713、#11714、#11715、#11716、#11717、#11718、#11719、#11721、#11722、#11724 的状态说明与措辞；dev-log 中 #11705、#11722、#11717 阻塞点、`builtin-agent-delegation` 同步说明的过期表述

## 静态核对缺口修复（2026-10-12，未编译、未运行测试）

- ✅ #11702 孤儿计时器 — `InMemoryExecutionLiveEvents.open` 在 `doOnSubscribe` 取消回收计时器；入口提交失败只取消自己的订阅，端口删除无调用方的 `complete`
- ✅ #11724 审批恢复订阅顺序 — `AssistantAguiController.resumeApproval` 校验审批归属并 `open` 后再 `decide`，失败时释放订阅；`AssistantApprovalEventService` 删除 `open(HumanApproval)` 重载；前端反馈按 code point 截断与计数
- ✅ #11715 技能范围 — `BuiltinSkillScope.filter/applicable` 增加分解策略入参，调用点 `AssistantApplicationService.resolveExecutionProfile` 同步
- ✅ #11711 REQUIRED/NONE 指令 — `PromptAssembler.invocationSource` 对协调者追加"分解策略"行
- ✅ #11717/#11718 — `SubmitCoordinationPlanTool.inputSchema` 声明嵌套 property，工具集越权错误附候选目录，`AggregationContract.validate` 强制 `AGGREGATOR_REDUCE` 的 `requirements`
- ✅ #11722 — `use-copywriting.ts` `onDone` 仅 `tool_call` 视为授权
- ✅ 测试 — `SubmitCoordinationPlanToolTest` fixture 协调者 `delivery=true`；`AgUiProjectorTest` 用例名称与注释改准确；未删除测试，未新增测试
- ⚠️ #11721 待协调者确认 — 实时端口层未按 userId 隔离（`ExecutionEvent.userId` 对部分 ownerType 为空，直接过滤有误伤风险），已缓解入口失败误伤与审批恢复订阅前的归属校验，详见 improvements


## 运行缺陷：plan_steps 工具目录不存在（2026-10-13，未编译、未运行验证）

- 现象：文案页（`decomposition=NONE`）协调者节点启动即失败，`RegistryToolPortAdapter.resolveOne` 抛"工具目录不存在: plan_steps"
- 根因：`AssistantInfrastructureAutoConfiguration` 中 `SubmitExecutorPlanTool`、`ReportExecutorStepTool` 使用 `@ConditionalOnBean(ExecutorPlanPort.class)`，而 `ExecutorPlanPort` 的 `@Bean` 方法声明在它们之后；同一配置类内条件按声明顺序求值，条件恒为 false，两个内置处理器未注册，`AgentScopeToolkitFactory` 找不到处理器后回落到目录路径，目录里本就没有内置工具
- ✅ 修复：`executorPlanPort` 方法移到两个工具 Bean 之前并加注释，不改条件与行为

## #11721 订阅归属核对（2026-10-12，未编译、未运行测试）

- 核对结论：入口（`TaskCommandService.create` 对已存在 Task 抛错，提交成功者即创建者）、审批恢复（订阅前校验审批用户）、澄清恢复（订阅在归属校验通过的 `submit` 成功后才消费）三处，事件转发给客户端前均已确认归属，端口层隔离不需要
- ✅ `AssistantAguiController.resumeClarification` — `submit` 失败时 `doOnError` 主动释放已登记订阅，不再等孤儿回收
- ✅ `ExecutionLiveEventPort.open` Javadoc — 声明端口不校验归属，由调用方保证
- 未改端口签名；残留风险（未来 `open` 调用方遗漏校验）已登记 improvements，待协调者确认
