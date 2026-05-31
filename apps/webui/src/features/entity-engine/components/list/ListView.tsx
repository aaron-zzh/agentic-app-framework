/**
 * 列表视图——基于 EntityDef.listView 配置渲染数据表格
 * @author AaronZZH & Kiro
 *
 * 基于 @tanstack/react-table，支持排序/分页/行选择/批量操作
 */

"use client"

import { useRouter } from "next/navigation"
import { useSemanticDraggable } from "@/features/chatter/dnd/useSemanticDraggable"
import { paths } from "@/lib/constants/paths"
import { useColumnPreferences } from "@/lib/hooks/use-column-preferences"
import { useUIStore } from "@/lib/store/ui-store"
import { buildColumns } from "../../lib/build-columns"
import type { DataFieldDef, EntityDef } from "../../types"
import { registerDefaultComponents } from "../register"
import type { ViewSettings } from "./components"
import { ColumnConfigPanel, DataTable, GroupedListView } from "./components"

// 确保字段组件已注册（幂等，重复调用无副作用）
registerDefaultComponents()

interface ListViewProps {
  entity: EntityDef
  data?: Record<string, unknown>[]
  loading?: boolean
  /** 视图设置（覆盖 EntityDef.listView 中的对应字段） */
  viewSettings?: ViewSettings
  /** 服务端分页信息（有值时启用服务端分页模式） */
  serverPagination?: { page: number; pageSize: number; total: number }
  onPageChange?: (page: number) => void
  onPageSizeChange?: (pageSize: number) => void
  queryToken?: string
}

export function ListView({
  entity,
  data = [],
  loading,
  viewSettings,
  serverPagination,
  onPageChange,
  onPageSizeChange,
  queryToken
}: ListViewProps) {
  const router = useRouter()
  const openRecordPanel = useUIStore((s) => s.openRecordPanel)
  const {
    visibleColumns: defaultVisibleColumns,
    preferences,
    toggleColumn,
    resetColumns
  } = useColumnPreferences(entity.slug, entity.listView)

  // viewSettings.columns 存在时，用它覆盖 useColumnPreferences 的结果
  const visibleColumns = (() => {
    const settingsCols = viewSettings?.columns
    if (!settingsCols?.length) return defaultVisibleColumns
    const allFieldDefs = entity.fields.filter(
      (f): f is import("../../types").DataFieldDef => "name" in f
    )
    return [...settingsCols]
      .filter((c) => c.visible)
      .sort((a, b) => a.order - b.order)
      .map((c) => {
        const field = allFieldDefs.find((f) => f.name === c.name)
        if (!field) return null
        // 转为 ColumnDef 格式，用户设置的 width 优先
        const listCol = entity.listView.columns.find((col) =>
          typeof col === "string" ? col === c.name : col.name === c.name
        )
        const base = typeof listCol === "string" ? { name: listCol } : (listCol ?? { name: c.name })
        return c.width ? { ...base, width: String(c.width) } : base
      })
      .filter((c): c is NonNullable<typeof c> => c !== null)
  })()

  // viewSettings 覆盖 EntityDef.listView 中的对应字段
  // 拖拽排序必须有 orderField 才能开启
  const effectiveDraggable =
    !!entity.listView.orderField && (viewSettings?.draggable ?? entity.listView.draggable ?? false)
  const effectiveGroupBy = viewSettings?.groupBy ?? entity.listView.groupBy
  // enableSort 默认 true，viewSettings 可关闭
  const enableSort = viewSettings?.enableSort ?? true

  // 字段名 → 标签映射（供列配置面板显示）
  const fieldLabels = Object.fromEntries(
    entity.fields
      .filter((f): f is DataFieldDef => "name" in f)
      .map((f) => [f.name, f.label ?? f.name])
  )

  if (loading) {
    return <ListSkeleton columns={visibleColumns.length} />
  }

  // 分组模式
  if (effectiveGroupBy) {
    const groupField = entity.fields.find(
      (f) => "name" in f && (f as DataFieldDef).name === effectiveGroupBy
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
        groupBy={effectiveGroupBy}
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
      enableSort={enableSort}
      draggable={effectiveDraggable}
      serverPagination={serverPagination}
      onPageChange={onPageChange}
      onPageSizeChange={onPageSizeChange}
      wordWrap={viewSettings?.wordWrap ?? false}
      columnFreeze={viewSettings?.columnFreeze ?? "none"}
      actionColumnFixed={viewSettings?.actionColumnFixed ?? true}
      onRowClick={(row) => {
        const id = row.id as string
        if (!id) return
        const action = viewSettings?.rowClickAction ?? "panel"
        if (action === "panel") openRecordPanel(id, "panel")
        else if (action === "drawer") openRecordPanel(id, "drawer")
        else if (action === "detail") router.push(recordHref(entity.slug, id, queryToken))
      }}
      renderRowActions={(row) => (
        <RowActions
          row={row}
          entitySlug={entity.slug}
          entityLabel={entity.label}
          queryToken={queryToken}
        />
      )}
    />
  )
}

/** 行操作（含拖放到对话） */
function RowActions({
  row,
  entitySlug,
  entityLabel,
  queryToken
}: {
  row: Record<string, unknown>
  entitySlug: string
  entityLabel: string
  queryToken?: string
}) {
  const router = useRouter()
  const id = row.id as string
  const title = (row.name ?? row.title ?? id) as string

  const { ref, listeners, attributes, isDragging } = useSemanticDraggable({
    id: `row-${entitySlug}-${id}`,
    item: {
      type: "record",
      id,
      title: `${entityLabel}: ${title}`,
      semantics: { componentName: "ListView", entity: entitySlug }
    }
  })

  return (
    <div className="flex items-center gap-1">
      <span
        ref={ref}
        {...listeners}
        {...attributes}
        className="cursor-grab rounded px-1 py-0.5 text-muted-foreground text-xs hover:bg-accent hover:text-foreground"
        style={{ opacity: isDragging ? 0.5 : 1 }}
        title="拖放到对话"
      >
        ⋮⋮
      </span>
      <button
        type="button"
        className="rounded px-2 py-0.5 text-muted-foreground text-xs hover:bg-accent hover:text-foreground"
        onClick={(e) => {
          e.stopPropagation()
          if (id) router.push(recordHref(entitySlug, id, queryToken))
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
  )
}

function recordHref(entitySlug: string, id: string, queryToken?: string) {
  const base = paths.workspace.record(entitySlug, id)
  return queryToken ? `${base}?qw=${encodeURIComponent(queryToken)}` : base
}

/** 列表骨架屏 */
function ListSkeleton({ columns, rows = 5 }: { columns: number; rows?: number }) {
  return (
    <div className="w-full space-y-2 p-4">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="flex gap-4">
          {Array.from({ length: columns }).map((_, j) => (
            <div key={j} className="h-8 flex-1 animate-pulse rounded bg-muted" />
          ))}
        </div>
      ))}
    </div>
  )
}
