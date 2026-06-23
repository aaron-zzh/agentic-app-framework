/**
 * Studio 面板类型注册表
 *
 * 每个面板类型定义：
 * - 渲染组件
 * - 默认宽度
 * - 标题与图标
 * - 是否需要 payload（如天气需要 city）
 *
 * @author AaronZZH & Kiro
 */

import type { LucideIcon } from "lucide-react"
import { Bell, Cloud, Coins, FolderOpen, ListTodo } from "lucide-react"
import type { ComponentType } from "react"
import { CreditsPanel } from "./panels/CreditsPanel"
import { NotificationPanel } from "./panels/NotificationPanel"
import { ProjectSummaryPanel } from "./panels/ProjectSummaryPanel"
import { RecentTasksPanel } from "./panels/RecentTasksPanel"
import { WeatherPanel } from "./panels/WeatherPanel"
import type { SlotPanelType } from "./store"

export interface SlotPanelProps {
  payload?: Record<string, unknown>
}

export interface SlotPanelDef {
  title: string
  icon: LucideIcon
  /** 默认宽度（px） */
  defaultWidth: number
  /** 最小宽度 */
  minWidth: number
  /** 最大宽度 */
  maxWidth: number
  /** 组件 */
  component: ComponentType<SlotPanelProps>
  /** 主题色调（玻璃光晕） */
  tone: "violet" | "cyan" | "emerald" | "amber" | "rose"
}

export const SLOT_REGISTRY: Record<SlotPanelType, SlotPanelDef> = {
  weather: {
    title: "实时天气",
    icon: Cloud,
    defaultWidth: 280,
    minWidth: 240,
    maxWidth: 420,
    component: WeatherPanel,
    tone: "amber"
  },
  "recent-tasks": {
    title: "任务进度",
    icon: ListTodo,
    defaultWidth: 320,
    minWidth: 260,
    maxWidth: 480,
    component: RecentTasksPanel,
    tone: "violet"
  },
  notifications: {
    title: "通知",
    icon: Bell,
    defaultWidth: 300,
    minWidth: 240,
    maxWidth: 420,
    component: NotificationPanel,
    tone: "rose"
  },
  credits: {
    title: "积分余额",
    icon: Coins,
    defaultWidth: 240,
    minWidth: 200,
    maxWidth: 360,
    component: CreditsPanel,
    tone: "emerald"
  },
  "project-summary": {
    title: "项目摘要",
    icon: FolderOpen,
    defaultWidth: 320,
    minWidth: 260,
    maxWidth: 480,
    component: ProjectSummaryPanel,
    tone: "cyan"
  }
}
