/**
 * useSubtable——子表明细行数据逻辑
 * @author AaronZZH & Kiro
 */

import { useCallback } from "react"

import type { DataFieldDef } from "@/features/entity-engine/types"

export function useSubtable(
  fields: DataFieldDef[],
  value: Record<string, unknown>[],
  onChange: (rows: Record<string, unknown>[]) => void
) {
  const addRow = useCallback(() => {
    const empty: Record<string, unknown> = { _key: crypto.randomUUID() }
    for (const f of fields) empty[f.name] = ""
    onChange([...value, empty])
  }, [fields, value, onChange])

  const removeRow = useCallback(
    (index: number) => onChange(value.filter((_, i) => i !== index)),
    [value, onChange]
  )

  const updateCell = useCallback(
    (rowIndex: number, field: string, cellValue: unknown) =>
      onChange(value.map((row, i) => (i === rowIndex ? { ...row, [field]: cellValue } : row))),
    [value, onChange]
  )

  const computeSummaries = useCallback(
    (summaryFields?: string[]) =>
      summaryFields?.reduce(
        (acc, f) => {
          acc[f] = value.reduce((sum, row) => sum + (Number(row[f]) || 0), 0)
          return acc
        },
        {} as Record<string, number>
      ),
    [value]
  )

  return { addRow, removeRow, updateCell, computeSummaries }
}
