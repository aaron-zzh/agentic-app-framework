/**
 * NotificationDrawer——通知抽屉（铃铛按钮 + Sheet 面板）
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import { useQueryClient } from "@tanstack/react-query"
import { Bell } from "lucide-react"
import Link from "next/link"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger
} from "@/components/ui/sheet"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { buildWsUrl } from "@/lib/api/config"
import type { NotificationItem as NotificationItemType } from "@/lib/api/rest/user/notification"
import { paths } from "@/lib/constants/paths"
import { useWebSocket } from "@/lib/hooks/use-websocket"
import { notify } from "@/lib/notification"
import { useMarkRead, useNotifications, useUnreadCount } from "@/lib/queries/use-notifications"
import { useAuthStore } from "@/lib/store/auth-store"
import { CountBadge } from "./count-badge"
import { NotificationItem } from "./notification-item"

export function NotificationDrawer() {
  const { data: countData } = useUnreadCount()
  const unreadCount = countData ?? 0
  const { value: open, setValue: setOpen, onFalse: onClose } = useBoolean()
  const qc = useQueryClient()
  const accessToken = useAuthStore((s) => s.accessToken)

  // WS 接入：JWT 握手认证，收到通知推送时刷新通知列表和未读数
  useWebSocket({
    url: buildWsUrl("/ws/notifications"),
    enabled: !!accessToken,
    onMessage: (data) => {
      try {
        const msg = JSON.parse(data) as { type: string; title?: string; body?: string }
        if (msg.type === "notification") {
          qc.invalidateQueries({ queryKey: ["notifications"] })
          notify.info(msg.title ?? "新通知", { description: msg.body })
        }
      } catch {
        // 忽略非 JSON 消息
      }
    }
  })

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        className="relative rounded-md p-1.5 text-muted-foreground hover:bg-accent"
        aria-label="通知"
        render={<button type="button" />}
      >
        <Bell className="size-5" />
        {unreadCount > 0 && (
          <Badge
            variant="destructive"
            className="absolute -top-1 -right-1 size-[18px] justify-center border-2 border-background p-0 text-[10px]"
          >
            {unreadCount > 99 ? "99+" : unreadCount}
          </Badge>
        )}
      </SheetTrigger>
      <SheetContent side="right">
        <SheetHeader>
          <SheetTitle>通知</SheetTitle>
          <SheetDescription>
            {unreadCount > 0 ? `您有 ${unreadCount} 条未读消息` : "暂无未读消息"}
          </SheetDescription>
        </SheetHeader>
        <NotificationPanel onClose={onClose} />
      </SheetContent>
    </Sheet>
  )
}

function NotificationPanel({ onClose }: { onClose: () => void }) {
  const { data: all } = useNotifications()
  const { mutate: markRead } = useMarkRead()

  const allItems = all?.list ?? []
  const unreadItems = allItems.filter((n) => !n.isRead)
  const readItems = allItems.filter((n) => n.isRead)

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      <div className="flex shrink-0 items-center justify-between px-4 py-2">
        {unreadItems.length > 0 && (
          <button
            type="button"
            className="text-muted-foreground text-xs hover:text-foreground"
            onClick={() => markRead(undefined)}
          >
            全部标为已读
          </button>
        )}
      </div>

      <Tabs defaultValue="all" className="flex flex-1 flex-col overflow-hidden">
        <TabsList className="w-full shrink-0 rounded-none px-4">
          <TabsTrigger value="all">
            全部
            <CountBadge count={allItems.length} />
          </TabsTrigger>
          <TabsTrigger value="unread">
            未读
            <CountBadge count={unreadItems.length} variant="info" />
          </TabsTrigger>
          <TabsTrigger value="read">
            已读
            <CountBadge count={readItems.length} variant="success" />
          </TabsTrigger>
        </TabsList>

        <TabsContent value="all" className="flex-1 overflow-y-auto">
          <NotificationList items={allItems} onRead={(id) => markRead([id])} onClose={onClose} />
        </TabsContent>
        <TabsContent value="unread" className="flex-1 overflow-y-auto">
          <NotificationList items={unreadItems} onRead={(id) => markRead([id])} onClose={onClose} />
        </TabsContent>
        <TabsContent value="read" className="flex-1 overflow-y-auto">
          <NotificationList items={readItems} onClose={onClose} />
        </TabsContent>
      </Tabs>

      <div className="shrink-0 border-t p-4">
        <Button
          variant="outline"
          className="w-full"
          nativeButton={false}
          render={<Link href={paths.workspace.notifications} onClick={onClose} />}
        >
          查看全部通知
        </Button>
      </div>
    </div>
  )
}

function NotificationList({
  items,
  onRead,
  onClose
}: {
  items: NotificationItemType[]
  onRead?: (id: number) => void
  onClose?: () => void
}) {
  if (!items || items.length === 0) {
    return <p className="p-8 text-center text-muted-foreground text-sm">暂无通知</p>
  }
  return (
    <ul>
      {items.map((item) => (
        <NotificationItem key={item.id} notification={item} onRead={onRead} onClose={onClose} />
      ))}
    </ul>
  )
}
