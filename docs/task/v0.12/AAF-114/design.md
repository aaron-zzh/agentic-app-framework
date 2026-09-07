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


## #11407 详细设计：消息 Part 投影、UI Block 协议与安全 allowlist

> 本章基于对现有代码的调研补齐 #11407 的可评审细节。调研结论：react-ag-ui 0.0.41 的 `RunAggregator` 对 CUSTOM 事件的处理是 `default` 分支仅 debug 记录，不会自动生成 `DataMessagePart`/`GenerativeUIMessagePart`；AAF 后端 `AgUiProjector` 已把公共事件降级为 `aaf.*` CUSTOM（含 `eventId/sequence/eventOffset?/version`，无 `revision`），内部节点收敛为 4 个 `aaf.node.*` CUSTOM（不含上述字段）；`ClarificationRequest`/`ExecutionInput` 当前 `values` 均为 `Map<String,String>`，无 typed value；assistant-ui 核心已原生提供 `DataMessagePart`/`GenerativeUIMessagePart` 与组件 allowlist 机制，AAF 不需要自建另一套抽象，只需按官方模式接线。

### 设计原则

1. **复用优先**：assistant-ui 已有 `DataMessagePart`（按 `name` 注册 renderer）、`GenerativeUIMessagePart`（按 `component` 查 allowlist）、Tool UI（按 `toolName` 注册，`running|requires-action|complete|incomplete` + `isError`）。AAF 投影适配器的职责是把 CUSTOM 转成这些**已有**类型，不新建平行的 part 类型体系。
2. **唯一投影点**：新增 `AguiCustomEventProjector`（前端，TS），是 CUSTOM → assistant-ui part 的唯一入口。当前 `ag-ui-runtime.tsx` 里直接 if/else 判断 `event.name` 写 Zustand 的逻辑保留（那是瞬时 UI 状态投影，语义不同），但新增的 durable part 投影不得复制到 Zustand。
3. **服务端先分类，前端只做确定性转换**：后端已用 registry 把事件分成"一等 AG-UI 事件"和"CUSTOM fallback"；#11407 不改动这条分类规则，只在 CUSTOM payload 里补充协议要求的字段（`schemaVersion`、去重键），并在前端建立按 `name` 精确匹配的转换表。

### 投影适配器接口

```ts
// apps/webui/src/features/chatter/runtime/ui-block/agui-custom-projector.ts

/** CUSTOM 事件投影结果——精确对应 assistant-ui 原生 part 或本设计定义的 AafUiBlock data part。 */
export type CustomEventProjection =
  | { kind: "data"; part: DataMessagePart<AafUiBlock> }
  | { kind: "generative-ui"; part: GenerativeUIMessagePart }
  | { kind: "ignore" } // 已知但不进消息历史（如 aaf.node.* 内部诊断事件）
  | { kind: "unknown"; rawName: string } // 未注册事件名，走可诊断 fallback

export interface AguiCustomEventEnvelope {
  /** AG-UI CUSTOM 事件名，即投影表的查找键。 */
  name: string
  /** 公共 fallback 已带 eventId；内部节点事件当前没有，适配器必须能处理缺失。 */
  eventId?: string
  /** 与 eventId 组合去重；历史读取路径下有值，实时流路径可能为 null。 */
  eventOffset?: number | null
  /** 本设计新增字段——payload 结构版本，未声明时按 1 处理并记录诊断。 */
  schemaVersion?: number
  /** parentMessageId：本次 CUSTOM 归属的 assistant 消息 ID，用于稳定 part 关联。 */
  parentMessageId: string
  value: unknown
}

/** 唯一投影函数：按 name 精确匹配已注册转换器；不匹配即 unknown。 */
export function projectCustomEvent(
  envelope: AguiCustomEventEnvelope
): CustomEventProjection
```

**去重与顺序**：适配器内部维护 `Set<string>`，键为 `${eventId ?? name}:${eventOffset ?? "live"}`；重复键直接返回 `{kind:"ignore"}`。乱序（`eventOffset` 回退）时不回滚已投影的 part，只记录一次 `console.warn` 诊断，不影响用户可见状态——历史场景下乱序意味着重放边界问题，交由 #11411 的 rehydrator 处理，不在本适配器内做复杂重排。

**payload 大小与非法处置**：单个 CUSTOM `value` 序列化后超过 32KB 时视为协议违规，返回 `unknown` 并记录 `rawName` 与截断后的前 256 字符供诊断，不抛异常中断整个事件流。

### AafUiBlock v1 协议

```ts
// apps/webui/src/features/chatter/runtime/ui-block/aaf-ui-block.ts

interface InfoItem {
  label: string
  value: string
}

interface ChoiceOption {
  id: string
  label: string
  description?: string
}

interface FormField {
  key: string
  label: string
  type: "text" | "textarea" | "number" | "select" | "checkbox"
  required?: boolean
  options?: ChoiceOption[] // type=select 时必填
  constraints?: { min?: number; max?: number; maxLength?: number }
}

export type AafUiBlock =
  | {
      version: 1
      id: string
      type: "INFO_CARD"
      title: string
      description?: string
      items?: InfoItem[]
    }
  | {
      version: 1
      id: string
      type: "CHOICE"
      clarificationId: string
      title: string
      options: ChoiceOption[]
      multiple?: boolean
    }
  | {
      version: 1
      id: string
      type: "FORM"
      clarificationId: string
      title: string
      fields: FormField[]
    }
```

**生成路径边界**（与后端一一对应）：

- `INFO_CARD`：唯一生成点是后端 `AafUiBlockConverter`（新增，位于 `aaf-api` 的 `module.ai.agui` 包），只接受服务端预定义的展示型事件（当前范围：executor plan 摘要、任务完成汇总），输出前先经过白名单字段裁剪。后端不得有第二条路径直接把任意事件转成 `INFO_CARD`。
- `CHOICE`/`FORM`：不是独立生命周期实体，是 `ClarificationRequest`（见下节 typed 扩展）在投影时刻的**只读快照**。`clarificationId` 就是 `ClarificationRequest.requestId`。前端收到后只允许本地保存未提交的表单草稿（Zustand 允许，因为是"未提交表单草稿"，符合协作红线里 Zustand 允许保存的范围）；`resolved/canceled/expired` 状态变化只能来自服务端下一次 CUSTOM 投影或历史重新加载，前端不能自行翻转已提交的卡片状态。

**渲染接线**（复用官方 Data UI 机制，不新建渲染框架）：

```ts
// 每个 AafUiBlock.type 对应一个通过 useAssistantDataUI 注册的 renderer
useAssistantDataUI({ name: "aaf.ui_block.info_card", render: InfoCardRenderer })
useAssistantDataUI({ name: "aaf.ui_block.choice", render: ChoiceCardRenderer })
useAssistantDataUI({ name: "aaf.ui_block.form", render: FormCardRenderer })
```

`DataMessagePart.name` 直接编码 block 类型，投影适配器据此产出对应 `name`；`data` 字段就是 `AafUiBlock` 本体。这样完全落在 assistant-ui 已有的"按 name 分发"机制内，不需要自定义消息渲染管线。

### Clarification 契约扩展（服务端）

现状：`ClarificationRequest.values: Map<String,String>`、`Question{field,question,options}` 均为字符串，`ExecutionInput.values`/`DelegatedTaskInputDTO.values` 同为 `Map<String,String>`。#11407 只做**契约设计**，扩展实现在 #11408。设计要点：

```java
// Question 扩展为携带 typed schema（新字段，向后兼容：省略时按 text 处理）
public record Question(
        String field,
        String question,
        List<String> options,       // 保留：CHOICE 场景的展示用选项标签
        FieldType type,             // 新增，默认 TEXT
        boolean required,           // 新增，默认 true（与当前 requiredFields 语义对齐）
        boolean multiple,           // 新增，SELECT/CHOICE 是否多选
        FieldConstraints constraints // 新增，可空
) {
    public enum FieldType { TEXT, TEXTAREA, NUMBER, SELECT, CHECKBOX }
    public record FieldConstraints(Integer min, Integer max, Integer maxLength) {}
}
```

`values` 的 typed 化不直接改成 `Map<String, Object>`（会引入反序列化歧义，违反"禁止用 JSON 字符串冒充数组或布尔值"的要求）。改为显式值联合：

```java
public sealed interface FieldValue {
    record TextValue(String value) implements FieldValue {}
    record NumberValue(BigDecimal value) implements FieldValue {}
    record BooleanValue(boolean value) implements FieldValue {}
    record MultiSelectValue(List<String> optionIds) implements FieldValue {}
}
// ClarificationRequest.values 由 Map<String,String> 改为 Map<String, FieldValue>
// ExecutionInput.values / DelegatedTaskInputDTO.values 同步扩展为 Map<String, FieldValue>
```

序列化上 `FieldValue` 用 Jackson 的 `@JsonTypeInfo`（`type` 字段做判别），前端 TS 侧对应生成判别联合类型，不用裸 JSON 字符串编解码数组/布尔值。**版本策略**：`ClarificationRequest`/`ExecutionInput` 新增 `schemaVersion`字段（默认 1）；服务端拒绝 `schemaVersion` 大于自己已知最大版本的提交（明确报错，不静默降级解析）。

`Question.type` 到 `AafUiBlock.FormField.type` 的映射是 1:1（`TEXT→text`、`TEXTAREA→textarea`、`NUMBER→number`、`SELECT→select`、`CHECKBOX→checkbox`），投影时直接透传，不做二次推断。

### 安全 allowlist 边界

沿用 assistant-ui 官方边界模型，不新增 AAF 专属抽象：

1. **Generative UI 组件 allowlist**：仅用于展示型组合（当前不规划使用，`INFO_CARD` 走 Data UI 而非 Generative UI，因为 `INFO_CARD` 字段是固定 schema，不需要树形组件组合的灵活性）。若后续确有需要用 `GenerativeUIMessagePart`，必须复用官方 `GenerativeUILibrary: Record<string, Component>` 模式：组件名不在 registry 中即拒绝渲染（`reportUnknownComponent`），不做动态 `import()`，不接受后端传来的组件实现代码。
2. **Data UI name allowlist**：`useAssistantDataUI({name, render})` 的 `name` 只允许来自本设计声明的 3 个值（`aaf.ui_block.info_card/choice/form`）；投影适配器产出的 `name` 与此白名单精确匹配，不做前缀匹配或正则匹配，防止后端拼接任意 `name` 绕过白名单意图。
3. **Props 校验**：投影适配器在把 CUSTOM payload 转成 `AafUiBlock` 时，用 TS 判别联合的 `type` 字段做 runtime 校验（而不是 `as` 强转）；字段缺失或类型不匹配时整体降级为 `{kind:"unknown"}`，不渲染半成品卡片。
4. **禁止项**（与 assistant-ui 官方安全实践一致）：不接受后端传来的组件树/组件名之外的任意 HTML 片段；`AafUiBlock` 的所有文本字段按纯文本渲染，不使用 `dangerouslySetInnerHTML`；不接受后端指定的可执行 URL（`options`/`items` 中若来源需要包含链接，必须是服务端预先校验过的内部路由或经签名的资源 URL，不接受任意外部 URL 直接渲染为可点击链接）。
5. **未知类型确定性 fallback**：`{kind:"unknown"}` 在 UI 上渲染为一条不可交互的诊断提示（"暂不支持展示该内容"），并在开发环境 console.warn 完整 payload；生产环境不回显完整 payload 防止信息泄露。

### 与既有机制的边界重申（不新建平行状态）

- TaskBoard/ExecutorPlan：`INFO_CARD` 摘要卡片只读展示，不替代 `TaskBoardPanel`；卡片过期或与实时 TaskBoard 不一致时以 TaskBoard 为准。
- Clarification 生命周期：只由 `ClarificationRequest` 状态机（`PENDING/RESOLVED/CANCELED/EXPIRED`）和其事件驱动；`CHOICE`/`FORM` UI Block 没有独立生命周期表，重新拉取历史即重新投影当前权威状态。
- HITL 授权：本设计不涉及 `HumanApproval`/`approvalId`/`resume[]`；`CHOICE`/`FORM` 提交继续走 `delegatedTaskApi.submitInput(taskId, {inputId, kind, values})`，不合并进 AG-UI `resume` 通道。

### #11407 验收标准补充

```gherkin
场景: 未知 CUSTOM 事件名
  当后端发出投影表未注册的 CUSTOM 事件名
  那么前端产出 {kind:"unknown"} 并渲染确定性 fallback 提示
  并且不抛出未捕获异常中断消息流渲染

场景: 重复 CUSTOM 事件
  当同一 eventId+eventOffset 的 CUSTOM 事件被重复投递
  那么第二次投影被适配器去重跳过
  并且不产生重复的 DataMessagePart

场景: schemaVersion 超前
  当 ClarificationRequest 携带的 schemaVersion 大于服务端已知最大版本
  那么服务端在写入前拒绝该请求并返回明确错误
  并且不静默按旧版本字段解析
```

## #11411 详细设计：完整消息信封、ThreadList 与可恢复运行

> 本章基于对现有代码的调研补齐 #11411 的可评审细节。调研结论：当前 `onSwitchToThread` 把每条后端消息固定映射为单个 `text` part，附件/工具调用/reasoning/分支全部丢失；`react-ag-ui` 已有 `resumeInFlightRun`/`unstable_resume`/`ThreadHistoryAdapter.resume()` 机制但 AAF 完全没接线；`SessionPopover` 的新建/切换按钮未真正调用线程管理 API（后端 API 已完整存在）；`ChatPersistenceListener` 按 `Long.valueOf(conversationId)` 解析 UUID 型 `threadId` 必然抛 `NumberFormatException` 导致消息静默丢失保存——这是必须在本任务修复的真实缺陷，不是新增功能。

### 范围边界与优先级

调研暴露的问题分两类：

1. **必须修复的现有缺陷**（阻塞正确性，优先级最高）：
   - `ChatPersistenceListener` UUID/Long 标识不匹配导致消息丢失保存。
   - `SessionPopover` 新建/切换按钮未接线（点击无实际效果）。
2. **本任务新增能力**（对齐设计目标）：
   - `AafThreadMessageEnvelope` 版本化消息信封 + rehydrator。
   - ThreadList 真实交互（新建/切换/重命名/搜索/归档/删除）。
   - 断线恢复接入官方 `resumeInFlightRun` 机制。
   - 图片附件历史持久化与 URL 重签。

分页/虚拟化（调研发现的第7点）不在本任务强制交付范围——完成标准里"消息分页或虚拟化"是补充项，优先保证信封正确性和 ThreadList 可用性，分页作为设计预留点但按数据量阈值决定是否本轮落地（见"分页留白"小节）。

### AafThreadMessageEnvelope 数据模型

版本化信封是消息持久化的唯一格式，取代 `ConversationMessage.content` 纯文本写法，复用已存在但未使用的 `payload`/`metadata` JSONB 字段：

```java
// apps/service/aaf-api/.../module/chat/message/domain/AafThreadMessageEnvelope.java（新增，写入 ConversationMessage.payload）

/** 消息信封版本化载荷——唯一持久化格式，取代裸文本 content。 */
public record AafThreadMessageEnvelope(
        int schemaVersion,        // 当前 1；未知/超前版本由 rehydrator 拒绝，不静默降级
        String role,              // user | assistant | system
        List<EnvelopePart> parts, // assistant-ui part union 的持久化投影
        MessageTiming timing,     // 可空：createdAt 已由 BaseEntity 承担，这里只放 run 相关计时
        BranchRef branch,         // 可空：{parentId, messageId}，对齐 ExportedMessageRepositoryItem
        Map<String, Object> aafMetadata // AAF 专有：assistantId/roleKey/modelId 等执行上下文
) {}

/** Part 判别联合——只覆盖当前系统真实产生的 part 类型，不预先设计未使用的类型。 */
public sealed interface EnvelopePart {
    record TextPart(String text) implements EnvelopePart {}
    record ReasoningPart(String text) implements EnvelopePart {}
    record ToolCallPart(
            String toolCallId, String toolName, String argsJson,
            String resultJson, boolean isError) implements EnvelopePart {}
    record ImageAttachmentPart(
            String resourceId,   // opaque file key，不持久化 presigned URL
            String fileName,
            String mimeType) implements EnvelopePart {}
    record TextAttachmentPart(String fileName, String content) implements EnvelopePart {}
}

public record BranchRef(String parentId, String messageId) {}
```

**写入路径**（替换 `ChatService.buildMessage` 的纯文本写法）：`ConversationMessage.content` 保留纯文本摘要（用于列表预览、全文搜索），`payload` 写入 `AafThreadMessageEnvelope` 的 JSON 序列化。两者不是双写同一份真理——`content` 是 `payload.parts` 中所有 `TextPart`/`ReasoningPart` 文本拼接后的派生缓存，只在写入时计算一次，不允许独立修改。

**未知 part 处理**：`EnvelopePart` 之外的历史数据（若未来扩展 `source`/`data`/`generative-ui`）按 sealed interface 的开放子类型增量添加，rehydrator 对无法识别的 JSON 判别值走确定性 fallback（见下节），不是异常中断整条历史。

### AAF Rehydrator

```ts
// apps/webui/src/features/chatter/runtime/history/aaf-rehydrator.ts

/** 后端返回的信封化消息 DTO——与 AafThreadMessageEnvelope 一一对应。 */
export interface AafThreadMessageDTO {
  id: string
  role: "user" | "assistant" | "system"
  schemaVersion: number
  parts: AafEnvelopePartDTO[]
  branch?: { parentId: string | null; messageId: string }
  createdAt: string
}

export type AafEnvelopePartDTO =
  | { type: "text"; text: string }
  | { type: "reasoning"; text: string }
  | { type: "tool-call"; toolCallId: string; toolName: string; argsJson: string; resultJson?: string; isError?: boolean }
  | { type: "image"; resourceId: string; fileName: string; mimeType: string; url: string /* 由后端按 resourceId 重签的短时 URL，不持久化 */ }
  | { type: "text-attachment"; fileName: string; content: string }

const KNOWN_SCHEMA_VERSION = 1

/**
 * 唯一 rehydrator：把后端信封 DTO 还原为 assistant-ui ExportedMessageRepositoryItem。
 *
 * 复用 fromAgUiMessages 的基础 text/tool-call 转换思路，但独立实现（不直接调用该函数）——
 * 后端信封的字段形状与 AG-UI 实时消息不同，没有必要先转成 AG-UI 再转一次。
 */
export function rehydrateThreadMessages(
  dtos: AafThreadMessageDTO[]
): { items: ExportedMessageRepositoryItem[]; diagnostics: RehydrationDiagnostic[] } {
  const diagnostics: RehydrationDiagnostic[] = []
  const items = dtos.map((dto) => {
    if (dto.schemaVersion > KNOWN_SCHEMA_VERSION) {
      diagnostics.push({ messageId: dto.id, reason: `未知 schemaVersion ${dto.schemaVersion}` })
      return unknownVersionPlaceholder(dto)
    }
    return rehydrateOne(dto, diagnostics)
  })
  return { items, diagnostics }
}

interface RehydrationDiagnostic {
  messageId: string
  reason: string
}
```

**未知版本/part 的确定性处置**：不静默丢弃整条消息（design.md 总纲明确要求"不静默丢弃，应显示可诊断错误并提供重试"）。`unknownVersionPlaceholder` 产出一条内容为固定诊断文案的 assistant 消息（保留原 `id`/`createdAt`/`branch` 以维持分支图完整性），前端渲染时该消息带 `status: {type:"incomplete", reason:"other"}`，assistant-ui 原生错误展示（`ErrorPrimitive`）即可呈现，不需要新建错误 UI。

**加载失败不显示为空会话**：`onSwitchToThread` 当前 `catch { return { messages: [] } }` 是明确的缺陷（design.md 总纲要求）。修正为区分"确实无消息"和"加载失败"：

```ts
onSwitchToThread: async (threadId) => {
  setCurrentThreadId(threadId)
  try {
    const dtos = await chatApi.getMessages(threadId)
    const { items, diagnostics } = rehydrateThreadMessages(dtos)
    diagnostics.forEach((d) => console.warn("[rehydrate]", d.messageId, d.reason))
    return { messages: items.map((i) => i.message), unstable_resume: true }
  } catch (error) {
    toast.error("会话历史加载失败，请重试")
    throw error // 不再吞掉异常伪装成空历史；assistant-ui 线程切换失败会保留在原线程
  }
}
```

### 断线恢复接入

补齐 `ThreadHistoryAdapter.resume()`，接入官方 `resumeInFlightRun` 机制（当前完全未使用）：

```ts
// apps/webui/src/features/livechat/runtime/thread-history-adapter.ts

const historyAdapter: ThreadHistoryAdapter = {
  async load() {
    // 复用 onSwitchToThread 的 rehydrate 逻辑；load() 用于初始进入而非线程切换
    const dtos = await chatApi.getMessages(currentThreadId)
    const { items } = rehydrateThreadMessages(dtos)
    return { messages: items, unstable_resume: hasInFlightRun(currentThreadId) }
  },
  async *resume() {
    // 复用既有 SSE cursor：后端持久化事件已有 eventOffset，按 Last-Event-ID 语义续读
    const stream = agent.subscribeResume(currentThreadId)
    for await (const chunk of stream) {
      yield chunk // 按到达顺序直接产出；不在此处去重排序（沿用官方模式）
    }
  },
  async append() {
    /* no-op：写入已在后端执行链路完成，这里不重复持久化 */
  }
}
```

`hasInFlightRun` 的判定：调用一个新增的轻量后端端点 `GET /api/agui/threads/{threadId}/in-flight`，返回该线程是否存在未终结的 run（复用 `AssistantTaskControlEntity`/`TaskBoard` 现有的运行态查询，不新建状态表）。这是本任务需要新增的唯一后端读接口。

### ThreadList 真实接入

替换 `SessionPopover` 内部逻辑，不新建并存组件（design.md 总纲要求"SessionPopover 应由 ThreadList/runtime adapter 替换而非并存"）：

```ts
// 修正 ag-ui-runtime.tsx 的 threadList adapter

onSwitchToNewThread: async () => {
  const session = await chatApi.createSession({ type: "ai" })
  setCurrentThreadId(session.threadId)
},
onRename: async (threadId, newTitle) => {
  await chatApi.renameSession(threadId, newTitle) // 后端 PUT rename 已存在，仅需前端调用
},
onArchive: async (threadId) => {
  await chatApi.archiveSession(threadId) // 后端 POST archive 已存在
},
onDelete: async (threadId) => {
  await chatApi.deleteSession(threadId) // 后端 DELETE 已存在
}
```

`SessionPopover` 组件改动：
- "新建会话"按钮改为直接调用 `runtime.threads.switchToNewThread()`（assistant-ui 提供的标准方法），不再只清附件。
- 会话行点击改为调用 `runtime.threads.switchToThread(session.threadId)`。
- 新增重命名（inline 编辑）、归档、删除操作项，复用现有 `DropdownMenu` 组件模式（项目已大量使用，见 `ChatterToolbar.tsx` 其他菜单）。
- 未读状态：本任务不新增未读字段（后端 `Conversation` 无此字段，属于范围外的新数据模型设计），若后续需要在单独任务评估。

### 标识体系 bug 修复（阻塞性缺陷）

`ChatPersistenceListener.onUserMessage` 当前：

```java
Long sessionId;
try {
    sessionId = Long.valueOf(event.conversationId()); // event.conversationId() 是 UUID threadId，必然抛异常
} catch (NumberFormatException e) {
    return; // 消息静默丢失，不保存
}
```

修复为按 `threadId` 查找 `Conversation` 再取其数值 `id`（与 `ChatService.listMessagesByThreadId` 使用的模式一致）：

```java
@EventListener
public void onUserMessage(UserMessageEvent event) {
    var conversation = conversationRepository.findByThreadId(event.conversationId());
    if (conversation.isEmpty()) {
        log.warn("消息持久化失败：threadId 对应会话不存在 threadId={}", event.conversationId());
        return;
    }
    chatService.saveMessage(
            event.userId(), "HUMAN", conversation.get().getId(), "user", event.content());
}
```

这个修复独立于信封改造，优先级最高——当前生产环境每次用户消息保存都会静默失败（除非 `event.conversationId()` 恰好是纯数字字符串），必须先修。

### 图片附件历史持久化

`AafThreadMessageEnvelope.EnvelopePart.ImageAttachmentPart` 只持久化 `resourceId`（fileKey），不持久化任何 URL。历史读取时后端按 `resourceId` 调用既有 `VisionMediaResolver`（`prepareCurrentOwnerExternalAccessByKey`）重新签发短时 URL，与执行链路完全复用同一组件，不新建第二套签发逻辑。

写入路径：`AssistantAguiController` 当前从 AG-UI 消息解析出 `Attachment(IMAGE, fileKey, ...)` 后只用于执行，不落库。补充：`ChatPersistenceListener`（或对应的 assistant 消息保存路径）需要把执行请求里的 `Attachment` 列表一并写入 `AafThreadMessageEnvelope.parts`，作为 `ImageAttachmentPart`。

### 分页留白

本轮不强制交付虚拟化——`ThreadPrimitive.Messages` 全量渲染在数据量可控（数百条以内）场景下性能可接受，虚拟化引入的复杂度（消息高度不定、图片/工具调用块异步渲染）超过本任务边际收益。设计预留点：`chatApi.getMessages` 改为接受可选 `sinceMessageId`/`limit` 参数，rehydrator 按增量结果 `append` 而非整批 `applyExternalMessages`；后端已有的 `/messages/page` 端点的内存分页实现（`findAll` 后 `subList`）留待数据量真正增长到需要虚拟化时，作为独立技术任务处理，不在本轮修复。

### #11411 验收标准补充

```gherkin
场景: 用户消息不再静默丢失
  当用户在 UUID threadId 的会话中发送消息
  那么 ChatPersistenceListener 按 threadId 查找 Conversation 并成功保存
  并且不再因 NumberFormatException 静默丢弃

场景: SessionPopover 新建会话生效
  当用户点击"新建会话"
  那么当前 assistant-ui 线程真正切换为新创建的 threadId
  并且旧线程消息不残留在新线程视图中

场景: 历史加载失败保留原会话
  当 GET 消息历史请求失败
  那么线程切换失败并提示用户重试
  并且不会呈现为一个消息为空的"新会话"假象

场景: 断线后重连线程恢复运行状态
  当用户在 run 进行中刷新页面并切回同一线程
  那么 unstable_resume 触发 resumeInFlightRun
  并且运行状态从持久化事件游标继续，不重新发起请求
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

## #11408 详细设计：对话内选择、参数表单与 Clarification 闭环

> **本章为第二版设计，替换初版的独立 REST 端点方案**。初版调研（`Map<String,String>` 存储链路判断、`Question` 元数据扩展、`ClarificationQueryPort` 安全修复思路）在核实 AG-UI 官方标准 interrupt/resume 协议后确认方向错误：AAF 已实现标准 AG-UI interrupt 机制（`AUTHORIZATION_REQUESTED` → `reason="tool_call"` interrupt，见 `RunLifecycleEventConverter`），`react-ag-ui`（AAF 实际依赖）原生支持 `AgUiInterrupt{id,reason,message?,toolCallId?,responseSchema?,expiresAt?,metadata?}` 和 `unstable_getPendingInterrupts()`/`unstable_submitInterruptResponses()`；`reason` 官方标准值含 `"input_required"`，`responseSchema` 字段（JSON Schema）正是为 Clarification 场景设计的标准通道。第二版改为复用这套已有协议基础设施，不新建独立 REST 端点，这也更贴合 assistant-ui 的既有用法，且对未来任何"需要人类介入"的场景（不止 Clarification）都通用。

### 设计原则（修正版）

1. **Clarification 复用标准 AG-UI interrupt/resume 协议，不新建传输通道**：`CLARIFICATION_REQUESTED` 投影为 `reason="input_required"` 的 interrupt，`responseSchema` 携带字段描述；提交走标准 `resume[]`，与工具审批（`reason="tool_call"`）走同一个 `/agui/run` 端点、同一套前端 `unstable_getPendingInterrupts`/`unstable_submitInterruptResponses` API，只是 `reason` 和 `responseSchema` 不同。
2. **底层存储链路不变**：`ClarificationRequest.values`/`ExecutionInput.values` 仍是 `Map<String,String>`（`TaskBoard` 最终把 `clarifiedParameters` 拼接成文本插入 prompt，typed 化对该链路无收益——此判断在第一版已核实，第二版沿用）。typed 信息只在 `responseSchema`（JSON Schema）里表达，供前端渲染表单，提交时前端按 schema 描述做值格式编码后仍写回字符串 `values`。
3. **`resumeRun` 按 interruptId 归属分派，不合并两个领域模型**：`HumanApproval` 和 `ClarificationRequest` 仍是两个独立的持久化模型和状态机；只是在传输层（interrupt/resume）统一，不在业务层合并——查完 approval 查不到再查 clarification，分派到不同的应用层入口。

### 后端：CLARIFICATION_REQUESTED 投影为标准 interrupt

**`RunLifecycleEventConverter` 扩展**：

```java
@Override
public Set<ExecutionEventType> supportedTypes() {
    return Set.of(
            ExecutionEventType.EXECUTION_STARTED,
            ExecutionEventType.EXECUTION_COMPLETED,
            ExecutionEventType.EXECUTION_FAILED,
            ExecutionEventType.EXECUTION_CANCELED,
            ExecutionEventType.COMMAND_REJECTED,
            ExecutionEventType.RUN_FAILED,
            ExecutionEventType.AUTHORIZATION_REQUESTED,
            ExecutionEventType.CLARIFICATION_REQUESTED); // 新增
}

case CLARIFICATION_REQUESTED -> context.runInterrupted(List.of(clarificationInterrupt(event)));

/**
 * {@code requestId} 直接作为 {@code interruptId}——与 {@code approvalId} 同一模式。
 * {@code reason="input_required"} 对齐官方标准值语义。
 */
private static AguiEvent.Interrupt clarificationInterrupt(ExecutionEvent event) {
    var values = event.payload().values();
    var requestId = (String) values.get("requestId");
    var responseSchema = buildResponseSchema(values); // 从 questions 快照生成 JSON Schema
    return new AguiEvent.Interrupt(
            requestId,
            "input_required",
            "需要补充参数",
            null,   // toolCallId：Clarification 不绑定具体工具调用
            responseSchema,
            null,
            Map.of("taskId", values.get("taskId")));
}
```

**`responseSchema` 生成规则**：从 `ClarificationRequest.questions()` 派生标准 JSON Schema，每个 `Question` 映射为一个 property：

```json
{
  "type": "object",
  "properties": {
    "destination": { "type": "string", "title": "目的地" },
    "budget": { "type": "number", "title": "预算" }
  },
  "required": ["destination", "budget"]
}
```

`Question` 本身不需要新增 `type`/`constraints` 字段（第一版设计的元数据扩展作废）——JSON Schema 是通用协议格式，AAF 侧只需要一个"`List<Question> → JSON Schema` 转换函数"，不需要改动 `ClarificationRequest` 领域模型本身。当前 `Question(field, question, options)` 的 `options` 非空时映射为 `"enum"`，否则映射为 `"type":"string"`——首批实现覆盖 text 和 单选枚举两种，与 `ClarificationRequest.values: Map<String,String>` 的现有能力完全对齐，不超前设计模型尚不支持的 number/checkbox/multiple（那些需要先扩展 `Question` 领域模型才有意义，留作后续迭代，不在本任务阻塞）。

**payload 需要携带 `taskId`**：`ExecutionEventPublicMapper`/`safeData` 的 `CLARIFICATION_REQUESTED` 分支当前只暴露 `requestId`/`subTaskId`/字段计数（详见 design.md #11407 章节调研），需要补充 `taskId` 供 interrupt `metadata` 使用（前端据此知道提交时用哪个 `taskId`，虽然本设计走 resume 不直接用 REST，但保留在 metadata 里便于前端展示/诊断）。

### 后端：`resumeRun` 按归属分派

```java
private SseEmitter resumeRun(RunRequest request) {
    var entry = request.resume().getFirst();
    var tenantId = currentTenant();
    var approval = approvals.find(tenantId, entry.interruptId());
    if (approval.isPresent()) {
        return resumeApproval(request, entry, tenantId, approval.get()); // 现有逻辑原样保留
    }
    return resumeClarification(request, entry, tenantId);
}

private SseEmitter resumeClarification(RunRequest request, ResumeEntry entry, TenantId tenantId) {
    var clarification = clarifications
            .findByRequestId(tenantId, entry.interruptId())
            .orElseThrow(() -> new IllegalArgumentException("未知的 interruptId: " + entry.interruptId()));
    var emitter = new SseEmitter(600_000L);
    var session = agUiProjector.openSession();
    if (!entry.approved()) {
        // "cancelled" resume 状态对应用户取消澄清；ClarificationRequest 无独立取消端口方法，
        // 复用现有 consumeInputs 消费空 UNRELATED kind 输入或直接调用 board.stopClarification
        // 的既有取消路径（沿用 InputBuffer 现有分支，不新建取消状态转换）。
        send(emitter, session.close(request.threadId(), request.runId()), Long.MAX_VALUE);
        emitter.complete();
        return emitter;
    }
    var input = new ExecutionInput(
            UUID.randomUUID().toString(),
            tenantId,
            currentUserId(),
            clarification.taskId(),
            ExecutionInput.Kind.SUPPLEMENT,
            null,
            payloadToValues(entry.payload()), // JSON payload → Map<String,String>，按 responseSchema 反向编码
            Instant.now());
    coordinator.acceptInput(input)
            .then(Mono.fromRunnable(() ->
                    subscribeExecutionEvents(emitter, session, request, clarification.executionId())))
            .subscribe();
    return emitter;
}
```

**事件续读复用泛化后的轮询服务**：`AssistantApprovalEventService.stream(HumanApproval)` 内部实际只依赖 `approval.invocationContext().executionId()`，与 approval 本身无关（已核实：核心轮询逻辑只是"按 `executionId` 从 `ExecutionEventStorePort` 轮询新事件"）。改造为：

```java
// 新增通用签名，approval/clarification 共用同一底层轮询实现
public RecoveryStream streamByExecutionId(TenantId tenantId, ExecutionId executionId) { ... }

// 现有 approval 入口收窄为薄封装，行为不变
public RecoveryStream stream(HumanApproval approval) {
    if (approval.status() != HumanApproval.Status.APPROVED || approval.decidedAt() == null) {
        throw new IllegalArgumentException("仅已批准审批可续读恢复事件");
    }
    var context = approval.invocationContext();
    var targetExecutionId = context.parentExecutionId() == null
            ? context.executionId() : context.parentExecutionId();
    return streamByExecutionId(approval.tenantId(), targetExecutionId);
}
```

**新增只读查询**：`ClarificationRequestRepository` 补充一个不带悲观锁的 `findByRequestId` 方法（`resumeClarification` 只读查询，不能复用 `findPendingForUpdate` 抢占写锁——这与第一版 `ClarificationQueryPort` 的判断一致，只是查询维度从"按 taskId 查 pending"改为"按 requestId 精确查"）。

### 前端：复用官方 interrupt 处理 API

不再需要 `render_ui_block` 工具的 `CHOICE`/`FORM` 分支——**移除**该分支（`RenderUiBlockTool` 收窄为只保留 `INFO_CARD`，纯展示无需人类响应，适合工具+tool-call 模式；`AafUiBlock.CHOICE`/`FORM` 类型定义、`ChoiceCard`/`FormCard` 组件、`aaf-ui-block.ts` 里对应分支一并移除，避免维护两套并行机制）。

新增 `ClarificationInterruptPanel` 组件，用官方 API 读取待处理 interrupt：

```tsx
function ClarificationInterruptPanel() {
  const runtime = useAssistantRuntime() as AgUiAssistantRuntime
  const pending = runtime.unstable_getPendingInterrupts()
  const clarificationInterrupts = pending?.interrupts.filter(i => i.reason === "input_required") ?? []
  if (clarificationInterrupts.length === 0) return null

  async function handleSubmit(interrupt: AgUiInterrupt, values: Record<string, unknown>) {
    await runtime.unstable_submitInterruptResponses([
      { interruptId: interrupt.id, status: "resolved", payload: values }
    ])
  }

  return clarificationInterrupts.map(interrupt => (
    <SchemaForm
      key={interrupt.id}
      schema={interrupt.responseSchema}
      onSubmit={(values) => handleSubmit(interrupt, values)}
    />
  ))
}
```

`SchemaForm` 是一个新增的通用 JSON Schema 驱动表单渲染器（`type:"string"`+`enum` → select；`type:"string"` 无 enum → text input），首批只需覆盖 `responseSchema` 实际产出的两种形态，不预先支持 JSON Schema 全部特性。

**多字段一次性提交**：`ClarificationRequest.requiredFields` 可能有多个字段，全部包含在同一个 `responseSchema.properties` 里，一次 `resolve` 提交所有字段值（`payload: {field1: value1, field2: value2}`）——这与 `submitInterruptResponses` 要求"一次 resume 回填全部 open interrupts"的语义不冲突：**一个 Clarification 请求只产生一个 interrupt**（不是每个字段一个 interrupt），多字段体现在这一个 interrupt 的 `responseSchema.properties` 里，不是 assistant-ui 的 multi-interrupt 场景（那是"同一个 run 有多个独立的审批点"）。

**部分补充场景的处理**：`ClarificationRequest.apply` 支持增量补充（`replace=false`），但 interrupt/resume 模型是"resolve 一次即恢复执行"，不支持"resolve 后 run 继续暂停等下一轮部分补充"。设计取舍：**首批实现要求用户在表单里填满全部 `required` 字段才能提交**（前端 `SchemaForm` 用 JSON Schema 的 `required` 数组做本地校验），不支持协议层面的多轮部分补充；如果提交后校验仍不完整（理论上不应发生，因为前端已校验），后端 `acceptInput` 走既有 `InputBuffer.apply` 逻辑，若 `complete()` 仍为 false，`consumeInputs` 会生成 `CLARIFICATION_UPDATED` 事件而不是 `CLARIFICATION_RESOLVED`——但此时 run 已经 resume 过一次，不会再产生新的 interrupt 等待下一轮（这是一个已知的边界情况，标注为需求收窄：**首批不支持多轮部分补充的 UI 闭环**，用户必须一次性填完）。

### 与既有机制的边界重申（修正版）

- `HumanApproval` 与 `ClarificationRequest` 仍是两个独立领域模型和状态机；只在传输层（AG-UI interrupt/resume）统一入口，`resumeRun` 按 `interruptId` 归属分派到不同应用层服务（`HitlCoordinatorPort` vs `DelegatedTaskCoordinator.acceptInput`）。
- `ClarificationRequest` 生命周期（`PENDING/RESOLVED/CANCELED/EXPIRED`）继续只能由 `JpaTaskTransitionAdapter` 既有状态机产生；本设计不新建 UI Block 生命周期表，不新建独立 REST 输入端点。
- `AafUiBlock` 协议收窄为只服务 `INFO_CARD` 场景；需要人类响应的场景统一走 interrupt/resume，不再有"CUSTOM/tool-call 展示 + 独立提交端点"和"interrupt/resume"两套并行机制。

### 刷新/切线程恢复语义（修正版结论不变）

Interrupt 状态目前存储在 assistant-ui 消息的 `metadata.custom`（`AG_UI_METADATA_NAMESPACE`），不是持久化在 AAF 消息历史里——这与 #11411 遗留限制（tool-call result 不持久化）是同一类问题的另一种表现：**刷新页面后，如果 run 当时处于 interrupt 等待态，前端不会重新看到这个 interrupt**（除非后端在下次连接时重新发出 `RUN_FINISHED` + `outcome:interrupt`）。检查 `AssistantAguiController.startRun`/线程切换逻辑是否会在重新进入时重放未决 interrupt——若无，这是需要在 #11411 完整信封落地时一并解决的已知限制，本任务不修复。

### #11408 验收标准（修正版）

```gherkin
场景: Clarification 投影为标准 interrupt
  当任务进入 AWAITING_CLARIFICATION 状态
  那么 AG-UI 事件流产生 reason="input_required" 的 interrupt
  并且 responseSchema 从 ClarificationRequest.questions() 正确派生

场景: 前端用官方 API 提交澄清
  当用户在 SchemaForm 中填写全部必填字段并提交
  那么调用 unstable_submitInterruptResponses 提交标准 resume payload
  并且后端 resumeRun 正确分派到 Clarification 处理分支（不是 approval 分支）

场景: 提交驱动状态机前进
  当 resumeClarification 调用 DelegatedTaskCoordinator.acceptInput 成功
  那么 ClarificationRequest 转为 RESOLVED，board 恢复執行
  并且事件流通过泛化后的 streamByExecutionId 续读，AG-UI run 正常收敛

场景: 部分补充不支持（已知限制，非缺陷）
  当用户提交的字段未覆盖全部 requiredFields
  那么后端不会崩溃，但也不会重新产生等待下一轮的 interrupt
  并且此限制记录为首批不支持范围，不阻塞本任务完成
```


