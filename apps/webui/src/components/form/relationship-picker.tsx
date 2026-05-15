/**
 * RelationshipPicker——关联字段（异步搜索 + 快速创建 + 多选 Tag）
 * @author AaronZZH & Kiro
 * 参考 next-ts Autocomplete 模式
 */

"use client"

import { useCallback, useEffect, useRef, useState } from "react"

import type { FieldProps } from "@/features/entity-engine/types"

interface RelationshipPickerProps extends FieldProps<string | string[]> {
  /** 是否多选 */
  multiple?: boolean
  /** 搜索 API 路径 */
  searchEndpoint?: string
  /** 显示字段 */
  displayField?: string
}

interface Option {
  id: string
  label: string
}

/** 关联字段组件 */
export function RelationshipPicker({
  name,
  value,
  onChange,
  error,
  disabled,
  multiple = false,
  searchEndpoint,
  displayField = "name",
}: RelationshipPickerProps) {
  const [query, setQuery] = useState("")
  const [options, setOptions] = useState<Option[]>([])
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout>>()

  // 异步搜索（debounce 300ms）
  useEffect(() => {
    if (!query.trim() || !searchEndpoint) {
      setOptions([])
      return
    }
    clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(async () => {
      setLoading(true)
      try {
        const res = await fetch(`${searchEndpoint}?search=${encodeURIComponent(query)}&limit=10`)
        const json = await res.json()
        const list = (json.data?.list ?? json.data ?? []) as Record<string, unknown>[]
        setOptions(list.map((r) => ({ id: r.id as string, label: r[displayField] as string ?? String(r.id) })))
      } catch {
        setOptions([])
      } finally {
        setLoading(false)
      }
    }, 300)
  }, [query, searchEndpoint, displayField])

  const selectedIds = multiple ? (Array.isArray(value) ? value : []) : value ? [value as string] : []

  const handleSelect = useCallback(
    (opt: Option) => {
      if (multiple) {
        const current = Array.isArray(value) ? value : []
        if (!current.includes(opt.id)) {
          onChange([...current, opt.id] as unknown as string | string[])
        }
      } else {
        onChange(opt.id as unknown as string | string[])
      }
      setQuery("")
      setOpen(false)
    },
    [multiple, value, onChange]
  )

  const handleRemove = useCallback(
    (id: string) => {
      if (multiple && Array.isArray(value)) {
        onChange(value.filter((v) => v !== id) as unknown as string | string[])
      } else {
        onChange("" as unknown as string | string[])
      }
    },
    [multiple, value, onChange]
  )

  return (
    <div className="space-y-1">
      {/* 已选 Tag */}
      {selectedIds.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {selectedIds.map((id) => (
            <span key={id} className="inline-flex items-center gap-1 rounded bg-muted px-2 py-0.5 text-xs">
              {id}
              {!disabled && (
                <button type="button" className="hover:text-destructive" onClick={() => handleRemove(id)}>✕</button>
              )}
            </span>
          ))}
        </div>
      )}

      {/* 搜索输入 */}
      <div className="relative">
        <input
          className="h-9 w-full rounded-md border px-3 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:opacity-50"
          placeholder="搜索关联记录..."
          value={query}
          onChange={(e) => { setQuery(e.target.value); setOpen(true) }}
          onFocus={() => setOpen(true)}
          disabled={disabled}
        />
        {loading && <span className="absolute right-3 top-2.5 text-xs animate-spin">⏳</span>}

        {/* 下拉选项 */}
        {open && options.length > 0 && (
          <ul className="absolute left-0 top-10 z-20 max-h-48 w-full overflow-auto rounded-md border bg-background shadow-md">
            {options.map((opt) => (
              <li key={opt.id}>
                <button
                  type="button"
                  className="w-full px-3 py-2 text-left text-sm hover:bg-muted disabled:opacity-50"
                  onClick={() => handleSelect(opt)}
                  disabled={selectedIds.includes(opt.id)}
                >
                  {opt.label}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}
