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

import { useCallback, useEffect, useId, useRef, useState } from "react"

import type { DataFieldDef, EntityDef } from "@/features/entity-engine/types"
import type { FilterCondition } from "./FilterBuilder"

type InputPhase = "idle" | "selectField" | "inputValue"

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
      onChange([...filters, { field: selectedField.name, operator: op, value: value.trim() }])
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
    onChange([...filters, { field: "__search", operator: "contains", value: query.trim() }])
    setQuery("")
    setPhase("idle")
  }, [query, filters, onChange])

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
      ? (selectedField as unknown as { options: { value: string; label: string }[] }).options
      : null

  const containerRef = useRef<HTMLDivElement>(null)

  // 点击外部关闭建议面板
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setPhase("idle")
        setSelectedField(null)
        setQuery("")
      }
    }
    document.addEventListener("mousedown", handleClickOutside)
    return () => document.removeEventListener("mousedown", handleClickOutside)
  }, [])

  return (
    <div ref={containerRef} className="relative flex-1">
      {/* 搜索框容器 */}
      <div className="flex min-h-[36px] flex-wrap items-center gap-1 rounded-md px-2 py-1 focus-within:bg-muted/50">
        <span className="text-muted-foreground">🔍</span>

        {/* 已选条件 tag */}
        {filters.map((f, i) => {
          const fieldDef = allFields.find((fd) => fd.name === f.field)
          const label = f.field === "__search" ? "关键词" : (fieldDef?.label ?? f.field)
          // select 字段显示 option label 而非原始 value
          const valueLabel =
            fieldDef?.type === "select" && "options" in fieldDef
              ? ((
                  fieldDef as unknown as { options: { value: string; label: string }[] }
                ).options.find((o) => o.value === f.value)?.label ?? f.value)
              : f.value
          return (
            <span
              key={`${f.field}-${f.operator}-${String(f.value)}`}
              className="inline-flex items-center gap-0.5 rounded bg-muted px-1.5 py-0.5 text-xs"
            >
              <span className="font-medium">{label}:</span>
              <span>{valueLabel}</span>
              <button
                type="button"
                className="ml-0.5 hover:text-destructive"
                onClick={() => handleRemove(i)}
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

      {/* 建议面板 */}
      {phase === "selectField" && (
        <div className="absolute top-full left-0 z-20 mt-1 w-full rounded-md border bg-background shadow-md">
          {query && (
            <button
              type="button"
              className="w-full border-b px-3 py-2 text-left text-sm hover:bg-muted"
              onClick={handleSearchSubmit}
            >
              搜索 &quot;关键词（{entity.listView.searchableFields?.join("、") ?? "名称、描述"}）:{" "}
              {query}&quot;
            </button>
          )}
          <ul className="max-h-48 overflow-auto p-1">
            {filteredFields.slice(0, 10).map((f) => (
              <li key={f.name}>
                <button
                  type="button"
                  className="w-full rounded px-3 py-1.5 text-left text-sm hover:bg-muted"
                  onClick={() => handleSelectField(f)}
                >
                  {f.label ?? f.name}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* select 类型：选项列表 */}
      {phase === "inputValue" && selectOptions && (
        <div className="absolute top-full left-0 z-20 mt-1 w-64 rounded-md border bg-background shadow-md">
          <ul className="max-h-48 overflow-auto p-1">
            {selectOptions.map((opt) => (
              <li key={opt.value}>
                <button
                  type="button"
                  className="w-full rounded px-3 py-1.5 text-left text-sm hover:bg-muted"
                  onClick={() => handleConfirmValue(opt.value)}
                >
                  {opt.label}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* date 类型：日期范围表单 */}
      {phase === "inputValue" && selectedField?.type === "date" && (
        <DateRangePopover
          onConfirm={handleConfirmValue}
          onCancel={() => {
            setPhase("idle")
            setSelectedField(null)
          }}
        />
      )}
    </div>
  )
}

/** 日期范围弹窗 */
function DateRangePopover({
  onConfirm,
  onCancel
}: {
  onConfirm: (value: string) => void
  onCancel: () => void
}) {
  const [start, setStart] = useState("")
  const uid = useId()
  const [end, setEnd] = useState("")

  return (
    <div className="absolute top-full left-0 z-20 mt-1 w-64 rounded-md border bg-background p-4 shadow-md">
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
            if (start || end) onConfirm(`${start || "..."}~${end || "..."}`)
          }}
        >
          确定
        </button>
      </div>
    </div>
  )
}
