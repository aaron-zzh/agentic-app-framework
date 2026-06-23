/**
 * 通知面板——TODO 接入实际通知接口
 * @author AaronZZH & Kiro
 */

"use client"

import { Bell } from "lucide-react"
import Link from "next/link"

export function NotificationPanel() {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-2 text-center">
      <Bell className="size-5 opacity-40" />
      <p className="text-muted-foreground text-xs">通知功能集成中</p>
      <Link
        href="/studio/me/settings"
        className="text-primary text-xs underline-offset-2 hover:underline"
      >
        通知偏好设置
      </Link>
    </div>
  )
}
