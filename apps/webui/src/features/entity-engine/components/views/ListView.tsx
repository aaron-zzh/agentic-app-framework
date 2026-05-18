/**
 * 列表视图——基于 EntityDef.listView 配置渲染数据表格
 * @author AaronZZH & Kiro
 *
 * 基于 @tanstack/react-table，支持排序/分页/行选择/批量操作
 */

"use client"

import { useRouter } from "next/navigation"
import { paths } from "@/lib/constants/paths"
import { useColumnPreferences } from "@/lib/hooks/use-column-preferences"
import { useUIStore } from "@/lib/store/ui-store"
import { buildColumns } from "../../lib/build-columns"
import type { DataFieldDef, EntityDef } from "../../types"
import { ColumnConfigPanel } from "../ColumnConfigPanel"
import { registerDefaultComponents } from "../register"
import { DataTable } from "./DataTable"
import { DraggableListView } from "./DraggableListView"
import { GroupedListView } from "./GroupedListView"

registerDefaultComponents()

interface ListViewProps {
  entity: EntityDef
  data?: Record<string, unknown>[]
  loading?: boolean
}

export function ListView({ entity, data = [], loading }: ListViewProps) {
  const router = useRouter()
  const openRecordPanel = useUIStore((s) => s.openRecordPanel)
  const { visibleColumns, preferences, toggleColumn, resetColumns } = useColumnPreferences(
    entity.slug,
    entity.listView
  )

  // 字段名 → 标签映射（供列配置面板显示）
  const fieldLabels = Object.fromEntries(
    entity.fields
      .filter((f): f is DataFieldDef => "name" in f)
      .map((f) => [f.name, f.label ?? f.name])
  )

  if (loading) {
    return <ListSkeleton columns={visibleColumns.length} />
  }

  // 拖拽排序模式
  if (entity.listView.draggable) {
    const columns = visibleColumns
      .map((col) => {
        const field = entity.fields.find(
          (f) => "name" in f && (f as DataFieldDef).name === col.name
        ) as DataFieldDef | undefined
        return field ? { name: col.name, field, def: col } : null
      })
      .filter(Boolean) as {
      name: string
      field: DataFieldDef
      def: (typeof visibleColumns)[number]
    }[]
    return <DraggableListView columns={columns} data={data} />
  }

  // 分组模式
  if (entity.listView.groupBy) {
    const groupField = entity.fields.find(
      (f) => "name" in f && (f as DataFieldDef).name === entity.listView.groupBy
    ) as DataFieldDef | undefined
    const columns = visibleColumns
      .map((col) => {
        const field = entity.fields.find(
          (f) => "name" in f && (f as DataFieldDef).name === col.name
        ) as DataFieldDef | undefined
        return field ? { name: col.name, field, def: col } : null
      })
      .filter(Boolean) as {
      name: string
      field: DataFieldDef
      def: (typeof visibleColumns)[number]
    }[]
    return (
      <GroupedListView
        columns={columns}
        data={data}
        groupBy={entity.listView.groupBy}
        groupField={groupField}
      />
    )
  }

  // TanStack Table 列定义
  const tableColumns = buildColumns(entity, visibleColumns)

  const columnConfigAction = (
    <ColumnConfigPanel
      preferences={preferences}
      onToggle={toggleColumn}
      onReset={resetColumns}
      labels={fieldLabels}
    />
  )

  return (
    <DataTable
      columns={tableColumns}
      data={data}
      headerAction={columnConfigAction}
      onRowClick={(row) => {
        const id = row.id as string
        if (id) openRecordPanel(id)
      }}
      renderRowActions={(row) => (
        <div className="flex items-center gap-1">
          <button
            type="button"
            className="rounded px-2 py-0.5 text-muted-foreground text-xs hover:bg-accent hover:text-foreground"
            onClick={(e) => {
              e.stopPropagation()
              const id = row.id as string
              if (id) router.push(paths.workspace.record(entity.slug, id))
            }}
          >
            编辑
          </button>
          <span className="text-border">|</span>
          <button
            type="button"
            className="rounded px-2 py-0.5 text-destructive text-xs hover:bg-destructive/10"
            onClick={(e) => {
              e.stopPropagation()
            }}
          >
            删除
          </button>
        </div>
      )}
    />
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
