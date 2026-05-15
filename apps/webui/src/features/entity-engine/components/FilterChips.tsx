/**
 * FilterChips——活跃筛选条件标签栏（单个删除 + 清除全部）
 * @author AaronZZH & Kiro
 */

"use client"

import type { DataFieldDef, EntityDef } from "@/features/entity-engine/types"
import type { FilterCondition } from "./FilterBuilder"

/** 操作符显示名 */
const operatorLabels: Record<string, string> = {
  contains: "包含",
  eq: "=",
  neq: "≠",
  gt: ">",
  lt: "<",
  between: "介于",
  startsWith: "开头是",
  isEmpty: "为空",
  in: "属于",
}

interface FilterChipsProps {
  entity: EntityDef
  filters: FilterCondition[]
  onRemove: (index: number) => void
  onClear: () => void
}

/** 活跃筛选标签栏 */
export function FilterChips({ entity, filters, onRemove, onClear }: FilterChipsProps) {
  if (filters.length === 0) return null

  return (
    <div className="flex flex-wrap items-center gap-1.5 px-4 py-2">
      {filters.map((filter, i) => {
        const fieldDef = entity.fields.find(
          (f) => "name" in f && (f as DataFieldDef).name === filter.field
        ) as DataFieldDef | undefined
        const label = fieldDef?.label ?? filter.field
        const op = operatorLabels[filter.operator] ?? filter.operator

        return (
          <span
            // biome-ignore lint/suspicious/noArrayIndexKey: 筛选条件列表
            key={i}
            className="inline-flex items-center gap-1 rounded-md border bg-muted/50 px-2 py-0.5 text-xs"
          >
            <span className="font-medium">{label}</span>
            <span className="text-muted-foreground">{op}</span>
            {filter.operator !== "isEmpty" && (
              <span>{filter.value}</span>
            )}
            <button
              type="button"
              className="ml-0.5 text-muted-foreground hover:text-destructive"
              onClick={() => onRemove(i)}
            >
              ✕
            </button>
          </span>
        )
      })}
      <button
        type="button"
        className="text-xs text-muted-foreground hover:text-destructive"
        onClick={onClear}
      >
        清除全部
      </button>
    </div>
  )
}
