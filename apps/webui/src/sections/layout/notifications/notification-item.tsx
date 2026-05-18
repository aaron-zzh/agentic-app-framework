/**
 * NotificationItem——单条通知
 * @author AaronZZH & Kiro
 */

"use client"

import type { NotificationItem as NotificationItemType } from "@/lib/api/notification"
import { cn } from "@/lib/utils/cn"
import { formatTimeAgo } from "@/lib/utils/time"
import { NotificationIcon } from "./icons"

interface Props {
  notification: NotificationItemType
  onRead?: (id: string) => void
}

export function NotificationItem({ notification, onRead }: Props) {
  return (
    <li
      className={cn("border-b border-dashed last:border-0", !notification.read && "bg-primary/5")}
    >
      <button
        type="button"
        className="flex w-full gap-3 px-4 py-3 text-left hover:bg-accent/50"
        onClick={() => onRead?.(notification.id)}
      >
        <NotificationIcon type={notification.type} />
        <div className="min-w-0 flex-1">
          <p className={cn("text-sm", !notification.read && "font-medium")}>{notification.title}</p>
          {notification.body && (
            <p className="mt-0.5 text-muted-foreground text-xs">{notification.body}</p>
          )}
          <p className="mt-1 text-muted-foreground text-xs">
            {formatTimeAgo(notification.createdAt)}
          </p>
        </div>
        {!notification.read && <span className="mt-2 size-2 shrink-0 rounded-full bg-primary" />}
      </button>
    </li>
  )
}
