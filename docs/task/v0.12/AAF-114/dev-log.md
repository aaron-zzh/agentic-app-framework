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
