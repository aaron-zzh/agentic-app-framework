/**
 * 列表视图——基于 EntityDef.listView 配置渲染数据表格
 * @author AaronZZH & Kiro
 *
 * 基于 @tanstack/react-table，支持排序/分页/行选择/批量操作
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import type { OnChangeFn, SortingState } from "@tanstack/react-table"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { toast } from "sonner"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { fromEntityDef, useCrudDelete } from "@/lib/api/rest/crud"
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
  sorting: SortingState
  onSortingChange: OnChangeFn<SortingState>
  sortableFields: string[]
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
  sorting,
  onSortingChange,
  sortableFields,
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
    // 骨架用于提示加载而非模拟整页数据；页大小可达 20/50/100，必须限制高度避免刷新时撑满视口。
    const skeletonRows = Math.min(
      serverPagination?.pageSize ?? DEFAULT_LOCAL_PAGE_SIZE,
      MAX_SKELETON_ROWS
    )
    return <ListSkeleton columns={visibleColumns.length} rows={skeletonRows} />
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
  const tableColumns = buildColumns(entity, visibleColumns, sortableFields)

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
      sorting={sorting}
      onSortingChange={onSortingChange}
      draggable={effectiveDraggable}
      serverPagination={serverPagination}
      onPageChange={onPageChange}
      onPageSizeChange={onPageSizeChange}
      wordWrap={viewSettings?.wordWrap ?? false}
      columnFreeze={viewSettings?.columnFreeze ?? "none"}
      actionColumnFixed={viewSettings?.actionColumnFixed ?? true}
      rowDragItem={(row) => {
        const r = row as Record<string, unknown>
        const id = r.id as string
        if (!id) return undefined as never
        const title = (r.name ?? r.title ?? id) as string
        return { id, title: `${entity.label}: ${title}`, entity: entity.slug }
      }}
      onRowClick={(row) => {
        const id = row.id as string
        if (!id) return
        const action = viewSettings?.rowClickAction ?? "panel"
        if (action === "panel") openRecordPanel(id, "panel", queryToken)
        else if (action === "drawer") openRecordPanel(id, "drawer", queryToken)
        else if (action === "detail") router.push(recordHref(entity.slug, id, queryToken))
      }}
      renderRowActions={
        entity.access?.update !== false || entity.access?.delete !== false
          ? (row) => <RowActions row={row} entity={entity} queryToken={queryToken} />
          : undefined
      }
    />
  )
}

/** 行操作（含拖放到对话） */
function RowActions({
  row,
  entity,
  queryToken
}: {
  row: Record<string, unknown>
  entity: EntityDef
  queryToken?: string
}) {
  const [isDeleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const router = useRouter()
  const queryClient = useQueryClient()
  const recordPanelId = useUIStore((s) => s.recordPanelId)
  const closeRecordPanel = useUIStore((s) => s.closeRecordPanel)
  const { mutate: remove, isPending: isDeleting } = useCrudDelete(fromEntityDef(entity))
  const id = row.id ? String(row.id) : ""
  const title = String(row.name ?? row.title ?? id)

  const handleDelete = () => {
    if (!id) return
    remove(
      { id },
      {
        onSuccess: () => {
          queryClient.removeQueries({ queryKey: [entity.slug, "detail"] })
          queryClient.invalidateQueries({ queryKey: [entity.slug, "queryWindow"] })
          if (recordPanelId === id) closeRecordPanel()
          toast.success(`${entity.label}已删除`)
        }
        //onError: () => toast.error(`${entity.label}删除失败，请稍后重试`)
      }
    )
  }

  return (
    <>
      <div className="flex items-center gap-1">
        {entity.access?.update !== false && (
          <button
            type="button"
            className="rounded px-2 py-0.5 text-muted-foreground text-xs hover:bg-accent hover:text-foreground"
            onClick={(e) => {
              e.stopPropagation()
              if (id) router.push(recordHref(entity.slug, id, queryToken))
            }}
          >
            编辑
          </button>
        )}
        {entity.access?.update !== false && entity.access?.delete !== false && (
          <span className="text-border">|</span>
        )}
        {entity.access?.delete !== false && (
          <button
            type="button"
            className="rounded px-2 py-0.5 text-destructive text-xs hover:bg-destructive/10"
            onClick={(e) => {
              e.stopPropagation()
              if (id) setDeleteDialogOpen(true)
            }}
          >
            删除
          </button>
        )}
      </div>
      <ConfirmDialog
        open={isDeleteDialogOpen}
        onOpenChange={setDeleteDialogOpen}
        title={`删除${entity.label}`}
        description={`确定要删除「${title}」吗？`}
        confirmText={isDeleting ? "删除中..." : "删除"}
        variant="destructive"
        onConfirm={handleDelete}
      />
    </>
  )
}

function recordHref(entitySlug: string, id: string, queryToken?: string) {
  const base = paths.workspace.record(entitySlug, id)
  return queryToken ? `${base}?qw=${encodeURIComponent(queryToken)}` : base
}

/** 本地分页模式下 TanStack Table 的默认页大小，需与 DataTable 未显式设置 pageSize 时的内部默认值保持一致 */
const DEFAULT_LOCAL_PAGE_SIZE = 10
/** 加载态最多展示六行，避免服务端大页大小导致骨架屏撑满列表视口。 */
const MAX_SKELETON_ROWS = 6

/** 列表骨架屏 */
function ListSkeleton({
  columns,
  rows = DEFAULT_LOCAL_PAGE_SIZE
}: {
  columns: number
  rows?: number
}) {
  return (
    <div className="w-full space-y-2 p-4">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} data-slot="list-skeleton-row" className="flex gap-4">
          {Array.from({ length: columns }).map((_, j) => (
            <div key={j} className="h-8 flex-1 animate-pulse rounded bg-muted" />
          ))}
        </div>
      ))}
    </div>
  )
}
