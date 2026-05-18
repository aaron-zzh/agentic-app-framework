/**
 * EntityDef → TanStack Table ColumnDef 转换
 * @author AaronZZH & Kiro
 */

import type { ColumnDef } from "@tanstack/react-table"
import type { DataFieldDef, ColumnDef as EntityColumnDef, EntityDef } from "../types"
import { getCellComponent } from "./component-registry"

export function buildColumns(
  entity: EntityDef,
  visibleColumns: EntityColumnDef[]
): ColumnDef<Record<string, unknown>>[] {
  const { fields } = entity

  return visibleColumns
    .map((col) => {
      const field = fields.find((f) => "name" in f && (f as DataFieldDef).name === col.name) as
        | DataFieldDef
        | undefined
      if (!field) return null

      const Cell = getCellComponent(field.type)

      const columnDef: ColumnDef<Record<string, unknown>> = {
        accessorKey: col.name,
        header: field.label ?? col.name,
        size: col.width ? Number.parseInt(String(col.width), 10) : undefined,
        cell: ({ row }) => {
          const value = row.original[col.name]
          if (Cell) {
            return <Cell value={value} record={row.original} field={field} />
          }
          return <span className="truncate">{String(value ?? "—")}</span>
        }
      }
      return columnDef
    })
    .filter(Boolean) as ColumnDef<Record<string, unknown>>[]
}
