/**
 * ShortcutWidget——快捷入口（金刚区风格）
 *
 * <p>无卡片容器、无 hover 动效，纯图标 + label 列表；左对齐 + 自动换行，
 * 适合任意 item 数量，对齐手机首页金刚区视觉。
 *
 * @author AaronZZH &amp; Kiro
 */

"use client"

import {
  Bot,
  Box,
  Database,
  FileText,
  Image,
  type LucideIcon,
  MessageSquare,
  Package,
  Settings,
  ShoppingBag,
  Sparkles,
  Users,
  Video,
  Wand2
} from "lucide-react"
import Link from "next/link"
import type { ShortcutWidgetConfig } from "@/lib/api/rest/dashboard/dashboard"
import { cn } from "@/lib/utils/cn"

interface ShortcutWidgetProps {
  title: string
  config: ShortcutWidgetConfig
}

/** 图标映射（shortcut 常用集，未匹配回落 Sparkles） */
const SHORTCUT_ICON_MAP: Record<string, LucideIcon> = {
  sparkles: Sparkles,
  image: Image,
  database: Database,
  settings: Settings,
  bot: Bot,
  box: Box,
  "file-text": FileText,
  "message-square": MessageSquare,
  package: Package,
  "shopping-bag": ShoppingBag,
  users: Users,
  video: Video,
  "wand-2": Wand2
}

export function ShortcutWidget({ title, config }: ShortcutWidgetProps) {
  return (
    <div className="flex h-full flex-wrap content-start items-start gap-x-6 gap-y-4 p-2">
      {config.items.map((item) => {
        const Icon = SHORTCUT_ICON_MAP[item.icon] ?? Sparkles
        return (
          <Link
            key={item.href}
            href={item.href}
            title={title ? `${title} · ${item.label}` : item.label}
            className="flex w-16 flex-col items-center gap-1.5"
          >
            <div
              className={cn(
                "flex h-12 w-12 items-center justify-center rounded-2xl",
                "bg-primary/10 text-primary shadow-sm"
              )}
            >
              <Icon className="h-6 w-6" strokeWidth={1.75} />
            </div>
            <span className="line-clamp-1 text-center text-foreground/80 text-xs">
              {item.label}
            </span>
          </Link>
        )
      })}
    </div>
  )
}
