/**
 * NotificationItem——单条通知
 * @author AaronZZH & Kiro
 */

"use client"

import { ArrowRight } from "lucide-react"
import Link from "next/link"
import type { NotificationItem as NotificationItemType } from "@/lib/api/rest/user/notification"
import { cn } from "@/lib/utils/cn"
import { formatTimeAgo } from "@/lib/utils/time"
import { NotificationIcon } from "./icons"

interface Props {
  notification: NotificationItemType
  onRead?: (id: number) => void
  /** 点击带 relatedUrl 的通知后关闭外层抽屉，避免 Sheet 遮罩盖住目标页 */
  onClose?: () => void
}

/** 按通知类型决定 action 标签 */
function actionLabel(type: string): string {
  switch (type) {
    case "mention":
      return "查看评论"
    case "approval":
      return "去审批"
    case "task":
      return "查看任务"
    case "change":
      return "查看变更"
    default:
      return "查看详情"
  }
}

export function NotificationItem({ notification, onRead, onClose }: Props) {
  const handleClick = () => {
    onRead?.(notification.id)
    onClose?.()
  }

  const inner = (
    <div className="flex w-full gap-3 px-4 py-3 text-left hover:bg-accent/50">
      <NotificationIcon type={notification.type} />
      <div className="min-w-0 flex-1">
        <p className={cn("text-sm", !notification.isRead && "font-medium")}>{notification.title}</p>
        {notification.body && (
          <p className="mt-0.5 line-clamp-2 text-muted-foreground text-xs">{notification.body}</p>
        )}
        <div className="mt-1 flex items-center gap-3">
          <span className="text-muted-foreground text-xs">
            {formatTimeAgo(notification.createTime)}
          </span>
          {notification.relatedUrl && (
            <span className="flex items-center gap-0.5 text-primary text-xs hover:underline">
              {actionLabel(notification.type)}
              <ArrowRight className="size-3" />
            </span>
          )}
        </div>
      </div>
      {!notification.isRead && <span className="mt-2 size-2 shrink-0 rounded-full bg-primary" />}
    </div>
  )

  return (
    <li
      className={cn("border-b border-dashed last:border-0", !notification.isRead && "bg-primary/5")}
    >
      {notification.relatedUrl ? (
        <Link href={notification.relatedUrl} onClick={handleClick} className="block">
          {inner}
        </Link>
      ) : (
        <button type="button" className="w-full" onClick={handleClick}>
          {inner}
        </button>
      )}
    </li>
  )
}
