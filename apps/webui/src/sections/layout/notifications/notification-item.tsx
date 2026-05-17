/**
 * NotificationItem——单条通知（含按类型的操作按钮）
 * @author AaronZZH & Kiro
 */

import type { Notification } from "@/lib/_mock/notifications"
import { cn } from "@/lib/utils/cn"
import { NotificationIcon } from "./icons"

interface NotificationItemProps {
  notification: Notification
}

export function NotificationItem({ notification }: NotificationItemProps) {
  return (
    <li
      className={cn(
        "flex gap-3 border-b border-dashed px-4 py-3 last:border-0",
        notification.isUnRead && "bg-primary/5"
      )}
    >
      <NotificationIcon type={notification.type} />
      <div className="min-w-0 flex-1">
        <p className={cn("text-sm", notification.isUnRead && "font-medium")}>
          {notification.title}
        </p>
        {notification.description && (
          <p className="mt-0.5 text-muted-foreground text-xs">{notification.description}</p>
        )}
        <p className="mt-1 text-muted-foreground text-xs">
          {formatTimeAgo(notification.createdAt)}
        </p>
        <NotificationAction type={notification.type} />
      </div>
      {notification.isUnRead && <span className="mt-2 size-2 shrink-0 rounded-full bg-primary" />}
    </li>
  )
}

/** 按通知类型渲染操作按钮 */
function NotificationAction({ type }: { type: string }) {
  switch (type) {
    case "approval":
      return (
        <div className="mt-2 flex gap-2">
          <button
            type="button"
            className="rounded bg-primary px-3 py-1 text-primary-foreground text-xs hover:bg-primary/90"
          >
            审批
          </button>
          <button type="button" className="rounded border px-3 py-1 text-xs hover:bg-accent">
            驳回
          </button>
        </div>
      )
    case "mention":
      return (
        <div className="mt-2">
          <button type="button" className="rounded border px-3 py-1 text-xs hover:bg-accent">
            查看详情
          </button>
        </div>
      )
    case "task":
      return (
        <div className="mt-2 flex gap-2">
          <button
            type="button"
            className="rounded bg-primary px-3 py-1 text-primary-foreground text-xs hover:bg-primary/90"
          >
            处理
          </button>
          <button type="button" className="rounded border px-3 py-1 text-xs hover:bg-accent">
            忽略
          </button>
        </div>
      )
    default:
      return null
  }
}

function formatTimeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}
