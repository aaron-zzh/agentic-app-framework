/**
 * 看板泳道——按字段分组展示，支持折叠/展开
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronDown, ChevronRight } from "lucide-react"
import { type ReactNode, useMemo, useState } from "react"

import type { FieldDef, SelectField } from "@/lib/types/entity/field"
import { cn } from "@/lib/utils/cn"

interface KanbanSwimlaneProps {
  data: Record<string, unknown>[]
  swimlaneField: string
  fields: FieldDef[]
  /** 渲染列内容的函数（接收该泳道的记录子集） */
  renderColumns: (records: Record<string, unknown>[]) => ReactNode
}

/** 看板泳道容器 */
export function KanbanSwimlane({
  data,
  swimlaneField,
  fields,
  renderColumns
}: KanbanSwimlaneProps) {
  // 获取泳道字段定义，用于显示标签
  const swimFieldDef = fields.find((f) => "name" in f && f.name === swimlaneField) as
    | SelectField
    | undefined

  // 按泳道字段分组
  const groups = useMemo(() => {
    const map = new Map<string, Record<string, unknown>[]>()
    for (const record of data) {
      const key = String(record[swimlaneField] ?? "未分组")
      if (!map.has(key)) map.set(key, [])
      map.get(key)?.push(record)
    }
    return map
  }, [data, swimlaneField])

  // 折叠状态
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())

  function toggleCollapse(key: string) {
    setCollapsed((prev) => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  /** 获取泳道标签 */
  function getLabel(value: string): string {
    if (swimFieldDef?.options) {
      const opt = swimFieldDef.options.find((o) => o.value === value)
      if (opt) return opt.label
    }
    return value
  }

  /** 获取泳道颜色 */
  function getColor(value: string): string | undefined {
    if (swimFieldDef?.options) {
      return swimFieldDef.options.find((o) => o.value === value)?.color
    }
    return undefined
  }

  return (
    <div className="flex flex-col gap-2 p-4">
      {Array.from(groups.entries()).map(([key, records]) => {
        const isCollapsed = collapsed.has(key)
        const color = getColor(key)

        return (
          <div key={key} className="rounded-lg border">
            {/* 泳道标题 */}
            <button
              type="button"
              onClick={() => toggleCollapse(key)}
              className={cn(
                "flex w-full items-center gap-2 px-3 py-2 text-left",
                "transition-colors hover:bg-muted/50"
              )}
            >
              {isCollapsed ? (
                <ChevronRight className="size-4 text-muted-foreground" />
              ) : (
                <ChevronDown className="size-4 text-muted-foreground" />
              )}
              {color && (
                <span className="size-2.5 rounded-full" style={{ backgroundColor: color }} />
              )}
              <span className="font-medium text-sm">{getLabel(key)}</span>
              <span className="text-muted-foreground text-xs">({records.length})</span>
            </button>

            {/* 泳道内容 */}
            {!isCollapsed && (
              <div className="flex gap-4 overflow-x-auto px-3 pb-3">{renderColumns(records)}</div>
            )}
          </div>
        )
      })}
    </div>
  )
}
