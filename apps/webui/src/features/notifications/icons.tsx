/**
 * 通知类型图标
 * @author AaronZZH & Kiro
 */

import { Bell, CheckCircle, ClipboardCheck, Cog, MessageCircle } from "lucide-react"
import { cn } from "@/lib/utils/cn"

const iconConfig: Record<string, { icon: typeof Bell; bg: string; color: string }> = {
  approval: {
    icon: ClipboardCheck,
    bg: "bg-orange-100 dark:bg-orange-900/30",
    color: "text-orange-600 dark:text-orange-400"
  },
  system: {
    icon: Cog,
    bg: "bg-gray-100 dark:bg-gray-800",
    color: "text-gray-600 dark:text-gray-400"
  },
  mention: {
    icon: MessageCircle,
    bg: "bg-blue-100 dark:bg-blue-900/30",
    color: "text-blue-600 dark:text-blue-400"
  },
  task: {
    icon: CheckCircle,
    bg: "bg-green-100 dark:bg-green-900/30",
    color: "text-green-600 dark:text-green-400"
  }
}

export function NotificationIcon({ type }: { type: string }) {
  const config = iconConfig[type] ?? { icon: Bell, bg: "bg-muted", color: "text-muted-foreground" }
  const Icon = config.icon

  return (
    <span
      className={cn("flex size-9 shrink-0 items-center justify-center rounded-full", config.bg)}
    >
      <Icon className={cn("size-4", config.color)} />
    </span>
  )
}
