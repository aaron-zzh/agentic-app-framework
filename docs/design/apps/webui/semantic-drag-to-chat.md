---
level: Practice
layer: Interaction
purpose: 语义拖放到对话框——让界面中的任意内容可作为 AI 对话上下文
status: draft
version: 1.0.0
date: 2026-05-29
author: AaronZZH & Kiro
scope:
  includes:
    - 统一语义拖放 hook 设计
    - 可拖放内容类型与数据模型
    - Composer 中上下文标签展示
    - 发送时上下文序列化
  excludes:
    - 对话框内部消息渲染
    - AI 如何消费上下文（属于后端 prompt 工程）
relations:
  - "[用户感知与语义界面](./user-awareness-semantic-ui.md) — 语义注册基础设施"
  - "[Copilot 插件](./copilot-plugin.md) — 对话体验层"
gains:
  - 理解拖放到对话框的完整数据流
  - 能为新组件接入拖放能力
  - 理解 Composer 中上下文标签的展示规则
---

# 语义拖放到对话框

> 用户可以将界面中的文档、图片、视频、列表数据、表单字段等内容拖放到对话输入框，作为 AI 对话的结构化上下文。

## 设计动机

传统做法：用户复制粘贴内容到对话框 → 丢失结构、丢失来源、AI 无法追溯。

本方案：拖放即引用 → 保留语义元数据 → AI 精确理解上下文 → 用户看到结构化标签。

## 架构总览

```text
┌─────────────────────────────────────────────────────────┐
│  业务组件（文档卡片 / 素材网格 / 列表行 / 表单字段）       │
│       ↓ 调用                                             │
│  useSemanticDraggable(hook)                              │
│    - 读取组件语义元数据（SemanticRegistry）                │
│    - 读取运行时状态/数据（PageSemanticsCollector）         │
│    - 生成 ChatterDropItem（含 context snapshot）          │
│    - 返回 drag props（ref + listeners + attributes）     │
└──────────────────────┬──────────────────────────────────┘
                       │ @dnd-kit drag
┌──────────────────────▼──────────────────────────────────┐
│  Chatter DndContext                                      │
│    - handleDragEnd → 收集到 attachments[]                │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│  ChatterComposer                                         │
│    - ContextChip 列表展示（图标 + 摘要 ≤100字 + 移除）    │
│    - 发送时序列化为 context-reference content parts       │
└─────────────────────────────────────────────────────────┘
```

## 数据模型

### ChatterDropItem（扩展）

```typescript
export interface ChatterDropItem {
  /** 内容类型 */
  type: "doc" | "file" | "image" | "video" | "text"
      | "view-context" | "field" | "record"
  id?: string | number
  title?: string
  /** 摘要（≤100 字符，用于 ContextChip 展示） */
  summary?: string
  /** 完整内容（文本类） */
  content?: string
  /** 资源地址（文件/图片/视频） */
  url?: string
  /** 缩略图（图片/视频预览） */
  thumbnailUrl?: string
  /** 语义元数据快照 */
  semantics?: {
    componentName: string
    entity?: string
    view?: string
    selectedIds?: string[]
    fieldData?: Record<string, unknown>
  }
}
```

### 各类型映射

| type | 来源组件 | summary 生成规则 | 展示 |
|------|---------|-----------------|------|
| `doc` | 文档卡片、知识库文档、DocTree | 文档标题 | 📄 标题 |
| `image` | 素材网格、富文本中的图片 | 文件名 | 🖼️ 缩略图+文件名 |
| `video` | 素材网格 | 文件名 | 🎬 文件名 |
| `file` | 文件上传区、附件列表 | 文件名 | 📎 文件名 |
| `text` | 富文本选区、任意文本 | 前 100 字符 | 📝 "内容..." |
| `view-context` | ListView、KanbanView | "实体名(N条选中)" | 📋 用户(3条选中) |
| `field` | 表单字段组件 | "字段名=值" | 🏷️ 姓名=张三 |
| `record` | 列表行、详情页 | 记录标题 | 📌 记录标题 |

## 核心 Hook

### useSemanticDraggable

```typescript
import { useDraggable } from "@dnd-kit/core"
import { useMemo } from "react"
import type { ChatterDropItem } from "@/features/chatter/types"

interface UseSemanticDraggableOptions {
  /** 唯一标识（组件内唯一即可） */
  id: string
  /** 拖放数据 */
  item: ChatterDropItem
  /** 是否禁用 */
  disabled?: boolean
}

export function useSemanticDraggable({ id, item, disabled }: UseSemanticDraggableOptions) {
  const enrichedItem = useMemo<ChatterDropItem>(() => ({
    ...item,
    summary: item.summary ?? truncate(item.title ?? item.content ?? "", 100)
  }), [item])

  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `semantic-drag-${id}`,
    data: enrichedItem,
    disabled
  })

  return { ref: setNodeRef, listeners, attributes, isDragging }
}

function truncate(str: string, max: number): string {
  return str.length > max ? `${str.slice(0, max)}…` : str
}
```

### 接入示例

```tsx
// 文档卡片
function DocCard({ doc }: { doc: Document }) {
  const { ref, listeners, attributes, isDragging } = useSemanticDraggable({
    id: `doc-${doc.id}`,
    item: { type: "doc", id: doc.id, title: doc.title, content: doc.summary }
  })

  return (
    <div ref={ref} {...listeners} {...attributes} style={{ opacity: isDragging ? 0.5 : 1 }}>
      <h3>{doc.title}</h3>
    </div>
  )
}

// 列表视图（拖放当前选中）
function ListViewDragHandle({ entity, selectedIds }: Props) {
  const { ref, listeners, attributes } = useSemanticDraggable({
    id: `list-${entity.slug}`,
    item: {
      type: "view-context",
      title: `${entity.label}(${selectedIds.length}条选中)`,
      semantics: { componentName: "ListView", entity: entity.slug, selectedIds }
    },
    disabled: selectedIds.length === 0
  })

  return <button ref={ref} {...listeners} {...attributes}>拖放到对话</button>
}

// 表单字段
function FieldWrapper({ field, value }: Props) {
  const { ref, listeners, attributes } = useSemanticDraggable({
    id: `field-${field.name}`,
    item: {
      type: "field",
      title: `${field.label}=${String(value)}`,
      semantics: { componentName: "FormField", fieldData: { [field.name]: value } }
    }
  })

  return <div ref={ref} {...listeners} {...attributes}>{/* 原字段组件 */}</div>
}
```

## Composer 展示：ContextChip

```tsx
const ICONS: Record<string, string> = {
  doc: "📄", image: "🖼️", video: "🎬", file: "📎",
  text: "📝", "view-context": "📋", field: "🏷️", record: "📌"
}

export function ContextChip({ item, onRemove }: { item: ChatterDropItem; onRemove: () => void }) {
  const icon = ICONS[item.type] ?? "📎"
  const label = item.summary ?? item.title ?? item.type

  // 图片/视频类型展示缩略图
  if ((item.type === "image" || item.type === "video") && item.thumbnailUrl) {
    return (
      <span className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-1 text-xs">
        <img src={item.thumbnailUrl} alt="" className="size-6 rounded object-cover" />
        <span className="max-w-[150px] truncate">{label}</span>
        <button onClick={onRemove} aria-label="移除">×</button>
      </span>
    )
  }

  return (
    <span className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-1 text-xs max-w-[200px]">
      <span>{icon}</span>
      <span className="truncate">{label}</span>
      <button onClick={onRemove} aria-label="移除">×</button>
    </span>
  )
}
```

## 发送时序列化

attachments 随消息发送，转为结构化 content parts：

```typescript
function serializeAttachments(items: ChatterDropItem[]): ContextReference[] {
  return items.map(item => ({
    type: "context-reference",
    refType: item.type,
    title: item.title,
    summary: item.summary,
    content: item.content,
    url: item.url,
    semantics: item.semantics
  }))
}
```

后端接收后可：
- 将 `doc` 类型的 content 注入 system prompt
- 将 `image`/`video` 的 url 作为多模态输入
- 将 `view-context` 的 selectedIds 查询实际数据注入上下文
- 将 `field` 的 fieldData 作为结构化参数

## 需要接入的组件清单

| 优先级 | 组件 | 位置 |
|--------|------|------|
| P0 | 文档卡片 | `features/knowledge/components/` |
| P0 | DocTree 节点 | `components/docs/DocTree.tsx` |
| P0 | AIGC 素材卡片 | `features/aigc/FileGrid.tsx` |
| P1 | ListView 行 | `features/entity-engine/components/list/` |
| P1 | KanbanCard | `features/entity-engine/components/kanban/` |
| P1 | 表单字段 | `features/entity-engine/components/form/` |
| P2 | 富文本选区 | `features/rich-text-editor/` |
| P2 | 流程节点 | `features/flow-editor/` |

## 与现有设施的关系

- **`DraggableItem` 组件**：保留作为简单场景的声明式包装，`useSemanticDraggable` 是其 hook 等价物
- **`SemanticRegistry`**：提供组件能力描述，hook 可选读取（当前阶段 item 由调用方显式构造）
- **`PageSemanticsCollector`**：提供运行时状态，未来可自动注入到 `semantics` 字段

## 未来演进

- v0.2：AI 主动建议"要不要把这个拖给我看看？"（基于意图推断）
- v0.2：支持从对话框拖出内容到界面（反向拖放）
- v0.3：多选批量拖放 + 拖放预览 overlay
