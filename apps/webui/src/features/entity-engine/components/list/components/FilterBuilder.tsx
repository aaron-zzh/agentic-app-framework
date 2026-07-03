/**
 * 筛选构建器——根据字段类型推断操作符，多条件组合筛选
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useState } from "react"
import { useDebouncedCallback } from "use-debounce"

import type { DataFieldDef, EntityDef, FilterCondition } from "@/lib/types/entity"

export type { FilterCondition } from "@/lib/types/entity"

/** 无需输入值的操作符——UI 隐藏值输入框 */
const VALUELESS_OPERATORS = new Set(["isEmpty", "isNotEmpty", "isTrue", "isFalse"])

/** 字段类型 → 可用操作符 */
const operatorsByType: Record<string, { value: string; label: string }[]> = {
  text: [
    { value: "contains", label: "包含" },
    { value: "eq", label: "等于" },
    { value: "startsWith", label: "开头是" },
    { value: "isEmpty", label: "为空" },
    { value: "isNotEmpty", label: "不为空" }
  ],
  number: [
    { value: "eq", label: "等于" },
    { value: "gt", label: "大于" },
    { value: "gte", label: "大于等于" },
    { value: "lt", label: "小于" },
    { value: "lte", label: "小于等于" },
    { value: "between", label: "介于" }
  ],
  date: [
    { value: "eq", label: "等于" },
    { value: "gt", label: "晚于" },
    { value: "lt", label: "早于" },
    { value: "between", label: "介于" },
    { value: "thisWeek", label: "本周" },
    { value: "thisMonth", label: "本月" }
  ],
  select: [
    { value: "eq", label: "等于" },
    { value: "in", label: "属于" },
    { value: "notIn", label: "不属于" }
  ],
  relationship: [
    { value: "eq", label: "等于" },
    { value: "in", label: "属于" },
    { value: "isEmpty", label: "为空" }
  ],
  checkbox: [
    { value: "isTrue", label: "是" },
    { value: "isFalse", label: "否" }
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

  const filterableFields = entity.fields.filter((f): f is DataFieldDef => "name" in f)

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
                {!VALUELESS_OPERATORS.has(filter.operator) && (
                  <FilterValueInput
                    key={`${filter.field}-${filter.operator}`}
                    value={filter.value}
                    onChange={(value) => updateFilter(i, { value })}
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

/** 筛选值输入框——本地即时展示，300ms 防抖后才向上游触发查询，避免逐字符输入连续发起请求 */
function FilterValueInput({
  value,
  onChange
}: {
  value: string
  onChange: (value: string) => void
}) {
  const [localValue, setLocalValue] = useState(value)
  const debouncedOnChange = useDebouncedCallback(onChange, 300)

  return (
    <input
      className="h-8 flex-1 rounded border px-2 text-sm"
      value={localValue}
      onChange={(e) => {
        setLocalValue(e.target.value)
        debouncedOnChange(e.target.value)
      }}
      placeholder="值"
    />
  )
}
