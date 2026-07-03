/**
 * FilterChips——已选筛选条件展示 + 清除
 * @author AaronZZH & Kiro
 *
 * 参考 next-ts UserTableFiltersResult
 */

"use client"

import { Trash2, X } from "lucide-react"
import type { DataFieldDef, EntityDef } from "../../../types"
import type { FilterCondition } from "./FilterBuilder"

interface FilterChipsProps {
  entity: EntityDef
  filters: FilterCondition[]
  onChange: (filters: FilterCondition[]) => void
}

export function FilterChips({ entity, filters, onChange }: FilterChipsProps) {
  // 排除内部字段（tab 字段、全文搜索）
  const tabField = entity.listView.tabs?.field
  const visibleFilters = filters.filter((f) => f.field !== "__search" && f.field !== tabField)

  if (!visibleFilters.length) return null

  const allFields = entity.fields.filter((f): f is DataFieldDef => "name" in f)

  const getLabel = (f: FilterCondition) => {
    const fieldDef = allFields.find((fd) => fd.name === f.field)
    const fieldLabel = fieldDef?.label ?? f.field
    const valueLabel =
      fieldDef?.type === "select" && "options" in fieldDef
        ? ((fieldDef as unknown as { options?: { value: string; label: string }[] }).options?.find(
            (o) => o.value === f.value
          )?.label ?? f.value)
        : f.value
    return { fieldLabel, valueLabel }
  }

  return (
    <div className="flex flex-wrap items-center gap-2 border-t px-4 py-2">
      {visibleFilters.map((f) => {
        const { fieldLabel, valueLabel } = getLabel(f)
        return (
          <span
            key={`${f.field}-${f.operator}-${String(f.value)}`}
            className="inline-flex items-center gap-1 rounded-full border bg-muted/50 px-2.5 py-1 text-xs"
          >
            <span className="text-muted-foreground">{fieldLabel}:</span>
            <span className="font-medium">{valueLabel}</span>
            <button
              type="button"
              className="ml-0.5 rounded-full text-muted-foreground hover:text-foreground"
              onClick={() =>
                onChange(
                  filters.filter(
                    (item) =>
                      !(
                        item.field === f.field &&
                        item.operator === f.operator &&
                        item.value === f.value
                      )
                  )
                )
              }
            >
              <X className="size-3" />
            </button>
          </span>
        )
      })}

      <button
        type="button"
        className="inline-flex items-center gap-1 text-destructive text-xs hover:underline"
        onClick={() =>
          onChange(filters.filter((f) => f.field === tabField || f.field === "__search"))
        }
      >
        <Trash2 className="size-3" />
        清除
      </button>
    </div>
  )
}
