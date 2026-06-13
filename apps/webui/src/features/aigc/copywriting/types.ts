/**
 * 文案面板相关类型
 * @author AaronZZH & Kiro
 */

import type React from "react"

/** 文案面板底部动作按钮配置 */
export interface CopywritingAction {
  key: string
  label: string
  icon?: React.ReactNode
  /** variant 对应 shadcn Button variant */
  variant?: "default" | "outline" | "ghost" | "destructive" | "secondary"
  className?: string
  /** 是否在 generating 时禁用（默认 true） */
  disableWhileGenerating?: boolean
  onClick: (ctx: { content: string; generating: boolean }) => void
}
