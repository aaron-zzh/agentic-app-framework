/**
 * DraggableListView——行拖拽排序（listView.draggable: true 时启用）
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useState } from "react"
import {
  DndContext,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core"
import {
  SortableContext,
  verticalListSortingStrategy,
  useSortable,
  arrayMove,
} from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"

import { getCellComponent } from "../../lib/component-registry"
import type { ColumnDef, DataFieldDef } from "../../types"

type ColumnInfo = { name: string; field: DataFieldDef; def: ColumnDef }

interface DraggableListViewProps {
  columns: ColumnInfo[]
  data: Record<string, unknown>[]
  onReorder?: (ids: string[]) => void
}

/** 可拖拽排序列表 */
export function DraggableListView({ columns, data, onReorder }: DraggableListViewProps) {
  const [items, setItems] = useState(data)
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over } = event
      if (!over || active.id === over.id) return

      setItems((prev) => {
        const oldIndex = prev.findIndex((r) => (r.id as string) === active.id)
        const newIndex = prev.findIndex((r) => (r.id as string) === over.id)
        const next = arrayMove(prev, oldIndex, newIndex)
        onReorder?.(next.map((r) => r.id as string))
        return next
      })
    },
    [onReorder]
  )

  const ids = items.map((r) => r.id as string)

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <div className="w-full overflow-auto">
        <table className="w-full caption-bottom text-sm">
          <thead className="border-b">
            <tr>
              <th className="w-8" />
              {columns.map((col) => (
                <th
                  key={col.name}
                  className="h-10 px-4 text-left align-middle font-medium text-muted-foreground"
                  style={col.def.width ? { width: col.def.width } : undefined}
                >
                  {col.field.label ?? col.name}
                </th>
              ))}
            </tr>
          </thead>
          <SortableContext items={ids} strategy={verticalListSortingStrategy}>
            <tbody>
              {items.map((record) => (
                <SortableRow key={record.id as string} id={record.id as string} record={record} columns={columns} />
              ))}
            </tbody>
          </SortableContext>
        </table>
      </div>
    </DndContext>
  )
}

/** 可排序行 */
function SortableRow({ id, record, columns }: { id: string; record: Record<string, unknown>; columns: ColumnInfo[] }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  }

  return (
    <tr ref={setNodeRef} style={style} className="border-b transition-colors hover:bg-muted/50">
      <td className="w-8 cursor-grab px-2 text-muted-foreground" {...attributes} {...listeners}>
        ⠿
      </td>
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
  )
}
