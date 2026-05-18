/**
 * DataTable——基于 TanStack Table 的通用数据表格
 * @author AaronZZH & Kiro
 *
 * 功能：排序、行选择、分页、批量操作浮层
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  PointerSensor,
  useSensor,
  useSensors
} from "@dnd-kit/core"
import {
  arrayMove,
  SortableContext,
  useSortable,
  verticalListSortingStrategy
} from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"
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
import { ArrowDown, ArrowUp, ArrowUpDown, GripVertical, Trash2 } from "lucide-react"
import { useCallback, useState } from "react"
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
import { cn } from "@/lib/utils/cn"
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
  /** 是否启用列头排序（默认 true） */
  enableSort?: boolean
  /** 是否启用行拖拽排序 */
  draggable?: boolean
  /** 拖拽完成回调（返回新顺序的 id 数组） */
  onReorder?: (ids: string[]) => void
  /** 服务端分页信息（有值时切换为服务端分页模式） */
  serverPagination?: { page: number; pageSize: number; total: number }
  onPageChange?: (page: number) => void
  onPageSizeChange?: (pageSize: number) => void
  /** 表格内容折行 */
  wordWrap?: boolean
  /** 数据列固定：none / first / first-two */
  columnFreeze?: "none" | "first" | "first-two"
  /** 操作列固定 */
  actionColumnFixed?: boolean
}

export function DataTable<TData>({
  columns,
  data,
  onRowClick,
  onBatchDelete,
  renderRowActions,
  headerAction,
  enableSelection = true,
  enableSort = true,
  draggable = false,
  onReorder,
  serverPagination,
  onPageChange,
  onPageSizeChange,
  wordWrap = false,
  columnFreeze = "none",
  actionColumnFixed = true
}: DataTableProps<TData>) {
  const [sorting, setSorting] = useState<SortingState>([])
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})
  const [columnSizing, setColumnSizing] = useState({})
  const [items, setItems] = useState<TData[]>(data)
  const { value: dense, setValue: setDense } = useBoolean(false)

  // 计算需要左固定的列 id（选择列 + 拖拽列 + 数据列）
  // TanStack Table 用 accessorKey 作为列 id（无显式 id 时）
  const getColId = (col: ColumnDef<TData, unknown>) =>
    col.id ?? (col as { accessorKey?: string }).accessorKey ?? ""

  const leftPinnedIds = (() => {
    const fixed: string[] = []
    if (draggable) fixed.push("drag")
    if (enableSelection) fixed.push("select")
    if (columnFreeze === "first" && columns[0]) fixed.push(getColId(columns[0]))
    if (columnFreeze === "first-two") {
      if (columns[0]) fixed.push(getColId(columns[0]))
      if (columns[1]) fixed.push(getColId(columns[1]))
    }
    return fixed.filter(Boolean)
  })()

  // 拖拽传感器
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over } = event
      if (!over || active.id === over.id) return
      setItems((prev) => {
        const oldIndex = prev.findIndex((r) => (r as Record<string, unknown>).id === active.id)
        const newIndex = prev.findIndex((r) => (r as Record<string, unknown>).id === over.id)
        const next = arrayMove(prev, oldIndex, newIndex)
        onReorder?.(next.map((r) => (r as Record<string, unknown>).id as string))
        return next
      })
    },
    [onReorder]
  )
  // 组装列：拖拽列 + 选择列 + 数据列 + 操作列
  const allColumns: ColumnDef<TData, unknown>[] = [
    ...(draggable ? [dragHandleColumn<TData>()] : []),
    ...(enableSelection ? [selectColumn<TData>()] : []),
    ...columns,
    ...(renderRowActions ? [actionsColumn<TData>(renderRowActions, headerAction)] : [])
  ]

  const tableData = draggable ? items : data

  const table = useReactTable({
    data: tableData,
    columns: allColumns,
    defaultColumn: { size: 150, minSize: 50 },
    state: {
      sorting,
      rowSelection,
      columnSizing,
      columnPinning: {
        left: leftPinnedIds,
        right: actionColumnFixed && renderRowActions ? ["actions"] : []
      },
      // 服务端分页：把 page/pageSize 状态交给外部控制
      ...(serverPagination && {
        pagination: {
          pageIndex: serverPagination.page - 1,
          pageSize: serverPagination.pageSize
        }
      })
    },
    onSortingChange: setSorting,
    onRowSelectionChange: setRowSelection,
    onColumnSizingChange: setColumnSizing,
    enableColumnResizing: true,
    columnResizeMode: "onChange",
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    // 服务端分页：manualPagination=true，pageCount 由后端决定
    ...(serverPagination
      ? {
          manualPagination: true,
          pageCount: Math.ceil(serverPagination.total / serverPagination.pageSize),
          onPaginationChange: (updater) => {
            const prev = {
              pageIndex: serverPagination.page - 1,
              pageSize: serverPagination.pageSize
            }
            const next = typeof updater === "function" ? updater(prev) : updater
            if (next.pageIndex !== prev.pageIndex) onPageChange?.(next.pageIndex + 1)
            if (next.pageSize !== prev.pageSize) onPageSizeChange?.(next.pageSize)
          }
        }
      : { getPaginationRowModel: getPaginationRowModel() }),
    enableRowSelection: enableSelection,
    enableSorting: enableSort
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
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <Table>
            <TableHeader className="sticky top-0 z-[5] bg-card">
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id}>
                  {headerGroup.headers.map((header) => {
                    const isPinned = header.column.getIsPinned()
                    // shadow 只加在固定了数据列时的最后一个左固定列
                    const isLastLeft = isPinned === "left" && header.column.getIsLastColumn("left") && columnFreeze !== "none"
                    // 用 getStart/getAfter 计算 sticky 偏移（依赖 columnDef.size）
                    const stickyLeft = isPinned === "left" ? header.column.getStart("left") : undefined
                    const stickyRight = isPinned === "right" ? header.column.getAfter("right") : undefined
                    return (
                      <TableHead
                        key={header.id}
                        style={{
                          width: isPinned
                            ? header.getSize()
                            : header.column.columnDef.size
                            ? header.getSize()
                            : undefined,
                          minWidth: isPinned ? header.getSize() : undefined,
                          maxWidth: isPinned ? header.getSize() : undefined,
                          left: isPinned === "left" ? stickyLeft : undefined,
                          right: isPinned === "right" ? stickyRight : undefined,
                          backgroundColor: isPinned ? "var(--card)" : undefined,
                          boxShadow: isLastLeft ? "4px 0 8px -4px rgba(0,0,0,0.15)" : undefined
                        }}
                        className={cn(
                          header.column.getCanSort() ? "cursor-pointer select-none" : "",
                          isPinned && "sticky z-[6]"
                        )}
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
                    )
                  })}
                </TableRow>
              ))}
            </TableHeader>
            <SortableContext
              items={
                draggable ? tableData.map((r) => (r as Record<string, unknown>).id as string) : []
              }
              strategy={verticalListSortingStrategy}
            >
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
                  table
                    .getRowModel()
                    .rows.map((row) => (
                      <DraggableRow
                        key={row.id}
                        row={row}
                        draggable={draggable}
                        dense={dense}
                        wordWrap={wordWrap}
                        columnFreeze={columnFreeze}
                        onRowClick={onRowClick}
                      />
                    ))
                )}
              </TableBody>
            </SortableContext>
          </Table>
        </DndContext>
      </div>

      {/* 分页 */}
      <DataTablePagination
        table={table}
        dense={dense}
        onDenseChange={setDense}
        serverTotal={serverPagination?.total}
      />
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

/** 拖拽手柄列定义 */
function dragHandleColumn<TData>(): ColumnDef<TData, unknown> {
  return {
    id: "drag",
    size: 36,
    enableSorting: false,
    header: () => null,
    cell: () => null // 由 DraggableRow 直接渲染手柄
  }
}

/** 可拖拽行——拖拽模式下替换普通 TableRow */
function DraggableRow<TData>({
  row,
  draggable,
  dense,
  wordWrap,
  columnFreeze,
  onRowClick
}: {
  row: import("@tanstack/react-table").Row<TData>
  draggable: boolean
  dense: boolean
  wordWrap: boolean
  columnFreeze: "none" | "first" | "first-two"
  onRowClick?: (row: TData) => void
}) {
  const id = (row.original as Record<string, unknown>).id as string
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: id ?? row.id
  })

  const style = draggable
    ? { transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.5 : 1 }
    : undefined

  return (
    <TableRow
      ref={draggable ? setNodeRef : undefined}
      style={style}
      data-state={row.getIsSelected() && "selected"}
      className="group cursor-pointer"
      onClick={() => onRowClick?.(row.original)}
    >
      {row.getVisibleCells().map((cell) => {
        const isPinned = cell.column.getIsPinned()
        const isLastLeft = isPinned === "left" && cell.column.getIsLastColumn("left") && columnFreeze !== "none"
        const stickyLeft = isPinned === "left" ? cell.column.getStart("left") : undefined
        const stickyRight = isPinned === "right" ? cell.column.getAfter("right") : undefined

        if (cell.column.id === "drag") {
          return (
            <TableCell
              key={cell.id}
              className={cn("w-9 px-1", isPinned && "sticky z-[4]")}
              style={{
                left: isPinned === "left" ? stickyLeft : undefined,
                backgroundColor: isPinned ? "var(--card)" : undefined,
                boxShadow: isLastLeft ? "4px 0 8px -4px rgba(0,0,0,0.15)" : undefined
              }}
            >
              {draggable && (
                // biome-ignore lint/a11y/noStaticElementInteractions lint/a11y/useKeyWithClickEvents: 拖拽手柄
                <div
                  className="flex cursor-grab items-center justify-center text-muted-foreground hover:text-foreground"
                  onClick={(e) => e.stopPropagation()}
                  {...attributes}
                  {...listeners}
                >
                  <GripVertical className="size-4" />
                </div>
              )}
            </TableCell>
          )
        }
        return (
          <TableCell
            key={cell.id}
            className={cn(
              dense ? "py-1" : "",
              wordWrap ? "whitespace-normal break-words" : "whitespace-nowrap",
              isPinned && "sticky z-[4]"
            )}
            style={{
              left: isPinned === "left" ? stickyLeft : undefined,
              right: isPinned === "right" ? stickyRight : undefined,
              backgroundColor: isPinned ? "var(--card)" : undefined,
              boxShadow: isLastLeft ? "4px 0 8px -4px rgba(0,0,0,0.15)" : undefined
            }}
          >
            {flexRender(cell.column.columnDef.cell, cell.getContext())}
          </TableCell>
        )
      })}
    </TableRow>
  )
}
