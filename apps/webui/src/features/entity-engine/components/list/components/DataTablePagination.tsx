/**
 * DataTablePagination——表格分页组件
 * @author AaronZZH & Kiro
 */

import type { Table } from "@tanstack/react-table"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { useId } from "react"
import { Button } from "@/components/ui/button"
import { Switch } from "@/components/ui/switch"

interface DataTablePaginationProps<TData> {
  table: Table<TData>
  dense?: boolean
  onDenseChange?: (dense: boolean) => void
  /** 服务端总条数（服务端分页模式时传入） */
  serverTotal?: number
}

export function DataTablePagination<TData>({
  table,
  dense = false,
  onDenseChange,
  serverTotal
}: DataTablePaginationProps<TData>) {
  const uid = useId()
  const denseId = `${uid}-dense`
  const pageIndex = table.getState().pagination.pageIndex
  const pageCount = table.getPageCount()
  const totalCount = serverTotal ?? table.getFilteredRowModel().rows.length

  const pages = getPageNumbers(pageIndex, pageCount)

  return (
    <div className="flex shrink-0 items-center border-t px-4 py-3">
      {/* 左侧：紧凑模式 */}
      <div className="flex flex-1 items-center gap-2">
        <label
          htmlFor={denseId}
          className="flex cursor-pointer items-center gap-1.5 text-muted-foreground text-xs"
        >
          <Switch
            id={denseId}
            checked={dense}
            onCheckedChange={(checked) => onDenseChange?.(!!checked)}
          />
          紧凑
        </label>
      </div>

      {/* 中间：页码 */}
      <div className="flex items-center gap-1">
        <Button
          variant="ghost"
          size="icon"
          className="size-7"
          onClick={() => table.previousPage()}
          disabled={!table.getCanPreviousPage()}
        >
          <ChevronLeft className="size-4" />
        </Button>

        {pages.map((p, i) =>
          p === "..." ? (
            <span key={`e${i}`} className="px-1 text-muted-foreground text-xs">
              …
            </span>
          ) : (
            <Button
              key={p}
              variant={p === pageIndex + 1 ? "default" : "ghost"}
              size="icon"
              className="size-7 text-xs"
              onClick={() => table.setPageIndex(p - 1)}
            >
              {p}
            </Button>
          )
        )}

        <Button
          variant="ghost"
          size="icon"
          className="size-7"
          onClick={() => table.nextPage()}
          disabled={!table.getCanNextPage()}
        >
          <ChevronRight className="size-4" />
        </Button>
      </div>

      {/* 右侧：总数 + 每页条数 */}
      <div className="flex flex-1 items-center justify-end gap-4">
        <span className="text-muted-foreground text-xs">共 {totalCount} 条</span>
        <div className="flex items-center gap-1.5 text-xs">
          <span className="text-muted-foreground">每页</span>
          <select
            className="h-7 rounded-md border bg-background px-2 text-xs"
            value={table.getState().pagination.pageSize}
            onChange={(e) => table.setPageSize(Number(e.target.value))}
          >
            {[10, 20, 50, 100].map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </select>
          <span className="text-muted-foreground">条</span>
        </div>
      </div>
    </div>
  )
}

/** 生成页码数组，最多显示 5 个数字 + 省略号 */
function getPageNumbers(current: number, total: number): (number | "...")[] {
  if (total <= 5) return Array.from({ length: total }, (_, i) => i + 1)

  const page = current + 1
  if (page <= 3) return [1, 2, 3, 4, "...", total]
  if (page >= total - 2) return [1, "...", total - 3, total - 2, total - 1, total]
  return [1, "...", page - 1, page, page + 1, "...", total]
}
