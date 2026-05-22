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
export type ChatterLayout = "panel" | "dialog" | "drawer"

/** 对话目标 */
export interface ChatterTarget {
  type: "ai" | "kiro" | "user"
  agentRole?: string
  userId?: string
}

/** 拖放数据项 */
export interface ChatterDropItem {
  type: "doc" | "file" | "image" | "text"
  id?: number
  title?: string
  content?: string
  url?: string
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
