/**
 * 看板视图——基于 @dnd-kit 实现拖拽状态变更 + 列内排序 + 列排序
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```tsx
 * <KanbanView entity={taskEntity} data={records} onStatusChange={handleChange} />
 * ```
 */

"use client"

import {
  closestCorners,
  DndContext,
  type DragEndEvent,
  type DragOverEvent,
  DragOverlay,
  type DragStartEvent,
  PointerSensor,
  useSensor,
  useSensors
} from "@dnd-kit/core"
import { arrayMove, horizontalListSortingStrategy, SortableContext } from "@dnd-kit/sortable"
import { useCallback, useMemo, useState } from "react"

import type { EntityDef, SelectField, SelectOption } from "../../types"
import { KanbanCard, KanbanColumn } from "./components"

interface KanbanViewProps {
  entity: EntityDef
  data?: Record<string, unknown>[]
  loading?: boolean
  /** 拖拽完成后触发状态变更 */
  onStatusChange?: (recordId: string, newStatus: string) => void
}

/** 看板视图 */
export function KanbanView({ entity, data = [], loading, onStatusChange }: KanbanViewProps) {
  const { kanbanView, fields } = entity

  const statusField = kanbanView?.statusField ?? ""
  const cardTitle = kanbanView?.cardTitle ?? ""
  const cardDescription = kanbanView?.cardDescription

  // 获取状态字段的选项列表作为列
  const statusFieldDef = fields.find((f) => "name" in f && f.name === statusField) as
    | SelectField
    | undefined
  const allOptions: SelectOption[] = statusFieldDef?.options ?? []

  // 列顺序：优先使用 columnOrder 配置，否则按 options 定义顺序
  const [columnOrder, setColumnOrder] = useState<string[]>(
    () => kanbanView?.columnOrder ?? allOptions.map((o) => o.value)
  )

  const columns = useMemo(
    () =>
      columnOrder
        .map((v) => allOptions.find((o) => o.value === v))
        .filter(Boolean) as SelectOption[],
    [columnOrder, allOptions]
  )

  const [activeId, setActiveId] = useState<string | null>(null)
  const [activeType, setActiveType] = useState<"card" | "column" | null>(null)
  const [localData, setLocalData] = useState(data)

  // 当外部 data 变化时同步
  if (data !== localData && !activeId) {
    setLocalData(data)
  }

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const activeRecord =
    activeId && activeType === "card" ? localData.find((r) => String(r.id) === activeId) : null

  const isColumnId = useCallback((id: string) => columnOrder.includes(id), [columnOrder])

  function handleDragStart(event: DragStartEvent) {
    const id = String(event.active.id)
    setActiveId(id)
    setActiveType(isColumnId(id) ? "column" : "card")
  }

  function handleDragOver(event: DragOverEvent) {
    const { active, over } = event
    if (!over || activeType !== "card") return

    const activeRecordId = String(active.id)
    const overId = String(over.id)

    // 确定目标列
    const targetColumn = isColumnId(overId) ? overId : getRecordColumn(overId)

    const sourceColumn = getRecordColumn(activeRecordId)

    if (sourceColumn && targetColumn && sourceColumn !== targetColumn) {
      // 跨列移动：乐观更新
      setLocalData((prev) =>
        prev.map((r) =>
          String(r.id) === activeRecordId ? { ...r, [statusField]: targetColumn } : r
        )
      )
    }
  }

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    setActiveId(null)
    setActiveType(null)

    if (!over) return

    const activeIdStr = String(active.id)
    const overIdStr = String(over.id)

    // 列排序
    if (activeType === "column") {
      if (activeIdStr !== overIdStr && isColumnId(overIdStr)) {
        setColumnOrder((prev) => {
          const oldIndex = prev.indexOf(activeIdStr)
          const newIndex = prev.indexOf(overIdStr)
          return arrayMove(prev, oldIndex, newIndex)
        })
      }
      return
    }

    // 卡片拖拽完成：通知外部状态变更
    const record = localData.find((r) => String(r.id) === activeIdStr)
    if (record) {
      const currentStatus = record[statusField] as string
      const originalRecord = data.find((r) => String(r.id) === activeIdStr)
      const originalStatus = originalRecord?.[statusField] as string
      if (currentStatus !== originalStatus) {
        onStatusChange?.(activeIdStr, currentStatus)
      }
    }
  }

  function getRecordColumn(recordId: string): string | undefined {
    const record = localData.find((r) => String(r.id) === recordId)
    return record ? (record[statusField] as string) : undefined
  }

  if (!kanbanView) {
    return <p className="p-4 text-muted-foreground text-sm">未配置看板视图</p>
  }

  if (loading) {
    return <KanbanSkeleton columns={columns.length || 3} />
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      onDragStart={handleDragStart}
      onDragOver={handleDragOver}
      onDragEnd={handleDragEnd}
    >
      <SortableContext items={columnOrder} strategy={horizontalListSortingStrategy}>
        <div className="flex gap-4 overflow-x-auto p-4">
          {columns.map((col) => {
            const items = localData.filter((r) => r[statusField] === col.value)
            const itemIds = items.map((r) => String(r.id))
            return (
              <KanbanColumn
                key={col.value}
                id={col.value}
                label={col.label}
                color={col.color}
                count={items.length}
                itemIds={itemIds}
              >
                {items.map((record) => (
                  <KanbanCard
                    key={String(record.id)}
                    id={String(record.id)}
                    title={String(record[cardTitle] ?? "")}
                    description={
                      cardDescription ? String(record[cardDescription] ?? "") : undefined
                    }
                  />
                ))}
              </KanbanColumn>
            )
          })}
        </div>
      </SortableContext>

      <DragOverlay>
        {activeRecord ? (
          <KanbanCard
            id={String(activeRecord.id)}
            title={String(activeRecord[cardTitle] ?? "")}
            description={cardDescription ? String(activeRecord[cardDescription] ?? "") : undefined}
            overlay
          />
        ) : null}
      </DragOverlay>
    </DndContext>
  )
}

/** 看板骨架屏 */
function KanbanSkeleton({ columns }: { columns: number }) {
  return (
    <div className="flex gap-4 p-4">
      {Array.from({ length: columns }).map((_, i) => (
        // biome-ignore lint/suspicious/noArrayIndexKey: 骨架屏静态列表
        <div key={i} className="flex w-64 shrink-0 flex-col gap-2">
          <div className="h-6 w-20 animate-pulse rounded bg-muted" />
          <div className="h-24 animate-pulse rounded bg-muted" />
          <div className="h-24 animate-pulse rounded bg-muted" />
        </div>
      ))}
    </div>
  )
}
