---
level: Practice
layer: Product
purpose: AAF 统一富文本编辑器模块——一个引擎四种场景（文档编辑/富文本字段/Chatter 输入/多行文本升级）
status: draft
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# 统一富文本编辑器（RichTextEditor）

> 基于 Lexical，一个编辑器引擎 + Feature 插件体系 + 四种预设配置，覆盖文档编辑、表单富文本字段、Chatter 评论输入、多行文本升级四种场景。
> 所属体系：[结构化视图模式](./interaction-mode-structured-view.md) | [生成式交互模式](tmp/interaction-mode-generative.md) | [对话式交互](tmp/conversational-interaction.md)

## 一、设计理念

四种场景的编辑能力是同一引擎的不同配置切面，区别仅在 Feature 集合和 UI 外壳。统一为一个模块，避免并行抽象。

```text
┌─────────────────────────────────────────────────────────┐
│           RichTextEditor（统一富文本编辑器）               │
│  Lexical 引擎 + Feature 插件注册 + Preset 配置           │
├─────────────────────────────────────────────────────────┤
│  Preset（按场景选择）：                                   │
│  ┌───────────┐ ┌───────────┐ ┌──────────┐ ┌──────────┐ │
│  │ document  │ │ richField │ │ chatter  │ │ minimal  │ │
│  │ 全功能文档 │ │ 表单字段   │ │ 评论输入  │ │ 纯文本+  │ │
│  │ 编辑器    │ │ 编辑器    │ │ 编辑器   │ │ 可升级   │ │
│  └───────────┘ └───────────┘ └──────────┘ └──────────┘ │
├─────────────────────────────────────────────────────────┤
│  Feature 插件（按需组合）：                               │
│  Heading | List | Link | Block | Upload | Mention |      │
│  Table | Code | Markdown | Toolbar | SlashMenu | Collab │
└─────────────────────────────────────────────────────────┘
```

### 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 编辑器引擎 | Lexical | Meta 开源，轻量可扩展，原生支持协同编辑，RSC 兼容 |
| 插件体系 | Feature 模式（server/client 分离） | 按需组合，Tree-shaking 友好，与 Next.js RSC 兼容 |
| 协同编辑 | Yjs CRDT | 与 AAF 实时协同策略一致（见 realtime-data-strategy.md） |
| 状态管理 | TanStack Query（内容持久化）+ 编辑器内部状态 | 遵循 AAF 状态边界规则 |
| 多行文本升级 | 同一组件，preset 切换 | textarea → richText 无缝过渡，数据格式兼容 |

## 二、四种场景

### 2.1 文档编辑器（document preset）

全功能编辑器，用于生成式交互模式下的文档创作。

```text
┌─ 固定工具栏 ──────────────────────────────────────────┐
│ H1 H2 H3 │ B I U S │ • 1. ☐ │ 🔗 📎 │ 代码 引用 ── │
├────────────────────────────────────────────────────────┤
│                                                        │
│  文档标题（H1）                                         │
│                                                        │
│  正文段落，支持 **粗体**、*斜体*、`行内代码`...          │
│                                                        │
│  / ← 斜杠菜单（插入块）                                │
│                                                        │
│  ┌─ 代码块 ─────────────────────────────────┐         │
│  │ const x: number = 42;                    │         │
│  └───────────────────────────────────────────┘         │
│                                                        │
│  ┌─ 嵌入块：工作流 ─────────────────────────┐         │
│  │ [FlowEditor readonly 缩略图]              │         │
│  └───────────────────────────────────────────┘         │
│                                                        │
└────────────────────────────────────────────────────────┘
```

能力：标题 / 列表 / 链接 / 引用 / 分割线 / 代码块 / 表格 / 图片上传 / 文件附件 / @提及 / 嵌入块（语义组件）/ 斜杠菜单 / Markdown 快捷输入 / 拖拽排序 / 协同编辑 / 版本历史

### 2.2 富文本字段（richField preset）

表单中 `type: 'richText'` 字段的编辑器，功能适中。

```text
┌─ 浮动工具栏（选中文本时出现）─┐
│ B I U S │ 🔗 │ H2 H3 │ • 1. │
└──────────────────────────────┘
┌────────────────────────────────────────────────────────┐
│  产品描述内容...                                        │
│  支持基础格式 + 列表 + 链接 + 图片                      │
│  不支持嵌入块、表格等重型功能                            │
└────────────────────────────────────────────────────────┘
```

能力：标题（H2-H4）/ 基础格式 / 列表 / 链接 / 图片上传 / 浮动工具栏 / Markdown 快捷输入

### 2.3 Chatter 评论输入（chatter preset）

表单底部活动流的评论/消息输入框，轻量级。

```text
┌────────────────────────────────────────────────────────┐
│  输入评论... @提及用户                    [发送]        │
│  ─────────────────────────────────────────             │
│  B I 🔗 📎 😀                                         │
└────────────────────────────────────────────────────────┘
```

能力：基础格式（粗体/斜体）/ 链接 / @提及 / 表情 / 文件附件 / 单行或少量多行 / Enter 发送

### 2.4 多行文本升级（minimal preset + 升级按钮）

`type: 'textarea'` 字段默认为纯文本，用户可一键升级为富文本。

```text
升级前（纯 textarea）：
┌────────────────────────────────────────────────────────┐
│  普通多行文本内容...                     [⇧ 富文本]    │
└────────────────────────────────────────────────────────┘

升级后（richField preset）：
┌─ 浮动工具栏 ─────────────────┐
│ B I U │ 🔗 │ • 1.            │
└──────────────────────────────┘
┌────────────────────────────────────────────────────────┐
│  现在支持 **格式化** 内容了...             [⇩ 纯文本]  │
└────────────────────────────────────────────────────────┘
```

升级逻辑：
- 纯文本 → 富文本：将 plaintext 包装为 Lexical paragraph 节点
- 富文本 → 纯文本：提取 plaintext，丢弃格式（需用户确认）

## 三、Feature 插件体系

### 3.1 Feature 结构

每个 Feature 分为服务端和客户端两部分，与 Next.js RSC 兼容：

```typescript
// 服务端 Feature（节点定义、验证、格式转换）
interface ServerFeature {
  key: string
  nodes?: NodeDef[]
  validate?: (data: unknown) => string[]
  converters?: {
    html?: HtmlConverter
    markdown?: MarkdownConverter
  }
  clientProps?: Record<string, unknown>
}

// 客户端 Feature（UI 组件、工具栏、快捷键）
interface ClientFeature {
  key: string
  nodes?: LexicalNodeClass[]
  plugins?: LexicalPlugin[]
  toolbar?: ToolbarConfig
  slashMenu?: SlashMenuItem[]
  markdownShortcuts?: MarkdownShortcut[]
}
```

### 3.2 内置 Feature 清单

| Feature | 说明 | document | richField | chatter | minimal |
|---------|------|:--------:|:---------:|:-------:|:-------:|
| `HeadingFeature` | 标题 H1-H6 | ✓ | ✓(H2-H4) | — | — |
| `BoldFeature` | 粗体 | ✓ | ✓ | ✓ | — |
| `ItalicFeature` | 斜体 | ✓ | ✓ | ✓ | — |
| `UnderlineFeature` | 下划线 | ✓ | ✓ | — | — |
| `StrikethroughFeature` | 删除线 | ✓ | ✓ | — | — |
| `InlineCodeFeature` | 行内代码 | ✓ | ✓ | — | — |
| `LinkFeature` | 链接 | ✓ | ✓ | ✓ | — |
| `ListFeature` | 有序/无序/待办列表 | ✓ | ✓ | — | — |
| `BlockquoteFeature` | 引用块 | ✓ | ✓ | — | — |
| `HorizontalRuleFeature` | 分割线 | ✓ | — | — | — |
| `CodeBlockFeature` | 代码块（语法高亮） | ✓ | — | — | — |
| `TableFeature` | 表格 | ✓ | — | — | — |
| `UploadFeature` | 图片/文件上传 | ✓ | ✓ | ✓(附件) | — |
| `MentionFeature` | @提及用户/文档 | ✓ | — | ✓ | — |
| `EmojiFeature` | 表情选择器 | ✓ | — | ✓ | — |
| `BlocksFeature` | 嵌入块（语义组件） | ✓ | — | — | — |
| `SlashMenuFeature` | `/` 命令面板 | ✓ | — | — | — |
| `MarkdownShortcutFeature` | Markdown 快捷输入 | ✓ | ✓ | — | — |
| `FixedToolbarFeature` | 固定工具栏 | ✓ | — | — | — |
| `FloatingToolbarFeature` | 浮动工具栏 | ✓ | ✓ | — | — |
| `InlineToolbarFeature` | 底部行内工具栏 | — | — | ✓ | — |
| `DraggableBlockFeature` | 块拖拽排序 | ✓ | — | — | — |
| `CollabFeature` | Yjs 协同编辑 | ✓ | ✓(可选) | — | — |
| `HistoryFeature` | 撤销/重做 | ✓ | ✓ | ✓ | — |

### 3.3 Preset 配置

```typescript
type EditorPreset = 'document' | 'richField' | 'chatter' | 'minimal'

function createEditor(preset: EditorPreset, overrides?: Partial<EditorConfig>): EditorConfig

// 使用示例
const documentEditor = createEditor('document')
const fieldEditor = createEditor('richField', { collab: true })
const chatterEditor = createEditor('chatter', { onSubmit: handleSend })
const textareaEditor = createEditor('minimal')  // 可升级为 richField
```

## 四、嵌入块（Blocks Feature）

文档编辑器的核心能力——在富文本中嵌入任意结构化数据块，对应 AAF 语义组件体系。

### 4.1 块类型注册

```typescript
interface BlockDef {
  slug: string
  label: string
  icon: string
  fields: FieldDef[]                  // 复用实体注册表的字段定义
  component: React.ComponentType      // 画布上的渲染组件
  keywords?: string[]                 // 斜杠菜单搜索关键词
}

// 注册示例
const blocks: BlockDef[] = [
  {
    slug: 'codeBlock',
    label: '代码块',
    icon: 'code',
    fields: [
      { name: 'language', type: 'select', options: [...] },
      { name: 'code', type: 'code' },
    ],
    component: CodeBlockRenderer,
    keywords: ['code', '代码'],
  },
  {
    slug: 'callout',
    label: '标注',
    icon: 'alert-circle',
    fields: [
      { name: 'type', type: 'select', options: ['info','warning','error','success'] },
      { name: 'content', type: 'richText' },  // 嵌套富文本
    ],
    component: CalloutRenderer,
  },
  {
    slug: 'embed',
    label: '嵌入',
    icon: 'frame',
    fields: [
      { name: 'entityType', type: 'text' },
      { name: 'entityId', type: 'text' },
      { name: 'viewMode', type: 'select', options: ['card','full','mini'] },
    ],
    component: EmbedRenderer,         // 嵌入其他实体的视图
    keywords: ['embed', '嵌入', '引用'],
  },
]
```

### 4.2 行内块

用于 @提及、标签等行内元素：

```typescript
const inlineBlocks: BlockDef[] = [
  {
    slug: 'mention',
    label: '提及',
    icon: 'at-sign',
    fields: [
      { name: 'targetType', type: 'select', options: ['user','document','entity'] },
      { name: 'targetId', type: 'text' },
      { name: 'displayName', type: 'text' },
    ],
    component: MentionChip,
  },
]
```

## 五、数据格式

### 5.1 存储格式（Lexical JSON）

```json
{
  "root": {
    "children": [
      {
        "type": "heading",
        "tag": "h1",
        "children": [{ "type": "text", "text": "文档标题" }]
      },
      {
        "type": "paragraph",
        "children": [
          { "type": "text", "text": "正文内容，" },
          { "type": "text", "text": "粗体", "format": 1 },
          { "type": "text", "text": "，提及" },
          {
            "type": "inline-block",
            "fields": { "blockType": "mention", "targetType": "user", "targetId": "u-123", "displayName": "张三" }
          }
        ]
      },
      {
        "type": "block",
        "fields": { "blockType": "codeBlock", "language": "typescript", "code": "const x = 1;" }
      }
    ]
  }
}
```

### 5.2 格式转换

```typescript
// Lexical JSON → HTML（服务端渲染/邮件通知）
convertToHTML(data: LexicalState): string

// Lexical JSON → Markdown（AI 处理/导出）
convertToMarkdown(data: LexicalState): string

// Markdown → Lexical JSON（导入/AI 生成内容回填）
convertFromMarkdown(markdown: string): LexicalState

// Lexical JSON → 纯文本（搜索索引/textarea 降级）
convertToPlaintext(data: LexicalState): string

// 纯文本 → Lexical JSON（textarea 升级为 richText）
convertFromPlaintext(text: string): LexicalState
```

### 5.3 textarea ↔ richText 数据兼容

```typescript
// 字段类型切换时的数据迁移
function migrateFieldData(
  value: string | LexicalState,
  from: 'textarea' | 'richText',
  to: 'textarea' | 'richText'
): string | LexicalState {
  if (from === 'textarea' && to === 'richText') {
    return convertFromPlaintext(value as string)
  }
  if (from === 'richText' && to === 'textarea') {
    return convertToPlaintext(value as LexicalState)
  }
  return value
}
```

## 六、只读渲染

服务端渲染（RSC），零客户端 JS：

```tsx
import { RenderRichText } from '@/features/rich-text-editor'

// 文档页面（RSC）
export default async function DocumentPage({ params }) {
  const doc = await api.documents.get(params.id)
  return <RenderRichText data={doc.content} />
}

// 列表单元格（简短预览）
export function RichTextCell({ value }) {
  return <RenderRichText data={value} truncate={100} />
}

// Chatter 评论（带 @提及高亮）
export function CommentContent({ content }) {
  return <RenderRichText data={content} inline />
}
```

## 七、与 Chatter 活动流的集成

Chatter 评论区使用 `chatter` preset，特殊行为：

```typescript
interface ChatterEditorProps {
  entityType: string
  entityId: string
  onSubmit: (content: LexicalState, mentions: MentionRef[]) => void
  placeholder?: string
}

// 行为特性
// - Enter 发送（Shift+Enter 换行）
// - @ 触发用户/文档选择器
// - 附件按钮上传文件
// - 提交后清空编辑器
// - 提及的用户自动加入通知列表
```

与 ActivityStream 组件的关系：

```text
ActivityStream
  ├── 时间线（操作日志 + 评论 + 活动，按时间倒序）
  ├── ChatterEditor（chatter preset）  ← 本模块提供
  │     ├── 输入框（Lexical + MentionFeature + EmojiFeature）
  │     ├── 附件按钮
  │     └── 发送按钮
  └── 活动调度表单
```

## 八、协同编辑

document 和 richField（可选）preset 支持 Yjs CRDT 实时协同：

```typescript
interface CollabConfig {
  provider: 'websocket' | 'webrtc'
  roomId: string                     // 通常为 entityType:entityId:fieldName
  awareness?: boolean                // 显示协作者光标
}

// CollabFeature 内部实现
// - Yjs Doc 绑定 Lexical EditorState
// - WebSocket Provider 连接后端 Yjs 服务
// - Awareness 协议同步光标位置和用户信息
// - 离线编辑 → 重连后自动合并
```

## 九、目录结构

```text
features/rich-text-editor/
├── components/
│   ├── rich-text-editor.tsx         → 编辑器主组件（统一入口）
│   ├── render-rich-text.tsx         → 只读渲染组件（RSC）
│   ├── chatter-editor.tsx           → Chatter 评论输入封装
│   └── textarea-upgrade.tsx         → textarea ↔ richText 切换 UI
├── features/
│   ├── heading/                     → 标题
│   ├── format/                      → 粗体/斜体/下划线/删除线/行内代码
│   ├── list/                        → 有序/无序/待办列表
│   ├── link/                        → 链接
│   ├── blockquote/                  → 引用块
│   ├── horizontal-rule/             → 分割线
│   ├── code-block/                  → 代码块
│   ├── table/                       → 表格
│   ├── upload/                      → 图片/文件上传
│   ├── mention/                     → @提及
│   ├── emoji/                       → 表情
│   ├── blocks/                      → 嵌入块（语义组件）
│   ├── slash-menu/                  → 斜杠命令面板
│   ├── markdown-shortcut/           → Markdown 快捷输入
│   ├── toolbar/                     → 工具栏（固定/浮动/行内）
│   ├── draggable-block/             → 块拖拽排序
│   ├── collab/                      → Yjs 协同编辑
│   └── history/                     → 撤销/重做
├── presets/
│   ├── document.ts                  → 全功能文档编辑器配置
│   ├── rich-field.ts                → 表单富文本字段配置
│   ├── chatter.ts                   → Chatter 评论输入配置
│   └── minimal.ts                   → 最小配置（可升级）
├── converters/
│   ├── html.ts                      → Lexical → HTML
│   ├── markdown.ts                  → Lexical ↔ Markdown
│   └── plaintext.ts                 → Lexical ↔ 纯文本
├── lib/
│   ├── create-editor.ts             → 编辑器工厂函数
│   ├── nodes.ts                     → 自定义 Lexical 节点定义
│   └── theme.ts                     → 编辑器主题（CSS 类名映射）
└── types.ts                         → 类型定义
```

## 十、与现有设计的关系

| 引用方 | 使用方式 |
|--------|---------|
| 结构化视图 · 字段组件 | `fieldComponents.richText` → `<RichTextEditor preset="richField" />` |
| 结构化视图 · Chatter | ActivityStream 评论区 → `<ChatterEditor />` |
| 结构化视图 · textarea 字段 | 升级按钮 → `<TextareaUpgrade />` 切换 preset |
| 生成式交互 · 文档编辑 | 画板文档视图 → `<RichTextEditor preset="document" />` |
| 列表视图 · 单元格 | richText 字段预览 → `<RenderRichText truncate={100} />` |
| 通知/邮件 | 服务端 `convertToHTML()` 生成邮件内容 |
| AI 处理 | `convertToMarkdown()` / `convertFromMarkdown()` 与 LLM 交互 |

## 十一、实现路径

| 阶段 | 能力 |
|------|------|
| v0.1 | Lexical 集成骨架 + richField preset（基础格式 + 列表 + 链接）+ 只读渲染 |
| v0.2 | document preset（全功能）+ 斜杠菜单 + Markdown 快捷输入 + 拖拽排序 |
| v0.3 | chatter preset + @提及 + 表情 + Chatter 集成 + textarea 升级能力 |
| v1.0 | 嵌入块（语义组件）+ Yjs 协同编辑 + 版本历史 + 自定义 Feature SDK |
