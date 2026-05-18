/**
 * QuickFilterBar——快速筛选组件组
 * @author AaronZZH & Kiro
 *
 * 根据字段类型渲染对应筛选 UI：
 * - select → 下拉选择
 * - text/email → 文本输入（回车确认）
 * - date → 日期输入
 * - number → 数字输入
 */

"use client"

import { useCallback } from "react"
import type { DataFieldDef, EntityDef } from "../types"
import type { FilterCondition } from "./FilterBuilder"
import type { ViewSettings } from "./ViewSettingsSheet"

interface QuickFilterBarProps {
  entity: EntityDef
  filters: FilterCondition[]
  onChange: (filters: FilterCondition[]) => void
  viewSettings?: ViewSettings
}

export function QuickFilterBar({ entity, filters, onChange, viewSettings }: QuickFilterBarProps) {
  const fields = getActiveQuickFilterFields(entity, viewSettings)
  if (!fields.length) return null

  return (
    <div className="flex flex-wrap items-center gap-2">
      {fields.map((field) => (
        <QuickFilterField key={field.name} field={field} filters={filters} onChange={onChange} />
      ))}
    </div>
  )
}

/** 根据字段类型渲染筛选组件 */
function QuickFilterField({
  field,
  filters,
  onChange
}: {
  field: DataFieldDef
  filters: FilterCondition[]
  onChange: (filters: FilterCondition[]) => void
}) {
  const currentFilter = filters.find((f) => f.field === field.name)
  const currentValue = currentFilter?.value ?? ""

  const handleChange = useCallback(
    (value: string, operator = "eq") => {
      const without = filters.filter((f) => f.field !== field.name)
      if (value) {
        onChange([...without, { field: field.name, operator, value }])
      } else {
        onChange(without)
      }
    },
    [field.name, filters, onChange]
  )

  const baseClass =
    "h-9 rounded-md border bg-background px-3 text-sm text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring"

  // select 字段 → 下拉
  if (field.type === "select" && "options" in field) {
    const options = (field as unknown as { options: { value: string; label: string }[] }).options
    return (
      <select
        className={baseClass}
        value={currentValue}
        onChange={(e) => handleChange(e.target.value)}
      >
        <option value="">{field.label ?? field.name}</option>
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
    )
  }

  // date 字段 → 日期输入
  if (field.type === "date") {
    return (
      <input
        type="date"
        className={baseClass}
        title={field.label ?? field.name}
        value={currentValue}
        onChange={(e) => handleChange(e.target.value, "eq")}
      />
    )
  }

  // number 字段 → 数字输入
  if (field.type === "number") {
    return (
      <input
        type="number"
        className={`${baseClass} w-32`}
        placeholder={field.label ?? field.name}
        value={currentValue}
        onChange={(e) => handleChange(e.target.value, "eq")}
      />
    )
  }

  // text/email 等 → 文本输入
  return (
    <input
      type="text"
      className={`${baseClass} w-36`}
      placeholder={field.label ?? field.name}
      value={currentValue}
      onChange={(e) => handleChange(e.target.value, "contains")}
    />
  )
}

/** 获取当前激活的快速筛选字段 */
function getActiveQuickFilterFields(
  entity: EntityDef,
  viewSettings?: ViewSettings
): DataFieldDef[] {
  const allFields = entity.fields.filter((f): f is DataFieldDef => "name" in f)

  // 用户在视图设置里配置的字段优先
  if (viewSettings?.quickFilterFields?.length) {
    return viewSettings.quickFilterFields
      .map((name) => allFields.find((f) => f.name === name))
      .filter((f): f is DataFieldDef => !!f)
  }

  // 其次用 EntityDef 的 quickFilters 配置（去重字段）
  if (entity.listView.quickFilters?.length) {
    const fieldNames = [...new Set(entity.listView.quickFilters.map((qf) => qf.field))]
    return fieldNames
      .map((name) => allFields.find((f) => f.name === name))
      .filter((f): f is DataFieldDef => !!f)
  }

  // 最后用 filterableFields
  return (entity.listView.filterableFields ?? [])
    .map((name) => allFields.find((f) => f.name === name))
    .filter((f): f is DataFieldDef => !!f)
}
