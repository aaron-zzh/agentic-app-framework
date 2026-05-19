/**
 * Subtable——子表明细行（一对多嵌套编辑）
 * @author AaronZZH & Kiro
 */

"use client"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import type { DataFieldDef } from "@/lib/types/entity"
import { useSubtable } from "@/lib/hooks/use-subtable"

interface SubtableProps {
  fields: DataFieldDef[]
  value: Record<string, unknown>[]
  onChange: (rows: Record<string, unknown>[]) => void
  disabled?: boolean
  summaryFields?: string[]
}

export function Subtable({ fields, value = [], onChange, disabled, summaryFields }: SubtableProps) {
  const { addRow, removeRow, updateCell, computeSummaries } = useSubtable(fields, value, onChange)
  const summaries = computeSummaries(summaryFields)

  return (
    <div className="flex flex-col gap-2">
      <div className="overflow-auto rounded-lg border">
        <table className="w-full text-sm">
          <thead className="bg-muted/30">
            <tr>
              <th className="w-8 px-2 py-1.5 text-center text-muted-foreground text-xs">#</th>
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
                {fields.map((f, fi) => (
                  <td key={f.name} className="px-1 py-1">
                    <Input
                      className="h-7"
                      value={String(row[f.name] ?? "")}
                      onChange={(e) => updateCell(i, f.name, e.target.value)}
                      disabled={disabled}
                      // Tab 到最后一列最后一行时自动添加新行
                      onKeyDown={(e) => {
                        if (
                          e.key === "Tab" &&
                          !e.shiftKey &&
                          fi === fields.length - 1 &&
                          i === value.length - 1
                        ) {
                          e.preventDefault()
                          addRow()
                        }
                      }}
                    />
                  </td>
                ))}
                {!disabled && (
                  <td className="px-1 py-1 text-center">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-xs"
                      onClick={() => removeRow(i)}
                      aria-label="删除行"
                    >
                      ×
                    </Button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
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
        <Button type="button" variant="ghost" size="sm" className="self-start" onClick={addRow}>
          + 添加行
        </Button>
      )}
    </div>
  )
}
