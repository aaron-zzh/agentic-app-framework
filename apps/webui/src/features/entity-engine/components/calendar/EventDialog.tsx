/**
 * 日历事件创建/编辑弹窗
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback } from "react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useEntityDelete, useEntityMutation } from "@/lib/queries/use-entity-mutations"

import type { EntityDef } from "../../types"

interface EventDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  entity: EntityDef
  /** 编辑时传入已有记录 */
  record: Record<string, unknown> | null
  /** 创建时传入选中的时间范围 */
  defaultRange: { start: string; end: string } | null
}

/** 事件创建/编辑弹窗 */
export function EventDialog({
  open,
  onOpenChange,
  entity,
  record,
  defaultRange
}: EventDialogProps) {
  const config = entity.calendarView
  const isEdit = !!record
  const recordId = record ? String(record.id) : undefined

  const mutation = useEntityMutation(entity, recordId)
  const deleteMutation = useEntityDelete(entity)

  const handleSubmit = useCallback(
    (e: React.FormEvent<HTMLFormElement>) => {
      e.preventDefault()
      if (!config) return

      const formData = new FormData(e.currentTarget)
      const data: Record<string, unknown> = {}

      data[config.titleField] = formData.get("title")
      data[config.startField] = formData.get("start")
      if (config.endField) {
        data[config.endField] = formData.get("end")
      }
      if (config.rruleField) {
        const rrule = formData.get("rrule")
        if (rrule) data[config.rruleField] = rrule
      }

      mutation.mutate(data, {
        onSuccess: () => onOpenChange(false)
      })
    },
    [config, mutation, onOpenChange]
  )

  const handleDelete = useCallback(() => {
    if (!recordId) return
    deleteMutation.mutate([recordId], {
      onSuccess: () => onOpenChange(false)
    })
  }, [recordId, deleteMutation, onOpenChange])

  if (!config) return null

  const defaultTitle = record ? String(record[config.titleField] ?? "") : ""
  const defaultStart = record
    ? String(record[config.startField] ?? "")
    : (defaultRange?.start ?? "")
  const defaultEnd = record
    ? config.endField
      ? String(record[config.endField] ?? "")
      : ""
    : (defaultRange?.end ?? "")
  const defaultRrule = record && config.rruleField ? String(record[config.rruleField] ?? "") : ""

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEdit ? "编辑事件" : "创建事件"}</DialogTitle>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="event-title">标题</Label>
            <Input id="event-title" name="title" defaultValue={defaultTitle} required autoFocus />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="event-start">开始时间</Label>
              <Input
                id="event-start"
                name="start"
                type="datetime-local"
                defaultValue={formatDateTimeLocal(defaultStart)}
                required
              />
            </div>
            {config.endField && (
              <div className="space-y-2">
                <Label htmlFor="event-end">结束时间</Label>
                <Input
                  id="event-end"
                  name="end"
                  type="datetime-local"
                  defaultValue={formatDateTimeLocal(defaultEnd)}
                />
              </div>
            )}
          </div>

          {config.rruleField && (
            <div className="space-y-2">
              <Label htmlFor="event-rrule">重复规则（RRULE）</Label>
              <Input
                id="event-rrule"
                name="rrule"
                placeholder="如：FREQ=WEEKLY;BYDAY=MO,WE,FR"
                defaultValue={defaultRrule}
              />
            </div>
          )}

          <DialogFooter className="gap-2">
            {isEdit && (
              <Button
                type="button"
                variant="destructive"
                onClick={handleDelete}
                disabled={deleteMutation.isPending}
              >
                删除
              </Button>
            )}
            <Button type="submit" disabled={mutation.isPending}>
              {isEdit ? "保存" : "创建"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/** 将 ISO 日期字符串转为 datetime-local 输入格式 */
function formatDateTimeLocal(dateStr: string): string {
  if (!dateStr) return ""
  // 去掉时区后缀，截取到分钟
  const d = new Date(dateStr)
  if (Number.isNaN(d.getTime())) return dateStr.slice(0, 16)
  const pad = (n: number) => String(n).padStart(2, "0")
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
