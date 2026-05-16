/**
 * 列表视图——基于 EntityDef.listView 配置渲染数据表格
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```tsx
 * <ListView entity={entityDef} data={records} loading={isLoading} />
 * ```
 */

"use client"

import { useVirtualizer } from "@tanstack/react-virtual"
import { useRef } from "react"
import { useColumnPreferences } from "@/lib/hooks/use-column-preferences"
import { getCellComponent } from "../../lib/component-registry"
import type { ColumnDef, DataFieldDef, EntityDef } from "../../types"
import { ColumnConfigPanel } from "../ColumnConfigPanel"
import { DraggableListView } from "./DraggableListView"
import { GroupedListView } from "./GroupedListView"

/** 虚拟滚动启用阈值 */
const VIRTUAL_THRESHOLD = 100
const ROW_HEIGHT = 40

interface ListViewProps {
  entity: EntityDef
  data?: Record<string, unknown>[]
  loading?: boolean
}

export function ListView({ entity, data = [], loading }: ListViewProps) {
  const { fields, listView } = entity
  const { visibleColumns, preferences, toggleColumn, resetColumns } = useColumnPreferences(
    entity.slug,
    listView
  )

  // 字段名 → 标签映射（供列配置面板显示）
  const fieldLabels = Object.fromEntries(
    fields.filter((f): f is DataFieldDef => "name" in f).map((f) => [f.name, f.label ?? f.name])
  )

  const columns = visibleColumns
    .map((col) => {
      const field = fields.find((f) => "name" in f && (f as { name: string }).name === col.name)
      return field && "name" in field
        ? { name: col.name, field: field as DataFieldDef, def: col }
        : null
    })
    .filter(Boolean) as { name: string; field: DataFieldDef; def: ColumnDef }[]

  if (loading) {
    return <ListSkeleton columns={columns.length} />
  }

  if (data.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-muted-foreground">
        <p className="text-sm">暂无数据</p>
      </div>
    )
  }

  // 拖拽排序模式
  if (entity.listView.draggable) {
    return <DraggableListView columns={columns} data={data} />
  }

  // 分组模式
  if (entity.listView.groupBy) {
    const groupField = entity.fields.find(
      (f) => "name" in f && (f as DataFieldDef).name === entity.listView.groupBy
    ) as DataFieldDef | undefined
    return (
      <GroupedListView
        columns={columns}
        data={data}
        groupBy={entity.listView.groupBy}
        groupField={groupField}
      />
    )
  }

  const useVirtual = data.length > VIRTUAL_THRESHOLD

  if (useVirtual) {
    return <VirtualTable columns={columns} data={data} />
  }

  return (
    <div className="w-full overflow-auto">
      <table className="w-full caption-bottom text-sm">
        <thead className="border-b">
          <tr>
            {columns.map((col) => (
              <th
                key={col.name}
                className="h-10 px-4 text-left align-middle font-medium text-muted-foreground"
                style={col.def.width ? { width: col.def.width } : undefined}
              >
                {col.field.label ?? col.name}
              </th>
            ))}
            <th className="h-10 w-8 px-1 text-right align-middle">
              <ColumnConfigPanel
                preferences={preferences}
                onToggle={toggleColumn}
                onReset={resetColumns}
                labels={fieldLabels}
              />
            </th>
          </tr>
        </thead>
        <tbody>
          {data.map((record, i) => (
            <tr
              key={(record.id as string) ?? i}
              className="border-b transition-colors hover:bg-muted/50"
            >
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
              <td className="w-8" />
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

type ColumnInfo = { name: string; field: DataFieldDef; def: ColumnDef }

/** 虚拟滚动表格（数据量 > 100 时自动启用） */
function VirtualTable({
  columns,
  data
}: {
  columns: ColumnInfo[]
  data: Record<string, unknown>[]
}) {
  const parentRef = useRef<HTMLDivElement>(null)

  const virtualizer = useVirtualizer({
    count: data.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 10
  })

  return (
    <div
      ref={parentRef}
      className="w-full overflow-auto"
      style={{ maxHeight: "calc(100vh - 200px)" }}
    >
      <table className="w-full caption-bottom text-sm">
        <thead className="sticky top-0 z-10 border-b bg-background">
          <tr>
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
        <tbody style={{ height: `${virtualizer.getTotalSize()}px`, position: "relative" }}>
          {virtualizer.getVirtualItems().map((virtualRow) => {
            const record = data[virtualRow.index]
            return (
              <tr
                key={(record.id as string) ?? virtualRow.index}
                className="border-b transition-colors hover:bg-muted/50"
                style={{
                  position: "absolute",
                  top: 0,
                  left: 0,
                  width: "100%",
                  height: `${virtualRow.size}px`,
                  transform: `translateY(${virtualRow.start}px)`
                }}
              >
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
          })}
        </tbody>
      </table>
    </div>
  )
}

/** 列表骨架屏 */
function ListSkeleton({ columns, rows = 5 }: { columns: number; rows?: number }) {
  return (
    <div className="w-full space-y-2 p-4">
      {Array.from({ length: rows }).map((_, i) => (
        // biome-ignore lint/suspicious/noArrayIndexKey: 骨架屏静态列表
        <div key={i} className="flex gap-4">
          {Array.from({ length: columns }).map((_, j) => (
            // biome-ignore lint/suspicious/noArrayIndexKey: 骨架屏静态列表
            <div key={j} className="h-8 flex-1 animate-pulse rounded bg-muted" />
          ))}
        </div>
      ))}
    </div>
  )
}
