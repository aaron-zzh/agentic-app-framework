/**
 * 待办视图页面——待处理/已完成/已忽略 Tab
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import Link from "next/link"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { TypographyH1 } from "@/components/ui/typography"
import type { TodoItem, TodoSourceType, TodoStatus } from "@/lib/api/todo"
import { paths } from "@/lib/constants/paths"
import { useTodoComplete, useTodoDismiss, useTodos } from "@/lib/queries/use-todos"
import { cn } from "@/lib/utils/cn"

const SOURCE_LABELS: Record<TodoSourceType, string> = {
  mention: "提及",
  schedule: "调度",
  manual: "手动"
}

type Tab = TodoStatus

export default function TodosPage() {
  const [activeTab, setActiveTab] = useState<Tab>("pending")
  const { data } = useTodos({ status: activeTab })
  const { mutate: complete } = useTodoComplete()
  const { mutate: dismiss } = useTodoDismiss()

  const items = data?.list ?? []

  return (
    <PageContainer>
      <TypographyH1 className="mb-6 text-2xl">我的待办</TypographyH1>

      <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as Tab)} className="mb-3">
        <TabsList>
          <TabsTrigger value="pending">待处理</TabsTrigger>
          <TabsTrigger value="done">已完成</TabsTrigger>
          <TabsTrigger value="dismissed">已忽略</TabsTrigger>
        </TabsList>
      </Tabs>

      <div className="rounded-lg border">
        {items.length === 0 ? (
          <Empty className="py-12">
            <EmptyHeader>
              <EmptyTitle>暂无待办</EmptyTitle>
              <EmptyDescription>
                {activeTab === "pending" ? "所有待办已处理" : "没有相关记录"}
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <ScrollArea className="max-h-[600px]">
            <ul>
              {items.map((item) => (
                <TodoRow
                  key={item.id}
                  item={item}
                  onComplete={() => complete(item.id)}
                  onDismiss={() => dismiss(item.id)}
                />
              ))}
            </ul>
          </ScrollArea>
        )}
      </div>
    </PageContainer>
  )
}

/** 判断日期是否已过期 */
function isOverdue(dueDate: string): boolean {
  return new Date(dueDate) < new Date()
}

function TodoRow({
  item,
  onComplete,
  onDismiss
}: {
  item: TodoItem
  onComplete: () => void
  onDismiss: () => void
}) {
  /** 构建来源记录链接 */
  const sourceHref =
    item.sourceEntity && item.sourceId
      ? paths.workspace.record(item.sourceEntity, item.sourceId)
      : undefined

  return (
    <li className="flex items-center gap-3 border-b border-dashed px-4 py-3 last:border-0">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          {sourceHref ? (
            <Link href={sourceHref} className="truncate text-sm font-medium hover:underline">
              {item.title}
            </Link>
          ) : (
            <span className="truncate text-sm font-medium">{item.title}</span>
          )}
          <Badge variant="outline" className="shrink-0 text-[10px]">
            {SOURCE_LABELS[item.sourceType] ?? item.sourceType}
          </Badge>
        </div>
        {item.dueDate && (
          <p
            className={cn(
              "mt-0.5 text-xs",
              isOverdue(item.dueDate) ? "text-red-500" : "text-muted-foreground"
            )}
          >
            截止：{new Date(item.dueDate).toLocaleDateString("zh-CN")}
          </p>
        )}
      </div>

      {item.status === "pending" && (
        <div className="flex shrink-0 gap-1">
          <Button variant="outline" size="sm" onClick={onComplete}>
            完成
          </Button>
          <Button variant="ghost" size="sm" onClick={onDismiss}>
            忽略
          </Button>
        </div>
      )}
    </li>
  )
}
