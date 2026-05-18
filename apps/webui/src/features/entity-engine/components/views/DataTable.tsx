/**
 * DataTable——基于 TanStack Table 的通用数据表格
 * @author AaronZZH & Kiro
 *
 * 功能：排序、行选择、分页、批量操作浮层
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  type RowSelectionState,
  type SortingState,
  useReactTable
} from "@tanstack/react-table"
import { ArrowDown, ArrowUp, ArrowUpDown, Trash2 } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import { DataTablePagination } from "./DataTablePagination"

interface DataTableProps<TData> {
  columns: ColumnDef<TData, unknown>[]
  data: TData[]
  /** 行点击回调 */
  onRowClick?: (row: TData) => void
  /** 批量删除回调 */
  onBatchDelete?: (rows: TData[]) => void
  /** 行尾操作渲染 */
  renderRowActions?: (row: TData) => React.ReactNode
  /** 表头右侧额外操作（如列配置） */
  headerAction?: React.ReactNode
  /** 是否启用行选择 */
  enableSelection?: boolean
  /** 是否启用分页 */
  enablePagination?: boolean
}

export function DataTable<TData>({
  columns,
  data,
  onRowClick,
  onBatchDelete,
  renderRowActions,
  headerAction,
  enableSelection = true,
  enablePagination = true
}: DataTableProps<TData>) {
  const [sorting, setSorting] = useState<SortingState>([])
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})
  const { value: dense, setValue: setDense } = useBoolean(false)
  // 组装列：选择列 + 数据列 + 操作列
  const allColumns: ColumnDef<TData, unknown>[] = [
    ...(enableSelection ? [selectColumn<TData>()] : []),
    ...columns,
    ...(renderRowActions ? [actionsColumn<TData>(renderRowActions, headerAction)] : [])
  ]

  const table = useReactTable({
    data,
    columns: allColumns,
    state: { sorting, rowSelection },
    onSortingChange: setSorting,
    onRowSelectionChange: setRowSelection,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    ...(enablePagination && { getPaginationRowModel: getPaginationRowModel() }),
    enableRowSelection: enableSelection
  })

  const selectedRows = table.getFilteredSelectedRowModel().rows
  const hasSelection = selectedRows.length > 0

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      {/* 表格 */}
      <div className="relative flex-1 overflow-auto">
        {/* 批量操作浮层——覆盖表头 */}
        {hasSelection && (
          <div className="absolute inset-x-0 top-0 z-10 flex h-10 items-center gap-3 border-b bg-accent px-2">
            <Checkbox
              checked={table.getIsAllPageRowsSelected()}
              indeterminate={table.getIsSomePageRowsSelected()}
              onCheckedChange={(checked) => table.toggleAllPageRowsSelected(!!checked)}
              onClick={(e) => e.stopPropagation()}
            />
            <span className="font-medium text-primary text-sm">已选 {selectedRows.length} 项</span>
            <div className="flex-1" />
            <Button
              variant="ghost"
              size="sm"
              className="text-destructive hover:text-destructive"
              onClick={() => onBatchDelete?.(selectedRows.map((r) => r.original))}
            >
              <Trash2 className="mr-1 size-4" />
              删除
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setRowSelection({})}>
              取消选择
            </Button>
          </div>
        )}
        <Table>
          <TableHeader className="sticky top-0 z-[5] bg-card">
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead
                    key={header.id}
                    style={{ width: header.getSize() !== 150 ? header.getSize() : undefined }}
                    className={header.column.getCanSort() ? "cursor-pointer select-none" : ""}
                    onClick={header.column.getToggleSortingHandler()}
                  >
                    <div className="flex items-center gap-1">
                      {header.isPlaceholder
                        ? null
                        : flexRender(header.column.columnDef.header, header.getContext())}
                      {header.column.getCanSort() && (
                        <SortIcon direction={header.column.getIsSorted()} />
                      )}
                    </div>
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={allColumns.length}
                  className="h-24 text-center text-muted-foreground"
                >
                  暂无数据
                </TableCell>
              </TableRow>
            ) : (
              table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  data-state={row.getIsSelected() && "selected"}
                  className="group cursor-pointer"
                  onClick={() => onRowClick?.(row.original)}
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id} className={dense ? "py-1" : ""}>
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* 分页 */}
      {enablePagination && (
        <DataTablePagination table={table} dense={dense} onDenseChange={setDense} />
      )}
    </div>
  )
}

/** 选择列定义 */
function selectColumn<TData>(): ColumnDef<TData, unknown> {
  return {
    id: "select",
    size: 40,
    enableSorting: false,
    header: ({ table }) => (
      // biome-ignore lint/a11y/noStaticElementInteractions lint/a11y/useKeyWithClickEvents: 阻止排序点击冒泡
      <div onClick={(e) => e.stopPropagation()}>
        <Checkbox
          checked={table.getIsAllPageRowsSelected()}
          indeterminate={table.getIsSomePageRowsSelected()}
          onCheckedChange={(checked) => table.toggleAllPageRowsSelected(!!checked)}
          aria-label="全选"
        />
      </div>
    ),
    cell: ({ row }) => (
      // biome-ignore lint/a11y/noStaticElementInteractions lint/a11y/useKeyWithClickEvents: 阻止行点击冒泡
      <div onClick={(e) => e.stopPropagation()}>
        <Checkbox
          checked={row.getIsSelected()}
          onCheckedChange={(checked) => row.toggleSelected(!!checked)}
          aria-label="选择行"
        />
      </div>
    )
  }
}

/** 排序图标 */
function SortIcon({ direction }: { direction: false | "asc" | "desc" }) {
  if (direction === "asc") return <ArrowUp className="size-3.5 text-foreground" />
  if (direction === "desc") return <ArrowDown className="size-3.5 text-foreground" />
  return <ArrowUpDown className="size-3.5 text-muted-foreground/50" />
}

/** 操作列定义 */
function actionsColumn<TData>(
  render: (row: TData) => React.ReactNode,
  headerAction?: React.ReactNode
): ColumnDef<TData, unknown> {
  return {
    id: "actions",
    size: 120,
    enableSorting: false,
    header: () => (
      <div className="flex w-full items-center">
        <span className="flex-1 text-center">操作</span>
        {headerAction}
      </div>
    ),
    cell: ({ row }) => <div className="flex justify-end">{render(row.original)}</div>
  }
}
