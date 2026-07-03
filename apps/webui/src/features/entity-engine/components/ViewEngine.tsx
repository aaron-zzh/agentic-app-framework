/**
 * ViewEngine 核心渲染器——根据 URL 参数选择视图渲染器
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```tsx
 * // 在动态路由页面中
 * const entity = entityRegistry.get(params.module)
 * <ViewEngine entity={entity} view={searchParams.view} />
 * ```
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { useRouter } from "next/navigation"
import { toast } from "sonner"
import { ViewErrorBoundary } from "@/components/common/ViewErrorBoundary"
import { fromEntityDef, useCrudUpdate } from "@/lib/api/rest/crud"
import type { PageResult } from "@/lib/api/rest/entity/crud"
import { paths } from "@/lib/constants/paths"
import { useEntityAccess } from "@/lib/queries/use-entity-access"
import { useEntityDetail } from "@/lib/queries/use-entity-detail"
import { useEntityList } from "@/lib/queries/use-entity-list"
import { useEntityQueryWindow } from "@/lib/queries/use-entity-query-window"
import { useEntitySearchParams } from "@/lib/queries/use-entity-search-params"
import { useFilterParams } from "@/lib/queries/use-filter-params"
import { useResolvedEntity } from "../hooks/use-resolved-entity"
import { getViewComponent } from "../lib/component-registry"
import type { EntityDef } from "../types"
import { CalendarView } from "./calendar"
import { CanvasView } from "./canvas"
import { EntityApproval } from "./EntityApproval"
import { FormView } from "./form"
import { KanbanView } from "./kanban"
import type { ViewSettings } from "./list"
import { ListView } from "./list"
import { PivotView } from "./pivot"

/** 支持的视图类型 */
export type ViewType =
  | "list"
  | "form"
  | "kanban"
  | "pivot"
  | "graph"
  | "chart"
  | "calendar"
  | "canvas"

interface ViewEngineProps {
  entity: EntityDef
  view?: string
  /** 记录 ID（表单视图时传入） */
  recordId?: string
  /** 查询窗口标识（表单视图时用于缓存复用） */
  queryToken?: string
  /** 视图设置（由 EntityListView 传入） */
  viewSettings?: ViewSettings
}

/** 视图引擎：根据 view 参数选择渲染器 */
export function ViewEngine({
  entity,
  view = "list",
  recordId,
  queryToken,
  viewSettings
}: ViewEngineProps) {
  return (
    <ViewErrorBoundary>
      <ViewEngineInner
        entity={entity}
        view={view}
        recordId={recordId}
        queryToken={queryToken}
        viewSettings={viewSettings}
      />
    </ViewErrorBoundary>
  )
}

/** 内部渲染逻辑 */
function ViewEngineInner({
  entity,
  view = "list",
  recordId,
  queryToken,
  viewSettings
}: ViewEngineProps) {
  // 物化带 dictType 的 select 字段：从字典拉取数据填充 options，下游视图组件无需改动
  const resolvedEntity = useResolvedEntity(entity)

  // 优先使用实体级自定义覆盖
  if (view === "list" && entity.overrides?.listView) {
    const Override = entity.overrides.listView
    return <Override />
  }
  if (view === "form" && entity.overrides?.formView) {
    const Override = entity.overrides.formView
    return <Override />
  }
  if (view === "kanban" && entity.overrides?.kanbanView) {
    const Override = entity.overrides.kanbanView
    return <Override />
  }

  // 从组件注册表获取视图组件
  const ViewComponent = getViewComponent(view)
  if (ViewComponent) {
    return <ViewComponent />
  }

  // 内置视图
  switch (view) {
    case "list":
      return <ConnectedListView entity={resolvedEntity} viewSettings={viewSettings} />
    case "kanban":
      return <KanbanView entity={resolvedEntity} />
    case "form":
      return (
        <ConnectedFormView entity={resolvedEntity} recordId={recordId} queryToken={queryToken} />
      )
    case "pivot":
      return <PivotView entity={resolvedEntity} />
    case "calendar":
      return <ConnectedCalendarView entity={resolvedEntity} />
    case "canvas":
      return <CanvasView entity={resolvedEntity} recordId={recordId} />
    default:
      return <ViewPlaceholder entity={resolvedEntity} view={view} />
  }
}

/** 列表视图——连接数据层 + URL 状态 */
function ConnectedListView({
  entity,
  viewSettings
}: {
  entity: EntityDef
  viewSettings?: ViewSettings
}) {
  const [params, setParams] = useEntitySearchParams()
  const [filters] = useFilterParams()
  const serverPagination =
    viewSettings?.serverPagination ?? entity.listView.serverPagination ?? false

  // Toolbar（QuickFilterBar/SearchBar/FilterChips）写入 URL 的筛选条件（f_xxx=op:value）
  // 需在此展开为后端 PageDTO 认识的普通字段参数；__search 映射到全文搜索参数
  const search = params.search ?? filters.find((f) => f.field === "__search")?.value ?? undefined
  const fieldFilters = Object.fromEntries(
    filters.filter((f) => f.field !== "__search").map((f) => [f.field, f.value])
  )

  const { data, isLoading, pagination, queryToken } = useEntityQueryWindow(entity, {
    // 服务端分页：传 page/pageSize；前端分页：传 pageSize=-1，由 /_query 返回过滤后的完整窗口
    ...(serverPagination
      ? { page: params.page, pageSize: params.pageSize }
      : { page: 1, pageSize: -1 }),
    sort: params.sort ?? undefined,
    search,
    ...fieldFilters
  })

  return (
    <ListView
      entity={entity}
      data={data}
      loading={isLoading}
      viewSettings={viewSettings}
      // 服务端分页时，把翻页/改页大小的控制权交给 URL 参数
      serverPagination={serverPagination ? pagination : undefined}
      onPageChange={serverPagination ? (page) => setParams({ page }) : undefined}
      onPageSizeChange={serverPagination ? (pageSize) => setParams({ pageSize }) : undefined}
      queryToken={queryToken}
    />
  )
}

/** 表单视图——连接数据层（仅编辑模式；新建走独立路由 EntityCreateView） */
function ConnectedFormView({
  entity,
  recordId,
  queryToken
}: {
  entity: EntityDef
  recordId?: string
  queryToken?: string
}) {
  const { data, isLoading } = useEntityDetail(entity, recordId, { queryToken })
  const { data: access } = useEntityAccess(entity.slug)
  const resource = fromEntityDef(entity)
  const { mutate: update, isPending: updating } = useCrudUpdate(resource)

  // 无更新权限时不传 onSubmit，FormView 据此隐藏保存按钮；access 未加载完成前保持可编辑，避免闪烁
  const canUpdate = access?.update !== false
  const handleSubmit = canUpdate
    ? (values: Record<string, unknown>) => {
        if (!recordId) return
        update(
          { id: recordId, data: values },
          {
            onSuccess: () => toast.success(`${entity.label}已保存`),
            onError: () => {}
          }
        )
      }
    : undefined

  return (
    <div className="space-y-4">
      <RecordWindowPager entity={entity} recordId={recordId} queryToken={queryToken} />
      <FormView
        key={recordId}
        entity={entity}
        data={data ?? undefined}
        loading={isLoading || updating}
        onSubmit={handleSubmit}
      />
      {entity.workflow && recordId && (
        <EntityApproval config={entity.workflow} entityId={recordId} currentUserId="current-user" />
      )}
    </div>
  )
}

function RecordWindowPager({
  entity,
  recordId,
  queryToken
}: {
  entity: EntityDef
  recordId?: string
  queryToken?: string
}) {
  const router = useRouter()
  const queryClient = useQueryClient()
  if (!queryToken) return null

  const queryWindow = findQueryWindow(queryClient, entity.slug, queryToken)
  const ids = queryWindow?.ids?.map(String) ?? []
  const currentIndex = recordId ? ids.indexOf(recordId) : -1
  const hasWindow = !!queryWindow && currentIndex >= 0
  const prevId = hasWindow && currentIndex > 0 ? ids[currentIndex - 1] : undefined
  const nextId = hasWindow && currentIndex < ids.length - 1 ? ids[currentIndex + 1] : undefined
  const boundaryTitle = getBoundaryTitle(hasWindow, queryWindow?.hasMore)

  return (
    <div className="flex items-center justify-between border-b px-4 py-2">
      <div className="text-muted-foreground text-xs">
        {hasWindow ? `当前窗口 ${currentIndex + 1} / ${ids.length}` : "当前查询窗口不可用"}
      </div>
      <div className="flex items-center gap-2">
        <button
          type="button"
          disabled={!prevId}
          title={prevId ? "上一条" : boundaryTitle}
          className="inline-flex h-8 items-center gap-1 rounded-md border px-2 text-sm disabled:cursor-not-allowed disabled:opacity-50"
          onClick={() => prevId && router.push(recordHref(entity.slug, prevId, queryToken))}
        >
          <ChevronLeft className="size-4" />
          上一条
        </button>
        <button
          type="button"
          disabled={!nextId}
          title={nextId ? "下一条" : boundaryTitle}
          className="inline-flex h-8 items-center gap-1 rounded-md border px-2 text-sm disabled:cursor-not-allowed disabled:opacity-50"
          onClick={() => nextId && router.push(recordHref(entity.slug, nextId, queryToken))}
        >
          下一条
          <ChevronRight className="size-4" />
        </button>
      </div>
    </div>
  )
}

function findQueryWindow(
  queryClient: ReturnType<typeof useQueryClient>,
  entitySlug: string,
  queryToken?: string
): PageResult<Record<string, unknown>> | undefined {
  const windows = queryClient.getQueriesData<PageResult<Record<string, unknown>>>({
    queryKey: [entitySlug, "queryWindow"]
  })
  for (const [, window] of windows) {
    if (!window) continue
    if (queryToken && window.queryToken !== queryToken) continue
    return window
  }
  return undefined
}

function getBoundaryTitle(hasWindow: boolean, hasMore?: boolean) {
  if (!hasWindow) return "当前查询窗口不可用，请从列表重新进入"
  if (hasMore) return "已到当前窗口边界，请返回列表切换分页"
  return "已到当前窗口边界"
}

function recordHref(entitySlug: string, id: string, queryToken?: string) {
  const base = paths.workspace.record(entitySlug, id)
  return queryToken ? `${base}?qw=${encodeURIComponent(queryToken)}` : base
}

/** 日历视图——连接数据层 */
function ConnectedCalendarView({ entity }: { entity: EntityDef }) {
  const { data, isLoading } = useEntityList(entity, {})
  return <CalendarView entity={entity} data={data} loading={isLoading} />
}

/** 视图占位组件（后续被 ListView/KanbanView/FormView 替换） */
function ViewPlaceholder({
  entity,
  view,
  recordId
}: {
  entity: EntityDef
  view: string
  recordId?: string
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 p-8 text-muted-foreground">
      <p className="font-medium text-lg">{entity.label}</p>
      <p className="text-sm">
        视图：{view}
        {recordId && ` | 记录：${recordId}`}
      </p>
      <p className="text-xs">（待实现）</p>
    </div>
  )
}
