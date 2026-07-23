/**
 * FilterBar——筛选组件组
 * @author AaronZZH & Kiro
 *
 * 根据字段类型渲染对应筛选 UI：
 * - select → 下拉选择
 * - text/email → 文本输入（回车确认）
 * - date → 日期输入
 * - number → 数字输入
 */

"use client"

import { type ReactNode, useCallback } from "react"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { buildDateRangeFilter, getDateRangeValues } from "../../../lib/date-range-filter"
import type { DataFieldDef, EntityDef } from "../../../types"
import type { FilterCondition } from "./FilterBuilder"
import type { ViewSettings } from "./ViewSettingsSheet"

interface FilterBarProps {
  entity: EntityDef
  filters: FilterCondition[]
  onChange: (filters: FilterCondition[]) => void
  viewSettings?: ViewSettings
  /** 由 Toolbar 提供的筛选栏尾部操作。 */
  trailingAction?: ReactNode
}

export function FilterBar({
  entity,
  filters,
  onChange,
  viewSettings,
  trailingAction
}: FilterBarProps) {
  const fields = getActiveFilterFields(entity, viewSettings)
  if (!fields.length && !trailingAction) return null

  return (
    <div className="flex flex-wrap items-center gap-2 pt-2">
      {fields.map((field) => (
        <FilterField key={field.name} field={field} filters={filters} onChange={onChange} />
      ))}
      {trailingAction}
    </div>
  )
}

/** 根据字段类型渲染筛选组件 */
function FilterField({
  field,
  filters,
  onChange
}: {
  field: DataFieldDef
  filters: FilterCondition[]
  onChange: (filters: FilterCondition[]) => void
}) {
  const currentFilter = filters.find((filter) => filter.field === field.name)
  const currentValue = currentFilter?.values[0] ?? ""

  const handleChange = useCallback(
    (value: string, operator = "eq") => {
      const without = filters.filter((filter) => filter.field !== field.name)
      if (value) {
        onChange([...without, { field: field.name, operator, values: [value] }])
      } else {
        onChange(without)
      }
    },
    [field.name, filters, onChange]
  )

  const baseClass = "h-9 text-sm w-36"

  // select 字段 → 下拉
  if (field.type === "select" && "options" in field) {
    const options =
      (field as unknown as { options?: { value: string; label: string }[] }).options ?? []
    const currentLabel = options.find((option) => option.value === currentValue)?.label
    return (
      <Select value={currentValue} onValueChange={(value) => handleChange(value ?? "")}>
        <SelectTrigger className="h-9 w-36 text-sm">
          <SelectValue placeholder={field.label ?? field.name}>
            {currentLabel ?? field.label ?? field.name}
          </SelectValue>
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="">{field.label ?? field.name}</SelectItem>
          {options.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    )
  }

  // date 字段 → 可单端提交的日期范围
  if (field.type === "date") {
    return <DateRangeFilter field={field} filters={filters} onChange={onChange} />
  }

  // number 字段 → 数字输入
  if (field.type === "number") {
    return (
      <Input
        type="number"
        className={baseClass}
        placeholder={field.label ?? field.name}
        value={currentValue}
        onChange={(event) => handleChange(event.target.value, "eq")}
      />
    )
  }

  // text/email 等 → 文本输入
  return (
    <Input
      type="text"
      className={baseClass}
      placeholder={field.label ?? field.name}
      value={currentValue}
      onChange={(event) => handleChange(event.target.value, "contains")}
    />
  )
}

/** 日期快捷筛选：未填一端时使用单边比较，避免构造不完整的 between 条件。 */
function DateRangeFilter({
  field,
  filters,
  onChange
}: {
  field: Extract<DataFieldDef, { type: "date" }>
  filters: FilterCondition[]
  onChange: (filters: FilterCondition[]) => void
}) {
  const currentFilter = filters.find((filter) => filter.field === field.name)
  const { start: startValue, end: endValue } = getDateRangeValues(currentFilter)
  const fieldLabel = field.label ?? field.name

  const handleRangeChange = useCallback(
    (start: string, end: string) => {
      const without = filters.filter((filter) => filter.field !== field.name)
      const condition = buildDateRangeFilter(field.name, start, end)
      onChange(condition ? [...without, condition] : without)
    },
    [field.name, filters, onChange]
  )

  return (
    <div className="flex items-center gap-1">
      <Input
        aria-label={`${fieldLabel}起始日期`}
        type="date"
        className="h-9 w-36 text-sm"
        value={startValue}
        onChange={(event) => handleRangeChange(event.target.value, endValue)}
      />
      <span className="text-muted-foreground text-xs">至</span>
      <Input
        aria-label={`${fieldLabel}结束日期`}
        type="date"
        className="h-9 w-36 text-sm"
        value={endValue}
        onChange={(event) => handleRangeChange(startValue, event.target.value)}
      />
    </div>
  )
}

/** 获取当前激活的筛选字段。 */
function getActiveFilterFields(entity: EntityDef, viewSettings?: ViewSettings): DataFieldDef[] {
  const allFields = entity.fields.filter((field): field is DataFieldDef => "name" in field)
  const fieldNames =
    viewSettings?.filterFields !== undefined
      ? viewSettings.filterFields
      : (entity.listView.filterFields ?? [])

  return fieldNames
    .map((name) => allFields.find((field) => field.name === name))
    .filter((field): field is DataFieldDef => !!field)
}
