/**
 * 消息中心完整页面
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { TypographyH1 } from "@/components/ui/typography"
import type { NotificationItem, NotificationType } from "@/lib/api/rest/user/notification"
import { notify } from "@/lib/notification"
import {
  useMarkRead,
  useNotifications,
  useRemoveNotifications
} from "@/lib/queries/use-notifications"
import { cn } from "@/lib/utils/cn"
import { formatTimeAgo } from "@/lib/utils/time"
import { NotificationIcon } from "@/sections/layout/notifications/icons"

const TYPE_LABELS: Record<NotificationType | "all", string> = {
  all: "全部",
  approval: "审批",
  system: "系统",
  mention: "协作",
  task: "业务",
  change: "变更"
}

type Tab = "all" | "unread" | "read"

export default function NotificationsPage() {
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  const [activeTab, setActiveTab] = useState<Tab>("all")

  const { data } = useNotifications()
  const { mutate: markRead } = useMarkRead()
  const { mutate: remove } = useRemoveNotifications()

  const allItems = data?.list ?? []
  const unreadItems = allItems.filter((n) => !n.isRead)
  const readItems = allItems.filter((n) => n.isRead)
  const currentItems =
    activeTab === "all" ? allItems : activeTab === "unread" ? unreadItems : readItems

  const allSelected = selectedIds.size === currentItems.length && currentItems.length > 0
  const someSelected = selectedIds.size > 0 && !allSelected

  const toggleSelect = (id: number) =>
    setSelectedIds((prev) => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })

  const handleMarkRead = () => {
    const ids = selectedIds.size > 0 ? [...selectedIds] : undefined
    markRead(ids, {
      onSuccess: () => {
        setSelectedIds(new Set())
        notify.success(ids ? `已标记 ${ids.length} 条为已读` : "已全部标为已读")
      }
    })
  }

  const handleRemove = () => {
    if (selectedIds.size === 0) return
    const ids = [...selectedIds]
    remove(ids, {
      onSuccess: () => {
        setSelectedIds(new Set())
        notify.success(`已删除 ${ids.length} 条通知`, {
          action: { label: "撤销", onClick: () => notify.info("暂不支持撤销删除") }
        })
      }
    })
  }

  return (
    <PageContainer>
      <TypographyH1 className="mb-6 text-2xl">消息中心</TypographyH1>

      {/* Tab 切换 + 工具栏同行 */}
      <Tabs
        value={activeTab}
        onValueChange={(v) => {
          setActiveTab(v as Tab)
          setSelectedIds(new Set())
        }}
        className="mb-3"
      >
        <div className="flex items-center justify-between gap-2">
          <TabsList>
            <TabsTrigger value="all">
              全部
              {allItems.length > 0 && (
                <Badge variant="secondary" className="ml-1.5 text-[10px]">
                  {allItems.length}
                </Badge>
              )}
            </TabsTrigger>
            <TabsTrigger value="unread">
              未读
              {unreadItems.length > 0 && (
                <Badge className="ml-1.5 bg-blue-100 text-[10px] text-blue-700 dark:bg-blue-900/30 dark:text-blue-400">
                  {unreadItems.length}
                </Badge>
              )}
            </TabsTrigger>
            <TabsTrigger value="read">已读</TabsTrigger>
          </TabsList>

          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={handleMarkRead}>
              {selectedIds.size > 0 ? `标记已读 (${selectedIds.size})` : "全部标为已读"}
            </Button>
            {selectedIds.size > 0 && (
              <Button variant="outline" size="sm" onClick={handleRemove}>
                删除 ({selectedIds.size})
              </Button>
            )}
          </div>
        </div>
      </Tabs>

      {/* 单一列表 */}
      <div className="rounded-lg border">
        {currentItems.length === 0 ? (
          <Empty className="py-12">
            <EmptyHeader>
              <EmptyTitle>暂无通知</EmptyTitle>
              <EmptyDescription>
                {activeTab === "unread" ? "所有通知均已读" : "没有相关通知"}
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <>
            <div className="flex items-center gap-3 px-4 py-2">
              <Checkbox
                checked={allSelected}
                indeterminate={someSelected}
                onCheckedChange={() =>
                  setSelectedIds(allSelected ? new Set() : new Set(currentItems.map((n) => n.id)))
                }
                aria-label="全选"
              />
              <span className="text-muted-foreground text-xs">
                {selectedIds.size > 0 ? `已选 ${selectedIds.size} 条` : "全选"}
              </span>
            </div>
            <Separator />
            <ScrollArea className="max-h-[600px]">
              <ul>
                {currentItems.map((item) => (
                  <NotificationRow
                    key={item.id}
                    item={item}
                    selected={selectedIds.has(item.id)}
                    onToggle={() => toggleSelect(item.id)}
                    onRead={(id) => markRead([id])}
                  />
                ))}
              </ul>
            </ScrollArea>
          </>
        )}
      </div>
    </PageContainer>
  )
}

/** 按通知类型决定跳转 aria-label */
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

function NotificationRow({
  item,
  selected,
  onToggle,
  onRead
}: {
  item: NotificationItem
  selected: boolean
  onToggle: () => void
  onRead?: (id: number) => void
}) {
  const handleNavigate = () => {
    if (!item.isRead) onRead?.(item.id)
  }

  const titleEl = (
    <p
      className={cn(
        "text-sm",
        !item.isRead && "font-medium",
        item.relatedUrl && "text-blue-600 dark:text-blue-400"
      )}
    >
      {item.title}
    </p>
  )

  return (
    <li
      className={cn(
        "flex items-start gap-3 border-b border-dashed px-4 py-3 last:border-0",
        !item.isRead && "bg-primary/5",
        selected && "bg-accent"
      )}
    >
      <Checkbox
        className="mt-1 shrink-0"
        checked={selected}
        onCheckedChange={onToggle}
        aria-label={`选择通知: ${item.title}`}
      />
      <NotificationIcon type={item.type} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          {item.relatedUrl ? (
            <Link
              href={item.relatedUrl}
              onClick={handleNavigate}
              className="min-w-0 flex-1 hover:underline"
            >
              {titleEl}
            </Link>
          ) : (
            <div className="min-w-0 flex-1">{titleEl}</div>
          )}
          <div className="flex shrink-0 items-center gap-2">
            <Badge variant="outline" className="text-[10px]">
              {TYPE_LABELS[item.type] ?? item.type}
            </Badge>
            {item.relatedUrl && (
              <Button
                size="sm"
                variant="outline"
                nativeButton={false}
                render={<Link href={item.relatedUrl} onClick={handleNavigate} />}
                aria-label={actionLabel(item.type)}
                className="h-6 px-2 text-xs"
              >
                {actionLabel(item.type)}
              </Button>
            )}
          </div>
        </div>
        {item.body && <p className="mt-0.5 text-muted-foreground text-xs">{item.body}</p>}
        <p className="mt-1 text-muted-foreground text-xs">{formatTimeAgo(item.createTime)}</p>
      </div>
      {!item.isRead && <span className="mt-2 size-2 shrink-0 rounded-full bg-primary" />}
    </li>
  )
}
