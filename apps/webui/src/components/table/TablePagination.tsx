/**
 * TablePagination——分页 + Dense 切换
 * @author AaronZZH & Kiro
 */

import { Checkbox } from "@/components/ui/checkbox"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"

interface TablePaginationProps {
  page: number
  pageSize: number
  total: number
  dense?: boolean
  onChangePage: (page: number) => void
  onChangePageSize: (size: number) => void
  onChangeDense?: () => void
}

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]

/** 分页组件 */
export function TablePagination({
  page,
  pageSize,
  total,
  dense,
  onChangePage,
  onChangePageSize,
  onChangeDense
}: TablePaginationProps) {
  const totalPages = Math.ceil(total / pageSize)
  const from = (page - 1) * pageSize + 1
  const to = Math.min(page * pageSize, total)

  return (
    <div className="flex items-center justify-between border-t px-4 py-2">
      <div className="flex items-center gap-3">
        {onChangeDense && (
          <label className="flex items-center gap-1.5 text-muted-foreground text-xs">
            <Checkbox
              checked={!!dense}
              onCheckedChange={() => onChangeDense()}
              aria-label="紧凑模式"
            />
            紧凑
          </label>
        )}
      </div>

      <div className="flex items-center gap-4 text-sm">
        <div className="flex items-center gap-1.5 text-muted-foreground text-xs">
          每页
          <Select
            value={String(pageSize)}
            onValueChange={(v) => onChangePageSize(Number(v))}
          >
            <SelectTrigger className="h-7 w-16 text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PAGE_SIZE_OPTIONS.map((n) => (
                <SelectItem key={n} value={String(n)}>
                  {n}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <span className="text-muted-foreground text-xs">
          {total > 0 ? `${from}–${to} / ${total}` : "0 条"}
        </span>

        <div className="flex gap-1">
          <button
            type="button"
            className="h-7 w-7 rounded border text-xs disabled:opacity-30"
            disabled={page <= 1}
            onClick={() => onChangePage(page - 1)}
            aria-label="上一页"
          >
            ‹
          </button>
          <button
            type="button"
            className="h-7 w-7 rounded border text-xs disabled:opacity-30"
            disabled={page >= totalPages}
            onClick={() => onChangePage(page + 1)}
            aria-label="下一页"
          >
            ›
          </button>
        </div>
      </div>
    </div>
  )
}
