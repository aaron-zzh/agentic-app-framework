---
level: Practice
layer: Model
purpose: AAF-117 任务式调用执行形态、产物保存与全环节流式可视化技术任务
status: draft
version: 1.0.0
date: 2026-10-12
author: AaronZZH & Kiro
tags:
  - AAF-117
  - Task
  - 流式
  - 协调者
  - 技术任务
related:
  - ../../../design/adr/ADR-008-task-execution-shape-and-streaming.md
  - ../AAF-115/tasks.md
---

# 技术任务：任务式调用执行形态与全环节流式可视化（AAF-117）

> 设计依据：[ADR-008](../../../design/adr/ADR-008-task-execution-shape-and-streaming.md)（proposed，决策未经人工审核前，🔴 级任务不得编码）
> 依赖：AAF-115 的 Task / TaskPlan / Execution 模型
> 负责人：architect 拆分 | 创建：10-12

## 任务约束

- 本故事不单独写用户故事与设计文档，任务说明即验收口径；设计取舍以 ADR-008 为准，任务与 ADR 冲突时先改 ADR
- 🔴 涉及 `TaskPlan`、`NodeIdentity`、协调者契约与事件投影，#11707 起的任务需 ADR-008 经人工审核通过后才能编码（#11701 至 #11704 已先行实施，待审核时一并复核）
- 项目未发布：直接替换，不保留旧工具名、旧 `PRIMARY` 角色的别名、fallback 或兼容层
- 非指定不新增 SQL 迁移；种子数据已确认直接修改 `v12__init_seed_data.sql`（项目未部署），不新增迁移文件
- 部署形态已确认：当前仅单实例，实时事件端口使用进程内实现，不做多实例方案
- 不新增非必要单元测试；已有测试因签名变化需要同步的除外
- 术语统一：不再使用 `PRIMARY`，统一称"协调者"，分轻量态（无 Task）与任务态（有 Task）

## 任务列表

### 流式链路

1. ⏳ #11701 `MESSAGE_DELTA` 不落库
   - `JpaExecutionEventStoreAdapter.append` 对 `MESSAGE_DELTA` 直接返回，完整文本仍由 `MESSAGE_COMPLETED` 携带
   - 已实施，待运行验证
   - verify: 一次回复的事件表新增行数不随文本长度增长；`MESSAGE_COMPLETED` 仍落库且含完整 `text`
2. ⏳ #11702 实时事件端口
   - 新增 `ExecutionLiveEventPort` 与单实例实现 `InMemoryExecutionLiveEvents`，`AssistantApplicationService.invoke` 对每个产出事件发布
   - 已实施，待运行验证
   - 静态核对缺口已修（2026-10-12，未运行验证）：孤儿回收计时器改为订阅建立时（`doOnSubscribe`）取消；入口提交失败时只取消自己的订阅，不再调用会结束该 task 全部订阅的 `complete`（该方法已无调用方，从端口与实现中删除）。仍存缺口：通道按 `tenantId|taskId` 分区，端口层无所有者校验，见 #11721
   - verify: 订阅登记后 `publish` 的事件按序到达；无订阅时直接丢弃；孤儿订阅 15 分钟回收
3. ⏳ #11703 入口先订阅后提交
   - `AssistantExecutionService` 对 TASK 路径先 `open` 再 `submitConversation`；`AssistantApprovalEventService.open` 取代轮询，审批与澄清恢复在触发恢复前先 `open`
   - 已实施，待运行验证
   - verify: `/studio/create/copy` 生成时正文随模型输出逐段显示，首个 delta 不丢
4. ⏳ #11704 空 delta 过滤
   - `TextMessageEventConverter` 丢弃空字符串 `delta`
   - 已实施，待运行验证
   - verify: 模型下发空片段时前端不再因 `requiredText` 校验失败中断流
5. ⏳ #11705 SSE 结束条件改为交付节点收口
   - 移除 `AssistantApplicationService.invoke` 里 `doFinally` 对 `liveEvents.complete` 的无条件调用；改为交付节点完成、任一节点失败或取消、进入等待授权 / 澄清 / 暂停时结束；多节点任务不得在协调者收口时截断聚合者输出
   - 依赖 #11707
   - 已实施，待运行验证：`RunDeliveryTracker` 提供两种收口，任务流 `endsStream`（交付节点完成或任一节点失败 / 取消 / 暂停 / 等待授权 / 澄清）与恢复流跟随整个任务（`AssistantApprovalEventService.open` 复用同一判定）；`invoke` 的 `doFinally` 只记录日志；空闲超时兜底分别为 10 分钟（首次）与 5 分钟（恢复）
   - verify: 单节点直答任务与"协调者加执行者加聚合者"任务都能完整流出最终正文后才结束；聚合者未被派发时由空闲超时兜底而不是永久挂起
6. ✅ #11706 缺口记录：AAF-115 dev-log 记录"TASK 路径提交后 SSE 立即闭合"缺陷与 #11701 至 #11705 的修复指针
   - verify: AAF-115 `dev-log.md` 有一条缺陷记录并指向本文件

### 协调者形态

7. ⏳ #11707 协调者交付标志
   - `AssistantCommand.nodeIdentityOf`：计划里没有聚合者时，`COORDINATOR` 节点 `delivery=true`；提交计划后的协调者与执行者不交付，聚合者交付；核对 `NodeIdentity` 构造器与 `InternalNodeEventConverter` 的降级规则
   - 已实施，待运行验证：判定条件为"协调者是计划唯一完成证据"（团队计划的协调者不交付）；委派后的交付权移交由 AG-UI 投影按事件流收口（`AafAguiConverterRegistry` 在协调者 `delegate_agents` 成功后降级其后续事件，`RUN_STARTED` 只发一次）。已知限制：协调者在 `delegate_agents` 之前输出的文本已作为正文发出，靠技能与指令约束"委派前不输出正文"；前端兜底（按事件流把委派前的协调者文本移出正文区）尚未实现，已登记 improvements
   - verify: 协调者直答的正文走 `TEXT_MESSAGE_*`；委派后协调者产出的文本不作为交付正文
8. ⏳ #11708 并入 `PRIMARY`
   - `AgentKind.PRIMARY`、`InvocationPolicy.PRIMARY`、`ContextDisclosurePolicy.primaryMinimal` 并入 `COORDINATOR`，指令文本与上下文披露合为一套；`TaskCommandService.promoteDirect` 的 `invocationPolicy == PRIMARY` 校验同步调整；轻量态以 `taskId == null` 区分
   - 已实施，待运行验证：`InvocationProfile.primary(...)` 改为 `InvocationProfile.coordinator(...)`（agentKey 为 `coordinator`）；`AgentPurpose.COORDINATION` 与 `Audience.PRIMARY` 一并删除；`PromptAssembler` 不再对协调者排除 Persona
   - verify: 代码与文档中不再出现 `PRIMARY` 角色；DIRECT 对话与升级行为不变
9. ⏳ #11709 升级继承技能
   - `promoteDirect` 构造 `TaskPlan.coordinated` 时继承轻量态已激活的 FIXED 技能，不再传 `null`
   - 已实施，待运行验证
   - verify: FIXED 技能的请求升级为 Task 后，协调者节点的技能与升级前一致
10. ⏳ #11710 协调者工具与上下文边界
    - `internalPlanner` 不再丢弃协调者的 Role/Skill 工具；`builtinControlTools` 为协调者补 `plan_steps`、`report_step`、`delegate_agents`、`request_clarification`，为执行者补 `plan_steps`、`report_step`；聚合者保持无业务工具；协调者上下文披露由 `SUMMARY_ONLY` 放宽为允许内容；`builtinControlTool` 判定同步
    - 🔴 涉及上下文披露的安全取舍，需人工确认
    - 已实施，待运行验证：协调者披露改为 `MINIMAL`、预算 16 来源，上下文请求目的改为 `EXECUTION`（可回看会话历史）；`REQUIRED` 下协调者连业务工具一并不可见；聚合者不开放一般业务工具，控制工具仅 `request_clarification`，`AUTO_SAVE_DRAFT` 下另放行交付所需的保存工具；保存意图按交付角色推导（`resolveExecutionProfile`：轻量态直答、唯一完成证据所在的协调者、聚合者要求并持有保存工具，执行者不可见保存工具）
    - 已知限制：任务级 `DRAFT_COMMITTED` 证据只在 DIRECT 收口（`finalizeDirect`）校验，节点收口处未校验
    - verify: 协调者有效工具包含四个控制工具与 Role/Skill 工具；聚合者控制工具仅 `request_clarification`（保存工具除外）；业务工具调用仍走 `ToolGateway` 授权
11. ⏳ #11711 统一协调者指令
    - 改写 `InvocationPolicy.COORDINATOR`：允许简单任务直答，规则只在技能中维护一份，去掉"不得生成最终业务内容"与"必须调用提交工具"的冲突表述
    - 已实施，待运行验证（合同版本升为 3）；静态核对缺口已修：`PromptAssembler.invocationSource` 对协调者在分解策略为 `NONE` / `REQUIRED` 时追加"分解策略"行并声明覆盖通用的直答规则；指令中三档规则仍简述一份（入参清单按 ADR 保留）
    - verify: 指令文本与 `builtin-task-triage` 等技能无矛盾；FIXED 文案请求的协调者直答被判定为合法完成
12. 🚫 #11712 轻量态自动升级（先做可行性验证）
    - 轻量态下 `ToolGateway` 判定调用需要授权，或需要结构化澄清、多步骤持久时，自动触发 `promoteDirect` 而不是拒绝；先验证升级后能否保留已产生输出与待执行调用
    - 先出验证结论，结论不可行则改为只在指令层要求提前升级并记录限制
    - 验证结论（2026-10-12 修正）：轻量态下 `DefaultToolGateway` 在创建任何 `HumanApproval` 之前就以错误拒绝授权/副作用工具（`taskId == null && grantRequired`），因此不存在需要作废的审批或 interrupt，此前"需抑制 interrupt、作废审批"的判断有误。升级仍是新开执行，原循环中间输出与待执行调用不保留。按"先简单实现"落地：拒绝错误现在直接指明 `promote_to_task`，模型在同一循环内收到后即可升级；加上 `InvocationPolicy.COORDINATOR` 与 `builtin-task-triage` 的前置要求。未做 harness 自动触发——工具错误在事件里不带原因，检测需要在网关与事件映射间新增证据通道，收益有限
    - 限制：模型不遵守时，轻量态仍以 `DIRECT execution 未满足完成条件或触及持久边界` 失败
    - verify: 轻量态触发需授权的工具后，生成 Task 并进入审批；不可行时给出书面结论

### 技能、工具与分解策略

13. ⏳ #11713 控制工具重命名
    - `submit_executor_plan`→`plan_steps`，`report_executor_step`→`report_step`，`submit_coordination_plan`→`delegate_agents`，`promote_direct_task`→`promote_to_task`；同步 `TOOL_NAME` 常量、`builtinControlTools`、`InvocationPolicy` 文本、技能正文、事件注册与测试引用；不保留旧名
    - 已实施，待运行验证（`builtinControlTools` 通过常量引用，无需单独改动）
    - verify: apps/ 下检索不再出现四个旧名（历史与设计文档中的旧名由 #11723 处理）；协调者与执行者的有效工具名为新名
14. ⏳ #11714 内置技能拆分
    - 将 `builtin-task-decomposition` 拆为 `builtin-task-triage`（仅协调者）、`builtin-step-planning`（所有智能体）、`builtin-agent-delegation`（仅协调者），正文按各自职责改写；同步 `v12__init_seed_data.sql` 种子与引用
    - 种子已确认直接修改 `v12__init_seed_data.sql`，不新增迁移
    - 已实施，待运行验证；`builtin-agent-delegation` 正文已同步 `requirements` 与可选 `roleKey`/`skillKey` 入参说明（v12 种子）
    - verify: 日志里协调者激活技能不再含 `builtin-task-decomposition`；执行者看不到委派技能
15. ⏳ #11715 按智能体类型绑定技能
    - 核实 `ai_system_skill_binding` 是否支持按智能体类型限定；不支持时由 harness 在解析激活技能时按智能体类型过滤
    - 已实施，待运行验证：绑定表无智能体类型维度，由 `BuiltinSkillScope` 在 `resolveExecutionProfile` 过滤（triage 仅协调者；step-planning 仅任务态协调者与执行者；agent-delegation 仅任务态协调者）；`builtin-step-planning` 未对聚合者开放（聚合者无 `plan_steps`），与 ADR 表格字面表述有差异，见 dev-log。静态核对缺口已修：过滤增加分解策略入参（`BuiltinSkillScope.filter`），`NONE` 下协调者不激活 delegation 技能，`REQUIRED` 下协调者不激活 step-planning 技能，执行者不受分解策略影响，与控制工具可见性一致
    - verify: 执行者的激活技能里没有 `builtin-task-triage` 与 `builtin-agent-delegation`
16. ⏳ #11716 分解策略 `AUTO / NONE / REQUIRED`
    - 只通过请求参数指定，不由技能或角色声明：`AssistantExecutionRequest.ExecutionOptions` 增加可选字段，缺省 `AUTO`；`NONE` 时从协调者控制工具移除 `delegate_agents`，`REQUIRED` 时仅保留 `delegate_agents` 与 `request_clarification`；策略随根命令冻结，重试与恢复沿用；`submitTask` 程序化入口同样支持
    - 前端联动：文案页 `copywritingExecutionRequest` 传 `NONE`；后端、前端 TS 类型（`assistant-agui.ts` 的 `AssistantExecutionOptions`）同步
    - 涉及请求契约新增字段，🔴 需人工确认字段名与取值
    - 已实施，待运行验证：字段 `execution.decomposition`（`AUTO` / `NONE` / `REQUIRED`，缺省 `AUTO`）冻结在 `ExecutionIntent`；非 `AUTO` 仅允许 `TASK`；程序化入口 `submitTask` 不传即 `AUTO`；`REQUIRED` 下协调者的业务工具一并不可见，协调者从启动起即内部节点（`AssistantCommand.nodeIdentityOf` 按策略计算交付标志），未成功调用 `delegate_agents` 按节点失败收口（`TaskCommandService.executeClaimed`，走既有重试，耗尽后 `PAUSE_TASK`）。已知限制：`REQUIRED` 靠工具可见性与失败收口约束，协调者指令未披露该策略
    - verify: 文案页请求在 `NONE` 下协调者没有 `delegate_agents`，一个循环直答并交付；不传时按 `AUTO`
17. ⏳ #11717 TASK 路径取消 L0 的 Role/Skill 选择
    - 协调者在 `delegate_agents` 中可选地为执行者指定 Role/Skill（`roleKey`/`skillKey` 省略即继承协调者），候选目录由 harness 提供；放宽"执行者必须等于协调者冻结基准"的校验为"属于候选目录且不扩大授权"；DIRECT 路径保留 L0 选择
    - 已实施，待运行验证：任务态节点一律使用节点上冻结的 Role/Skill（`effectiveRoute`），不再触发 `DefaultRoleSelector` 与 `ModelSkillSelectionPort`；`roleKey`/`skillKey` 改为可选，省略即与协调者相同
    - 规则（人工确认放宽，2026-10-12）：候选目录 = Assistant 已发布 Role 及其 `candidateOnDemandSkillKeys`；不扩大授权 = 执行者 Role 的 `candidateToolKeys`（Role 工具加 Assistant 工具）必须是协调者 Role 的子集，Skill 的有效工具再与所属 Role 白名单求交集，因此无需单独比较 Skill 需求；Team 板仍严格匹配冻结 Worker；无法读取 Assistant 定义时拒绝；越权错误返回候选目录供协调者重试
    - 决策（2026-10-12）：执行者 Role/Skill 暂不由协调者指定，默认继承协调者；不做候选目录只读工具，也不做代码选择。`delegate_agents` 仍保留可选 `roleKey`/`skillKey` 与 `requireDelegable` 校验，作为后续按需启用的入口。静态核对缺口已修：工具集越权错误同样附候选目录
    - 已知限制：保存工具要求已按交付角色推导（见 #11710），执行者不再要求 Skill 声明保存工具，本条原限制不再适用
    - verify: AUTO 的 TASK 请求不再触发 `DefaultRoleSelector` 与 `ModelSkillSelectionPort`；越权的 Role/Skill 指定被拒绝

### 聚合

18. ⏳ #11718 聚合要求
    - `delegate_agents` 的聚合合同新增 `requirements` 自由文本（`AGGREGATOR_REDUCE` 必填，默认安全上限 8000 字符，可由请求参数（`ExecutionOptions` 可选字段）和系统配置（`AiProperties`）调整，硬顶 32000 字符），工具入参模式写明应包含的内容，校验失败返回明确错误让协调者重试；`TaskPlanDraft.AggregationContract` 与计划持久化结构同步，核对是否涉及已有表
    - 已实施，待运行验证：`AggregationContract` 增 `requirements`（`TaskPlanDraft`），上限解析为请求参数 / `AiProperties` / 默认 8000 并受 32000 硬顶（`SubmitCoordinationPlanTool.requirementsLimit`），`AGGREGATOR_REDUCE` 缺失或超长在工具层拒绝（`requireAggregationRequirements`）；随计划以 jsonb 原样持久化，无需改表。静态核对缺口已修：`inputSchema` 声明了 `aggregationContract`、`executors.items`、`iterationGroup` 的嵌套 property（含 `requirements`、`roleKey`、`skillKey`）；`TaskPlanDraft.AggregationContract.validate` 同样强制 `AGGREGATOR_REDUCE` 的 `requirements` 非空
    - verify: `AGGREGATOR_REDUCE` 下缺少或超长的 `requirements` 被拒绝并可重试；合法值随计划冻结
19. ⏳ #11719 聚合者输入契约
    - harness 组装聚合者输入：原始用户请求全文（`Task.inputRef`）、协调者 `goal`、聚合要求、计划摘要、各执行者完整结果；聚合者对这些结构化条目允许内容级披露，原始附件仍只给摘要；调整 `TaskPlan.resolveInput` 与 `finalizerDescription`
    - 已实施，待运行验证：`TaskPlan.appendAggregationContext` 在 `AGGREGATOR_REDUCE` 下依次拼入原始请求、`goal`、`requirements`、执行者与依赖摘要及绑定的完整结果；`PASS_THROUGH` 不进入该路径
    - verify: `AGGREGATOR_REDUCE` 的聚合者输入包含原始请求与聚合要求；`PASS_THROUGH` 行为不变

### 产物保存

20. 🚫 #11720 harness 驱动的产物保存（先做可行性验证）
    - 交付节点完成后由 harness 通过 `ToolGateway` 调用业务注册的保存处理器，默认文档草稿 `content.draft.upsert`；协调者不获得保存工具；需确定标题来源、`REQUEST_ON_DEMAND` 下审批的暂停点，并确认 `CompletionValidator` 仍得到 `DRAFT_COMMITTED` 证据
    - 先验证 harness 主动发起工具调用在现有结构里是否可行
    - 验证结论：不可行（低风险前提下），未实施，需人工决策。阻塞点：(1) `ToolGateway` 的授权等待只有一条恢复路径——`resumeApproval` 触发 agent 循环 `RESUME`，由模型重新调用被批准的工具；harness 主动调用遇到 `REQUEST_ON_DEMAND` 时没有"恢复后重做保存"的恢复点，需新增状态机与 `RecoveryPreflight` 分支；(2) `DRAFT_COMMITTED` 证据由 `AgentScopeEventMapper` 在 agent 循环内通过 `evidenceStore` 按 `toolCallId` 取出并写进 `TOOL_CALL_COMPLETED` 事件，harness 旁路调用拿不到这条证据链，需要在节点收口前补发等价事件并保证序号、`nodeIdentity` 与持久化一致；(3) 标题没有可靠来源（请求里只有主题文本），需约定从首行或请求参数取。现状保持不变：协调者/执行者经 Skill 声明的 `content.draft.upsert` 由模型调用保存，授权、审批、审计与完成证据照旧，代价是全文作为工具参数多输出一遍
    - 决策（2026-10-12）：采用方案 (c) 维持现状，不做 harness 驱动保存；#11710 披露放宽与 step-planning 范围已人工确认
    - verify: 文案生成完成后产生文档草稿，前端能收到产物引用；模型不再把全文作为工具参数重复输出一遍

### 过程可视化

21. ⏳ #11721 节点过程事件
    - 内部节点的文本增量增加带文本的过程事件（`CUSTOM`，按 `nodeId` 归类），仅走实时通道不落库；交付节点仍走标准 `TEXT_MESSAGE_*`；确认文本披露仅限任务所有者
    - 🔴 涉及公共事件脱敏规则变更，需人工确认
    - verify: 委派任务中每个执行者的输出能逐段到达前端；其增量文本不出现在持久事件中（`MESSAGE_COMPLETED` 的完整文本仍按 #11701 落库）
    - 已实施，待运行验证：`InternalNodeEventConverter.appendLiveDelta` 为 `aaf.node.message` 补回 `delta`，`ExecutionEventPublicMapper.safeData` 的公共投影只保留 `contentLength`，增量因 #11701 不落库。静态核对缺口（部分缓解）："仅任务所有者可见"仍未在实时端口层强制——通道按 `tenantId|taskId` 分区，`taskId` 取自客户端 `runId`；已核对三个 `open` 调用点在转发给客户端前均已完成归属校验：入口以"Task 已存在即失败"保证提交成功者即创建者，审批恢复订阅前校验审批用户，澄清恢复订阅仅在归属校验通过的 submit 成功后消费（失败时主动释放）。端口层按 `userId` 隔离不可行（部分 ownerType 为空），不改端口签名，Javadoc 声明"归属由调用方保证"；残留风险与确认请求登记在 improvements（待协调者确认，未运行验证）
22. ⏳ #11722 前端按节点分组的折叠展示
    - `use-copywriting.ts` 的 `recordEvent` 与文案页"执行过程"按 `nodeId` 分组，默认折叠，展开后流式显示该节点输出；只有交付节点输出作为最终结果与保存产物
    - verify: 多执行者任务展开任一节点能看到其实时输出；折叠状态下不影响正文区
    - 已实施，待运行验证：节点输出按 `nodeId` 分组、默认折叠、流式显示；正文区只消费交付节点的 `TEXT_MESSAGE_*`；内部节点工具事件的脱敏 data 按节点记录 `artifacts`，不覆盖页面当前文档（`assistantDraftArtifact`）；任一节点的授权 / 澄清等待在 AG-UI 投影为标准 interrupt（`AafAguiConverterRegistry`）；任务面板 `TaskPanel` 计划区可在"文字 / 依赖图"间切换（`TaskPlanGraph.tsx`，React Flow，按依赖深度分层、状态着色、点击节点看详情）。已知限制：任务面板为轮询投影，不含实时输出，实时输出详情只在文案页节点面板；`copy` 页以 `decomposition: NONE` 调用，委派场景由通用任务入口承载；文案页对 `input_required` 澄清类 interrupt 不再按授权展示，改为提示"需要补充信息"并进入错误态（`use-copywriting.ts` `onDone` 仅 `tool_call` 视为授权；本页为 `FAIL_ON_BLOCKER`，正常不会出现澄清 interrupt）

### 文档对齐

23. ⏳ #11723 设计文档同步
    - 协调者修改 `runtime.md`、`coordination.md`，删除"协调者简单直答、`TaskBoard.single` 已删除"等与 AAF-115 代码不一致的描述；`improvements.md` 记录冲突；ADR-008 经审核后改 `accepted`，并在 ADR 索引更新状态
    - verify: 文档与代码在协调者形态、工具名、技能名上一致
    - 开发侧已完成：冲突与待决策项登记在 `docs/prd/improvements.md`；`runtime.md`、`coordination.md`、ADR-008 状态与索引由协调者修改，本任务保持 ⏳ 直到协调者完成
24. ⏳ #11724 拒绝授权反馈并续接节点
    - AG-UI 拒绝决定支持可选反馈，后端上限 1000 个 Unicode 字符，超限直接拒绝；反馈复用 `HumanApproval.decisionReason` 与决定事件 payload，不新增列
    - 拒绝与批准同样把 Task、Execution 和发起节点恢复为 READY / DISPATCHED，并打开任务恢复流触发 `RESUME`
    - 网关在当前 node execution 内对同资源返回最近拒绝反馈，不重复创建审批；同 nodeId 同 action 累计拒绝两次后以 `IllegalStateException` 交给既有失败重试收口
    - 已实施，待运行验证：`DefaultToolGateway.priorRejection`、`JpaHitlTransitionAdapter.decideAuthorization`、`PersistentHitlCoordinator.decideDelegated`、`AssistantAguiController.resumeApproval`（反馈按 code point 计数，超 1000 直接拒绝）；前端 `use-copywriting.ts` / `copy/page.tsx` 提供反馈输入。静态核对缺口已修：(1) `resumeApproval` 先校验审批属于当前用户并 `approvalEvents.open`，再 `hitl.decide`，决定失败时释放订阅（`AssistantApprovalEventService` 删去不再使用的 `open(HumanApproval)` 重载）；(2) 前端反馈输入改按 code point 截断与计数（`clampApprovalFeedback`）
    - verify: 拒绝后节点收到工具错误并可继续调整；反馈公共投影最多 1000 字符；第二次拒绝后节点失败而非循环申请

## 后续评估（不在本故事范围）

- 协调者挂起并恢复（用 `RESUME` 与 `stateSlot` 恢复同一协调者聚合，取消独立聚合者节点）：见 ADR-008"聚合方式"
- 循环内的短时并行委派模式（类似 AgentScope `agent_spawn` 加 `wait_async_results`）
- 领域模型 `ExecutorPlan`、`ExecutorPlanStep`、事件类型 `EXECUTOR_PLAN_*` 的重命名
- 实时事件端口的多实例实现（Redis Streams）
- DIRECT 路径的 Role/Skill 选择并入协调者

## 新增任务

> 开发过程中发现需要新增的任务，由开发者提出，协调者评估后写入

<!-- 格式同上，追加到任务列表 -->

## 评审状态

> 🔴 高风险：协调者派发下一阶段前检查上游状态为 ✅ CLEAR。本故事不单独写需求与设计文档，product 与 architect 阶段以 ADR-008 和本文件为准。

| 阶段 | 执行次数 | 最后执行 | 状态 | 必须 |
|------|---------|---------|------|------|
| product（需求细化） | 0 | — | ✅ CLEAR（免：任务说明即口径） | 免 |
| architect（技术设计） | 1 | 10-12 | ✅ CLEAR（用户 10-12 确认 ADR-008 并指示开始实现；ADR 状态保持 proposed，待 #11723 一并改 accepted） | 🔴 是 |
| designer（UI 审查） | 0 | — | ⏳ PENDING | 涉及前端时 |
| developer（编码） | 1 | 10-11 | 🔄 IN_PROGRESS（#11701 至 #11704 已实施待验证） | 🔴 是 |
| architect（代码审查） | 0 | — | ⏳ PENDING | 🔴 是 |
| tester（验收测试） | 0 | — | ⏳ PENDING | 🔴 是 |
| qa（过程审计） | 0 | — | ⏳ PENDING | 🔴 是 |

<!-- 状态：⏳ PENDING | 🔄 IN_PROGRESS | ✅ CLEAR | ❌ BLOCKED | ↩️ RETURNED -->
<!-- 任务状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 | ❌ 已取消 | 🚫 阻塞中 -->
