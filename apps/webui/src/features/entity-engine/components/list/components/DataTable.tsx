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
  type DraggableAttributes,
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
  type Cell,
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  type Header,
  type PaginationState,
  type Row,
  type RowSelectionState,
  type SortingState,
  type Updater,
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
import { useSemanticDraggable } from "@/features/chatter/dnd/useSemanticDraggable"
import { cn } from "@/lib/utils/cn"
import { DataTablePagination } from "./DataTablePagination"

interface DataTableProps<TData> {
  columns: ColumnDef<TData, unknown>[]
  data: TData[]
  onRowClick?: (row: TData) => void
  onBatchDelete?: (rows: TData[]) => void
  renderRowActions?: (row: TData) => React.ReactNode
  headerAction?: React.ReactNode
  enableSelection?: boolean
  enableSort?: boolean
  draggable?: boolean
  onReorder?: (ids: string[]) => void
  serverPagination?: { page: number; pageSize: number; total: number }
  onPageChange?: (page: number) => void
  onPageSizeChange?: (pageSize: number) => void
  wordWrap?: boolean
  columnFreeze?: "none" | "first" | "first-two"
  actionColumnFixed?: boolean
  /** 整行拖到对话的 item 描述，有值时整行可拖放到聊天 */
  rowDragItem?: (row: TData) => { id: string; title: string; entity: string }
}

/** 计算左固定列 id 列表 */
function buildLeftPinnedIds<TData>(
  columns: ColumnDef<TData, unknown>[],
  draggable: boolean,
  enableSelection: boolean,
  columnFreeze: "none" | "first" | "first-two"
): string[] {
  const getColId = (col: ColumnDef<TData, unknown>) =>
    col.id ?? (col as { accessorKey?: string }).accessorKey ?? ""
  const fixed: string[] = []
  if (draggable) fixed.push("drag")
  if (enableSelection) fixed.push("select")
  if (columnFreeze === "first" && columns[0]) fixed.push(getColId(columns[0]))
  if (columnFreeze === "first-two") {
    if (columns[0]) fixed.push(getColId(columns[0]))
    if (columns[1]) fixed.push(getColId(columns[1]))
  }
  return fixed.filter(Boolean)
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
  actionColumnFixed = true,
  rowDragItem
}: DataTableProps<TData>) {
  const [sorting, setSorting] = useState<SortingState>([])
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})
  const [columnSizing, setColumnSizing] = useState({})
  const [items, setItems] = useState<TData[]>(data)
  const { value: dense, setValue: setDense } = useBoolean(false)

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

  const allColumns: ColumnDef<TData, unknown>[] = [
    ...(draggable ? [dragHandleColumn<TData>()] : []),
    ...(enableSelection ? [selectColumn<TData>()] : []),
    ...columns,
    ...(renderRowActions ? [actionsColumn<TData>(renderRowActions, headerAction)] : [])
  ]

  const tableData = draggable ? items : data
  const leftPinnedIds = buildLeftPinnedIds(columns, draggable, enableSelection, columnFreeze)

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
      ...(serverPagination && {
        pagination: { pageIndex: serverPagination.page - 1, pageSize: serverPagination.pageSize }
      })
    },
    onSortingChange: setSorting,
    onRowSelectionChange: setRowSelection,
    onColumnSizingChange: setColumnSizing,
    enableColumnResizing: true,
    columnResizeMode: "onChange",
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    enableRowSelection: enableSelection,
    enableSorting: enableSort,
    ...(serverPagination
      ? {
          manualPagination: true,
          pageCount: Math.ceil(serverPagination.total / serverPagination.pageSize),
          onPaginationChange: (updater: Updater<PaginationState>) => {
            const prev = {
              pageIndex: serverPagination.page - 1,
              pageSize: serverPagination.pageSize
            }
            const next = typeof updater === "function" ? updater(prev) : updater
            if (next.pageIndex !== prev.pageIndex) onPageChange?.(next.pageIndex + 1)
            if (next.pageSize !== prev.pageSize) onPageSizeChange?.(next.pageSize)
          }
        }
      : { getPaginationRowModel: getPaginationRowModel() })
  })

  const selectedRows = table.getFilteredSelectedRowModel().rows
  const hasSelection = selectedRows.length > 0

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      <div className="relative flex-1 overflow-auto">
        {hasSelection && (
          <BatchActionBar
            count={selectedRows.length}
            allSelected={table.getIsAllPageRowsSelected()}
            someSelected={table.getIsSomePageRowsSelected()}
            onToggleAll={(checked) => table.toggleAllPageRowsSelected(checked)}
            onDelete={() => onBatchDelete?.(selectedRows.map((r) => r.original))}
            onClear={() => setRowSelection({})}
          />
        )}
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <Table>
            <TableHeader className="sticky top-0 z-[5] bg-card">
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id}>
                  {headerGroup.headers.map((header) => (
                    <DataTableHead key={header.id} header={header} columnFreeze={columnFreeze} />
                  ))}
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
                        rowDragItem={rowDragItem}
                      />
                    ))
                )}
              </TableBody>
            </SortableContext>
          </Table>
        </DndContext>
      </div>
      <DataTablePagination
        table={table}
        dense={dense}
        onDenseChange={setDense}
        serverTotal={serverPagination?.total}
      />
    </div>
  )
}

/** 批量操作浮层 */
function BatchActionBar({
  count,
  allSelected,
  someSelected,
  onToggleAll,
  onDelete,
  onClear
}: {
  count: number
  allSelected: boolean
  someSelected: boolean
  onToggleAll: (checked: boolean) => void
  onDelete: () => void
  onClear: () => void
}) {
  return (
    <div className="absolute inset-x-0 top-0 z-10 flex h-10 items-center gap-3 border-b bg-accent px-2">
      <Checkbox
        checked={allSelected}
        indeterminate={someSelected}
        onCheckedChange={(checked) => onToggleAll(!!checked)}
        onClick={(e) => e.stopPropagation()}
      />
      <span className="font-medium text-primary text-sm">已选 {count} 项</span>
      <div className="flex-1" />
      <Button
        variant="ghost"
        size="sm"
        className="text-destructive hover:text-destructive"
        onClick={onDelete}
      >
        <Trash2 className="mr-1 size-4" />
        删除
      </Button>
      <Button variant="ghost" size="sm" onClick={onClear}>
        取消选择
      </Button>
    </div>
  )
}

/** 表头单元格 */
function DataTableHead<TData>({
  header,
  columnFreeze
}: {
  header: Header<TData, unknown>
  columnFreeze: "none" | "first" | "first-two"
}) {
  const isPinned = header.column.getIsPinned()
  const isLastLeft =
    isPinned === "left" && header.column.getIsLastColumn("left") && columnFreeze !== "none"
  const stickyLeft = isPinned === "left" ? header.column.getStart("left") : undefined
  const stickyRight = isPinned === "right" ? header.column.getAfter("right") : undefined

  return (
    <TableHead
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
        {header.column.getCanSort() && <SortIcon direction={header.column.getIsSorted()} />}
      </div>
    </TableHead>
  )
}

/** 可拖拽行 */
function DraggableRow<TData>({
  row,
  draggable,
  dense,
  wordWrap,
  columnFreeze,
  onRowClick,
  rowDragItem
}: {
  row: Row<TData>
  draggable: boolean
  dense: boolean
  wordWrap: boolean
  columnFreeze: "none" | "first" | "first-two"
  onRowClick?: (row: TData) => void
  rowDragItem?: (row: TData) => { id: string; title: string; entity: string }
}) {
  const id = (row.original as Record<string, unknown>).id as string
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: id ?? row.id
  })
  const style = draggable
    ? { transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.5 : 1 }
    : undefined

  const dragItem = rowDragItem?.(row.original)
  const {
    ref: semanticRef,
    listeners: semanticListeners,
    attributes: semanticAttributes
  } = useSemanticDraggable({
    id: `row-${id}`,
    disabled: !dragItem,
    item: {
      type: "record",
      id: dragItem?.id ?? "",
      title: dragItem?.title ?? "",
      semantics: { componentName: "ListView", entity: dragItem?.entity ?? "" }
    }
  })

  return (
    <TableRow
      ref={(node) => {
        if (draggable) setNodeRef(node)
        if (dragItem) (semanticRef as React.RefCallback<HTMLElement>)(node)
      }}
      style={style}
      data-state={row.getIsSelected() && "selected"}
      className="group cursor-pointer"
      onClick={() => onRowClick?.(row.original)}
      {...(dragItem ? { ...semanticAttributes, ...semanticListeners } : {})}
    >
      {row.getVisibleCells().map((cell) => (
        <DataTableCell
          key={cell.id}
          cell={cell}
          draggable={draggable}
          dense={dense}
          wordWrap={wordWrap}
          columnFreeze={columnFreeze}
          dragHandleProps={{ attributes, listeners }}
        />
      ))}
    </TableRow>
  )
}

/** 表格单元格（含拖拽手柄特殊处理） */
function DataTableCell<TData>({
  cell,
  draggable,
  dense,
  wordWrap,
  columnFreeze,
  dragHandleProps
}: {
  cell: Cell<TData, unknown>
  draggable: boolean
  dense: boolean
  wordWrap: boolean
  columnFreeze: "none" | "first" | "first-two"
  dragHandleProps: {
    attributes: DraggableAttributes
    listeners: ReturnType<typeof useSortable>["listeners"]
  }
}) {
  const isPinned = cell.column.getIsPinned()
  const isLastLeft =
    isPinned === "left" && cell.column.getIsLastColumn("left") && columnFreeze !== "none"
  const stickyLeft = isPinned === "left" ? cell.column.getStart("left") : undefined
  const stickyRight = isPinned === "right" ? cell.column.getAfter("right") : undefined
  const pinnedStyle = {
    left: isPinned === "left" ? stickyLeft : undefined,
    right: isPinned === "right" ? stickyRight : undefined,
    backgroundColor: isPinned ? "var(--card)" : undefined,
    boxShadow: isLastLeft ? "4px 0 8px -4px rgba(0,0,0,0.15)" : undefined
  }

  if (cell.column.id === "drag") {
    return (
      <TableCell className={cn("w-9 px-1", isPinned && "sticky z-[4]")} style={pinnedStyle}>
        {draggable && (
          // biome-ignore lint/a11y/noStaticElementInteractions lint/a11y/useKeyWithClickEvents: 拖拽手柄
          <div
            className="flex cursor-grab items-center justify-center text-muted-foreground hover:text-foreground"
            onClick={(e) => e.stopPropagation()}
            {...dragHandleProps.attributes}
            {...dragHandleProps.listeners}
          >
            <GripVertical className="size-4" />
          </div>
        )}
      </TableCell>
    )
  }

  return (
    <TableCell
      className={cn(
        dense ? "py-1" : "",
        wordWrap ? "whitespace-normal break-words" : "whitespace-nowrap",
        isPinned && "sticky z-[4]"
      )}
      style={pinnedStyle}
    >
      {flexRender(cell.column.columnDef.cell, cell.getContext())}
    </TableCell>
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
    cell: () => null
  }
}
