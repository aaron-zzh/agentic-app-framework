/**
 * 消息中心完整页面
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { TypographyH1 } from "@/components/ui/typography"
import type { NotificationItem, NotificationType } from "@/lib/api/notification"
import { notify } from "@/lib/notification"
import {
  useMarkRead,
  useNotifications,
  useRemoveNotifications
} from "@/lib/queries/use-notifications"
import { cn } from "@/lib/utils/cn"
import { NotificationIcon } from "@/sections/layout/notifications/icons"

const TYPE_LABELS: Record<NotificationType | "all", string> = {
  all: "全部",
  approval: "审批",
  system: "系统",
  mention: "协作",
  task: "业务",
  change: "变更"
}

export default function NotificationsPage() {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [activeTab, setActiveTab] = useState<"all" | "unread" | "read">("all")

  const { data: allData } = useNotifications()
  const { data: unreadData } = useNotifications({ read: false })
  const { mutate: markRead } = useMarkRead()
  const { mutate: remove } = useRemoveNotifications()

  const allItems = allData?.list ?? []
  const unreadItems = unreadData?.list ?? []
  const readItems = allItems.filter((n) => n.read)

  const currentItems =
    activeTab === "all" ? allItems : activeTab === "unread" ? unreadItems : readItems

  const allSelected = selectedIds.size === currentItems.length && currentItems.length > 0
  const someSelected = selectedIds.size > 0 && !allSelected

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      next.has(id) ? next.delete(id) : next.add(id)
      return next
    })
  }

  const toggleSelectAll = () => {
    setSelectedIds(allSelected ? new Set() : new Set(currentItems.map((n) => n.id)))
  }

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
      <div className="flex flex-col gap-4">
        {/* 工具栏 */}
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

        <Tabs
          value={activeTab}
          onValueChange={(v) => {
            setActiveTab(v as typeof activeTab)
            setSelectedIds(new Set())
          }}
        >
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

          {(["all", "unread", "read"] as const).map((tab) => (
            <TabsContent key={tab} value={tab}>
              <div className="rounded-lg border">
                {currentItems.length === 0 ? (
                  <Empty className="py-12">
                    <EmptyHeader>
                      <EmptyTitle>暂无通知</EmptyTitle>
                      <EmptyDescription>
                        {tab === "unread" ? "所有通知均已读" : "没有相关通知"}
                      </EmptyDescription>
                    </EmptyHeader>
                  </Empty>
                ) : (
                  <>
                    {/* 全选行 */}
                    <div className="flex items-center gap-3 px-4 py-2">
                      <Checkbox
                        checked={allSelected}
                        indeterminate={someSelected}
                        onCheckedChange={toggleSelectAll}
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
                          />
                        ))}
                      </ul>
                    </ScrollArea>
                  </>
                )}
              </div>
            </TabsContent>
          ))}
        </Tabs>
      </div>
    </PageContainer>
  )
}

function NotificationRow({
  item,
  selected,
  onToggle
}: {
  item: NotificationItem
  selected: boolean
  onToggle: () => void
}) {
  return (
    <li
      className={cn(
        "flex items-start gap-3 border-b border-dashed px-4 py-3 last:border-0",
        !item.read && "bg-primary/5",
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
        <div className="flex items-start justify-between gap-2">
          <p className={cn("text-sm", !item.read && "font-medium")}>{item.title}</p>
          <Badge variant="outline" className="shrink-0 text-[10px]">
            {TYPE_LABELS[item.type] ?? item.type}
          </Badge>
        </div>
        {item.body && <p className="mt-0.5 text-muted-foreground text-xs">{item.body}</p>}
        <p className="mt-1 text-muted-foreground text-xs">
          {new Date(item.createdAt).toLocaleString("zh-CN")}
        </p>
      </div>
      {!item.read && <span className="mt-2 size-2 shrink-0 rounded-full bg-primary" />}
    </li>
  )
}
