---
level: Practice
layer: Product
purpose: AAF-114 助理对话框优化技术任务
status: active
version: 1.0.0
date: 2026-09-05
author: AaronZZH & Kiro
tags:
  - AAF-114
  - Chatter
  - 技术任务
related:
  - requirement.md
  - design.md
---

# AAF-114 助理对话框优化任务

## 任务约束

- 需求真理源：[requirement.md](requirement.md)。
- 技术与交互设计：[design.md](design.md)。
- 复用既有 Assistant、Role、Model、TaskBoard、ExecutorPlan 与 reasoning 状态，不建立第二套状态源。
- 参考 `tmp/assistant-ui` 的 Attachment、Composer、Reasoning 与 AG-UI runtime 实现，不孤立照搬示例。
- 用户明确要求本轮不执行 lint、测试或 check；仅做静态复核并披露风险。

## 技术任务

### #11401 产品/UI/技术契约

- **优先级**：P0
- **依赖**：无
- **状态**：✅ 已完成（2026-09-05）— product / architect / designer / qa
- 固化角色原子选择、附件真实发送、计划/思考安全语义和 320px 布局约束。
- 完成需求、技术设计及跨角色审阅。

### #11402 助理与角色标题选择

- **优先级**：P0
- **依赖**：#11401
- **状态**：✅ 已完成（2026-09-05）— developer-webui
- 使用 `/ai/assistants/available` 生成按 Assistant 分组的 Role 选项。
- 标题展示“助理名称 · 角色”，选择时原子更新 `assistantId + roleKey` 并清除 Skill。
- 删除前端 fallback Role，不把服务端 resolved Role 写回请求 target。

### #11403 Composer 模型、附件和模式控制

- **优先级**：P0
- **依赖**：#11401
- **状态**：✅ 已完成（2026-09-05）— developer-webui
- 模型 AUTO 入口改为带图标的“自动”。
- 启用 AddAttachment、AttachmentDropzone 及图片/文本组合 adapter。
- 增加计划、思考展示偏好 Toggle 和准确 Tooltip。

### #11404 AG-UI 附件与偏好接线

- **优先级**：P0
- **依赖**：#11402、#11403
- **状态**：✅ 已完成（2026-09-05）— developer-webui / developer-service
- `showThinking` 贯通 Chatter → AgUiChatProvider → `useAgUiRuntime`。
- `showPlan` 只控制 TaskBoardPanel 可见性。
- 后端从 AG-UI 最后一条 user 消息解析图片文件 key，进入既有 `AssistantExecutionRequest.Attachment`。
- 补齐 `aaf.executor_plan.*` 前端事件类型。

### #11405 静态复核与任务记录

- **优先级**：P0
- **依赖**：#11402、#11403、#11404
- **状态**：✅ 已完成（2026-09-05）— developer-webui / architect / qa
- 复核导入、props、唯一 key、Base UI `render` 组合、窄宽布局和单一状态源。
- 更新任务状态与 dev-log。
- 不运行 lint、test、typecheck、build 或 check，记录未验证风险。

## 评审状态

| 阶段 | 执行次数 | 最后执行 | 状态 | 说明 |
|------|---------|---------|------|------|
| product（需求细化） | 1 | 2026-09-05 | ✅ 完成 | 已定义 Story 与 Gherkin AC |
| architect（技术设计） | 1 | 2026-09-05 | ✅ 完成 | 已冻结状态边界与附件协议 |
| designer（UI 审查） | 1 | 2026-09-05 | ✅ 完成 | 已约束标题层级与 320px 布局 |
| developer（编码） | 1 | 2026-09-05 | ✅ 完成 | #11402～#11404 已实现 |
| architect（代码审查） | 2 | 2026-09-05 | ✅ 静态通过 | blocker=0，代码问题已修复；自动化验证缺失 |
| tester（验收测试） | 0 | — | ⏭️ 跳过 | 用户明确要求不运行测试 |
| qa（过程审计） | 2 | 2026-09-05 | ✅ 静态完成 | 已记录门禁偏离和剩余风险 |

## 新增任务

> 开发过程中发现需要新增的任务，由协调者评估后写入。


## 后续集成任务

> 以下任务来自 2026-09-05 对 `tmp/agentscope-java` 与 `tmp/assistant-ui` 的二次核对。本轮只建立计划，不开始编码。#11407、#11408、#11410、#11411 涉及跨模块协议、安全或持久化，均按 🔴 高风险执行，开发前须完成人类设计审核。
>
> 实现任务 #11406～#11412 各自交付时必须包含对应单测、静态安全检查和 affected 门禁；纯评估任务 #11413 以架构、设计、QA 和人类安全评审记录为 DoD。#11414 只负责跨任务最终回归与验收，不替代各任务 DoD。

### #11406 AgentScope 多模态契约与视觉模型门禁

- **优先级**：P0
- **风险**：🟡 中
- **状态**：✅ 已完成（2026-09-06）— developer-service
- **依赖**：#11404
- 保持 `DataBlock(URLSource)` 路径，不改回兼容 `ImageBlock`。
- 使用不泄露存储布局的 opaque resource ID，将文件名和 MIME 稳定关联到 AAF 持久层、`AgentMessage`、AgentScope `Msg` 与审计映射。
- AUTO 模式继续使用 `hasImage → VISION`；EXPLICIT 模式执行前由服务端校验模型 VISION 能力，前端同步标识或禁用不兼容模型。
- 分别验证 OpenAI、DashScope、Gemini、Anthropic formatter 的 URLSource、MIME、媒体内容和过期 URL 重签；不要求 provider payload 回显 `DataBlock.id/name`。
- **完成标准**：AAF 全链路可按 opaque ID 审计附件；四类 provider 正确接收媒体与 MIME；非视觉显式模型确定性失败且错误可理解。

### #11407 消息 Part 投影、UI Block 协议与安全 allowlist

- **优先级**：P0
- **风险**：🔴 高 — 人类设计审核后开发
- **状态**：✅ 已完成（2026-09-06）— architect / developer-webui
- **依赖**：#11404
- 明确 AG-UI CUSTOM transport event 与 assistant-ui message part 是两层对象，建立唯一投影适配器。
- 规定映射优先级：原生 source/file/image、已知 tool-call、版本化 data、展示型 generative-ui；citations 不自造通用 data 卡片。
- 定义 `parentMessageId`、稳定 partId、schemaVersion、`eventId + eventOffset/revision` 去重、顺序、重放、payload 大小和 fallback。
- 定义 `AafUiBlock v1`：`INFO_CARD` 只能转换为 allowlisted GenerativeUISpec；`CHOICE`/`FORM` 只投影 canonical Clarification。
- 扩展 Clarification 契约设计：字段 type/required/options/multiple/constraints、typed value union 与版本策略，禁止用 JSON 字符串冒充数组或布尔值。
- 禁止直接传组件代码、任意 HTML、动态 import、后端指定 React 实现或未校验 URL。
- **完成标准**：协议评审通过；未知类型、重复/乱序事件、越权提交、非法 URL/props 和未知版本均有确定性处置。
- **实现落地与对原设计的调整**：实现过程中确认 react-ag-ui 0.0.41 的 `RunAggregator` 不支持外部注入 message part 到 `ThreadMessage.content`（第三方库内部限制，见 `run-aggregator.ts` 的 `default` 分支仅 debug 忽略 CUSTOM）。据此把 `AafUiBlock` 投影目标从"assistant-ui DataMessagePart"调整为复用既有 `agent-run-store` Zustand 瞬时展示状态通道（与 `aigcTasks` 同模式），通过独立 `UiBlockPanel` 组件渲染，接入位置与 `TaskBoardPanel` 同级。协议层（类型定义、运行时校验、eventId 去重、32KB 大小限制、未知类型 fallback）按设计原样落地。`CHOICE`/`FORM` 本轮只做只读展示 + 本地未提交草稿，提交闭环留给 #11408。新增文件：`apps/webui/src/features/chatter/runtime/ui-block/{aaf-ui-block.ts,ui-block-projector.ts,UiBlockPanel.tsx}`。

### #11408 对话内选择、参数表单与 Clarification 闭环

- **优先级**：P0
- **风险**：🔴 高 — 人类设计审核后开发
- **状态**：✅ 已完成（2026-09-07）— developer-service / developer-webui
- **依赖**：#11407、#11411、AAF-104 中断恢复能力
- 在既有 `ClarificationRequest`、`ExecutionInput` 和前端输入契约中实现 typed schema/value，不建立 UI Block 生命周期表。
- 首批字段支持 text、textarea、number、select、checkbox；支持单选/多选、必填和服务端约束校验。
- FORM/CHOICE 绑定 canonical clarification/request identity，提交复用 `delegatedTaskApi.submitInput(taskId, { inputId, values })`。
- created/updated/resolved/canceled/expired 只由 Clarification 事务和事件产生；前端仅保存未提交 draft。
- 保留现有 Tool 授权确认，不把授权与参数澄清合并成同一状态机。
- **完成标准**：选项、typed 参数、幂等提交、刷新/切线程恢复和过期五条路径均可验收。
- **设计产出**：详见 [design.md #11408 详细设计（第二版）](design.md#11408-详细设计对话内选择参数表单与-clarification-闭环)。**第一版设计已作废**——初版方案（`ClarificationRequest.Question` 元数据扩展 + 独立 `/inputs` REST 端点 + `render_ui_block` 工具承载 CHOICE/FORM）在用户提示核对 AG-UI 官方标准后确认方向错误。核实发现：`react-ag-ui`（AAF 实际依赖）原生支持标准 AG-UI interrupt 协议（`AgUiInterrupt{id,reason,responseSchema,...}`、`unstable_getPendingInterrupts`/`unstable_submitInterruptResponses`），AAF 后端 `RunLifecycleEventConverter` 已把 `AUTHORIZATION_REQUESTED` 投影为这种标准 interrupt（`reason="tool_call"`），`reason` 官方标准值另含 `"input_required"`——正是 Clarification 场景。第二版改为：`CLARIFICATION_REQUESTED` 投影为 `reason="input_required"` interrupt，`responseSchema` 从 `questions()` 生成 JSON Schema；`resumeRun` 按 `interruptId` 归属分派到 `HitlCoordinatorPort`（approval）或 `DelegatedTaskCoordinator.acceptInput`（clarification）；`AssistantApprovalEventService` 泛化出 `streamByExecutionId` 供两者共用事件续读；前端移除 `render_ui_block` 的 CHOICE/FORM 分支，新增 `ClarificationInterruptPanel` + `SchemaForm` 用官方 API 处理。已知限制：不支持多轮部分补充（首批要求一次性填满必填字段）；interrupt 状态未持久化，刷新后不会重放（与 #11411 遗留限制同根因，不在本任务修复）。**尚未编码实现**，等待人类审核后转入开发。

### #11409 Tool UI、Data UI 与信息卡片注册表

- **优先级**：P1
- **风险**：🟡 中
- **状态**：✅ 已完成（2026-09-07）— developer-webui / developer-service
- **依赖**：#11407、#11411
- 扩展现有 AIGC toolkit，按已知 backend tool 注册 loading/success/error UI，不用通用字符串卡片替代所有工具。
- sources/citations 优先渲染原生 `SourceMessagePart`；为剩余结构化事实注册版本化 Data UI renderer。
- `INFO_CARD` 通过唯一转换器生成 allowlisted Generative UI；交互控件仍走 Tool UI/Clarification。
- TaskBoard、ExecutorPlan 和 Clarification 卡片只携带 canonical ID 与 revision/eventOffset，从权威服务端投影读取实时状态。
- 未注册 CUSTOM/part 进入可诊断 fallback，但过滤 framework plumbing 和敏感 payload。
- **完成标准**：至少交付信息卡、来源导航、任务摘要和通用工具错误四类组件，且无 durable Zustand 状态副本。
- **实现落地**：信息卡复用 #11407 的 `AafUiBlockCard`/`render_ui_block` 工具。来源导航新增 `ChatterThread.tsx` 的 `case "source"` 分支 + `SourceLink` 组件，直接消费 assistant-ui 原生 `SourceMessagePart`（url/document 两种 `sourceType`），未自造 data 卡片协议。任务摘要发现后端缺口——`ExecutorPlan` 此前只有数据库表定义，无任何 REST 查询端点；新增 `ExecutorPlanQueryService`（遍历 `TaskBoard` 子任务查 `findActive`）+ `ExecutorPlanSummaryVO`（只携带 canonical `planId`/`revision`）+ `DelegatedTaskController` 新端点 `GET /ai/tasks/{taskId}/executor-plans`；前端 `ExecutorPlanSummary.tsx` 用 TanStack Query 轮询（3s，仅非终态任务），嵌入 `TaskBoardPanel.TaskItem`。通用工具错误增强 `ToolFallback` 区分 running/error/complete 三态，error 态判定依据 `status.type==="incomplete" || isError===true`，只展示确定性文案不回显 `status.error`/`result` 等敏感字段。

### #11410 文档附件服务端处理与生产上传体验

- **优先级**：P1
- **风险**：🔴 高 — 人类设计审核后开发
- **状态**：✅ 已完成（2026-09-07）— developer-service / developer-webui
- **依赖**：#11406、#11411
- **设计产出**：详见 [design.md #11410 详细设计](design.md#11410-详细设计文档附件服务端处理与生产上传体验)。核心方案：新增 `DocumentAttachmentGuardService` 作为解析前门禁（owner/状态/扩展名/MIME/魔数/大小/安全扫描），不改动现有 `ImporterFactory`/`DocumentImporter` 接口，只在各 importer 内部加固页数/ZIP 炸弹/超时限制；新增 `FileSecurityScanPort`（`aaf-framework`）+ 首批 `HeuristicFileSecurityScanAdapter` 启发式实现（不接入外部病毒扫描引擎，已知局限已披露）；新增 `DocumentTokenBudgetSplitter` 做 Token 预算裁剪与 provenance 记录；文档解析结果统一以既有 `Attachment(TEXT,...)` 语义进入执行链路，不扩展 `AttachmentType` 枚举。前端复用 `CompositeAttachmentAdapter` 模式新增 `OssDocumentAttachmentAdapter`。
- **实现落地与对原设计的调整**：实现过程中通过读取 `react-ag-ui` 源码（`conversions.js`）发现设计假设有误——前端 `file` content part 经转换层按 mimeType 分流后，非图片文件在 AG-UI 协议层的 `type` 字段实际是 `"document"`，不是原设计假设的 `"file"`；已同步修正 `AssistantAguiController.messageInput` 的分流判断（`"document".equals(type)`），避免了一个会导致文档附件永远无法被识别的隐藏缺陷。`HeuristicFileSecurityScanAdapter` 的 ZIP 解压比检测原实现依赖 `ZipEntry.getSize()`，经 shell 实测验证该字段在 `ZipInputStream` 顺序读取 DEFLATED 压缩条目（DOCX 默认压缩方式）时恒为 -1，导致检测在真实场景下完全失效；已修正为边解压边用 buffer 累计实际读出字节数、超阈值提前终止，与 POI `ZipSecureFile` 的检测方式一致，并用构造的高压缩比 ZIP 验证新逻辑生效。`WordImporter` 的 ZIP 炸弹防护改为依赖 POI 内置默认阈值（1% = 100:1，与设计默认值一致），不主动调用全局静态 `ZipSecureFile.setMinInflateRatio`，避免虚拟线程并发场景下的状态竞争。已完成后端单测覆盖 guard 校验分支、五个 importer 加固边界、splitter 截断逻辑与安全扫描适配器；按用户指示本轮不编写前端单测、不执行 `pnpm check:affected`（`OssImageAttachmentAdapter`/`OssDocumentAttachmentAdapter` 已加 `export` 关键字以支持后续测试，是唯一保留的前端改动）。
- 对话附件扩展 PDF、DOCX、Markdown、HTML、TXT；在 importer 前增加受控解析 façade 并复用 `ImporterFactory`，禁止另建解析器。
- 校验 owner、状态、扩展名/MIME/魔数一致性、大小、加密/损坏状态；通过 `FileSecurityScanPort` 接入经审核的扫描实现，只有 `CLEAN` 文件可进入 importer，`PENDING`、`UNAVAILABLE`、`INFECTED`、`ERROR` 一律隔离或拒绝，禁止绕过。
- 限制 PDF 页数、DOCX ZIP entry/解压比、总字符、单段长度、超时和内存，禁止 XXE 与外链资源；不安全输入不得 fallback 到纯文本。
- 解析结果清理 Unicode/HTML/空白/控制字符，按单文件、总请求和 Token 预算分块、筛选或生成可审计摘要。
- 输出记录 opaque resource ID、content hash、importer 名称/版本和截断/摘要 provenance；TaskMaterial 始终是不可信上下文。
- 前端补上传进度、取消、失败重试、总数量/总大小限制和可访问预览；已发送附件不可静默删除。
- **完成标准**：五类文档有明确成功/失败路径；超限、加密、损坏和恶意文件不进入模型；附件失败与重试状态可见。

### #11411 完整消息信封、ThreadList 与可恢复运行

- **优先级**：P0
- **风险**：🔴 高 — 人类设计审核后开发
- **状态**：✅ 已完成（2026-09-06）— developer-service / developer-webui
- **依赖**：#11407
- 定义版本化 `AafThreadMessageEnvelope`，持久化 assistant-ui part union、message status/timing、interrupt、branch/parent、feedback、附件 resource ref 和 AAF metadata。
- `fromAgUiMessages` 仅作为 text/tool/reasoning/用户附件基础转换器；实现 AAF rehydrator 与 source/data/generative-ui/interrupt/branch codecs。
- 服务端消息信封是唯一历史真理；runtime 是投影。未知版本/part 可诊断显示且不静默丢弃，不把加载失败显示为空会话。
- 用 ThreadList/runtime adapter 替换而非并存现有 SessionPopover，实现新建、真实切换、重命名、搜索、归档/删除、运行/未读状态和线程分页。
- active thread 只由 adapter 管理；图片只持久 file key/opaque ref 并按 owner 重签 URL，不持久 presigned URL。
- 支持单线程消息分页或虚拟化，以及 in-flight run cursor replay、重复 chunk 去重和断线/刷新恢复。
- **完成标准**：复杂 part 会话在刷新、切换和流式恢复后语义等价；错误态可重试且不会伪装为空会话。
- **实现落地与对原设计的调整**：实施过程中发现比设计预期更严重的现有缺陷——`UserMessageEvent` 在全代码库无任何 publisher，AG-UI 主对话链路完全未写入 `ConversationMessage` 表，`chatApi.getMessages` 对 AG-UI 会话历史查询实际上永远返回空。已修复：`ChatService` 新增 `saveMessageByThreadId`，`AssistantAguiController.startRun` 接入 `persistUserMessage`（run 开始时保存用户消息）与 `persistAssistantMessageIfCompleted`（监听 `MESSAGE_COMPLETED` 事件保存 AI 回复全文）。`SessionPopover` 新建/切换按钮改为真实调用 `runtime.threads.switchToNewThread()`/`switchToThread(threadId)`，新增重命名/归档/删除操作菜单（对应后端已存在的 API，前端补充 `chatApi.renameSession/archiveSession/deleteSession` 封装）。`onSwitchToThread` 加载失败不再吞异常伪装空会话，改为成功后才切换 `currentThreadId`。**`AafThreadMessageEnvelope` 完整信封格式（tool-call/reasoning/attachment/branch 的结构化 JSON payload）本轮未实现**——鉴于消息持久化本身此前完全缺失，先接通纯文本持久化是更紧迫的修复；信封化改造收益需要在有真实 tool-call/attachment 历史需求时再评估，作为后续任务处理，不在本轮引入未经验证的复杂度。分页/虚拟化保持设计阶段的留白判断，未实现。

### #11412 反馈、输入历史、建议与可观测体验

- **优先级**：P2
- **风险**：🟡 中
- **状态**：✅ 已完成（2026-09-07）— developer-service / developer-webui
- **依赖**：#11411
- 接入 FeedbackAdapter 与正/负反馈 ActionBar，服务端记录 messageId、原因、model/runId 和执行版本。
- 封装 `unstable_useComposerInputHistory`，支持空输入时上下键召回且不干扰 IME、编辑态和建议弹层。
- 在现有 suggestion primitive 上增加标题/描述、fill-only/auto-send、对话后续建议和恢复语义，不重复建设建议状态源。
- 提供受控的 model、latency/timing、token/credit、runId 诊断入口，默认不暴露敏感 provider metadata。
- **完成标准**：反馈可追溯；输入历史无数据丢失；建议行为明确；诊断元数据符合权限与脱敏要求。
- **实现落地与对原设计的调整**：调研过程中发现比预期更深的架构问题——AG-UI 前端 `ThreadMessage.id`（`{replyId}:{blockId}`，`TextMessageEventConverter` 派生）与后端此前持久化时机（`MESSAGE_COMPLETED`）payload 中的 `messageId`（AgentScope `Msg.id`，独立随机 UUID）是两个完全不相关的值（已用本地 `agentscope-java` 源码验证 `Msg.id`/`replyId` 各自独立生成，无关联关系），此前 #11411 落地的纯文本持久化没有建立任何跨端消息标识映射。修复：`AssistantAguiController.startRun` 改为按 `MESSAGE_DELTA` 累积文本、在 `MESSAGE_BLOCK_COMPLETED`（携带 `replyId`+`blockId`）时落库，`ChatService.saveMessageByThreadId` 新增 6 参数重载把 `externalMessageId` 写入 `ConversationMessage.payload`（`{"aguiMessageId": "..."}`），`ConversationMessageRepository` 新增 `findByConversationIdAndAguiMessageId`（PostgreSQL JSONB `->>` 查询）建立反查。反馈不新建表，新建 `MessageFeedbackService`（`chat.message.service`）写入既有 `metadata` 列；删除死代码——旧 `ChatController.messageFeedback`（`Long messageId` 路径，前端零调用，与 AG-UI 字符串 ID 体系不兼容）按禁兼容层原则直接移除不保留双路径，新端点 `POST /sessions/thread/{threadId}/messages/{aguiMessageId}/feedback`。诊断入口过程中发现并修复真实缺陷：`ExecutionEventPublicMapper.safeData` 白名单原本缺失 `MODEL_CALL_COMPLETED` 分支，`modelId`/token/耗时数据永远无法透出到公开 CUSTOM 事件；已补齐分支与 `copyDouble` 辅助方法。前端确认 CUSTOM 载荷信封结构（安全字段在 `value.data`，与既有 `aaf.role.resolved` 读取方式一致）后接入 `agent-run-store.diagnostic`，`DiagnosticInfoButton` 只在 `message.isLast` 时展示避免历史消息误显示。建议行为增量（标题/描述/autoSend）与输入历史封装均为薄接线，未发现架构问题。已知限制：#11411 遗留的完整消息信封（tool-call/attachment 结构化 payload）仍未实现；"对话后续建议"的后端动态生成逻辑（调模型/规则引擎）超出本轮范围，只交付前端展示能力。按用户指示本轮不新增数据库迁移、不执行 `pnpm check:affected`、不编写测试。

### #11413 Interactables、MCP Apps 与页面协同评估

- **优先级**：P2
- **风险**：🟡 中（仅评估）
- **状态**：✅ 已完成（2026-09-07）— architect
- **依赖**：#11407、#11411
- **评估产出**：详见 [design.md #11413 评估](design.md#11413-评估interactables、mcp-apps-与页面协同)。Interactables 关键发现（读 `@assistant-ui/core` 源码确认）：AI 更新通过框架自动生成的 `update_{name}` 前端工具直调 `setDefState`，**完全绕过服务端，无内建 HITL/权限拦截点**；AAF 代码库当前零使用。矩阵结论：仅适用于纯前端 UI 偏好/未提交草稿/无需审批字段，禁用于服务端权威实体（TaskBoard/ExecutorPlan/Clarification）与需审批的敏感操作。MCP Apps（SEP-1865）经调研确认是 2025-11 提出的协议**草案**扩展，与 AAF 已有的标准 MCP 工具调用能力（`McpConnectionService`）是不同能力层；结论为草案阶段+无业务场景驱动，本轮不深入接入细节，暂不实现。低风险试点方案：`features/aigc/copywriting/CopywritingParamsBar.tsx`（Zustand 管理的 3 个纯 UI 参数），范围严格限定不含持久化、不接入 `CopywritingEditor`/`StoryboardPanel`。
- 评估表单预填、画布、项目面板等页面外组件是否适合 assistant-ui Interactables。
- Interactable 只保存页面 UI 草稿；服务端实体、TaskBoard、ExecutorPlan 不复制进本地状态。
- AI 更新必须调用受权工具并经过现有权限/HITL，不允许前端自动生成工具绕开策略。
- 单独评估 MCP Apps/interactive resource 的 URI、sandbox、权限、内容安全和持久化边界，不与 unrestricted Generative UI 混用。
- **完成标准**：产出 Interactables 与 MCP Apps 的适用/禁用矩阵及一个低风险试点设计，通过 architect、designer、qa 和人类安全评审；不在评估任务中直接实现或推广。

### #11414 跨任务最终回归与质量门禁

- **优先级**：P0（最终收口）
- **风险**：🔴 高 — 发布门禁
- **状态**：⏳ 待开始 — tester / qa
- **依赖**：#11406～#11413 全部完成
- 汇总各任务单测和 affected 门禁证据，执行跨任务集成、完整 check 与 acceptance。
- 验收覆盖 DataBlock、VISION 门禁、消息 part 投影、typed Clarification、文档安全、完整历史、320px、键盘/读屏、断线恢复、跨用户 key、过期 URL 和多 provider。
- 检查 schema 版本迁移/拒绝、事件去重、加载错误、权限/HITL 和单一状态源。
- **完成标准**：blocker=0、major≤2，完整自动化门禁与验收报告均有可引用证据。

## 后续实施顺序

```text
并行契约：#11406 || #11407
基础历史：#11407 → #11411
P0 交互：#11407 + #11411 → #11408
P1 展示：#11407 + #11411 → #11409
P1 文档：#11406 + #11411 → #11410
P2 体验：#11411 → #11412；#11407 + #11411 → #11413
最终门禁：#11406～#11413 → #11414
```
