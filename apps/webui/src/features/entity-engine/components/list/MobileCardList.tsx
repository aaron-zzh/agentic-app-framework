/**
 * MobileCardList——手机端卡片列表视图
 * @author AaronZZH & Kiro
 *
 * 列表视图在 <768px 时切换为卡片模式，展示 2-3 个关键字段。
 * 支持长按批量选择。
 */

"use client"

import { Check } from "lucide-react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { useLongPress } from "@/lib/hooks/use-long-press"
import { cn } from "@/lib/utils/cn"
import type { DataFieldDef, EntityDef, SelectField } from "../../types"

interface MobileCardListProps {
  entity: EntityDef
  data: Record<string, unknown>[]
  onRecordClick?: (id: string) => void
}

/** 手机端卡片列表 */
export function MobileCardList({ entity, data, onRecordClick }: MobileCardListProps) {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [selectionMode, setSelectionMode] = useState(false)

  // 取前 3 个列表列作为卡片展示字段
  const displayColumns = entity.listView.columns.slice(0, 3).map((col) =>
    typeof col === "string" ? col : col.name
  )

  const titleField = displayColumns[0]
  const subtitleFields = displayColumns.slice(1)

  function toggleSelect(id: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      if (next.size === 0) setSelectionMode(false)
      return next
    })
  }

  function handleCardClick(id: string) {
    if (selectionMode) {
      toggleSelect(id)
    } else {
      onRecordClick?.(id)
    }
  }

  return (
    <div className="flex flex-col gap-2 p-3">
      {/* 批量选择工具栏 */}
      {selectionMode && (
        <div className="flex items-center justify-between rounded-lg bg-primary/10 px-3 py-2">
          <span className="text-sm">已选 {selectedIds.size} 项</span>
          <button
            type="button"
            className="text-primary text-sm"
            onClick={() => { setSelectionMode(false); setSelectedIds(new Set()) }}
          >
            取消
          </button>
        </div>
      )}

      {data.map((record) => {
        const id = String(record.id ?? "")
        return (
          <MobileCard
            key={id}
            record={record}
            entity={entity}
            titleField={titleField}
            subtitleFields={subtitleFields}
            selected={selectedIds.has(id)}
            selectionMode={selectionMode}
            onClick={() => handleCardClick(id)}
            onLongPress={() => { setSelectionMode(true); toggleSelect(id) }}
          />
        )
      })}
    </div>
  )
}

/** 单张卡片 */
function MobileCard({
  record,
  entity,
  titleField,
  subtitleFields,
  selected,
  selectionMode,
  onClick,
  onLongPress
}: {
  record: Record<string, unknown>
  entity: EntityDef
  titleField: string | undefined
  subtitleFields: string[]
  selected: boolean
  selectionMode: boolean
  onClick: () => void
  onLongPress: () => void
}) {
  const longPressProps = useLongPress(onLongPress)

  return (
    <Card
      className={cn("cursor-pointer transition-colors active:bg-accent", selected && "ring-2 ring-primary")}
    >
      <CardContent className="flex items-center gap-3 p-3" onClick={onClick} {...longPressProps}>
        {selectionMode && (
          <Checkbox checked={selected} className="shrink-0" />
        )}
        <div className="min-w-0 flex-1">
          {titleField && (
            <p className="truncate font-medium text-sm">
              {String(record[titleField] ?? "")}
            </p>
          )}
          <div className="mt-0.5 flex flex-wrap items-center gap-2">
            {subtitleFields.map((fieldName) => {
              const fieldDef = entity.fields.find(
                (f) => "name" in f && (f as DataFieldDef).name === fieldName
              ) as DataFieldDef | undefined
              const value = record[fieldName]
              if (value == null) return null

              // select 字段渲染为 Badge
              if (fieldDef?.type === "select") {
                const opt = (fieldDef as SelectField).options.find((o) => o.value === value)
                return (
                  <Badge key={fieldName} variant="secondary" className="text-xs">
                    {opt?.label ?? String(value)}
                  </Badge>
                )
              }

              return (
                <span key={fieldName} className="truncate text-muted-foreground text-xs">
                  {String(value)}
                </span>
              )
            })}
          </div>
        </div>
        {selected && !selectionMode && <Check className="size-4 text-primary" />}
      </CardContent>
    </Card>
  )
}
