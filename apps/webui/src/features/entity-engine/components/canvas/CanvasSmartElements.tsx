/**
 * 画板智能元素——便签、嵌入卡片、思维导图、流程图模式
 * 通过 tldraw 自定义 shape 实现
 *
 * 注意：tldraw v5 自定义 shape API 需要完整的 geometry 实现，
 * 当前为占位实现，v2.0 画板功能正式开发时需按 tldraw 文档完善。
 * @author AaronZZH & Kiro
 */

import type { TLBaseShape } from "tldraw"

// ============ 便签 Shape ============

/** 便签颜色分类 */
export type StickyNoteColor = "yellow" | "pink" | "blue" | "green" | "purple"

/** 便签 Shape 属性 */
export type StickyNoteShape = TLBaseShape<
  "sticky-note",
  {
    w: number
    h: number
    text: string
    color: StickyNoteColor
  }
>

/** 便签颜色映射 */
export const STICKY_COLORS: Record<StickyNoteColor, string> = {
  yellow: "#fef3c7",
  pink: "#fce7f3",
  blue: "#dbeafe",
  green: "#dcfce7",
  purple: "#f3e8ff"
}

// ============ 嵌入实体卡片 Shape ============

/** 嵌入卡片 Shape 属性 */
export type EntityCardShape = TLBaseShape<
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

// ============ 思维导图节点 Shape ============

/** 思维导图节点 Shape 属性 */
export type MindMapNodeShape = TLBaseShape<
  "mindmap-node",
  {
    w: number
    h: number
    text: string
    level: number
    collapsed: boolean
  }
>

/**
 * 自定义 Shape 工具列表占位
 * tldraw v5 的 ShapeUtil 需要实现 getGeometry/component/indicator 等抽象方法，
 * 完整实现将在 v2.0 画板功能开发时完成。
 */
export const canvasCustomShapeUtils: unknown[] = []
