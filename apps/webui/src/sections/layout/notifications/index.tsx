/**
 * NotificationDrawer——通知抽屉（铃铛按钮 + Sheet 面板）
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
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
import { paths } from "@/lib/constants/paths"
import { useMarkRead, useNotifications, useUnreadCount } from "@/lib/queries/use-notifications"
import { CountBadge } from "./count-badge"
import { NotificationItem } from "./notification-item"

export function NotificationDrawer() {
  const { data: countData } = useUnreadCount()
  const unreadCount = countData?.count ?? 0
  const { value: open, setValue: setOpen, onFalse: onClose } = useBoolean()

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
  const { data: unread } = useNotifications({ read: false })
  const { mutate: markRead } = useMarkRead()

  const allItems = all?.list ?? []
  const unreadItems = unread?.list ?? []
  const readItems = allItems.filter((n) => n.read)

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
          <NotificationList items={allItems} onRead={(id) => markRead([id])} />
        </TabsContent>
        <TabsContent value="unread" className="flex-1 overflow-y-auto">
          <NotificationList items={unreadItems} onRead={(id) => markRead([id])} />
        </TabsContent>
        <TabsContent value="read" className="flex-1 overflow-y-auto">
          <NotificationList items={readItems} />
        </TabsContent>
      </Tabs>

      <div className="shrink-0 border-t p-4">
        <Button variant="outline" className="w-full" asChild>
          <Link href={paths.workspace.notifications} onClick={onClose}>
            查看全部通知
          </Link>
        </Button>
      </div>
    </div>
  )
}

import type { NotificationItem as NotificationItemType } from "@/lib/api/notification"

function NotificationList({
  items,
  onRead
}: {
  items: NotificationItemType[]
  onRead?: (id: string) => void
}) {
  if (!items || items.length === 0) {
    return <p className="p-8 text-center text-muted-foreground text-sm">暂无通知</p>
  }
  return (
    <ul>
      {items.map((item) => (
        <NotificationItem key={item.id} notification={item} onRead={onRead} />
      ))}
    </ul>
  )
}
