/**
 * SearchBar——统一搜索栏（tag 内嵌 + 智能字段建议 + 动态值表单）
 * @author AaronZZH & Kiro
 *
 * 交互流程：
 * 1. 输入文字 → 弹出建议面板（全文搜索 + 可选字段列表）
 * 2. 选择字段 → 根据字段类型弹出值输入（text→直接输入，date→日期范围，select→选项列表）
 * 3. 确认值 → 生成 tag 内嵌在搜索框中
 * 4. 点击 tag ✕ → 删除该条件
 */

"use client"

import { useCallback, useId, useRef, useState } from "react"

import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import type { DataFieldDef, EntityDef } from "@/features/entity-engine/types"
import type { QuickFilter } from "@/lib/types/entity"
import { buildDateRangeFilter } from "../../../lib/date-range-filter"
import type { FilterCondition } from "./FilterBuilder"

type InputPhase = "idle" | "selectField" | "inputValue"

const OPERATOR_LABELS: Record<string, string> = {
  eq: "等于",
  in: "属于",
  notIn: "不属于",
  contains: "包含",
  startsWith: "开头是",
  gt: "大于",
  gte: "大于等于",
  lt: "小于",
  lte: "小于等于",
  between: "介于",
  isNull: "为 NULL",
  isNotNull: "非 NULL",
  isEmpty: "为空",
  isNotEmpty: "不为空",
  isTrue: "为真",
  isFalse: "为假"
}

interface SearchBarProps {
  entity: EntityDef
  filters: FilterCondition[]
  onChange: (filters: FilterCondition[]) => void
}

/** 统一搜索栏 */
export function SearchBar({ entity, filters, onChange }: SearchBarProps) {
  const [query, setQuery] = useState("")
  const [phase, setPhase] = useState<InputPhase>("idle")
  const [selectedField, setSelectedField] = useState<DataFieldDef | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const allFields = entity.fields.filter((f): f is DataFieldDef => "name" in f)

  // 可筛选字段：配置了 filterableFields 则只显示这些，否则显示全部
  const filterableFields = entity.listView.filterableFields?.length
    ? allFields.filter((f) => entity.listView.filterableFields?.includes(f.name))
    : allFields

  const filteredFields = query
    ? filterableFields.filter((f) =>
        (f.label ?? f.name).toLowerCase().includes(query.toLowerCase())
      )
    : filterableFields
  const quickFilters = entity.listView.quickFilters ?? []
  const activeQuickFilters = quickFilters.filter((preset) =>
    preset.conditions.every((condition) =>
      filters.some((filter) => isSameFilterCondition(filter, condition))
    )
  )
  const visibleFilters = filters
    .map((filter, index) => ({ filter, index }))
    .filter(
      ({ filter }) =>
        !activeQuickFilters.some((preset) =>
          preset.conditions.some((condition) => isSameFilterCondition(filter, condition))
        )
    )

  // 选择字段
  const handleSelectField = useCallback((field: DataFieldDef) => {
    setSelectedField(field)
    setPhase("inputValue")
    setQuery("")
    setTimeout(() => inputRef.current?.focus(), 0)
  }, [])

  // 确认值（文本直接回车）
  const handleConfirmValue = useCallback(
    (value: string) => {
      if (!selectedField || !value.trim()) return
      const op = selectedField.type === "select" ? "eq" : "contains"
      onChange([...filters, { field: selectedField.name, operator: op, values: [value.trim()] }])
      setSelectedField(null)
      setPhase("idle")
      setQuery("")
    },
    [selectedField, filters, onChange]
  )

  // 确认日期范围：单端转为比较操作符，双端才使用 between。
  const handleConfirmDateRange = useCallback(
    (start: string, end: string) => {
      if (!selectedField || selectedField.type !== "date") return

      const condition = buildDateRangeFilter(selectedField.name, start, end)
      if (!condition) return

      onChange([...filters, condition])
      setSelectedField(null)
      setPhase("idle")
      setQuery("")
    },
    [selectedField, filters, onChange]
  )

  // 全文搜索回车
  const handleSearchSubmit = useCallback(() => {
    if (!query.trim()) return
    // 搜索作为特殊筛选条件
    onChange([...filters, { field: "__search", operator: "contains", values: [query.trim()] }])
    setQuery("")
    setPhase("idle")
  }, [query, filters, onChange])

  const handleQuickFilterSelect = useCallback(
    (preset: QuickFilter) => {
      const enabled = preset.conditions.every((condition) =>
        filters.some((filter) => isSameFilterCondition(filter, condition))
      )
      if (enabled) {
        onChange(
          filters.filter(
            (filter) =>
              !preset.conditions.some((condition) => isSameFilterCondition(filter, condition))
          )
        )
      } else {
        const fields = new Set(preset.conditions.map((condition) => condition.field))
        onChange([...filters.filter((filter) => !fields.has(filter.field)), ...preset.conditions])
      }
      setQuery("")
      setPhase("idle")
    },
    [filters, onChange]
  )

  // 删除单个 tag
  const handleRemove = useCallback(
    (index: number) => {
      onChange(filters.filter((_, i) => i !== index))
    },
    [filters, onChange]
  )

  // 键盘事件
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Enter") {
        e.preventDefault()
        if (phase === "inputValue") {
          handleConfirmValue(query)
        } else {
          handleSearchSubmit()
        }
      } else if (e.key === "Escape") {
        setPhase("idle")
        setSelectedField(null)
        setQuery("")
      } else if (e.key === "Backspace" && !query && filters.length > 0 && phase === "idle") {
        // 空输入时退格删除最后一个 tag
        onChange(filters.slice(0, -1))
      }
    },
    [phase, query, filters, onChange, handleConfirmValue, handleSearchSubmit]
  )

  // select 类型字段的选项
  const selectOptions =
    selectedField?.type === "select" && "options" in selectedField
      ? ((selectedField as unknown as { options?: { value: string; label: string }[] }).options ??
        [])
      : null

  const closeSuggestions = useCallback(() => {
    setPhase("idle")
    setSelectedField(null)
    setQuery("")
  }, [])
  const suggestionOpen =
    phase === "selectField" ||
    (phase === "inputValue" && (selectOptions !== null || selectedField?.type === "date"))

  return (
    <Popover
      open={suggestionOpen}
      onOpenChange={(open, eventDetails) => {
        if (!open && eventDetails.reason !== "trigger-press") closeSuggestions()
      }}
    >
      <PopoverTrigger nativeButton={false} render={<div className="relative flex-1" />}>
        {/* 搜索框容器 */}
        <div className="flex min-h-[36px] flex-wrap items-center gap-1 rounded-md px-2 py-1 focus-within:bg-muted/50">
          <span className="text-muted-foreground">🔍</span>

          {/* 已应用预设筛选 tag */}
          {activeQuickFilters.map((preset) => (
            <span
              key={`${preset.label}-${preset.conditions
                .map(
                  (condition) =>
                    `${condition.field}:${condition.operator}:${condition.values.join("|")}`
                )
                .join(",")}`}
              className="inline-flex items-center gap-0.5 rounded bg-primary/10 px-1.5 py-0.5 text-primary text-xs"
            >
              <span className="font-medium">{preset.label}</span>
              <button
                type="button"
                aria-label={`移除${preset.label}筛选`}
                className="ml-0.5 hover:text-destructive"
                onClick={() => handleQuickFilterSelect(preset)}
              >
                ✕
              </button>
            </span>
          ))}

          {/* 已选单条件 tag */}
          {visibleFilters.map(({ filter, index }) => {
            const fieldDef = allFields.find((field) => field.name === filter.field)
            const label = filter.field === "__search" ? "关键词" : (fieldDef?.label ?? filter.field)
            // select 字段显示 option label 而非原始 value
            const valueLabel = filter.values
              .map((value) =>
                fieldDef?.type === "select" && "options" in fieldDef
                  ? ((
                      fieldDef as unknown as { options?: { value: string; label: string }[] }
                    ).options?.find((option) => option.value === value)?.label ?? value)
                  : value
              )
              .join(filter.operator === "between" ? " 至 " : "、")
            const operatorLabel = OPERATOR_LABELS[filter.operator] ?? filter.operator
            const chipLabel = `${label} ${operatorLabel}${valueLabel ? ` ${valueLabel}` : ""}`
            return (
              <span
                key={`${filter.field}-${filter.operator}-${filter.values.join("|")}`}
                className="inline-flex items-center gap-0.5 rounded bg-muted px-1.5 py-0.5 text-xs"
              >
                <span className="font-medium">{chipLabel}</span>
                <button
                  type="button"
                  aria-label={`移除${label}筛选`}
                  className="ml-0.5 hover:text-destructive"
                  onClick={() => handleRemove(index)}
                >
                  ✕
                </button>
              </span>
            )
          })}

          {/* 当前输入状态提示 */}
          {phase === "inputValue" && selectedField && (
            <span className="text-muted-foreground text-xs">
              {selectedField.label ?? selectedField.name}:
            </span>
          )}

          {/* 输入框 */}
          <input
            ref={inputRef}
            className="min-w-[120px] flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
            placeholder={phase === "inputValue" ? "输入值后回车确认" : `搜索${entity.label}...`}
            value={query}
            onChange={(e) => {
              setQuery(e.target.value)
              if (phase === "idle") setPhase("selectField")
            }}
            onFocus={() => {
              if (phase === "idle" && !query) setPhase("selectField")
            }}
            onKeyDown={handleKeyDown}
          />

          {/* 清除全部 */}
          {filters.length > 0 && (
            <button
              type="button"
              className="text-muted-foreground hover:text-destructive"
              onClick={() => onChange([])}
            >
              ✕
            </button>
          )}
        </div>
      </PopoverTrigger>
      {suggestionOpen && (
        <PopoverContent align="start" className="w-80 gap-0 p-0" sideOffset={4}>
          {phase === "selectField" && (
            <>
              {quickFilters.length > 0 && (
                <div className="border-b p-1">
                  <p className="px-2 py-1 text-muted-foreground text-xs">预设筛选</p>
                  {quickFilters.map((preset) => {
                    const selected = activeQuickFilters.includes(preset)
                    return (
                      <button
                        key={`${preset.label}-${preset.conditions
                          .map(
                            (condition) =>
                              `${condition.field}:${condition.operator}:${condition.values.join("|")}`
                          )
                          .join(",")}`}
                        type="button"
                        className="flex w-full items-center justify-between rounded px-3 py-1.5 text-left text-sm hover:bg-muted"
                        onClick={() => handleQuickFilterSelect(preset)}
                      >
                        <span>{preset.label}</span>
                        {selected && (
                          <span
                            role="img"
                            aria-label={`${preset.label}已选中`}
                            className="text-primary"
                          >
                            ✓
                          </span>
                        )}
                      </button>
                    )
                  })}
                </div>
              )}
              {query && (
                <button
                  type="button"
                  className="w-full border-b px-3 py-2 text-left text-sm hover:bg-muted"
                  onClick={handleSearchSubmit}
                >
                  搜索 &quot;关键词（{entity.listView.searchableFields?.join("、") ?? "名称、描述"}
                  ）: {query}&quot;
                </button>
              )}
              <ul className="max-h-48 overflow-auto p-1">
                {filteredFields.slice(0, 10).map((field) => (
                  <li key={field.name}>
                    <button
                      type="button"
                      className="w-full rounded px-3 py-1.5 text-left text-sm hover:bg-muted"
                      onClick={() => handleSelectField(field)}
                    >
                      {field.label ?? field.name}
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
          {phase === "inputValue" && selectOptions && (
            <ul className="max-h-48 overflow-auto p-1">
              {selectOptions.map((option) => (
                <li key={option.value}>
                  <button
                    type="button"
                    className="w-full rounded px-3 py-1.5 text-left text-sm hover:bg-muted"
                    onClick={() => handleConfirmValue(option.value)}
                  >
                    {option.label}
                  </button>
                </li>
              ))}
            </ul>
          )}
          {phase === "inputValue" && selectedField?.type === "date" && (
            <DateRangePopover onConfirm={handleConfirmDateRange} onCancel={closeSuggestions} />
          )}
        </PopoverContent>
      )}
    </Popover>
  )
}

function isSameFilterCondition(left: FilterCondition, right: FilterCondition): boolean {
  return (
    left.field === right.field &&
    left.operator === right.operator &&
    left.values.length === right.values.length &&
    left.values.every((value, index) => value === right.values[index])
  )
}

/** 日期范围弹窗 */
function DateRangePopover({
  onConfirm,
  onCancel
}: {
  onConfirm: (start: string, end: string) => void
  onCancel: () => void
}) {
  const [start, setStart] = useState("")
  const uid = useId()
  const [end, setEnd] = useState("")

  return (
    <div className="p-4">
      <p className="mb-2 text-primary text-xs">请至少输入一个日期</p>
      <div className="space-y-2">
        <div>
          <label htmlFor={`${uid}-start`} className="font-medium text-xs">
            开始日期
          </label>
          <input
            id={`${uid}-start`}
            type="date"
            className="mt-0.5 h-8 w-full rounded border px-2 text-sm"
            value={start}
            onChange={(e) => setStart(e.target.value)}
          />
        </div>
        <div>
          <label htmlFor={`${uid}-end`} className="font-medium text-xs">
            结束日期
          </label>
          <input
            id={`${uid}-end`}
            type="date"
            className="mt-0.5 h-8 w-full rounded border px-2 text-sm"
            value={end}
            onChange={(e) => setEnd(e.target.value)}
          />
        </div>
      </div>
      <div className="mt-3 flex justify-end gap-2">
        <button type="button" className="rounded border px-3 py-1 text-sm" onClick={onCancel}>
          取消
        </button>
        <button
          type="button"
          className="rounded bg-primary px-3 py-1 text-primary-foreground text-sm"
          onClick={() => {
            if (start || end) onConfirm(start, end)
          }}
        >
          确定
        </button>
      </div>
    </div>
  )
}
