/**
 * 筛选构建器——根据字段类型推断操作符，多条件组合筛选
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useState } from "react"

import type { DataFieldDef, EntityDef } from "@/features/entity-engine/types"

/** 筛选条件 */
export interface FilterCondition {
  field: string
  operator: string
  value: string
}

/** 字段类型 → 可用操作符 */
const operatorsByType: Record<string, { value: string; label: string }[]> = {
  text: [
    { value: "contains", label: "包含" },
    { value: "eq", label: "等于" },
    { value: "startsWith", label: "开头是" },
    { value: "isEmpty", label: "为空" }
  ],
  number: [
    { value: "eq", label: "等于" },
    { value: "gt", label: "大于" },
    { value: "lt", label: "小于" },
    { value: "between", label: "介于" }
  ],
  date: [
    { value: "eq", label: "等于" },
    { value: "gt", label: "晚于" },
    { value: "lt", label: "早于" },
    { value: "between", label: "介于" }
  ],
  select: [
    { value: "eq", label: "等于" },
    { value: "in", label: "属于" },
    { value: "neq", label: "不等于" }
  ]
}

function getOperators(fieldType: string) {
  return operatorsByType[fieldType] ?? operatorsByType.text
}

interface FilterBuilderProps {
  entity: EntityDef
  filters: FilterCondition[]
  onChange: (filters: FilterCondition[]) => void
}

/** 筛选构建器 */
export function FilterBuilder({ entity, filters, onChange }: FilterBuilderProps) {
  const [open, setOpen] = useState(false)

  const filterableFields = entity.fields.filter(
    (f): f is DataFieldDef =>
      "name" in f && f.type !== "group" && f.type !== "tabs" && f.type !== "row"
  )

  const addFilter = useCallback(() => {
    const first = filterableFields[0]
    if (!first) return
    onChange([...filters, { field: first.name, operator: "contains", value: "" }])
    setOpen(true)
  }, [filters, filterableFields, onChange])

  const updateFilter = useCallback(
    (index: number, patch: Partial<FilterCondition>) => {
      const next = filters.map((f, i) => (i === index ? { ...f, ...patch } : f))
      onChange(next)
    },
    [filters, onChange]
  )

  const removeFilter = useCallback(
    (index: number) => {
      onChange(filters.filter((_, i) => i !== index))
    },
    [filters, onChange]
  )

  return (
    <div className="space-y-2">
      {filters.length > 0 && open && (
        <div className="space-y-2 rounded-md border p-3">
          {filters.map((filter, i) => {
            const fieldDef = filterableFields.find((f) => f.name === filter.field)
            const operators = getOperators(fieldDef?.type ?? "text")
            return (
              // biome-ignore lint/suspicious/noArrayIndexKey: 筛选条件列表
              <div key={i} className="flex items-center gap-2">
                <select
                  className="h-8 rounded border px-2 text-sm"
                  value={filter.field}
                  onChange={(e) =>
                    updateFilter(i, { field: e.target.value, operator: "contains", value: "" })
                  }
                >
                  {filterableFields.map((f) => (
                    <option key={f.name} value={f.name}>
                      {f.label ?? f.name}
                    </option>
                  ))}
                </select>
                <select
                  className="h-8 rounded border px-2 text-sm"
                  value={filter.operator}
                  onChange={(e) => updateFilter(i, { operator: e.target.value })}
                >
                  {operators.map((op) => (
                    <option key={op.value} value={op.value}>
                      {op.label}
                    </option>
                  ))}
                </select>
                {filter.operator !== "isEmpty" && (
                  <input
                    className="h-8 flex-1 rounded border px-2 text-sm"
                    value={filter.value}
                    onChange={(e) => updateFilter(i, { value: e.target.value })}
                    placeholder="值"
                  />
                )}
                <button
                  type="button"
                  className="h-8 px-2 text-muted-foreground text-sm hover:text-destructive"
                  onClick={() => removeFilter(i)}
                >
                  ✕
                </button>
              </div>
            )
          })}
        </div>
      )}
      <button type="button" className="text-primary text-sm hover:underline" onClick={addFilter}>
        + 添加筛选
      </button>
    </div>
  )
}
