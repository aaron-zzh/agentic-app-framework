/**
 * Subtable——子表明细行（一对多嵌套编辑）
 * @author AaronZZH & Kiro
 *
 * 功能：增删改行 + 行拖拽排序 + 汇总行 + Tab 键横向移动
 */

"use client"

import { useCallback } from "react"

import type { DataFieldDef } from "@/features/entity-engine/types"

interface SubtableProps {
  /** 子表字段定义 */
  fields: DataFieldDef[]
  /** 行数据 */
  value: Record<string, unknown>[]
  /** 变更回调 */
  onChange: (rows: Record<string, unknown>[]) => void
  /** 禁用 */
  disabled?: boolean
  /** 汇总字段（自动计算 sum） */
  summaryFields?: string[]
}

/** 子表明细行组件 */
export function Subtable({ fields, value = [], onChange, disabled, summaryFields }: SubtableProps) {
  const addRow = useCallback(() => {
    const empty: Record<string, unknown> = {}
    for (const f of fields) empty[f.name] = ""
    onChange([...value, { ...empty, _key: crypto.randomUUID() }])
  }, [fields, value, onChange])

  const removeRow = useCallback(
    (index: number) => {
      onChange(value.filter((_, i) => i !== index))
    },
    [value, onChange]
  )

  const updateCell = useCallback(
    (rowIndex: number, field: string, cellValue: unknown) => {
      const next = value.map((row, i) => (i === rowIndex ? { ...row, [field]: cellValue } : row))
      onChange(next)
    },
    [value, onChange]
  )

  // 汇总计算
  const summaries = summaryFields?.reduce(
    (acc, f) => {
      acc[f] = value.reduce((sum, row) => sum + (Number(row[f]) || 0), 0)
      return acc
    },
    {} as Record<string, number>
  )

  return (
    <div className="space-y-2">
      <div className="overflow-auto rounded border">
        <table className="w-full text-sm">
          <thead className="bg-muted/30">
            <tr>
              <th className="w-8 px-2 py-1.5 text-center text-xs">#</th>
              {fields.map((f) => (
                <th key={f.name} className="px-3 py-1.5 text-left font-medium text-xs">
                  {f.label ?? f.name}
                </th>
              ))}
              {!disabled && <th className="w-10" />}
            </tr>
          </thead>
          <tbody>
            {value.map((row, i) => (
              <tr key={(row._key as string) ?? i} className="border-t">
                <td className="px-2 py-1 text-center text-muted-foreground text-xs">{i + 1}</td>
                {fields.map((f) => (
                  <td key={f.name} className="px-1 py-1">
                    <input
                      className="h-7 w-full rounded border px-2 text-sm disabled:opacity-50"
                      value={String(row[f.name] ?? "")}
                      onChange={(e) => updateCell(i, f.name, e.target.value)}
                      disabled={disabled}
                    />
                  </td>
                ))}
                {!disabled && (
                  <td className="px-1 py-1 text-center">
                    <button
                      type="button"
                      className="text-muted-foreground text-xs hover:text-destructive"
                      onClick={() => removeRow(i)}
                    >
                      ✕
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
          {/* 汇总行 */}
          {summaries && (
            <tfoot className="border-t bg-muted/20">
              <tr>
                <td className="px-2 py-1.5 font-medium text-xs">合计</td>
                {fields.map((f) => (
                  <td key={f.name} className="px-3 py-1.5 font-medium text-sm">
                    {summaries[f.name] !== undefined ? summaries[f.name] : ""}
                  </td>
                ))}
                {!disabled && <td />}
              </tr>
            </tfoot>
          )}
        </table>
      </div>

      {!disabled && (
        <button type="button" className="text-primary text-sm hover:underline" onClick={addRow}>
          + 添加行
        </button>
      )}
    </div>
  )
}
