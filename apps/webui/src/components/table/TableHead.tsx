/**
 * TableHead——列头（全选复选框 + 排序）
 * @author AaronZZH & Kiro
 */

import { cn } from "@/lib/utils/cn"

export interface HeadCell {
  id: string
  label: string
  width?: string
  sortable?: boolean
  align?: "left" | "center" | "right"
}

interface TableHeadProps {
  headCells: HeadCell[]
  order?: "asc" | "desc"
  orderBy?: string
  rowCount?: number
  numSelected?: number
  onSort?: (id: string) => void
  onSelectAllRows?: (checked: boolean) => void
}

/** 列表表头 */
export function TableHead({
  headCells,
  order,
  orderBy,
  rowCount = 0,
  numSelected = 0,
  onSort,
  onSelectAllRows
}: TableHeadProps) {
  return (
    <thead className="border-b bg-muted/30">
      <tr>
        {onSelectAllRows && (
          <th className="w-10 px-2">
            <input
              type="checkbox"
              className="h-4 w-4 rounded border"
              checked={rowCount > 0 && numSelected === rowCount}
              ref={(el) => {
                if (el) el.indeterminate = numSelected > 0 && numSelected < rowCount
              }}
              onChange={(e) => onSelectAllRows(e.target.checked)}
              aria-label="全选"
            />
          </th>
        )}
        {headCells.map((cell) => (
          <th
            key={cell.id}
            className={cn(
              "h-10 px-4 text-left align-middle font-medium text-muted-foreground text-xs",
              cell.align === "center" && "text-center",
              cell.align === "right" && "text-right"
            )}
            style={cell.width ? { width: cell.width } : undefined}
          >
            {cell.sortable && onSort ? (
              <button
                type="button"
                className="inline-flex items-center gap-1 hover:text-foreground"
                onClick={() => onSort(cell.id)}
              >
                {cell.label}
                {orderBy === cell.id && (
                  <span className="text-xs">{order === "asc" ? "↑" : "↓"}</span>
                )}
              </button>
            ) : (
              cell.label
            )}
          </th>
        ))}
        {/* 操作列 */}
        <th className="w-16" />
      </tr>
    </thead>
  )
}
