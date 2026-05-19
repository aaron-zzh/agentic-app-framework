/**
 * MobileKanban——手机端看板视图（单列滚动）
 * @author AaronZZH & Kiro
 *
 * 看板在 <768px 时切换为单列模式：
 * - 顶部 Tab 切换状态列
 * - 当前列的卡片纵向滚动
 */

"use client"

import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { ScrollArea } from "@/components/ui/scroll-area"
import { cn } from "@/lib/utils/cn"
import type { EntityDef, SelectField, SelectOption } from "../../types"

interface MobileKanbanProps {
  entity: EntityDef
  data: Record<string, unknown>[]
  onRecordClick?: (id: string) => void
  onStatusChange?: (recordId: string, newStatus: string) => void
}

/** 手机端看板（单列滚动） */
export function MobileKanban({ entity, data, onRecordClick, onStatusChange }: MobileKanbanProps) {
  const { kanbanView, fields } = entity
  const statusField = kanbanView?.statusField ?? ""
  const cardTitle = kanbanView?.cardTitle ?? ""
  const cardDescription = kanbanView?.cardDescription

  // 获取状态选项
  const statusFieldDef = fields.find((f) => "name" in f && f.name === statusField) as
    | SelectField
    | undefined
  const options: SelectOption[] = statusFieldDef?.options ?? []

  const [activeStatus, setActiveStatus] = useState(options[0]?.value ?? "")

  // 当前状态列的记录
  const filteredData = data.filter((r) => r[statusField] === activeStatus)

  return (
    <div className="flex h-full flex-col">
      {/* 状态 Tab 切换 */}
      <div className="flex gap-1 overflow-x-auto border-b px-3 py-2">
        {options.map((opt) => {
          const count = data.filter((r) => r[statusField] === opt.value).length
          return (
            <button
              key={opt.value}
              type="button"
              onClick={() => setActiveStatus(opt.value)}
              className={cn(
                "shrink-0 rounded-full px-3 py-1 text-xs transition-colors",
                activeStatus === opt.value
                  ? "bg-primary text-primary-foreground"
                  : "bg-muted text-muted-foreground"
              )}
            >
              {opt.label} ({count})
            </button>
          )
        })}
      </div>

      {/* 卡片列表 */}
      <ScrollArea className="flex-1">
        <div className="flex flex-col gap-2 p-3">
          {filteredData.map((record) => {
            const id = String(record.id ?? "")
            return (
              <Card
                key={id}
                className="cursor-pointer active:bg-accent"
                onClick={() => onRecordClick?.(id)}
              >
                <CardContent className="p-3">
                  <p className="truncate font-medium text-sm">
                    {String(record[cardTitle] ?? "")}
                  </p>
                  {cardDescription && record[cardDescription] && (
                    <p className="mt-1 line-clamp-2 text-muted-foreground text-xs">
                      {String(record[cardDescription])}
                    </p>
                  )}
                </CardContent>
              </Card>
            )
          })}
          {filteredData.length === 0 && (
            <p className="py-8 text-center text-muted-foreground text-sm">暂无数据</p>
          )}
        </div>
      </ScrollArea>
    </div>
  )
}
