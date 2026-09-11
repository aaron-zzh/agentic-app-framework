/**
 * Chatter 组件类型定义
 * 统一对话组件的 preset、layout、target 和拖放数据模型
 *
 * @author AaronZZH & Kiro
 */

import type { ReactNode } from "react"

/** 场景预设：决定默认 target + 是否持久化 */
export type ChatterPreset = "ai" | "kiro" | "livechat" | "guest"

/** 布局模式 */
export type ChatterLayout = "panel" | "dialog" | "drawer" | "page"

/** Dynamic 任务使用的 CHAT LLM 选择。 */
export type TaskModelSelection = { mode: "AUTO" } | { mode: "EXPLICIT"; modelId: string }

/** Chatter 默认由后端能力路由自动选择任务模型。 */
export const DEFAULT_TASK_MODEL_SELECTION: TaskModelSelection = { mode: "AUTO" }

/** 仅控制服务端计划进度和安全推理摘要的 UI 展示，不改变执行决策。 */
export interface ChatterDisplayPreferences {
  showPlan: boolean
  showThinking: boolean
}

export const DEFAULT_CHATTER_DISPLAY_PREFERENCES: ChatterDisplayPreferences = {
  showPlan: true,
  showThinking: false
}

/** 对话目标 */
export interface ChatterTarget {
  type: "ai" | "guest" | "kiro" | "user"
  agentRole?: string
  /** 显式指定 Skill code（AAF-107 #10708），与 agentRole 各自独立可选。 */
  agentSkill?: string
  userId?: string
  /** 稳定 Assistant ID，由 AG-UI initialState 发送。 */
  assistantId?: string
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
  /** 所属素材组 ID（素材拖拽时携带，供 drop handler 判断是否需要变更分组） */
  groupId?: number
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
  /** 显式指定 Skill code（AAF-107 #10708），与 agentRole 各自独立可选。 */
  agentSkill?: string
  persist?: boolean
  defaultSize?: number
  minSize?: number
  maxSize?: number
  open?: boolean
  onOpenChange?: (open: boolean) => void
  onLayoutChange?: (layout: ChatterLayout) => void
  /** dialog 模式初始宽度（px），默认 380 */
  dialogWidth?: number
  /** dialog 模式初始高度（px），默认 560 */
  dialogHeight?: number
  /** dialog 模式右下锚点（距视口右、底的 px），默认 { right: 96, bottom: 96 } */
  dialogAnchor?: { right: number; bottom: number }
  toolbar?: ReactNode
  /** 隐藏顶部工具栏（已有外部导航时使用） */
  hideToolbar?: boolean
  onDrop?: (item: ChatterDropItem) => void
  sessionId?: string
  onSessionChange?: (sessionId: string) => void
}
