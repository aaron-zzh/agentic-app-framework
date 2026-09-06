---
level: Practice
layer: Design
purpose: AAF-114 助理对话框技术与交互设计
status: active
version: 1.0.0
date: 2026-09-05
author: AaronZZH & Kiro
tags:
  - AAF-114
  - Chatter
  - AG-UI
related:
  - requirement.md
  - ../AAF-106/tasks.md
  - ../AAF-107/tasks.md
---

# AAF-114 技术设计

## 设计决策

### 标题选择器

`ChatterToolbar` 将可用 Role 映射为 `{assistantId, assistantName, roleKey, roleName}`，选择值唯一绑定 Assistant 与 Role。无显式选择时展示“AI 助理 · 自动角色”，不伪造默认 role key，也不使用前端 fallback Role。

选择后一次更新：

```ts
{
  ...target,
  type: "ai",
  assistantId: option.assistantId,
  agentRole: option.roleKey,
  agentSkill: undefined
}
```

服务端 `aaf.role.resolved` 继续只表达本次运行的解析结果，不反向改写请求选择。

### 附件协议

文件附件只进入 assistant-ui runtime adapter：

- 图片：`OssImageAttachmentAdapter.add` 校验类型与大小；`send` 上传 `/system/files/upload`，返回 file content part，URL 用于 AG-UI 多模态 source，`filename` 携带 `StoredFile.key`。
- 文本：组合 `SimpleTextAttachmentAdapter`，文本作为 attachment text part，由 react-ag-ui 合并进入用户消息。
- `CompositeAttachmentAdapter` 统一 `accept` 为图片与文本 MIME。
- `AssistantAguiController` 从最后一条 user 消息读取文本和 image part；图片只接受 URL source 且要求 `metadata.filename` 中存在文件 key，转换为 `AssistantExecutionRequest.Attachment(IMAGE, ..., resourceId)`。
- 后续图像解析继续复用 `AssistantExecutionService` 与 `VisionMediaResolver`；必须按 key 校验当前用户所有权、拒绝已删除或非 `image/*` 文件，再生成短时访问 URL，不信任浏览器 URL 作为资源授权依据。

### 展示偏好

`ChatterDisplayPreferences` 仅存在于 `Chatter` 或 `GlobalChatter` 的 React state，并同时传给 Runtime、Panel 和 Composer：

- `showPlan`：控制 `TaskBoardPanel` 可见性；任务查询、授权浮层及服务端计划事实不变。
- `showThinking`：同时传给 `useAgUiRuntime({ showThinking })` 并门控 `ChatterThread` 已有 reasoning part 的渲染；不改变 AAF-106 的 mapper 拦截规则。

默认值为计划开启、思考关闭。不新增 Zustand store。

### 事件与状态复用

`AAF_AI_TASK_EVENT_TYPES` 补齐 `aaf.executor_plan.*` 九类 v0.12 事件，使既有任务事件快照/流类型能表达计划生命周期。界面不建立本地 ExecutorPlan reducer，持久 TaskBoard 与服务端事件仍是权威来源。

## 交互结构

- 标题栏：会话入口｜助理与角色选择｜语音及窗口操作。
- Composer 附件区：紧凑文件项，可移除。
- Composer 左侧：附件｜模型｜计划｜思考。
- Composer 右侧：语音波形｜麦克风｜发送或停止。
- 模式开关 Tooltip 明示“展示偏好”语义。

## 风险控制

- 320px 宽度使用 `min-w-0`、截断和紧凑按钮，禁止横向滚动。
- 图片最大 20MB，文本最大 1MB；服务端文件上传校验仍是最终边界。
- 不支持的媒体类型由 Composite adapter 拒绝。
- 本轮按用户明确要求只做静态复核，不运行 lint、test、typecheck、build 或 check；该偏离必须写入 dev-log 和最终汇报。


## AgentScope 多模态链核对

当前图片确实通过 AgentScope 原生 `ContentBlock` 传递，但具体类型是 `DataBlock`，不是兼容类型 `ImageBlock`：

```text
assistant-ui CompleteAttachment(file + image MIME)
  → react-ag-ui InputContent.image(URL source + file key metadata)
  → AssistantAguiController Attachment(IMAGE, resourceId=fileKey)
  → VisionMediaResolver 当前 owner/MIME 校验 + signedUrl
  → AgentMessage(USER, text, attachments)
  → AgentScopeMessageMapper
  → Msg.content = [TextBlock, DataBlock(URLSource)]
```

`DataBlock` 继承 `ContentBlock`。AgentScope 源码明确说明它是 image/audio/video/file 的前向统一容器，新代码应优先于仅为兼容保留的 `ImageBlock`。OpenAI、DashScope、Gemini、Anthropic formatter 均显式处理 `DataBlock`，因此当前方向正确，不应为了“看起来像图片”改回 `ImageBlock`。

待加强边界：

- `DataBlock` 当前未设置稳定 `id` 和 `name`，每次映射会生成随机 ID。后续使用不泄露存储布局的 AAF opaque resource ID 和原始文件名，在持久层、`AgentMessage`、AgentScope `Msg` 构造与审计映射中保持关联。
- provider formatter 主要消费 `source` 和 MIME，不要求供应商负载回显 `DataBlock.id/name`；provider 契约验证 URLSource、MIME、失效 URL 重签和媒体内容正确性。
- AUTO 模型路由已用 `userAttachments.isEmpty()` 生成 `hasImage` 特征并选择 VISION 模型；EXPLICIT 模式尚需服务端校验所选模型具备 VISION 能力，前端提示不能替代服务端拒绝。

## assistant-ui 后续集成原则

### 传输事件与消息 part 分层

AG-UI `CUSTOM` 是传输事件，不是 assistant-ui `DataMessagePart`。当前 react-ag-ui `RunAggregator` 只聚合 text、reasoning、tool call 和 interrupt；不能假设 CUSTOM 会自动进入消息历史。

AAF 需要一个唯一、版本化的消息投影适配器，将允许的 CUSTOM 事件确定性映射为 assistant-ui part：

```text
AG-UI CUSTOM(eventId, eventOffset, parentMessageId, payload)
  → AAF event schema/权限/大小校验
  → native SourceMessagePart | ToolCallMessagePart
    | DataMessagePart | GenerativeUIMessagePart
  → runtime message projection + AafThreadMessageEnvelope 持久化
```

映射优先级为原生 `source` / `file` / `image` part、已知 `tool-call` part、版本化 `data` part、最后才是展示型 `generative-ui` part。sources/citations 不另造通用卡片协议。适配器必须定义稳定 part ID、父消息关联、`eventId + eventOffset/revision` 去重、事件顺序、重放和未知 part 的可诊断 fallback。

### 富交互能力分工

- **Tool UI**：用于已知工具的参数流、running/success/error、结果和受控恢复；事实来源是既有 tool call/result，不自建工具状态。
- **Data UI**：用于没有原生 part 的确定性结构化事实；renderer 按版本化 `DataMessagePart.name` 注册。
- **Generative UI**：仅用于 `INFO_CARD`、指标、列表等展示组合；组件名必须来自前端 allowlist，props 必须校验，禁止任意 HTML、动态 import、后端指定 React 实现和可执行 URL。
- **Clarification UI**：是既有 `ClarificationRequest` 的投影，不是第四套持久状态；提交只走 `delegatedTaskApi.submitInput(taskId, { inputId, values })`。

TaskBoard、ExecutorPlan、Clarification 和会话历史均以服务端为权威。实时卡片只携带 canonical ID、revision/eventOffset 并读取权威投影；历史中的不可变展示快照不得驱动当前提交、授权或任务状态。不得把 durable CUSTOM 事件先写入 Zustand 再渲染；Zustand 只允许保存展开态、未提交表单草稿等临时 UI。

### 首批 UI Block 白名单

```ts
type AafUiBlock =
  | { version: 1; id: string; type: "INFO_CARD"; title: string; description?: string; items?: InfoItem[] }
  | { version: 1; id: string; type: "CHOICE"; clarificationId: string; title: string; options: Option[]; multiple?: boolean }
  | { version: 1; id: string; type: "FORM"; clarificationId: string; title: string; fields: Field[] }
```

`INFO_CARD` 只能经唯一转换器生成 allowlisted `GenerativeUISpec`，后端不得同时拥有直接发送任意 Generative UI 组件树的第二路径。`CHOICE`/`FORM` 由 canonical clarification schema 派生，生命周期只由既有 Clarification 事务和事件产生，不建立 UI Block 生命周期表。

当前 `ClarificationRequest` 与 `DelegatedTaskInputRequest` 仅支持字符串值，不能直接承载 number、checkbox、多选数组。#11407/#11408 必须先扩展 schemaVersion、字段 type/required/options/multiple/constraints 和 typed value union，再交付 `text`、`textarea`、`number`、`select`、`checkbox`。前端只保存未提交草稿；resolved/canceled/expired 状态来自服务端并冻结卡片。

### 文档附件处理

PDF/DOCX 等不能直接套用当前 `SimpleTextAttachmentAdapter`。后端应在既有 importer 前增加受控解析 façade，再复用 `ImporterFactory`、`PdfImporter`、`WordImporter`、`MarkdownImporter`、`HtmlImporter` 和 `PlainTextImporter`：

```text
上传 → owner/状态/扩展名/MIME/魔数/大小一致性校验 → 安全扫描
  → 解析期资源边界 → ImporterFactory 解析 → Unicode/HTML/空白清理
  → 字符与 Token 预算 → 分块/相关性筛选/必要时可审计摘要
  → Attachment(TEXT) / TaskMaterial → Agent 上下文
```

解析期必须限制 PDF 页数、DOCX ZIP entry/解压比、总字符、单段长度、超时与内存，并禁止 XXE 和外链资源。恶意文件扫描通过 `FileSecurityScanPort` 接入经审核实现，只有 `CLEAN` 状态可进入 importer；`PENDING`、`UNAVAILABLE`、`INFECTED`、`ERROR` 必须隔离或拒绝，不能在扫描不可用时降级放行。输出记录 opaque resource ID、content hash、importer 名称/版本、截断或摘要 provenance。不支持、加密、损坏或超限文件显式失败，不回退为纯文本解析。

不得在 Chatter 新建第二套文档解析器。文档内容始终是不可信任务材料；不得拼入系统提示。PDF/DOCX 通常已压缩，不需要再次 ZIP；这里需要的是上下文压缩，而不是物理压缩。

### 完整会话信封与 ThreadList

`fromAgUiMessages` 当前只能作为 text、tool call、reasoning 和用户附件的基础转换器，不能独立恢复 source、data、generative-ui、interrupt metadata、branch graph 或 AAF metadata。完整历史采用版本化 `AafThreadMessageEnvelope`，至少保存：

- assistant-ui message part union、message status 与 timing；
- interrupt、branch/parent、feedback 和 AAF metadata；
- 附件 opaque resource ref、文件名和 MIME，不保存长期 presigned URL；
- CUSTOM 投影的 eventId/eventOffset/revision 与 schema version。

服务端消息信封是唯一历史真理，assistant-ui runtime 是投影。AAF rehydrator 可复用 `fromAgUiMessages` 的基础转换，但必须补齐自有 part codecs；未知版本或 part 不得静默丢弃，应显示可诊断错误并提供重试。SessionPopover 应由 ThreadList/runtime adapter 替换而非并存，active thread 只由该 adapter 管理。

ThreadList 交付包括真实新建、切换、重命名、搜索、归档/删除、运行/未读状态和分页；单线程消息也需分页或虚拟化。历史加载失败不能显示成空会话。断线/刷新需要支持 in-flight run cursor replay、chunk 去重和恢复；图片按 file key 与 owner 重新签发短时 URL。

### 生产体验与受控扩展

- sources/citations 优先使用 `SourceMessagePart`，支持标题、文档定位和受权短链。
- feedback adapter 对接服务端评价；输入历史可使用 `unstable_useComposerInputHistory`，但需封装实验 API 边界。
- suggestions 只做现有能力增量：标题/描述、fill-only/auto-send、对话后续建议和恢复语义。
- BranchPicker 已存在，不重复建设；只补分支持久化与附件/tool/data 一致性。
- Interactables 只用于页面外由开发者放置的可控组件；不得复制 TaskBoard 等服务端状态，AI 更新必须经过受权工具。
- MCP Apps/interactive resource 另做安全评估，不与 unrestricted Generative UI 混用。

## 后续验收标准

```gherkin
场景: 图片进入 AgentScope 多模态消息
  当用户发送一张已授权图片
  那么 AgentScope Msg.content 同时包含 TextBlock 与 DataBlock
  并且 AAF 各层用 opaque resource ID 稳定关联名称、image MIME 和短时 URL
  并且显式非视觉模型在执行前被服务端拒绝
```

```gherkin
场景: CUSTOM 事件投影为消息 part
  当服务端发送允许的 CUSTOM 事件
  那么唯一投影适配器按事件类型生成原生或版本化 assistant-ui part
  并且重复、乱序、未知或非法 payload 有确定性处置
  并且 durable part 不以 Zustand 作为事实源
```

```gherkin
场景: AI 请求用户补充参数
  当 canonical ClarificationRequest 发出 FORM 或 CHOICE 投影
  那么对话中展示服务端定义的 typed 字段和选项
  并且提交使用 taskId 与 inputId 幂等写入既有输入接口
  并且刷新后卡片状态可恢复且已解决卡片不可重复提交
```

```gherkin
场景: AI 展示信息卡片
  当服务端发出 INFO_CARD 或已注册 data payload
  那么唯一转换器只生成 allowlist 中的展示组件
  并且未知组件或非法 props 使用安全 fallback，不执行任意代码或 URL
```

```gherkin
场景: 用户发送 PDF 或 DOCX
  当文件通过解析前安全校验、资源边界和既有 importer 解析
  那么清理后的内容按 Token 预算作为不可信 TaskMaterial 进入执行
  并且超限、加密、损坏或不支持文件得到明确错误，不静默截断或降级解析
```

```gherkin
场景: 用户切换历史会话
  当历史包含附件、工具、source、data、generative-ui、reasoning、branch 或待处理 interrupt
  那么 AafThreadMessageEnvelope 经 AAF rehydrator 后语义等价恢复
  并且加载失败不显示为空会话，过期图片 URL 由 file key 重新签发
```
