/**
 * Chatter 组件类型定义
 * 统一对话组件的 preset、layout、target 和拖放数据模型
 *
 * @author AaronZZH & Kiro
 */

import type { ReactNode } from "react"

/** 场景预设：决定默认 target + 是否持久化 */
export type ChatterPreset = "ai" | "kiro" | "livechat"

/** 布局模式 */
export type ChatterLayout = "panel" | "dialog" | "drawer" | "page"

/** 对话目标 */
export interface ChatterTarget {
  type: "ai" | "kiro" | "user"
  agentRole?: string
  userId?: string
}

/** 拖放数据项 */
export interface ChatterDropItem {
  type: "doc" | "file" | "image" | "video" | "text" | "view-context" | "field" | "record"
  id?: string | number
  title?: string
  /** 摘要（≤100 字符，用于 ContextChip 展示） */
  summary?: string
  content?: string
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

/** Chatter 组件 Props */
export interface ChatterProps {
  preset: ChatterPreset
  layout: ChatterLayout
  targetUserId?: string
  agentRole?: string
  persist?: boolean
  defaultSize?: number
  minSize?: number
  maxSize?: number
  open?: boolean
  onOpenChange?: (open: boolean) => void
  toolbar?: ReactNode
  onDrop?: (item: ChatterDropItem) => void
  sessionId?: string
  onSessionChange?: (sessionId: string) => void
}
