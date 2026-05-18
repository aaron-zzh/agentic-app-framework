/**
 * GroupedListView——按字段分组展示列表（listView.groupBy 配置）
 * @author AaronZZH & Kiro
 */

"use client"

import { useMemo, useState } from "react"

import { getCellComponent } from "../../../lib/component-registry"
import type { ColumnDef, DataFieldDef } from "../../../types"

type ColumnInfo = { name: string; field: DataFieldDef; def: ColumnDef }

interface GroupedListViewProps {
  columns: ColumnInfo[]
  data: Record<string, unknown>[]
  groupBy: string
  groupField?: DataFieldDef
}

/** 按字段分组的列表视图 */
export function GroupedListView({ columns, data, groupBy, groupField }: GroupedListViewProps) {
  const groups = useMemo(() => {
    const map = new Map<string, Record<string, unknown>[]>()
    for (const record of data) {
      const key = String(record[groupBy] ?? "未分组")
      if (!map.has(key)) map.set(key, [])
      map.get(key)?.push(record)
    }
    return [...map.entries()]
  }, [data, groupBy])

  return (
    <div className="w-full overflow-auto">
      {groups.map(([groupValue, records]) => (
        <GroupSection
          key={groupValue}
          label={groupField?.label ? `${groupField.label}: ${groupValue}` : groupValue}
          count={records.length}
          columns={columns}
          records={records}
        />
      ))}
    </div>
  )
}

/** 分组区块（可折叠） */
function GroupSection({
  label,
  count,
  columns,
  records
}: {
  label: string
  count: number
  columns: ColumnInfo[]
  records: Record<string, unknown>[]
}) {
  const [collapsed, setCollapsed] = useState(false)

  return (
    <div className="border-b">
      <button
        type="button"
        className="flex w-full items-center gap-2 px-4 py-2 text-left font-medium text-sm hover:bg-muted/50"
        onClick={() => setCollapsed(!collapsed)}
      >
        <span className="text-xs">{collapsed ? "▶" : "▼"}</span>
        <span>{label}</span>
        <span className="text-muted-foreground text-xs">({count})</span>
      </button>
      {!collapsed && (
        <table className="w-full text-sm">
          <tbody>
            {records.map((record, i) => (
              <tr
                key={(record.id as string) ?? i}
                className="border-t transition-colors hover:bg-muted/50"
              >
                {columns.map((col) => {
                  const Cell = getCellComponent(col.field.type)
                  const value = record[col.name]
                  return (
                    <td key={col.name} className="h-10 px-4 align-middle">
                      {Cell ? (
                        <Cell value={value} record={record} field={col.field} />
                      ) : (
                        <span className="truncate">{String(value ?? "—")}</span>
                      )}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
