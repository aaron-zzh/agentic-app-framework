/**
 * 看板视图——基于 @dnd-kit 实现拖拽状态变更
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
  DragOverlay,
  type DragStartEvent,
  PointerSensor,
  useSensor,
  useSensors
} from "@dnd-kit/core"
import { useState } from "react"

import type { EntityDef, SelectField, SelectOption } from "../../types"
import { KanbanCard } from "./KanbanCard"
import { KanbanColumn } from "./KanbanColumn"

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
  const columns: SelectOption[] = statusFieldDef?.options ?? []

  const [activeId, setActiveId] = useState<string | null>(null)
  const [localData, setLocalData] = useState(data)

  // 当外部 data 变化时同步
  if (data !== localData && !activeId) {
    setLocalData(data)
  }

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const activeRecord = activeId ? localData.find((r) => String(r.id) === activeId) : null

  if (!kanbanView) {
    return <p className="p-4 text-muted-foreground text-sm">未配置看板视图</p>
  }

  function handleDragStart(event: DragStartEvent) {
    setActiveId(String(event.active.id))
  }

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    setActiveId(null)

    if (!over) return

    const recordId = String(active.id)
    const newStatus = String(over.id)
    const record = localData.find((r) => String(r.id) === recordId)

    if (!record || record[statusField] === newStatus) return

    // 乐观更新
    setLocalData((prev) =>
      prev.map((r) => (String(r.id) === recordId ? { ...r, [statusField]: newStatus } : r))
    )
    onStatusChange?.(recordId, newStatus)
  }

  if (loading) {
    return <KanbanSkeleton columns={columns.length || 3} />
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      onDragStart={handleDragStart}
      onDragEnd={handleDragEnd}
    >
      <div className="flex gap-4 overflow-x-auto p-4">
        {columns.map((col) => {
          const items = localData.filter((r) => r[statusField] === col.value)
          return (
            <KanbanColumn
              key={col.value}
              id={col.value}
              label={col.label}
              color={col.color}
              count={items.length}
            >
              {items.map((record) => (
                <KanbanCard
                  key={String(record.id)}
                  id={String(record.id)}
                  title={String(record[cardTitle] ?? "")}
                  description={cardDescription ? String(record[cardDescription] ?? "") : undefined}
                />
              ))}
            </KanbanColumn>
          )
        })}
      </div>

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
