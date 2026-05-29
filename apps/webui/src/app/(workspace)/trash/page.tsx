/**
 * 回收站页面——已删除记录的恢复与彻底删除
 * @author AaronZZH & Kiro
 */

"use client"

import { RotateCcwIcon, Trash2Icon } from "lucide-react"
import { useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { TypographyH1 } from "@/components/ui/typography"
import { useTrashList, useTrashPurge, useTrashRestore } from "@/lib/queries/use-trash"

/** 相对时间格式化 */
function formatRelativeTime(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return "刚刚"
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return new Date(dateStr).toLocaleDateString("zh-CN")
}

export default function TrashPage() {
  const [entityType, setEntityType] = useState<string>("")
  const [purgeId, setPurgeId] = useState<string | null>(null)

  const { data } = useTrashList(entityType ? { entityType } : {})
  const { mutate: restore } = useTrashRestore()
  const { mutate: purge, isPending: purging } = useTrashPurge()

  const items = data?.list ?? []

  return (
    <PageContainer>
      <div className="mb-6 flex items-center justify-between">
        <TypographyH1 className="text-2xl">🗑️ 回收站</TypographyH1>

        {/* 实体类型筛选 */}
        <Select value={entityType} onValueChange={(v) => setEntityType(v ?? "")}>
          <SelectTrigger className="w-[160px]">
            <SelectValue placeholder="全部类型" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="">全部类型</SelectItem>
            <SelectItem value="document">文档</SelectItem>
            <SelectItem value="workflow">工作流</SelectItem>
            <SelectItem value="agent">Agent</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div className="rounded-lg border">
        {items.length === 0 ? (
          <Empty className="py-12">
            <EmptyHeader>
              <EmptyTitle>回收站为空</EmptyTitle>
              <EmptyDescription>没有已删除的记录</EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <ScrollArea className="max-h-[600px]">
            <ul>
              {items.map((item) => (
                <li
                  key={item.id}
                  className="flex items-center gap-3 border-b border-dashed px-4 py-3 last:border-0"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate font-medium text-sm">{item.title}</span>
                      <Badge variant="outline" className="shrink-0 text-[10px]">
                        {item.entityType}
                      </Badge>
                    </div>
                    <p className="mt-0.5 text-muted-foreground text-xs">
                      {item.deletedBy} · {formatRelativeTime(item.deletedAt)}
                    </p>
                  </div>

                  <div className="flex shrink-0 gap-1">
                    <Button variant="outline" size="sm" onClick={() => restore([item.id])}>
                      <RotateCcwIcon className="mr-1 size-3.5" />
                      恢复
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive hover:text-destructive"
                      onClick={() => setPurgeId(item.id)}
                    >
                      <Trash2Icon className="mr-1 size-3.5" />
                      彻底删除
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          </ScrollArea>
        )}
      </div>

      {/* 彻底删除确认 Dialog */}
      <Dialog open={purgeId !== null} onOpenChange={(open) => !open && setPurgeId(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认彻底删除</DialogTitle>
            <DialogDescription>此操作不可恢复，数据将被永久删除。确定继续？</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPurgeId(null)}>
              取消
            </Button>
            <Button
              variant="destructive"
              disabled={purging}
              onClick={() => {
                if (purgeId) {
                  purge([purgeId], { onSuccess: () => setPurgeId(null) })
                }
              }}
            >
              确认删除
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  )
}
