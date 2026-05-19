/**
 * ConflictDialog——乐观锁冲突对话框，展示"我的修改 vs 服务端最新"
 * @author AaronZZH & Kiro
 */

"use client"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { ScrollArea } from "@/components/ui/scroll-area"

interface ConflictDialogProps {
  open: boolean
  /** 用户本地提交的数据 */
  myData: Record<string, unknown> | null
  /** 服务端最新数据 */
  serverData: Record<string, unknown> | null
  /** 强制覆盖（用本地数据覆盖服务端） */
  onOverwrite: () => void
  /** 刷新（放弃本地修改，使用服务端数据） */
  onRefresh: () => void
  /** 取消操作 */
  onCancel: () => void
}

/** 渲染字段对比行 */
function DiffRow({ field, mine, server }: { field: string; mine: unknown; server: unknown }) {
  const changed = JSON.stringify(mine) !== JSON.stringify(server)
  if (!changed) return null

  return (
    <div className="grid grid-cols-[1fr_1fr_1fr] gap-2 border-b py-2 text-xs last:border-b-0">
      <span className="font-medium text-muted-foreground">{field}</span>
      <span className="rounded bg-red-50 px-1 text-red-700 dark:bg-red-950 dark:text-red-300">
        {String(mine ?? "—")}
      </span>
      <span className="rounded bg-green-50 px-1 text-green-700 dark:bg-green-950 dark:text-green-300">
        {String(server ?? "—")}
      </span>
    </div>
  )
}

export function ConflictDialog({
  open,
  myData,
  serverData,
  onOverwrite,
  onRefresh,
  onCancel
}: ConflictDialogProps) {
  // 收集所有字段 key
  const allKeys = Array.from(
    new Set([...Object.keys(myData ?? {}), ...Object.keys(serverData ?? {})])
  ).filter((k) => k !== "version")

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onCancel()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>数据冲突</DialogTitle>
          <DialogDescription>
            该记录已被其他人修改，请选择如何处理。
          </DialogDescription>
        </DialogHeader>

        {/* 对比表头 */}
        <div className="grid grid-cols-[1fr_1fr_1fr] gap-2 text-xs font-semibold text-muted-foreground">
          <span>字段</span>
          <span>我的修改</span>
          <span>服务端最新</span>
        </div>

        {/* 对比内容 */}
        <ScrollArea className="max-h-60">
          {allKeys.map((key) => (
            <DiffRow
              key={key}
              field={key}
              mine={myData?.[key]}
              server={serverData?.[key]}
            />
          ))}
        </ScrollArea>

        <DialogFooter>
          <Button variant="destructive" onClick={onOverwrite}>
            强制覆盖
          </Button>
          <Button variant="outline" onClick={onRefresh}>
            使用最新数据
          </Button>
          <Button variant="ghost" onClick={onCancel}>
            取消
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
