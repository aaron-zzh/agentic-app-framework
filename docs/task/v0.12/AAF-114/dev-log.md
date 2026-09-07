# AAF-114 开发记录

## #11401 产品/UI/技术契约

✅ 2026-09-05 — product / architect / designer / qa

- 完成 Gherkin 验收标准
- 冻结单一状态源边界
- 明确附件真实发送协议
- 计划开关不强制规划
- 思考开关不展示原始 CoT

## 验证约束

- 用户明确要求跳过 lint、测试和 check。
- 本轮仅执行源码与 diff 静态复核。
- 未经自动化验证，不满足常规完工门禁；最终汇报必须披露风险。


## #11402 助理与角色标题选择

✅ 2026-09-05 — developer-webui

- 按 Assistant 分组 Role
- 原子绑定 Assistant 与 Role
- 清除未校验 Skill
- 移除虚构 Role fallback
- resolved Role 不回写请求

## #11403 Composer 模型、附件和模式控制

✅ 2026-09-05 — developer-webui

- AUTO 精简为图标“自动”
- 启用附件按钮与 Dropzone
- 组合图片与文本 adapter
- 新增计划思考 Tooltip Toggle
- 保留语音输入与窄宽布局

## #11404 AG-UI 附件与偏好接线

✅ 2026-09-05 — developer-webui / developer-service

- showThinking 贯通摄取与渲染
- showPlan 仅隐藏 TaskBoard
- 图片 key 进入既有附件请求
- 校验 owner/MIME 并统一文件错误码
- 补齐 ExecutorPlan 事件类型

## #11405 静态复核与任务记录

✅ 2026-09-05 — developer-webui / architect / qa

- 两轮独立静态审阅
- 修复角色双真理源风险
- 修复跨用户文件访问风险
- LSP 改动文件无诊断
- 自动化门禁按用户要求跳过

## #11406 AgentScope 多模态契约与视觉模型门禁

✅ 2026-09-06 — developer-service

- EXPLICIT+图片走 CAP_VISION 校验，堵住非视觉模型漏洞
- DataBlock.id/name 改用 opaque fileKey 与原始文件名，不再用随机 UUID
- VisionAttachment/AgentMessage.Attachment 新增 fileName 贯通
- 前端含图片时按 capabilities 过滤模型列表，仅提示不替代服务端拒绝
- 未运行 lint/test/check，按用户要求跳过自动化门禁

## #11407 消息 Part 投影、UI Block 协议与安全 allowlist（设计阶段）

📝 2026-09-06 — architect

- 三份调研报告：AG-UI CUSTOM 现状、Clarification 契约、assistant-ui 官方参考
- 投影适配器复用 assistant-ui 原生 DataMessagePart/GenerativeUIMessagePart，不新建平行 part 体系
- AafUiBlock v1 三类型（INFO_CARD/CHOICE/FORM）已给出 TS 接口
- Clarification 扩展为 sealed FieldValue 联合类型，禁止字符串冒充数组/布尔值
- 安全 allowlist 沿用官方 Data UI name 白名单 + Generative UI component allowlist 模式
- 设计已写入 design.md，🔴高风险任务，等待人类审核后再进入编码

## #11407 实现落地

✅ 2026-09-06 — developer-webui

- 发现 react-ag-ui 0.0.41 RunAggregator 不支持外部注入 message part，调整为 Zustand 展示通道
- AafUiBlock 类型定义 + parseAafUiBlock 运行时校验（拒绝非法结构/未知版本）
- UiBlockProjector：eventId 去重 + 32KB payload 大小限制 + 未知类型 fallback
- UiBlockPanel：INFO_CARD 只读展示，CHOICE/FORM 只读展示+本地草稿（不接提交）
- 接入 ChatterPanel，与 TaskBoardPanel 同级；ag-ui-runtime 复用现有 ui_block 事件名新增判别分支
- CHOICE/FORM 提交闭环留给 #11408，未运行 lint/test/check

## #11411 完整消息信封、ThreadList 与可恢复运行（设计阶段）

📝 2026-09-06 — architect

- 三份调研报告：历史加载链路、SessionPopover/会话API现状、assistant-ui ThreadList/History参考
- 发现两个阻塞性现有缺陷：ChatPersistenceListener UUID/Long解析崩溃致消息丢失；SessionPopover新建/切换未接线
- AafThreadMessageEnvelope数据模型：sealed EnvelopePart + BranchRef，复用ConversationMessage现有payload字段
- AAF rehydrator：未知schemaVersion/part走确定性fallback，不静默丢弃
- 断线恢复方案：接入官方resumeInFlightRun/ThreadHistoryAdapter.resume()（当前完全未使用）
- ThreadList真实接入：复用后端已存在的rename/archive/delete API，只改前端接线
- 分页/虚拟化列为设计留白，本轮不强制交付；设计已写入design.md，等待人类审核

## #11411 实现落地

✅ 2026-09-06 — developer-service / developer-webui

- 发现比预期更严重问题：UserMessageEvent无publisher，AG-UI主链路从未写ConversationMessage表
- ChatService新增saveMessageByThreadId；AssistantAguiController接入用户消息+MESSAGE_COMPLETED回复持久化
- onSwitchToThread不再catch吞异常伪装空会话，成功后才切换currentThreadId
- SessionPopover新建/切换接入真实runtime.threads API；新增重命名/归档/删除菜单
- 新增chatApi.renameSession/archiveSession/deleteSession + 3个endpoint定义
- AafThreadMessageEnvelope完整信封格式本轮未实现，判断收益不足以覆盖复杂度，留后续任务
- 分页/虚拟化保持设计阶段留白，未运行lint/test/check

## #11409 Tool UI、Data UI 与信息卡片注册表

✅ 2026-09-07 — developer-webui / developer-service

- 信息卡复用#11407的AafUiBlockCard/render_ui_block工具
- 来源导航：ChatterThread新增case"source"+SourceLink，直接消费原生SourceMessagePart
- 任务摘要：发现ExecutorPlan此前无任何REST查询端点（只有DB表定义）
- 新增ExecutorPlanQueryService+ExecutorPlanSummaryVO+GET /ai/tasks/{taskId}/executor-plans
- 前端ExecutorPlanSummary用TanStack Query轮询(3s)，嵌入TaskBoardPanel
- 通用工具错误：ToolFallback增强区分running/error/complete，不回显敏感payload
- 未运行lint/test/check

## #11408 对话内选择、参数表单与 Clarification 闭环（设计阶段）

📝 2026-09-07 — architect

- 重新核实ClarificationRequest/ExecutionInput/DelegatedTaskInputDTO均为Map<String,String>
- 关键判断：TaskBoard最终把clarifiedParameters拼接成文本插入prompt，typed化对该链路无收益
- 设计决定不改ExecutionInput/TaskBoard/JpaTaskTransitionAdapter类型签名，避免大范围破坏
- Question新增type/required/options/multiple/constraints元数据，带默认值不破坏现有构造
- 发现并设计修复#11407遗留安全缺口：RenderUiBlockTool完全信任模型传入的clarificationId/字段结构
- 设计新增ClarificationQueryPort只读端口，工具改为查真实Clarification并从questions派生字段
- 前端提交走delegatedTaskApi.submitInput，inputId用UUID对齐既有幂等逻辑
- 刷新恢复已知限制：tool-call result不持久化(#11411遗留)，不在本任务修复
- 设计已写入design.md，🔴高风险任务，等待人类审核后再进入编码

## #11408 设计第二版：修正为 AG-UI 标准 interrupt/resume 协议

📝 2026-09-07 — architect

- 用户提示核对AG-UI官方标准后发现第一版方向错误——另建REST端点绕开了已有协议基础设施
- 核实react-ag-ui已原生支持AgUiInterrupt{responseSchema}和unstable_getPendingInterrupts/submitInterruptResponses
- 核实AAF后端RunLifecycleEventConverter已把AUTHORIZATION_REQUESTED投影为标准interrupt(reason="tool_call")
- reason官方标准值另含"input_required"，正是Clarification场景，此前AguiEvent.Interrupt的responseSchema参数一直传null
- 第二版：CLARIFICATION_REQUESTED投影为reason="input_required" interrupt，responseSchema从questions()生成JSON Schema
- resumeRun按interruptId归属分派(先查approval查不到查clarification)，分派到不同应用层入口
- AssistantApprovalEventService泛化出streamByExecutionId，approval/clarification共用同一轮询实现
- 前端移除render_ui_block的CHOICE/FORM分支，新增ClarificationInterruptPanel+SchemaForm用官方API
- 已知限制：不支持多轮部分补充；interrupt状态未持久化刷新不重放，与#11411同根因不在本任务修复
- 第一版方案（Question元数据扩展/独立REST端点/ClarificationQueryPort按taskId查询）已作废

## #11410 文档附件服务端处理与生产上传体验

📐 2026-09-07 — architect

- 调研确认：ImporterFactory/五个DocumentImporter当前零安全防护，FileSecurityScanPort代码库完全不存在
- 设计方案：新增DocumentAttachmentGuardService做解析前门禁，不改ImporterFactory/DocumentImporter接口
- FileSecurityScanPort契约（aaf-framework）+ HeuristicFileSecurityScanAdapter默认实现（不接入外部病毒引擎，已知局限披露）
- 文档解析结果统一以既有Attachment(TEXT,...)语义进入执行链路，不扩展AttachmentType枚举
- 设计已写入design.md，🔴高风险任务，人类审核通过后转入开发

## #11410 实现落地

✅ 2026-09-07 — developer-service / developer-webui

- 新增FileSecurityScanPort契约 + HeuristicFileSecurityScanAdapter（ZIP解压比+XXE特征启发式检测）
- 新增DocumentMagicBytes魔数校验（PDF/DOCX二进制头，纯文本UTF-8可解码性）
- 加固PdfImporter（页数上限+CompletableFuture超时+总字符）、WordImporter（entry数量+总字符）
- 加固MarkdownImporter/HtmlImporter/PlainTextImporter总字符上限，均通过DocumentImportLimits配置化
- 新增DocumentAttachmentGuardService唯一门禁入口 + DocumentTokenBudgetSplitter预算裁剪与provenance
- 关键发现并修正设计缺陷：react-ag-ui按mimeType把file part分流为document类型（非file），已修正controller判断
- 前端新增OssDocumentAttachmentAdapter接入CompositeAttachmentAdapter，.md/.html文件行为变为走服务端解析
- 关键修复：HeuristicFileSecurityScanAdapter原依赖ZipEntry.getSize()在DEFLATED流式读取下恒为-1，已修正为边解压边计数
- 后端单测覆盖guard校验分支/importer加固边界/splitter截断逻辑；按用户指示本轮跳过前端单测与check:affected


## #11413 Interactables、MCP Apps 与页面协同评估

✅ 2026-09-07 — architect

- 读@assistant-ui/core源码确认：Interactable的AI更新经框架自动生成update_{name}工具直调setDefState，无内建HITL/权限拦截点
- AAF代码库当前零使用Interactables（仅llms-full.txt官方文档提及）
- 矩阵结论：仅适用纯前端UI偏好/未提交草稿，禁用服务端权威实体与需审批操作
- MCP Apps(SEP-1865)经用户澄清定位：2025-11提出的协议草案扩展，与AAF已有MCP工具调用是不同能力层
- MCP Apps结论：草案阶段+无业务场景驱动，本轮不深入接入细节，暂不实现
- 低风险试点方案：CopywritingParamsBar（Zustand管理3个纯UI参数），严格限定不含持久化
- 纯评估任务，未产出代码；design.md已写入完整矩阵与试点设计，待人类安全评审


## #11412 待协调者决策：数据库迁移号段分配

⚠️ 2026-09-07 — developer-service

- #11412 需新增 message_feedback 表（存储 AG-UI 字符串 messageId + 反馈类型 + 原因 + model/runId），需要一个新的迁移文件
- 按 architecture-constraints.md 硬约束：v1-v99 历史号段已固定不再变动，v100-v199 当前"待分配"，登记只能由协调者操作
- message_feedback 语义上属于 chat/AI 助理模块增量，非全新业务板块，但 v8（chat_schema）在冻结的历史号段内不可续用
- 请协调者决策：(a) 登记 v100-v199 给 chat 模块反馈功能使用，或 (b) 指定其他处理方式
- 本条目待协调者确认后更新，未确认前不创建迁移文件


## #11412 范围裁剪：反馈 messageId 关联的架构限制

⚠️ 2026-09-07 — developer-service

- 深入调研发现：AG-UI 协议层前端最终 messageId 是 {replyId}:{blockId}（TextMessageEventConverter.blockMessageId，来自 MESSAGE_STARTED/DELTA/BLOCK_COMPLETED）
- 但持久化时机 persistAssistantMessageIfCompleted 监听的是 MESSAGE_COMPLETED（对应 AgentScope AGENT_RESULT），其 payload.messageId 是 AgentScope Msg.id，与前端 {replyId}:{blockId} 是两个不同的值，且当前持久化代码根本没有读取这个字段（只取了text）
- 建立"前端可见 messageId → 数据库消息"的稳定映射需要改造持久化时机（在 MESSAGE_BLOCK_COMPLETED 落库并存入 payload），属于 #11411 遗留的信封化改造范畴，超出 #11412 原定范围
- 决定：#11412 反馈功能范围裁剪为不依赖新数据库映射——反馈端点直接以 AG-UI 字符串 messageId 为 key 存储（不关联 ConversationMessage.id，不要求消息已持久化成功），本轮反馈记录是独立的、以 messageId 为主键的轻量存储，不建立与 ConversationMessage 的外键关系
- 已知限制：反馈记录与会话消息历史是两条平行数据，不做 JOIN 查询关联；如需展示"某条历史消息的反馈状态"需要后续在信封化改造后补充关联，本轮不实现
- 用户已确认现阶段不新增迁移 SQL、不部署，本轮改为纯代码实现 + 内存态验证，数据库表结构变更留待部署前统一处理


## #11412 范围说明：对话后续建议后端生成逻辑不在本轮

⚠️ 2026-09-07 — developer-webui

- 设计要求"suggestions 增加对话后续建议"，前端渲染基础设施已完成：FollowUpSuggestions 组件（非空对话中展示，复用 agent-run-store.suggestions 同一状态源，不建新状态）+ AgentSuggestion 类型扩展（title/description/autoSend）
- 后端侧：现有 aaf.suggestions CUSTOM 事件名已注册但从未被任何业务逻辑发送（悬空能力，非本轮新增缺陷）；若要真正实现"AI 回复后动态生成后续建议"，需要新增推荐逻辑（调模型生成或规则引擎），这是全新业务能力设计，超出#11412"接入现有 primitive 增强交互"的任务范围
- 决定：本轮只交付前端展示能力（可被未来任何 aaf.suggestions 事件消费），不实现后端生成逻辑；欢迎页初始建议（WelcomeSuggestionService）不受影响，继续正常工作


## #11412 反馈、输入历史、建议与可观测体验

✅ 2026-09-07 — developer-service / developer-webui

- 用本地agentscope-java源码验证：AgentScope Msg.id与ReActAgent的replyId各自独立随机UUID，无关联——推翻此前假设的messageId映射关系
- 修复#11411遗留缺陷：AI消息持久化改为按MESSAGE_BLOCK_COMPLETED（携带replyId:blockId）落库，写入payload.aguiMessageId建立跨端映射
- ConversationMessageRepository新增findByConversationIdAndAguiMessageId（JSONB->>原生查询）
- 新建MessageFeedbackService（不新建表，复用metadata列），删除死代码旧ChatController.messageFeedback（Long id体系与AG-UI不兼容）
- 新端点POST /sessions/thread/{threadId}/messages/{aguiMessageId}/feedback，DTO含type/reason/model/runId
- 前端接入FeedbackAdapter（positive标准流程）+ NegativeFeedbackButton（Popover收集原因，官方API不支持reason字段）
- 接入unstable_useComposerInputHistory到ChatterComposer主输入框
- 建议增量：AgentSuggestion加title/description/autoSend，新增FollowUpSuggestions组件支持对话中建议
- 诊断入口过程中发现并修复真实缺陷：ExecutionEventPublicMapper.safeData白名单缺失MODEL_CALL_COMPLETED分支，token/耗时数据从未透出
- agent-run-store新增diagnostic状态，DiagnosticInfoButton只在message.isLast时展示避免历史消息误显示
- 已知限制：完整消息信封仍未实现；对话后续建议后端生成逻辑超出范围未实现
- 按用户指示本轮不新增迁移SQL、不执行check:affected、不编写测试
