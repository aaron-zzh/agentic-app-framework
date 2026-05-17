/**
 * NotificationDrawer——通知抽屉（铃铛按钮 + Sheet 面板）
 * @author AaronZZH & Kiro
 */

"use client"

import { Bell } from "lucide-react"
import { Badge as ShadcnBadge } from "@/components/ui/badge"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger
} from "@/components/ui/sheet"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { _notifications } from "@/lib/_mock/notifications"
import { CountBadge } from "./count-badge"
import { NotificationItem } from "./notification-item"

export function NotificationDrawer() {
  const totalUnRead = _notifications.filter((n) => n.isUnRead).length

  return (
    <Sheet>
      <SheetTrigger
        className="relative rounded-md p-1.5 text-muted-foreground hover:bg-accent"
        aria-label="通知"
        render={<button type="button" />}
      >
        <Bell className="size-5" />
        {totalUnRead > 0 && (
          <ShadcnBadge
            variant="destructive"
            className="absolute -top-1 -right-1 size-[18px] justify-center border-2 border-background p-0 text-[10px]"
          >
            {totalUnRead}
          </ShadcnBadge>
        )}
      </SheetTrigger>
      <SheetContent side="right">
        <SheetHeader>
          <SheetTitle>通知</SheetTitle>
          <SheetDescription>您有 {totalUnRead} 条未读消息</SheetDescription>
        </SheetHeader>
        <NotificationList />
      </SheetContent>
    </Sheet>
  )
}

function NotificationList() {
  const all = _notifications
  const unread = _notifications.filter((n) => n.isUnRead)
  const read = _notifications.filter((n) => !n.isUnRead)

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      <Tabs defaultValue="all" className="flex flex-1 flex-col overflow-hidden">
        <TabsList className="w-full shrink-0 rounded-none px-4">
          <TabsTrigger value="all">
            全部
            <CountBadge count={all.length} />
          </TabsTrigger>
          <TabsTrigger value="unread">
            未读
            <CountBadge count={unread.length} variant="info" />
          </TabsTrigger>
          <TabsTrigger value="read">
            已读
            <CountBadge count={read.length} variant="success" />
          </TabsTrigger>
        </TabsList>

        <TabsContent value="all" className="flex-1 overflow-y-auto">
          <NotificationItems items={all} />
        </TabsContent>
        <TabsContent value="unread" className="flex-1 overflow-y-auto">
          <NotificationItems items={unread} />
        </TabsContent>
        <TabsContent value="read" className="flex-1 overflow-y-auto">
          <NotificationItems items={read} />
        </TabsContent>
      </Tabs>

      <div className="shrink-0 border-t p-4">
        <button
          type="button"
          className="w-full rounded-md border py-2 text-center text-muted-foreground text-sm hover:bg-accent"
        >
          查看全部通知
        </button>
      </div>
    </div>
  )
}

function NotificationItems({ items }: { items: typeof _notifications }) {
  if (items.length === 0) {
    return <p className="p-8 text-center text-muted-foreground text-sm">暂无通知</p>
  }
  return (
    <ul className="divide-y">
      {items.map((item) => (
        <NotificationItem key={item.id} notification={item} />
      ))}
    </ul>
  )
}
