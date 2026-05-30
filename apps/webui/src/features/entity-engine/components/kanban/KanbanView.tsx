/**
 * 看板视图——基于 @dnd-kit 实现拖拽状态变更 + 列内排序 + 批量拖拽 + WIP 限制 + 泳道
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
import { useCallback, useEffect, useMemo, useState } from "react"

import type { EntityDef, SelectField, SelectOption } from "../../types"
import { KanbanCard, KanbanColumn } from "./components"
import { KanbanSwimlane } from "./components/KanbanSwimlane"

interface KanbanViewProps {
  entity: EntityDef
  data?: Record<string, unknown>[]
  loading?: boolean
  /** 拖拽完成后触发状态变更 */
  onStatusChange?: (recordId: string, newStatus: string) => void
  /** 排序变更回调（recordId → 新排序值） */
  onOrderChange?: (updates: { id: string; order: number; status?: string }[]) => void
}

/** 看板视图 */
export function KanbanView({
  entity,
  data = [],
  loading,
  onStatusChange,
  onOrderChange
}: KanbanViewProps) {
  const { kanbanView, fields } = entity

  const statusField = kanbanView?.statusField ?? ""
  const cardTitle = kanbanView?.cardTitle ?? ""
  const cardDescription = kanbanView?.cardDescription
  const orderField = kanbanView?.orderField
  const swimlaneField = kanbanView?.swimlaneField
  const wipLimits = kanbanView?.wipLimits
  const wipLimitMode = kanbanView?.wipLimitMode ?? "soft"

  // 获取状态字段的选项列表作为列
  const statusFieldDef = fields.find((f) => "name" in f && f.name === statusField) as
    | SelectField
    | undefined
  const allOptions: SelectOption[] = statusFieldDef?.options ?? []

  // 列顺序
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
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())

  // 当外部 data 变化时同步（移到 useEffect 避免渲染期 setState）
  useEffect(() => {
    if (!activeId) {
      setLocalData(data)
    }
  }, [data, activeId])

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const activeRecord =
    activeId && activeType === "card" ? localData.find((r) => String(r.id) === activeId) : null

  const isColumnId = useCallback((id: string) => columnOrder.includes(id), [columnOrder])

  /** 切换卡片选中状态（批量拖拽用） */
  function toggleSelect(id: string, multi: boolean) {
    setSelectedIds((prev) => {
      const next = new Set(multi ? prev : [])
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  /** 检查 WIP 限制 */
  function checkWipLimit(columnValue: string, additionalCount = 1): boolean {
    if (!wipLimits?.[columnValue]) return true
    const currentCount = localData.filter((r) => r[statusField] === columnValue).length
    return currentCount + additionalCount <= wipLimits[columnValue]
  }

  /** 获取列内排序后的记录 */
  function getColumnItems(columnValue: string, records: Record<string, unknown>[]) {
    const items = records.filter((r) => r[statusField] === columnValue)
    if (orderField) {
      return items.sort(
        (a, b) => ((a[orderField] as number) ?? 0) - ((b[orderField] as number) ?? 0)
      )
    }
    return items
  }

  function handleDragStart(event: DragStartEvent) {
    const id = String(event.active.id)
    setActiveId(id)
    setActiveType(isColumnId(id) ? "column" : "card")
    // 如果拖拽的卡片不在选中集合中，清空选中
    if (!isColumnId(id) && !selectedIds.has(id)) {
      setSelectedIds(new Set([id]))
    }
  }

  function handleDragOver(event: DragOverEvent) {
    const { active, over } = event
    if (!over || activeType !== "card") return

    const activeRecordId = String(active.id)
    const overId = String(over.id)
    const targetColumn = isColumnId(overId) ? overId : getRecordColumn(overId)
    const sourceColumn = getRecordColumn(activeRecordId)

    if (sourceColumn && targetColumn && sourceColumn !== targetColumn) {
      // WIP 硬限制检查
      const moveCount = selectedIds.size > 1 ? selectedIds.size : 1
      if (wipLimitMode === "hard" && !checkWipLimit(targetColumn, moveCount)) return

      // 跨列移动：乐观更新（含批量选中的卡片）
      const idsToMove = selectedIds.size > 1 ? selectedIds : new Set([activeRecordId])
      setLocalData((prev) =>
        prev.map((r) => (idsToMove.has(String(r.id)) ? { ...r, [statusField]: targetColumn } : r))
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

    // 卡片拖拽完成
    const idsToMove = selectedIds.size > 1 ? selectedIds : new Set([activeIdStr])
    const orderUpdates: { id: string; order: number; status?: string }[] = []

    // 列内排序
    if (!isColumnId(overIdStr)) {
      const targetCol = getRecordColumn(overIdStr)
      if (targetCol) {
        const colItems = getColumnItems(targetCol, localData)
        const oldIndex = colItems.findIndex((r) => String(r.id) === activeIdStr)
        const newIndex = colItems.findIndex((r) => String(r.id) === overIdStr)
        if (oldIndex !== -1 && newIndex !== -1 && oldIndex !== newIndex) {
          const reordered = arrayMove(colItems, oldIndex, newIndex)
          // 更新排序值
          if (orderField) {
            const updatedData = [...localData]
            for (let i = 0; i < reordered.length; i++) {
              const idx = updatedData.findIndex((r) => r.id === reordered[i].id)
              if (idx !== -1) updatedData[idx] = { ...updatedData[idx], [orderField]: i }
              orderUpdates.push({ id: String(reordered[i].id), order: i })
            }
            setLocalData(updatedData)
          }
        }
      }
    }

    // 通知外部状态变更
    for (const id of idsToMove) {
      const record = localData.find((r) => String(r.id) === id)
      const originalRecord = data.find((r) => String(r.id) === id)
      if (record && originalRecord) {
        const currentStatus = record[statusField] as string
        const originalStatus = originalRecord[statusField] as string
        if (currentStatus !== originalStatus) {
          onStatusChange?.(id, currentStatus)
          orderUpdates.push({ id, order: 0, status: currentStatus })
        }
      }
    }

    if (orderUpdates.length > 0) onOrderChange?.(orderUpdates)
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

  /** 渲染列内容 */
  function renderColumns(records: Record<string, unknown>[]) {
    return columns.map((col) => {
      const items = getColumnItems(col.value, records)
      const itemIds = items.map((r) => String(r.id))
      const wipLimit = wipLimits?.[col.value]
      const isOverLimit = wipLimit ? items.length > wipLimit : false

      return (
        <KanbanColumn
          key={col.value}
          id={col.value}
          label={col.label}
          color={col.color}
          count={items.length}
          itemIds={itemIds}
          wipLimit={wipLimit}
          isOverLimit={isOverLimit}
          wipLimitMode={wipLimitMode}
        >
          {items.map((record) => (
            <KanbanCard
              key={String(record.id)}
              id={String(record.id)}
              title={String(record[cardTitle] ?? "")}
              description={cardDescription ? String(record[cardDescription] ?? "") : undefined}
              record={record}
              entity={entity}
              selected={selectedIds.has(String(record.id))}
              onSelect={toggleSelect}
            />
          ))}
        </KanbanColumn>
      )
    })
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
        {swimlaneField ? (
          <KanbanSwimlane
            data={localData}
            swimlaneField={swimlaneField}
            fields={fields}
            renderColumns={renderColumns}
          />
        ) : (
          <div className="flex gap-4 overflow-x-auto p-4">{renderColumns(localData)}</div>
        )}
      </SortableContext>

      <DragOverlay>
        {activeRecord ? (
          <KanbanCard
            id={String(activeRecord.id)}
            title={String(activeRecord[cardTitle] ?? "")}
            description={cardDescription ? String(activeRecord[cardDescription] ?? "") : undefined}
            record={activeRecord}
            entity={entity}
            overlay
          />
        ) : null}
        {/* 批量拖拽指示 */}
        {activeRecord && selectedIds.size > 1 && (
          <div className="absolute -top-2 -right-2 flex size-5 items-center justify-center rounded-full bg-primary text-primary-foreground text-xs">
            {selectedIds.size}
          </div>
        )}
      </DragOverlay>
    </DndContext>
  )
}

/** 看板骨架屏 */
function KanbanSkeleton({ columns }: { columns: number }) {
  return (
    <div className="flex gap-4 p-4">
      {Array.from({ length: columns }).map((_, i) => (
        <div key={i} className="flex w-64 shrink-0 flex-col gap-2">
          <div className="h-6 w-20 animate-pulse rounded bg-muted" />
          <div className="h-24 animate-pulse rounded bg-muted" />
          <div className="h-24 animate-pulse rounded bg-muted" />
        </div>
      ))}
    </div>
  )
}
