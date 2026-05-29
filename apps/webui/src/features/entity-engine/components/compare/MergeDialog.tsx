/**
 * 合并确认弹窗——展示合并预览并确认执行
 * @author AaronZZH & Kiro
 */

"use client"

import { GitMerge } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"

import type { FieldDef } from "../../types"

export interface MergeSelection {
  [fieldName: string]: "left" | "right"
}

interface MergeDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  fields: FieldDef[]
  leftRecord: Record<string, unknown>
  rightRecord: Record<string, unknown>
  selections: MergeSelection
  /** 执行合并 */
  onConfirm: () => void
  loading?: boolean
}

/** 合并确认弹窗 */
export function MergeDialog({
  open,
  onOpenChange,
  fields,
  leftRecord,
  rightRecord,
  selections,
  onConfirm,
  loading = false
}: MergeDialogProps) {
  /** 根据选择构建合并后的数据预览 */
  const mergedData = fields
    .filter((f) => "name" in f)
    .map((f) => {
      const name = (f as { name: string }).name
      const side = selections[name] ?? "left"
      const value = side === "left" ? leftRecord[name] : rightRecord[name]
      return { name, label: ("label" in f ? f.label : undefined) ?? name, value }
    })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <GitMerge className="size-4" />
            确认合并
          </DialogTitle>
          <DialogDescription>
            合并后将保留左侧记录，右侧记录将被软删除。请确认合并结果。
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-64 overflow-y-auto rounded-md border">
          {mergedData.map((item) => (
            <div
              key={item.name}
              className="flex items-center justify-between border-b px-3 py-2 text-sm last:border-b-0"
            >
              <span className="text-muted-foreground">{item.label}</span>
              <span className="max-w-[200px] truncate font-medium">
                {item.value === null || item.value === undefined ? "—" : String(item.value)}
              </span>
            </div>
          ))}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
            取消
          </Button>
          <Button onClick={onConfirm} disabled={loading}>
            {loading ? "合并中..." : "确认合并"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
