/**
 * 画板智能元素——便签、嵌入卡片、思维导图、流程图模式
 * 通过 tldraw 自定义 shape 实现
 * @author AaronZZH & Kiro
 */

import type { TLBaseShape, TLShapeUtilConstructor } from "@tldraw/tldraw"
import { BaseBoxShapeUtil, HTMLContainer } from "@tldraw/tldraw"

// ============ 便签 Shape ============

/** 便签颜色分类 */
export type StickyNoteColor = "yellow" | "pink" | "blue" | "green" | "purple"

/** 便签 Shape 属性 */
type StickyNoteShape = TLBaseShape<
  "sticky-note",
  {
    w: number
    h: number
    text: string
    color: StickyNoteColor
  }
>

/** 便签颜色映射 */
const STICKY_COLORS: Record<StickyNoteColor, string> = {
  yellow: "#fef3c7",
  pink: "#fce7f3",
  blue: "#dbeafe",
  green: "#dcfce7",
  purple: "#f3e8ff"
}

/** 便签 Shape 工具 */
export class StickyNoteShapeUtil extends BaseBoxShapeUtil<StickyNoteShape> {
  static override type = "sticky-note" as const

  getDefaultProps(): StickyNoteShape["props"] {
    return { w: 200, h: 200, text: "", color: "yellow" }
  }

  component(shape: StickyNoteShape) {
    return (
      <HTMLContainer>
        <div
          className="flex h-full w-full flex-col rounded-sm p-3 shadow-md"
          style={{ backgroundColor: STICKY_COLORS[shape.props.color] }}
        >
          <p className="flex-1 whitespace-pre-wrap text-sm">{shape.props.text || "双击编辑..."}</p>
        </div>
      </HTMLContainer>
    )
  }

  indicator(shape: StickyNoteShape) {
    return <rect width={shape.props.w} height={shape.props.h} rx={4} />
  }
}

// ============ 嵌入实体卡片 Shape ============

/** 嵌入卡片 Shape 属性 */
type EntityCardShape = TLBaseShape<
  "entity-card",
  {
    w: number
    h: number
    entitySlug: string
    recordId: string
    title: string
    fields: Record<string, string>
  }
>

/** 嵌入实体卡片 Shape 工具 */
export class EntityCardShapeUtil extends BaseBoxShapeUtil<EntityCardShape> {
  static override type = "entity-card" as const

  getDefaultProps(): EntityCardShape["props"] {
    return { w: 240, h: 160, entitySlug: "", recordId: "", title: "", fields: {} }
  }

  component(shape: EntityCardShape) {
    const entries = Object.entries(shape.props.fields)
    return (
      <HTMLContainer>
        <div className="flex h-full w-full flex-col rounded-md border bg-card p-3 shadow-sm">
          <p className="mb-2 font-medium text-sm">{shape.props.title || "实体卡片"}</p>
          <div className="flex-1 space-y-1 overflow-hidden">
            {entries.map(([key, value]) => (
              <div key={key} className="flex justify-between text-muted-foreground text-xs">
                <span>{key}</span>
                <span className="ml-2 truncate">{value}</span>
              </div>
            ))}
          </div>
        </div>
      </HTMLContainer>
    )
  }

  indicator(shape: EntityCardShape) {
    return <rect width={shape.props.w} height={shape.props.h} rx={6} />
  }
}

// ============ 思维导图节点 Shape ============

/** 思维导图节点 Shape 属性 */
type MindMapNodeShape = TLBaseShape<
  "mindmap-node",
  {
    w: number
    h: number
    text: string
    level: number
    collapsed: boolean
  }
>

/** 思维导图节点 Shape 工具 */
export class MindMapNodeShapeUtil extends BaseBoxShapeUtil<MindMapNodeShape> {
  static override type = "mindmap-node" as const

  getDefaultProps(): MindMapNodeShape["props"] {
    return { w: 160, h: 40, text: "", level: 0, collapsed: false }
  }

  component(shape: MindMapNodeShape) {
    const bgColors = ["bg-primary/10", "bg-secondary/10", "bg-muted"]
    const bgClass = bgColors[Math.min(shape.props.level, bgColors.length - 1)]
    return (
      <HTMLContainer>
        <div className={`flex h-full w-full items-center rounded-full px-4 ${bgClass} border`}>
          <span className="truncate text-sm">{shape.props.text || "节点"}</span>
          {shape.props.collapsed && <span className="ml-1 text-muted-foreground text-xs">+</span>}
        </div>
      </HTMLContainer>
    )
  }

  indicator(shape: MindMapNodeShape) {
    return <rect width={shape.props.w} height={shape.props.h} rx={shape.props.h / 2} />
  }
}

/** 所有自定义 Shape 工具列表 */
export const canvasCustomShapeUtils = [
  StickyNoteShapeUtil,
  EntityCardShapeUtil,
  MindMapNodeShapeUtil
] as TLShapeUtilConstructor<never>[]
